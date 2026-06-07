package com.ydh.minesweeper_xtreme.solver;

import com.ydh.minesweeper_xtreme.game.GameMode;

import java.util.ArrayList;
import java.util.List;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.*;

final class NormalState {
    final int[][] visible;
    final boolean[][] flags;
    final int[][] variableIndex = new int[SIZE][SIZE];
    final List<Cell> variables = new ArrayList<Cell>();
    final List<Cell> numbers = new ArrayList<Cell>();
    final boolean[] assigned = new boolean[SIZE * SIZE];
    final boolean[] value = new boolean[SIZE * SIZE];
    boolean[] mineSeen = new boolean[SIZE * SIZE];
    int knownMineCount;
    int outsideCount;
    int solutionCount;
    boolean outsideCanContainMine;

    NormalState(int[][] visible, boolean[][] flags) {
        this.visible = visible;
        this.flags = flags;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                variableIndex[r][c] = -1;
                if (isKnownMine(r, c)) {
                    knownMineCount++;
                }
                if (visible[r][c] >= 0) {
                    numbers.add(new Cell(r, c));
                }
            }
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!isUnknown(r, c)) {
                    continue;
                }
                if (touchesNumber(r, c)) {
                    variableIndex[r][c] = variables.size();
                    variables.add(new Cell(r, c));
                } else {
                    outsideCount++;
                }
            }
        }
        mineSeen = new boolean[variables.size()];
    }

    boolean isKnownMine(int r, int c) {
        return visible[r][c] == KNOWN_MINE;
    }

    boolean isUnknown(int r, int c) {
        return visible[r][c] == HIDDEN;
    }

    boolean touchesNumber(int r, int c) {
        for (int i = 0; i < DR.length; i++) {
            int nr = r + DR[i];
            int nc = c + DC[i];
            if (inBounds(nr, nc) && visible[nr][nc] >= 0) {
                return true;
            }
        }
        return false;
    }

    void recurse(int k) {
        if (!partiallyConsistent()) {
            return;
        }
        if (k == variables.size()) {
            int mines = knownMineCount;
            for (int i = 0; i < variables.size(); i++) {
                if (value[i]) {
                    mines++;
                }
            }
            int outsideMines = GameMode.NORMAL.mines - mines;
            if (outsideMines < 0 || outsideMines > outsideCount) {
                return;
            }
            solutionCount++;
            if (outsideMines > 0) {
                outsideCanContainMine = true;
            }
            for (int i = 0; i < variables.size(); i++) {
                if (value[i]) {
                    mineSeen[i] = true;
                }
            }
            return;
        }

        assigned[k] = true;
        value[k] = true;
        recurse(k + 1);
        value[k] = false;
        recurse(k + 1);
        assigned[k] = false;
    }

    boolean partiallyConsistent() {
        for (int n = 0; n < numbers.size(); n++) {
            Cell cell = numbers.get(n);
            int mines = 0;
            int unknown = 0;
            for (int i = 0; i < DR.length; i++) {
                int nr = cell.r + DR[i];
                int nc = cell.c + DC[i];
                if (!inBounds(nr, nc)) {
                    continue;
                }
                if (isKnownMine(nr, nc)) {
                    mines++;
                } else {
                    int index = variableIndex[nr][nc];
                    if (index >= 0) {
                        if (assigned[index]) {
                            if (value[index]) {
                                mines++;
                            }
                        } else {
                            unknown++;
                        }
                    }
                }
            }
            int number = visible[cell.r][cell.c];
            if (mines > number || mines + unknown < number) {
                return false;
            }
        }
        int assignedMines = knownMineCount;
        for (int i = 0; i < variables.size(); i++) {
            if (assigned[i] && value[i]) {
                assignedMines++;
            }
        }
        return assignedMines <= GameMode.NORMAL.mines;
    }

    boolean[][] findSolutionWithMine(int mineR, int mineC) {
        boolean[][] solution = new boolean[SIZE][SIZE];
        return searchSolution(0, mineR, mineC, solution) ? solution : null;
    }

    private boolean searchSolution(int k, int mineR, int mineC, boolean[][] solution) {
        if (!partiallyConsistent()) {
            return false;
        }
        int requiredIndex = variableIndex[mineR][mineC];
        if (requiredIndex >= 0 && assigned[requiredIndex] && !value[requiredIndex]) {
            return false;
        }
        if (k == variables.size()) {
            int mines = knownMineCount;
            for (int i = 0; i < variables.size(); i++) {
                if (value[i]) {
                    mines++;
                }
            }
            int outsideMines = GameMode.NORMAL.mines - mines;
            if (outsideMines < 0 || outsideMines > outsideCount) {
                return false;
            }
            if (requiredIndex >= 0) {
                if (!value[requiredIndex]) {
                    return false;
                }
            } else if (isUnknown(mineR, mineC)) {
                if (outsideMines <= 0) {
                    return false;
                }
            } else if (!isKnownMine(mineR, mineC)) {
                return false;
            }
            buildSolution(solution, mineR, mineC, outsideMines);
            return true;
        }

        assigned[k] = true;
        value[k] = true;
        if (searchSolution(k + 1, mineR, mineC, solution)) {
            assigned[k] = false;
            return true;
        }
        value[k] = false;
        if (searchSolution(k + 1, mineR, mineC, solution)) {
            assigned[k] = false;
            return true;
        }
        assigned[k] = false;
        return false;
    }

    private void buildSolution(boolean[][] solution, int mineR, int mineC, int outsideMines) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                solution[r][c] = isKnownMine(r, c);
            }
        }
        for (int i = 0; i < variables.size(); i++) {
            Cell cell = variables.get(i);
            solution[cell.r][cell.c] = value[i];
        }
        int requiredIndex = variableIndex[mineR][mineC];
        if (requiredIndex < 0 && isUnknown(mineR, mineC)) {
            solution[mineR][mineC] = true;
            outsideMines--;
        }
        for (int r = 0; r < SIZE && outsideMines > 0; r++) {
            for (int c = 0; c < SIZE && outsideMines > 0; c++) {
                if (visible[r][c] == HIDDEN && variableIndex[r][c] < 0 && !solution[r][c]) {
                    solution[r][c] = true;
                    outsideMines--;
                }
            }
        }
    }
}
