package com.ydh.minesweeper_xtreme.solver;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.buildAdjacentMask;

// 线索类，表示一个数字格子的约束信息
final class Clue {
    final long adjacentMask; // 该数字格子周围8个邻居的位掩码
    final int number; // 数字提示（周围雷的数量）

    Clue(long cellBit, int number) {
        this.adjacentMask = buildAdjacentMask(cellBit); // 根据格子位置计算相邻掩码
        this.number = number;
    }
}
