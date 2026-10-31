package com.simple.carlauncher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Pantalla completa de Música con fondo de ondas sonoras neón en vivo,
 * controles avanzados (aleatorio, repetir, +/-10s) y línea de tiempo interactiva.
 */
public class MusicActivity extends Activity implements NowPlaying.Listener {

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;

    private WaveVisualizerView visualizer;
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

        // Contenedor principal con capas: Fondo Animado -> Capa Tinte -> Contenido
        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackground(Ui.background(this));

        // 1. Fondo de ondas sonoras vivas y ecualizador
        visualizer = new WaveVisualizerView(this);
        rootFrame.addView(visualizer, new FrameLayout.LayoutParams(-1, -1));

        // 2. Capa de cristal ahumado para garantizar contraste y legibilidad
        View shade = new View(this);
        GradientDrawable shadeBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x77060A14, 0x550A0E1C, 0x8805070E});
        shade.setBackground(shadeBg);
        rootFrame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        // 3. Contenido en primer plano
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(compact ? 12 : 18), dp(compact ? 10 : 14), dp(compact ? 12 : 18), dp(compact ? 10 : 16));

        content.addView(buildTopBar(compact), new LinearLayout.LayoutParams(-1, dp(compact ? 48 : 54)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, 0, 1f);
        blp.topMargin = dp(compact ? 6 : 12);
        content.addView(body, blp);

        // Columna Izquierda: Gran Carátula
        int artSize = dp(compact ? 220 : 270);
        body.addView(buildArtCard(compact, artSize), new LinearLayout.LayoutParams(artSize, artSize));

        // Columna Derecha: Información, Línea de tiempo y Controles
        LinearLayout rightCol = buildDetailsCol(compact);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, -1, 1f);
        rlp.leftMargin = dp(compact ? 16 : 24);
        body.addView(rightCol, rlp);

        rootFrame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(rootFrame);
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
        sbg.setStroke(dp(1), 0x8800E5FF);
        sourceBadge.setBackground(sbg);
        bar.addView(sourceBadge);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, -1, 1f));

        openAppBtn = Ui.text(this, "Abrir app ↗", compact ? 13 : 14, Color.WHITE);
        openAppBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable obg = new GradientDrawable();
        obg.setColor(0x33FFFFFF);
        obg.setCornerRadius(dp(14));
        obg.setStroke(dp(1), 0x44FFFFFF);
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
        bg.setColor(0x40101424);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), 0x5500E5FF);
        card.setBackground(bg);

        // Fondo vinilo decorativo cuando no hay carátula
        vinylDisc = new ImageView(this);
        vinylDisc.setImageResource(R.drawable.ic_music);
        vinylDisc.setColorFilter(0x4400E5FF);
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

        songArtist = Ui.text(this, "Conecta tu música por Bluetooth o abre Spotify", compact ? 14 : 17, 0xCCFFFFFF);
        songArtist.setGravity(Gravity.START);
        songArtist.setSingleLine(true);
        songArtist.setPadding(0, dp(4), 0, dp(compact ? 10 : 16));
        col.addView(songArtist);

        // Barra de progreso y tiempos
        LinearLayout timeBox = new LinearLayout(this);
        timeBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(0x33000000);
        tbg.setCornerRadius(dp(14));
        tbg.setStroke(dp(1), 0x33FFFFFF);
        timeBox.setBackground(tbg);
        timeBox.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout timesRow = new LinearLayout(this);
        curTimeText = Ui.text(this, "0:00", compact ? 12 : 13, 0xFF00E5FF);
        curTimeText.setTypeface(Typeface.DEFAULT_BOLD);
        timesRow.addView(curTimeText);

        View timeSpacer = new View(this);
        timesRow.addView(timeSpacer, new LinearLayout.LayoutParams(0, 0, 1f));

        totalTimeText = Ui.text(this, "0:00", compact ? 12 : 13, 0xB3FFFFFF);
        timesRow.addView(totalTimeText);
        timeBox.addView(timesRow);

        timeBar = new SeekBar(this);
        timeBar.setPadding(dp(6), dp(8), dp(6), dp(8));
        timeBar.setProgressDrawable(createSeekBarProgress());
        timeBar.setThumb(createSeekBarThumb());
        timeBar.setSplitTrack(false);

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
        shuffleBtn = Ui.iconButton(this, R.drawable.ic_shuffle, compact ? 42 : 46, false, this::onShuffleClicked);
        controls.addView(shuffleBtn, new LinearLayout.LayoutParams(dp(compact ? 42 : 46), dp(compact ? 42 : 46)));

        // Retroceder 10s
        rewindBtn = Ui.iconButton(this, R.drawable.ic_rewind, compact ? 46 : 50, false, this::onRewindClicked);
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
        forwardBtn = Ui.iconButton(this, R.drawable.ic_forward, compact ? 46 : 50, false, this::onForwardClicked);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(dp(compact ? 46 : 50), dp(compact ? 46 : 50));
        flp.leftMargin = dp(compact ? 8 : 12);
        controls.addView(forwardBtn, flp);

        // Botón Repetir
        repeatBtn = Ui.iconButton(this, R.drawable.ic_repeat, compact ? 42 : 46, false, this::onRepeatClicked);
        LinearLayout.LayoutParams relp = new LinearLayout.LayoutParams(dp(compact ? 42 : 46), dp(compact ? 42 : 46));
        relp.leftMargin = dp(compact ? 8 : 12);
        controls.addView(repeatBtn, relp);

        col.addView(controls);
        return col;
    }

    private Drawable createSeekBarProgress() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x33FFFFFF);
        bg.setCornerRadius(dp(4));

        GradientDrawable progress = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF00E5FF, 0xFF7C4DFF});
        progress.setCornerRadius(dp(4));
        ClipDrawable clip = new ClipDrawable(progress, Gravity.LEFT, ClipDrawable.HORIZONTAL);

        return new LayerDrawable(new Drawable[]{bg, clip});
    }

    private Drawable createSeekBarThumb() {
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setSize(dp(22), dp(22));
        thumb.setColor(Color.WHITE);
        thumb.setStroke(dp(3), 0xFF00E5FF);
        return thumb;
    }

    private void onShuffleClicked() {
        nowPlaying.toggleShuffle();
        updateShuffleState();
        int mode = nowPlaying.getShuffleMode();
        toast(mode != 0 ? "Aleatorio: Activado" : "Aleatorio: Desactivado");
    }

    private void onRepeatClicked() {
        nowPlaying.toggleRepeat();
        updateRepeatState();
        int mode = nowPlaying.getRepeatMode();
        toast(mode == 1 ? "Repetir: 1 Canción" : mode == 2 ? "Repetir: Todo" : "Repetir: Desactivado");
    }

    private void onRewindClicked() {
        nowPlaying.rewind10();
        toast("⏪ -10 segundos");
        updateProgress();
    }

    private void onForwardClicked() {
        nowPlaying.fastForward10();
        toast("⏩ +10 segundos");
        updateProgress();
    }

    private void updateShuffleState() {
        int mode = nowPlaying.getShuffleMode();
        boolean active = mode != 0;
        shuffleBtn.setColorFilter(active ? 0xFF00E5FF : 0x88FFFFFF);
        shuffleBtn.setBackground(Ui.circle(active ? 0x4400E5FF : 0x26FFFFFF));
    }

    private void updateRepeatState() {
        int mode = nowPlaying.getRepeatMode();
        boolean active = mode != 0;
        repeatBtn.setColorFilter(mode == 1 ? 0xFFFF9F5A : active ? 0xFF00E5FF : 0x88FFFFFF);
        repeatBtn.setBackground(Ui.circle(active ? (mode == 1 ? 0x44FF9F5A : 0x4400E5FF) : 0x26FFFFFF));
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
            timeBar.setMax(210000);
            timeBar.setProgress((int) Math.min(cur, 210000));
            curTimeText.setText(formatTime(cur));
            totalTimeText.setText("3:30");
        }
    }

    private void updateUi() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        boolean playing = t != null && t.playing;

        sourceBadge.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Conecta tu música por Bluetooth o abre Spotify" : t.artist);
        playBtn.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (visualizer != null) {
            visualizer.setPlaying(playing);
        }

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
        if (visualizer != null) visualizer.startAnimation();
        nowPlaying.addListener(this);
        updateUi();
        handler.post(progressUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (visualizer != null) visualizer.stopAnimation();
        nowPlaying.removeListener(this);
        handler.removeCallbacks(progressUpdater);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
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
