package com.keepup.course;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class Notifications {
    public static final String CH_NEW = "tasks";
    public static final String CH_DUE = "deadlines";
    public static final String CH_SYS = "system";

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TASK = "taskId";

    public static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel n = new NotificationChannel(CH_NEW, c.getString(R.string.chan_new), NotificationManager.IMPORTANCE_HIGH);
        n.setDescription("老师发布新作业时提醒");
        nm.createNotificationChannel(n);
        NotificationChannel d = new NotificationChannel(CH_DUE, c.getString(R.string.chan_due), NotificationManager.IMPORTANCE_HIGH);
        d.setDescription("作业截止前的提醒");
        nm.createNotificationChannel(d);
        NotificationChannel s = new NotificationChannel(CH_SYS, c.getString(R.string.chan_sys), NotificationManager.IMPORTANCE_DEFAULT);
        nm.createNotificationChannel(s);
    }

    private static PendingIntent openApp(Context c, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent openTask(Context c, Task t, int req) {
        Intent i = new Intent(c, WorkActivity.class);
        i.putExtra(EXTRA_URL, t.url);
        i.putExtra(EXTRA_TASK, t.workId);
        i.putExtra("title", t.title);
        i.setAction("open_" + t.workId);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int id(String key) {
        return key.hashCode() & 0x7fffffff;
    }

    private static void post(Context c, int id, String channel, String title, String text, PendingIntent content, PendingIntent action, String actionLabel) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b = new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_notify)
                .setColor(c.getColor(R.color.brand))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(content);
        if (action != null && actionLabel != null) {
            b.addAction(new Notification.Action.Builder(null, actionLabel, action).build());
        }
        try {
            nm.notify(id, b.build());
        } catch (SecurityException ignored) {
        }
    }

    public static void newTask(Context c, Task t) {
        String title = c.getString(R.string.new_task_title, t.courseName);
        String text = t.title + "\n" + c.getString(R.string.due_line, t.deadlineText());
        post(c, id("new_" + t.workId), CH_NEW, title, text,
                openApp(c, id("newc_" + t.workId)), openTask(c, t, id("newa_" + t.workId)), c.getString(R.string.action_open));
    }

    public static void dueSoon(Context c, Task t, int leadMinutes) {
        String when = leadMinutes <= 0
                ? c.getString(R.string.due_now_title, t.courseName)
                : c.getString(R.string.due_soon_title, leadText(leadMinutes), t.courseName);
        String text = t.title + "\n" + c.getString(R.string.due_line, t.deadlineText());
        post(c, id("due_" + t.workId + "_" + leadMinutes), CH_DUE, when, text,
                openApp(c, id("duec_" + t.workId)), openTask(c, t, id("duea_" + t.workId)), c.getString(R.string.action_open));
    }

    public static void authExpired(Context c) {
        post(c, 1001, CH_SYS, c.getString(R.string.app_name), c.getString(R.string.auth_expired),
                openApp(c, 1001), null, null);
    }

    public static void summary(Context c, int courses, int unsubmitted) {
        String text = "已适配 " + courses + " 门课程，发现 " + unsubmitted + " 个未交任务";
        post(c, 1002, CH_SYS, c.getString(R.string.app_name), text, openApp(c, 1002), null, null);
    }

    public static String leadText(int minutes) {
        if (minutes >= 1440) return (minutes / 1440) + " 天";
        if (minutes >= 60) return (minutes / 60) + " 小时";
        return minutes + " 分钟";
    }
}
