package com.simple.carlauncher;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * Listens for notifications and media sessions so the launcher can display
 * track titles, artists, album art and transport controls for Spotify, YouTube Music, etc.
 */
public class MediaListener extends NotificationListenerService {

    private static final String TAG = "MediaListener";

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification listener connected");
        try {
            NowPlaying np = NowPlaying.get(this);
            np.onNotificationListenerConnected();
            scanActiveMediaNotifications();
        } catch (Throwable t) {
            Log.w(TAG, "Error in onListenerConnected", t);
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Notification listener disconnected");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                requestRebind(new ComponentName(this, MediaListener.class));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        try {
            handleNotification(sbn);
        } catch (Throwable t) {
            Log.w(TAG, "Error handling notification", t);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) return;
        try {
            NowPlaying.get(this).onNotificationRemoved(sbn.getPackageName());
        } catch (Throwable ignored) {}
    }

    private void scanActiveMediaNotifications() {
        try {
            StatusBarNotification[] sbns = getActiveNotifications();
            if (sbns != null) {
                for (StatusBarNotification sbn : sbns) {
                    handleNotification(sbn);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void handleNotification(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;
        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        MediaSession.Token token = null;
        try {
            token = extras.getParcelable("android.mediaSession");
        } catch (Throwable ignored) {}

        CharSequence titleCs = extras.getCharSequence("android.title");
        CharSequence textCs = extras.getCharSequence("android.text");
        String title = titleCs == null ? "" : titleCs.toString().trim();
        String artist = textCs == null ? "" : textCs.toString().trim();

        Bitmap art = null;
        try {
            Object large = extras.get("android.largeIcon");
            if (large instanceof Bitmap) {
                art = (Bitmap) large;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && large instanceof android.graphics.drawable.Icon) {
                android.graphics.drawable.Drawable d = ((android.graphics.drawable.Icon) large).loadDrawable(this);
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    art = ((android.graphics.drawable.BitmapDrawable) d).getBitmap();
                }
            }
            if (art == null) {
                Object pic = extras.get("android.picture");
                if (pic instanceof Bitmap) art = (Bitmap) pic;
            }
        } catch (Throwable ignored) {}

        NowPlaying.get(this).onNotificationMedia(sbn.getPackageName(), token, title, artist, art);
    }
}
