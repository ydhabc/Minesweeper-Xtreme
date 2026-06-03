package com.ydh.minesweeper_xtreme;

import static com.ydh.minesweeper_xtreme.MinesweeperSolver.*;

final class TwoGState {
    final int[][] visible;
    final boolean[][] flags;
    final boolean[][] mines = new boolean[SIZE][SIZE];
    final boolean[][] mineSeen = new boolean[SIZE][SIZE];
    int solutionCount;

    TwoGState(int[][] visible, boolean[][] flags) {
        this.visible = visible;
        this.flags = flags;
    }

    void recurse(int start, int blocks) {
        if (blocks == 3) {
            if (!matchesKnownCells() || !matchesNumbers()) {
                return;
            }
            solutionCount++;
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (mines[r][c]) {
                        mineSeen[r][c] = true;
                    }
                }
            }
            return;
        }
        int remaining = 3 - blocks;
        for (int p = start; p <= 36 - remaining; p++) {
            int r = p / 6;
            int c = p % 6;
            if (!canPlaceBlock(r, c)) {
                continue;
            }
            setBlock(r, c, true);
            recurse(p + 1, blocks + 1);
            setBlock(r, c, false);
        }
    }

    boolean canPlaceBlock(int r, int c) {
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (mines[nr][nc] || visible[nr][nc] >= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    void setBlock(int r, int c, boolean value) {
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                mines[r + dr][c + dc] = value;
            }
        }
    }

    boolean matchesKnownCells() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == KNOWN_MINE && !mines[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean matchesNumbers() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] >= 0 && countAdjacentMines(mines, r, c) != visible[r][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean[][] findSolutionWithMine(int mineR, int mineC) {
        boolean[][] solution = new boolean[SIZE][SIZE];
        return searchSolution(0, 0, mineR, mineC, solution) ? solution : null;
    }

    private boolean searchSolution(int start, int blocks, int mineR, int mineC, boolean[][] solution) {
        if (blocks == 3) {
            if (!mines[mineR][mineC] || !matchesKnownCells() || !matchesNumbers()) {
                return false;
            }
            copyMines(solution);
            return true;
        }
        int remaining = 3 - blocks;
        for (int p = start; p <= 36 - remaining; p++) {
            int r = p / 6;
            int c = p % 6;
            if (!canPlaceBlock(r, c)) {
                continue;
            }
            setBlock(r, c, true);
            if (searchSolution(p + 1, blocks + 1, mineR, mineC, solution)) {
                setBlock(r, c, false);
                return true;
            }
            setBlock(r, c, false);
        }
        return false;
    }

    private void copyMines(boolean[][] solution) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                solution[r][c] = mines[r][c];
            }
        }
    }
}
