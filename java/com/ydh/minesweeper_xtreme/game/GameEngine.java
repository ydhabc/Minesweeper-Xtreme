package com.ydh.minesweeper_xtreme.game;

import com.ydh.minesweeper_xtreme.solver.MinesweeperSolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class GameEngine {
    public static final int SIZE = 7;

    private static final int MAX_GENERATE_ATTEMPTS = 300;
    private static final long FAST_STEP_BONUS_MILLIS = 10000L;
    private static final int FAST_STEP_BONUS_SCORE = 15;

    private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

    private final Random random = new Random();

    public GameMode mode;
    public boolean stepTimerEnabled;
    public boolean expertModeEnabled;
    public boolean generated;
    public boolean gameOver;
    public boolean won;
    public boolean newRecord;
    public boolean paused;
    public boolean pauseUsed;
    public boolean[][] mines = new boolean[SIZE][SIZE];
    public boolean[][] revealed = new boolean[SIZE][SIZE];
    public boolean[][] flagged = new boolean[SIZE][SIZE];
    public boolean[][] skippedMine = new boolean[SIZE][SIZE];
    public int[][] numbers = new int[SIZE][SIZE];
    public int score;
    public int combo;
    public long globalStartMillis;
    public long lastActionMillis;
    public long lastStepStartMillis;
    public long pauseStartMillis;
    public long gameOverMillis;
    public String message = "点击任意格开始。";

    public GameEngine() {
        reset(GameMode.NORMAL, false, false);
    }

    public void reset(GameMode mode, boolean stepTimerEnabled, boolean expertModeEnabled) {
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

    public MoveResult reveal(int r, int c, long now) {
        MoveResult result = new MoveResult();
        if (gameOver || paused || !inBounds(r, c) || flagged[r][c] || revealed[r][c] || skippedMine[r][c]) {
            return result;
        }
        boolean wasGenerated = generated;
        if (!generated) {
            if (!generate(r, c)) {
                clearMines();
                message = "当前首点无法生成合法地图，请换一格重新点击。";
                return result;
            }
            generated = true;
            globalStartMillis = now;
            lastStepStartMillis = now;
        }
        boolean hasCertainSafe = false;
        if (wasGenerated) {
            MinesweeperSolver.Result solverResult =
                    MinesweeperSolver.findCertainSafe(mode, makeVisibleBoard(), flagged);
            hasCertainSafe = solverResult.hasCertainSafe();
            if (expertModeEnabled && hasCertainSafe && !solverResult.safe[r][c]) {
                boolean[][] displayMines = MinesweeperSolver.findMineMapWithMine(mode, makeVisibleBoard(), r, c);
                if (displayMines != null) {
                    replaceMines(displayMines);
                }
                lose("当前局面存在确定安全格，专家模式不允许猜测，判负。", now);
                result.lost = true;
                return result;
            }
        }

        if (mines[r][c]) {
            if (hasCertainSafe) {
                lose("当前局面存在确定安全格，但点到了雷，判负。", now);
                result.lost = true;
                return result;
            }
            skippedMine[r][c] = true;
            flagged[r][c] = false;
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
            int baseGained = opened * 5 * combo;
            boolean fastStepBonus = lastStepStartMillis > 0L
                    && now - lastStepStartMillis <= FAST_STEP_BONUS_MILLIS;
            int gained = baseGained + (fastStepBonus ? FAST_STEP_BONUS_SCORE : 0);
            score += gained;
            lastActionMillis = now;
            lastStepStartMillis = now;
            result.openedCells = opened;
            result.scoreGained = gained;
            message = "打开 " + opened + " 格，连击 x" + combo + "，+" + baseGained + " 分"
                    + (fastStepBonus ? "，10秒内选择奖励 +" + FAST_STEP_BONUS_SCORE + " 分" : "")
                    + "。";
        }
        if (isWin()) {
            win(now);
            result.won = true;
        }
        return result;
    }

    public void toggleFlag(int r, int c) {
        if (gameOver || paused || !generated || !inBounds(r, c) || revealed[r][c] || skippedMine[r][c]) {
            return;
        }
        flagged[r][c] = !flagged[r][c];
        message = flagged[r][c] ? "已插旗。" : "已取消旗标。";
    }

    public void loseByTimeout() {
        loseByTimeout(System.currentTimeMillis());
    }

    public void loseByTimeout(long now) {
        lose("单步倒计时结束，判负。", now);
    }

    public void togglePause(long now) {
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

    public void pauseFromMenu(long now) {
        if (gameOver || !generated || paused) {
            return;
        }
        paused = true;
        pauseUsed = true;
        pauseStartMillis = now;
        message = "已返回主页，本局自动暂停。";
    }

    public void resumeFromMenu(long now) {
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

    public int[][] makeVisibleBoard() {
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

    public int remainingMinesDisplay() {
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

    public int totalMinesDisplay() {
        return mode.mines;
    }

    public int elapsedSeconds(long now) {
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

    public int stepRemainingSeconds(long now) {
        if (!stepTimerEnabled || !generated || gameOver) {
            return 60;
        }
        if (paused) {
            now = pauseStartMillis;
        }
        int elapsed = (int) ((now - lastStepStartMillis) / 1000L);
        return Math.max(0, 60 - elapsed);
    }

    public void updateComboTimeout(long now) {
        if (!generated || gameOver || paused || lastActionMillis == 0L || combo == 0) {
            return;
        }
        if (now - lastActionMillis > 30000L) {
            combo = 0;
            message = "单步操作超过30秒，连击清零。";
        }
    }

    private boolean generate(int firstR, int firstC) {
        boolean[][] safeZone = makeSafeZone(firstR, firstC);

        for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
            clearMines();

            boolean success;
            if (mode == GameMode.TWO_G) {
                success = generateTwoG(safeZone);
            } else if (mode == GameMode.TWO_D) {
                success = generateTwoD(safeZone);
            } else if (mode == GameMode.TWO_H) {
                success = generateTwoH(safeZone);
            } else {
                success = generateNormal(safeZone);
            }

            if (success) {
                computeNumbers();
                return true;
            }
        }

        clearMines();
        return false;
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

    private boolean generateNormal(boolean[][] safeZone) {
        List<Integer> cells = new ArrayList<Integer>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!safeZone[r][c]) {
                    cells.add(Integer.valueOf(r * SIZE + c));
                }
            }
        }
        if (cells.size() < mode.mines) {
            clearMines();
            return false;
        }
        Collections.shuffle(cells, random);
        for (int i = 0; i < mode.mines; i++) {
            int cell = cells.get(i).intValue();
            mines[cell / SIZE][cell % SIZE] = true;
        }
        return true;
    }

    private boolean generateTwoG(boolean[][] safeZone) {
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
            return true;
        }

        clearMines();
        return false;
    }

    private boolean generateTwoH(boolean[][] safeZone) {
        try {
            TwoHGenerator generator = new TwoHGenerator(safeZone);
            int[] selected = generator.pick();

            clearMines();
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    mines[r][c] = ((selected[r] >> c) & 1) == 1;
                }
            }
            return true;
        } catch (IllegalStateException ignored) {
            clearMines();
            return false;
        }
    }

    private boolean generateTwoD(boolean[][] safeZone) {
        try {
            TwoDGenerator generator = new TwoDGenerator(safeZone);
            long selected = generator.pick();

            clearMines();
            for (int idx = 0; idx < SIZE * SIZE; idx++) {
                if (((selected >> idx) & 1L) == 1L) {
                    mines[idx / SIZE][idx % SIZE] = true;
                }
            }
            return true;
        } catch (IllegalStateException ignored) {
            clearMines();
            return false;
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
            flagged[cr][cc] = false;
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
        final long[][] suffixWays = new long[SIZE + 1][GameMode.TWO_H.mines + 1];

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
            buildSuffixWays();
        }

        int[] pick() {
            int remainingMines = GameMode.TWO_H.mines;
            long totalWays = suffixWays[0][remainingMines];
            if (totalWays <= 0L) {
                throw new IllegalStateException("No valid 2H board for this first click.");
            }

            for (int row = 0; row < SIZE; row++) {
                long choice = nextLongBounded(suffixWays[row][remainingMines]);
                for (int i = 0; i < rowMasks[row].size(); i++) {
                    int mask = rowMasks[row].get(i).intValue();
                    int count = Integer.bitCount(mask);
                    if (count > remainingMines) {
                        continue;
                    }
                    long branchWays = suffixWays[row + 1][remainingMines - count];
                    if (choice < branchWays) {
                        selected[row] = mask;
                        remainingMines -= count;
                        break;
                    }
                    choice -= branchWays;
                }
            }

            int[] copy = new int[SIZE];
            System.arraycopy(selected, 0, copy, 0, SIZE);
            return copy;
        }

        private void buildSuffixWays() {
            suffixWays[SIZE][0] = 1L;
            for (int row = SIZE - 1; row >= 0; row--) {
                for (int remaining = 0; remaining <= GameMode.TWO_H.mines; remaining++) {
                    long total = 0L;
                    for (int i = 0; i < rowMasks[row].size(); i++) {
                        int mask = rowMasks[row].get(i).intValue();
                        int count = Integer.bitCount(mask);
                        if (count <= remaining) {
                            total = cappedAdd(total, suffixWays[row + 1][remaining - count]);
                        }
                    }
                    suffixWays[row][remaining] = total;
                }
            }
        }

        private long cappedAdd(long a, long b) {
            if (Long.MAX_VALUE - a < b) {
                return Long.MAX_VALUE;
            }
            return a + b;
        }

        private long nextLongBounded(long bound) {
            if (bound <= 0L) {
                throw new IllegalArgumentException("bound must be positive");
            }
            if (bound <= Integer.MAX_VALUE) {
                return random.nextInt((int) bound);
            }
            long bits;
            long value;
            do {
                bits = random.nextLong() & Long.MAX_VALUE;
                value = bits % bound;
            } while (bits - value + (bound - 1L) < 0L);
            return value;
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
            this.forbiddenMask = buildDominoConnectionMask(r1, c1, r2, c2);
        }
    }

    private static long cellBit(int r, int c) {
        return 1L << (r * SIZE + c);
    }

    private static long buildDominoConnectionMask(int r1, int c1, int r2, int c2) {
        long mask = cellBit(r1, c1) | cellBit(r2, c2);
        mask |= orthogonalNeighborMask(r1, c1);
        mask |= orthogonalNeighborMask(r2, c2);
        mask &= ~(cellBit(r1, c1) | cellBit(r2, c2));
        return mask;
    }

    private static long orthogonalNeighborMask(int r, int c) {
        long mask = 0L;
        if (r > 0) {
            mask |= cellBit(r - 1, c);
        }
        if (r < SIZE - 1) {
            mask |= cellBit(r + 1, c);
        }
        if (c > 0) {
            mask |= cellBit(r, c - 1);
        }
        if (c < SIZE - 1) {
            mask |= cellBit(r, c + 1);
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
