package com.keepup.course;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Task {
    public String workId = "";
    public String courseId = "";
    public String classId = "";
    public String title = "";
    public String courseName = "";
    public String status = "";
    public long endAt = 0L;
    public String url = "";
    public long firstSeen = 0L;

    public boolean done() {
        return status != null && (status.contains("已完成") || status.contains("已交"));
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("workId", workId);
            o.put("courseId", courseId);
            o.put("classId", classId);
            o.put("title", title);
            o.put("courseName", courseName);
            o.put("status", status);
            o.put("endAt", endAt);
            o.put("url", url);
            o.put("firstSeen", firstSeen);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Task fromJson(JSONObject o) {
        try {
            Task t = new Task();
            t.workId = o.optString("workId");
            t.courseId = o.optString("courseId");
            t.classId = o.optString("classId");
            t.title = o.optString("title");
            t.courseName = o.optString("courseName");
            t.status = o.optString("status");
            t.endAt = o.optLong("endAt");
            t.url = o.optString("url");
            t.firstSeen = o.optLong("firstSeen");
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    public String deadlineText() {
        if (endAt <= 0) return "";
        SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        return f.format(new Date(endAt));
    }

    public String remainText() {
        if (endAt <= 0) return "";
        long ms = endAt - System.currentTimeMillis();
        if (ms <= 0) return "已截止";
        long min = TimeUnit.MILLISECONDS.toMinutes(ms);
        long days = min / (60 * 24);
        long hours = (min % (60 * 24)) / 60;
        long mins = min % 60;
        if (days > 0) return "剩余 " + days + " 天 " + hours + " 小时";
        if (hours > 0) return "剩余 " + hours + " 小时 " + mins + " 分";
        return "剩余 " + Math.max(1, mins) + " 分钟";
    }
}
