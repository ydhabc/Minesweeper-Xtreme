package com.ydh.minesweeper_xtreme.solver;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.*;

final class TwoGState {
    final int[][] visible; // 可见棋盘
    final boolean[][] flags; // 旗标标记
    final boolean[][] mines = new boolean[SIZE][SIZE]; // 当前搜索路径中的雷分布
    final boolean[][] mineSeen = new boolean[SIZE][SIZE]; // 记录哪些位置在某个解中是雷
    int solutionCount; // 找到的合法解总数

    // 构造函数：初始化状态
    TwoGState(int[][] visible, boolean[][] flags) {
        this.visible = visible;
        this.flags = flags;
    }

    // 核心递归搜索函数：枚举所有2x2方块组合，找出所有合法解
    void recurse(int start, int blocks) {
        if (blocks == 3) {
            // 基准情况：已放置3个方块（12颗雷）
            if (!matchesKnownCells() || !matchesNumbers()) {
                return; // 验证失败，剪枝
            }
            solutionCount++; // 找到一个合法解
            for (int r = 0; r < SIZE; r++) {
                for (int c = 0; c < SIZE; c++) {
                    if (mines[r][c]) {
                        mineSeen[r][c] = true; // 记录雷位置
                    }
                }
            }
            return;
        }
        int remaining = 3 - blocks; // 计算还需放置的方块数
        for (int p = start; p <= 36 - remaining; p++) {
            // 遍历所有可能的方块位置（6x6=36个左上角位置）
            int r = p / 6;
            int c = p % 6;
            if (!canPlaceBlock(r, c)) {
                continue; // 剪枝：该位置无法放置方块
            }
            setBlock(r, c, true); // 放置方块
            recurse(p + 1, blocks + 1); // 递归搜索下一个方块
            setBlock(r, c, false); // 回溯：移除方块
        }
    }

    // 检查指定位置是否可以放置2x2方块（不与已有雷或数字格子重叠）
    boolean canPlaceBlock(int r, int c) {
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (mines[nr][nc] || visible[nr][nc] >= 0) {
                    return false; // 方块的某个格子已有雷或是数字格子，不能放置
                }
            }
        }
        return true;
    }

    // 设置或取消指定位置的2x2方块
    void setBlock(int r, int c, boolean value) {
        for (int dr = 0; dr < 2; dr++) {
            for (int dc = 0; dc < 2; dc++) {
                mines[r + dr][c + dc] = value; // 将4个格子都设为value
            }
        }
    }

    // 验证当前雷分布是否与已知雷一致
    boolean matchesKnownCells() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == KNOWN_MINE && !mines[r][c]) {
                    return false; // 已知雷位置当前没有雷，不匹配
                }
            }
        }
        return true;
    }

    // 验证当前雷分布是否满足所有数字格子的约束
    boolean matchesNumbers() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] >= 0 && countAdjacentMines(mines, r, c) != visible[r][c]) {
                    return false; // 数字格子的相邻雷数与提示不符
                }
            }
        }
        return true;
    }

    // 寻找一个包含指定位置为雷的合法解
    boolean[][] findSolutionWithMine(int mineR, int mineC) {
        boolean[][] solution = new boolean[SIZE][SIZE];
        return searchSolution(0, 0, mineR, mineC, solution) ? solution : null;
    }

    // 递归搜索含指定雷的第一个解
    private boolean searchSolution(int start, int blocks, int mineR, int mineC, boolean[][] solution) {
        if (blocks == 3) {
            // 基准情况：已放置3个方块
            if (!mines[mineR][mineC] || !matchesKnownCells() || !matchesNumbers()) {
                return false; // 验证：指定位置必须是雷，且满足所有约束
            }
            copyMines(solution); // 复制当前雷分布到解
            return true;
        }
        int remaining = 3 - blocks;
        for (int p = start; p <= 36 - remaining; p++) {
            int r = p / 6;
            int c = p % 6;
            if (!canPlaceBlock(r, c)) {
                continue; // 剪枝：无法放置
            }
            setBlock(r, c, true); // 放置方块
            if (searchSolution(p + 1, blocks + 1, mineR, mineC, solution)) {
                setBlock(r, c, false);
                return true; // 找到解立即返回
            }
            setBlock(r, c, false); // 回溯：移除方块
        }
        return false;
    }

    // 复制当前雷分布到解数组
    private void copyMines(boolean[][] solution) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                solution[r][c] = mines[r][c];
            }
        }
    }
}
