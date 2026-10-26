package com.keepup.course;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * 启动页的标记：一圈渐变光环被「扫」出来，随后勾出对勾，末端留一颗薄荷色光点。
 * 只暴露一个 progress，动画由 SplashActivity 驱动，逻辑集中在这里。
 */
public class SplashMarkView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Matrix m = new Matrix();

    private float progress = 0f;

    public SplashMarkView(Context c) {
        this(c, null);
    }

    public SplashMarkView(Context c, AttributeSet a) {
        super(c, a);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeJoin(Paint.Join.ROUND);
        tick.setColor(Color.WHITE);
        dot.setColor(Color.WHITE);
        glow.setStyle(Paint.Style.FILL);
    }

    public void setProgress(float p) {
        this.progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float size = Math.min(w, h);
        float stroke = size * 0.075f;
        ring.setStrokeWidth(stroke);
        tick.setStrokeWidth(stroke);
        float pad = stroke * 1.6f;
        oval.set(pad, pad, w - pad, h - pad);

        // 光环用扫描渐变：从紫→品红→薄荷，扫一圈就是极光
        SweepGradient sg = new SweepGradient(w / 2f, h / 2f,
                new int[]{0xFFE9B824, 0xFFF5CE4E, 0xFFFFFFFF, 0xFFE9B824, 0xFFF5CE4E},
                new float[]{0f, 0.28f, 0.52f, 0.78f, 1f});
        m.reset();
        m.setRotate(-90, w / 2f, h / 2f);
        sg.setLocalMatrix(m);
        ring.setShader(sg);

        glow.setShader(new RadialGradient(w / 2f, h / 2f, size * 0.62f,
                new int[]{0x55E9B824, 0x22E9B824, 0x00E9B824}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h);

        float breathe = 0.92f + 0.08f * progress;
        canvas.save();
        canvas.scale(breathe, breathe, w / 2f, h / 2f);
        canvas.drawCircle(w / 2f, h / 2f, size * 0.62f, glow);

        // 环：留 8° 缺口，progress 从 0 扫到 360
        float sweep = 352f * progress;
        canvas.drawArc(oval, -90f, sweep, false, ring);

        // 末端光点
        if (progress > 0.06f) {
            double end = Math.toRadians(-90f + sweep);
            float r = oval.width() / 2f;
            float cx = oval.centerX() + (float) Math.cos(end) * r;
            float cy = oval.centerY() + (float) Math.sin(end) * r;
            dot.setAlpha((int) (255 * Math.min(1f, progress * 3f)));
            canvas.drawCircle(cx, cy, size * 0.052f, dot);
        }

        // 对勾：progress 后 45% 才出现，用极细的“画线”比例控制
        float t = (progress - 0.45f) / 0.55f;
        if (t > 0f) {
            t = Math.min(1f, t);
            float cx = w / 2f;
            float cy = h / 2f;
            float s = size * 0.30f;
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(cx - s * 0.72f, cy + s * 0.04f);
            p.lineTo(cx - s * 0.18f, cy + s * 0.56f);
            p.lineTo(cx + s * 0.86f, cy - s * 0.52f);

            tick.setAlpha((int) (255 * Math.min(1f, t * 2f)));
            if (t < 1f) {
                android.graphics.PathMeasure pm = new android.graphics.PathMeasure(p, false);
                android.graphics.Path part = new android.graphics.Path();
                pm.getSegment(0f, pm.getLength() * t, part, true);
                canvas.drawPath(part, tick);
            } else {
                canvas.drawPath(p, tick);
            }
        }
        canvas.restore();
    }
}
