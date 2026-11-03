package com.simple.carlauncher;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Vista de velocímetro de arco circular estilo deportivo de alta gama (0 a 200 km/h)
 * con brillo neón cyan y lectura digital central exacta.
 */
public class ArcSpeedometerView extends View {

    private static final float START_ANGLE = 145f;
    private static final float SWEEP_ANGLE = 250f;
    private static final float MAX_SPEED = 200f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();
    private final float density;

    private float currentSpeed = 0f;
    private float targetSpeed = 0f;

    public ArcSpeedometerView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(3.5f * density);
        trackPaint.setColor(0x2200E5FF);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(5.5f * density);
        arcPaint.setColor(0xFF00E5FF);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(12f * density);
        glowPaint.setColor(0x6600E5FF);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        try {
            glowPaint.setMaskFilter(new BlurMaskFilter(8f * density, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xCCFFFFFF);
        textPaint.setTextSize(11f * density);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        speedNumberPaint.setTextAlign(Paint.Align.CENTER);
        speedNumberPaint.setColor(Color.WHITE);
        speedNumberPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        speedNumberPaint.setTextSize(52f * density);

        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setColor(0xAAFFFFFF);
        unitPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        unitPaint.setTextSize(13f * density);

        wingPaint.setStyle(Paint.Style.STROKE);
        wingPaint.setStrokeWidth(2f * density);
        wingPaint.setColor(0x9900E5FF);
        wingPaint.setStrokeCap(Paint.Cap.ROUND);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /**
     * Establece la velocidad objetivo en km/h (0 a 200).
     */
    public void setSpeed(float speed) {
        targetSpeed = Math.max(0f, Math.min(MAX_SPEED, speed));
        invalidate();
    }

    public float getSpeed() {
        return targetSpeed;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Suavizado dinámico de la aguja / arco hacia la velocidad objetivo
        float diff = targetSpeed - currentSpeed;
        if (Math.abs(diff) > 0.2f) {
            currentSpeed += diff * 0.18f;
            postInvalidateOnAnimation();
        } else {
            currentSpeed = targetSpeed;
        }

        float cx = w * 0.5f;
        float cy = h * 0.65f;
        float radius = Math.min(w * 0.38f, h * 0.56f);

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // 1. Pista de arco de fondo
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        // 2. Arco de velocidad activo con gradiente cyan a azul neón
        float progressFraction = Math.max(0.01f, currentSpeed / MAX_SPEED);
        float activeSweep = progressFraction * SWEEP_ANGLE;

        LinearGradient grad = new LinearGradient(
                cx - radius, cy, cx + radius, cy,
                new int[]{0xFF00E5FF, 0xFF0091EA, 0xFF2979FF},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        arcPaint.setShader(grad);
        glowPaint.setShader(grad);

        canvas.drawArc(arcRect, START_ANGLE, activeSweep, false, glowPaint);
        canvas.drawArc(arcRect, START_ANGLE, activeSweep, false, arcPaint);

        // 3. Ticks y numeración (0, 20, 40, ... 200)
        float tickOuter = radius - 7f * density;
        float tickMajorInner = tickOuter - 10f * density;
        float tickMinorInner = tickOuter - 5f * density;
        float textRadius = tickMajorInner - 11f * density;

        for (int v = 0; v <= 200; v += 10) {
            float frac = (float) v / MAX_SPEED;
            double angleRad = Math.toRadians(START_ANGLE + frac * SWEEP_ANGLE);
            float cos = (float) Math.cos(angleRad);
            float sin = (float) Math.sin(angleRad);

            boolean isMajor = (v % 20 == 0);
            float inner = isMajor ? tickMajorInner : tickMinorInner;

            float x1 = cx + cos * inner;
            float y1 = cy + sin * inner;
            float x2 = cx + cos * tickOuter;
            float y2 = cy + sin * tickOuter;

            boolean active = v <= currentSpeed;
            tickPaint.setStrokeWidth((isMajor ? 2.2f : 1.2f) * density);
            tickPaint.setColor(active ? 0xFF00E5FF : (isMajor ? 0x99FFFFFF : 0x44FFFFFF));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);

            if (isMajor) {
                float tx = cx + cos * textRadius;
                float ty = cy + sin * textRadius + (4f * density);
                textPaint.setColor(active ? Color.WHITE : 0xAAFFFFFF);
                canvas.drawText(String.valueOf(v), tx, ty, textPaint);
            }
        }

        // 4. Lectura digital central
        int displaySpeed = Math.round(currentSpeed);
        canvas.drawText(String.valueOf(displaySpeed), cx, cy - 2f * density, speedNumberPaint);
        canvas.drawText("km/h", cx, cy + 18f * density, unitPaint);

        // 5. Alas de acento aerodinámicas inferiores (igual al diseño del mockup)
        Path leftWing = new Path();
        leftWing.moveTo(cx - 36f * density, cy + 24f * density);
        leftWing.quadTo(cx - 70f * density, cy + 28f * density, cx - 120f * density, cy + 20f * density);
        canvas.drawPath(leftWing, wingPaint);

        Path rightWing = new Path();
        rightWing.moveTo(cx + 36f * density, cy + 24f * density);
        rightWing.quadTo(cx + 70f * density, cy + 28f * density, cx + 120f * density, cy + 20f * density);
        canvas.drawPath(rightWing, wingPaint);
    }
}

