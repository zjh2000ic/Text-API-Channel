package io.github.zjh2000ic.textapichannel;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * 暴露给页面的原生桥（JS 里通过 {@code window.ChatDemo} 调用）。
 *
 * <p>WebView 不会把 {@code blob:} / {@code data:} 的下载交给 DownloadListener，
 * 所以页面里的「导出对话」「下载附件」在 native.js 里被劫持成 base64，
 * 再从这里落到系统下载目录。
 */
public class NativeBridge {
    private static final String TAG = "ChatDemoBridge";
    private static final String SHARED_DIR = "shared";

    private final MainActivity activity;
    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());

    NativeBridge(MainActivity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    // ------------------------------------------------------------ 文件

    /**
     * 保存文件到「下载」目录（Android 10+ 走 MediaStore，无需存储权限）。
     *
     * @return 成功时返回展示给用户的保存位置，失败返回空串
     */
    @JavascriptInterface
    public String saveFile(String base64, String fileName, String mimeType) {
        byte[] bytes = decode(base64);
        if (bytes == null) {
            Log.w(TAG, "saveFile: base64 decode failed");
            return "";
        }
        String safeName = sanitize(fileName);
        String mime = mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri target;
            try {
                target = activity.getContentResolver().insert(collection, values);
                if (target == null) {
                    return "";
                }
                try (OutputStream out = activity.getContentResolver().openOutputStream(target)) {
                    if (out == null) {
                        throw new IllegalStateException("openOutputStream returned null");
                    }
                    out.write(bytes);
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                activity.getContentResolver().update(target, values, null, null);
            } catch (Exception error) {
                Log.w(TAG, "saveFile to MediaStore failed", error);
                return "";
            }
            toast("已保存到 下载/" + safeName);
            return "下载/" + safeName;
        }

        // Android 9 及以下：无 MediaStore.Downloads，写到应用专属的外部目录
        try {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                dir = new File(activity.getFilesDir(), "download");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                return "";
            }
            File outFile = uniqueFile(dir, safeName);
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                out.write(bytes);
            }
            showToast("已保存到 " + outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
        } catch (Exception error) {
            Log.w(TAG, "saveFile to private dir failed", error);
            return "";
        }
    }

    /**
     * 用系统应用打开 blob: 附件预览（写到缓存目录 + FileProvider 授权）。
     */
    @JavascriptInterface
    public boolean previewFile(String base64, String fileName, String mimeType) {
        final String safeName = sanitize(fileName);
        final String mime = mimeType == null || mimeType.isEmpty()
                ? "*/*" : mimeType;
        byte[] bytes = decode(base64);
        if (bytes == null) {
            return false;
        }
        try {
            File dir = new File(activity.getCacheDir(), SHARED_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                return false;
            }
            File outFile = new File(dir, safeName);
            try (FileOutputStream out = new FileOutputStream(outFile)) {
                out.write(bytes);
            }
            Uri uri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".fileprovider", outFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(Intent.createChooser(intent, safeName));
            return true;
        } catch (Exception error) {
            Log.w(TAG, "previewFile failed", error);
            showToast("没有可以打开该文件的应用");
            return false;
        }
    }

    // ------------------------------------------------------------ 界面

    /** 同步状态栏 / 导航栏底色，颜色来自页面 {@code meta[name=theme-color]} */
    @JavascriptInterface
    public void setSystemBars(final String hexColor) {
        final int color;
        try {
            color = Color.parseColor(hexColor);
        } catch (Exception error) {
            return;
        }
        main.post(() -> activity.applyWindowChrome(color));
    }

    @JavascriptInterface
    public void toast(final String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        main.post(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public void openExternal(final String url) {
        if (url == null || url.isEmpty()) {
            return;
        }
        main.post(() -> activity.openExternal(Uri.parse(url)));
    }

    @JavascriptInterface
    public String getVersion() {
        return BuildConfigCompat.VERSION_NAME;
    }

    // ------------------------------------------------------------ 工具

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        String payload = base64;
        int comma = base64.indexOf(',');
        if (base64.startsWith("data:") && comma > 0) {
            payload = base64.substring(comma + 1);
        }
        try {
            return Base64.decode(payload, Base64.DEFAULT);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String sanitize(String name) {
        String value = name == null || name.trim().isEmpty() ? "download" : name.trim();
        value = value.replace('/', '_').replace('\\', '_').replace(':', '_');
        if (value.length() > 120) {
            value = value.substring(value.length() - 120);
        }
        return value;
    }

    private static File uniqueFile(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) {
            return candidate;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            candidate = new File(dir, stem + "(" + i + ")" + ext);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return new File(dir, System.currentTimeMillis() + "-" + name);
    }

    private void showToast(String message) {
        main.post(() -> Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
    }

    /** 供 Activity 使用：注入脚本的入口，便于后续扩展 */
    void evaluate(String script) {
        if (webView != null) {
            webView.evaluateJavascript(script, null);
        }
    }
}
