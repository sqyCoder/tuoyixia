package com.keepup.course;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Prefs {
    private static final String FILE = "course_reminder";
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String cookies(Context c) {
        return sp(c).getString("cookies", "[]");
    }

    public static void setCookies(Context c, String json) {
        sp(c).edit().putString("cookies", json).apply();
    }

    public static boolean hasCookies(Context c) {
        return !"[]".equals(cookies(c));
    }

    public static long lastSync(Context c) {
        return sp(c).getLong("lastSync", 0);
    }

    public static void setLastSync(Context c, long v) {
        sp(c).edit().putLong("lastSync", v).apply();
    }

    public static String lastError(Context c) {
        return sp(c).getString("lastError", "");
    }

    public static String accountId(Context c) {
        return sp(c).getString("accountId", "");
    }

    public static void setAccountId(Context c, String v) {
        sp(c).edit().putString("accountId", v == null ? "" : v).apply();
    }

    public static void setLastError(Context c, String v) {
        sp(c).edit().putString("lastError", v == null ? "" : v).apply();
    }

    public static boolean needFirstSync(Context c) {
        return sp(c).getBoolean("needFirstSync", false);
    }

    public static void setNeedFirstSync(Context c, boolean v) {
        sp(c).edit().putBoolean("needFirstSync", v).apply();
    }

    public static List<Task> tasks(Context c) {
        List<Task> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("tasks", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                Task t = Task.fromJson(arr.getJSONObject(i));
                if (t != null) out.add(t);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static void setTasks(Context c, List<Task> tasks) {
        JSONArray arr = new JSONArray();
        for (Task t : tasks) arr.put(t.toJson());
        sp(c).edit().putString("tasks", arr.toString()).apply();
    }

    public static void setCourses(Context c, JSONArray arr) {
        sp(c).edit().putString("courses", arr.toString()).apply();
    }

    public static int courseCount(Context c) {
        try {
            return new JSONArray(sp(c).getString("courses", "[]")).length();
        } catch (JSONException e) {
            return 0;
        }
    }

    public static String coursesJson(Context c) {
        return sp(c).getString("courses", "[]");
    }

    public static boolean lead24(Context c) {
        return sp(c).getBoolean("lead24", true);
    }

    public static boolean lead2(Context c) {
        return sp(c).getBoolean("lead2", true);
    }

    public static boolean lead0(Context c) {
        return sp(c).getBoolean("lead0", false);
    }

    public static boolean showDone(Context c) {
        return sp(c).getBoolean("showDone", false);
    }

    public static int interval(Context c) {
        return sp(c).getInt("interval", 30);
    }

    public static void saveSettings(Context c, boolean l24, boolean l2, boolean l0, boolean showDone, int interval) {
        sp(c).edit()
                .putBoolean("lead24", l24)
                .putBoolean("lead2", l2)
                .putBoolean("lead0", l0)
                .putBoolean("showDone", showDone)
                .putInt("interval", interval)
                .apply();
    }

    /* ---------------------------------------------------------- 解题助手 */

    /** free = 免 Key 公共通道；custom = 自备 OpenAI 兼容接口 */
    public static String aiMode(Context c) {
        return sp(c).getString("aiMode", "free");
    }

    public static boolean aiFree(Context c) {
        return "free".equals(aiMode(c));
    }

    public static String aiBase(Context c) {
        return sp(c).getString("aiBase", "https://api.deepseek.com/v1");
    }

    public static String aiKey(Context c) {
        return sp(c).getString("aiKey", "");
    }

    public static String aiModel(Context c) {
        return sp(c).getString("aiModel", "deepseek-chat");
    }

    public static boolean autoSubmit(Context c) {
        return sp(c).getBoolean("autoSubmit", false);
    }

    public static void saveAi(Context c, String mode, String base, String key, String model, boolean autoSubmit) {
        sp(c).edit()
                .putString("aiMode", mode == null ? "free" : mode)
                .putString("aiBase", base == null ? "" : base.trim())
                .putString("aiKey", key == null ? "" : key.trim())
                .putString("aiModel", model == null ? "" : model.trim())
                .putBoolean("autoSubmit", autoSubmit)
                .apply();
    }

    /* 答案缓存：同一道题（题干+题型）算过一次就不再算第二次 */

    public static String cacheGet(Context c, String key) {
        try {
            JSONObject o = new JSONObject(sp(c).getString("ansCache", "{}"));
            return o.optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    public static void cachePut(Context c, String key, String answer) {
        if (key == null || key.isEmpty() || answer == null || answer.isEmpty()) return;
        if (key.length() > 200) key = key.substring(0, 200);
        try {
            JSONObject o = new JSONObject(sp(c).getString("ansCache", "{}"));
            o.put(key, answer.length() > 2000 ? answer.substring(0, 2000) : answer);
            if (o.length() > 500) {
                JSONArray keys = o.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length() - 400; i++) o.remove(keys.optString(i));
                }
            }
            sp(c).edit().putString("ansCache", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void clearCache(Context c) {
        sp(c).edit().remove("ansCache").apply();
    }

    public static void setScheduled(Context c, JSONArray arr) {
        sp(c).edit().putString("scheduled", arr.toString()).apply();
    }

    public static List<String> scheduled(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString("scheduled", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (JSONException ignored) {
        }
        return out;
    }

    /** 用户协议：一旦同意，清账号也不重置 */
    public static boolean agreed(Context c) {
        return sp(c).getBoolean("agreed", false);
    }

    public static void setAgreed(Context c, boolean v) {
        sp(c).edit().putBoolean("agreed", v).apply();
    }

    public static void clearAccount(Context c) {
        // 整份清掉：enc_/chapters_/lastChapter_/ansCache 这些按课程存的数据也必须清，
        // 否则换账号后会拿旧账号的 enc 和旧目录去加载。
        boolean agreed = agreed(c);
        sp(c).edit()
                .clear()
                .putBoolean("agreed", agreed)
                .putBoolean("needFirstSync", true)
                .apply();
    }
}
