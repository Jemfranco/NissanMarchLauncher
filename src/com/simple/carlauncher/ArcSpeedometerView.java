package com.simple.carlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Velocímetro semi-circular premium de alta gama (0–200 km/h).
 *
 * Diseño visual de múltiples capas con profundidad:
 *   - Fondo con viñeta radial oscura para profundidad.
 *   - Anillo exterior decorativo con fragmentos luminosos.
 *   - Banda de arco gruesa multi-capa (halo difuminado + core brillante + borde interior).
 *   - Ticks radiales largos con grosor perspectivo y glow en los activos.
 *   - Números grandes alrededor del arco.
 *   - Aguja/indicador triangular sólido con sombra y punta iluminada.
 *   - Lectura digital central enorme con sombra de texto.
 *   - Líneas HUD viales neón en la base inferior.
 *   - Ocupa el 100% del espacio de su tarjeta contenedora.
 */
public class ArcSpeedometerView extends View {

    private static final float START_ANGLE = 180f;
    private static final float SWEEP_ANGLE = 180f;
    private static final float MAX_SPEED = 200f;

    private final float d; // density

    // Paints
    private final Paint vignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outerRingGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcOuterGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needleTipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF arcRect = new RectF();

    private float currentSpeed = 0f;
    private float targetSpeed = 0f;
    private ValueAnimator demoAnimator;

    public interface OnSpeedDemoListener {
        void onDemoSpeedChanged(float speed);
    }
    private OnSpeedDemoListener demoListener;

