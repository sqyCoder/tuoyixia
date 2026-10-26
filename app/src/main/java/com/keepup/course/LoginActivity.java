package com.keepup.course;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoginActivity extends Activity {
    private WebView web;
    private AuroraView aurora;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean checking = new AtomicBoolean(false);

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Prefs.hasCookies(this)) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);
        aurora = findViewById(R.id.aurora);
        web = findViewById(R.id.web);
        Button done = findViewById(R.id.btnDone);
        Ui.press(done);
        Ui.gradientText((android.widget.TextView) findViewById(R.id.tvLoginTitle),
                0xFF1A1D1C, 0xFF2C2952, 0xFF3E3A6E);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (isLoggedInUrl(url) && checking.compareAndSet(false, true)) {
                    verify(false);
                }
            }
        });
        web.loadUrl("https://i.chaoxing.com");

        done.setOnClickListener(v -> {
            Toast.makeText(this, R.string.login_checking, Toast.LENGTH_SHORT).show();
            if (checking.compareAndSet(false, true)) verify(true);
        });
    }

    private boolean isLoggedInUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("://i.chaoxing.com") && !u.contains("passport");
    }

    private void verify(boolean userTriggered) {
        io.execute(() -> {
            String reason = "";
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    int seeded = seedCookies();
                    Http.reset(); // 丢掉旧 cookie jar，否则会一直用第一次（可能还没登录）的 cookie
                    List<ChaoxingApi.Course> courses = ChaoxingApi.fetchCourses(this);
                    if (!courses.isEmpty()) {
                        enterMain();
                        return;
                    }
                    reason = seeded == 0 ? "没能读到网页里的登录信息" : "课程列表为空";
                } catch (Http.AuthException e) {
                    reason = "登录信息未被识别";
                } catch (Exception e) {
                    String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    reason = "网络异常：" + m;
                }
                if (attempt < 3) sleep(1200);
            }

            // 网页登录确实成功过（存到了 _uid），就别再拦着，进主界面让同步去暴露真实的错误
            if (new CookieStore(this).hasUid()) {
                enterMain();
                return;
            }
            final String msg = "未检测到有效登录（" + reason + "），请先在页面中完成登录";
            runOnUiThread(() -> {
                checking.set(false);
                if (userTriggered) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        });
    }

    /** CookieManager 只在主线程可靠，抓取前先 flush。返回抓到的 cookie 条数。 */
    private int seedCookies() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                CookieManager.getInstance().flush();
                return new CookieStore(this).seedFromWebView();
            } catch (Exception e) {
                return 0;
            }
        }
        final int[] out = {0};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                CookieManager.getInstance().flush();
                out[0] = new CookieStore(LoginActivity.this).seedFromWebView();
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(6, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return out[0];
    }

    private void enterMain() {
        runOnUiThread(() -> {
            checking.set(false);
            Prefs.setNeedFirstSync(this, true);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null && web.canGoBack()) {
            web.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (aurora != null) aurora.resume();
    }

    @Override
    protected void onPause() {
        if (aurora != null) aurora.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
