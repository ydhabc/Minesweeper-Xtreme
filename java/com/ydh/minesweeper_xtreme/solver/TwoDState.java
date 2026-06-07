package com.ydh.minesweeper_xtreme.solver;

import com.ydh.minesweeper_xtreme.game.GameMode;

import java.util.ArrayList;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.*;

final class TwoDState {
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
