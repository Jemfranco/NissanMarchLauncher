package com.simple.carlauncher;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks what is playing from any source and routes controls back to it:
 * apps with a media session (Spotify, YouTube Music…), the factory USB/SD player
 * (com.android.music.* broadcasts) and Bluetooth audio (com.bt.* / XY AUTO broadcasts).
 */
final class NowPlaying {

    enum Source { APP, LOCAL, BT }

    interface Listener {
        void onNowPlayingChanged();
    }

    private static final String LOCAL_PLAYER = "com.nwd.android.music.ui/.home_horizontalActivity";

    /** State reported by one source. */
    static final class Track {
        String title = "", artist = "";
        Bitmap art;
        boolean playing;
        long updated;
    }

    private static NowPlaying instance;

    static NowPlaying get(Context c) {
        if (instance == null) instance = new NowPlaying(c.getApplicationContext());
        return instance;
    }

    static boolean isNotificationListenerEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private final Context ctx;
    private final List<Listener> listeners = new ArrayList<>();
    private final Track app = new Track(), local = new Track(), bt = new Track();
    final BtClient btClient = new BtClient();

    private final MediaSessionManager sessions;
    private final ComponentName notificationListener;
    private MediaController controller;
    private boolean sessionListenerRegistered = false;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener =
            list -> refreshSessions();

    private NowPlaying(Context c) {
        ctx = c;
        sessions = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        notificationListener = new ComponentName(c, MediaListener.class);

        IntentFilter f = new IntentFilter();
        f.addAction("com.android.music.metachanged");
        f.addAction("com.android.music.playstatechanged");
        f.addAction("com.android.music.playbackcomplete");
        f.addAction("com.android.music.queuechanged");
        f.addAction("com.spotify.music.metadatachanged");
        f.addAction("com.spotify.music.playbackstatechanged");
        f.addAction("com.spotify.music.queuechanged");
        f.addAction("com.bt.ACTION_AVRCP_MUSIC_ID3");
        f.addAction("com.bt.ACTION_BT_MUSIC_PLAY");
        f.addAction("com.bt.ACTION_BT_MUSIC_PAUSE");
        f.addAction("com.bt.ACTION_A2DP_RELEASE");
        f.addAction("com.bt.ACTION_AVRCP_RELEASE");
        f.addAction("com.xyauto.bt.music.id3");
        f.addAction("com.xyauto.bt.music.play");
        f.addAction("com.xyauto.bt.music.pause");
        f.addAction("autochips.intent.action.MUSIC_CHANGED");
        c.registerReceiver(receiver, f);

        ensureSessionListenerRegistered();
        refreshSessions();

        btClient.bind(c, () -> {
            bt.playing = btClient.isMusicPlaying();
            if (bt.playing) bt.updated = SystemClock.elapsedRealtime();
            btClient.queryId3();
            notifyListeners();
        });
    }

