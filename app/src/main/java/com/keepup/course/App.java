package com.keepup.course;

import android.app.Application;
import android.webkit.WebView;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 调试用：允许用 chrome://inspect / adb forward 检查 WebView 里的真实页面
        try {
            WebView.setWebContentsDebuggingEnabled(true);
        } catch (Exception ignored) {
        }
        Notifications.createChannels(this);
        SyncWorker.schedulePeriodic(this);
    }
}
