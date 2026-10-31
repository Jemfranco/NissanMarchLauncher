package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * Vista de animación premium con ondas sonoras neón, barras ecualizadoras
 * y aura de luz ambiental viva para el reproductor de música.
 */
public class WaveVisualizerView extends View {

    private static final int BAR_COUNT = 36;

    private final Paint wavePaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wavePaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path path1 = new Path();
    private final Path path2 = new Path();
    private final Path linePath = new Path();

    private final float[] barHeights = new float[BAR_COUNT];
    private final float[] barPeaks = new float[BAR_COUNT];
    private final float[] barTargets = new float[BAR_COUNT];

    private final Random random = new Random();

    private float phase = 0f;
    private boolean isPlaying = true;
    private boolean isRunning = false;
    private long lastTime = 0;

    public WaveVisualizerView(Context context) {
        super(context);
        init();
    }

    public WaveVisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wavePaint1.setStyle(Paint.Style.FILL);
        wavePaint2.setStyle(Paint.Style.FILL);

        waveLinePaint.setStyle(Paint.Style.STROKE);
        waveLinePaint.setStrokeWidth(3f);
        waveLinePaint.setColor(0xFF00E5FF);

        barPaint.setStyle(Paint.Style.FILL);
        peakPaint.setStyle(Paint.Style.FILL);
        peakPaint.setColor(0xFF00E5FF);

        glowPaint.setStyle(Paint.Style.FILL);

        for (int i = 0; i < BAR_COUNT; i++) {
            barHeights[i] = 10f;
            barPeaks[i] = 12f;
            barTargets[i] = 30f + random.nextFloat() * 60f;
        }
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        invalidate();
    }

    public void startAnimation() {
        if (!isRunning) {
            isRunning = true;
            lastTime = SystemClock.elapsedRealtime();
            postInvalidateOnAnimation();
        }
    }

    public void stopAnimation() {
        isRunning = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            // Gradientes Neón vivos: Cian a Púrpura y Magenta
            LinearGradient g1 = new LinearGradient(0, h * 0.3f, w, h,
                    new int[]{0x4D00E5FF, 0x337C4DFF, 0x1A0D47A1}, null, Shader.TileMode.CLAMP);
            wavePaint1.setShader(g1);

            LinearGradient g2 = new LinearGradient(0, h * 0.4f, w, h,
                    new int[]{0x40E040FB, 0x2E651FFF, 0x11000000}, null, Shader.TileMode.CLAMP);
            wavePaint2.setShader(g2);

            LinearGradient bg = new LinearGradient(0, h, 0, h - 220f,
                    new int[]{0xCC00E5FF, 0x99D500F9, 0x332979FF}, null, Shader.TileMode.CLAMP);
            barPaint.setShader(bg);

            RadialGradient rg = new RadialGradient(w * 0.28f, h * 0.45f, w * 0.45f,
                    new int[]{0x3300E5FF, 0x227C4DFF, 0x00000000}, null, Shader.TileMode.CLAMP);
            glowPaint.setShader(rg);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        long now = SystemClock.elapsedRealtime();
        float delta = lastTime == 0 ? 0.016f : (now - lastTime) / 1000f;
        lastTime = now;
        if (delta > 0.05f) delta = 0.05f;

        // 1. Resplandor de fondo ambiental
        canvas.drawRect(0, 0, w, h, glowPaint);

        // 2. Ondas Neón fluidas
        float speed = isPlaying ? 2.2f : 0.6f;
        phase += speed * delta;

        float baseY = h * 0.72f;
        float amplitude1 = isPlaying ? h * 0.12f : h * 0.04f;
        float amplitude2 = isPlaying ? h * 0.08f : h * 0.03f;

        path1.reset();
        path1.moveTo(0, h);
        path1.lineTo(0, baseY);

        path2.reset();
        path2.moveTo(0, h);
        path2.lineTo(0, baseY);

        linePath.reset();

        int steps = 40;
        float stepX = (float) w / steps;

        for (int i = 0; i <= steps; i++) {
            float x = i * stepX;
            float y1 = baseY + (float) Math.sin((x / (float) w) * Math.PI * 3f + phase) * amplitude1
                    + (float) Math.cos((x / (float) w) * Math.PI * 1.5f - phase * 0.7f) * (amplitude1 * 0.4f);
            float y2 = baseY + (float) Math.cos((x / (float) w) * Math.PI * 2.5f + phase * 1.2f) * amplitude2
                    + (float) Math.sin((x / (float) w) * Math.PI * 4f + phase * 0.5f) * (amplitude2 * 0.3f);

            if (i == 0) {
                path1.lineTo(x, y1);
                path2.lineTo(x, y2);
                linePath.moveTo(x, y1);
            } else {
                path1.lineTo(x, y1);
                path2.lineTo(x, y2);
                linePath.lineTo(x, y1);
            }
        }

        path1.lineTo(w, h);
        path1.close();
        canvas.drawPath(path1, wavePaint1);

        path2.lineTo(w, h);
        path2.close();
        canvas.drawPath(path2, wavePaint2);

        canvas.drawPath(linePath, waveLinePaint);

        // 3. Barras de ecualizador rítmicas al fondo
        float barWidth = (float) w / (BAR_COUNT * 1.6f);
        float spacing = barWidth * 0.6f;
        float startX = (w - (BAR_COUNT * (barWidth + spacing))) / 2f;
        float maxBarH = h * 0.42f;

        for (int i = 0; i < BAR_COUNT; i++) {
            if (isPlaying) {
                if (Math.abs(barHeights[i] - barTargets[i]) < 4f) {
                    barTargets[i] = 12f + random.nextFloat() * maxBarH;
                }
                float lerpSpeed = 12f * delta;
                barHeights[i] += (barTargets[i] - barHeights[i]) * lerpSpeed;
            } else {
                barHeights[i] += (10f - barHeights[i]) * (6f * delta);
            }

            // Gravedad en picos
            if (barHeights[i] > barPeaks[i]) {
                barPeaks[i] = barHeights[i];
            } else {
                barPeaks[i] -= 45f * delta;
                if (barPeaks[i] < barHeights[i]) barPeaks[i] = barHeights[i];
            }

            float bx = startX + i * (barWidth + spacing);
            float byTop = h - barHeights[i];
            float corner = barWidth / 2f;

            canvas.drawRoundRect(bx, byTop, bx + barWidth, h, corner, corner, barPaint);

            // Pequeña barra o partícula flotante superior
            float peakTop = h - barPeaks[i] - 5f;
            canvas.drawRoundRect(bx, peakTop - 4f, bx + barWidth, peakTop, corner, corner, peakPaint);
        }

        if (isRunning) {
            postInvalidateOnAnimation();
        }
    }
}
