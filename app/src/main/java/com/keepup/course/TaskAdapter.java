package com.keepup.course;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 极光列表：每条作业是一块玻璃，左侧一根渐变导轨 + 大号倒计时。
 * 紧急/逾期的行换成珊瑚导轨，数字用珊瑚→品红渐变，其余保持墨色。
 */
public class TaskAdapter extends BaseAdapter {
    private static final int TYPE_HEAD = 0;
    private static final int TYPE_TASK = 1;

    private static final int[] GRAD_URGENT = {0xFFC2410C, 0xFFE8590C};
    private static final int[] GRAD_DONE = {0xFF8C8C84, 0xFF6F6F68};

    public interface OnTaskClick {
        void onTask(View row, Task task);
    }

    private final Context ctx;
    private final OnTaskClick listener;
    private final List<Object> rows = new ArrayList<>();
    private final List<Task> source = new ArrayList<>();
    private boolean animateNext = true;
    private int filter = 0; // 0 全部 1 今天 2 已截止

    public TaskAdapter(Context ctx, OnTaskClick listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setItems(List<Task> list) {
        source.clear();
        if (list != null) source.addAll(list);
        rebuild();
    }

    public void setFilter(int f) {
        if (filter == f) return;
        filter = f;
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        long now = System.currentTimeMillis();
        String bucket = null;
        for (Task t : source) {
            if (!pass(t, now)) continue;
            String b = bucketOf(t, now);
            if (!b.equals(bucket)) {
                bucket = b;
                rows.add(b);
            }
            rows.add(t);
        }
        animateNext = true;
        notifyDataSetChanged();
    }

    private boolean pass(Task t, long now) {
        if (filter == 1) return !t.done() && t.endAt > 0 && t.endAt - now <= 24 * 3600_000L;
        if (filter == 2) return !t.done() && t.endAt > 0 && t.endAt <= now;
        return true;
    }

    private static String bucketOf(Task t, long now) {
        if (t.done()) return "已完成";
        if (t.endAt <= 0) return "没有截止时间";
        long ms = t.endAt - now;
        if (ms <= 0) return "已截止";
        if (ms <= 24 * 3600_000L) return "今天截止";
        if (ms <= 3 * 24 * 3600_000L) return "三天内";
        if (ms <= 7 * 24 * 3600_000L) return "本周内";
        return "更晚";
    }

    public Task getItemAt(int position) {
        if (position < 0 || position >= rows.size()) return null;
        Object o = rows.get(position);
        return o instanceof Task ? (Task) o : null;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        Object o = rows.get(position);
        return o instanceof Task ? ((Task) o).workId.hashCode() : 0;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof Task ? TYPE_TASK : TYPE_HEAD;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) == TYPE_TASK;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object o = rows.get(position);
        if (o instanceof String) return headView((String) o, convertView, position);

        View v = convertView;
        if (v == null || v.getTag(R.id.rowRoot) == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_task, parent, false);
            v.setTag(R.id.rowRoot, Boolean.TRUE);
        }
        bindTask(v, (Task) o, position);
        if (listener != null) {
            final Task task = (Task) o;
            View row = v.findViewById(R.id.rowRoot);
            if (row != null) row.setOnClickListener(x -> listener.onTask(x, task));
        }
        if (animateNext && position < 7) Ui.staggerIn(v, position);
        else Ui.reset(v);
        if (animateNext && position >= 6) animateNext = false;
        return v;
    }

    private View headView(String title, View convertView, int position) {
        TextView tv;
        if (convertView instanceof TextView) {
            tv = (TextView) convertView;
        } else {
            tv = new TextView(ctx);
            tv.setTextSize(11f);
            tv.setTextColor(ctx.getColor(R.color.ink_3));
            tv.setLetterSpacing(0.16f);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 20), Ui.dp(ctx, 8), Ui.dp(ctx, 10));
        }
        // 组头带一个数量，信息量更足
        int n = 0;
        for (int i = position + 1; i < rows.size(); i++) {
            if (rows.get(i) instanceof Task) n++;
            else break;
        }
        tv.setText(title + "  " + n);
        return tv;
    }

    private void bindTask(View v, Task t, int position) {
        TextView num = v.findViewById(R.id.tvNum);
        TextView unit = v.findViewById(R.id.tvUnit);
        TextView title = v.findViewById(R.id.tvTitle);
        TextView status = v.findViewById(R.id.tvStatus);
        TextView course = v.findViewById(R.id.tvCourse);
        TextView due = v.findViewById(R.id.tvDue);
        View root = v.findViewById(R.id.rowRoot);
        View rail = v.findViewById(R.id.rail);

        long now = System.currentTimeMillis();
        boolean done = t.done();
        boolean overdue = !done && t.endAt > 0 && t.endAt <= now;
        boolean urgent = !done && t.endAt > 0 && t.endAt - now <= 24 * 3600_000L;

        if (done) {
            root.setBackgroundResource(R.drawable.bg_glass_row_done);
            rail.setVisibility(View.GONE);
        } else if (urgent) {
            root.setBackgroundResource(R.drawable.bg_glass_row_urgent);
            rail.setVisibility(View.VISIBLE);
            rail.setBackgroundResource(R.drawable.bg_rail_urgent);
        } else {
            root.setBackgroundResource(R.drawable.bg_glass_row);
            rail.setVisibility(View.VISIBLE);
            rail.setBackgroundResource(R.drawable.bg_rail);
        }

        title.setTextColor(ctx.getColor(done ? R.color.ink_3 : R.color.ink));
        course.setTextColor(ctx.getColor(done ? R.color.ink_3 : R.color.violet_deep));
        due.setTextColor(ctx.getColor(R.color.ink_3));
        unit.setTextColor(ctx.getColor(done ? R.color.ink_3 : (urgent ? R.color.coral : R.color.ink_3)));

        if (done) setNumGradient(num, GRAD_DONE);
        else if (urgent) setNumGradient(num, GRAD_URGENT);
        else setNumSolid(num, ctx.getColor(R.color.ink));

        // 剩余时间：数字最大，单位最小
        String n;
        String u;
        if (done) {
            n = "✓";
            u = "已交";
        } else if (overdue) {
            n = "!";
            u = "已截止";
        } else if (t.endAt <= 0) {
            n = "—";
            u = "无期限";
        } else {
            long ms = t.endAt - now;
            if (ms < TimeUnit.HOURS.toMillis(1)) {
                n = String.valueOf(Math.max(1, TimeUnit.MILLISECONDS.toMinutes(ms)));
                u = "分钟后";
            } else if (ms < TimeUnit.DAYS.toMillis(1)) {
                n = String.valueOf((long) Math.ceil(ms / (double) TimeUnit.HOURS.toMillis(1)));
                u = "小时后";
            } else {
                n = String.valueOf((long) Math.ceil(ms / (double) TimeUnit.DAYS.toMillis(1)));
                u = "天后";
            }
        }
        num.setText(n);
        unit.setText(u);

        title.setText(t.title == null || t.title.isEmpty() ? "作业" : t.title);
        course.setText(t.courseName == null ? "" : t.courseName);
        course.setVisibility(t.courseName == null || t.courseName.isEmpty() ? View.GONE : View.VISIBLE);

        if (done) {
            status.setText("已交");
            status.setTextColor(ctx.getColor(R.color.mint_deep));
        } else if (overdue) {
            status.setText("已截止");
            status.setTextColor(ctx.getColor(R.color.coral));
        } else {
            status.setText(t.status == null || t.status.isEmpty() ? "未交" : t.status);
            status.setTextColor(ctx.getColor(urgent ? R.color.coral : R.color.ink_3));
        }

        String dl = t.deadlineText();
        if (dl.isEmpty()) {
            due.setText(done ? "" : "没有截止时间");
        } else {
            due.setText("截止 " + dl + (done ? "" : " · " + t.remainText()));
        }
    }

    private void setNumGradient(TextView tv, int[] colors) {
        tv.setTextColor(colors[0]);
        Ui.gradientText(tv, colors);
    }

    private void setNumSolid(TextView tv, int color) {
        tv.getPaint().setShader(null);
        tv.setTextColor(color);
        tv.invalidate();
    }

    public static String todayText() {
        SimpleDateFormat f = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);
        return f.format(new Date());
    }
}
