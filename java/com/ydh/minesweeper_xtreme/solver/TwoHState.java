package com.ydh.minesweeper_xtreme.solver;

import com.ydh.minesweeper_xtreme.game.GameMode;

import java.util.ArrayList;
import java.util.List;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.*;

final class TwoHState {
    final int[][] visible;
    final boolean[][] flags;
    final List<Integer>[] rowMasks;
    final int[] selected = new int[SIZE];
    final int[] minSuffix = new int[SIZE + 1];
    final int[] maxSuffix = new int[SIZE + 1];
    final boolean[][] mineSeen = new boolean[SIZE][SIZE];
    int solutionCount;

    @SuppressWarnings("unchecked")
    TwoHState(int[][] visible, boolean[][] flags) {
        this.visible = visible;
        this.flags = flags;
        rowMasks = new List[SIZE];
        for (int r = 0; r < SIZE; r++) {
            rowMasks[r] = new ArrayList<Integer>();
            for (int mask = 0; mask < 128; mask++) {
                if (isLegalTwoHRow(mask) && rowMaskMatchesKnown(r, mask)) {
                    rowMasks[r].add(mask);
                }
            }
        }
        for (int r = SIZE - 1; r >= 0; r--) {
            int min = 8;
            int max = -1;
            for (int i = 0; i < rowMasks[r].size(); i++) {
                int count = Integer.bitCount(rowMasks[r].get(i).intValue());
                if (count < min) {
                    min = count;
                }
                if (count > max) {
                    max = count;
                }
            }
            if (max < 0) {
                min = 8;
                max = -1;
            }
            minSuffix[r] = minSuffix[r + 1] + min;
            maxSuffix[r] = maxSuffix[r + 1] + max;
        }
    }

    void recurse(int row, int mines) {
        if (mines + minSuffix[row] > GameMode.TWO_H.mines) {
            return;
        }
        if (mines + maxSuffix[row] < GameMode.TWO_H.mines) {
            return;
        }
        if (row == SIZE) {
            if (mines == GameMode.TWO_H.mines && allNumbersMatch()) {
                solutionCount++;
                for (int r = 0; r < SIZE; r++) {
                    for (int c = 0; c < SIZE; c++) {
                        if (hasMine(r, c)) {
                            mineSeen[r][c] = true;
                        }
                    }
                }
            }
            return;
        }
        for (int i = 0; i < rowMasks[row].size(); i++) {
            int mask = rowMasks[row].get(i).intValue();
            selected[row] = mask;
            int nextMines = mines + Integer.bitCount(mask);
            if (row >= 2 && !numbersInRowMatch(row - 1)) {
                continue;
            }
            recurse(row + 1, nextMines);
        }
    }

    boolean rowMaskMatchesKnown(int r, int mask) {
        for (int c = 0; c < SIZE; c++) {
            boolean mine = ((mask >> c) & 1) == 1;
            if (visible[r][c] >= 0 && mine) {
                return false;
            }
            if (visible[r][c] == KNOWN_MINE && !mine) {
                return false;
            }
        }
        return true;
    }

    boolean allNumbersMatch() {
        for (int r = 0; r < SIZE; r++) {
            if (!numbersInRowMatch(r)) {
                return false;
            }
        }
        return true;
    }

    boolean numbersInRowMatch(int r) {
        for (int c = 0; c < SIZE; c++) {
            if (visible[r][c] >= 0 && countAdjacentMines(r, c) != visible[r][c]) {
                return false;
            }
        }
        return true;
    }

    int countAdjacentMines(int r, int c) {
        int count = 0;
        for (int i = 0; i < DR.length; i++) {
            int nr = r + DR[i];
            int nc = c + DC[i];
            if (inBounds(nr, nc) && hasMine(nr, nc)) {
                count++;
            }
        }
        return count;
    }

    boolean hasMine(int r, int c) {
        return ((selected[r] >> c) & 1) == 1;
    }

    boolean[][] findSolutionWithMine(int mineR, int mineC) {
        boolean[][] solution = new boolean[SIZE][SIZE];
        return searchSolution(0, 0, mineR, mineC, solution) ? solution : null;
    }

    private boolean searchSolution(int row, int mines, int mineR, int mineC, boolean[][] solution) {
        if (mines + minSuffix[row] > GameMode.TWO_H.mines) {
            return false;
        }
        if (mines + maxSuffix[row] < GameMode.TWO_H.mines) {
            return false;
        }
        if (row == SIZE) {
            if (mines == GameMode.TWO_H.mines && hasMine(mineR, mineC) && allNumbersMatch()) {
                copySelected(solution);
                return true;
            }
            return false;
        }
        for (int i = 0; i < rowMasks[row].size(); i++) {
            int mask = rowMasks[row].get(i).intValue();
            selected[row] = mask;
            int nextMines = mines + Integer.bitCount(mask);
            if (row >= 2 && !numbersInRowMatch(row - 1)) {
                continue;
            }
            if (searchSolution(row + 1, nextMines, mineR, mineC, solution)) {
                return true;
            }
        }
        return false;
    }

    private void copySelected(boolean[][] solution) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                solution[r][c] = hasMine(r, c);
            }
        }
    }
}
