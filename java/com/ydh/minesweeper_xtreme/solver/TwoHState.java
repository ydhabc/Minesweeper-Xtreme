package com.ydh.minesweeper_xtreme.solver;

import com.ydh.minesweeper_xtreme.game.GameMode;

import java.util.ArrayList;
import java.util.List;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.*;

final class TwoHState {
    final int[][] visible; // 可见棋盘
    final boolean[][] flags; // 旗标标记
    final List<Integer>[] rowMasks; // 每行所有合法的雷排列mask列表
    final int[] selected = new int[SIZE]; // 当前搜索路径中每行选择的mask
    final int[] minSuffix = new int[SIZE + 1]; // 后缀最小雷数：从第r行到最后一行至少需要的雷数
    final int[] maxSuffix = new int[SIZE + 1]; // 后缀最大雷数：从第r行到最后一行最多能放的雷数
    final boolean[][] mineSeen = new boolean[SIZE][SIZE]; // 记录哪些位置在某个解中是雷
    int solutionCount; // 找到的合法解总数

    @SuppressWarnings("unchecked")
    // 构造函数：预计算每行合法mask和后缀极值
    TwoHState(int[][] visible, boolean[][] flags) {
        this.visible = visible;
        this.flags = flags;
        rowMasks = new List[SIZE];
        for (int r = 0; r < SIZE; r++) {
            rowMasks[r] = new ArrayList<Integer>();
            for (int mask = 0; mask < 128; mask++) {
                // 遍历所有7位mask（0-127），筛选合法的
                if (isLegalTwoHRow(mask) && rowMaskMatchesKnown(r, mask)) {
                    rowMasks[r].add(mask); // 符合2H规则且与已知格子不冲突的mask
                }
            }
        }
        for (int r = SIZE - 1; r >= 0; r--) {
            // 从最后一行向前计算后缀极值
            int min = 8;
            int max = -1;
            for (int i = 0; i < rowMasks[r].size(); i++) {
                int count = Integer.bitCount(rowMasks[r].get(i).intValue()); // 统计mask中的雷数
                if (count < min) {
                    min = count; // 更新该行最小雷数
                }
                if (count > max) {
                    max = count; // 更新该行最大雷数
                }
            }
            if (max < 0) {
                min = 8;
                max = -1; // 该行无合法mask时的默认值
            }
            minSuffix[r] = minSuffix[r + 1] + min; // 累加后缀最小雷数
            maxSuffix[r] = maxSuffix[r + 1] + max; // 累加后缀最大雷数
        }
    }

    // 核心递归搜索函数：逐行枚举合法mask，找出所有合法解
    void recurse(int row, int mines) {
        if (mines + minSuffix[row] > GameMode.TWO_H.mines) {
            return; // 剪枝：当前雷数加上后续最少雷数超过总雷数
        }
        if (mines + maxSuffix[row] < GameMode.TWO_H.mines) {
            return; // 剪枝：当前雷数加上后续最多雷数仍不足总雷数
        }
        if (row == SIZE) {
            // 基准情况：所有行都已处理
            if (mines == GameMode.TWO_H.mines && allNumbersMatch()) {
                solutionCount++; // 找到一个合法解
                for (int r = 0; r < SIZE; r++) {
                    for (int c = 0; c < SIZE; c++) {
                        if (hasMine(r, c)) {
                            mineSeen[r][c] = true; // 记录雷位置
                        }
                    }
                }
            }
            return;
        }
        for (int i = 0; i < rowMasks[row].size(); i++) {
            // 遍历当前行的所有合法mask
            int mask = rowMasks[row].get(i).intValue();
            selected[row] = mask; // 选择该mask
            int nextMines = mines + Integer.bitCount(mask); // 计算新的雷数
            if (row >= 2 && !numbersInRowMatch(row - 1)) {
                continue; // 延迟验证：检查上一行的数字约束是否满足
            }
            recurse(row + 1, nextMines); // 递归处理下一行
        }
    }

    // 检查指定行的mask是否与已知格子一致（不与数字格子冲突，包含所有已知雷）
    boolean rowMaskMatchesKnown(int r, int mask) {
        for (int c = 0; c < SIZE; c++) {
            boolean mine = ((mask >> c) & 1) == 1; // 提取第c位是否为雷
            if (visible[r][c] >= 0 && mine) {
                return false; // 数字格子位置不能有雷
            }
            if (visible[r][c] == KNOWN_MINE && !mine) {
                return false; // 已知雷位置必须有雷
            }
        }
        return true;
    }

    // 验证所有行的数字约束是否满足
    boolean allNumbersMatch() {
        for (int r = 0; r < SIZE; r++) {
            if (!numbersInRowMatch(r)) {
                return false;
            }
        }
        return true;
    }

    // 验证指定行的所有数字格子的相邻雷数是否与提示一致
    boolean numbersInRowMatch(int r) {
        for (int c = 0; c < SIZE; c++) {
            if (visible[r][c] >= 0 && countAdjacentMines(r, c) != visible[r][c]) {
                return false; // 数字约束不满足
            }
        }
        return true;
    }

    // 计算指定位置周围8个方向的雷数（基于selected数组）
    int countAdjacentMines(int r, int c) {
        int count = 0;
        for (int i = 0; i < DR.length; i++) {
            int nr = r + DR[i];
            int nc = c + DC[i];
            if (inBounds(nr, nc) && hasMine(nr, nc)) {
                count++; // 邻居是雷则计数加1
            }
        }
        return count;
    }

    // 检查指定位置是否有雷（从selected数组中提取）
    boolean hasMine(int r, int c) {
        return ((selected[r] >> c) & 1) == 1; // 检查第r行mask的第c位
    }

    // 寻找一个包含指定位置为雷的合法解
    boolean[][] findSolutionWithMine(int mineR, int mineC) {
        boolean[][] solution = new boolean[SIZE][SIZE];
        return searchSolution(0, 0, mineR, mineC, solution) ? solution : null;
    }

    // 递归搜索含指定雷的第一个解
    private boolean searchSolution(int row, int mines, int mineR, int mineC, boolean[][] solution) {
        if (mines + minSuffix[row] > GameMode.TWO_H.mines) {
            return false; // 剪枝：雷数超标
        }
        if (mines + maxSuffix[row] < GameMode.TWO_H.mines) {
            return false; // 剪枝：雷数不足
        }
        if (row == SIZE) {
            // 基准情况：所有行已处理
            if (mines == GameMode.TWO_H.mines && hasMine(mineR, mineC) && allNumbersMatch()) {
                copySelected(solution); // 复制当前选择到解
                return true;
            }
            return false;
        }
        for (int i = 0; i < rowMasks[row].size(); i++) {
            int mask = rowMasks[row].get(i).intValue();
            selected[row] = mask; // 选择该mask
            int nextMines = mines + Integer.bitCount(mask);
            if (row >= 2 && !numbersInRowMatch(row - 1)) {
                continue; // 延迟验证：检查上一行数字约束
            }
            if (searchSolution(row + 1, nextMines, mineR, mineC, solution)) {
                return true; // 找到解立即返回
            }
        }
        return false;
    }

    // 复制当前选择的雷分布到解数组
    private void copySelected(boolean[][] solution) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                solution[r][c] = hasMine(r, c);
            }
        }
    }
}
