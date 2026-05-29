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
        private static final int DOMINO_COUNT = GameMode.TWO_D.mines / 2;

        final int[][] visible;
        final boolean[][] flags;
        final long allowedMask;
        final long requiredMask;
        final long hiddenMask;
        final ArrayList<DominoPlacement> placements = new ArrayList<DominoPlacement>();
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
            long hidden = 0L;

            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    long cellBit = bit(r, c);
                    if (visible[r][c] >= 0) {
                        clues.add(new Clue(cellBit, visible[r][c]));
                    } else {
                        allowed |= cellBit;
                        if (visible[r][c] == HIDDEN) {
                            hidden |= cellBit;
                        }
                        if (visible[r][c] == KNOWN_MINE) {
                            required |= cellBit;
                        }
                    }
                }
            }

            allowedMask = allowed;
            requiredMask = required;
            hiddenMask = hidden;
            placementsByCell = new ArrayList[SIZE * SIZE];
            for (int i = 0; i < placementsByCell.length; i++) {
                placementsByCell[i] = new ArrayList<Integer>();
            }
            buildPlacements();
        }

        void search(int start, int placed, long occupiedMask, long blockedMask, long ignoredRemainingRequiredMask) {
            searchAll(start, placed, occupiedMask, blockedMask);
        }

        boolean searchFirst(int start, int placed, long occupiedMask, long blockedMask, long ignoredRemainingRequiredMask) {
            return searchOne(start, placed, occupiedMask, blockedMask);
        }

        private void searchAll(int start, int placed, long occupiedMask, long blockedMask) {
            if (solutionCount > 0 && (mineSeenMask & hiddenMask) == hiddenMask) {
                return;
            }

            if (placed == DOMINO_COUNT) {
                if ((occupiedMask & requiredMask) != requiredMask) {
                    return;
                }
                if (!allCluesMatch(occupiedMask)) {
                    return;
                }
                solutionCount++;
                mineSeenMask |= occupiedMask;
                return;
            }

            int needed = DOMINO_COUNT - placed;
            if (placements.size() - start < needed) {
                return;
            }
            if (!cluesRemainPossible(occupiedMask, blockedMask)) {
                return;
            }

            long remainingRequiredMask = requiredMask & ~occupiedMask;
            if (remainingRequiredMask != 0L
                    && !requiredCellsStillCoverable(start, occupiedMask, blockedMask, remainingRequiredMask)) {
                return;
            }

            ArrayList<Integer> preferred = new ArrayList<Integer>();
            ArrayList<Integer> others = new ArrayList<Integer>();
            for (int i = start; i < placements.size(); i++) {
                DominoPlacement placement = placements.get(i);
                if (!isCompatible(placement, occupiedMask, blockedMask)) {
                    continue;
                }
                if ((placement.cellsMask & remainingRequiredMask) != 0L) {
                    preferred.add(Integer.valueOf(i));
                } else {
                    others.add(Integer.valueOf(i));
                }
            }

            searchPlacementList(preferred, placed, occupiedMask, blockedMask);
            if (solutionCount > 0 && (mineSeenMask & hiddenMask) == hiddenMask) {
                return;
            }
            searchPlacementList(others, placed, occupiedMask, blockedMask);
        }

        private void searchPlacementList(ArrayList<Integer> indexes, int placed, long occupiedMask, long blockedMask) {
            for (int j = 0; j < indexes.size(); j++) {
                int index = indexes.get(j).intValue();
                DominoPlacement placement = placements.get(index);
                long newOccupiedMask = occupiedMask | placement.cellsMask;
                long newBlockedMask = blockedMask | placement.connectionMask;

                long remainingRequiredMask = requiredMask & ~newOccupiedMask;
                if ((placement.connectionMask & remainingRequiredMask) != 0L) {
                    continue;
                }
                if (!cluesRemainPossible(newOccupiedMask, newBlockedMask)) {
                    continue;
                }

                searchAll(index + 1, placed + 1, newOccupiedMask, newBlockedMask);
                if (solutionCount > 0 && (mineSeenMask & hiddenMask) == hiddenMask) {
                    return;
                }
            }
        }

        private boolean searchOne(int start, int placed, long occupiedMask, long blockedMask) {
            if (placed == DOMINO_COUNT) {
                if ((occupiedMask & requiredMask) != requiredMask) {
                    return false;
                }
                if (!allCluesMatch(occupiedMask)) {
                    return false;
                }
                solutionMask = occupiedMask;
                return true;
            }

            int needed = DOMINO_COUNT - placed;
            if (placements.size() - start < needed) {
                return false;
            }
            if (!cluesRemainPossible(occupiedMask, blockedMask)) {
                return false;
            }

            long remainingRequiredMask = requiredMask & ~occupiedMask;
            if (remainingRequiredMask != 0L
                    && !requiredCellsStillCoverable(start, occupiedMask, blockedMask, remainingRequiredMask)) {
                return false;
            }

            ArrayList<Integer> preferred = new ArrayList<Integer>();
            ArrayList<Integer> others = new ArrayList<Integer>();
            for (int i = start; i < placements.size(); i++) {
                DominoPlacement placement = placements.get(i);
                if (!isCompatible(placement, occupiedMask, blockedMask)) {
                    continue;
                }
                if ((placement.cellsMask & remainingRequiredMask) != 0L) {
                    preferred.add(Integer.valueOf(i));
                } else {
                    others.add(Integer.valueOf(i));
                }
            }

            if (searchOnePlacementList(preferred, placed, occupiedMask, blockedMask)) {
                return true;
            }
            return searchOnePlacementList(others, placed, occupiedMask, blockedMask);
        }

        private boolean searchOnePlacementList(ArrayList<Integer> indexes, int placed, long occupiedMask, long blockedMask) {
            for (int j = 0; j < indexes.size(); j++) {
                int index = indexes.get(j).intValue();
                DominoPlacement placement = placements.get(index);
                long newOccupiedMask = occupiedMask | placement.cellsMask;
                long newBlockedMask = blockedMask | placement.connectionMask;

                long remainingRequiredMask = requiredMask & ~newOccupiedMask;
                if ((placement.connectionMask & remainingRequiredMask) != 0L) {
                    continue;
                }
                if (!cluesRemainPossible(newOccupiedMask, newBlockedMask)) {
                    continue;
                }
                if (searchOne(index + 1, placed + 1, newOccupiedMask, newBlockedMask)) {
                    return true;
                }
            }
            return false;
        }

        private boolean requiredCellsStillCoverable(int start, long occupiedMask, long blockedMask, long remainingRequiredMask) {
            long bits = remainingRequiredMask;
            while (bits != 0L) {
                long requiredCellBit = bits & -bits;
                int cellIndex = Long.numberOfTrailingZeros(requiredCellBit);
                boolean possible = false;
                ArrayList<Integer> options = placementsByCell[cellIndex];
                for (int i = 0; i < options.size(); i++) {
                    int placementIndex = options.get(i).intValue();
                    if (placementIndex < start) {
                        continue;
                    }
                    DominoPlacement placement = placements.get(placementIndex);
                    if (isCompatible(placement, occupiedMask, blockedMask)) {
                        long restRequired = remainingRequiredMask & ~placement.cellsMask;
                        if ((placement.connectionMask & restRequired) == 0L) {
                            possible = true;
                            break;
                        }
                    }
                }
                if (!possible) {
                    return false;
                }
                bits &= bits - 1;
            }
            return true;
        }

        private boolean isCompatible(DominoPlacement placement, long occupiedMask, long blockedMask) {
            if ((placement.cellsMask & allowedMask) != placement.cellsMask) {
                return false;
            }
            if ((placement.cellsMask & occupiedMask) != 0L) {
                return false;
            }
            if ((placement.cellsMask & blockedMask) != 0L) {
                return false;
            }
            return (placement.connectionMask & occupiedMask) == 0L;
        }

        private boolean cluesRemainPossible(long occupiedMask, long blockedMask) {
            for (int i = 0; i < clues.size(); i++) {
                Clue clue = clues.get(i);
                int current = Long.bitCount(occupiedMask & clue.adjacentMask);
                if (current > clue.number) {
                    return false;
                }
                long available = clue.adjacentMask & allowedMask & ~occupiedMask & ~blockedMask;
                if (current + Long.bitCount(available) < clue.number) {
                    return false;
                }
            }
            return true;
        }

        private boolean allCluesMatch(long occupiedMask) {
            for (int i = 0; i < clues.size(); i++) {
                Clue clue = clues.get(i);
                if (Long.bitCount(occupiedMask & clue.adjacentMask) != clue.number) {
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
            DominoPlacement placement = new DominoPlacement(cellsMask, buildDominoConnectionMask(r1, c1, r2, c2));
            int index = placements.size();
            placements.add(placement);
            placementsByCell[r1 * SIZE + c1].add(Integer.valueOf(index));
            placementsByCell[r2 * SIZE + c2].add(Integer.valueOf(index));
        }
    }

    private static final class DominoPlacement {
        final long cellsMask;
        final long connectionMask;

        DominoPlacement(long cellsMask, long connectionMask) {
            this.cellsMask = cellsMask;
            this.connectionMask = connectionMask;
        }
    }

    private static final class Clue {
        final long adjacentMask;
        final int number;

        Clue(long cellBit, int number) {
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

    private static long buildDominoConnectionMask(int r1, int c1, int r2, int c2) {
        long ownCells = bit(r1, c1) | bit(r2, c2);
        long mask = orthogonalNeighborMask(r1, c1) | orthogonalNeighborMask(r2, c2);
        return mask & ~ownCells;
    }

    private static long orthogonalNeighborMask(int r, int c) {
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
