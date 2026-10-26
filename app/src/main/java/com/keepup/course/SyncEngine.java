package com.keepup.course;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SyncEngine {
    private static final Object LOCK = new Object();

    public static class Result {
        public int courses;
        public int unsubmitted;
        public int added;
        public boolean first;
    }

    public static Result sync(Context c, ChaoxingApi.Progress p) throws Exception {
        synchronized (LOCK) {
            ChaoxingApi.Snapshot snap = ChaoxingApi.sync(c, p);

            boolean first = Prefs.lastSync(c) == 0;
            String oldAccount = Prefs.accountId(c);
            if (!oldAccount.isEmpty() && !snap.accountId.isEmpty() && !oldAccount.equals(snap.accountId)) {
                Reminders.cancelAll(c);
                Prefs.setTasks(c, new ArrayList<>());
                first = true;
            }

            List<Task> old = Prefs.tasks(c);
            Map<String, Task> oldMap = new HashMap<>();
            for (Task t : old) oldMap.put(t.workId, t);

            long now = System.currentTimeMillis();
            List<Task> merged = new ArrayList<>();
            int added = 0;
            int unsubmitted = 0;

            for (Task t : snap.tasks) {
                Task o = oldMap.get(t.workId);
                if (o != null) {
                    t.firstSeen = o.firstSeen > 0 ? o.firstSeen : now;
                } else {
                    t.firstSeen = now;
                    added++;
                    if (!first && !t.done()) {
                        Notifications.newTask(c, t);
                    }
                }
                if (!t.done()) unsubmitted++;
                merged.add(t);
            }

            merged.sort(new Comparator<Task>() {
                @Override
                public int compare(Task a, Task b) {
                    if (a.done() != b.done()) return a.done() ? 1 : -1;
                    long x = a.endAt > 0 ? a.endAt : Long.MAX_VALUE;
                    long y = b.endAt > 0 ? b.endAt : Long.MAX_VALUE;
                    return Long.compare(x, y);
                }
            });

            Prefs.setTasks(c, merged);
            Prefs.setCourses(c, coursesJson(snap.courses));
            Prefs.setAccountId(c, snap.accountId);
            Prefs.setLastSync(c, now);
            Prefs.setLastError(c, "");
            Prefs.setNeedFirstSync(c, false);

            Reminders.reschedule(c, merged);
            if (first) Notifications.summary(c, snap.courses.size(), unsubmitted);

            Result r = new Result();
            r.courses = snap.courses.size();
            r.unsubmitted = unsubmitted;
            r.added = added;
            r.first = first;
            return r;
        }
    }

    private static JSONArray coursesJson(List<ChaoxingApi.Course> courses) {
        JSONArray arr = new JSONArray();
        for (ChaoxingApi.Course c : courses) {
            JSONObject o = new JSONObject();
            try {
                o.put("name", c.name);
                o.put("courseId", c.courseId);
                o.put("classId", c.classId);
                o.put("cpi", c.cpi);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        return arr;
    }
}
