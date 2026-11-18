# 保留 JS 接口方法名，否则 release 包里的 @JavascriptInterface 会失效
-keepclassmembers class com.example.chatdemo.NativeBridge {
    <methods>;
}
