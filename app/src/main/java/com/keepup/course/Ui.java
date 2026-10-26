package com.keepup.course;

import android.app.Activity;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/**
 * 统一的动效语言：
 * 主缓动 cubic-bezier(0.16, 1, 0.3, 1)（出场快、收尾长，最像"高级"的那种减速）
 * 入场位移 18dp + 缩放 0.97 + 淡入，错落 42ms
 * 按压 0.96，配触感
 */
public final class Ui {
    private Ui() {
    }

    private static PathInterpolator ease;

    public static PathInterpolator ease() {
        if (ease == null) ease = new PathInterpolator(0.16f, 1f, 0.3f, 1f);
        return ease;
    }

    public static PathInterpolator easeInOut() {
        return new PathInterpolator(0.65f, 0f, 0.35f, 1f);
    }

    public static int dp(Context c, float v) {
        return Math.round(c.getResources().getDisplayMetrics().density * v);
    }

    /** 系统关闭动画（无障碍里的「移除动画」）时不做位移，直接出结果。 */
    public static boolean reduced(Context c) {
        try {
            return android.provider.Settings.Global.getFloat(
                    c.getContentResolver(),
                    android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    /** 列表项入场：位移 + 轻微放大 + 淡入，按序号错开，一屏内铺开。 */
    public static void staggerIn(View v, int index) {
        v.animate().cancel();
        if (reduced(v.getContext())) {
            v.setAlpha(1f);
            v.setTranslationY(0f);
            v.setScaleX(1f);
            v.setScaleY(1f);
            return;
        }
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 18));
        v.setScaleX(0.97f);
        v.setScaleY(0.97f);
        v.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(Math.min(index * 42L, 300L))
                .setDuration(460)
                .setInterpolator(ease())
                .start();
    }

    public static void reset(View v) {
        v.animate().cancel();
        v.setAlpha(1f);
        v.setTranslationY(0f);
        v.setScaleX(1f);
        v.setScaleY(1f);
    }

    /** 弹层：从底部推上来，长尾减速，同时让内容轻微错落。 */
    public static void sheetIn(View v) {
        if (reduced(v.getContext())) {
            v.setTranslationY(0f);
            v.setAlpha(1f);
            return;
        }
        v.setTranslationY(dp(v.getContext(), 260));
        v.setAlpha(0.6f);
        v.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(380)
                .setInterpolator(ease())
                .start();
    }

    /** 按压：按下缩到 0.96 并给一次轻触感，松手弹回。 */
    public static void press(final View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f)
                            .setDuration(120).setInterpolator(ease()).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f)
                            .setDuration(300).setInterpolator(ease()).start();
                    break;
            }
            return false;
        });
    }

    public static void tap(View v) {
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 23) {
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        } else {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    /**
     * 渐变文字：给 TextView 的画笔挂一个横向 LinearGradient。
     * 宽度依赖测量结果，所以放到 post 里挂，并在尺寸变化后重挂。
     */
    public static void gradientText(final TextView tv, final int... colors) {
        if (tv == null) return;
        tv.post(() -> {
            int w = tv.getWidth() - tv.getPaddingLeft() - tv.getPaddingRight();
            if (w <= 0) return;
            tv.getPaint().setShader(new LinearGradient(
                    0, 0, w * 0.92f, tv.getHeight() * 0.6f,
                    colors, null, Shader.TileMode.CLAMP));
            tv.invalidate();
        });
    }

    private static final java.util.WeakHashMap<TextView, android.animation.ValueAnimator> SHIMMERS =
            new java.util.WeakHashMap<>();

    /**
     * 流动渐变标题：渐变沿水平方向缓慢平移，让大标题有"活着"的光泽。
     * 6s 一个循环、位移幅度只有半个字宽，是氛围而不是动画表演。
     */
    public static void shimmer(final TextView tv, final int... colors) {
        if (tv == null) return;
        tv.post(() -> {
            int w = tv.getWidth();
            if (w <= 0) return;
            final LinearGradient lg = new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP);
            tv.getPaint().setShader(lg);
            tv.invalidate();
            if (reduced(tv.getContext())) return;

            // 重复调用（reload / resume）时先把上一条停掉，避免多层动画叠加
            android.animation.ValueAnimator old = SHIMMERS.remove(tv);
            if (old != null) old.cancel();

            final android.graphics.Matrix m = new android.graphics.Matrix();
            android.animation.ValueAnimator a =
                    android.animation.ValueAnimator.ofFloat(-w * 0.45f, w * 0.45f);
            a.setDuration(6000);
            a.setStartDelay(800);
            a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.LinearInterpolator());
            a.addUpdateListener(an -> {
                m.setTranslate((float) an.getAnimatedValue(), 0);
                lg.setLocalMatrix(m);
                tv.invalidate();
            });
            SHIMMERS.put(tv, a);
            a.start();
        });
    }

    /** 数字滚动：用于首页统计与倒计时变化，避免"数字突然跳"。 */    public static void countTo(final TextView tv, final int from, final int to) {
        if (tv == null) return;
        if (from == to || reduced(tv.getContext())) {
            tv.setText(String.valueOf(to));
            return;
        }
        android.animation.ValueAnimator a =
                android.animation.ValueAnimator.ofInt(from, to);
        a.setDuration(560);
        a.setInterpolator(ease());
        a.addUpdateListener(v -> tv.setText(String.valueOf(v.getAnimatedValue())));
        a.start();
    }

    /** 持续呼吸：紧急状态的小光点用。 */
    public static void breathe(final View v, float min, float max) {
        if (v == null) return;
        if (reduced(v.getContext())) {
            v.setAlpha(max);
            return;
        }
        android.animation.ValueAnimator a =
                android.animation.ValueAnimator.ofFloat(max, min);
        a.setDuration(1500);
        a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        a.setInterpolator(easeInOut());
        a.addUpdateListener(an -> v.setAlpha((float) an.getAnimatedValue()));
        a.start();
    }

    public static void pageIn(Activity a) {
        a.overridePendingTransition(R.anim.page_in, R.anim.page_hold);
    }

    public static void pageOut(Activity a) {
        a.overridePendingTransition(R.anim.page_hold, R.anim.page_out);
    }
}
