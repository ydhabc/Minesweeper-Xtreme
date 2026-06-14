package com.ydh.minesweeper_xtreme.solver;

// 骨牌放置类，表示一个可能的骨牌放置位置及其约束
final class DominoPlacement {
    final long cellsMask; // 骨牌占用的两个格子的位掩码
    final long connectionMask; // 与骨牌八方向接触的格子位掩码（其他骨牌不能放在这些位置）

    DominoPlacement(long cellsMask, long connectionMask) {
        this.cellsMask = cellsMask;
        this.connectionMask = connectionMask;
    }
}
