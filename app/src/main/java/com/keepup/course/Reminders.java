package com.keepup.course;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Reminders {
    private static final int[] LEADS = {1440, 120, 0};

    public static void reschedule(Context c, List<Task> tasks) {
        long now = System.currentTimeMillis();
        Set<String> desired = new HashSet<>();
        WorkManager wm = WorkManager.getInstance(c);
        boolean[] on = {Prefs.lead24(c), Prefs.lead2(c), Prefs.lead0(c)};
        for (Task t : tasks) {
            if (t.done() || t.endAt <= 0 || t.url == null || t.url.isEmpty()) continue;
            for (int i = 0; i < LEADS.length; i++) {
                if (!on[i]) continue;
                int lead = LEADS[i];
                long trigger = t.endAt - lead * 60000L;
                if (trigger <= now + 30000L) continue;
                String name = "rem_" + t.workId + "_" + lead;
                Data data = new Data.Builder()
                        .putString("workId", t.workId)
                        .putString("title", t.title)
                        .putString("course", t.courseName)
                        .putString("url", t.url)
                        .putLong("endAt", t.endAt)
                        .putInt("lead", lead)
                        .build();
                OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                        .setInputData(data)
                        .setInitialDelay(Math.max(1, trigger - now), java.util.concurrent.TimeUnit.MILLISECONDS)
                        .build();
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req);
                desired.add(name);
            }
        }
        List<String> old = Prefs.scheduled(c);
        for (String name : old) {
            if (!desired.contains(name)) wm.cancelUniqueWork(name);
        }
        JSONArray arr = new JSONArray(new ArrayList<>(desired));
        Prefs.setScheduled(c, arr);
    }

    public static void cancelAll(Context c) {
        WorkManager wm = WorkManager.getInstance(c);
        for (String name : Prefs.scheduled(c)) wm.cancelUniqueWork(name);
        Prefs.setScheduled(c, new JSONArray());
    }
}
