package com.keepup.course;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class Http {
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36";
    private static OkHttpClient client;

    public static class Result {
        public final String url;
        public final int code;
        public final String body;

        Result(String url, int code, String body) {
            this.url = url;
            this.code = code;
            this.body = body;
        }
    }

    public static class AuthException extends IOException {
        public AuthException(String msg) {
            super(msg);
        }
    }

    public static synchronized OkHttpClient client(Context c) {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .cookieJar(new CookieStore(c))
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build();
        }
        return client;
    }

    public static synchronized void reset() {
        client = null;
    }

    public static Result get(Context c, String url, String referer) throws IOException {
        Request.Builder b = new Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        if (referer != null) b.header("Referer", referer);
        try (Response r = client(c).newCall(b.build()).execute()) {
            String body = r.body() == null ? "" : r.body().string();
            String finalUrl = r.request().url().toString();
            checkAuth(finalUrl, body);
            return new Result(finalUrl, r.code(), body);
        }
    }

    private static void checkAuth(String finalUrl, String body) throws AuthException {
        String u = finalUrl.toLowerCase();
        if (u.contains("passport") || u.contains("/login")) {
            throw new AuthException("登录已失效");
        }
        if (body != null && body.contains("用户登录") && body.contains("passport2")) {
            throw new AuthException("登录已失效");
        }
    }

    public static void copyToWebView(Context c) {
        try {
            JSONArray arr = new JSONArray(Prefs.cookies(c));
            CookieManager cm = CookieManager.getInstance();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String domain = o.optString("domain");
                String name = o.optString("name");
                String value = o.optString("value");
                String path = o.optString("path", "/");
                if (domain.isEmpty() || name.isEmpty()) continue;
                String url = "https://" + domain + path;
                String cookie = name + "=" + value + "; path=" + path;
                if (!o.optBoolean("hostOnly", false)) cookie += "; domain=" + domain;
                cm.setCookie(url, cookie);
            }
            cm.flush();
        } catch (Exception ignored) {
        }
    }
}
