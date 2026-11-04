package com.simple.carlauncher;

import android.animation.ValueAnimator;
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
import android.view.animation.DecelerateInterpolator;

/**
 * Vista de velocímetro semi-circular estilo deportivo digital (0 a 200 km/h).
 * Fiel a la interfaz deportiva de alta gama:
 * - Arco semi-circular de 180° plano en la base (0 km/h a la izquierda, 100 arriba, 200 a la derecha).
 * - Banda luminosa neón cyan / azul eléctrico con halo difuminado según la velocidad actual.
 * - Rayo indicador / aguja luminosa en la punta de la velocidad activa.
 * - Ticks radiales y números en tipografía deportiva limpia.
 * - Lectura digital central en números grandes, en negrita e inclinados (itálica deportiva) con "km/h".
 * - Líneas de perspectiva vial estilo HUD neón en la parte inferior.
 * - Modo demo interactivo al tocar para probar la animación y el movimiento.
 */
public class ArcSpeedometerView extends View {

    private static final float START_ANGLE = 180f; // Comienza horizontal a la izquierda
    private static final float SWEEP_ANGLE = 180f; // Arco de 180 grados exactos
    private static final float MAX_SPEED = 200f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudRoadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudRoadGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();
    private final float density;

    private float currentSpeed = 0f;
    private float targetSpeed = 0f;
    private ValueAnimator demoAnimator;

    public interface OnSpeedDemoListener {
        void onDemoSpeedChanged(float speed);
    }
    private OnSpeedDemoListener demoListener;

