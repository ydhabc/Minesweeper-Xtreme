package com.ydh.minesweeper_xtreme.solver;

final class DominoPlacement {
    final long cellsMask;
    final long connectionMask;

    DominoPlacement(long cellsMask, long connectionMask) {
        this.cellsMask = cellsMask;
        this.connectionMask = connectionMask;
    }
}
