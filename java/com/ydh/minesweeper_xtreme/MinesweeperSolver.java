package com.ydh.minesweeper_xtreme;

import java.util.ArrayList;
import java.util.List;

final class MinesweeperSolver {
    static final int HIDDEN = -1;
    static final int KNOWN_MINE = -3;
    static final int SIZE = 7;

    private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

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

    private static final class NormalState {
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

    private static final class TwoGState {
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

    private static final class TwoDState {
        final int[][] visible;
        final boolean[][] flags;
        final long boardMask = (1L << (SIZE * SIZE)) - 1L;
        final long allowedMask;
        final long requiredMask;
        final ArrayList<Placement> placements = new ArrayList<Placement>();
        final ArrayList<Integer>[] placementsByCell;
        final ArrayList<Clue> clues = new ArrayList<Clue>();
        long mineSeenMask;
        long solutionMask;
        int solutionCount;

        @SuppressWarnings("unchecked")
        TwoDState(int[][] visible, boolean[][] flags) {
            this(visible, flags, 0L);
        }

        @SuppressWarnings("unchecked")
        TwoDState(int[][] visible, boolean[][] flags, long extraRequiredMask) {
            this.visible = visible;
            this.flags = flags;
            long allowed = 0L;
            long required = extraRequiredMask;
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    long bit = bit(r, c);
                    if (visible[r][c] >= 0) {
                        continue;
                    }
                    allowed |= bit;
                    if (visible[r][c] == KNOWN_MINE) {
                        required |= bit;
                    }
                }
            }
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (visible[r][c] >= 0) {
                        clues.add(new Clue(bit(r, c), visible[r][c]));
                    }
                }
            }
            allowedMask = allowed;
            requiredMask = required;
            placementsByCell = new ArrayList[SIZE * SIZE];
            for (int i = 0; i < placementsByCell.length; i++) {
                placementsByCell[i] = new ArrayList<Integer>();
            }
            buildPlacements();
        }

        void search(int start, int placed, long occupiedMask, long forbiddenMask, long remainingRequiredMask) {
            if (solutionCount > 0 && (mineSeenMask & allowedMask) == allowedMask) {
                return;
            }
            if (placed == 6) {
                long currentMineMask = occupiedMask | requiredMask;
                if (remainingRequiredMask != 0L) {
                    return;
                }
                if (!allCluesMatch(currentMineMask)) {
                    return;
                }
                solutionCount++;
                mineSeenMask |= currentMineMask;
                return;
            }

            int needed = 6 - placed;
            if (placements.size() - start < needed) {
                return;
            }

            long currentMineMask = occupiedMask | requiredMask;
            if (!cluesRemainPossible(currentMineMask, forbiddenMask)) {
                return;
            }

            if (remainingRequiredMask != 0L) {
                if (!requiredCellsStillCoverable(start, forbiddenMask, remainingRequiredMask)) {
                    return;
                }
            }

            ArrayList<Integer> preferred = new ArrayList<Integer>();
            ArrayList<Integer> others = new ArrayList<Integer>();
            for (int i = start; i < placements.size(); i++) {
                Placement p = placements.get(i);
                if (!isCompatible(p, forbiddenMask, remainingRequiredMask)) {
                    continue;
                }
                if ((p.cellsMask & remainingRequiredMask) != 0L) {
                    preferred.add(Integer.valueOf(i));
                } else {
                    others.add(Integer.valueOf(i));
                }
            }

            for (int pass = 0; pass < 2; pass++) {
                ArrayList<Integer> list = pass == 0 ? preferred : others;
                for (int j = 0; j < list.size(); j++) {
                    int idx = list.get(j).intValue();
                    Placement p = placements.get(idx);
                    long newOccupied = occupiedMask | p.cellsMask;
                    long newForbidden = forbiddenMask | p.touchMask;
                    long newRemainingRequired = remainingRequiredMask & ~p.cellsMask;
                    if (!cluesRemainPossible(newOccupied | requiredMask, newForbidden)) {
                        continue;
                    }
                    search(idx + 1, placed + 1, newOccupied, newForbidden, newRemainingRequired);
                    if ((mineSeenMask & allowedMask) == allowedMask) {
                        return;
                    }
                }
                if (remainingRequiredMask == 0L) {
                    break;
                }
            }
        }

        boolean searchFirst(int start, int placed, long occupiedMask, long forbiddenMask, long remainingRequiredMask) {
            if (placed == 6) {
                long currentMineMask = occupiedMask | requiredMask;
                if (remainingRequiredMask != 0L || !allCluesMatch(currentMineMask)) {
                    return false;
                }
                solutionMask = currentMineMask;
                return true;
            }

            int needed = 6 - placed;
            if (placements.size() - start < needed) {
                return false;
            }

            long currentMineMask = occupiedMask | requiredMask;
            if (!cluesRemainPossible(currentMineMask, forbiddenMask)) {
                return false;
            }
            if (remainingRequiredMask != 0L
                    && !requiredCellsStillCoverable(start, forbiddenMask, remainingRequiredMask)) {
                return false;
            }

            ArrayList<Integer> preferred = new ArrayList<Integer>();
            ArrayList<Integer> others = new ArrayList<Integer>();
            for (int i = start; i < placements.size(); i++) {
                Placement p = placements.get(i);
                if (!isCompatible(p, forbiddenMask, remainingRequiredMask)) {
                    continue;
                }
                if ((p.cellsMask & remainingRequiredMask) != 0L) {
                    preferred.add(Integer.valueOf(i));
                } else {
                    others.add(Integer.valueOf(i));
                }
            }

            for (int pass = 0; pass < 2; pass++) {
                ArrayList<Integer> list = pass == 0 ? preferred : others;
                for (int j = 0; j < list.size(); j++) {
                    int idx = list.get(j).intValue();
                    Placement p = placements.get(idx);
                    long newOccupied = occupiedMask | p.cellsMask;
                    long newForbidden = forbiddenMask | p.touchMask;
                    long newRemainingRequired = remainingRequiredMask & ~p.cellsMask;
                    if (!cluesRemainPossible(newOccupied | requiredMask, newForbidden)) {
                        continue;
                    }
                    if (searchFirst(idx + 1, placed + 1, newOccupied, newForbidden, newRemainingRequired)) {
                        return true;
                    }
                }
                if (remainingRequiredMask == 0L) {
                    break;
                }
            }
            return false;
        }

        private boolean requiredCellsStillCoverable(int start, long forbiddenMask, long remainingRequiredMask) {
            long bits = remainingRequiredMask;
            while (bits != 0L) {
                long bit = bits & -bits;
                int cellIndex = Long.numberOfTrailingZeros(bit);
                boolean possible = false;
                ArrayList<Integer> options = placementsByCell[cellIndex];
                for (int i = 0; i < options.size(); i++) {
                    int idx = options.get(i).intValue();
                    if (idx < start) {
                        continue;
                    }
                    Placement p = placements.get(idx);
                    if (isCompatible(p, forbiddenMask, remainingRequiredMask)) {
                        possible = true;
                        break;
                    }
                }
                if (!possible) {
                    return false;
                }
                bits &= bits - 1;
            }
            return true;
        }

        private boolean isCompatible(Placement p, long forbiddenMask, long remainingRequiredMask) {
            if ((p.cellsMask & allowedMask) != p.cellsMask) {
                return false;
            }
            if ((p.cellsMask & forbiddenMask) != 0L) {
                return false;
            }
            long touchedRequired = p.touchMask & remainingRequiredMask;
            if ((touchedRequired & ~p.cellsMask) != 0L) {
                return false;
            }
            return true;
        }

        private boolean cluesRemainPossible(long currentMineMask, long forbiddenMask) {
            for (int i = 0; i < clues.size(); i++) {
                Clue clue = clues.get(i);
                int current = Long.bitCount(currentMineMask & clue.adjacentMask);
                if (current > clue.number) {
                    return false;
                }
                long available = clue.adjacentMask & allowedMask & ~forbiddenMask & ~currentMineMask;
                if (current + Long.bitCount(available) < clue.number) {
                    return false;
                }
            }
            return true;
        }

        private boolean allCluesMatch(long currentMineMask) {
            for (int i = 0; i < clues.size(); i++) {
                Clue clue = clues.get(i);
                if (Long.bitCount(currentMineMask & clue.adjacentMask) != clue.number) {
                    return false;
                }
            }
            return true;
        }

        private void buildPlacements() {
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE - 1; c++) {
                    addPlacement(r, c, r, c + 1);
                }
            }
            for (int r = 0; r < SIZE - 1; r++) {
                for (int c = 0; c < SIZE; c++) {
                    addPlacement(r, c, r + 1, c);
                }
            }
        }

        private void addPlacement(int r1, int c1, int r2, int c2) {
            long cellsMask = bit(r1, c1) | bit(r2, c2);
            if ((cellsMask & ~allowedMask) != 0L) {
                return;
            }
            long touchMask = buildTouchMask(r1, c1, r2, c2);
            Placement placement = new Placement(cellsMask, touchMask);
            int index = placements.size();
            placements.add(placement);
            addCellPlacement(index, r1, c1);
            addCellPlacement(index, r2, c2);
        }

        private void addCellPlacement(int placementIndex, int r, int c) {
            placementsByCell[r * SIZE + c].add(Integer.valueOf(placementIndex));
        }
    }

    private static final class Placement {
        final long cellsMask;
        final long touchMask;

        Placement(long cellsMask, long touchMask) {
            this.cellsMask = cellsMask;
            this.touchMask = touchMask;
        }
    }

    private static final class Clue {
        final long cellBit;
        final long adjacentMask;
        final int number;

        Clue(long cellBit, int number) {
            this.cellBit = cellBit;
            this.adjacentMask = buildAdjacentMask(cellBit);
            this.number = number;
        }
    }

    private static final class TwoHState {
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

    private static long bit(int r, int c) {
        return 1L << (r * SIZE + c);
    }

    private static boolean[][] maskToBoard(long mask) {
        boolean[][] board = new boolean[SIZE][SIZE];
        for (int idx = 0; idx < SIZE * SIZE; idx++) {
            if (((mask >> idx) & 1L) == 1L) {
                board[idx / SIZE][idx % SIZE] = true;
            }
        }
        return board;
    }

    private static long buildAdjacentMask(long cellBit) {
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

    private static long buildTouchMask(int r1, int c1, int r2, int c2) {
        long mask = 0L;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r1 + dr;
                int nc = c1 + dc;
                if (inBounds(nr, nc)) {
                    mask |= bit(nr, nc);
                }
                nr = r2 + dr;
                nc = c2 + dc;
                if (inBounds(nr, nc)) {
                    mask |= bit(nr, nc);
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

    private static int countAdjacentMines(boolean[][] mines, int r, int c) {
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

    private static boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

    private static final class Cell {
        final int r;
        final int c;

        Cell(int r, int c) {
            this.r = r;
            this.c = c;
        }
    }
}
