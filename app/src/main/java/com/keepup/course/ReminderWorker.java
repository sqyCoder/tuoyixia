package com.keepup.course;

import android.content.Context;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ReminderWorker extends Worker {
    public ReminderWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        String workId = getInputData().getString("workId");
        int lead = getInputData().getInt("lead", 0);
        long endAt = getInputData().getLong("endAt", 0L);
        if (workId == null) return Result.success();
        Task found = null;
        for (Task t : Prefs.tasks(getApplicationContext())) {
            if (workId.equals(t.workId)) {
                found = t;
                break;
            }
        }
        if (found == null || found.done()) return Result.success();
        if (found.endAt > 0 && endAt > 0 && Math.abs(found.endAt - endAt) > 15 * 60000L) {
            return Result.success();
        }
        Notifications.dueSoon(getApplicationContext(), found, lead);
        return Result.success();
    }
}
