package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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
 * Vista de conducción 3D inmersiva:
 * - Perspectiva de carretera nocturna en escala de grises y azul oscuro.
 * - Nissan March 2015 blanco en vista trasera siguiendo el carril central.
 * - Animación sutil de suspensión y líneas de carril en movimiento cuando el vehículo avanza.
 * - Escena 100% estática cuando el vehículo está detenido (velocidad = 0).
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
            android.widget.Toast.makeText(context, "🎙️ Saludo de bienvenida activado", android.widget.Toast.LENGTH_SHORT).show();
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
            oilKmText.setText(left < 0 ? "¡Ya toca el cambio!" : "Faltan " + OilActivity.fmt(left) + " km");
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
        bg.setColor(0xD90D1220); // Dark glassmorphism
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
        oilIcon.setText("🛢️");
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
        pbFill.setColor(0xFFFFB300); // Amber
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
     * Vista interna de dibujo de carretera en perspectiva 3D y carrocería del Nissan March 2015.
     */
    private static class RoadSurfaceView extends View {

        private final float d;
        private final Paint roadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint lanePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowLanePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint guardRailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap roadBgBmp;
        private Bitmap carBmp;

        private float speed = 0f;
        private float roadZOffset = 0f;
        private long lastFrameTime = 0;

        public RoadSurfaceView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;

            roadPaint.setColor(0xFF14171E);
            roadPaint.setStyle(Paint.Style.FILL);

            lanePaint.setColor(0xDDFFFFFF);
            lanePaint.setStyle(Paint.Style.STROKE);
            lanePaint.setStrokeCap(Paint.Cap.ROUND);

            glowLanePaint.setColor(0x4400E5FF);
            glowLanePaint.setStyle(Paint.Style.STROKE);
            glowLanePaint.setStrokeCap(Paint.Cap.ROUND);

            shadowPaint.setColor(0xAA000000);
            shadowPaint.setStyle(Paint.Style.FILL);

            guardRailPaint.setColor(0x4488A0B8);
            guardRailPaint.setStyle(Paint.Style.STROKE);
            guardRailPaint.setStrokeWidth(2f * d);

            try {
                roadBgBmp = BitmapFactory.decodeResource(c.getResources(), R.drawable.drive_road_bg);
            } catch (Throwable ignored) {}

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
                // Avance de carretera proporcional a la velocidad real
                float moveSpeed = Math.max(12f, speed * 0.7f);
                roadZOffset += dt * moveSpeed;
                if (roadZOffset > 10000f) roadZOffset = 0f;
            }

            // 1. Dibujar el fondo de carretera / paisaje en perspectiva
            if (roadBgBmp != null) {
                Rect src = new Rect(0, 0, roadBgBmp.getWidth(), roadBgBmp.getHeight());
                Rect dst = new Rect(0, 0, (int) w, (int) h);
                canvas.drawBitmap(roadBgBmp, src, dst, null);
            } else {
                // Fondo fallback: asfalto oscuro y horizonte
                canvas.drawColor(0xFF0F131D);
            }

            // 2. Líneas punteadas 3D de carril en perspectiva
            drawPerspectiveLanes(canvas, w, h, roadZOffset, isMoving);

            // 3. Vehículo: Nissan March 2015 Blanco
            drawNissanMarch(canvas, w, h, isMoving, now);

            // Si el vehículo está en movimiento, solicitar el siguiente frame para animación fluida a 60 FPS
            if (isMoving) {
                postInvalidateOnAnimation();
            }
        }

        /**
         * Dibuja las líneas blancas divisorias en proyección 3D realista con movimiento.
         */
        private void drawPerspectiveLanes(Canvas canvas, float w, float h, float zOffset, boolean moving) {
            float horizonY = h * 0.32f;
            float vanishX = w * 0.5f;

            float roadBottomLeft = w * 0.04f;
            float roadBottomRight = w * 0.96f;
            float roadTopLeft = vanishX - w * 0.14f;
            float roadTopRight = vanishX + w * 0.14f;

            // 2 líneas divisoras de carril (creando 3 carriles)
            float[] laneFractions = new float[]{0.35f, 0.65f};

            int numDashes = 14;
            float dashSpacing = 1.0f; // Espaciado en coordenadas de profundidad Z

            for (float frac : laneFractions) {
                float bottomX = roadBottomLeft + (roadBottomRight - roadBottomLeft) * frac;
                float topX = roadTopLeft + (roadTopRight - roadTopLeft) * frac;

                for (int i = 0; i < numDashes; i++) {
                    // Posición Z con desplazamiento continuo
                    float rawZ = (i * dashSpacing - (zOffset * 0.15f)) % (numDashes * dashSpacing);
                    if (rawZ < 0) rawZ += (numDashes * dashSpacing);

                    float depthFrac = rawZ / (numDashes * dashSpacing); // 0 (cerca) a 1 (horizonte)
                    // Perspectiva no lineal (más denso en el horizonte)
                    float t = 1.0f - (float) Math.pow(depthFrac, 1.8);

                    if (t < 0.05f || t > 0.98f) continue;

                    float dashY = horizonY + (h - horizonY) * t;
                    float dashX = topX + (bottomX - topX) * t;

                    float dashLength = (10f + 55f * t) * d;
                    float dashWidth = (1.5f + 4.5f * t) * d;

                    float startY = dashY - dashLength * 0.5f;
                    float endY = dashY + dashLength * 0.5f;

                    float startX = topX + (bottomX - topX) * ((startY - horizonY) / (h - horizonY));
                    float endX = topX + (bottomX - topX) * ((endY - horizonY) / (h - horizonY));

                    int alpha = (int) (255 * (0.25f + 0.75f * t));
                    lanePaint.setStrokeWidth(dashWidth);
                    lanePaint.setAlpha(alpha);

                    glowLanePaint.setStrokeWidth(dashWidth * 2.2f);
                    glowLanePaint.setAlpha((int) (alpha * 0.45f));

                    canvas.drawLine(startX, startY, endX, endY, glowLanePaint);
                    canvas.drawLine(startX, startY, endX, endY, lanePaint);
                }
            }
        }

        /**
         * Dibuja el Nissan March 2015 en vista trasera centrada con física de suspensión sutil.
         */
        private void drawNissanMarch(Canvas canvas, float w, float h, boolean moving, long timeMs) {
            float carW = Math.min(w * 0.52f, 220f * d);
            float carH = carW * 0.92f;

            float cx = w * 0.50f;
            float cy = h * 0.62f;

            float offsetY = 0f;
            float rollDeg = 0f;

            if (moving) {
                // Micro-vibraciones y balanceo de suspensión realista a velocidad de crucero
                float t = timeMs * 0.001f;
                offsetY = (float) (Math.sin(t * 14.0) * 1.8f * d + Math.sin(t * 8.5) * 0.9f * d);
                rollDeg = (float) (Math.sin(t * 4.2) * 0.40f);
            }

            // Sombra ovalada de contacto bajo los neumáticos
            canvas.save();
            float shadowW = carW * 0.88f;
            float shadowH = 14f * d;
            float shadowY = cy + (carH * 0.46f) + offsetY;
            RectF shadowRect = new RectF(cx - shadowW * 0.5f, shadowY - shadowH * 0.5f,
                    cx + shadowW * 0.5f, shadowY + shadowH * 0.5f);
            canvas.drawOval(shadowRect, shadowPaint);
            canvas.restore();

            // Dibujar carrocería del Nissan March 2015
            canvas.save();
            canvas.translate(cx, cy + offsetY);
            if (moving) {
                canvas.rotate(rollDeg);
            }

            if (carBmp != null) {
                Rect src = new Rect(0, 0, carBmp.getWidth(), carBmp.getHeight());
                RectF dst = new RectF(-carW * 0.5f, -carH * 0.5f, carW * 0.5f, carH * 0.5f);
                canvas.drawBitmap(carBmp, src, dst, null);
            } else {
                // Renderizado vectorial fallback elegante si no cargó el bitmap
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

