package com.ydh.minesweeper_xtreme;

import android.content.Context;
import android.media.MediaPlayer;

final class GameMusicPlayer {
    private static final float DEFAULT_HOME_VOLUME = 0.28f;
    private static final float DEFAULT_GAME_VOLUME = 0.38f;
    private static final String HOME_BGM_NAME = "bgm_home";
    private static final String GAME_BGM_NAME = "bgm_game";
    private static final String FALLBACK_BGM_NAME = "bgm";

    private final Context context;
    private final Object lock = new Object();

    private volatile boolean running;
    private volatile boolean muted;
    private volatile boolean gameScene;
    private volatile float homeVolume = DEFAULT_HOME_VOLUME;
    private volatile float gameVolume = DEFAULT_GAME_VOLUME;
    private MediaPlayer mediaPlayer;
    private int currentResId = -1;

    GameMusicPlayer(Context context) {
        this.context = context.getApplicationContext();
    }

    void start() {
        synchronized (lock) {
            if (running) {
                return;
            }
            running = true;
            ensurePlayerLocked();
            startPlayerLocked();
        }
    }

    void stop() {
        synchronized (lock) {
            running = false;
            releaseLocked();
        }
    }

    void setMuted(boolean muted) {
        this.muted = muted;
        applyVolume();
    }

    void setScene(boolean gameScene) {
        synchronized (lock) {
            if (this.gameScene == gameScene) {
                applyVolumeLocked();
                return;
            }
            this.gameScene = gameScene;
            if (!running) {
                return;
            }
            switchTrackLocked();
        }
    }

    void setVolumes(float homeVolume, float gameVolume) {
        this.homeVolume = clamp(homeVolume);
        this.gameVolume = clamp(gameVolume);
        applyVolume();
    }

    private void ensurePlayerLocked() {
        int desiredResId = resolveResId(gameScene);
        if (desiredResId == 0) {
            desiredResId = resolveResId(false);
        }
        if (desiredResId == 0) {
            desiredResId = resolveFallbackResId();
        }
        if (desiredResId == 0) {
            releaseLocked();
            running = false;
            return;
        }
        if (mediaPlayer != null && currentResId == desiredResId) {
            return;
        }
        releaseLocked();
        try {
            mediaPlayer = MediaPlayer.create(context, desiredResId);
            currentResId = desiredResId;
            if (mediaPlayer == null) {
                running = false;
                currentResId = -1;
            }
        } catch (RuntimeException ignored) {
            releaseLocked();
            running = false;
        }
    }

    private void switchTrackLocked() {
        releaseLocked();
        ensurePlayerLocked();
        if (mediaPlayer == null) {
            return;
        }
        startPlayerLocked();
    }

    private void startPlayerLocked() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.setLooping(true);
            applyVolumeLocked();
            mediaPlayer.start();
        } catch (IllegalStateException ignored) {
            releaseLocked();
            running = false;
        }
    }

    private void applyVolume() {
        synchronized (lock) {
            applyVolumeLocked();
        }
    }

    private void applyVolumeLocked() {
        if (mediaPlayer == null) {
            return;
        }
        float volume = muted ? 0f : (gameScene ? gameVolume : homeVolume);
        try {
            mediaPlayer.setVolume(volume, volume);
        } catch (IllegalStateException ignored) {
        }
    }

    private void releaseLocked() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            try {
                mediaPlayer.release();
            } catch (RuntimeException ignored) {
            }
            mediaPlayer = null;
        }
        currentResId = -1;
    }

    private int resolveResId(boolean inGame) {
        String name = inGame ? GAME_BGM_NAME : HOME_BGM_NAME;
        return context.getResources().getIdentifier(name, "raw", context.getPackageName());
    }

    private int resolveFallbackResId() {
        return context.getResources().getIdentifier(FALLBACK_BGM_NAME, "raw", context.getPackageName());
    }

    private float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
