package com.ydh.minesweeper_xtreme;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class GameEngine {
    static final int SIZE = 7;

    private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

    private final Random random = new Random();

    GameMode mode;
    boolean stepTimerEnabled;
    boolean expertModeEnabled;
    boolean generated;
    boolean gameOver;
    boolean won;
    boolean newRecord;
    boolean paused;
    boolean pauseUsed;
    boolean[][] mines = new boolean[SIZE][SIZE];
    boolean[][] revealed = new boolean[SIZE][SIZE];
    boolean[][] flagged = new boolean[SIZE][SIZE];
    boolean[][] skippedMine = new boolean[SIZE][SIZE];
    int[][] numbers = new int[SIZE][SIZE];
    int score;
    int combo;
    long globalStartMillis;
    long lastActionMillis;
    long lastStepStartMillis;
    long pauseStartMillis;
    long gameOverMillis;
    String message = "点击任意格开始。";

    GameEngine() {
        reset(GameMode.NORMAL, false, false);
    }

    void reset(GameMode mode, boolean stepTimerEnabled, boolean expertModeEnabled) {
        this.mode = mode;
        this.stepTimerEnabled = stepTimerEnabled;
        this.expertModeEnabled = expertModeEnabled;
        generated = false;
        gameOver = false;
        won = false;
        newRecord = false;
        paused = false;
        pauseUsed = false;
        score = 0;
        combo = 0;
        globalStartMillis = 0L;
        lastActionMillis = 0L;
        lastStepStartMillis = 0L;
        pauseStartMillis = 0L;
        gameOverMillis = 0L;
        message = "点击任意格开始。长按格子可插旗。";
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                mines[r][c] = false;
                revealed[r][c] = false;
                flagged[r][c] = false;
                skippedMine[r][c] = false;
                numbers[r][c] = 0;
            }
        }
    }

    MoveResult reveal(int r, int c, long now) {
        MoveResult result = new MoveResult();
        if (gameOver || paused || !inBounds(r, c) || flagged[r][c] || revealed[r][c] || skippedMine[r][c]) {
            return result;
        }
        if (!generated) {
            generate(r, c);
            generated = true;
            globalStartMillis = now;
            lastStepStartMillis = now;
        } else {
            MinesweeperSolver.Result solverResult =
                    MinesweeperSolver.findCertainSafe(mode, makeVisibleBoard(), flagged);
            if (solverResult.hasCertainSafe() && !solverResult.safe[r][c]) {
                boolean[][] displayMines = MinesweeperSolver.findMineMapWithMine(mode, makeVisibleBoard(), r, c);
                if (displayMines != null) {
                    replaceMines(displayMines);
                }
                lose("当前局面存在确定安全格，这一步属于猜测，判负。", now);
                result.lost = true;
                return result;
            }
        }

        if (mines[r][c]) {
            skippedMine[r][c] = true;
            combo = 0;
            lastActionMillis = now;
            lastStepStartMillis = now;
            message = "这一格是雷，但当前没有确定解，雷被跳过，连击清零。";
            result.mineSkipped = true;
            return result;
        }

        int opened = openSafeCells(r, c);
        if (opened > 0) {
            if (lastActionMillis > 0L && now - lastActionMillis <= 30000L) {
                combo++;
            } else {
                combo = 1;
            }
            int gained = opened * 5 * combo;
            score += gained;
            lastActionMillis = now;
            lastStepStartMillis = now;
            result.openedCells = opened;
            result.scoreGained = gained;
            message = "打开 " + opened + " 格，连击 x" + combo + "，+" + gained + " 分。";
        }
        if (isWin()) {
            win(now);
            result.won = true;
        }
        return result;
    }

    void toggleFlag(int r, int c) {
        if (gameOver || paused || !generated || !inBounds(r, c) || revealed[r][c] || skippedMine[r][c]) {
            return;
        }
        flagged[r][c] = !flagged[r][c];
        message = flagged[r][c] ? "已插旗。" : "已取消旗标。";
    }

    void loseByTimeout() {
        loseByTimeout(System.currentTimeMillis());
    }

    void loseByTimeout(long now) {
        lose("单步倒计时结束，判负。", now);
    }

    void togglePause(long now) {
        if (gameOver || !generated) {
            return;
        }
        if (!paused) {
            paused = true;
            pauseUsed = true;
            pauseStartMillis = now;
            message = "游戏已暂停。再次点击暂停按钮继续。";
            return;
        }
        long pausedMillis = now - pauseStartMillis;
        if (globalStartMillis > 0L) {
            globalStartMillis += pausedMillis;
        }
        if (lastActionMillis > 0L) {
            lastActionMillis += pausedMillis;
        }
        if (lastStepStartMillis > 0L) {
            lastStepStartMillis += pausedMillis;
        }
        paused = false;
        pauseStartMillis = 0L;
        message = "游戏继续。";
    }

    void pauseFromMenu(long now) {
        if (gameOver || !generated || paused) {
            return;
        }
        paused = true;
        pauseUsed = true;
        pauseStartMillis = now;
        message = "已返回主页，本局自动暂停。";
    }

    void resumeFromMenu(long now) {
        if (!paused) {
            return;
        }
        long pausedMillis = now - pauseStartMillis;
        if (globalStartMillis > 0L) {
            globalStartMillis += pausedMillis;
        }
        if (lastActionMillis > 0L) {
            lastActionMillis += pausedMillis;
        }
        if (lastStepStartMillis > 0L) {
            lastStepStartMillis += pausedMillis;
        }
        paused = false;
        pauseStartMillis = 0L;
        message = "游戏继续。";
    }

    int[][] makeVisibleBoard() {
        int[][] visible = new int[SIZE][SIZE];
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (revealed[r][c]) {
                    visible[r][c] = numbers[r][c];
                } else if (skippedMine[r][c]) {
                    visible[r][c] = MinesweeperSolver.KNOWN_MINE;
                } else {
                    visible[r][c] = MinesweeperSolver.HIDDEN;
                }
            }
        }
        return visible;
    }

    int remainingMinesDisplay() {
        int flags = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (flagged[r][c]) {
                    flags++;
                }
            }
        }
        return mode.mines - flags;
    }

    int totalMinesDisplay() {
        return mode.mines;
    }

    int elapsedSeconds(long now) {
        if (!generated || globalStartMillis == 0L) {
            return 0;
        }
        if (paused) {
            now = pauseStartMillis;
        }
        if (gameOver && gameOverMillis > 0L) {
            now = gameOverMillis;
        }
        return (int) ((now - globalStartMillis) / 1000L);
    }

    int stepRemainingSeconds(long now) {
        if (!stepTimerEnabled || !generated || gameOver) {
            return 60;
        }
        if (paused) {
            now = pauseStartMillis;
        }
        int elapsed = (int) ((now - lastStepStartMillis) / 1000L);
        return Math.max(0, 60 - elapsed);
    }

    void updateComboTimeout(long now) {
        if (!generated || gameOver || paused || lastActionMillis == 0L || combo == 0) {
            return;
        }
        if (now - lastActionMillis > 30000L) {
            combo = 0;
            message = "单步操作超过30秒，连击清零。";
        }
    }

    private void generate(int firstR, int firstC) {
        boolean[][] safeZone = makeSafeZone(firstR, firstC);
        if (mode == GameMode.TWO_G) {
            generateTwoG(safeZone);
        } else if (mode == GameMode.TWO_D) {
            generateTwoD(safeZone);
        } else if (mode == GameMode.TWO_H) {
            generateTwoH(safeZone);
        } else {
            generateNormal(safeZone);
        }
        computeNumbers();
    }

    private boolean[][] makeSafeZone(int firstR, int firstC) {
        boolean[][] safeZone = new boolean[SIZE][SIZE];
        safeZone[firstR][firstC] = true;
        for (int i = 0; i < DR.length; i++) {
            int r = firstR + DR[i];
            int c = firstC + DC[i];
            if (inBounds(r, c)) {
                safeZone[r][c] = true;
            }
        }
        return safeZone;
    }

    private void generateNormal(boolean[][] safeZone) {
        List<Integer> cells = new ArrayList<Integer>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!safeZone[r][c]) {
                    cells.add(Integer.valueOf(r * SIZE + c));
                }
            }
        }
        Collections.shuffle(cells, random);
        for (int i = 0; i < mode.mines; i++) {
            int cell = cells.get(i).intValue();
            mines[cell / SIZE][cell % SIZE] = true;
        }
    }

    private void generateTwoG(boolean[][] safeZone) {
        while (true) {
            clearMines();
            List<Integer> blocks = new ArrayList<Integer>();
            for (int r = 0; r < SIZE - 1; r++) {
                for (int c = 0; c < SIZE - 1; c++) {
                    if (blockAvoidsSafeZone(r, c, safeZone)) {
                        blocks.add(Integer.valueOf(r * 6 + c));
                    }
                }
            }
            Collections.shuffle(blocks, random);
            int placed = 0;
            for (int i = 0; i < blocks.size() && placed < 3; i++) {
                int block = blocks.get(i).intValue();
                int r = block / 6;
                int c = block % 6;
                if (canPlaceBlock(r, c)) {
                    setBlock(r, c, true);
                    placed++;
                }
            }
            if (placed == 3) {
                return;
            }
        }
    }

    private void generateTwoH(boolean[][] safeZone) {
        TwoHGenerator generator = new TwoHGenerator(safeZone);
        int[] selected = generator.pick();
        clearMines();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                mines[r][c] = ((selected[r] >> c) & 1) == 1;
            }
        }
    }

    private void generateTwoD(boolean[][] safeZone) {
        TwoDGenerator generator = new TwoDGenerator(safeZone);
        long selected = generator.pick();
        clearMines();
        for (int idx = 0; idx < SIZE * SIZE; idx++) {
            if (((selected >> idx) & 1L) == 1L) {
                mines[idx / SIZE][idx % SIZE] = true;
            }
        }
    }

    private void clearMines() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                mines[r][c] = false;
            }
        }
    }

    private boolean blockAvoidsSafeZone(int r, int c, boolean[][] safeZone) {
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                if (safeZone[r + dr][c + dc]) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canPlaceBlock(int r, int c) {
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                if (mines[r + dr][c + dc]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void setBlock(int r, int c, boolean value) {
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                mines[r + dr][c + dc] = value;
            }
        }
    }

    private void computeNumbers() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                numbers[r][c] = countAdjacentMines(r, c);
            }
        }
    }

    private void replaceMines(boolean[][] newMines) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                mines[r][c] = newMines[r][c];
            }
        }
        computeNumbers();
    }

    private int openSafeCells(int r, int c) {
        int opened = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        queue.add(Integer.valueOf(r * SIZE + c));
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst().intValue();
            int cr = cell / SIZE;
            int cc = cell % SIZE;
            if (!inBounds(cr, cc) || revealed[cr][cc] || mines[cr][cc]) {
                continue;
            }
            revealed[cr][cc] = true;
            opened++;
            if (numbers[cr][cc] == 0) {
                for (int i = 0; i < DR.length; i++) {
                    int nr = cr + DR[i];
                    int nc = cc + DC[i];
                    if (inBounds(nr, nc) && !revealed[nr][nc]) {
                        queue.add(Integer.valueOf(nr * SIZE + nc));
                    }
                }
            }
        }
        return opened;
    }

    private boolean isWin() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!mines[r][c] && !revealed[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void win(long now) {
        int bonus = Math.max(0, 180 - elapsedSeconds(now)) * 5;
        won = true;
        gameOver = true;
        gameOverMillis = now;
        score += bonus;
        message = "胜利！全局计时奖励 +" + bonus + " 分。";
    }

    private void lose(String reason, long now) {
        gameOver = true;
        won = false;
        gameOverMillis = now;
        combo = 0;
        message = reason;
    }

    private int countAdjacentMines(int r, int c) {
        int count = 0;
        for (int i = 0; i < DR.length; i++) {
            int nr = r + DR[i];
            int nc = c + DC[i];
            if (inBounds(nr, nc) && mines[nr][nc]) {
                count++;
            }
        }
        return count;
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    static final class MoveResult {
        int openedCells;
        int scoreGained;
        boolean mineSkipped;
        boolean lost;
        boolean won;
    }

    private final class TwoHGenerator {
        final boolean[][] safeZone;
        final List<Integer>[] rowMasks;
        final int[] selected = new int[SIZE];
        final int[] chosen = new int[SIZE];
        final int[] minSuffix = new int[SIZE + 1];
        final int[] maxSuffix = new int[SIZE + 1];
        int candidates;

        @SuppressWarnings("unchecked")
        TwoHGenerator(boolean[][] safeZone) {
            this.safeZone = safeZone;
            rowMasks = new List[SIZE];
            for (int r = 0; r < SIZE; r++) {
                rowMasks[r] = new ArrayList<Integer>();
                for (int mask = 0; mask < 128; mask++) {
                    if (isLegalTwoHRow(mask) && avoidsSafeZone(r, mask)) {
                        rowMasks[r].add(Integer.valueOf(mask));
                    }
                }
            }
            for (int r = SIZE - 1; r >= 0; r--) {
                int min = 8;
                int max = -1;
                for (int i = 0; i < rowMasks[r].size(); i++) {
                    int count = Integer.bitCount(rowMasks[r].get(i).intValue());
                    min = Math.min(min, count);
                    max = Math.max(max, count);
                }
                minSuffix[r] = minSuffix[r + 1] + min;
                maxSuffix[r] = maxSuffix[r + 1] + max;
            }
        }

        int[] pick() {
            search(0, 0);
            if (candidates == 0) {
                throw new IllegalStateException("No valid 2H board for this first click.");
            }
            int[] copy = new int[SIZE];
            System.arraycopy(chosen, 0, copy, 0, SIZE);
            return copy;
        }

        void search(int row, int minesCount) {
            if (minesCount + minSuffix[row] > GameMode.TWO_H.mines) {
                return;
            }
            if (minesCount + maxSuffix[row] < GameMode.TWO_H.mines) {
                return;
            }
            if (row == SIZE) {
                if (minesCount == GameMode.TWO_H.mines) {
                    candidates++;
                    if (random.nextInt(candidates) == 0) {
                        System.arraycopy(selected, 0, chosen, 0, SIZE);
                    }
                }
                return;
            }
            Collections.shuffle(rowMasks[row], random);
            for (int i = 0; i < rowMasks[row].size(); i++) {
                int mask = rowMasks[row].get(i).intValue();
                selected[row] = mask;
                search(row + 1, minesCount + Integer.bitCount(mask));
            }
        }

        boolean avoidsSafeZone(int r, int mask) {
            for (int c = 0; c < SIZE; c++) {
                if (((mask >> c) & 1) == 1 && safeZone[r][c]) {
                    return false;
                }
            }
            return true;
        }
    }

    private final class TwoDGenerator {
        final long safeMask;
        final ArrayList<Placement> placements = new ArrayList<Placement>();

        TwoDGenerator(boolean[][] safeZone) {
            long mask = 0L;
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (safeZone[r][c]) {
                        mask |= cellBit(r, c);
                    }
                }
            }
            safeMask = mask;
            buildPlacements();
        }

        long pick() {
            long result = search(0, 0, 0L, 0L);
            if (result == 0L) {
                throw new IllegalStateException("No valid 2D board for this first click.");
            }
            return result;
        }

        long search(int start, int placed, long occupiedMask, long forbiddenMask) {
            if (placed == 6) {
                return occupiedMask;
            }
            if (placements.size() - start < 6 - placed) {
                return 0L;
            }
            ArrayList<Integer> order = new ArrayList<Integer>();
            for (int i = start; i < placements.size(); i++) {
                order.add(Integer.valueOf(i));
            }
            Collections.shuffle(order, random);
            for (int i = 0; i < order.size(); i++) {
                Placement p = placements.get(order.get(i).intValue());
                if ((p.cellsMask & safeMask) != 0L) {
                    continue;
                }
                if ((p.cellsMask & forbiddenMask) != 0L) {
                    continue;
                }
                long result = search(order.get(i).intValue() + 1, placed + 1,
                        occupiedMask | p.cellsMask, forbiddenMask | p.forbiddenMask);
                if (result != 0L) {
                    return result;
                }
            }
            return 0L;
        }

        private void buildPlacements() {
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE - 1; c++) {
                    placements.add(new Placement(r, c, r, c + 1));
                }
            }
            for (int r = 0; r < SIZE - 1; r++) {
                for (int c = 0; c < SIZE; c++) {
                    placements.add(new Placement(r, c, r + 1, c));
                }
            }
        }
    }

    private final class Placement {
        final int r1;
        final int c1;
        final int r2;
        final int c2;
        final long cellsMask;
        final long forbiddenMask;

        Placement(int r1, int c1, int r2, int c2) {
            this.r1 = r1;
            this.c1 = c1;
            this.r2 = r2;
            this.c2 = c2;
            this.cellsMask = cellBit(r1, c1) | cellBit(r2, c2);
            this.forbiddenMask = buildForbiddenMask(r1, c1, r2, c2);
        }
    }

    private static long cellBit(int r, int c) {
        return 1L << (r * SIZE + c);
    }

    private static long buildForbiddenMask(int r1, int c1, int r2, int c2) {
        long mask = 0L;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = r1 + dr;
                int c = c1 + dc;
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE) {
                    mask |= cellBit(r, c);
                }
                r = r2 + dr;
                c = c2 + dc;
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE) {
                    mask |= cellBit(r, c);
                }
            }
        }
        return mask;
    }

    private static boolean isLegalTwoHRow(int mask) {
        for (int c = 0; c < SIZE; c++) {
            if (((mask >> c) & 1) == 0) {
                continue;
            }
            boolean left = c > 0 && ((mask >> (c - 1)) & 1) == 1;
            boolean right = c < SIZE - 1 && ((mask >> (c + 1)) & 1) == 1;
            if (!left && !right) {
                return false;
            }
        }
        return true;
    }
}
