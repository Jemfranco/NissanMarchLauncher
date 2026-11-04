package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * Vista de conducción 3D inmersiva con perspectiva realista:
 * - Cielo nocturno degradado con estrellas y atmósfera.
 * - Carretera procedural 3D con punto de fuga, asfalto texturizado.
 * - Líneas de borde continuas y líneas de carril punteadas en perspectiva.
 * - Barandas laterales metálicas con reflectores naranjas animados.
 * - Postes de luz con halos luminosos que pasan en el campo de visión.
 * - Cono de iluminación de faros del March sobre la carretera.
 * - Nissan March 2015 blanco con física de suspensión y sombra dinámica.
 * - Todo el paisaje se mueve según la velocidad; estático cuando está detenido.
 * - Tarjeta flotante inferior con Reloj digital y Estado de Cambio de Aceite.
 */
public class DriveRoadView extends FrameLayout {

    private final float density;
    private final RoadSurfaceView roadSurface;
    private TextView oilKmText;
    private View oilProgressBar;
    private View oilProgressFill;
    private int oilProgressWidth = 100;

    public DriveRoadView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        // Fondo y superficie interactiva de carretera + carro
        roadSurface = new RoadSurfaceView(context);
        roadSurface.setOnClickListener(v -> {
            context.sendBroadcast(new android.content.Intent("com.simple.carlauncher.TEST_CAR_ON"));
            android.widget.Toast.makeText(context, "\uD83C\uDF99\uFE0F Saludo de bienvenida activado", android.widget.Toast.LENGTH_SHORT).show();
        });
        addView(roadSurface, new FrameLayout.LayoutParams(-1, -1));

        // Tarjeta flotante de Reloj + Aceite en la parte inferior
        View bottomOverlay = buildBottomOverlay(context);
        FrameLayout.LayoutParams olp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        int marginH = (int) (14 * density);
        int marginB = (int) (12 * density);
        olp.setMargins(marginH, 0, marginH, marginB);
        addView(bottomOverlay, olp);

