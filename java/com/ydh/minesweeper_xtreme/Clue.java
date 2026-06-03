package com.ydh.minesweeper_xtreme;

import static com.ydh.minesweeper_xtreme.MinesweeperSolver.buildAdjacentMask;

final class Clue {
    final long adjacentMask;
    final int number;

    Clue(long cellBit, int number) {
        this.adjacentMask = buildAdjacentMask(cellBit);
        this.number = number;
    }
}