    private void ensureSessionListenerRegistered() {
        if (sessionListenerRegistered) return;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChangedListener, notificationListener);
            sessionListenerRegistered = true;
        } catch (SecurityException ignored) {
            sessionListenerRegistered = false;
        } catch (Throwable t) {
            Log.w("NowPlaying", "Failed to add active sessions listener", t);
        }
    }

    void onNotificationListenerConnected() {
        ensureSessionListenerRegistered();
        refreshSessions();
        notifyListeners();
    }

    void onNotificationMedia(String pkg, MediaSession.Token token, String title, String artist, Bitmap art) {
        if (pkg == null || pkg.equals(ctx.getPackageName()) || "com.android.server.telecom".equals(pkg)) return;

        boolean isMedia = token != null || !title.isEmpty();
        if (!isMedia) return;

        if (token != null) {
            try {
                MediaController mc = new MediaController(ctx, token);
                setController(mc);
            } catch (Throwable t) {
                Log.w("NowPlaying", "Failed to wrap MediaSession.Token", t);
            }
        }

        if (!title.isEmpty()) {
            app.title = title;
            if (!artist.isEmpty()) app.artist = artist;
            if (art != null) app.art = art;
            app.updated = SystemClock.elapsedRealtime();
            notifyListeners();
        } else if (art != null && app.art == null) {
            app.art = art;
            notifyListeners();
        }
    }

    void onNotificationRemoved(String pkg) {
        if (controller != null && pkg != null && pkg.equals(controller.getPackageName())) {
            refreshSessions();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null) return;
            i.setExtrasClassLoader(NowPlaying.class.getClassLoader());
            try {
                handle(a, i);
            } catch (RuntimeException e) {
                // Factory apps may attach extras we cannot unparcel; never crash the launcher for it.
                Log.w("NowPlaying", "Ignoring broadcast " + a, e);
            }
        }

        private void handle(String a, Intent i) {
            long now = SystemClock.elapsedRealtime();
            switch (a) {
                case "com.android.music.metachanged":
                case "com.android.music.playstatechanged":
                case "com.spotify.music.metadatachanged":
                case "com.spotify.music.playbackstatechanged":
                case "autochips.intent.action.MUSIC_CHANGED":
                    String track = i.getStringExtra("track");
                    if (track == null) track = i.getStringExtra("title");
                    if (track == null) track = i.getStringExtra("track_name");
                    if (track != null && !track.trim().isEmpty()) local.title = track.trim();

                    String artist = i.getStringExtra("artist");
                    if (artist == null) artist = i.getStringExtra("artist_name");
                    if (artist != null && !artist.trim().isEmpty()) local.artist = artist.trim();

                    if (i.hasExtra("playing")) local.playing = i.getBooleanExtra("playing", local.playing);
                    else if (i.hasExtra("playstate")) local.playing = i.getBooleanExtra("playstate", local.playing);
                    else if (i.hasExtra("isplaying")) local.playing = i.getBooleanExtra("isplaying", local.playing);
                    local.updated = now;
                    break;
                case "com.bt.ACTION_AVRCP_MUSIC_ID3":
                case "com.xyauto.bt.music.id3":
                    String btTitle = i.getStringExtra("extra_avrcp_id3_title");
                    if (btTitle == null) btTitle = i.getStringExtra("id3_title");
                    if (btTitle == null) btTitle = i.getStringExtra("title");
                    if (btTitle != null && !btTitle.trim().isEmpty()) {
                        bt.title = btTitle.trim();
                        bt.updated = now;
                    }
                    String btArtist = i.getStringExtra("extra_avrcp_id3_artist");
                    if (btArtist == null) btArtist = i.getStringExtra("id3_artist");
                    if (btArtist == null) btArtist = i.getStringExtra("artist");
                    if (btArtist != null && !btArtist.trim().isEmpty()) bt.artist = btArtist.trim();
                    break;
                case "com.bt.ACTION_BT_MUSIC_PLAY":
                case "com.xyauto.bt.music.play":
                    bt.playing = true;
                    bt.updated = now;
                    if (btClient.isBound()) btClient.queryId3();
                    break;
                case "com.bt.ACTION_BT_MUSIC_PAUSE":
                case "com.xyauto.bt.music.pause":
                    bt.playing = false;
                    break;
                default: // A2DP/AVRCP released: phone disconnected
                    bt.playing = false;
                    bt.title = "";
                    bt.artist = "";
                    bt.updated = 0;
                    break;
            }
            notifyListeners();
        }
    };

    // ---------- App media sessions ----------

    private final MediaController.Callback sessionCallback = new MediaController.Callback() {
        public void onMetadataChanged(MediaMetadata m) { readSession(); }
        public void onPlaybackStateChanged(PlaybackState s) { readSession(); }
        public void onSessionDestroyed() { refreshSessions(); }
    };

    void pollBluetoothSettings() {
        try {
            android.content.ContentResolver cr = ctx.getContentResolver();
            String[] titleKeys = {"bt_music_title", "bt_title", "xy_bt_title", "bluetooth_title", "bt_music_name", "id3_title", "avrcp_title"};
            String[] artistKeys = {"bt_music_artist", "bt_artist", "xy_bt_artist", "bluetooth_artist", "id3_artist", "avrcp_artist"};
            boolean changed = false;
            for (String k : titleKeys) {
                String val = Settings.System.getString(cr, k);
                if (val != null && !val.trim().isEmpty() && !val.equals(bt.title)) {
                    bt.title = val.trim();
                    bt.updated = SystemClock.elapsedRealtime();
                    changed = true;
                    break;
                }
            }
            for (String k : artistKeys) {
                String val = Settings.System.getString(cr, k);
                if (val != null && !val.trim().isEmpty() && !val.equals(bt.artist)) {
                    bt.artist = val.trim();
                    changed = true;
                    break;
                }
            }
            if (changed) notifyListeners();
        } catch (Throwable ignored) {}
    }

    void refreshSessions() {
        ensureSessionListenerRegistered();
        pollBluetoothSettings();
        List<MediaController> list = null;
        try {
            list = sessions.getActiveSessions(notificationListener);
        } catch (SecurityException ignored) { }
        MediaController pick = null;
        if (list != null) {
            for (MediaController c : list) {
                if ("com.android.server.telecom".equals(c.getPackageName())) continue;
                if (pick == null) pick = c;
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { pick = c; break; }
            }
        }
        if (pick != null || controller == null) {
            setController(pick);
        }
    }

    private void setController(MediaController pick) {
        if (controller == pick && pick != null) return;
        if (controller != null) {
            try {
                controller.unregisterCallback(sessionCallback);
            } catch (Throwable ignored) {}
        }
        controller = pick;
        if (controller != null) {
            try {
                controller.registerCallback(sessionCallback);
            } catch (Throwable ignored) {}
        }
        readSession();
    }

    private void readSession() {
        if (controller == null) {
            if (app.updated == 0) {
                app.title = "";
                app.artist = "";
                app.art = null;
                app.playing = false;
            }
        } else {
            MediaMetadata m = null;
            PlaybackState s = null;
            try {
                m = controller.getMetadata();
                s = controller.getPlaybackState();
            } catch (Throwable ignored) {}

            if (m != null) {
                String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
                if (title == null || title.isEmpty()) title = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if (title != null && !title.trim().isEmpty()) app.title = title.trim();

                String artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null || artist.isEmpty()) artist = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                if (artist == null || artist.isEmpty()) artist = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
                if (artist != null && !artist.trim().isEmpty()) app.artist = artist.trim();

                Bitmap art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (art == null) art = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (art != null) app.art = art;
            }

            boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
            if (playing || !app.title.isEmpty()) app.updated = SystemClock.elapsedRealtime();
            app.playing = playing;
        }
        notifyListeners();
    }

    // ---------- Public API ----------

    /** The source to show: whatever is playing (most recent first), else the last one used. */
    Source current() {
        Source best = null;
        long bestTime = -1;
        for (Source s : Source.values()) {
            Track t = track(s);
            if (t.playing && t.updated > bestTime) { best = s; bestTime = t.updated; }
        }
        if (best != null) return best;
        for (Source s : Source.values()) {
            Track t = track(s);
            if (t.updated > 0 && t.updated > bestTime) { best = s; bestTime = t.updated; }
        }
        return best;
    }

    Track track(Source s) {
        return s == Source.APP ? app : s == Source.LOCAL ? local : bt;
    }

    String label(Source s) {
        if (s == Source.BT) return "Bluetooth";
        if (s == Source.LOCAL) return "USB";
        if (controller != null) {
            try {
                PackageManager pm = ctx.getPackageManager();
                return pm.getApplicationLabel(pm.getApplicationInfo(controller.getPackageName(), 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return "Música";
    }

    void playPause() {
        Source s = current();
        if (s == Source.BT && btClient.isBound()) {
            if (Ui.radioIsSource(ctx)) {
                // Radio has the speakers (the phone may even be playing muted): bring Bluetooth in.
                Ui.userChoseRadioAt = 0;
                Ui.selectBluetooth(ctx);
                btClient.playControl(BtClient.PLAY);
            } else {
                btClient.playControl(bt.playing ? BtClient.PAUSE : BtClient.PLAY);
            }
        } else if (controller != null && controller.getTransportControls() != null) {
            try {
                PlaybackState ps = controller.getPlaybackState();
                boolean playing = ps != null && ps.getState() == PlaybackState.STATE_PLAYING;
                if (playing) controller.getTransportControls().pause();
                else controller.getTransportControls().play();
            } catch (Throwable t) {
                mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            }
        } else {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        }
    }

    void skip(boolean next) {
        Source s = current();
        if (s == Source.BT && btClient.isBound()) {
            btClient.playControl(next ? BtClient.NEXT : BtClient.PREVIOUS);
        } else if (controller != null && controller.getTransportControls() != null) {
            try {
                if (next) controller.getTransportControls().skipToNext();
                else controller.getTransportControls().skipToPrevious();
            } catch (Throwable t) {
                mediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
        } else {
            mediaKey(next ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        }
    }

    private int repeatMode = 0; // 0 = NONE, 2 = ALL, 1 = ONE
    private int shuffleMode = 0; // 0 = OFF, 1 = ON

    void holdMediaKey(int keyCode, long durationMs) {
        new Thread(() -> {
            long downTime = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0);
            try {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.dispatchMediaKeyEvent(down);
            } catch (Throwable ignored) {}
            try {
                Intent bDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
                bDown.putExtra(Intent.EXTRA_KEY_EVENT, down);
                ctx.sendOrderedBroadcast(bDown, null);
            } catch (Throwable ignored) {}
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException ignored) {}
            long upTime = SystemClock.uptimeMillis();
            KeyEvent up = new KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, keyCode, 0);
            try {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.dispatchMediaKeyEvent(up);
            } catch (Throwable ignored) {}
            try {
                Intent bUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
                bUp.putExtra(Intent.EXTRA_KEY_EVENT, up);
                ctx.sendOrderedBroadcast(bUp, null);
            } catch (Throwable ignored) {}
        }).start();
    }

    long getPosition() {
        if (controller != null) {
            try {
                PlaybackState s = controller.getPlaybackState();
                if (s != null) {
                    long pos = s.getPosition();
                    if (s.getState() == PlaybackState.STATE_PLAYING) {
                        long diff = SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime();
                        if (diff > 0) {
                            pos += (long) (diff * s.getPlaybackSpeed());
                        }
                    }
                    return Math.max(0, pos);
                }
            } catch (Throwable ignored) {}
        }
        if (bt.playing && bt.updated > 0) {
            return Math.max(0, SystemClock.elapsedRealtime() - bt.updated);
        }
        return 0;
    }

    long getDuration() {
        if (controller != null) {
            try {
                MediaMetadata m = controller.getMetadata();
                if (m != null) {
                    long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                    if (dur > 0) return dur;
                }
            } catch (Throwable ignored) {}
        }
        if (bt.playing && bt.updated > 0) {
            long pos = getPosition();
            return Math.max(210000L, pos + 30000L);
        }
        return 0;
    }

    void seekTo(long posMs) {
        if (controller != null && controller.getTransportControls() != null) {
            try {
                controller.getTransportControls().seekTo(posMs);
            } catch (Throwable ignored) {}
        }
        if (bt.playing) {
            long oldPos = getPosition();
            long diff = posMs - oldPos;
            bt.updated = SystemClock.elapsedRealtime() - posMs;
            if (diff > 0) {
                holdMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, Math.min(1000, Math.max(300, diff / 20)));
            } else if (diff < 0) {
                holdMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND, Math.min(1000, Math.max(300, -diff / 20)));
            }
        }
        notifyListeners();
    }

    void rewind10() {
        long cur = getPosition();
        long dur = getDuration();
        if (controller != null && dur > 0) {
            seekTo(Math.max(0, cur - 10000));
        } else {
            holdMediaKey(KeyEvent.KEYCODE_MEDIA_REWIND, 500);
            ctx.sendBroadcast(new Intent("com.xyauto.bt.music.prev"));
            ctx.sendBroadcast(new Intent("com.autochips.bluetooth.prev"));
            try {
                Intent cmd = new Intent("com.android.music.musicservicecommand");
                cmd.putExtra("command", "rewind");
                ctx.sendBroadcast(cmd);
            } catch (Throwable ignored) {}
            if (bt.updated > 0) {
                bt.updated += 10000;
            }
        }
        notifyListeners();
    }

    void fastForward10() {
        long cur = getPosition();
        long dur = getDuration();
        if (controller != null && dur > 0) {
            seekTo(Math.min(dur, cur + 10000));
        } else {
            holdMediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 500);
            ctx.sendBroadcast(new Intent("com.xyauto.bt.music.next"));
            ctx.sendBroadcast(new Intent("com.autochips.bluetooth.next"));
            try {
                Intent cmd = new Intent("com.android.music.musicservicecommand");
                cmd.putExtra("command", "fastforward");
                ctx.sendBroadcast(cmd);
            } catch (Throwable ignored) {}
            if (bt.updated > 0) {
                bt.updated = Math.max(0, bt.updated - 10000);
            }
        }
        notifyListeners();
    }

    int getRepeatMode() {
        if (controller != null) {
            try {
                java.lang.reflect.Method m = controller.getClass().getMethod("getRepeatMode");
                Object res = m.invoke(controller);
                if (res instanceof Integer) return (Integer) res;
            } catch (Throwable ignored) {}
        }
        return repeatMode;
    }

    void toggleRepeat() {
        int cur = getRepeatMode();
        int next = (cur == 0) ? 2 : (cur == 2) ? 1 : 0;
        repeatMode = next;
        if (controller != null && controller.getTransportControls() != null) {
            try {
                java.lang.reflect.Method m = controller.getTransportControls().getClass().getMethod("setRepeatMode", int.class);
                m.invoke(controller.getTransportControls(), next);
            } catch (Throwable ignored) {}
        }
        try {
            ctx.sendBroadcast(new Intent("com.xyauto.bt.music.repeat").putExtra("mode", next));
            ctx.sendBroadcast(new Intent("com.autochips.bluetooth.repeat").putExtra("mode", next));
            ctx.sendBroadcast(new Intent("com.spotify.music.service.ACTION_REPEAT").putExtra("mode", next));
            Intent cmd = new Intent("com.android.music.musicservicecommand");
            cmd.putExtra("command", "repeat");
            ctx.sendBroadcast(cmd);
        } catch (Throwable ignored) {}
        notifyListeners();
    }

    int getShuffleMode() {
        if (controller != null) {
            try {
                java.lang.reflect.Method m = controller.getClass().getMethod("getShuffleMode");
                Object res = m.invoke(controller);
                if (res instanceof Integer) return (Integer) res;
            } catch (Throwable ignored) {}
        }
        return shuffleMode;
    }

    void toggleShuffle() {
        int cur = getShuffleMode();
        int next = (cur == 0) ? 1 : 0;
        shuffleMode = next;
        if (controller != null && controller.getTransportControls() != null) {
            try {
                java.lang.reflect.Method m = controller.getTransportControls().getClass().getMethod("setShuffleMode", int.class);
                m.invoke(controller.getTransportControls(), next);
            } catch (Throwable ignored) {}
        }
        try {
            ctx.sendBroadcast(new Intent("com.xyauto.bt.music.shuffle").putExtra("mode", next));
            ctx.sendBroadcast(new Intent("com.autochips.bluetooth.shuffle").putExtra("mode", next));
            ctx.sendBroadcast(new Intent("com.spotify.music.service.ACTION_SHUFFLE").putExtra("mode", next));
            Intent cmd = new Intent("com.android.music.musicservicecommand");
            cmd.putExtra("command", "shuffle");
            ctx.sendBroadcast(cmd);
        } catch (Throwable ignored) {}
        notifyListeners();
    }

    MediaController getController() {
        return controller;
    }

    /** Opens the screen that belongs to the current source. */
    void open(Context c) {
        if (!isNotificationListenerEnabled(c)) {
            new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Acceso a música y notificaciones")
                    .setMessage("Para mostrar el título de la canción, portada y cambiar de canción con Spotify, activa el permiso de 'Inicio' en la lista que se abrirá.")
                    .setPositiveButton("Activar", (dialog, which) -> {
                        try {
                            c.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        } catch (Exception e) {
                            c.startActivity(new Intent(Settings.ACTION_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }

        Source s = current();
        if (s == Source.BT && Profile.isNwd(c)) {
            c.startActivity(new Intent(c, BtActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else if (s == Source.APP && controller != null) {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(controller.getPackageName());
            if (i != null) {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            launchMusicApp(c);
        } else {
            launchMusicApp(c);
        }
    }

    static void launchMusicApp(Context c) {
        PackageManager pm = c.getPackageManager();
        String[] preferred = {
                "com.spotify.music",
                "com.spotify.lite",
                "com.google.android.apps.youtube.music",
                "com.google.android.music",
                "deezer.android.app",
                "com.amazon.mp3",
                "com.apple.android.music",
                "com.autochips.music",
                "com.xyauto.music",
                "com.android.music"
        };
        for (String pkg : preferred) {
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) {
                c.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
        }
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName.toLowerCase();
            String name = (ri.loadLabel(pm) + " " + pkg).toLowerCase();
            if (pkg.equals(c.getPackageName())) continue;
            if (name.contains("spotify") || name.contains("music") || name.contains("musica")
                    || name.contains("player") || name.contains("reproductor")) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return;
            }
        }
        Ui.launch(c, LOCAL_PLAYER);
    }

    void addListener(Listener l) {
        listeners.add(l);
        refreshSessions();
        if (btClient.isBound()) btClient.queryId3();
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onNowPlayingChanged();
    }

    void mediaKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);

        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(down);
                am.dispatchMediaKeyEvent(up);
            }
        } catch (Throwable ignored) {}

        try {
            Intent btnDown = new Intent(Intent.ACTION_MEDIA_BUTTON);
            btnDown.putExtra(Intent.EXTRA_KEY_EVENT, down);
            ctx.sendOrderedBroadcast(btnDown, null);
            Intent btnUp = new Intent(Intent.ACTION_MEDIA_BUTTON);
            btnUp.putExtra(Intent.EXTRA_KEY_EVENT, up);
            ctx.sendOrderedBroadcast(btnUp, null);
        } catch (Throwable ignored) {}

        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            ctx.sendBroadcast(new Intent("com.xyauto.bt.music.next"));
            ctx.sendBroadcast(new Intent("com.autochips.bluetooth.next"));
            ctx.sendBroadcast(new Intent("com.android.music.musicservicecommand.next"));
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            ctx.sendBroadcast(new Intent("com.xyauto.bt.music.prev"));
            ctx.sendBroadcast(new Intent("com.autochips.bluetooth.prev"));
            ctx.sendBroadcast(new Intent("com.android.music.musicservicecommand.previous"));
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            ctx.sendBroadcast(new Intent("com.xyauto.bt.music.toggle"));
            ctx.sendBroadcast(new Intent("com.android.music.musicservicecommand.togglepause"));
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
