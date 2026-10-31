package com.simple.carlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** Shared look (neon blue/purple) and small helpers for all screens. */
final class Ui {

    // MCU source ids for ACTION_REQUEST_CHANGE_SOURCE: the "Source" column of
    // /nwdappconfig/app/ApplicationList.xml (not the AppID).
    static final int SOURCE_MUSIC = -128;
    static final int SOURCE_RADIO = 4;
    static final int SOURCE_BT_MUSIC = -125;

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ---------- Theme: light (blue/purple neon) or dark (black) ----------

    static final String THEME_AUTO = "auto", THEME_LIGHT = "light", THEME_DARK = "dark";

    // Wallpapers
    static final String WP_NONE = "none";
    static final String WP_CITY_BRIDGE = "wp_city_bridge";
    static final String WP_LAKE_MOUNTAINS = "wp_lake_mountains";
    static final String WP_ALPINE_LAKE = "wp_alpine_lake";
    static final String WP_FOREST_LAKE = "wp_forest_lake";
    static final String WP_CITY_NIGHT = "wp_city_night";

    static final String[] ALL_WALLPAPERS = {
            WP_NONE,
            WP_CITY_BRIDGE,
            WP_LAKE_MOUNTAINS,
            WP_ALPINE_LAKE,
            WP_FOREST_LAKE,
            WP_CITY_NIGHT
    };

    static final String[] WALLPAPER_NAMES = {
            "Tema Original",
            "Ciudad y Puente",
            "Lago y Montañas",
            "Laguna Alpina",
            "Bosque Otoñal",
            "Skyline Nocturno"
    };

    static String wallpaper(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("wallpaper", WP_NONE);
    }

