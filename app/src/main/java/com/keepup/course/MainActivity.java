package com.keepup.course;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private TaskAdapter adapter;
    private TextView tvToday, tvHeadline, tvLastSync, tvEmptyTitle, tvEmptySub;
    private TextView tvStatOpen, tvStatToday, tvStatDone;
    private TextView chipAll, chipToday, chipOverdue;
    private ProgressBar progress;
    private Button syncBtn;
    private View emptyState;
    private AuroraView aurora;
    private View heroCard;
    private TextView heroNum, heroUnit, heroTitle, heroDue;
    private Task heroTask;

    private int prevOpen = -1, prevToday = -1, prevDone = -1;
    private int filter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Prefs.agreed(this)) {
            startActivity(new Intent(this, AgreementActivity.class));
            finish();
            return;
        }
        if (!Prefs.hasCookies(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        aurora = findViewById(R.id.aurora);
        tvToday = findViewById(R.id.tvToday);
        tvHeadline = findViewById(R.id.tvHeadline);
        tvLastSync = findViewById(R.id.tvLastSync);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptySub = findViewById(R.id.tvEmptySub);
        tvStatOpen = findViewById(R.id.tvStatOpen);
        tvStatToday = findViewById(R.id.tvStatToday);
        tvStatDone = findViewById(R.id.tvStatDone);
        chipAll = findViewById(R.id.chipAll);
        chipToday = findViewById(R.id.chipToday);
        chipOverdue = findViewById(R.id.chipOverdue);
        progress = findViewById(R.id.progress);
        syncBtn = findViewById(R.id.btnSync);
        emptyState = findViewById(R.id.emptyState);
        heroCard = findViewById(R.id.heroCard);
        heroNum = findViewById(R.id.heroNum);
        heroUnit = findViewById(R.id.heroUnit);
        heroTitle = findViewById(R.id.heroTitle);
        heroDue = findViewById(R.id.heroDue);
        ListView list = findViewById(R.id.list);

        adapter = new TaskAdapter(this, this::openTask);
        list.setAdapter(adapter);
        list.setEmptyView(emptyState);
        heroCard.setOnClickListener(v -> {
            Ui.tap(v);
            openTask(v, heroTask);
        });
        Ui.press(heroCard);

        tvToday.setText(TaskAdapter.todayText());
        Ui.shimmer(tvHeadline, 0xFF121212, 0xFF7A6412, 0xFFC99A12, 0xFF7A6412, 0xFF121212);

        syncBtn.setOnClickListener(v -> {
            Ui.tap(v);
            startSync(true);
        });
        Ui.press(syncBtn);
        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            Ui.tap(v);
            startActivity(new Intent(this, SettingsActivity.class));
            Ui.pageIn(this);
        });

        chipAll.setOnClickListener(v -> pickFilter(0));        chipToday.setOnClickListener(v -> pickFilter(1));
        chipOverdue.setOnClickListener(v -> pickFilter(2));
        Ui.press(chipAll);
        Ui.press(chipToday);
        Ui.press(chipOverdue);

        headerIn();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private void openTask(View view, Task t) {
        if (t == null) return;
        if (t.url == null || t.url.isEmpty()) {
            Toast.makeText(this, "这个作业没有可打开的链接", Toast.LENGTH_SHORT).show();
            return;
        }
        Ui.tap(view);
        Intent i = new Intent(this, WorkActivity.class);
        i.putExtra(Notifications.EXTRA_URL, t.url);
        i.putExtra("title", t.title);
        startActivity(i);
        Ui.pageIn(this);
    }

    /** 首屏渐变卡：最近一个还没截止的作业。 */
    private void bindHero(Task t) {
        heroTask = t;
        if (t == null) {
            heroCard.setVisibility(View.GONE);
            return;
        }
        boolean firstShow = heroCard.getVisibility() != View.VISIBLE;
        heroCard.setVisibility(View.VISIBLE);

        long ms = t.endAt - System.currentTimeMillis();
        String n, u;
        if (ms <= 0) {
            n = "!";
            u = "已截止";
        } else if (ms < 3600_000L) {
            n = String.valueOf(Math.max(1, ms / 60000L));
            u = "分钟后";
        } else if (ms < 24 * 3600_000L) {
            n = String.valueOf((long) Math.ceil(ms / 3600_000.0));
            u = "小时后";
        } else {
            n = String.valueOf((long) Math.ceil(ms / (double) (24 * 3600_000L)));
            u = "天后";
        }
        heroNum.setText(n);
        heroUnit.setText(u);
        heroTitle.setText(t.title == null || t.title.isEmpty() ? "作业" : t.title);
        StringBuilder d = new StringBuilder();
        if (t.courseName != null && !t.courseName.isEmpty()) d.append(t.courseName).append(" · ");
        d.append("截止 ").append(t.deadlineText());
        heroDue.setText(d.toString());

        if (firstShow && !Ui.reduced(this)) {
            heroCard.setAlpha(0f);
            heroCard.setScaleX(0.97f);
            heroCard.setScaleY(0.97f);
            heroCard.setTranslationY(Ui.dp(this, 14));
            heroCard.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setStartDelay(160).setDuration(560).setInterpolator(Ui.ease()).start();
        }
    }

    private void pickFilter(int f) {
        if (filter == f) return;
        filter = f;
        Ui.tap(chipAll);
        applyChipStyles();
        adapter.setFilter(f);
    }

    private void applyChipStyles() {
        TextView[] chips = {chipAll, chipToday, chipOverdue};
        for (int i = 0; i < chips.length; i++) {
            boolean on = i == filter;
            chips[i].setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_pill_ghost);
            chips[i].setTextColor(getColor(on ? R.color.surface : R.color.ink_2));
        }
    }

    private void headerIn() {
        View[] parts = {tvToday, tvHeadline, tvLastSync, tvStatOpen, tvStatToday, tvStatDone};
        for (int k = 0; k < parts.length; k++) {
            View p = parts[k];
            if (Ui.reduced(this)) {
                p.setAlpha(1f);
                continue;
            }
            p.setAlpha(0f);
            p.setTranslationY(Ui.dp(this, 16));
            p.animate().alpha(1f).translationY(0f)
                    .setStartDelay(90 + k * 55L)
                    .setDuration(520)
                    .setInterpolator(Ui.ease())
                    .start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Prefs.hasCookies(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (aurora != null) aurora.resume();
        reload();
        if (Prefs.needFirstSync(this)) startSync(false);
    }

    private void reload() {
        List<Task> all = Prefs.tasks(this);
        List<Task> show = new ArrayList<>();
        boolean showDone = Prefs.showDone(this);
        for (Task t : all) {
            if (!t.done() || showDone) show.add(t);
        }
        adapter.setItems(show);
        applyChipStyles();

        int open = 0;
        int today = 0;
        int done = 0;
        Task next = null;
        Task overdue = null;
        long now = System.currentTimeMillis();
        for (Task t : all) {
            if (t.done()) {
                done++;
                continue;
            }
            open++;
            if (t.endAt > 0 && t.endAt - now <= 24 * 3600_000L) today++;
            if (t.endAt > now && (next == null || t.endAt < next.endAt)) next = t;
            if (t.endAt > 0 && t.endAt <= now && (overdue == null || t.endAt > overdue.endAt)) {
                overdue = t;
            }
        }

        tvHeadline.setText(open == 0 ? "都交了" : "还有 " + open + " 个作业没交");
        Ui.shimmer(tvHeadline, 0xFF121212, 0xFF7A6412, 0xFFC99A12, 0xFF7A6412, 0xFF121212);
        tvEmptyTitle.setText("全部交完了");
        tvEmptySub.setText("没有未交作业。下次同步会自动再检查一遍。");

        animateStat(tvStatOpen, prevOpen, open);
        animateStat(tvStatToday, prevToday, today);
        animateStat(tvStatDone, prevDone, done);
        prevOpen = open;
        prevToday = today;
        prevDone = done;

        bindHero(next != null ? next : overdue);

        StringBuilder sub = new StringBuilder();
        long ls = Prefs.lastSync(this);
        if (ls > 0) {
            sub.append("上次同步 ")
                    .append(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(ls)))
                    .append(" · ").append(Prefs.courseCount(this)).append(" 门课程");
        } else {
            sub.append(getString(R.string.status_idle));
        }
        if (!Prefs.lastError(this).isEmpty()) sub.append("\n上次同步失败，检查下网络");
        syncLine = sub.toString();
        nextTask = next;
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    private void animateStat(TextView tv, int prev, int now) {
        if (prev < 0) {
            tv.setText(String.valueOf(now));
        } else {
            Ui.countTo(tv, prev, now);
        }
    }

    private String syncLine = "";
    private Task nextTask;
    private final android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            String s = syncLine;
            if (nextTask != null) {
                s = s + "\n最近：" + nextTask.title + " · " + nextTask.remainText();
            }
            tvLastSync.setText(s);
            if (nextTask != null) ticker.postDelayed(this, 20000);
        }
    };

    private void startSync(boolean userTriggered) {
        if (!syncing.compareAndSet(false, true)) return;
        syncBtn.setEnabled(false);
        syncBtn.setText("正在同步…");
        progress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                SyncEngine.Result r = SyncEngine.sync(getApplicationContext(),
                        msg -> runOnUiThread(() -> tvLastSync.setText(msg)));
                runOnUiThread(() -> {
                    syncing.set(false);
                    syncBtn.setEnabled(true);
                    syncBtn.setText(R.string.btn_sync);
                    progress.setVisibility(View.GONE);
                    reload();
                    if (r.first) {
                        Toast.makeText(this, "已适配 " + r.courses + " 门课程，" + r.unsubmitted + " 个未交作业", Toast.LENGTH_LONG).show();
                    } else if (r.added > 0) {
                        Toast.makeText(this, "新增 " + r.added + " 个作业", Toast.LENGTH_SHORT).show();
                    } else if (userTriggered) {
                        Toast.makeText(this, "已是最新", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Http.AuthException e) {
                runOnUiThread(() -> {
                    syncing.set(false);
                    syncBtn.setEnabled(true);
                    syncBtn.setText(R.string.btn_sync);
                    progress.setVisibility(View.GONE);
                    showAuthDialog();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    syncing.set(false);
                    syncBtn.setEnabled(true);
                    syncBtn.setText(R.string.btn_sync);
                    progress.setVisibility(View.GONE);
                    tvLastSync.setText("同步失败：" + (e.getMessage() == null ? "网络错误" : e.getMessage()));
                    if (userTriggered) Toast.makeText(this, "同步失败，检查下网络", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showAuthDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.auth_expired)
                .setMessage("需要重新登录学习通才能继续同步。")
                .setPositiveButton(R.string.relogin, (d, w) -> {
                    Prefs.setCookies(this, "[]");
                    Http.reset();
                    Reminders.cancelAll(this);
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        if (aurora != null) aurora.pause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Ui.pageOut(this);
    }

    @Override
    protected void onDestroy() {
        ticker.removeCallbacks(tick);
        io.shutdownNow();
        super.onDestroy();
    }
}
