package com.simple.carlauncher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

/**
 * Pantalla completa del Reproductor Multimedia con carátula grande,
 * línea de tiempo interactiva, retroceso/avance de 10s, repetir y aleatorio.
 */
public class MusicActivity extends Activity implements NowPlaying.Listener {

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;

    private ImageView artImage, vinylDisc;
    private TextView songTitle, songArtist, sourceBadge;
    private TextView curTimeText, totalTimeText;
    private SeekBar timeBar;
    private ImageView shuffleBtn, rewindBtn, prevBtn, playBtn, nextBtn, forwardBtn, repeatBtn;
    private TextView openAppBtn;

    private boolean isTrackingSeekBar = false;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isTrackingSeekBar) {
                updateProgress();
            }
            if (!isFinishing()) {
                handler.postDelayed(this, 500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        nowPlaying = NowPlaying.get(this);

        boolean compact = isCompact();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(compact ? 12 : 18), dp(compact ? 10 : 14), dp(compact ? 12 : 18), dp(compact ? 10 : 16));
        root.setBackground(Ui.background(this));

        root.addView(buildTopBar(compact), new LinearLayout.LayoutParams(-1, dp(compact ? 48 : 54)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(compact ? 6 : 12);
        root.addView(body, blp);

        // Columna Izquierda: Gran Carátula
        int artSize = dp(compact ? 220 : 270);
        body.addView(buildArtCard(compact, artSize), new LinearLayout.LayoutParams(artSize, artSize));

        // Columna Derecha: Información, Línea de tiempo y Controles
        LinearLayout rightCol = buildDetailsCol(compact);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = dp(compact ? 16 : 24);
        body.addView(rightCol, rlp);

        setContentView(root);
    }

    private View buildTopBar(boolean compact) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageView back = Ui.iconButton(this, R.drawable.ic_back, compact ? 42 : 48, false, this::finish);
        bar.addView(back, new LinearLayout.LayoutParams(dp(compact ? 42 : 48), dp(compact ? 42 : 48)));

        TextView header = Ui.text(this, "Reproductor", compact ? 20 : 24, Color.WHITE);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(dp(14), 0, dp(12), 0);
        bar.addView(header);

        sourceBadge = Ui.text(this, "MÚSICA", 11, Color.WHITE);
        sourceBadge.setTypeface(Typeface.DEFAULT_BOLD);
        sourceBadge.setLetterSpacing(0.08f);
        sourceBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(0x3300E5FF);
        sbg.setCornerRadius(dp(12));
        sbg.setStroke(dp(1), 0x5500E5FF);
        sourceBadge.setBackground(sbg);
        bar.addView(sourceBadge);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, -1, 1f));

        openAppBtn = Ui.text(this, "Abrir app ↗", compact ? 13 : 14, Color.WHITE);
        openAppBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable obg = new GradientDrawable();
        obg.setColor(0x26FFFFFF);
        obg.setCornerRadius(dp(14));
        openAppBtn.setBackground(obg);
        Ui.pressable(openAppBtn, 0.94f);
        openAppBtn.setOnClickListener(v -> nowPlaying.open(this));
        bar.addView(openAppBtn);

        return bar;
    }

    private View buildArtCard(boolean compact, int size) {
        FrameLayout card = new FrameLayout(this);
        card.setClipToOutline(true);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.hasWallpaper(this) ? 0x66101424 : 0xFF141829);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x44FFFFFF);
        card.setBackground(bg);

        // Fondo vinilo decorativo cuando no hay carátula
        vinylDisc = new ImageView(this);
        vinylDisc.setImageResource(R.drawable.ic_music);
        vinylDisc.setColorFilter(0x33FFFFFF);
        vinylDisc.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(compact ? 40 : 50);
        vinylDisc.setPadding(pad, pad, pad, pad);
        card.addView(vinylDisc, new FrameLayout.LayoutParams(-1, -1));

        artImage = new ImageView(this);
        artImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(artImage, new FrameLayout.LayoutParams(-1, -1));

        return card;
    }

    private LinearLayout buildDetailsCol(boolean compact) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);

        songTitle = Ui.text(this, "Nada sonando", compact ? 22 : 28, Color.WHITE);
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        songTitle.setGravity(Gravity.START);
        songTitle.setMaxLines(2);
        col.addView(songTitle);

        songArtist = Ui.text(this, "Conecta tu música por Bluetooth o Spotify", compact ? 14 : 17, 0xCCFFFFFF);
        songArtist.setGravity(Gravity.START);
        songArtist.setSingleLine(true);
        songArtist.setPadding(0, dp(4), 0, dp(compact ? 10 : 16));
        col.addView(songArtist);

        // Barra de progreso y tiempos
        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0x22000000);
        tbg.setCornerRadius(dp(14));
        tbg.setStroke(dp(1), 0x22FFFFFF);
        timeBox.setBackground(tbg);
        timeBox.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout timesRow = new LinearLayout(this);
        curTimeText = Ui.text(this, "0:00", compact ? 12 : 13, 0xFF00E5FF);
        curTimeText.setTypeface(Typeface.DEFAULT_BOLD);
        timesRow.addView(curTimeText);

        View timeSpacer = new View(this);
        timesRow.addView(timeSpacer, new LinearLayout.LayoutParams(0, 0, 1f));

        totalTimeText = Ui.text(this, "0:00", compact ? 12 : 13, 0x99FFFFFF);
        timesRow.addView(totalTimeText);
        timeBox.addView(timesRow);

        timeBar = new SeekBar(this);
        timeBar.setPadding(dp(4), dp(6), dp(4), dp(6));
        timeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    curTimeText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTrackingSeekBar = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTrackingSeekBar = false;
                nowPlaying.seekTo(seekBar.getProgress());
            }
        });
        timeBox.addView(timeBar);
        col.addView(timeBox);

        // Fila de controles principales
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(compact ? 12 : 18), 0, 0);

        // Botón Aleatorio
        shuffleBtn = Ui.iconButton(this, R.drawable.ic_shuffle, compact ? 42 : 46, false, this::toggleShuffle);
        controls.addView(shuffleBtn, new LinearLayout.LayoutParams(dp(compact ? 42 : 46), dp(compact ? 42 : 46)));

        // Retroceder 10s
        rewindBtn = Ui.iconButton(this, R.drawable.ic_rewind, compact ? 46 : 50, false, () -> nowPlaying.rewind10());
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(compact ? 46 : 50), dp(compact ? 46 : 50));
        rlp.leftMargin = dp(compact ? 8 : 12);
        controls.addView(rewindBtn, rlp);

        // Canción Anterior
        prevBtn = Ui.iconButton(this, R.drawable.ic_prev, compact ? 50 : 56, false, () -> nowPlaying.skip(false));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(compact ? 50 : 56), dp(compact ? 50 : 56));
        plp.leftMargin = dp(compact ? 8 : 12);
        controls.addView(prevBtn, plp);

        // Play / Pausa Principal
        playBtn = Ui.iconButton(this, R.drawable.ic_play, compact ? 66 : 74, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams cplp = new LinearLayout.LayoutParams(dp(compact ? 66 : 74), dp(compact ? 66 : 74));
        cplp.leftMargin = dp(compact ? 14 : 18);
        cplp.rightMargin = dp(compact ? 14 : 18);
        controls.addView(playBtn, cplp);

        // Canción Siguiente
        nextBtn = Ui.iconButton(this, R.drawable.ic_next, compact ? 50 : 56, false, () -> nowPlaying.skip(true));
        controls.addView(nextBtn, new LinearLayout.LayoutParams(dp(compact ? 50 : 56), dp(compact ? 50 : 56)));

        // Adelantar 10s
        forwardBtn = Ui.iconButton(this, R.drawable.ic_forward, compact ? 46 : 50, false, () -> nowPlaying.fastForward10());
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(compact ? 46 : 50), dp(compact ? 46 : 50));
        flp.leftMargin = dp(compact ? 8 : 12);
        controls.addView(forwardBtn, flp);

        // Botón Repetir
        repeatBtn = Ui.iconButton(this, R.drawable.ic_repeat, compact ? 42 : 46, false, this::toggleRepeat);
        LinearLayout.LayoutParams relp = new LinearLayout.LayoutParams(dp(compact ? 42 : 46), dp(compact ? 42 : 46));
        relp.leftMargin = dp(compact ? 8 : 12);
        controls.addView(repeatBtn, relp);

        col.addView(controls);
        return col;
    }

    private void toggleShuffle() {
        nowPlaying.toggleShuffle();
        updateShuffleState();
    }

    private void toggleRepeat() {
        nowPlaying.toggleRepeat();
        updateRepeatState();
    }

    private void updateShuffleState() {
        int mode = nowPlaying.getShuffleMode();
        boolean active = mode != 0;
        shuffleBtn.setColorFilter(active ? 0xFF00E5FF : 0x88FFFFFF);
        shuffleBtn.setBackground(Ui.circle(active ? 0x3300E5FF : 0x26FFFFFF));
    }

    private void updateRepeatState() {
        int mode = nowPlaying.getRepeatMode();
        boolean active = mode != 0;
        repeatBtn.setColorFilter(mode == 1 ? 0xFFFF9F5A : active ? 0xFF00E5FF : 0x88FFFFFF);
        repeatBtn.setBackground(Ui.circle(active ? 0x3300E5FF : 0x26FFFFFF));
    }

    private void updateProgress() {
        long cur = nowPlaying.getPosition();
        long dur = nowPlaying.getDuration();
        if (dur > 0) {
            timeBar.setMax((int) dur);
            timeBar.setProgress((int) Math.min(cur, dur));
            curTimeText.setText(formatTime(cur));
            totalTimeText.setText(formatTime(dur));
        } else {
            timeBar.setMax(100);
            if (cur > 0) {
                curTimeText.setText(formatTime(cur));
                totalTimeText.setText("--:--");
                timeBar.setProgress((int) ((cur / 1000) % 100));
            } else {
                curTimeText.setText("0:00");
                totalTimeText.setText("0:00");
                timeBar.setProgress(0);
            }
        }
    }

    private void updateUi() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();

        sourceBadge.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Toca para reproducir música" : t.artist);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            artImage.setVisibility(View.VISIBLE);
            artImage.setImageBitmap(t.art);
            vinylDisc.setVisibility(View.GONE);
        } else {
            artImage.setVisibility(View.GONE);
            vinylDisc.setVisibility(View.VISIBLE);
            vinylDisc.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
        }

        updateShuffleState();
        updateRepeatState();
        updateProgress();
    }

    @Override
    public void onNowPlayingChanged() {
        runOnUiThread(this::updateUi);
    }

    @Override
    protected void onResume() {
        super.onResume();
        nowPlaying.addListener(this);
        updateUi();
        handler.post(progressUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nowPlaying.removeListener(this);
        handler.removeCallbacks(progressUpdater);
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "0:00";
        long sec = (ms / 1000) % 60;
        long min = (ms / (1000 * 60)) % 60;
        long hr = ms / (1000 * 60 * 60);
        if (hr > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hr, min, sec);
        }
        return String.format(Locale.getDefault(), "%d:%02d", min, sec);
    }

    private boolean isCompact() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        return (dm.heightPixels / dm.density) < 520;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