    static void setWallpaper(Context c, String wp) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("wallpaper", wp).apply();
    }

    static boolean hasWallpaper(Context c) {
        return !WP_NONE.equals(wallpaper(c));
    }

    static String nextWallpaper(Context c) {
        String cur = wallpaper(c);
        int idx = 0;
        for (int i = 0; i < ALL_WALLPAPERS.length; i++) {
            if (ALL_WALLPAPERS[i].equals(cur)) {
                idx = (i + 1) % ALL_WALLPAPERS.length;
                break;
            }
        }
        setWallpaper(c, ALL_WALLPAPERS[idx]);
        return ALL_WALLPAPERS[idx];
    }

    static String wallpaperLabel(Context c) {
        String cur = wallpaper(c);
        for (int i = 0; i < ALL_WALLPAPERS.length; i++) {
            if (ALL_WALLPAPERS[i].equals(cur)) return WALLPAPER_NAMES[i];
        }
        return "Tema Original";
    }

    static String themeMode(Context c) {
        return c.getSharedPreferences("ui", Context.MODE_PRIVATE).getString("theme", THEME_AUTO);
    }

    static void setThemeMode(Context c, String mode) {
        c.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putString("theme", mode).apply();
    }

    /**
     * Checks if the vehicle headlights are detected (via MCU system settings,
     * XY AUTO/YT9216 broadcasts or Android system Night Mode).
     */
    static boolean areCarLightsOn(Context c) {
        try {
            android.content.ContentResolver cr = c.getContentResolver();
            String[] keys = {
                    "headlight_state",
                    "xy_ill_state",
                    "ill_state",
                    "mcu_headlight_state",
                    "mcu_ill_state",
                    "xy_headlight_state",
                    "com.xyauto.ill_state",
                    "car_lights_state",
                    "night_mode"
            };
            for (String k : keys) {
                if (android.provider.Settings.System.getInt(cr, k, 0) != 0) return true;
                try {
                    if (android.provider.Settings.Global.getInt(cr, k, 0) != 0) return true;
                } catch (Throwable ignored) {}
            }
            // NWD / Allwinner bit 2
            if ((android.provider.Settings.System.getInt(cr, "mcu_no_source_device_state", 0) & 2) != 0) return true;
        } catch (Throwable ignored) {}
        try {
            // Android UI Night mode (often triggered by car ILL wire on head units)
            int uiMode = c.getResources().getConfiguration().uiMode;
            if ((uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                return true;
            }
        } catch (Throwable ignored) {}
        // Fallback: Automatic by schedule (from 6:00 PM to 6:00 AM headlights are on)
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 18 || h < 6;
    }

    /** Auto = dark while the car's lights are on (ILL wire) or at night. */
    static boolean isDark(Context c) {
        String m = themeMode(c);
        if (THEME_DARK.equals(m)) return true;
        if (THEME_LIGHT.equals(m)) return false;
        if (areCarLightsOn(c)) return true;
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 19 || h < 6;
    }

    /** Picks the light or dark variant of a gradient. */
    static int[] pick(Context c, int[] light, int[] dark) {
        return isDark(c) ? dark : light;
    }

    static Drawable background(Context c) {
        if (hasWallpaper(c)) {
            String wp = wallpaper(c);
            int resId = c.getResources().getIdentifier(wp, "drawable", c.getPackageName());
            if (resId != 0) {
                Drawable img = c.getDrawable(resId);
                GradientDrawable tint = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{0x40000000, 0x55080A12, 0x6E000000});
                return new LayerDrawable(new Drawable[]{img, tint});
            }
        }
        if (isDark(c)) {
            GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0C0C12, 0xFF050507, 0xFF000000});
            GradientDrawable glow = new GradientDrawable();
            glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            glow.setGradientRadius(dp(c, 560));
            glow.setGradientCenter(0.95f, 0.95f);
            glow.setColors(new int[]{0x223F5AFF, 0x003F5AFF});
            return new LayerDrawable(new Drawable[]{base, glow});
        }
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1B2C8C, 0xFF20206E, 0xFF371764});
        GradientDrawable glowLeft = new GradientDrawable();
        glowLeft.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowLeft.setGradientRadius(dp(c, 520));
        glowLeft.setGradientCenter(0.15f, 0.1f);
        glowLeft.setColors(new int[]{0x553FA9FF, 0x003FA9FF});
        GradientDrawable glowRight = new GradientDrawable();
        glowRight.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowRight.setGradientRadius(dp(c, 560));
        glowRight.setGradientCenter(0.95f, 0.95f);
        glowRight.setColors(new int[]{0x66B04CFF, 0x00B04CFF});
        return new LayerDrawable(new Drawable[]{base, glowLeft, glowRight});
    }

    static GradientDrawable card(Context c) {
        if (hasWallpaper(c)) {
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x59101424, 0x6E0A0D18});
            bg.setCornerRadius(dp(c, 18));
            bg.setStroke(dp(c, 1), 0x33FFFFFF);
            return bg;
        }
        boolean dark = isDark(c);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0xFF17171E, 0xFF101015} : new int[]{0x4D5B6CFF, 0x333B3FB8});
        bg.setCornerRadius(dp(c, 18));
        bg.setStroke(dp(c, 1), dark ? 0x14FFFFFF : 0x337F9CFF);
        return bg;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /** Round icon button. filled = white disc with dark icon (primary action). */
    static ImageView iconButton(Context c, int icon, int sizeDp, boolean filled, Runnable action) {
        ImageView b = new ImageView(c);
        b.setImageResource(icon);
        int pad = Math.round(sizeDp * 0.24f);
        b.setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad));
        b.setBackground(circle(filled ? Color.WHITE : 0x26FFFFFF));
        b.setColorFilter(filled ? 0xFF2A1F6E : Color.WHITE);
        pressable(b, 0.88f);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    static void pressable(View v, float scale) {
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(scale).scaleY(scale).alpha(0.8f).setDuration(90).start();
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(140).start();
            }
            return false;
        });
    }

    static void launch(Context c, String component) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(ComponentName.unflattenFromString(component));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "No disponible", Toast.LENGTH_SHORT).show();
        }
    }

    /** When the user last picked the radio; the BT auto-switch leaves it alone for a while. */
    static long userChoseRadioAt;

    /** True while the FM radio is the active audio source. */
    static boolean radioIsSource(Context c) {
        return android.provider.Settings.System.getInt(c.getContentResolver(), "mcu_current_source", 0)
                == SOURCE_RADIO;
    }

    /**
     * User picked the radio: route it, power the FM chip up and unmute it, and pause the
     * phone so it does not keep streaming (and trigger the Bluetooth auto-switch).
     */
    static void selectRadio(Context c, RadioClient radio) {
        userChoseRadioAt = android.os.SystemClock.elapsedRealtime();
        NowPlaying np = NowPlaying.get(c);
        if (np.track(NowPlaying.Source.BT).playing) np.btClient.playControl(BtClient.PAUSE);
        requestSource(c, SOURCE_RADIO);
        new android.os.Handler().postDelayed(() -> radio.wakeTuner(c), 800);
    }

    /**
     * Makes Bluetooth music the source. Source id -125 is shared by the BT phone (AppID 10) and
     * BT music (AppID 15); a plain source request picks the phone entry, which mutes the
     * amplifier. Announcing the BT music app (KernelUtils.appStart: id 15, op 1, event 0) is what
     * the factory apps do and routes the music properly.
     */
    static void selectBluetooth(Context c) {
        Intent i = new Intent("com.nwd.action.ACTION_APP_IN_OUT");
        i.putExtra("extra_app_id", 15);
        i.putExtra("extra_app_operation", 1);
        i.putExtra("extra_app_event", 0);
        c.sendBroadcast(i);
        // Picking the radio mutes Bluetooth audio (A2DP mute = amplifier mute flag 32) and only
        // the factory BT screen lifts it. Lift it after the source change has gone through.
        new android.os.Handler().postDelayed(
                () -> c.sendBroadcast(new Intent("com.bt.ACTION_A2DP_UNMUTE")), 500);
    }

    /** Asks the NWD kernel to route this audio source to the speakers. */
    static void requestSource(Context c, int sourceId) {
        Intent i = new Intent("com.nwd.action.ACTION_REQUEST_CHANGE_SOURCE");
        i.putExtra("extra_source_id", (byte) sourceId);
        c.sendBroadcast(i);
    }
}