        setClipToOutline(true);
    }

    public void setSpeed(float kmh) {
        roadSurface.setSpeed(kmh);
    }

    public void updateOil() {
        if (!Odometer.isConfigured(getContext())) {
            if (oilKmText != null) oilKmText.setText("Aceite: configurar");
            if (oilProgressFill != null) oilProgressFill.getLayoutParams().width = (int) (20 * density);
            return;
        }
        int left = Odometer.remainingKm(getContext());
        if (oilKmText != null) {
            oilKmText.setText(left < 0 ? "\u00A1Ya toca el cambio!" : "Faltan " + OilActivity.fmt(left) + " km");
            oilKmText.setTextColor(left < 0 ? 0xFFFF5252 : left <= Odometer.WARN_KM ? 0xFFFFB300 : 0xFFFFD54F);
        }
        if (oilProgressFill != null) {
            float frac = Math.max(0f, Math.min(1f, (float) left / Odometer.DEFAULT_INTERVAL_KM));
            int fillW = Math.max((int) (6 * density), (int) (120 * density * frac));
            oilProgressFill.getLayoutParams().width = fillW;
            oilProgressFill.requestLayout();
        }
    }

    private View buildBottomOverlay(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (16 * density);
        int padV = (int) (10 * density);
        card.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD90D1220);
        bg.setCornerRadius(16 * density);
        bg.setStroke((int) (1 * density), 0x3300E5FF);
        card.setBackground(bg);

        // Lado izquierdo: Hora y Fecha
        LinearLayout leftCol = new LinearLayout(c);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        leftCol.setGravity(Gravity.CENTER_VERTICAL);

        TextClock time = new TextClock(c);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(22);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        leftCol.addView(time);

        TextClock date = new TextClock(c);
        date.setFormat24Hour("EEE, d 'de' MMM");
        date.setFormat12Hour("EEE, d 'de' MMM");
        date.setTextSize(11);
        date.setTextColor(0xAAFFFFFF);
        date.setPadding(0, (int) (2 * density), 0, 0);
        leftCol.addView(date);

        card.addView(leftCol, new LinearLayout.LayoutParams(-2, -2));

        // Separador vertical
        View sep = new View(c);
        sep.setBackgroundColor(0x2BFFFFFF);
        LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams((int) (1 * density), (int) (32 * density));
        sepLp.setMargins((int) (16 * density), 0, (int) (16 * density), 0);
        card.addView(sep, sepLp);

        // Lado derecho: Cambio de Aceite
        LinearLayout rightCol = new LinearLayout(c);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        rightCol.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout oilTitleRow = new LinearLayout(c);
        oilTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView oilIcon = new TextView(c);
        oilIcon.setText("\uD83D\uDEE2\uFE0F");
        oilIcon.setTextSize(13);
        oilTitleRow.addView(oilIcon);

        TextView oilTitle = new TextView(c);
        oilTitle.setText(" Cambio de aceite");
        oilTitle.setTextSize(12);
        oilTitle.setTextColor(0xCCFFFFFF);
        oilTitle.setTypeface(Typeface.DEFAULT_BOLD);
        oilTitleRow.addView(oilTitle);
        rightCol.addView(oilTitleRow);

        oilKmText = new TextView(c);
        oilKmText.setText("Faltan 5.000 km");
        oilKmText.setTextSize(12);
        oilKmText.setTextColor(0xFFFFD54F);
        oilKmText.setTypeface(Typeface.DEFAULT_BOLD);
        oilKmText.setPadding(0, (int) (2 * density), 0, (int) (4 * density));
        rightCol.addView(oilKmText);

        // Barra de progreso del aceite
        oilProgressBar = new FrameLayout(c);
        GradientDrawable pbBg = new GradientDrawable();
        pbBg.setColor(0x33FFFFFF);
        pbBg.setCornerRadius(4 * density);
        oilProgressBar.setBackground(pbBg);

        oilProgressFill = new View(c);
        GradientDrawable pbFill = new GradientDrawable();
        pbFill.setColor(0xFFFFB300);
        pbFill.setCornerRadius(4 * density);
        oilProgressFill.setBackground(pbFill);
        ((FrameLayout) oilProgressBar).addView(oilProgressFill, new FrameLayout.LayoutParams((int) (80 * density), (int) (5 * density)));

        rightCol.addView(oilProgressBar, new LinearLayout.LayoutParams((int) (130 * density), (int) (5 * density)));

        card.addView(rightCol, new LinearLayout.LayoutParams(0, -2, 1f));

        Ui.pressable(card, 0.94f);
        card.setOnClickListener(v -> c.startActivity(new android.content.Intent(c, OilActivity.class)));
        return card;
    }

    /**
     * Vista interna de dibujo de carretera inmersiva en perspectiva 3D con paisaje nocturno completo.
     */
    private static class RoadSurfaceView extends View {

        private final float d;

        // Paints principales
        private final Paint skyPaint = new Paint();
        private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint roadFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shoulderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgeLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dashGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guardRailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint reflectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lampPostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lampGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint headlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint asphaltGrainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint treeSilhouettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap carBmp;

        private float speed = 0f;
        private float roadZOffset = 0f;
        private long lastFrameTime = 0;

        // Posiciones fijas de estrellas (generadas una sola vez)
        private float[] starX, starY, starBright;
        private static final int NUM_STARS = 60;

        public RoadSurfaceView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;

            roadFillPaint.setStyle(Paint.Style.FILL);
            shoulderPaint.setStyle(Paint.Style.FILL);
            shoulderPaint.setColor(0xFF0E1018);

            edgeLinePaint.setStyle(Paint.Style.STROKE);
            edgeLinePaint.setStrokeCap(Paint.Cap.BUTT);

            dashPaint.setColor(0xEEFFFFFF);
            dashPaint.setStyle(Paint.Style.STROKE);
            dashPaint.setStrokeCap(Paint.Cap.ROUND);

            dashGlowPaint.setColor(0x3300E5FF);
            dashGlowPaint.setStyle(Paint.Style.STROKE);
            dashGlowPaint.setStrokeCap(Paint.Cap.ROUND);

            guardRailPaint.setStyle(Paint.Style.STROKE);
            guardRailPaint.setStrokeWidth(1.8f * d);

            reflectorPaint.setStyle(Paint.Style.FILL);

            lampPostPaint.setColor(0xFF3A3A4A);
            lampPostPaint.setStyle(Paint.Style.STROKE);
            lampPostPaint.setStrokeWidth(2f * d);

            lampGlowPaint.setStyle(Paint.Style.FILL);

            headlightPaint.setStyle(Paint.Style.FILL);

            shadowPaint.setColor(0xCC000000);
            shadowPaint.setStyle(Paint.Style.FILL);

            asphaltGrainPaint.setStyle(Paint.Style.FILL);

            starPaint.setStyle(Paint.Style.FILL);

            treeSilhouettePaint.setColor(0xFF080C14);
            treeSilhouettePaint.setStyle(Paint.Style.FILL);

            hillPaint.setStyle(Paint.Style.FILL);

            // Generar estrellas fijas aleatorias
            java.util.Random rng = new java.util.Random(42);
            starX = new float[NUM_STARS];
            starY = new float[NUM_STARS];
            starBright = new float[NUM_STARS];
            for (int i = 0; i < NUM_STARS; i++) {
                starX[i] = rng.nextFloat();
                starY[i] = rng.nextFloat() * 0.25f;
                starBright[i] = 0.3f + rng.nextFloat() * 0.7f;
            }

            try {
                carBmp = BitmapFactory.decodeResource(c.getResources(), R.drawable.march_rear_chase);
            } catch (Throwable ignored) {}

            setLayerType(LAYER_TYPE_HARDWARE, null);
        }

        public void setSpeed(float kmh) {
            boolean wasZero = (speed <= 0.5f);
            this.speed = Math.max(0f, kmh);
            boolean isZero = (speed <= 0.5f);

            if (wasZero != isZero || !isZero) {
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            long now = SystemClock.uptimeMillis();
            float dt = lastFrameTime == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameTime) / 1000f);
            lastFrameTime = now;

            boolean isMoving = speed > 1.5f;

            if (isMoving) {
                float moveSpeed = Math.max(15f, speed * 0.85f);
                roadZOffset += dt * moveSpeed;
                if (roadZOffset > 10000f) roadZOffset -= 10000f;
            }

            // Punto de fuga y perspectiva
            float horizonY = h * 0.34f;
            float vanishX = w * 0.50f;

            // ===== 1. CIELO NOCTURNO CON DEGRADADO Y ESTRELLAS =====
            drawSky(canvas, w, horizonY, now, isMoving);

            // ===== 2. COLINAS/VEGETACIÓN SILUETEADA EN EL HORIZONTE =====
            drawHorizonHills(canvas, w, horizonY);

            // ===== 3. CARRETERA CON HOMBRILLOS Y ASFALTO =====
            drawRoadSurface(canvas, w, h, horizonY, vanishX);

            // ===== 4. ASFALTO TEXTURIZADO =====
            drawAsphaltTexture(canvas, w, h, horizonY, vanishX, roadZOffset, isMoving);

            // ===== 5. LÍNEAS DE BORDE Y CARRILES PUNTEADOS =====
            drawEdgeLines(canvas, w, h, horizonY, vanishX);
            drawDashedLanes(canvas, w, h, horizonY, vanishX, roadZOffset);

            // ===== 6. BARANDAS CON REFLECTORES =====
            drawGuardRails(canvas, w, h, horizonY, vanishX, roadZOffset);

            // ===== 7. POSTES DE LUZ CON HALOS =====
            drawLampPosts(canvas, w, h, horizonY, vanishX, roadZOffset);

            // ===== 8. CONO DE LUZ DE LOS FAROS =====
            drawHeadlights(canvas, w, h, horizonY, vanishX);

            // ===== 9. VEHÍCULO: NISSAN MARCH 2015 =====
            drawNissanMarch(canvas, w, h, isMoving, now);

            // Solicitar siguiente frame si en movimiento
            if (isMoving) {
                postInvalidateOnAnimation();
            }
        }

        // ───────────────────────────────────────────────────
        // 1. CIELO NOCTURNO
        // ───────────────────────────────────────────────────
        private void drawSky(Canvas canvas, float w, float horizonY, long now, boolean moving) {
            // Degradado vertical: azul profundo arriba → gris azulado en horizonte
            LinearGradient skyGrad = new LinearGradient(0, 0, 0, horizonY,
                    new int[]{0xFF050A18, 0xFF0A1428, 0xFF121D38, 0xFF1A2540},
                    new float[]{0f, 0.3f, 0.7f, 1f},
                    Shader.TileMode.CLAMP);
            skyPaint.setShader(skyGrad);
            canvas.drawRect(0, 0, w, horizonY + 2, skyPaint);

            // Estrellas titilantes
            for (int i = 0; i < NUM_STARS; i++) {
                float sx = starX[i] * w;
                float sy = starY[i] * horizonY;
                float bright = starBright[i];
                // Parpadeo sutil en cada estrella
                if (moving) {
                    bright *= 0.7f + 0.3f * (float) Math.sin(now * 0.002 + i * 1.7);
                }
                int alpha = (int) (255 * bright);
                starPaint.setColor(Color.argb(alpha, 220, 230, 255));
                float radius = (0.6f + bright * 0.9f) * d;
                canvas.drawCircle(sx, sy, radius, starPaint);
            }
        }

        // ───────────────────────────────────────────────────
        // 2. COLINAS EN EL HORIZONTE
        // ───────────────────────────────────────────────────
        private void drawHorizonHills(Canvas canvas, float w, float horizonY) {
            // Capa de colinas lejanas
            hillPaint.setColor(0xFF0C1220);
            Path hills = new Path();
            hills.moveTo(0, horizonY);
            hills.cubicTo(w * 0.15f, horizonY - 18 * d, w * 0.30f, horizonY - 10 * d, w * 0.45f, horizonY - 22 * d);
            hills.cubicTo(w * 0.55f, horizonY - 14 * d, w * 0.70f, horizonY - 26 * d, w * 0.85f, horizonY - 12 * d);
            hills.lineTo(w, horizonY - 8 * d);
            hills.lineTo(w, horizonY + 4);
            hills.lineTo(0, horizonY + 4);
            hills.close();
            canvas.drawPath(hills, hillPaint);

            // Árboles silueteados (triángulos irregulares)
            float[] treePosX = {w * 0.06f, w * 0.14f, w * 0.24f, w * 0.72f, w * 0.82f, w * 0.91f};
            float[] treeH = {16f, 22f, 18f, 20f, 14f, 24f};
            for (int i = 0; i < treePosX.length; i++) {
                float tx = treePosX[i];
                float th = treeH[i] * d;
                Path tree = new Path();
                tree.moveTo(tx, horizonY);
                tree.lineTo(tx - 4 * d, horizonY);
                tree.lineTo(tx - 2 * d, horizonY - th * 0.5f);
                tree.lineTo(tx, horizonY - th);
                tree.lineTo(tx + 2 * d, horizonY - th * 0.5f);
                tree.lineTo(tx + 4 * d, horizonY);
                tree.close();
                canvas.drawPath(tree, treeSilhouettePaint);
            }
        }

        // ───────────────────────────────────────────────────
        // 3. SUPERFICIE DE CARRETERA
        // ───────────────────────────────────────────────────
        private void drawRoadSurface(Canvas canvas, float w, float h, float horizonY, float vanishX) {
            float roadBL = -w * 0.15f;
            float roadBR = w * 1.15f;
            float roadTL = vanishX - w * 0.08f;
            float roadTR = vanishX + w * 0.08f;

            // Hombrillos exteriores (más oscuros)
            Path shoulder = new Path();
            shoulder.moveTo(-w * 0.3f, h);
            shoulder.lineTo(vanishX - w * 0.12f, horizonY);
            shoulder.lineTo(vanishX + w * 0.12f, horizonY);
            shoulder.lineTo(w * 1.3f, h);
            shoulder.close();
            shoulderPaint.setColor(0xFF0A0E16);
            canvas.drawPath(shoulder, shoulderPaint);

            // Carretera principal con degradado de profundidad
            Path road = new Path();
            road.moveTo(roadBL, h);
            road.lineTo(roadTL, horizonY);
            road.lineTo(roadTR, horizonY);
            road.lineTo(roadBR, h);
            road.close();

            LinearGradient roadGrad = new LinearGradient(0, horizonY, 0, h,
                    new int[]{0xFF181C28, 0xFF1A1E2A, 0xFF1E2230},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
            roadFillPaint.setShader(roadGrad);
            canvas.drawPath(road, roadFillPaint);
        }

        // ───────────────────────────────────────────────────
        // 4. TEXTURA DE ASFALTO (GRANO MOVIÉNDOSE)
        // ───────────────────────────────────────────────────
        private void drawAsphaltTexture(Canvas canvas, float w, float h, float horizonY, float vanishX, float zOff, boolean moving) {
            if (!moving) return;
            // Líneas horizontales de asfalto en perspectiva que se mueven hacia la cámara
            float roadBL = -w * 0.05f;
            float roadBR = w * 1.05f;
            float roadTL = vanishX - w * 0.06f;
            float roadTR = vanishX + w * 0.06f;

            int numTextureLines = 20;
            float spacing = 1.0f;
            asphaltGrainPaint.setStyle(Paint.Style.STROKE);
            asphaltGrainPaint.setStrokeWidth(0.5f * d);

            for (int i = 0; i < numTextureLines; i++) {
                float rawZ = (i * spacing - (zOff * 0.12f)) % (numTextureLines * spacing);
                if (rawZ < 0) rawZ += numTextureLines * spacing;
                float depthFrac = rawZ / (numTextureLines * spacing);
                float t = 1.0f - (float) Math.pow(depthFrac, 2.0);
                if (t < 0.02f || t > 0.98f) continue;

                float y = horizonY + (h - horizonY) * t;
                float lx = roadTL + (roadBL - roadTL) * t;
                float rx = roadTR + (roadBR - roadTR) * t;

                int alpha = (int) (18 * t);
                asphaltGrainPaint.setColor(Color.argb(alpha, 255, 255, 255));
                canvas.drawLine(lx, y, rx, y, asphaltGrainPaint);
            }
        }

        // ───────────────────────────────────────────────────
        // 5a. LÍNEAS DE BORDE CONTINUAS
        // ───────────────────────────────────────────────────
        private void drawEdgeLines(Canvas canvas, float w, float h, float horizonY, float vanishX) {
            float roadBL = w * 0.02f;
            float roadBR = w * 0.98f;
            float roadTL = vanishX - w * 0.065f;
            float roadTR = vanishX + w * 0.065f;

            // Líneas de borde izquierda y derecha (continuas blancas)
            int segments = 30;
            for (int i = 0; i < segments; i++) {
                float t0 = (float) i / segments;
                float t1 = (float) (i + 1) / segments;
                // Perspectiva no lineal
                float pt0 = (float) Math.pow(t0, 2.2);
                float pt1 = (float) Math.pow(t1, 2.2);

                float y0 = horizonY + (h - horizonY) * pt0;
                float y1 = horizonY + (h - horizonY) * pt1;

                float lx0 = roadTL + (roadBL - roadTL) * pt0;
                float lx1 = roadTL + (roadBL - roadTL) * pt1;
                float rx0 = roadTR + (roadBR - roadTR) * pt0;
                float rx1 = roadTR + (roadBR - roadTR) * pt1;

                float lineWidth = (0.8f + 2.2f * pt0) * d;
                int alpha = (int) (30 + 225 * pt0);

                edgeLinePaint.setStrokeWidth(lineWidth);
                edgeLinePaint.setColor(Color.argb(alpha, 255, 255, 255));
                canvas.drawLine(lx0, y0, lx1, y1, edgeLinePaint);
                canvas.drawLine(rx0, y0, rx1, y1, edgeLinePaint);
            }
        }

        // ───────────────────────────────────────────────────
        // 5b. CARRILES PUNTEADOS CENTRALES
        // ───────────────────────────────────────────────────
        private void drawDashedLanes(Canvas canvas, float w, float h, float horizonY, float vanishX, float zOff) {
            float roadBL = w * 0.02f;
            float roadBR = w * 0.98f;
            float roadTL = vanishX - w * 0.065f;
            float roadTR = vanishX + w * 0.065f;

            float[] laneFracs = {0.35f, 0.65f};
            int numDashes = 18;
            float dashSpacing = 1.0f;

            for (float lf : laneFracs) {
                float bottomX = roadBL + (roadBR - roadBL) * lf;
                float topX = roadTL + (roadTR - roadTL) * lf;

                for (int i = 0; i < numDashes; i++) {
                    float rawZ = (i * dashSpacing - (zOff * 0.12f)) % (numDashes * dashSpacing);
                    if (rawZ < 0) rawZ += numDashes * dashSpacing;
                    float depthFrac = rawZ / (numDashes * dashSpacing);
                    float t = 1.0f - (float) Math.pow(depthFrac, 2.2);
                    if (t < 0.03f || t > 0.97f) continue;

                    float dashY = horizonY + (h - horizonY) * t;
                    float dashLength = (6f + 48f * t) * d;
                    float dashWidth = (1.2f + 4.0f * t) * d;

                    float startY = dashY - dashLength * 0.5f;
                    float endY = dashY + dashLength * 0.5f;
                    float sT = (startY - horizonY) / (h - horizonY);
                    float eT = (endY - horizonY) / (h - horizonY);
                    float startX = topX + (bottomX - topX) * sT;
                    float endX = topX + (bottomX - topX) * eT;

                    int alpha = (int) (255 * (0.20f + 0.80f * t));

                    // Brillo cyan sutil
                    dashGlowPaint.setStrokeWidth(dashWidth * 2.5f);
                    dashGlowPaint.setAlpha((int) (alpha * 0.35f));
                    canvas.drawLine(startX, startY, endX, endY, dashGlowPaint);

                    dashPaint.setStrokeWidth(dashWidth);
                    dashPaint.setAlpha(alpha);
                    canvas.drawLine(startX, startY, endX, endY, dashPaint);
                }
            }
        }

        // ───────────────────────────────────────────────────
        // 6. BARANDAS METÁLICAS CON REFLECTORES
        // ───────────────────────────────────────────────────
        private void drawGuardRails(Canvas canvas, float w, float h, float horizonY, float vanishX, float zOff) {
            float railBL = -w * 0.04f;
            float railBR = w * 1.04f;
            float railTL = vanishX - w * 0.10f;
            float railTR = vanishX + w * 0.10f;

            int numPosts = 12;
            float postSpacing = 1.0f;

            for (int side = 0; side < 2; side++) {
                float bottomRX = (side == 0) ? railBL : railBR;
                float topRX = (side == 0) ? railTL : railTR;

                for (int i = 0; i < numPosts; i++) {
                    float rawZ = (i * postSpacing - (zOff * 0.10f)) % (numPosts * postSpacing);
                    if (rawZ < 0) rawZ += numPosts * postSpacing;
                    float depthFrac = rawZ / (numPosts * postSpacing);
                    float t = 1.0f - (float) Math.pow(depthFrac, 2.0);
                    if (t < 0.05f || t > 0.95f) continue;

                    float py = horizonY + (h - horizonY) * t;
                    float px = topRX + (bottomRX - topRX) * t;
                    float postH = (4f + 20f * t) * d;
                    float postW = (0.8f + 1.5f * t) * d;

                    int postAlpha = (int) (50 + 180 * t);

                    // Poste de baranda
                    guardRailPaint.setColor(Color.argb(postAlpha, 100, 110, 130));
                    guardRailPaint.setStrokeWidth(postW);
                    canvas.drawLine(px, py, px, py - postH, guardRailPaint);

                    // Riel horizontal (baranda metálica)
                    float railY = py - postH * 0.6f;
                    float nextT = 1.0f - (float) Math.pow(Math.min(1f, (rawZ + postSpacing * 0.5f) / (numPosts * postSpacing)), 2.0);
                    float nextPy = horizonY + (h - horizonY) * nextT;
                    float nextPx = topRX + (bottomRX - topRX) * nextT;
                    float nextRailY = nextPy - (4f + 20f * nextT) * d * 0.6f;

                    guardRailPaint.setStrokeWidth(postW * 0.6f);
                    guardRailPaint.setColor(Color.argb((int) (postAlpha * 0.6f), 150, 160, 175));
                    canvas.drawLine(px, railY, nextPx, nextRailY, guardRailPaint);

                    // Reflector naranja
                    float refSize = (1.5f + 3f * t) * d;
                    int refAlpha = (int) (80 + 175 * t);
                    reflectorPaint.setColor(Color.argb(refAlpha, 255, 165, 0));
                    canvas.drawCircle(px, py - postH * 0.75f, refSize, reflectorPaint);
                    // Brillo del reflector
                    reflectorPaint.setColor(Color.argb(refAlpha / 3, 255, 200, 50));
                    canvas.drawCircle(px, py - postH * 0.75f, refSize * 2.2f, reflectorPaint);
                }
            }
        }

        // ───────────────────────────────────────────────────
        // 7. POSTES DE LUZ CON HALOS
        // ───────────────────────────────────────────────────
        private void drawLampPosts(Canvas canvas, float w, float h, float horizonY, float vanishX, float zOff) {
            float railBR = w * 1.06f;
            float railTR = vanishX + w * 0.11f;

            int numLamps = 6;
            float lampSpacing = 2.0f;

            for (int i = 0; i < numLamps; i++) {
                float rawZ = (i * lampSpacing - (zOff * 0.08f)) % (numLamps * lampSpacing);
                if (rawZ < 0) rawZ += numLamps * lampSpacing;
                float depthFrac = rawZ / (numLamps * lampSpacing);
                float t = 1.0f - (float) Math.pow(depthFrac, 2.2);
                if (t < 0.04f || t > 0.96f) continue;

                float py = horizonY + (h - horizonY) * t;
                float px = railTR + (railBR - railTR) * t;
                float postHeight = (12f + 60f * t) * d;
                float postWidth = (1f + 2.5f * t) * d;

                int pAlpha = (int) (40 + 180 * t);

                // Poste vertical
                lampPostPaint.setColor(Color.argb(pAlpha, 60, 65, 80));
                lampPostPaint.setStrokeWidth(postWidth);
                canvas.drawLine(px, py, px, py - postHeight, lampPostPaint);

                // Brazo horizontal
                float armLen = (4f + 14f * t) * d;
                float armY = py - postHeight;
                lampPostPaint.setStrokeWidth(postWidth * 0.5f);
                canvas.drawLine(px, armY, px - armLen, armY + 2 * d, lampPostPaint);

                // Halo de luz del farol
                float glowR = (6f + 22f * t) * d;
                float lampX = px - armLen;
                float lampY = armY + 2 * d;

                try {
                    RadialGradient lampGlow = new RadialGradient(
                            lampX, lampY, glowR,
                            new int[]{0x55FFE082, 0x22FFE082, 0x00FFE082},
                            new float[]{0f, 0.5f, 1f},
                            Shader.TileMode.CLAMP);
                    lampGlowPaint.setShader(lampGlow);
                    canvas.drawCircle(lampX, lampY, glowR, lampGlowPaint);
                } catch (Throwable ignored) {}

                // Punto de luz
                lampGlowPaint.setShader(null);
                lampGlowPaint.setColor(Color.argb(pAlpha, 255, 240, 180));
                canvas.drawCircle(lampX, lampY, (1.5f + 2.5f * t) * d, lampGlowPaint);
            }
        }

        // ───────────────────────────────────────────────────
        // 8. CONO DE LUZ DE FAROS
        // ───────────────────────────────────────────────────
        private void drawHeadlights(Canvas canvas, float w, float h, float horizonY, float vanishX) {
            // Cono trapezoidal de luz iluminando la carretera desde la posición del carro
            Path cone = new Path();
            float coneBottomLeft = w * 0.28f;
            float coneBottomRight = w * 0.72f;
            float coneTopLeft = vanishX - w * 0.04f;
            float coneTopRight = vanishX + w * 0.04f;
            float coneBottom = h * 0.82f;
            float coneTop = horizonY + (h - horizonY) * 0.12f;

            cone.moveTo(coneBottomLeft, coneBottom);
            cone.lineTo(coneTopLeft, coneTop);
            cone.lineTo(coneTopRight, coneTop);
            cone.lineTo(coneBottomRight, coneBottom);
            cone.close();

            LinearGradient headlightGrad = new LinearGradient(0, coneBottom, 0, coneTop,
                    new int[]{0x18FFFFFF, 0x0DFFFFFF, 0x04FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.3f, 0.6f, 1f},
                    Shader.TileMode.CLAMP);
            headlightPaint.setShader(headlightGrad);
            canvas.drawPath(cone, headlightPaint);
        }

        // ───────────────────────────────────────────────────
        // 9. NISSAN MARCH 2015 CON FÍSICA DE SUSPENSIÓN
        // ───────────────────────────────────────────────────
        private void drawNissanMarch(Canvas canvas, float w, float h, boolean moving, long timeMs) {
            float carW = Math.min(w * 0.52f, 220f * d);
            float carH = carW * 0.92f;

            float cx = w * 0.50f;
            float cy = h * 0.62f;

            float offsetY = 0f;
            float rollDeg = 0f;
            float pitchOffset = 0f;

            if (moving) {
                float t = timeMs * 0.001f;
                // Suspensión multi-frecuencia: baches suaves + vibraciones finas del motor
                offsetY = (float) (Math.sin(t * 12.0) * 1.6f * d
                        + Math.sin(t * 7.3) * 0.8f * d
                        + Math.sin(t * 22.0) * 0.3f * d);
                // Balanceo lateral sutil
                rollDeg = (float) (Math.sin(t * 3.8) * 0.35f + Math.sin(t * 6.5) * 0.15f);
                // Cabeceo frontal/trasero
                pitchOffset = (float) (Math.sin(t * 5.2) * 0.6f * d);
            }

            // Sombra ovalada con gradiente de profundidad bajo los neumáticos
            float shadowW = carW * 0.90f;
            float shadowH = (10f + 8f * (moving ? 1f : 0f)) * d;
            float shadowY = cy + (carH * 0.46f) + offsetY;

            canvas.save();
            RectF shadowRect = new RectF(cx - shadowW * 0.5f, shadowY - shadowH * 0.5f,
                    cx + shadowW * 0.5f, shadowY + shadowH * 0.5f);
            try {
                RadialGradient shadowGrad = new RadialGradient(cx, shadowY, shadowW * 0.5f,
                        new int[]{0xBB000000, 0x66000000, 0x00000000},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP);
                shadowPaint.setShader(shadowGrad);
            } catch (Throwable ignored) {
                shadowPaint.setShader(null);
                shadowPaint.setColor(0xAA000000);
            }
            canvas.drawOval(shadowRect, shadowPaint);
            canvas.restore();

            // Dibujar carrocería
            canvas.save();
            canvas.translate(cx, cy + offsetY + pitchOffset);
            if (moving) {
                canvas.rotate(rollDeg);
            }

            if (carBmp != null) {
                Rect src = new Rect(0, 0, carBmp.getWidth(), carBmp.getHeight());
                RectF dst = new RectF(-carW * 0.5f, -carH * 0.5f, carW * 0.5f, carH * 0.5f);
                canvas.drawBitmap(carBmp, src, dst, null);
            } else {
                drawFallbackMarchVector(canvas, carW, carH);
            }

            canvas.restore();
        }

        private void drawFallbackMarchVector(Canvas canvas, float cw, float ch) {
            Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
            body.setColor(Color.WHITE);
            body.setStyle(Paint.Style.FILL);
            RectF rect = new RectF(-cw * 0.45f, -ch * 0.40f, cw * 0.45f, ch * 0.42f);
            canvas.drawRoundRect(rect, 24f * d, 24f * d, body);

            // Faros traseros rojos encendidos
            Paint tail = new Paint(Paint.ANTI_ALIAS_FLAG);
            tail.setColor(0xFFFF1744);
            canvas.drawCircle(-cw * 0.36f, -ch * 0.05f, 12f * d, tail);
            canvas.drawCircle(cw * 0.36f, -ch * 0.05f, 12f * d, tail);

            // Glow de faros traseros
            Paint tailGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
            tailGlow.setColor(0x44FF1744);
            canvas.drawCircle(-cw * 0.36f, -ch * 0.05f, 20f * d, tailGlow);
            canvas.drawCircle(cw * 0.36f, -ch * 0.05f, 20f * d, tailGlow);

            // Placa MARCH
            Paint plate = new Paint(Paint.ANTI_ALIAS_FLAG);
            plate.setColor(0xFFEEEEEE);
            plate.setStyle(Paint.Style.FILL);
            RectF pRect = new RectF(-35f * d, ch * 0.18f, 35f * d, ch * 0.32f);
            canvas.drawRoundRect(pRect, 4f * d, 4f * d, plate);

            Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
            pText.setColor(Color.BLACK);
            pText.setTextSize(10f * d);
            pText.setTextAlign(Paint.Align.CENTER);
            pText.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("MARCH", 0, ch * 0.28f, pText);
        }
    }
}
