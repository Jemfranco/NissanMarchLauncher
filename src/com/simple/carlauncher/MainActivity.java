package com.simple.carlauncher;

import android.Manifest;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.text.TextUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.SeekBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends Activity implements NowPlaying.Listener {


    private static final String MAPS = "com.google.android.apps.maps/com.google.android.maps.MapsActivity";
    private static final String EQ = "com.nwd.audioset/.home_horizontalActivity";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private WebView mapWebView;
    private WaveVisualizerView musicCardVisualizer;
    private LocationManager locManager;
    private Location lastLoc;
    private final LocationListener locListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            if (loc == null) return;
            float kmh = loc.hasSpeed() ? loc.getSpeed() * 3.6f : 0f;
            if (lastLoc != null && (!loc.hasSpeed() || kmh <= 0.5f)) {
                float dist = lastLoc.distanceTo(loc);
                float dt = Math.max(0.5f, (loc.getTime() - lastLoc.getTime()) / 1000f);
                if (dt < 10f) {
                    float calc = (dist / dt) * 3.6f;
                    if (calc >= 2f && calc <= 200f) kmh = calc;
                }
            }
            lastLoc = loc;

            final float finalKmh = kmh;
            final double lat = loc.getLatitude();
            final double lng = loc.getLongitude();

            handler.post(() -> {
                if (speedometerView != null) speedometerView.setSpeed(finalKmh);
                if (driveRoadView != null) driveRoadView.setSpeed(finalKmh);
                if (mapWebView != null) {
                    mapWebView.evaluateJavascript("if (window.setPos) setPos(" + lat + "," + lng + ");", null);
                }
            });
        }
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };

    private static String cachedMapHtml = null;

    private String readRawString(int resId) {
        try (InputStream is = getResources().openRawResource(resId);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toString("UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }

    private String getMapHtml(boolean isDark) {
        String css = readRawString(R.raw.leaflet_css);
        String js = readRawString(R.raw.leaflet_js);

        return "<!DOCTYPE html><html><head>"
            + "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'/>"
            + "<style>"
            + css
            + "html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: " + (isDark ? "#0c101d" : "#e5e3df") + "; overflow: hidden; -webkit-tap-highlight-color: transparent; user-select: none; -webkit-user-select: none; transition: background 0.3s ease; }"
            + ".leaflet-control-attribution, .leaflet-control-zoom { display: none !important; }"
            + ".leaflet-container { background: " + (isDark ? "#0c101d" : "#e5e3df") + "; transition: background 0.3s ease; }"
            + "body.dark-map, body.dark-map #map, body.dark-map .leaflet-container { background: #0c101d !important; }"
            + "body.dark-map .leaflet-tile { filter: invert(100%) hue-rotate(180deg) brightness(88%) contrast(125%) saturate(130%); -webkit-filter: invert(100%) hue-rotate(180deg) brightness(88%) contrast(125%) saturate(130%); }"
            + ".nav-box { position: relative; width: 38px; height: 38px; display: flex; align-items: center; justify-content: center; pointer-events: none; }"
            + ".nav-pulse { position: absolute; width: 38px; height: 38px; border-radius: 50%; background: rgba(0, 140, 255, 0.35); box-shadow: 0 0 16px rgba(0, 140, 255, 0.7); animation: ring-pulse 1.8s infinite ease-out; }"
            + ".nav-arrow { width: 0; height: 0; border-left: 9px solid transparent; border-right: 9px solid transparent; border-bottom: 22px solid #0066FF; filter: drop-shadow(0 0 6px rgba(0, 80, 220, 0.9)); z-index: 5; }"
            + "body.dark-map .nav-arrow { border-bottom-color: #00E5FF !important; filter: drop-shadow(0 0 8px #00E5FF) !important; }"
            + "body.dark-map .nav-pulse { background: rgba(0, 229, 255, 0.25) !important; box-shadow: 0 0 14px #00E5FF !important; }"
            + ".dest-pin { position: relative; width: 36px; height: 36px; display: flex; align-items: center; justify-content: center; font-size: 26px; filter: drop-shadow(0 3px 6px rgba(0,0,0,0.45)); animation: bounce 1s infinite alternate; cursor: pointer; }"
            + "@keyframes bounce { 0% { transform: translateY(0); } 100% { transform: translateY(-7px); } }"
            + "@keyframes ring-pulse { 0% { transform: scale(0.5); opacity: 1; } 100% { transform: scale(1.6); opacity: 0; } }"
            + "</style>"
            + "<script>"
            + js
            + "</script>"
            + "</head><body class='" + (isDark ? "dark-map" : "") + "'>"
            + "<div id='map'></div>"
            + "<script>"
            + "var map = L.map('map', {zoomControl: false, attributionControl: false, dragging: true, touchZoom: true, tap: false, doubleClickZoom: true, bounceAtZoomLimits: false}).setView([10.9685, -74.7813], 17);"
            + "L.tileLayer('https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {subdomains: ['0', '1', '2', '3'], maxZoom: 20}).addTo(map);"
            + "var carIcon = L.divIcon({ className: 'custom-car', html: '<div class=\"nav-box\"><div class=\"nav-pulse\"></div><div class=\"nav-arrow\"></div></div>', iconSize: [38, 38], iconAnchor: [19, 19] });"
            + "var marker = L.marker([10.9685, -74.7813], {icon: carIcon}).addTo(map);"
            + "var destMarker = null;"
            + "function setPos(lat, lng) { var z = map.getZoom(); map.setView([lat, lng], z > 0 ? z : 17); marker.setLatLng([lat, lng]); }"
            + "function setDarkMode(dark) { if (dark) document.body.classList.add('dark-map'); else document.body.classList.remove('dark-map'); }"
            + "function showDestination(lat, lng, label) {"
            + "  if (destMarker) { map.removeLayer(destMarker); }"
            + "  var destIcon = L.divIcon({ className: 'custom-dest', html: '<div class=\"dest-pin\">📍</div>', iconSize: [36, 36], iconAnchor: [18, 34] });"
            + "  destMarker = L.marker([lat, lng], {icon: destIcon}).addTo(map);"
            + "  destMarker.bindPopup('<div style=\"text-align:center; padding:4px;\"><b>' + label + '</b><br><br><span style=\"color:#0088cc; font-size:12px;\">Toca para navegar en Waze</span></div>').openPopup();"
            + "  map.flyTo([lat, lng], 17);"
            + "}"
            + "function zoomIn() { map.zoomIn(); }"
            + "function zoomOut() { map.zoomOut(); }"
            + "var touchStartTime = 0; var touchStartX = 0; var touchStartY = 0; var hasMoved = false;"
            + "var mapDiv = document.getElementById('map');"
            + "mapDiv.addEventListener('touchstart', function(e) {"
            + "  touchStartTime = Date.now();"
            + "  hasMoved = false;"
            + "  if (e.touches.length === 1) {"
            + "    touchStartX = e.touches[0].clientX; touchStartY = e.touches[0].clientY;"
            + "  } else {"
            + "    hasMoved = true;"
            + "  }"
            + "}, {passive: true});"
            + "mapDiv.addEventListener('touchmove', function(e) {"
            + "  if (e.touches.length > 1) { hasMoved = true; }"
            + "  else if (e.touches.length === 1) {"
            + "    var dx = Math.abs(e.touches[0].clientX - touchStartX);"
            + "    var dy = Math.abs(e.touches[0].clientY - touchStartY);"
            + "    if (dx > 8 || dy > 8) { hasMoved = true; }"
            + "  }"
            + "}, {passive: true});"
            + "map.on('click', function(e) {"
            + "  var dt = Date.now() - touchStartTime;"
            + "  if (!hasMoved && dt < 450) {"
            + "    if (window.AndroidMap) { window.AndroidMap.onMapClicked(); }"
            + "  }"
            + "});"
            + "</script></body></html>";
    }
    private NowPlaying nowPlaying;

    private TextView greeting, weatherTemp, weatherDesc, weatherCity, weatherExtra;
    private ImageView weatherIcon;
    private GradientDrawable weatherBg;
    private ImageView car, carLights;
    private boolean hasLightsImage;
    private boolean lightsOn;
    private TextView oilInfo;
    private boolean createdDark;
    private boolean createdMapDark;
    private String createdProfile;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified() + "|" + Ui.wallpaper(this) + "|" + Ui.isMapDark(this);
    }
    private GradientDrawable oilBg;
    private long lastWeatherFetch;

    private TextView fmBand, fmFreq, fmUnit;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist;

    // Vista de Manejo (Cockpit Mode)
    private boolean isDriveViewMode = true;
    private DriveRoadView driveRoadView;
    private ArcSpeedometerView speedometerView;

    // Cockpit Music Card Views
    private ImageView cockpitArt;
    private TextView cockpitTitle, cockpitArtist, cockpitFav;
    private TextView cockpitTimeCurrent, cockpitTimeTotal;
    private SeekBar cockpitSeekBar;
    private ImageView cockpitShuffleBtn, cockpitRepeatBtn, cockpitPlayIcon;
    private View cockpitPlayBtn;
    private boolean isUserScrubbing = false;
    private boolean isFavoriteTrack = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isDriveViewMode = getSharedPreferences("launcher_prefs", MODE_PRIVATE)
                .getBoolean("drive_view_mode", true);

        boolean compact = isCompact();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(compact ? 8 : 12), dp(compact ? 6 : 8), dp(compact ? 8 : 12), dp(compact ? 2 : 4));
        root.setBackground(Ui.background(this));

        View cards = buildCardsContainer(compact);
        root.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildDock(compact), new LinearLayout.LayoutParams(-1, dp(compact ? 56 : 74)));
        setContentView(root);

        radio.bind(this);
        nowPlaying = NowPlaying.get(this);
        createdDark = Ui.isDark(this);
        createdMapDark = Ui.isMapDark(this);
        createdProfile = profileStamp();
        // Shared installs: ask for name, car photo and permissions on first start.
        if (!Profile.isSetupDone(this) && Profile.bundledCar(this, false) == 0) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) startService(new Intent(this, VolumeOverlay.class));
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));
    }

    private void toggleViewMode() {
        isDriveViewMode = !isDriveViewMode;
        getSharedPreferences("launcher_prefs", MODE_PRIVATE).edit()
                .putBoolean("drive_view_mode", isDriveViewMode).apply();
        recreate();
    }

    // ---------- Customización Dinámica de Tarjetas (Card Layout System) ----------

    private static final String[][] AVAILABLE_CARDS = {
        {"road", "🚗 Carretera 3D (Nissan March + Reloj + Aceite)"},
        {"speedo", "⚡ Velocímetro HUD (Digital 0-200 km/h)"},
        {"music_cockpit", "🎵 Música Cockpit (Moderna con Timeline)"},
        {"map", "🗺️ Mapa en Vivo (Google Maps + Waze)"},
        {"clock", "⏰ Reloj Grande (Hora + Fecha + Aceite)"},
        {"car", "🚙 Nissan March Clásico (Clima + Luces)"},
        {"music_classic", "🎶 Música Clásica (Visualizador de Ondas)"},
        {"radio", "📻 Radio FM (Dial y Emisoras)"},
        {"none", "❌ Ocultar (Expandir las demás tarjetas)"}
    };

    private View createCardByType(String type, boolean compact) {
        if ("road".equals(type)) {
            driveRoadView = new DriveRoadView(this);
            driveRoadView.setLights(lightsOn);
            driveRoadView.updateOil();
            return driveRoadView;
        } else if ("speedo".equals(type)) {
            return buildSpeedometerCard(compact);
        } else if ("music_cockpit".equals(type)) {
            return buildCockpitMusicCard(compact);
        } else if ("map".equals(type)) {
            return buildMapCard(compact);
        } else if ("clock".equals(type)) {
            return buildClockCard(compact);
        } else if ("car".equals(type)) {
            return buildCarCard(compact);
        } else if ("music_classic".equals(type)) {
            return buildMusicCard(compact);
        } else if ("radio".equals(type)) {
            return buildFmCard(compact);
        }
        return null;
    }

    private View wrapCardWithSlot(View cardView, String slotKey) {
        if (cardView == null) return null;
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.addView(cardView, new FrameLayout.LayoutParams(-1, -1));

        // Botón elegante de 3 puntos (⋮) en la esquina superior derecha
        FrameLayout badgeContainer = new FrameLayout(this);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0x4418202E);
        badgeBg.setCornerRadius(dp(8));
        badgeBg.setStroke(dp(1), 0x26FFFFFF);
        badgeContainer.setBackground(badgeBg);

        TextView editBadge = Ui.text(this, "\u22EE", 16, 0xDDFFFFFF);
        editBadge.setGravity(Gravity.CENTER);
        editBadge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badgeContainer.addView(editBadge, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        Ui.pressable(badgeContainer, 0.85f);
        badgeContainer.setOnClickListener(v -> showCardOptionsDialog(slotKey));
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        elp.setMargins(0, dp(8), dp(8), 0);
        wrapper.addView(badgeContainer, elp);

        wrapper.setLongClickable(true);
        wrapper.setOnLongClickListener(v -> {
            showCardOptionsDialog(slotKey);
            return true;
        });
        cardView.setLongClickable(true);
        cardView.setOnLongClickListener(v -> {
            showCardOptionsDialog(slotKey);
            return true;
        });
        return wrapper;
    }

    private View buildCardsContainer(boolean compact) {
        LinearLayout cards = new LinearLayout(this);
        SharedPreferences prefs = getSharedPreferences("card_layout_prefs", MODE_PRIVATE);

        if (isDriveViewMode) {
            String slotMain = prefs.getString("slot_main", "road");
            String slotTop = prefs.getString("slot_top", "speedo");
            String slotBottom = prefs.getString("slot_bottom", "music_cockpit");
            boolean swapCols = prefs.getBoolean("swap_cols", false);

            float ratioMain = prefs.getFloat("ratio_main", 1.15f);
            float ratioSec = prefs.getFloat("ratio_sec", 1.25f);
            float ratioTop = prefs.getFloat("ratio_top", 1.12f);
            float ratioBottom = prefs.getFloat("ratio_bottom", 1.0f);

            View mainColView = null;
            if (!"none".equals(slotMain)) {
                View card = createCardByType(slotMain, compact);
                if (card != null) {
                    mainColView = wrapCardWithSlot(card, "slot_main");
                }
            }

            View secColView = null;
            boolean hasTop = !"none".equals(slotTop);
            boolean hasBottom = !"none".equals(slotBottom);

            if (hasTop && hasBottom) {
                LinearLayout rightCol = new LinearLayout(this);
                rightCol.setOrientation(LinearLayout.VERTICAL);
                View topCard = createCardByType(slotTop, compact);
                if (topCard != null) {
                    rightCol.addView(wrapCardWithSlot(topCard, "slot_top"), vweight(ratioTop, 0));
                }
                View bottomCard = createCardByType(slotBottom, compact);
                if (bottomCard != null) {
                    rightCol.addView(wrapCardWithSlot(bottomCard, "slot_bottom"), vweight(ratioBottom, dp(compact ? 6 : 10)));
                }
                secColView = rightCol;
            } else if (hasTop) {
                View topCard = createCardByType(slotTop, compact);
                if (topCard != null) secColView = wrapCardWithSlot(topCard, "slot_top");
            } else if (hasBottom) {
                View bottomCard = createCardByType(slotBottom, compact);
                if (bottomCard != null) secColView = wrapCardWithSlot(bottomCard, "slot_bottom");
            }

            if (mainColView != null && secColView != null) {
                if (swapCols) {
                    cards.addView(secColView, weight(ratioSec, 0));
                    cards.addView(mainColView, weight(ratioMain, dp(compact ? 8 : 12)));
                } else {
                    cards.addView(mainColView, weight(ratioMain, 0));
                    cards.addView(secColView, weight(ratioSec, dp(compact ? 8 : 12)));
                }
            } else if (mainColView != null) {
                cards.addView(mainColView, weight(1f, 0));
            } else if (secColView != null) {
                cards.addView(secColView, weight(1f, 0));
            } else {
                driveRoadView = new DriveRoadView(this);
                cards.addView(wrapCardWithSlot(driveRoadView, "slot_main"), weight(1f, 0));
            }
        } else {
            String cLeft = prefs.getString("classic_left", "map");
            String cMidTop = prefs.getString("classic_mid_top", "clock");
            String cMidBottom = prefs.getString("classic_mid_bottom", "car");
            String cRight = prefs.getString("classic_right", "music_classic");

            if (!"none".equals(cLeft)) {
                View leftCard = createCardByType(cLeft, compact);
                if (leftCard != null) {
                    cards.addView(wrapCardWithSlot(leftCard, "classic_left"), weight(1.25f, 0));
                }
            }

            boolean hasMidTop = !"none".equals(cMidTop);
            boolean hasMidBottom = !"none".equals(cMidBottom);
            if (hasMidTop && hasMidBottom) {
                LinearLayout middle = new LinearLayout(this);
                middle.setOrientation(LinearLayout.VERTICAL);
                View midTop = createCardByType(cMidTop, compact);
                if (midTop != null) {
                    middle.addView(wrapCardWithSlot(midTop, "classic_mid_top"), vweight(compact ? 0.75f : 0.62f, 0));
                }
                View midBottom = createCardByType(cMidBottom, compact);
                if (midBottom != null) {
                    middle.addView(wrapCardWithSlot(midBottom, "classic_mid_bottom"), vweight(compact ? 1.25f : 1.38f, dp(compact ? 8 : 12)));
                }
                cards.addView(middle, weight(1f, dp(compact ? 8 : 12)));
            } else if (hasMidTop) {
                View midTop = createCardByType(cMidTop, compact);
                if (midTop != null) cards.addView(wrapCardWithSlot(midTop, "classic_mid_top"), weight(1f, dp(compact ? 8 : 12)));
            } else if (hasMidBottom) {
                View midBottom = createCardByType(cMidBottom, compact);
                if (midBottom != null) cards.addView(wrapCardWithSlot(midBottom, "classic_mid_bottom"), weight(1f, dp(compact ? 8 : 12)));
            }

            if (!"none".equals(cRight)) {
                View rightCard = createCardByType(cRight, compact);
                if (rightCard != null) {
                    cards.addView(wrapCardWithSlot(rightCard, "classic_right"), weight(1.05f, dp(compact ? 8 : 12)));
                }
            }
        }
        return cards;
    }

    private void showCardOptionsDialog(String slotKey) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("⚙️ Tarjeta: " + getSlotTitle(slotKey));

        java.util.List<String> options = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();

        options.add("✏️ Cambiar contenido de esta tarjeta...");
        actions.add(() -> showSelectCardTypeDialog(slotKey));

        // Detectar si hay espacios vacíos para añadir directamente
        SharedPreferences prefs = getSharedPreferences("card_layout_prefs", MODE_PRIVATE);
        if (isDriveViewMode) {
            String slotMain = prefs.getString("slot_main", "road");
            String slotTop = prefs.getString("slot_top", "speedo");
            String slotBottom = prefs.getString("slot_bottom", "music_cockpit");

            if ("none".equals(slotBottom) && "slot_top".equals(slotKey)) {
                options.add("➕ Añadir tarjeta debajo (Dividir columna derecha)");
                actions.add(() -> showSelectCardTypeDialog("slot_bottom"));
            } else if ("none".equals(slotTop) && "slot_bottom".equals(slotKey)) {
                options.add("➕ Añadir tarjeta arriba (Dividir columna derecha)");
                actions.add(() -> showSelectCardTypeDialog("slot_top"));
            } else if ("none".equals(slotMain)) {
                options.add("➕ Añadir tarjeta en columna principal");
                actions.add(() -> showSelectCardTypeDialog("slot_main"));
            }
        }

        options.add("➕ Añadir / Restaurar una tarjeta en cualquier espacio...");
        actions.add(this::showAddCardChooserDialog);

        options.add("📐 Ajustar tamaños y proporciones...");
        actions.add(this::showAdjustSizeDialog);

        if (isDriveViewMode) {
            options.add("⇄ Invertir posición (Izquierda ↔ Derecha)");
            actions.add(this::toggleSwapColumns);
        }

        options.add("❌ Quitar / Ocultar esta tarjeta");
        actions.add(() -> hideSlot(slotKey));

        options.add("🎨 Administrador completo de pantalla...");
        actions.add(this::showLayoutCustomizationDialog);

        String[] optArray = options.toArray(new String[0]);
        b.setItems(optArray, (dialog, which) -> actions.get(which).run());
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void showAddCardChooserDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("➕ ¿En qué espacio deseas añadir o cambiar?");

        SharedPreferences prefs = getSharedPreferences("card_layout_prefs", MODE_PRIVATE);
        java.util.List<String> slotNames = new java.util.ArrayList<>();
        java.util.List<String> slotKeys = new java.util.ArrayList<>();

        if (isDriveViewMode) {
            String m = prefs.getString("slot_main", "road");
            String t = prefs.getString("slot_top", "speedo");
            String bt = prefs.getString("slot_bottom", "music_cockpit");

            slotNames.add(("none".equals(m) ? "➕ [VACÍO] " : "📍 ") + "Columna Principal (Izquierda/Derecha)" + ("none".equals(m) ? " (Disponible)" : " - Actual: " + getCardName(m)));
            slotKeys.add("slot_main");

            slotNames.add(("none".equals(t) ? "➕ [VACÍO] " : "📍 ") + "Columna Secundaria Superior" + ("none".equals(t) ? " (Disponible)" : " - Actual: " + getCardName(t)));
            slotKeys.add("slot_top");

            slotNames.add(("none".equals(bt) ? "➕ [VACÍO] " : "📍 ") + "Columna Secundaria Inferior" + ("none".equals(bt) ? " (Disponible)" : " - Actual: " + getCardName(bt)));
            slotKeys.add("slot_bottom");
        } else {
            String cl = prefs.getString("classic_left", "map");
            String cmt = prefs.getString("classic_mid_top", "clock");
            String cmb = prefs.getString("classic_mid_bottom", "car");
            String cr = prefs.getString("classic_right", "music_classic");

            slotNames.add(("none".equals(cl) ? "➕ [VACÍO] " : "📍 ") + "Columna Izquierda (Mapa)" + ("none".equals(cl) ? " (Disponible)" : " - Actual: " + getCardName(cl)));
            slotKeys.add("classic_left");

            slotNames.add(("none".equals(cmt) ? "➕ [VACÍO] " : "📍 ") + "Centro Superior (Reloj)" + ("none".equals(cmt) ? " (Disponible)" : " - Actual: " + getCardName(cmt)));
            slotKeys.add("classic_mid_top");

            slotNames.add(("none".equals(cmb) ? "➕ [VACÍO] " : "📍 ") + "Centro Inferior (Auto/Clima)" + ("none".equals(cmb) ? " (Disponible)" : " - Actual: " + getCardName(cmb)));
            slotKeys.add("classic_mid_bottom");

            slotNames.add(("none".equals(cr) ? "➕ [VACÍO] " : "📍 ") + "Columna Derecha (Música)" + ("none".equals(cr) ? " (Disponible)" : " - Actual: " + getCardName(cr)));
            slotKeys.add("classic_right");
        }

        b.setItems(slotNames.toArray(new String[0]), (dialog, which) -> {
            showSelectCardTypeDialog(slotKeys.get(which));
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void showSelectCardTypeDialog(String slotKey) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Elegir contenido (" + getSlotTitle(slotKey) + ")");

        String[] titles = new String[AVAILABLE_CARDS.length];
        for (int i = 0; i < AVAILABLE_CARDS.length; i++) {
            titles[i] = AVAILABLE_CARDS[i][1];
        }

        b.setItems(titles, (dialog, which) -> {
            String selectedType = AVAILABLE_CARDS[which][0];
            getSharedPreferences("card_layout_prefs", MODE_PRIVATE).edit()
                    .putString(slotKey, selectedType).apply();
            recreate();
        });
        b.setNegativeButton("Cancelar", null);
        b.show();
    }

    private void hideSlot(String slotKey) {
        getSharedPreferences("card_layout_prefs", MODE_PRIVATE).edit()
                .putString(slotKey, "none").apply();
        toast("Tarjeta ocultada. Puedes reactivarla desde el menú.");
        recreate();
    }

    private void toggleSwapColumns() {
        SharedPreferences prefs = getSharedPreferences("card_layout_prefs", MODE_PRIVATE);
        boolean swap = prefs.getBoolean("swap_cols", false);
        prefs.edit().putBoolean("swap_cols", !swap).apply();
        toast(swap ? "Columnas en posición original" : "Columnas invertidas");
        recreate();
    }

    private void showAdjustSizeDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("📐 Proporciones y Tamaños de Tarjeta");

        String[] options = {
            "Ancho: 50% Izquierda - 50% Derecha (Equitativo)",
            "Ancho: 60% Izquierda - 40% Derecha (Izquierda ancha)",
            "Ancho: 40% Izquierda - 60% Derecha (Derecha ancha)",
            "Ancho: 70% Izquierda - 30% Derecha (Izquierda gigante)",
            "Alto derecha: 50% Arriba - 50% Abajo (Equitativo)",
            "Alto derecha: 65% Arriba - 35% Abajo (Velocímetro grande)",
            "Alto derecha: 35% Arriba - 65% Abajo (Música grande)",
            "🔄 Restablecer proporciones originales"
        };

        b.setItems(options, (dialog, which) -> {
            SharedPreferences.Editor ed = getSharedPreferences("card_layout_prefs", MODE_PRIVATE).edit();
            switch (which) {
                case 0:
                    ed.putFloat("ratio_main", 1.0f).putFloat("ratio_sec", 1.0f);
                    break;
                case 1:
                    ed.putFloat("ratio_main", 1.5f).putFloat("ratio_sec", 1.0f);
                    break;
                case 2:
                    ed.putFloat("ratio_main", 1.0f).putFloat("ratio_sec", 1.5f);
                    break;
                case 3:
                    ed.putFloat("ratio_main", 2.3f).putFloat("ratio_sec", 1.0f);
                    break;
                case 4:
                    ed.putFloat("ratio_top", 1.0f).putFloat("ratio_bottom", 1.0f);
                    break;
                case 5:
                    ed.putFloat("ratio_top", 1.8f).putFloat("ratio_bottom", 1.0f);
                    break;
                case 6:
                    ed.putFloat("ratio_top", 1.0f).putFloat("ratio_bottom", 1.8f);
                    break;
                case 7:
                    ed.putFloat("ratio_main", 1.15f).putFloat("ratio_sec", 1.25f)
                      .putFloat("ratio_top", 1.12f).putFloat("ratio_bottom", 1.0f);
                    break;
            }
            ed.apply();
            recreate();
        });
        b.setNegativeButton("Cerrar", null);
        b.show();
    }

    private void showLayoutCustomizationDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("🎨 Personalizar Tarjetas y Pantalla");

        SharedPreferences prefs = getSharedPreferences("card_layout_prefs", MODE_PRIVATE);
        boolean swap = prefs.getBoolean("swap_cols", false);

        java.util.List<String> options = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();

        options.add("🔀 Alternar Modo General (" + (isDriveViewMode ? "2 Columnas Conducción" : "3 Columnas Clásico") + ")");
        actions.add(this::toggleViewMode);

        options.add("➕ Añadir / Restaurar una tarjeta a la pantalla...");
        actions.add(this::showAddCardChooserDialog);

        if (isDriveViewMode) {
            String slotMain = prefs.getString("slot_main", "road");
            String slotTop = prefs.getString("slot_top", "speedo");
            String slotBottom = prefs.getString("slot_bottom", "music_cockpit");

            options.add(("none".equals(slotMain) ? "➕ [VACÍO] " : "📍 ") + "Columna Principal (" + (swap ? "Derecha" : "Izquierda") + "): " + ("none".equals(slotMain) ? "Toca para Añadir" : getCardName(slotMain)));
            actions.add(() -> showSelectCardTypeDialog("slot_main"));

            options.add(("none".equals(slotTop) ? "➕ [VACÍO] " : "📍 ") + "Secundaria Superior: " + ("none".equals(slotTop) ? "Toca para Añadir" : getCardName(slotTop)));
            actions.add(() -> showSelectCardTypeDialog("slot_top"));

            options.add(("none".equals(slotBottom) ? "➕ [VACÍO] " : "📍 ") + "Secundaria Inferior: " + ("none".equals(slotBottom) ? "Toca para Añadir" : getCardName(slotBottom)));
            actions.add(() -> showSelectCardTypeDialog("slot_bottom"));

            options.add("⇄ Invertir posición de columnas (" + (swap ? "Principal en Derecha" : "Principal en Izquierda") + ")");
            actions.add(this::toggleSwapColumns);
        } else {
            String cl = prefs.getString("classic_left", "map");
            String cmt = prefs.getString("classic_mid_top", "clock");
            String cmb = prefs.getString("classic_mid_bottom", "car");
            String cr = prefs.getString("classic_right", "music_classic");

            options.add(("none".equals(cl) ? "➕ [VACÍO] " : "📍 ") + "Columna Izquierda: " + ("none".equals(cl) ? "Toca para Añadir" : getCardName(cl)));
            actions.add(() -> showSelectCardTypeDialog("classic_left"));

            options.add(("none".equals(cmt) ? "➕ [VACÍO] " : "📍 ") + "Centro Superior: " + ("none".equals(cmt) ? "Toca para Añadir" : getCardName(cmt)));
            actions.add(() -> showSelectCardTypeDialog("classic_mid_top"));

            options.add(("none".equals(cmb) ? "➕ [VACÍO] " : "📍 ") + "Centro Inferior: " + ("none".equals(cmb) ? "Toca para Añadir" : getCardName(cmb)));
            actions.add(() -> showSelectCardTypeDialog("classic_mid_bottom"));

            options.add(("none".equals(cr) ? "➕ [VACÍO] " : "📍 ") + "Columna Derecha: " + ("none".equals(cr) ? "Toca para Añadir" : getCardName(cr)));
            actions.add(() -> showSelectCardTypeDialog("classic_right"));
        }

        options.add("📐 Ajustar tamaños y proporciones...");
        actions.add(this::showAdjustSizeDialog);

        options.add("🔄 Restablecer todas las tarjetas de fábrica");
        actions.add(() -> {
            prefs.edit().clear().apply();
            toast("Tarjetas restablecidas a fábrica");
            recreate();
        });

        b.setItems(options.toArray(new String[0]), (dialog, which) -> actions.get(which).run());
        b.setNegativeButton("Cerrar", null);
        b.show();
    }

    private String getCardName(String type) {
        for (String[] pair : AVAILABLE_CARDS) {
            if (pair[0].equals(type)) return pair[1];
        }
        return type;
    }

    private String getSlotTitle(String slotKey) {
        if ("slot_main".equals(slotKey)) return "Columna Principal";
        if ("slot_top".equals(slotKey)) return "Columna Secundaria Superior";
        if ("slot_bottom".equals(slotKey)) return "Columna Secundaria Inferior";
        if ("classic_left".equals(slotKey)) return "Columna Izquierda";
        if ("classic_mid_top".equals(slotKey)) return "Centro Superior";
        if ("classic_mid_bottom".equals(slotKey)) return "Centro Inferior";
        if ("classic_right".equals(slotKey)) return "Columna Derecha";
        return slotKey;
    }

    private String formatTime(long ms) {
        if (ms < 0) return "0:00";
        long sec = ms / 1000;
        long min = sec / 60;
        sec = sec % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            if (nowPlaying != null) {
                long dur = nowPlaying.getDuration();
                long pos = nowPlaying.getPosition();
                if (dur > 0 && cockpitTimeTotal != null) {
                    cockpitTimeTotal.setText(formatTime(dur));
                    if (cockpitSeekBar != null && !isUserScrubbing) {
                        cockpitSeekBar.setMax((int) (dur / 1000));
                    }
                }
                if (pos >= 0 && cockpitTimeCurrent != null && !isUserScrubbing) {
                    cockpitTimeCurrent.setText(formatTime(pos));
                    if (cockpitSeekBar != null && !isUserScrubbing) {
                        cockpitSeekBar.setProgress((int) (pos / 1000));
                    }
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void toggleFavoriteTrack() {
        isFavoriteTrack = !isFavoriteTrack;
        if (cockpitFav != null) {
            cockpitFav.setText(isFavoriteTrack ? "♥" : "♡");
            cockpitFav.setTextColor(isFavoriteTrack ? 0xFFFF4081 : 0xCCFFFFFF);
        }
        toast(isFavoriteTrack ? "Canción añadida a favoritos ♥" : "Eliminada de favoritos");
    }

    private void updatePlaybackModesUi() {
        if (nowPlaying == null) return;
        int shuffle = nowPlaying.getShuffleMode();
        int repeat = nowPlaying.getRepeatMode();
        if (cockpitShuffleBtn != null) {
            cockpitShuffleBtn.setColorFilter(shuffle != 0 ? 0xFF00E5FF : 0x88FFFFFF);
        }
        if (cockpitRepeatBtn != null) {
            cockpitRepeatBtn.setColorFilter(repeat != 0 ? 0xFF00E5FF : 0x88FFFFFF);
        }
    }

    private View buildSpeedometerCard(boolean compact) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD90D1220);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x3300E5FF);
        card.setBackground(bg);
        card.setClipToOutline(true);

        speedometerView = new ArcSpeedometerView(this);
        speedometerView.setOnSpeedDemoListener(speed -> {
            if (driveRoadView != null) driveRoadView.setSpeed(speed);
        });
        card.addView(speedometerView, new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    private View buildCockpitMusicCard(boolean compact) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD90D1220);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x3300E5FF);
        card.setBackground(bg);
        card.setClipToOutline(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(compact ? 14 : 18), dp(compact ? 10 : 12), dp(compact ? 14 : 18), dp(compact ? 8 : 12));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        cockpitArt = new ImageView(this);
        cockpitArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int artSize = dp(compact ? 52 : 62);
        GradientDrawable artBg = new GradientDrawable();
        artBg.setColor(0x26FFFFFF);
        artBg.setCornerRadius(dp(12));
        cockpitArt.setBackground(artBg);
        cockpitArt.setClipToOutline(true);
        cockpitArt.setImageResource(R.drawable.ic_music);
        topRow.addView(cockpitArt, new LinearLayout.LayoutParams(artSize, artSize));

        LinearLayout infoCol = new LinearLayout(this);
        infoCol.setOrientation(LinearLayout.VERTICAL);
        infoCol.setPadding(dp(compact ? 10 : 14), 0, dp(8), 0);

        cockpitTitle = Ui.text(this, "Vida Bonita", compact ? 16 : 18, Color.WHITE);
        cockpitTitle.setTypeface(Typeface.DEFAULT_BOLD);
        cockpitTitle.setSingleLine(true);
        cockpitTitle.setEllipsize(TextUtils.TruncateAt.END);
        infoCol.addView(cockpitTitle);

        cockpitArtist = Ui.text(this, "Carlos Vives", compact ? 12 : 13, 0xB3FFFFFF);
        cockpitArtist.setSingleLine(true);
        cockpitArtist.setEllipsize(TextUtils.TruncateAt.END);
        cockpitArtist.setPadding(0, dp(2), 0, 0);
        infoCol.addView(cockpitArtist);

        topRow.addView(infoCol, new LinearLayout.LayoutParams(0, -2, 1f));

        cockpitFav = Ui.text(this, "♡", compact ? 20 : 24, 0xCCFFFFFF);
        cockpitFav.setPadding(dp(8), dp(4), dp(8), dp(4));
        Ui.pressable(cockpitFav, 0.85f);
        cockpitFav.setOnClickListener(v -> toggleFavoriteTrack());
        topRow.addView(cockpitFav, new LinearLayout.LayoutParams(-2, -2));

        content.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout seekRow = new LinearLayout(this);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        seekRow.setPadding(0, dp(compact ? 4 : 6), 0, dp(compact ? 2 : 4));

        cockpitTimeCurrent = Ui.text(this, "1:24", compact ? 10 : 11, 0x99FFFFFF);
        seekRow.addView(cockpitTimeCurrent, new LinearLayout.LayoutParams(-2, -2));

        cockpitSeekBar = createStyledSeekBar();
        LinearLayout.LayoutParams skLp = new LinearLayout.LayoutParams(0, -2, 1f);
        skLp.setMargins(dp(6), 0, dp(6), 0);
        seekRow.addView(cockpitSeekBar, skLp);

        cockpitTimeTotal = Ui.text(this, "3:56", compact ? 10 : 11, 0x99FFFFFF);
        seekRow.addView(cockpitTimeTotal, new LinearLayout.LayoutParams(-2, -2));

        content.addView(seekRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controlsRow = new LinearLayout(this);
        controlsRow.setOrientation(LinearLayout.HORIZONTAL);
        controlsRow.setGravity(Gravity.CENTER);
        controlsRow.setPadding(0, dp(compact ? 2 : 4), 0, 0);

        View shuffleView = Ui.iconButton(this, R.drawable.ic_shuffle, compact ? 34 : 40, false, () -> {
            nowPlaying.toggleShuffle();
            updatePlaybackModesUi();
        });
        if (shuffleView instanceof ImageView) cockpitShuffleBtn = (ImageView) shuffleView;
        controlsRow.addView(shuffleView, new LinearLayout.LayoutParams(dp(compact ? 34 : 40), dp(compact ? 34 : 40)));

        View prevBtn = Ui.iconButton(this, R.drawable.ic_prev, compact ? 40 : 46, false, () -> nowPlaying.skip(false));
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(dp(compact ? 40 : 46), dp(compact ? 40 : 46));
        prevLp.setMargins(dp(compact ? 12 : 18), 0, dp(compact ? 12 : 18), 0);
        controlsRow.addView(prevBtn, prevLp);

        cockpitPlayBtn = createGlowingPlayButton(compact);
        controlsRow.addView(cockpitPlayBtn, new LinearLayout.LayoutParams(dp(compact ? 50 : 58), dp(compact ? 50 : 58)));

        View nextBtn = Ui.iconButton(this, R.drawable.ic_next, compact ? 40 : 46, false, () -> nowPlaying.skip(true));
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(dp(compact ? 40 : 46), dp(compact ? 40 : 46));
        nextLp.setMargins(dp(compact ? 12 : 18), 0, dp(compact ? 12 : 18), 0);
        controlsRow.addView(nextBtn, nextLp);

        View repeatView = Ui.iconButton(this, R.drawable.ic_repeat, compact ? 34 : 40, false, () -> {
            nowPlaying.toggleRepeat();
            updatePlaybackModesUi();
        });
        if (repeatView instanceof ImageView) cockpitRepeatBtn = (ImageView) repeatView;
        controlsRow.addView(repeatView, new LinearLayout.LayoutParams(dp(compact ? 34 : 40), dp(compact ? 34 : 40)));

        content.addView(controlsRow, new LinearLayout.LayoutParams(-1, 0, 1f));

        card.addView(content, new FrameLayout.LayoutParams(-1, -1));
        return card;
    }

    private SeekBar createStyledSeekBar() {
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(35);
        sb.setPadding(dp(8), dp(8), dp(8), dp(8));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(3));

        GradientDrawable prog = new GradientDrawable();
        prog.setColor(0xFF00E5FF);
        prog.setCornerRadius(dp(3));

        android.graphics.drawable.ClipDrawable clipProg = new android.graphics.drawable.ClipDrawable(
                prog, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);

        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bg, clipProg});
        layer.setId(0, android.R.id.background);
        layer.setId(1, android.R.id.progress);
        sb.setProgressDrawable(layer);

        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(Color.WHITE);
        thumb.setSize(dp(16), dp(16));
        thumb.setStroke(dp(2), 0xFF00E5FF);
        sb.setThumb(thumb);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && cockpitTimeCurrent != null) {
                    cockpitTimeCurrent.setText(formatTime(progress * 1000L));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserScrubbing = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserScrubbing = false;
                if (nowPlaying != null) {
                    nowPlaying.seekTo(seekBar.getProgress() * 1000L);
                }
            }
        });
        return sb;
    }

    private View createGlowingPlayButton(boolean compact) {
        FrameLayout btn = new FrameLayout(this);
        int size = dp(compact ? 50 : 58);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x2200E5FF);
        bg.setStroke(dp(2), 0xFF00E5FF);
        btn.setBackground(bg);

        cockpitPlayIcon = new ImageView(this);
        cockpitPlayIcon.setImageResource(R.drawable.ic_play);
        cockpitPlayIcon.setColorFilter(Color.WHITE);
        int iconSize = dp(compact ? 26 : 30);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        btn.addView(cockpitPlayIcon, ilp);

        Ui.pressable(btn, 0.88f);
        btn.setOnClickListener(v -> nowPlaying.playPause());
        return btn;
    }

    // ---------- Greeting + weather ----------

        // ---------- Left Card: Live Map (Waze) ----------

    private View buildMapCard(boolean compact) {
        boolean mapDark = Ui.isMapDark(this);
        createdMapDark = mapDark;

        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.hasWallpaper(this) ? new int[]{0x59101424, 0x6E0A0D18}
                : (mapDark ? new int[]{0xFF14141E, 0xFF0D0D14} : new int[]{0xFFE8EEF5, 0xFFCBD5E1}));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Ui.hasWallpaper(this) ? 0x33FFFFFF : (mapDark ? 0x443FA9FF : 0x440066FF));
        card.setBackground(bg);
        card.setClipToOutline(true);

        mapWebView = new WebView(this);
        mapWebView.setBackgroundColor(0);
        WebSettings ws = mapWebView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);

        mapWebView.addJavascriptInterface(new Object() {
            @android.webkit.JavascriptInterface
            public void onMapClicked() {
                handler.post(() -> openWaze());
            }
            @android.webkit.JavascriptInterface
            public void openWazeWithQuery(String q) {
                handler.post(() -> MainActivity.this.openWazeWithQuery(q));
            }
        }, "AndroidMap");

        mapWebView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        mapWebView.setWebViewClient(new WebViewClient());
        mapWebView.loadDataWithBaseURL("https://maps.google.com", getMapHtml(mapDark), "text/html", "UTF-8", null);
        card.addView(mapWebView, new FrameLayout.LayoutParams(-1, -1));

        // Barra superior con Waze Chip y Botón Lupa de Búsqueda
        LinearLayout topBar = new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(compact ? 10 : 14), dp(compact ? 8 : 12), dp(compact ? 10 : 14), 0);

        TextView wazeChip = Ui.text(this, "🚗 WAZE", compact ? 11 : 12, Color.WHITE);
        wazeChip.setTypeface(Typeface.DEFAULT_BOLD);
        wazeChip.setPadding(dp(compact ? 10 : 14), dp(compact ? 5 : 7), dp(compact ? 10 : 14), dp(compact ? 5 : 7));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(0xEE00B0FF);
        chipBg.setCornerRadius(dp(14));
        wazeChip.setBackground(chipBg);
        Ui.pressable(wazeChip, 0.90f);
        wazeChip.setOnClickListener(v -> openWaze());
        topBar.addView(wazeChip);

        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView searchBtn = Ui.text(this, "🔍 Buscar", compact ? 11 : 12, mapDark ? Color.WHITE : 0xFF141E33);
        searchBtn.setTypeface(Typeface.DEFAULT_BOLD);
        searchBtn.setPadding(dp(compact ? 10 : 14), dp(compact ? 5 : 7), dp(compact ? 10 : 14), dp(compact ? 5 : 7));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(mapDark ? 0xEE111827 : 0xEEFFFFFF);
        sBg.setCornerRadius(dp(14));
        sBg.setStroke(dp(1), mapDark ? 0x8800E5FF : 0x880066FF);
        searchBtn.setBackground(sBg);
        Ui.pressable(searchBtn, 0.90f);
        searchBtn.setOnClickListener(v -> showSearchDialog());
        topBar.addView(searchBtn);

        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        card.addView(topBar, tlp);

        TextView bottomPrompt = Ui.text(this, "Toca el mapa para abrir Waze ↗", compact ? 10 : 12, mapDark ? 0xEEFFFFFF : 0xFF141E33);
        bottomPrompt.setPadding(dp(compact ? 10 : 14), dp(compact ? 4 : 6), dp(compact ? 10 : 14), dp(compact ? 4 : 6));
        GradientDrawable pBg = new GradientDrawable();
        pBg.setColor(mapDark ? 0xCC101422 : 0xEEFFFFFF);
        pBg.setCornerRadius(dp(12));
        pBg.setStroke(dp(1), mapDark ? 0x33FFFFFF : 0x440066FF);
        bottomPrompt.setBackground(pBg);
        Ui.pressable(bottomPrompt, 0.90f);
        bottomPrompt.setOnClickListener(v -> openWaze());
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(compact ? 8 : 12);
        card.addView(bottomPrompt, blp);

        LinearLayout zoomControls = new LinearLayout(this);
        zoomControls.setOrientation(LinearLayout.VERTICAL);
        zoomControls.setGravity(Gravity.CENTER);

        int zSize = dp(compact ? 30 : 36);

        TextView zoomIn = Ui.text(this, "+", compact ? 18 : 20, mapDark ? Color.WHITE : 0xFF141E33);
        zoomIn.setTypeface(Typeface.DEFAULT_BOLD);
        zoomIn.setGravity(Gravity.CENTER);
        GradientDrawable zBgIn = new GradientDrawable();
        zBgIn.setColor(mapDark ? 0xCC111827 : 0xEEFFFFFF);
        zBgIn.setCornerRadius(dp(8));
        zBgIn.setStroke(dp(1), mapDark ? 0x4400E5FF : 0x440066FF);
        zoomIn.setBackground(zBgIn);
        Ui.pressable(zoomIn, 0.88f);
        zoomIn.setOnClickListener(v -> {
            if (mapWebView != null) mapWebView.evaluateJavascript("zoomIn();", null);
        });
        zoomControls.addView(zoomIn, new LinearLayout.LayoutParams(zSize, zSize));

        TextView zoomOut = Ui.text(this, "−", compact ? 18 : 20, mapDark ? Color.WHITE : 0xFF141E33);
        zoomOut.setTypeface(Typeface.DEFAULT_BOLD);
        zoomOut.setGravity(Gravity.CENTER);
        GradientDrawable zBgOut = new GradientDrawable();
        zBgOut.setColor(mapDark ? 0xCC111827 : 0xEEFFFFFF);
        zBgOut.setCornerRadius(dp(8));
        zBgOut.setStroke(dp(1), mapDark ? 0x4400E5FF : 0x440066FF);
        zoomOut.setBackground(zBgOut);
        Ui.pressable(zoomOut, 0.88f);
        zoomOut.setOnClickListener(v -> {
            if (mapWebView != null) mapWebView.evaluateJavascript("zoomOut();", null);
        });
        LinearLayout.LayoutParams zolp = new LinearLayout.LayoutParams(zSize, zSize);
        zolp.topMargin = dp(compact ? 4 : 6);
        zoomControls.addView(zoomOut, zolp);

        FrameLayout.LayoutParams zlp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        zlp.setMargins(0, 0, dp(compact ? 8 : 12), dp(compact ? 8 : 12));
        card.addView(zoomControls, zlp);

        return card;
    }

    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(dp(20), dp(18), dp(20), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF00D1220);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x6600E5FF);
        dialogView.setBackground(bg);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = Ui.text(this, "🔍", 20, Color.WHITE);
        titleRow.addView(icon);
        TextView title = Ui.text(this, " Buscar Destino", 18, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(title);
        dialogView.addView(titleRow);

        TextView sub = Ui.text(this, "Escribe una dirección o lugar para ver en Google Maps o navegar en Waze:", 12, 0xAAFFFFFF);
        sub.setPadding(0, dp(4), 0, dp(12));
        dialogView.addView(sub);

        final EditText input = new EditText(this);
        input.setHint("Ej: Viva Barranquilla, C.C. Buenavista, Cra 53 # 80...");
        input.setHintTextColor(0x66FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(0x33FFFFFF);
        inBg.setCornerRadius(dp(10));
        inBg.setStroke(dp(1), 0x4400E5FF);
        input.setBackground(inBg);
        dialogView.addView(input);

        LinearLayout chipsRow = new LinearLayout(this);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(0, dp(10), 0, dp(14));

        String[] quickPlaces = new String[]{"🏠 Casa", "🏢 Trabajo", "⛽ Gasolina", "🛒 Supermercado"};
        for (String place : quickPlaces) {
            TextView chip = Ui.text(this, place, 12, Color.WHITE);
            chip.setPadding(dp(10), dp(5), dp(10), dp(5));
            GradientDrawable cBg = new GradientDrawable();
            cBg.setColor(0x22FFFFFF);
            cBg.setCornerRadius(dp(12));
            cBg.setStroke(dp(1), 0x33FFFFFF);
            chip.setBackground(cBg);
            Ui.pressable(chip, 0.9f);
            chip.setOnClickListener(v -> {
                String clean = place.substring(place.indexOf(' ') + 1);
                input.setText(clean);
                input.setSelection(clean.length());
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
            clp.rightMargin = dp(6);
            chipsRow.addView(chip, clp);
        }
        dialogView.addView(chipsRow);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);

        TextView cancelBtn = Ui.text(this, "Cancelar", 14, 0xAAFFFFFF);
        cancelBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
        Ui.pressable(cancelBtn, 0.9f);

        TextView mapBtn = Ui.text(this, "🗺️ Ver en Mapa", 14, Color.WHITE);
        mapBtn.setTypeface(Typeface.DEFAULT_BOLD);
        mapBtn.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable mBg = new GradientDrawable();
        mBg.setColor(0x3300E5FF);
        mBg.setCornerRadius(dp(10));
        mBg.setStroke(dp(1), 0x8800E5FF);
        mapBtn.setBackground(mBg);
        Ui.pressable(mapBtn, 0.9f);

        TextView wazeBtn = Ui.text(this, "🚗 Iniciar en Waze", 14, Color.WHITE);
        wazeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        wazeBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable wBg = new GradientDrawable();
        wBg.setColor(0xEE00B0FF);
        wBg.setCornerRadius(dp(10));
        wazeBtn.setBackground(wBg);
        Ui.pressable(wazeBtn, 0.9f);

        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, -2);
        mlp.leftMargin = dp(8);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(-2, -2);
        wlp.leftMargin = dp(8);

        btnRow.addView(cancelBtn);
        btnRow.addView(mapBtn, mlp);
        btnRow.addView(wazeBtn, wlp);
        dialogView.addView(btnRow);

        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        mapBtn.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            if (!q.isEmpty()) {
                dialog.dismiss();
                searchAndShowOnMap(q);
            }
        });

        wazeBtn.setOnClickListener(v -> {
            String q = input.getText().toString().trim();
            dialog.dismiss();
            if (!q.isEmpty()) {
                openWazeWithQuery(q);
            } else {
                openWaze();
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = input.getText().toString().trim();
                dialog.dismiss();
                if (!q.isEmpty()) {
                    searchAndShowOnMap(q);
                }
                return true;
            }
            return false;
        });

        dialog.setOnShowListener(d -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        dialog.show();
        input.requestFocus();
    }

    private void searchAndShowOnMap(String query) {
        Toast.makeText(this, "Buscando: " + query + "...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            double lat = 0, lng = 0;
            String foundName = query;
            try {
                String q = URLEncoder.encode(query + " Barranquilla", "UTF-8");
                URL url = new URL("https://photon.komoot.io/api/?q=" + q + "&limit=1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "NissanMarchLauncher/6.3");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject json = new JSONObject(sb.toString());
                    JSONArray features = json.optJSONArray("features");
                    if (features != null && features.length() > 0) {
                        JSONObject feat = features.getJSONObject(0);
                        JSONObject geom = feat.getJSONObject("geometry");
                        JSONArray coords = geom.getJSONArray("coordinates");
                        lng = coords.getDouble(0);
                        lat = coords.getDouble(1);
                        JSONObject props = feat.optJSONObject("properties");
                        if (props != null) {
                            foundName = props.optString("name", query);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            if (lat == 0 && lng == 0) {
                try {
                    String q = URLEncoder.encode(query + " Colombia", "UTF-8");
                    URL url = new URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + q);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "NissanMarchLauncher/6.3");
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        br.close();
                        JSONArray arr = new JSONArray(sb.toString());
                        if (arr.length() > 0) {
                            JSONObject item = arr.getJSONObject(0);
                            lat = item.getDouble("lat");
                            lng = item.getDouble("lon");
                            foundName = item.optString("display_name", query);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            final double fLat = lat;
            final double fLng = lng;
            final String fName = foundName.replace("'", "\\'");
            handler.post(() -> {
                if (fLat != 0 && fLng != 0) {
                    if (mapWebView != null) {
                        mapWebView.evaluateJavascript("showDestination(" + fLat + ", " + fLng + ", '" + fName + "');", null);
                    }
                    Toast.makeText(MainActivity.this, "Destino ubicado en el mapa", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Abriendo destino en Waze...", Toast.LENGTH_SHORT).show();
                    openWazeWithQuery(query);
                }
            });
        }).start();
    }

    private void openWaze() {
        Intent i = getPackageManager().getLaunchIntentForPackage("com.waze");
        if (i == null) {
            try {
                i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("waze://?favorite=home"));
            } catch (Exception e) {
                i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:10.9685,-74.7813?q=Waze"));
            }
        }
        safeStart(i);
    }

    private void openWazeWithQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            openWaze();
            return;
        }
        try {
            String enc = URLEncoder.encode(query, "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("waze://?q=" + enc + "&navigate=yes"));
            if (getPackageManager().resolveActivity(i, 0) != null) {
                safeStart(i);
                return;
            }
        } catch (Throwable ignored) {}
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=" + URLEncoder.encode(query, "UTF-8")));
            safeStart(i);
        } catch (Throwable ignored) {
            openWaze();
        }
    }

    // ---------- Center Bottom Card: Nissan March 2015 + Weather ----------

    private View buildCarCard(boolean compact) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(compact ? 14 : 20), dp(compact ? 10 : 16), dp(compact ? 14 : 20), dp(compact ? 8 : 14));
        weatherBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.hasWallpaper(this) ? new int[]{0x59101424, 0x6E0A0D18}
                : Ui.pick(this, new int[]{0x993FA9FF, 0x667B4DFF}, new int[]{0xFF1E1E28, 0xFF121218}));
        weatherBg.setCornerRadius(dp(18));
        weatherBg.setStroke(dp(1), Ui.hasWallpaper(this) ? 0x33FFFFFF : 0x447FD8FF);
        card.setBackground(weatherBg);

        TextView hello = left(Ui.text(this, Profile.hello(this), compact ? 19 : 23, Color.WHITE));
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(hello);

        greeting = left(Ui.text(this, "", compact ? 12 : 14, 0xCCFFFFFF));
        greeting.setPadding(0, dp(compact ? 1 : 2), 0, 0);
        card.addView(greeting);

        FrameLayout carBox = new FrameLayout(this);
        car = new ImageView(this);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights = new ImageView(this);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        android.graphics.Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false), bundledLights = Profile.bundledCar(this, true);
        if (own != null) {
            car.setImageBitmap(own);
        } else if (bundled != 0) {
            car.setImageResource(bundled);
            if (bundledLights != 0) {
                carLights.setImageResource(bundledLights);
                hasLightsImage = true;
            }
        } else {
            car.setImageResource(R.drawable.ic_car);
            car.setColorFilter(0x40FFFFFF);
        }
        carBox.addView(car, new FrameLayout.LayoutParams(-1, -1));
        carBox.addView(carLights, new FrameLayout.LayoutParams(-1, -1));
        carBox.setOnClickListener(v -> {
            lightsOn = !lightsOn;
            showCarLights(lightsOn);
        });
        carBox.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SetupActivity.class));
            return true;
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
        clp.setMargins(dp(compact ? 6 : 14), dp(compact ? 2 : 4), dp(compact ? 6 : 14), dp(compact ? 2 : 4));
        card.addView(carBox, clp);

        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(0xFFFFE07A);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(compact ? 26 : 34), dp(compact ? 26 : 34)));
        weatherTemp = left(Ui.text(this, "--°", compact ? 22 : 28, Color.WHITE));
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(compact ? 4 : 6), 0, dp(compact ? 6 : 8), 0);
        now.addView(weatherTemp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        weatherDesc = left(Ui.text(this, "Cargando clima…", compact ? 11 : 13, Color.WHITE));
        weatherDesc.setSingleLine(true);
        texts.addView(weatherDesc);
        weatherCity = left(Ui.text(this, "Barranquilla", compact ? 10 : 11, 0xB3FFFFFF));
        texts.addView(weatherCity);
        now.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(now);

        weatherExtra = left(Ui.text(this, "", 1, 0x00000000)); // hidden helper placeholder

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> {
            fetchWeather(true);
            sendBroadcast(new Intent("com.simple.carlauncher.TEST_CAR_ON"));
        });
        return card;
    }

    private void updateGreeting() {
        if (greeting == null) return;
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText((h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches")
                + " · Bienvenido");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        if (weatherIcon != null) {
            weatherIcon.setImageResource(w.icon());
            weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                    : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Color.WHITE);
        }
        if (weatherTemp != null) weatherTemp.setText(w.temp + "°");
        if (weatherDesc != null) weatherDesc.setText(w.description());
        if (weatherCity != null) {
            weatherCity.setText(w.city.isEmpty() ? "Sensación " + w.feelsLike + "°"
                    : w.city + " · Sensación " + w.feelsLike + "°");
        }
        if (weatherExtra != null) {
            weatherExtra.setText("↑ " + w.max + "°   ↓ " + w.min + "°   💧 " + w.humidity + "%   " + w.wind + " km/h");
        }
        if (weatherBg != null && !Ui.hasWallpaper(this)) {
            weatherBg.setColors(Ui.isDark(this) ? new int[]{0xFF1E1E28, 0xFF121218}
                    : w.isDay ? new int[]{0x993FA9FF, 0x667B4DFF} : new int[]{0x8C2A3A9E, 0x80261A6E});
        }
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable lightPoll = new Runnable() {
        @Override
        public void run() {
            boolean on = hasLightsImage && carLightsOn();
            if (on != lightsOn) {
                lightsOn = on;
                showCarLights(on);
                if (Ui.isDark(MainActivity.this) != createdDark || Ui.isMapDark(MainActivity.this) != createdMapDark) {
                    recreate();
                } else if (mapWebView != null) {
                    mapWebView.evaluateJavascript("setDarkMode(" + Ui.isMapDark(MainActivity.this) + ");", null);
                }
            }
            handler.postDelayed(this, 1500);
        }
    };

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark || Ui.isMapDark(MainActivity.this) != createdMapDark) recreate();
            updateGreeting();
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (ILL wire & Head units) ----------

    private final android.content.BroadcastReceiver illReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            String action = i.getAction();
            android.util.Log.d("CarLights", "ILL broadcast action=" + action + " extras=" + i.getExtras());
            boolean on = false;
            if ("com.xyauto.intent.action.ILLUMINATION_ON".equals(action)
                    || "android.intent.action.HEADLIGHT_ON".equals(action)) {
                on = true;
            } else if ("com.xyauto.intent.action.ILLUMINATION_OFF".equals(action)
                    || "android.intent.action.HEADLIGHT_OFF".equals(action)) {
                on = false;
            } else if (i.hasExtra("state")) {
                on = i.getBooleanExtra("state", false);
            } else if (i.hasExtra("extra_ill_state")) {
                on = i.getByteExtra("extra_ill_state", (byte) 0) != 0;
            } else {
                on = Ui.areCarLightsOn(c);
            }
            lightsOn = on;
            showCarLights(on);
            // Auto theme follows the car's lights; give the kernel a moment to store the state.
            handler.postDelayed(() -> {
                if (Ui.isDark(MainActivity.this) != createdDark || Ui.isMapDark(MainActivity.this) != createdMapDark) {
                    recreate();
                } else if (mapWebView != null) {
                    mapWebView.evaluateJavascript("setDarkMode(" + Ui.isMapDark(MainActivity.this) + ");", null);
                }
            }, 1000);
        }
    };

    private boolean carLightsOn() {
        return Ui.areCarLightsOn(this);
    }

    private void showCarLights(boolean on) {
        if (driveRoadView != null) driveRoadView.setLights(on);
        // Cross-fade between the "lights off" and "lights on" photos.
        if (!hasLightsImage || carLights == null || car == null) return;
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    private void updateOil() {
        if (driveRoadView != null) driveRoadView.updateOil();
        if (oilInfo == null || oilBg == null) return;
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite: configurar");
            oilBg.setColor(0x26FFFFFF);
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
    }

    // ---------- Clock ----------

    private View buildClockCard(boolean compact) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(compact ? 32 : 40);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        card.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(compact ? 12 : 14);
        date.setTextColor(0xCCFFFFFF);
        date.setPadding(0, dp(compact ? 2 : 6), 0, 0);
        date.setGravity(Gravity.CENTER);
        card.addView(date);

        // Oil-change status pill; opens the oil screen.
        oilInfo = Ui.text(this, "", compact ? 11 : 13, Color.WHITE);
        oilInfo.setPadding(dp(compact ? 10 : 14), dp(compact ? 4 : 6), dp(compact ? 10 : 14), dp(compact ? 4 : 6));
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(14));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.92f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.topMargin = dp(compact ? 4 : 8);
        card.addView(oilInfo, olp);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openSettings());
        return card;
    }

    // ---------- FM ----------

    private View buildFmCard(boolean compact) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER);
        ImageView radioIcon = new ImageView(this);
        radioIcon.setImageResource(R.drawable.ic_radio);
        radioIcon.setColorFilter(0xFFFF9F5A);
        head.addView(radioIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        fmBand = Ui.text(this, "RADIO FM", 15, Color.WHITE);
        fmBand.setPadding(dp(8), 0, 0, 0);
        head.addView(fmBand);
        card.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.addView(Ui.iconButton(this, R.drawable.ic_prev, 52, false, () -> stationStep(false)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        fmFreq = Ui.text(this, "--.-", 50, Color.WHITE);
        fmFreq.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(fmFreq, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.iconButton(this, R.drawable.ic_next, 52, false, () -> stationStep(true)),
                new LinearLayout.LayoutParams(dp(52), dp(52)));
        row.setPadding(dp(12), dp(10), dp(12), 0);
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));

        fmUnit = Ui.text(this, "MHz", 15, 0x99FFFFFF);
        card.addView(fmUnit);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openRadio());
        return card;
    }

    private void stationStep(boolean next) {
        if (!Profile.isNwd(this)) {
            openRadio();
            return;
        }
        if (!Ui.radioIsSource(this)) {
            Ui.selectRadio(this, radio); // first tap from Bluetooth: bring the radio back
            handler.postDelayed(this::updateRadio, 2500);
            return;
        }
        radio.preset(next);
        handler.postDelayed(this::updateRadio, 300);
    }

    private void updateRadio() {
        RadioClient.Station st = radio.current();
        if (st == null) return;
        if (fmBand != null) fmBand.setText(st.psName.isEmpty() ? "RADIO " + st.bandLabel() : st.bandLabel() + " · " + st.psName);
        if (fmFreq != null) fmFreq.setText(st.frequencyLabel());
        if (fmUnit != null) fmUnit.setText(st.isAm() ? "kHz" : "MHz");
    }

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            updateRadio();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Music (any source) ----------

    private View buildMusicCard(boolean compact) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                Ui.hasWallpaper(this) ? new int[]{0x591E1035, 0x6E0F071C}
                : Ui.pick(this, new int[]{0xFF6A3BFF, 0xFFC04DE8, 0xFF2B1A7A},
                        new int[]{0xFF2E1A55, 0xFF1A1030, 0xFF0A0A0E}));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Ui.hasWallpaper(this) ? 0x33FFFFFF : 0x00000000);
        card.setBackground(bg);
        card.setClipToOutline(true);

        // Visualizador animado de ondas de fondo en la tarjeta principal
        musicCardVisualizer = new WaveVisualizerView(this);
        card.addView(musicCardVisualizer, new FrameLayout.LayoutParams(-1, -1));

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x44000000, 0x11000000, 0xDD100A30}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        songSource = Ui.text(this, "MÚSICA", 12, Color.WHITE);
        songSource.setLetterSpacing(0.1f);
        songSource.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x40000000);
        chip.setCornerRadius(dp(14));
        songSource.setBackground(chip);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        slp.setMargins(dp(14), dp(14), 0, 0);
        card.addView(songSource, slp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), 0, dp(16), dp(18));
        songTitle = Ui.text(this, "", 21, Color.WHITE);
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(songTitle);
        songArtist = Ui.text(this, "", 15, 0xCCFFFFFF);
        songArtist.setPadding(0, dp(2), 0, dp(14));
        bottom.addView(songArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 50, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(50), dp(50)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 66, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(66), dp(66));
        plp.setMargins(dp(18), 0, dp(18), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 50, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(50), dp(50)));
        bottom.addView(controls);
        card.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> startActivity(new Intent(this, MusicActivity.class)));
        card.setOnLongClickListener(v -> {
            nowPlaying.open(this);
            return true;
        });
        return card;
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean hasNotifAccess = NowPlaying.isNotificationListenerEnabled(this);
        boolean empty = t == null || t.title.isEmpty();

        if (musicCardVisualizer != null) {
            musicCardVisualizer.setPlaying(t != null && t.playing);
        }

        if (!hasNotifAccess && empty) {
            if (songSource != null) songSource.setText("PERMISO REQUERIDO");
            if (songTitle != null) songTitle.setText("Música desactivada");
            if (songArtist != null) songArtist.setText("Toca aquí para permitir acceso a notificaciones");
            if (playBtn != null) playBtn.setImageResource(R.drawable.ic_play);
        } else {
            if (songSource != null) songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
            if (songTitle != null) songTitle.setText(empty ? "Nada sonando" : t.title);
            if (songArtist != null) songArtist.setText(empty ? "Toca para abrir la música" : t.artist);
            if (playBtn != null) playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }

        if (art != null) {
            if (t != null && t.art != null) {
                art.setPadding(0, 0, 0, 0);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.clearColorFilter();
                art.setAlpha(0.40f);
                art.setImageBitmap(t.art);
            } else {
                art.setPadding(dp(80), dp(56), dp(80), dp(170));
                art.setScaleType(ImageView.ScaleType.FIT_CENTER);
                art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
                art.setColorFilter(Color.WHITE);
                art.setAlpha(0.20f);
            }
        }

        // Actualización de la tarjeta de música estilo Cockpit
        if (cockpitTitle != null) {
            cockpitTitle.setText(empty ? "Vida Bonita" : t.title);
        }
        if (cockpitArtist != null) {
            cockpitArtist.setText(empty ? "Carlos Vives" : t.artist);
        }
        if (cockpitPlayIcon != null) {
            cockpitPlayIcon.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        if (cockpitArt != null) {
            if (t != null && t.art != null) {
                cockpitArt.setImageBitmap(t.art);
                cockpitArt.clearColorFilter();
            } else {
                cockpitArt.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
                cockpitArt.setColorFilter(0x88FFFFFF);
            }
        }
        if (cockpitTimeTotal != null) {
            long dur = nowPlaying.getDuration();
            if (dur > 0) {
                cockpitTimeTotal.setText(formatTime(dur));
                if (cockpitSeekBar != null && !isUserScrubbing) {
                    cockpitSeekBar.setMax((int) (dur / 1000));
                }
            }
        }
        updatePlaybackModesUi();
    }

    private void openLauncherSettings() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("⚙️ Ajustes y Configuración");
        String[] opts = {
            "🚗 Configuración del Launcher (Nombre, Auto, Bienvenida por Voz, Odómetro)",
            "🎨 Personalizar Tarjetas de Pantalla (Añadir, Quitar, Mover, Tamaños)",
            "📱 Ajustes del Sistema Android"
        };
        b.setItems(opts, (dialog, which) -> {
            if (which == 0) startActivity(new Intent(this, SetupActivity.class));
            else if (which == 1) showLayoutCustomizationDialog();
            else openSettings();
        });
        b.setNegativeButton("Cerrar", null);
        b.show();
    }

    // ---------- Dock ----------

    private View buildDock(boolean compact) {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(dockIcon(isDriveViewMode ? R.drawable.ic_compass : R.drawable.ic_car,
                this::toggleViewMode, () -> openWaze(), compact), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_spotify, this::openSpotify, this::openBluetooth, compact), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_radio, this::openRadio, compact), weight(1f, 0));

        FrameLayout center = new FrameLayout(this);
        RingView ring = new RingView(this);
        int ringSize = dp(compact ? 48 : 64);
        center.addView(ring, new FrameLayout.LayoutParams(ringSize, ringSize, Gravity.CENTER));
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        ring.setOnLongClickListener(v -> {
            showLayoutCustomizationDialog();
            return true;
        });
        dock.addView(center, weight(1f, 0));

        dock.addView(dockIcon(R.drawable.ic_w_moon,
                () -> startActivity(new Intent(this, ScreenOffActivity.class)), compact), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_youtube, this::openYouTube, this::openEqualizer, compact), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_gear, this::openSettings,
                this::openLauncherSettings, compact), weight(1f, 0));
        if (Ui.hasWallpaper(this)) {
            GradientDrawable dockBg = new GradientDrawable();
            dockBg.setColor(0x40000000);
            dockBg.setCornerRadius(dp(compact ? 16 : 22));
            dockBg.setStroke(dp(1), 0x22FFFFFF);
            dock.setBackground(dockBg);
        }
        return dock;
    }

    private View dockIcon(int icon, Runnable action, boolean compact) {
        return dockIcon(icon, action, null, compact);
    }

    private View dockIcon(int icon, Runnable action, Runnable longAction, boolean compact) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xE6FFFFFF);
        int pad = dp(compact ? 8 : 12);
        iv.setPadding(pad, pad, pad, pad);
        int size = dp(compact ? 44 : 56);
        f.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        if (longAction != null) {
            iv.setOnLongClickListener(v -> {
                longAction.run();
                return true;
            });
        }
        return f;
    }

    // ---------- Shortcuts: NWD screens, or the head unit's own apps elsewhere ----------

    private void openRadio() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, RadioActivity.class));
        else if (!launchByKeyword("radio", "fm", "tuner")) toast("No se encontró una app de radio");
    }

    private void openBluetooth() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, BtActivity.class));
        else if (!launchByKeyword("bluetooth", "bt music", "btmusic", "phone link"))
            safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void openSpotify() {
        if (launchByKeyword("spotify", "com.spotify.music", "com.spotify.lite")) return;
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.spotify.music");
            if (intent != null && safeStart(intent)) return;
        } catch (Throwable ignored) {}
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com"));
            safeStart(web);
        } catch (Throwable t) {
            toast("Spotify no está instalado");
        }
    }

    private void openYouTube() {
        if (launchByKeyword("youtube", "com.google.android.youtube", "newpipe", "revanced")) return;
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            if (intent != null && safeStart(intent)) return;
        } catch (Throwable ignored) {}
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"));
            safeStart(web);
        } catch (Throwable t) {
            toast("YouTube no está instalado");
        }
    }

    private void openEqualizer() {
        if (Profile.isNwd(this)) {
            startActivity(new Intent(this, EqActivity.class));
            return;
        }
        // 1. Saved custom app chosen by the user
        String savedPkg = getSharedPreferences("ui", MODE_PRIVATE).getString("custom_eq_pkg", "");
        if (!savedPkg.isEmpty()) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(savedPkg);
            if (launch != null && safeStart(launch)) return;
        }
        // 2. Direct XY AUTO / head unit DSP packages
        String[] eqPackages = {
                "com.xyauto.dsp",
                "com.xyauto.equalizer",
                "com.xyauto.eq",
                "com.xyauto.sound",
                "com.ts.main.eq",
                "com.forfan.dsp",
                "com.microntek.eq"
        };
        for (String pkg : eqPackages) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null && safeStart(launch)) return;
        }
        Intent eq = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
        if (safeStart(eq)) return;
        if (launchEqByKeyword()) return;
        showSelectEqualizerDialog();
    }

    private void showSelectEqualizerDialog() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> list = pm.queryIntentActivities(main, 0);
        List<CharSequence> names = new ArrayList<>();
        List<String> packages = new ArrayList<>();
        for (android.content.pm.ResolveInfo ri : list) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            names.add(ri.loadLabel(pm));
            packages.add(ri.activityInfo.packageName);
        }
        CharSequence[] items = names.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Selecciona tu app de sonido / EQ")
                .setItems(items, (dialog, which) -> {
                    String chosenPkg = packages.get(which);
                    getSharedPreferences("ui", MODE_PRIVATE).edit().putString("custom_eq_pkg", chosenPkg).apply();
                    toast("Guardado: " + items[which]);
                    safeStart(pm.getLaunchIntentForPackage(chosenPkg));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean launchEqByKeyword() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String pkg = ri.activityInfo.packageName.toLowerCase();
            String label = (ri.loadLabel(pm) + " " + pkg).toLowerCase();
            if (label.contains("grabador") || label.contains("record") || label.contains("voice") || label.contains("dictaphone")
                    || label.contains("music") || label.contains("musica") || label.contains("player") || label.contains("reproductor")) {
                continue;
            }
            if (label.contains("ecualiz") || label.contains("equaliz") || label.contains("dsp")
                    || label.contains("sonido") || label.contains("audio") || label.contains("sound")
                    || pkg.contains(".eq") || label.contains("audiofx") || pkg.contains("car.setting")) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                return safeStart(i);
            }
        }
        return false;
    }

    private void openSettings() {
        if (Profile.isNwd(this)) Ui.launch(this, SETTINGS);
        else safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private void openMaps() {
        openWaze();
    }

    /** Opens the first installed app whose name or package matches one of the keywords. */
    private boolean launchByKeyword(String... keywords) {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            if (ri.activityInfo.packageName.equals(getPackageName())) continue;
            String label = (ri.loadLabel(pm) + " " + ri.activityInfo.packageName).toLowerCase();
            for (String k : keywords) {
                if (label.contains(k)) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    return safeStart(i);
                }
            }
        }
        return false;
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }



    // ---------- Lifecycle & helpers ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark || Ui.isMapDark(this) != createdMapDark || !profileStamp().equals(createdProfile)) {
            // theme (setting, night or car lights) or profile (name, car photo) changed
            recreate();
            return;
        }
        updateGreeting();
        updateOil();
        showWeather(Weather.cached(this));
        android.content.IntentFilter illFilter = new android.content.IntentFilter();
        illFilter.addAction("com.nwd.ACTION_ILL_STATE_CHANGE");
        illFilter.addAction("com.xyauto.intent.action.ILLUMINATION_ON");
        illFilter.addAction("com.xyauto.intent.action.ILLUMINATION_OFF");
        illFilter.addAction("com.xyauto.broadcast.ILL_CHANGED");
        illFilter.addAction("com.xyauto.ACTION_ILL_STATE");
        illFilter.addAction("com.xyauto.action.HEADLIGHT_ON");
        illFilter.addAction("com.xyauto.action.HEADLIGHT_OFF");
        illFilter.addAction("com.ts.main.action.ACTION_ILL_STATE_CHANGE");
        illFilter.addAction("com.microntek.HEADLIGHT_CHANGED");
        illFilter.addAction("android.intent.action.HEADLIGHT_ON");
        illFilter.addAction("android.intent.action.HEADLIGHT_OFF");
        illFilter.addAction("android.intent.action.SCREEN_BRIGHTNESS_CHANGED");
        illFilter.addAction("android.intent.action.CONFIGURATION_CHANGED");
        registerReceiver(illReceiver, illFilter);
        handler.post(lightPoll);
        lightsOn = hasLightsImage && carLightsOn();
        android.util.Log.d("CarLights", "onResume lights=" + lightsOn);
        if (driveRoadView != null) {
            driveRoadView.setLights(lightsOn);
        }
        if (hasLightsImage && carLights != null && car != null) {
            carLights.setAlpha(lightsOn ? 1f : 0f);
            car.setAlpha(lightsOn ? 0f : 1f);
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (locManager == null) locManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            try {
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locListener);
                locManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5, locListener);
                Location lastGps = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location lastNet = locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                Location best = lastGps != null ? lastGps : lastNet;
                if (best != null) {
                    locListener.onLocationChanged(best);
                }
            } catch (Exception ignored) {}
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(timeUpdater);
        handler.post(periodic);
        if (Profile.isNwd(this)) handler.post(radioPoll);
        if (NowPlaying.isNotificationListenerEnabled(this)) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    android.service.notification.NotificationListenerService.requestRebind(
                            new android.content.ComponentName(this, MediaListener.class));
                }
            } catch (Throwable ignored) {}
        }
        nowPlaying.refreshSessions();
        nowPlaying.addListener(this);
        onNowPlayingChanged();
        if (musicCardVisualizer != null) musicCardVisualizer.startAnimation();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(timeUpdater);
        if (musicCardVisualizer != null) musicCardVisualizer.stopAnimation();
        if (locManager != null) locManager.removeUpdates(locListener);
        unregisterReceiver(illReceiver);
        handler.removeCallbacks(lightPoll);
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(radioPoll);
        nowPlaying.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) fetchWeather(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        radio.unbind(this);
    }

    private TextView left(TextView tv) {
        tv.setGravity(Gravity.START);
        return tv;
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
    }

    private LinearLayout.LayoutParams vweight(float w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w);
        lp.topMargin = top;
        return lp;
    }

        private boolean isCompact() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return (dm.heightPixels / dm.density) < 520;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
