package io.github.zjh2000ic.textapichannel;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 单 Activity + WebView 外壳。
 *
 * <p>前端产物放在 {@code assets/www}，通过 {@link WebViewAssetLoader} 以
 * {@code https://appassets.androidplatform.net/...} 提供：这样页面处于安全上下文，
 * localStorage / IndexedDB / getUserMedia / AudioContext 才能正常工作（file:// 下不行）。
 */
public class MainActivity extends Activity {
    private static final String TAG = "ChatDemo";
    static final String APP_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + APP_HOST + "/assets/www/index.html";

    private static final int REQ_FILE_CHOOSER = 4101;
    private static final int REQ_MIC = 4102;

    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PermissionRequest pendingPermissionRequest;
    private long lastBackPressedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        installCrashHandler();

        try {
            applyWindowChrome(getColorCompat(R.color.surface));
        } catch (Throwable ignored) {
            // 状态栏配色失败不应该影响启动
        }

        // 先挂一个纯色占位视图，再在下一帧构造 WebView。
        // 这样即使系统 WebView 缺失/被禁用（构造时抛异常），也能把原因显示在屏幕上，
        // 而不是「点一下就闪退」。
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(getColorCompat(R.color.surface));
        setContentView(root);

        root.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WebView view = createWebView();
                    root.addView(view, new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                } catch (Throwable error) {
                    Log.e(TAG, "WebView 初始化失败", error);
                    reportCrash("WebView 初始化失败", error);
                    showFatal(root, error);
                }
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        assertWebViewAvailable();

        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(getColorCompat(R.color.surface));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // 语音/朗读依赖 AudioContext，允许非手势场景出声
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        // 注意：不能关闭 content access，否则文件选择器返回的 content:// 无法被页面读取
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 前端通过 UA 判断是否为外壳（可选，页面不使用也不影响）
        settings.setUserAgentString(settings.getUserAgentString() + " ChatDemoAndroid/" + BuildConfigCompat.VERSION_NAME);

        webView.addJavascriptInterface(new NativeBridge(this, webView), "ChatDemo");
        webView.setWebViewClient(new AppWebViewClient());
        webView.setWebChromeClient(new AppChromeClient());
        webView.setDownloadListener(this::handleDownload);
        webView.loadUrl(START_URL);
        return webView;
    }

    // ---------------------------------------------------------------- WebViewClient

    private class AppWebViewClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return assetLoader.shouldInterceptRequest(request.getUrl());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri url = request.getUrl();
            if (APP_HOST.equals(url.getHost())) {
                return false; // 应用内页面，交给 WebView
            }
            openExternal(url);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Log.i(TAG, "page finished: " + url);
        }
    }

    // ---------------------------------------------------------------- WebChromeClient

    private class AppChromeClient extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(null);
            }
            fileChooserCallback = callback;

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] acceptTypes = params.getAcceptTypes();
            if (acceptTypes != null) {
                List<String> mimeTypes = new ArrayList<>();
                for (String accept : acceptTypes) {
                    if (accept == null) continue;
                    String value = accept.trim();
                    if (value.isEmpty() || value.startsWith(".")) continue;
                    mimeTypes.add(value);
                }
                if (!mimeTypes.isEmpty()) {
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toArray(new String[0]));
                    if (mimeTypes.size() == 1) {
                        intent.setType(mimeTypes.get(0));
                    }
                }
            }
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            try {
                startActivityForResult(intent, REQ_FILE_CHOOSER);
                return true;
            } catch (ActivityNotFoundException error) {
                fileChooserCallback = null;
                callback.onReceiveValue(null);
                Toast.makeText(MainActivity.this, R.string.no_file_picker, Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                boolean wantsAudio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                        wantsAudio = true;
                    }
                }
                if (!wantsAudio) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pendingPermissionRequest = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                }
            });
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      Message resultMsg) {
            // 兜底：页面里的 window.open 已在 native.js 中拦截，这里再拦一层防止弹窗白屏
            WebView popup = new WebView(MainActivity.this);
            popup.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                    openExternal(request.getUrl());
                    v.destroy();
                    return true;
                }
            });
            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(popup);
            resultMsg.sendToTarget();
            return true;
        }

        @Override
        public boolean onConsoleMessage(android.webkit.ConsoleMessage message) {
            Log.d(TAG, "[web] " + message.message() + " @" + message.lineNumber());
            return true;
        }
    }

    // ---------------------------------------------------------------- 回调

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FILE_CHOOSER) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) {
                    result[i] = clip.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        if (fileChooserCallback != null) {
            fileChooserCallback.onReceiveValue(result);
            fileChooserCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC || pendingPermissionRequest == null) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            pendingPermissionRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            pendingPermissionRequest.deny();
            Toast.makeText(this, R.string.mic_denied, Toast.LENGTH_LONG).show();
        }
        pendingPermissionRequest = null;
    }

    // ---------------------------------------------------------------- 返回键

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressedAt < 1500) {
            super.onBackPressed();
            return;
        }
        lastBackPressedAt = now;
        // 让页面有机会先关闭弹层（当前 demo 未实现 app:back 监听，可作为扩展点）
        if (webView != null) {
            webView.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('app:back'))", null);
        }
        Toast.makeText(this, R.string.back_again_to_exit, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- 工具方法

    /** 普通 http(s) 链接交给系统浏览器 */
    void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    /** 兜底下载：blob:/data: 由 native.js 走 ChatDemo.saveFile，这里只处理普通链接 */
    private void handleDownload(String url, String userAgent, String contentDisposition,
                                String mimeType, long contentLength) {
        if (url == null || url.startsWith("data:") || url.startsWith("blob:")) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) {
                request.addRequestHeader("Cookie", cookie);
            }
            request.setTitle(fileName);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, getString(R.string.download_started, fileName),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception error) {
            Log.w(TAG, "download failed", error);
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 按页面主题色同步状态栏 / 导航栏，深色背景用浅色图标 */
    void applyWindowChrome(int color) {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);

        boolean lightBackground = isLight(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBackground ? mask : 0, mask);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility();
            if (lightBackground) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
        if (webView != null) {
            webView.setBackgroundColor(color);
        }
    }

    private static boolean isLight(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.6;
    }

    int getColorCompat(int resId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(resId);
        }
        return getResources().getColor(resId);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- 启动失败兜底

    /** 系统 WebView 缺失或被禁用时，构造 WebView 会直接抛异常，先给出可读的提示 */
    private void assertWebViewAvailable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PackageInfo pkg = WebView.getCurrentWebViewPackage();
            if (pkg == null) {
                throw new IllegalStateException("系统 WebView 不可用。请在应用商店更新"
                        + "「Android System WebView」或「Chrome」，或在 设置 → 应用 里重新启用它，然后重试。");
            }
        }
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                reportCrash("未捕获异常", error);
            } catch (Throwable ignored) {
                // 兜底里再抛异常就没有意义了
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }

    /** 把崩溃信息写进应用私有目录，下次排查时可以直接取 */
    private void reportCrash(String where, Throwable error) {
        try {
            String text = where + "\n" + buildDiagnostics(error);
            Log.e(TAG, text);
            File dir = getExternalFilesDir(null);
            if (dir == null) {
                dir = getFilesDir();
            }
            File out = new File(dir, "crash-" + System.currentTimeMillis() + ".txt");
            FileOutputStream stream = new FileOutputStream(out);
            try {
                stream.write(text.getBytes(StandardCharsets.UTF_8));
            } finally {
                stream.close();
            }
        } catch (Throwable ignored) {
            // 写不了日志也不影响主流程
        }
    }

    private String buildDiagnostics(Throwable error) {
        StringBuilder out = new StringBuilder();
        out.append("机型: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        out.append("系统: Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(')').append('\n');
        out.append("应用: ").append(BuildConfigCompat.VERSION_NAME)
                .append(" / ").append(getPackageName()).append('\n');
        out.append("WebView: ").append(webViewVersion()).append('\n');
        out.append("时间: ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()))
                .append('\n');
        if (error != null) {
            out.append('\n').append(Log.getStackTraceString(error));
        }
        return out.toString();
    }

    private static String webViewVersion() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PackageInfo pkg = WebView.getCurrentWebViewPackage();
                if (pkg == null) {
                    return "不可用（当前 WebView 包为 null）";
                }
                return pkg.packageName + " " + pkg.versionName;
            }
            return "未知（API 低于 26）";
        } catch (Throwable error) {
            return "读取失败: " + error;
        }
    }

    /** 在窗口里显示失败原因，并允许复制全文，方便直接反馈 */
    private void showFatal(ViewGroup root, Throwable error) {
        final String detail = buildDiagnostics(error);
        root.removeAllViews();

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("启动失败");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFB3261E);
        title.setPadding(dp(20), dp(24), dp(20), dp(4));
        column.addView(title);

        TextView hint = new TextView(this);
        hint.setText("把这一屏截图发给开发者，就能直接定位原因；也可以用下面的「复制」把全文取出来。");
        hint.setTextSize(13);
        hint.setTextColor(0xFF444444);
        hint.setPadding(dp(20), 0, dp(20), dp(12));
        column.addView(hint);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(16), 0, dp(16), dp(8));

        Button copy = new Button(this);
        copy.setText("复制");
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("ChatDemo 启动失败", detail));
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(copy);

        Button retry = new Button(this);
        retry.setText("重试");
        retry.setOnClickListener(v -> recreate());
        actions.addView(retry);

        Button quit = new Button(this);
        quit.setText("退出");
        quit.setOnClickListener(v -> finish());
        actions.addView(quit);

        column.addView(actions);

        TextView body = new TextView(this);
        body.setText(detail);
        body.setTextIsSelectable(true);
        body.setTextSize(11);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextColor(0xFF111111);
        body.setPadding(dp(16), dp(8), dp(16), dp(16));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        column.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
