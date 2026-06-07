package com.ydh.minesweeper_xtreme.game;

public final class GameCoordinator {
    private final GameEngine engine = new GameEngine();

    public GameEngine engine() {
        return engine;
    }

    public void startGame(GameMode mode, boolean stepTimerEnabled, boolean expertModeEnabled) {
        engine.reset(mode, stepTimerEnabled, expertModeEnabled);
    }

    public void togglePause(long now) {
        engine.togglePause(now);
    }

    public void pauseFromMenu(long now) {
        engine.pauseFromMenu(now);
    }

    public void resumeFromMenu(long now) {
        engine.resumeFromMenu(now);
    }

    public void updateComboTimeout(long now) {
        engine.updateComboTimeout(now);
    }

    public boolean handleStepTimeout(long now) {
        if (!engine.paused && engine.stepTimerEnabled && engine.generated && !engine.gameOver
                && engine.stepRemainingSeconds(now) <= 0) {
            engine.loseByTimeout(now);
            return true;
        }
        return false;
    }

    public void reveal(int row, int col, long now) {
        engine.reveal(row, col, now);
    }

    public void toggleFlag(int row, int col) {
        engine.toggleFlag(row, col);
    }
}