    public ArcSpeedometerView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        // Pista de arco inactiva (fondo oscuro sutil)
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(3.5f * density);
        trackPaint.setColor(0x223B82F6);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);

        // Arco activo central (núcleo brillante cyan)
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(5f * density);
        arcPaint.setColor(0xFF00E5FF);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        // Brillo difuminado exterior (halo neón)
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(16f * density);
        glowPaint.setColor(0x6600B0FF);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        try {
            glowPaint.setMaskFilter(new BlurMaskFilter(10f * density, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        // Rayo / aguja luminosa en la velocidad activa
        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(2.2f * density);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        needleGlowPaint.setStyle(Paint.Style.STROKE);
        needleGlowPaint.setStrokeWidth(6f * density);
        needleGlowPaint.setColor(0x5500E5FF);
        try {
            needleGlowPaint.setMaskFilter(new BlurMaskFilter(4f * density, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        // Marcas de escala (ticks)
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        // Texto de números (0, 20, 40, ... 200)
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xCCFFFFFF);
        textPaint.setTextSize(11.5f * density);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        // Número digital central (grande, negrita e itálica deportiva inclinada)
        speedNumberPaint.setTextAlign(Paint.Align.CENTER);
        speedNumberPaint.setColor(Color.WHITE);
        speedNumberPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        speedNumberPaint.setTextSkewX(-0.16f); // Inclinación deportiva
        speedNumberPaint.setTextSize(54f * density);

        // Unidad "km/h"
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setColor(0xCCFFFFFF);
        unitPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        unitPaint.setTextSize(13f * density);

        // Líneas viales HUD inferiores estilo carretera neón
        hudRoadPaint.setStyle(Paint.Style.STROKE);
        hudRoadPaint.setStrokeWidth(2.5f * density);
        hudRoadPaint.setColor(0xFF00E5FF);
        hudRoadPaint.setStrokeCap(Paint.Cap.ROUND);

        hudRoadGlowPaint.setStyle(Paint.Style.STROKE);
        hudRoadGlowPaint.setStrokeWidth(8f * density);
        hudRoadGlowPaint.setColor(0x6600E5FF);
        hudRoadGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        try {
            hudRoadGlowPaint.setMaskFilter(new BlurMaskFilter(6f * density, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        hudGridPaint.setStyle(Paint.Style.STROKE);
        hudGridPaint.setStrokeWidth(1.2f * density);
        hudGridPaint.setColor(0x2B00E5FF);

        setLayerType(LAYER_TYPE_SOFTWARE, null);

        // Toque para iniciar simulación / demostración interactiva
        setOnClickListener(v -> toggleDemoAnimation());
    }

    public void setOnSpeedDemoListener(OnSpeedDemoListener listener) {
        this.demoListener = listener;
    }

    /**
     * Alterna la simulación de velocidad para probar visualmente el comportamiento a 73 km/h
     */
    public void toggleDemoAnimation() {
        if (demoAnimator != null && demoAnimator.isRunning()) {
            demoAnimator.cancel();
            setSpeed(0f);
            if (demoListener != null) demoListener.onDemoSpeedChanged(0f);
            return;
        }

        demoAnimator = ValueAnimator.ofFloat(0f, 73f, 73f, 0f);
        demoAnimator.setDuration(6000);
        demoAnimator.setInterpolator(new DecelerateInterpolator());
        demoAnimator.addUpdateListener(anim -> {
            float val = (float) anim.getAnimatedValue();
            setSpeed(val);
            if (demoListener != null) demoListener.onDemoSpeedChanged(val);
        });
        demoAnimator.start();
        android.widget.Toast.makeText(getContext(), "🚗 Demostración: Acelerando a 73 km/h", android.widget.Toast.LENGTH_SHORT).show();
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

        // Suavizado dinámico de aguja y arco hacia la velocidad objetivo
        float diff = targetSpeed - currentSpeed;
        if (Math.abs(diff) > 0.2f) {
            currentSpeed += diff * 0.18f;
            postInvalidateOnAnimation();
        } else {
            currentSpeed = targetSpeed;
        }

        float cx = w * 0.5f;
        float cy = h * 0.84f; // Base horizontal plana del arco semi-circular
        float maxRadiusW = w * 0.44f;
        float maxRadiusH = cy - h * 0.08f;
        float radius = Math.min(maxRadiusW, maxRadiusH);

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // 1. Pista inactiva de arco semi-circular completa (180° a 360°)
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        // 2. Arco activo iluminado con gradiente neón según la velocidad
        float frac = Math.max(0.005f, currentSpeed / MAX_SPEED);
        float activeSweep = frac * SWEEP_ANGLE;

        LinearGradient grad = new LinearGradient(
                cx - radius, cy, cx + radius, cy,
                new int[]{0xFF00E5FF, 0xFF00B0FF, 0xFF2979FF},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP
        );
        arcPaint.setShader(grad);
        glowPaint.setShader(grad);

        canvas.drawArc(arcRect, START_ANGLE, activeSweep, false, glowPaint);
        canvas.drawArc(arcRect, START_ANGLE, activeSweep, false, arcPaint);

        // 3. Ticks radiales y números en la escala (0, 20, 40, ... 200)
        float tickOuter = radius - 4f * density;
        float tickMajorInner = tickOuter - 8f * density;
        float tickMinorInner = tickOuter - 4f * density;
        float textRadius = tickMajorInner - 11f * density;

        for (int v = 0; v <= 200; v += 10) {
            float vFrac = (float) v / MAX_SPEED;
            double angleRad = Math.toRadians(START_ANGLE + vFrac * SWEEP_ANGLE);
            float cos = (float) Math.cos(angleRad);
            float sin = (float) Math.sin(angleRad);

            boolean isMajor = (v % 20 == 0);
            float inner = isMajor ? tickMajorInner : tickMinorInner;

            float x1 = cx + cos * inner;
            float y1 = cy + sin * inner;
            float x2 = cx + cos * tickOuter;
            float y2 = cy + sin * tickOuter;

            boolean active = v <= currentSpeed;
            tickPaint.setStrokeWidth((isMajor ? 2.0f : 1.0f) * density);
            tickPaint.setColor(active ? 0xFF00E5FF : (isMajor ? 0x88FFFFFF : 0x33FFFFFF));
            canvas.drawLine(x1, y1, x2, y2, tickPaint);

            if (isMajor) {
                float tx = cx + cos * textRadius;
                float ty = cy + sin * textRadius + (4f * density);
                // Ajuste fino para los extremos 0 y 200
                if (v == 0) tx += 3f * density;
                else if (v == 200) tx -= 3f * density;

                textPaint.setColor(active ? Color.WHITE : 0x99FFFFFF);
                canvas.drawText(String.valueOf(v), tx, ty, textPaint);
            }
        }

        // 4. Rayo / aguja luminosa que apunta desde el arco hacia el centro
        double activeAngleRad = Math.toRadians(START_ANGLE + activeSweep);
        float actCos = (float) Math.cos(activeAngleRad);
        float actSin = (float) Math.sin(activeAngleRad);

        float needleOuterX = cx + actCos * (radius + 2f * density);
        float needleOuterY = cy + actSin * (radius + 2f * density);
        float needleInnerX = cx + actCos * (radius * 0.58f);
        float needleInnerY = cy + actSin * (radius * 0.58f);

        LinearGradient needleGrad = new LinearGradient(
                needleOuterX, needleOuterY, needleInnerX, needleInnerY,
                new int[]{0xFF00E5FF, 0x8800B0FF, 0x0000E5FF},
                new float[]{0f, 0.4f, 1f},
                Shader.TileMode.CLAMP
        );
        needlePaint.setShader(needleGrad);
        needleGlowPaint.setShader(needleGrad);

        canvas.drawLine(needleOuterX, needleOuterY, needleInnerX, needleInnerY, needleGlowPaint);
        canvas.drawLine(needleOuterX, needleOuterY, needleInnerX, needleInnerY, needlePaint);

        // Punto iluminado en el extremo de la aguja
        Paint tipDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        tipDot.setColor(Color.WHITE);
        canvas.drawCircle(needleOuterX, needleOuterY, 2.8f * density, tipDot);

        // 5. Lectura digital central exacta (grande, inclinada itálica blanca)
        int displaySpeed = Math.round(currentSpeed);
        float numberY = cy - radius * 0.28f;
        canvas.drawText(String.valueOf(displaySpeed), cx, numberY, speedNumberPaint);
        canvas.drawText("km/h", cx, numberY + 20f * density, unitPaint);

        // 6. Líneas HUD de perspectiva de carretera en la parte inferior (exacto a la imagen)
        float roadBottomY = cy + 10f * density;
        float roadTopY = cy - 6f * density;

        float roadLeftBottomX = cx - radius * 0.38f;
        float roadLeftTopX = cx - radius * 0.14f;
        float roadRightBottomX = cx + radius * 0.38f;
        float roadRightTopX = cx + radius * 0.14f;

        // Cuadrícula horizontal en perspectiva
        for (float p = 0.25f; p <= 1.0f; p += 0.35f) {
            float gy = roadTopY + (roadBottomY - roadTopY) * p;
            float gx1 = roadLeftTopX + (roadLeftBottomX - roadLeftTopX) * p;
            float gx2 = roadRightTopX + (roadRightBottomX - roadRightTopX) * p;
            canvas.drawLine(gx1, gy, gx2, gy, hudGridPaint);
        }

        // Línea izquierda brillante con brillo neón
        canvas.drawLine(roadLeftBottomX, roadBottomY, roadLeftTopX, roadTopY, hudRoadGlowPaint);
        canvas.drawLine(roadLeftBottomX, roadBottomY, roadLeftTopX, roadTopY, hudRoadPaint);

        // Línea derecha brillante con brillo neón
        canvas.drawLine(roadRightBottomX, roadBottomY, roadRightTopX, roadTopY, hudRoadGlowPaint);
        canvas.drawLine(roadRightBottomX, roadBottomY, roadRightTopX, roadTopY, hudRoadPaint);
    }
}
