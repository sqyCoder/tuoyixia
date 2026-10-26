package com.keepup.course;

import android.content.Context;
import android.webkit.CookieManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class CookieStore implements CookieJar {
    private static final String[] HOSTS = {
            "i.chaoxing.com",
            "mooc1.chaoxing.com",
            "mooc1-1.chaoxing.com",
            "mooc2-ans.chaoxing.com",
            "mobilelearn.chaoxing.com",
            "notice.chaoxing.com",
            "passport2.chaoxing.com",
            "chaoxing.com"
    };

    private final Context context;
    private final List<Cookie> cookies = new ArrayList<>();
    private String loadedJson;

    public CookieStore(Context context) {
        this.context = context.getApplicationContext();
        load();
    }

    private void load() {
        cookies.clear();
        loadedJson = Prefs.cookies(context);
        try {
            JSONArray arr = new JSONArray(loadedJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String domain = o.optString("domain");
                if (domain.isEmpty()) continue;
                Cookie.Builder b = o.optBoolean("hostOnly", false)
                        ? new Cookie.Builder().hostOnlyDomain(domain)
                        : new Cookie.Builder().domain(domain);
                b.name(o.optString("name"))
                        .value(o.optString("value"))
                        .path(o.optString("path", "/"));
                long exp = o.optLong("expiresAt", 0L);
                if (exp > 0) b.expiresAt(exp);
                if (o.optBoolean("secure", false)) b.secure();
                if (o.optBoolean("httpOnly", false)) b.httpOnly();
                cookies.add(b.build());
            }
        } catch (Exception ignored) {
        }
    }

    /** 本地 cookie 被重新写入过（例如刚从登录页抓取到）就重载，避免老实例一直用旧 cookie。 */
    private void reloadIfChanged() {
        String json = Prefs.cookies(context);
        if (!json.equals(loadedJson)) load();
    }

    public void save() {
        JSONArray arr = new JSONArray();
        for (Cookie c : cookies) {
            JSONObject o = new JSONObject();
            try {
                o.put("domain", c.domain());
                o.put("name", c.name());
                o.put("value", c.value());
                o.put("path", c.path());
                o.put("expiresAt", c.expiresAt());
                o.put("secure", c.secure());
                o.put("httpOnly", c.httpOnly());
                o.put("hostOnly", c.hostOnly());
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        loadedJson = arr.toString();
        Prefs.setCookies(context, loadedJson);
    }

    public void clear() {
        cookies.clear();
        save();
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        reloadIfChanged();
        long now = System.currentTimeMillis();
        Map<String, Cookie> best = new LinkedHashMap<>();
        for (Cookie c : cookies) {
            if (c.expiresAt() < now) continue;
            if (!c.matches(url)) continue;
            Cookie cur = best.get(c.name());
            if (cur == null || c.domain().length() > cur.domain().length()) {
                best.put(c.name(), c);
            }
        }
        return new ArrayList<>(best.values());
    }

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> received) {
        for (Cookie c : received) {
            Cookie old = null;
            for (Cookie x : cookies) {
                if (x.name().equals(c.name()) && x.domain().equals(c.domain()) && x.path().equals(c.path())) {
                    old = x;
                    break;
                }
            }
            if (old != null) cookies.remove(old);
            cookies.add(c);
        }
        save();
    }

    public synchronized int seedFromWebView() {
        CookieManager cm = CookieManager.getInstance();
        int added = 0;
        for (String host : HOSTS) {
            String raw = cm.getCookie("https://" + host + "/");
            if (raw == null || raw.isEmpty()) continue;
            for (String part : raw.split(";")) {
                String kv = part.trim();
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String name = kv.substring(0, eq).trim();
                String value = kv.substring(eq + 1).trim();
                if (name.isEmpty()) continue;
                removeHostOnly(host, name);
                Cookie c = new Cookie.Builder()
                        .hostOnlyDomain(host)
                        .name(name)
                        .value(value)
                        .path("/")
                        .build();
                cookies.add(c);
                added++;
            }
        }
        save();
        return added;
    }

    private void removeHostOnly(String host, String name) {
        Cookie old = null;
        for (Cookie c : cookies) {
            if (c.hostOnly() && c.domain().equals(host) && c.name().equals(name)) {
                old = c;
                break;
            }
        }
        if (old != null) cookies.remove(old);
    }

    /** 本地是否已经存有学习通的登录 UID（说明网页端确实登录成功过）。 */
    public synchronized boolean hasUid() {
        try {
            JSONArray arr = new JSONArray(Prefs.cookies(context));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String name = o.optString("name");
                if (("_uid".equals(name) || "UID".equals(name)) && !o.optString("value").isEmpty()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public synchronized String cookieHeaderFor(String url) {
        HttpUrl u = HttpUrl.parse(url);
        if (u == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Cookie c : loadForRequest(u)) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(c.name()).append('=').append(c.value());
        }
        return sb.toString();
    }
}
