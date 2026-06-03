package com.ydh.minesweeper_xtreme;

final class MinesweeperSolver {
    static final int HIDDEN = -1;
    static final int KNOWN_MINE = -3;
    static final int SIZE = 7;

    static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

    private MinesweeperSolver() {
    }

    static Result findCertainSafe(GameMode mode, int[][] visible, boolean[][] flags) {
        if (mode == GameMode.TWO_G) {
            return solveTwoG(visible, flags);
        }
        if (mode == GameMode.TWO_D) {
            return solveTwoD(visible, flags);
        }
        if (mode == GameMode.TWO_H) {
            return solveTwoH(visible, flags);
        }
        return solveNormal(visible, flags);
    }

    static boolean[][] findMineMapWithMine(GameMode mode, int[][] visible, int mineR, int mineC) {
        if (!inBounds(mineR, mineC) || visible[mineR][mineC] >= 0) {
            return null;
        }
        boolean[][] flags = new boolean[SIZE][SIZE];
        if (mode == GameMode.TWO_G) {
            TwoGState state = new TwoGState(visible, flags);
            return state.findSolutionWithMine(mineR, mineC);
        }
        if (mode == GameMode.TWO_D) {
            TwoDState state = new TwoDState(visible, flags, bit(mineR, mineC));
            if (state.searchFirst(0, 0, 0L, 0L, state.requiredMask)) {
                return maskToBoard(state.solutionMask);
            }
            return null;
        }
        if (mode == GameMode.TWO_H) {
            TwoHState state = new TwoHState(visible, flags);
            return state.findSolutionWithMine(mineR, mineC);
        }
        NormalState state = new NormalState(visible, flags);
        return state.findSolutionWithMine(mineR, mineC);
    }

    static final class Result {
        final boolean[][] safe = new boolean[SIZE][SIZE];
        int solutionCount;

        boolean hasCertainSafe() {
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (safe[r][c]) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static Result solveNormal(int[][] visible, boolean[][] flags) {
        NormalState state = new NormalState(visible, flags);
        state.recurse(0);
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result;
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (state.isUnknown(r, c)) {
                    int index = state.variableIndex[r][c];
                    if (index >= 0) {
                        result.safe[r][c] = !state.mineSeen[index];
                    } else {
                        result.safe[r][c] = !state.outsideCanContainMine;
                    }
                }
            }
        }
        return result;
    }


    private static Result solveTwoG(int[][] visible, boolean[][] flags) {
        TwoGState state = new TwoGState(visible, flags);
        state.recurse(0, 0);
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result;
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == HIDDEN) {
                    result.safe[r][c] = !state.mineSeen[r][c];
                }
            }
        }
        return result;
    }

    private static Result solveTwoD(int[][] visible, boolean[][] flags) {
        TwoDState state = new TwoDState(visible, flags);
        state.search(0, 0, 0L, 0L, state.requiredMask);
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result;
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == HIDDEN) {
                    long bit = bit(r, c);
                    result.safe[r][c] = (state.mineSeenMask & bit) == 0L;
                }
            }
        }
        return result;
    }


    private static Result solveTwoH(int[][] visible, boolean[][] flags) {
        TwoHState state = new TwoHState(visible, flags);
        state.recurse(0, 0);
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result;
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == HIDDEN) {
                    result.safe[r][c] = !state.mineSeen[r][c];
                }
            }
        }
        return result;
    }


    static long bit(int r, int c) {
        return 1L << (r * SIZE + c);
    }

    static boolean[][] maskToBoard(long mask) {
        boolean[][] board = new boolean[SIZE][SIZE];
        for (int idx = 0; idx < SIZE * SIZE; idx++) {
            if (((mask >> idx) & 1L) == 1L) {
                board[idx / SIZE][idx % SIZE] = true;
            }
        }
        return board;
    }

    static long buildAdjacentMask(long cellBit) {
        int index = Long.numberOfTrailingZeros(cellBit);
        int r = index / SIZE;
        int c = index % SIZE;
        long mask = 0L;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = r + dr;
                int nc = c + dc;
                if (inBounds(nr, nc)) {
                    mask |= bit(nr, nc);
                }
            }
        }
        return mask;
    }

    static long buildDominoConnectionMask(int r1, int c1, int r2, int c2) {
        long ownCells = bit(r1, c1) | bit(r2, c2);
        long mask = orthogonalNeighborMask(r1, c1) | orthogonalNeighborMask(r2, c2);
        return mask & ~ownCells;
    }

    static long orthogonalNeighborMask(int r, int c) {
        long mask = 0L;
        if (r > 0) {
            mask |= bit(r - 1, c);
        }
        if (r < SIZE - 1) {
            mask |= bit(r + 1, c);
        }
        if (c > 0) {
            mask |= bit(r, c - 1);
        }
        if (c < SIZE - 1) {
            mask |= bit(r, c + 1);
        }
        return mask;
    }

    static boolean isLegalTwoHRow(int mask) {
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

    static int countAdjacentMines(boolean[][] mines, int r, int c) {
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

    static boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

}