    public ArcSpeedometerView(Context context) {
        super(context);
        d = context.getResources().getDisplayMetrics().density;

        // Vignette de profundidad
        vignettePaint.setStyle(Paint.Style.FILL);

        // Anillo exterior decorativo
        outerRingPaint.setStyle(Paint.Style.STROKE);
        outerRingPaint.setStrokeWidth(1.5f * d);
        outerRingPaint.setColor(0x3300E5FF);

        outerRingGlowPaint.setStyle(Paint.Style.STROKE);
        outerRingGlowPaint.setStrokeWidth(4f * d);
        outerRingGlowPaint.setColor(0x1A00E5FF);

        // Pista inactiva (fondo del arco)
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(8f * d);
        trackPaint.setColor(0x18FFFFFF);
        trackPaint.setStrokeCap(Paint.Cap.BUTT);

        // Arco activo — core brillante
        arcCorePaint.setStyle(Paint.Style.STROKE);
        arcCorePaint.setStrokeWidth(8f * d);
        arcCorePaint.setStrokeCap(Paint.Cap.BUTT);

        // Arco activo — glow interior
        arcGlowPaint.setStyle(Paint.Style.STROKE);
        arcGlowPaint.setStrokeWidth(18f * d);
        arcGlowPaint.setStrokeCap(Paint.Cap.BUTT);
        try {
            arcGlowPaint.setMaskFilter(new BlurMaskFilter(10f * d, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        // Arco activo — glow exterior amplio
        arcOuterGlowPaint.setStyle(Paint.Style.STROKE);
        arcOuterGlowPaint.setStrokeWidth(32f * d);
        arcOuterGlowPaint.setStrokeCap(Paint.Cap.BUTT);
        try {
            arcOuterGlowPaint.setMaskFilter(new BlurMaskFilter(18f * d, BlurMaskFilter.Blur.OUTER));
        } catch (Throwable ignored) {}

        // Ticks
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        tickGlowPaint.setStyle(Paint.Style.STROKE);
        tickGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        try {
            tickGlowPaint.setMaskFilter(new BlurMaskFilter(3f * d, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        // Números de escala
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));

        // Número digital central — sombra
        speedShadowPaint.setTextAlign(Paint.Align.CENTER);
        speedShadowPaint.setColor(0x6600B0FF);
        speedShadowPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        try {
            speedShadowPaint.setMaskFilter(new BlurMaskFilter(8f * d, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        // Número digital central — frontal
        speedPaint.setTextAlign(Paint.Align.CENTER);
        speedPaint.setColor(Color.WHITE);
        speedPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));

        // Unidad km/h
        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setColor(0xBBFFFFFF);
        unitPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        // Aguja triangular
        needlePaint.setStyle(Paint.Style.FILL);
        needlePaint.setColor(0xFF00E5FF);

        needleGlowPaint.setStyle(Paint.Style.FILL);
        needleGlowPaint.setColor(0x4400E5FF);
        try {
            needleGlowPaint.setMaskFilter(new BlurMaskFilter(6f * d, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        needleTipPaint.setStyle(Paint.Style.FILL);
        needleTipPaint.setColor(Color.WHITE);

        centerDotPaint.setStyle(Paint.Style.FILL);

        // HUD road lines
        hudLinePaint.setStyle(Paint.Style.STROKE);
        hudLinePaint.setStrokeWidth(2.5f * d);
        hudLinePaint.setColor(0xFF00E5FF);
        hudLinePaint.setStrokeCap(Paint.Cap.ROUND);

        hudGlowPaint.setStyle(Paint.Style.STROKE);
        hudGlowPaint.setStrokeWidth(10f * d);
        hudGlowPaint.setColor(0x5500E5FF);
        hudGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        try {
            hudGlowPaint.setMaskFilter(new BlurMaskFilter(8f * d, BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        hudGridPaint.setStyle(Paint.Style.STROKE);
        hudGridPaint.setStrokeWidth(1f * d);
        hudGridPaint.setColor(0x2200E5FF);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setOnClickListener(v -> toggleDemoAnimation());
    }

    public void setOnSpeedDemoListener(OnSpeedDemoListener listener) {
        this.demoListener = listener;
    }

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
    }

    public void setSpeed(float speed) {
        targetSpeed = Math.max(0f, Math.min(MAX_SPEED, speed));
        invalidate();
    }

    public float getSpeed() { return targetSpeed; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Suavizado dinámico
        float diff = targetSpeed - currentSpeed;
        if (Math.abs(diff) > 0.15f) {
            currentSpeed += diff * 0.15f;
            postInvalidateOnAnimation();
        } else {
            currentSpeed = targetSpeed;
        }

        // ── Geometría: el arco ocupa la mayor parte posible del espacio ──
        float padSide = 12f * d;
        float padTop = 8f * d;
        float padBottom = 24f * d; // espacio para HUD inferior

        float availW = w - padSide * 2;
        float availH = h - padTop - padBottom;

        // Radio máximo: la mitad del ancho disponible, o la altura disponible
        float radius = Math.min(availW * 0.5f, availH * 0.92f);
        float cx = w * 0.5f;
        float cy = padTop + radius + 4f * d; // centro del arco

        // ════════════════════════════════════════════
        // 0. VIÑETA RADIAL DE PROFUNDIDAD
        // ════════════════════════════════════════════
        try {
            RadialGradient vig = new RadialGradient(cx, cy * 0.85f, radius * 1.4f,
                    new int[]{0x0000080E, 0x2200050A, 0x66000307},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
            vignettePaint.setShader(vig);
            canvas.drawRect(0, 0, w, h, vignettePaint);
        } catch (Throwable ignored) {}

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);

        // ════════════════════════════════════════════
        // 1. ANILLO EXTERIOR DECORATIVO
        // ════════════════════════════════════════════
        float outerR = radius + 6f * d;
        RectF outerRect = new RectF(cx - outerR, cy - outerR, cx + outerR, cy + outerR);
        canvas.drawArc(outerRect, START_ANGLE, SWEEP_ANGLE, false, outerRingGlowPaint);
        canvas.drawArc(outerRect, START_ANGLE, SWEEP_ANGLE, false, outerRingPaint);

        // Fragmentos luminosos decorativos en el anillo exterior
        Paint fragPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fragPaint.setStyle(Paint.Style.STROKE);
        fragPaint.setStrokeWidth(2.5f * d);
        fragPaint.setStrokeCap(Paint.Cap.ROUND);
        float fragR = radius + 12f * d;
        RectF fragRect = new RectF(cx - fragR, cy - fragR, cx + fragR, cy + fragR);
        for (int v = 0; v <= 200; v += 20) {
            float frac = (float) v / MAX_SPEED;
            boolean active = v <= currentSpeed;
            fragPaint.setColor(active ? 0x5500E5FF : 0x1500E5FF);
            float startA = START_ANGLE + frac * SWEEP_ANGLE - 1.5f;
            canvas.drawArc(fragRect, startA, 3f, false, fragPaint);
        }

        // ════════════════════════════════════════════
        // 2. PISTA INACTIVA DEL ARCO
        // ════════════════════════════════════════════
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_ANGLE, false, trackPaint);

        // ════════════════════════════════════════════
        // 3. ARCO ACTIVO MULTI-CAPA (3 capas de brillo)
        // ════════════════════════════════════════════
        float frac = Math.max(0.003f, currentSpeed / MAX_SPEED);
        float activeSweep = frac * SWEEP_ANGLE;

        LinearGradient arcGrad = new LinearGradient(
                cx - radius, cy, cx + radius, cy,
                new int[]{0xFF00E5FF, 0xFF00AAFF, 0xFF2962FF, 0xFF6200EA},
                new float[]{0f, 0.35f, 0.7f, 1f},
                Shader.TileMode.CLAMP);

        arcOuterGlowPaint.setShader(arcGrad);
        arcGlowPaint.setShader(arcGrad);
        arcCorePaint.setShader(arcGrad);

        // Capa 3: resplandor difuso amplio
        canvas.drawArc(arcRect, START_ANGLE, activeSweep, false, arcOuterGlowPaint);
        // Capa 2: glow interior
        canvas.drawArc(arcRect, START_ANGLE, activeSweep, false, arcGlowPaint);
        // Capa 1: núcleo brillante sólido
        canvas.drawArc(arcRect, START_ANGLE, activeSweep, false, arcCorePaint);

        // ════════════════════════════════════════════
        // 4. TICKS RADIALES (largos, con glow en activos)
        // ════════════════════════════════════════════
        float tickOuter = radius - 4f * d;
        float tickMajorLen = 16f * d;
        float tickMinorLen = 8f * d;
        float textR = tickOuter - tickMajorLen - 10f * d;

        for (int v = 0; v <= 200; v += 10) {
            float vFrac = (float) v / MAX_SPEED;
            double aRad = Math.toRadians(START_ANGLE + vFrac * SWEEP_ANGLE);
            float cos = (float) Math.cos(aRad);
            float sin = (float) Math.sin(aRad);

            boolean isMajor = (v % 20 == 0);
            float tLen = isMajor ? tickMajorLen : tickMinorLen;

            float x1 = cx + cos * (tickOuter - tLen);
            float y1 = cy + sin * (tickOuter - tLen);
            float x2 = cx + cos * tickOuter;
            float y2 = cy + sin * tickOuter;

            boolean active = v <= currentSpeed;

            if (active && isMajor) {
                // Glow detrás del tick activo
                tickGlowPaint.setStrokeWidth(4f * d);
                tickGlowPaint.setColor(0x6600E5FF);
                canvas.drawLine(x1, y1, x2, y2, tickGlowPaint);
            }

            tickPaint.setStrokeWidth((isMajor ? 2.5f : 1.2f) * d);
            if (active) {
                tickPaint.setColor(isMajor ? 0xFF00E5FF : 0xBB00CCFF);
            } else {
                tickPaint.setColor(isMajor ? 0x77FFFFFF : 0x33FFFFFF);
            }
            canvas.drawLine(x1, y1, x2, y2, tickPaint);

            // Números
            if (isMajor) {
                float tx = cx + cos * textR;
                float ty = cy + sin * textR;
                // Tamaño dinámico según espacio
                float fontSize = Math.min(14f, Math.max(10f, radius * 0.09f));
                textPaint.setTextSize(fontSize * d);
                textPaint.setColor(active ? Color.WHITE : 0x88FFFFFF);

                // Ajuste vertical al baseline
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                ty -= (fm.ascent + fm.descent) * 0.5f;

                canvas.drawText(String.valueOf(v), tx, ty, textPaint);
            }
        }

        // ════════════════════════════════════════════
        // 5. AGUJA / INDICADOR TRIANGULAR
        // ════════════════════════════════════════════
        double activeRad = Math.toRadians(START_ANGLE + activeSweep);
        float nCos = (float) Math.cos(activeRad);
        float nSin = (float) Math.sin(activeRad);

        float needleLen = radius * 0.48f;
        float needleOutR = tickOuter + 2f * d;

        float tipX = cx + nCos * needleOutR;
        float tipY = cy + nSin * needleOutR;
        float baseX = cx + nCos * (needleOutR - needleLen);
        float baseY = cy + nSin * (needleOutR - needleLen);

        // Vector perpendicular para el ancho de la base del triángulo
        float perpX = -nSin;
        float perpY = nCos;
        float baseHalfW = 3.5f * d;

        Path needlePath = new Path();
        needlePath.moveTo(tipX, tipY);
        needlePath.lineTo(baseX + perpX * baseHalfW, baseY + perpY * baseHalfW);
        needlePath.lineTo(baseX - perpX * baseHalfW, baseY - perpY * baseHalfW);
        needlePath.close();

        // Sombra / glow de la aguja
        canvas.drawPath(needlePath, needleGlowPaint);
        // Aguja sólida
        needlePaint.setShader(new LinearGradient(
                tipX, tipY, baseX, baseY,
                new int[]{0xFF00E5FF, 0xCC00AAFF, 0x4400AAFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(needlePath, needlePaint);

        // Punto brillante en la punta
        needleTipPaint.setColor(Color.WHITE);
        canvas.drawCircle(tipX, tipY, 3.5f * d, needleTipPaint);

        // Dot luminoso en el hub central
        try {
            RadialGradient hubGrad = new RadialGradient(cx, cy, 8f * d,
                    new int[]{0xAA00E5FF, 0x4400AAFF, 0x0000AAFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            centerDotPaint.setShader(hubGrad);
        } catch (Throwable ignored) {
            centerDotPaint.setShader(null);
            centerDotPaint.setColor(0x6600E5FF);
        }
        canvas.drawCircle(cx, cy, 6f * d, centerDotPaint);

        // ════════════════════════════════════════════
        // 6. LECTURA DIGITAL CENTRAL (ENORME)
        // ════════════════════════════════════════════
        int displaySpeed = Math.round(currentSpeed);
        String speedStr = String.valueOf(displaySpeed);

        // Tamaño dinámico: ocupa ~ 40% del radio
        float bigSize = Math.max(32f * d, radius * 0.42f);
        speedPaint.setTextSize(bigSize);
        speedShadowPaint.setTextSize(bigSize);

        float numY = cy - radius * 0.18f;

        // Sombra neón detrás del número
        canvas.drawText(speedStr, cx, numY, speedShadowPaint);
        // Número blanco principal
        canvas.drawText(speedStr, cx, numY, speedPaint);

        // Unidad km/h
        float unitSize = Math.max(11f * d, radius * 0.10f);
        unitPaint.setTextSize(unitSize);
        canvas.drawText("km/h", cx, numY + bigSize * 0.36f, unitPaint);

        // ════════════════════════════════════════════
        // 7. LÍNEAS HUD VIALES EN LA BASE
        // ════════════════════════════════════════════
        float hudBaseY = cy + 6f * d;
        float hudTopY = cy - 8f * d;
        float hudSpread = radius * 0.42f;
        float hudNarrow = radius * 0.10f;

        // Cuadrícula horizontal
        for (float p = 0.2f; p <= 1.0f; p += 0.30f) {
            float gy = hudTopY + (hudBaseY - hudTopY) * p;
            float gxL = (cx - hudNarrow) + ((cx - hudSpread) - (cx - hudNarrow)) * p;
            float gxR = (cx + hudNarrow) + ((cx + hudSpread) - (cx + hudNarrow)) * p;
            canvas.drawLine(gxL, gy, gxR, gy, hudGridPaint);
        }

        // Dos líneas diagonales brillantes
        canvas.drawLine(cx - hudSpread, hudBaseY, cx - hudNarrow, hudTopY, hudGlowPaint);
        canvas.drawLine(cx - hudSpread, hudBaseY, cx - hudNarrow, hudTopY, hudLinePaint);
        canvas.drawLine(cx + hudSpread, hudBaseY, cx + hudNarrow, hudTopY, hudGlowPaint);
        canvas.drawLine(cx + hudSpread, hudBaseY, cx + hudNarrow, hudTopY, hudLinePaint);
    }
}
