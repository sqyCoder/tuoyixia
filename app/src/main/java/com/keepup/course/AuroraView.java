package com.keepup.course;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 极光氛围底：几颗柔光球在大范围里缓慢漂移、呼吸。
 * 用 RadialGradient + Matrix 位移，只改矩阵不重建 shader，30fps 也不掉帧。
 * 尊重系统「移除动画」：关掉动画时只画一帧静态极光。
 */
public class AuroraView extends View {

    private static final class Blob {
        final int color;
        final float cx, cy, radius;
        final float ampX, ampY;
        final float speed;
        final float phase;
        final Matrix matrix = new Matrix();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader shader;

        Blob(int color, float cx, float cy, float radius, float ampX, float ampY,
             float speed, float phase) {
            this.color = color;
            this.cx = cx;
            this.cy = cy;
            this.radius = radius;
            this.ampX = ampX;
            this.ampY = ampY;
            this.speed = speed;
            this.phase = phase;
        }
    }

    private final Blob[] blobs = new Blob[]{
            new Blob(0x5CEBE2EC, 0.12f, 0.06f, 0.62f, 0.05f, 0.035f, 1.00f, 0.0f),
            new Blob(0x66F0E2D8, 0.90f, 0.14f, 0.58f, 0.045f, 0.05f, 0.78f, 1.7f),
            new Blob(0x57DEE2F0, 0.86f, 0.92f, 0.55f, 0.05f, 0.04f, 0.62f, 3.1f),
            new Blob(0x4DF3E8EA, 0.30f, 1.02f, 0.50f, 0.06f, 0.03f, 0.88f, 4.4f),
            new Blob(0x33E7E3F0, 0.55f, 0.46f, 0.42f, 0.10f, 0.07f, 0.55f, 2.3f),
    };

    private final Paint base = new Paint();
    private float phase = 0f;
    private long lastDraw = 0L;
    private android.animation.ValueAnimator anim;

    public AuroraView(Context c) {
        this(c, null);
    }

    public AuroraView(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        base.setShader(new android.graphics.LinearGradient(0, 0, w * 0.4f, h,
                Color.parseColor("#FDFBF4"), Color.parseColor("#F5F0DE"), Shader.TileMode.CLAMP));
        for (Blob b : blobs) {
            float r = Math.max(w, h) * b.radius;
            b.shader = new RadialGradient(0, 0, r,
                    new int[]{b.color, b.color & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            b.paint.setShader(b.shader);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        canvas.drawRect(0, 0, w, h, base);
        for (Blob b : blobs) {
            float r = Math.max(w, h) * b.radius;
            float cx = w * b.cx + (float) Math.sin(phase * b.speed + b.phase) * w * b.ampX;
            float cy = h * b.cy + (float) Math.cos(phase * b.speed * 0.8f + b.phase) * h * b.ampY;
            b.matrix.reset();
            b.matrix.setTranslate(cx, cy);
            b.shader.setLocalMatrix(b.matrix);
            canvas.drawCircle(cx, cy, r, b.paint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Ui.reduced(getContext())) return;
        anim = android.animation.ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        anim.setDuration(26000);
        anim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        anim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            // 光球移动极慢，没必要 60fps 重绘，压到 ~13fps 省电
            long now = System.currentTimeMillis();
            if (now - lastDraw < 75L) return;
            lastDraw = now;
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
        super.onDetachedFromWindow();
    }

    /** 页面切走时暂停，回来再续上，别在后台白烧电。 */
    public void pause() {
        if (anim != null) anim.pause();
    }

    public void resume() {
        if (anim != null) anim.resume();
    }
}
