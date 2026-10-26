package com.keepup.course;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/**
 * 启动页：光环扫描 → 对勾 → 文字上浮。整段约 1.6s，之后按登录状态跳转。
 * 系统关掉动画时直接呈现终态，不做位移。
 */
public class SplashActivity extends Activity {

    private static final long TOTAL = 1620L;

    private SplashMarkView mark;
    private TextView tvName, tvTag, tvFooter;
    private View spine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mark = findViewById(R.id.mark);
        tvName = findViewById(R.id.tvName);
        tvTag = findViewById(R.id.tvTag);
        tvFooter = findViewById(R.id.tvFooter);
        spine = findViewById(R.id.spine);

        boolean reduced = Ui.reduced(this);
        if (reduced) {
            mark.setProgress(1f);
            tvName.setAlpha(1f);
            tvTag.setAlpha(1f);
            tvFooter.setAlpha(1f);
        } else {
            playIntro();
        }

        tvName.postDelayed(this::route, reduced ? 360L : TOTAL);
    }

    private void playIntro() {
        PathInterpolator ease = Ui.ease();

        // 光环：0 → 1，900ms
        android.animation.ValueAnimator sweep = android.animation.ValueAnimator.ofFloat(0f, 1f);
        sweep.setDuration(900);
        sweep.setInterpolator(ease);
        sweep.addUpdateListener(a -> mark.setProgress((float) a.getAnimatedValue()));
        sweep.start();

        // 标记本身：缩放 0.84 → 1 + 淡入
        mark.setAlpha(0f);
        mark.setScaleX(0.84f);
        mark.setScaleY(0.84f);
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(520).setInterpolator(ease).start();

        // 名字：上移 16dp + 淡入 + 字距收紧
        tvName.setAlpha(0f);
        tvName.setTranslationY(Ui.dp(this, 18));
        tvName.setLetterSpacing(0.14f);
        tvName.animate().alpha(1f).translationY(0f)
                .setStartDelay(320).setDuration(620).setInterpolator(ease).start();
        android.animation.ValueAnimator ls = android.animation.ValueAnimator.ofFloat(0.14f, 0.06f);
        ls.setStartDelay(320);
        ls.setDuration(620);
        ls.setInterpolator(ease);
        ls.addUpdateListener(a -> tvName.setLetterSpacing((float) a.getAnimatedValue()));
        ls.start();

        // 副标题与页脚：最后到位
        tvTag.setAlpha(0f);
        tvTag.setTranslationY(Ui.dp(this, 10));
        tvTag.animate().alpha(1f).translationY(0f)
                .setStartDelay(560).setDuration(520).setInterpolator(ease).start();

        tvFooter.setAlpha(0f);
        tvFooter.animate().alpha(1f).setStartDelay(760).setDuration(520).start();

        // 顶部进度：跟着扫描走
        spine.setScaleX(0f);
        spine.setPivotX(0f);
        spine.animate().scaleX(1f).setDuration(1400).setInterpolator(ease).start();
    }

    private void route() {
        if (isFinishing()) return;
        if (!Prefs.agreed(this)) {
            startActivity(new Intent(this, AgreementActivity.class));
            Ui.pageIn(this);
            finish();
            return;
        }
        Intent next = Prefs.hasCookies(this)
                ? new Intent(this, MainActivity.class)
                : new Intent(this, LoginActivity.class);
        startActivity(next);
        Ui.pageIn(this);
        finish();
    }

    @Override
    public void onBackPressed() {
        // 启动页不允许返回
    }
}
