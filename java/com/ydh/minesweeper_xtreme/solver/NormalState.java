package com.ydh.minesweeper_xtreme.solver;

import com.ydh.minesweeper_xtreme.game.GameMode;

import java.util.ArrayList;
import java.util.List;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.*;

final class NormalState {
    final int[][] visible; // 可见棋盘，-1表示隐藏，>=0表示数字，-3表示已知雷
    final boolean[][] flags; // 旗标标记
    final int[][] variableIndex = new int[SIZE][SIZE]; // 每个格子的变量索引，-1表示不是变量
    final List<Cell> variables = new ArrayList<Cell>(); // 受数字约束的未知格子列表
    final List<Cell> numbers = new ArrayList<Cell>(); // 已揭示的数字格子列表
    final boolean[] assigned = new boolean[SIZE * SIZE]; // 标记变量是否已赋值
    final boolean[] value = new boolean[SIZE * SIZE]; // 记录变量的值（true为雷，false为安全）
    boolean[] mineSeen = new boolean[SIZE * SIZE]; // 记录变量是否在某个解中是雷
    int knownMineCount; // 已知雷的数量（被跳过的雷）
    int outsideCount; // 外部区域格子数（不接触任何数字的隐藏格子）
    int solutionCount; // 找到的合法解总数
    boolean outsideCanContainMine; // 外部区域是否可能包含雷

    // 构造函数：初始化状态，识别变量和数字格子
    NormalState(int[][] visible, boolean[][] flags) {
        this.visible = visible;
        this.flags = flags;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                variableIndex[r][c] = -1; // 初始化为非变量
                if (isKnownMine(r, c)) {
                    knownMineCount++; // 统计已知雷数
                }
                if (visible[r][c] >= 0) {
                    numbers.add(new Cell(r, c)); // 收集数字格子
                }
            }
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!isUnknown(r, c)) {
                    continue; // 跳过已揭示的格子
                }
                if (touchesNumber(r, c)) {
                    variableIndex[r][c] = variables.size(); // 分配变量索引
                    variables.add(new Cell(r, c)); // 加入变量列表
                } else {
                    outsideCount++; // 统计外部区域格子数
                }
            }
        }
        mineSeen = new boolean[variables.size()]; // 根据变量数量初始化mineSeen数组
    }

    // 检查指定位置是否是已知雷
    boolean isKnownMine(int r, int c) {
        return visible[r][c] == KNOWN_MINE;
    }

    // 检查指定位置是否是隐藏的未知格子
    boolean isUnknown(int r, int c) {
        return visible[r][c] == HIDDEN;
    }

    // 检查指定位置是否接触任何数字格子（8方向）
    boolean touchesNumber(int r, int c) {
        for (int i = 0; i < DR.length; i++) {
            int nr = r + DR[i];
            int nc = c + DC[i];
            if (inBounds(nr, nc) && visible[nr][nc] >= 0) {
                return true; // 邻居中有数字格子
            }
        }
        return false;
    }

    // 核心递归搜索函数：枚举所有变量的真假赋值，找出所有合法解
    void recurse(int k) {
        if (!partiallyConsistent()) {
            return; // 剪枝：当前部分赋值不一致
        }
        if (k == variables.size()) {
            // 基准情况：所有变量都已赋值
            int mines = knownMineCount;
            for (int i = 0; i < variables.size(); i++) {
                if (value[i]) {
                    mines++; // 统计总雷数
                }
            }
            int outsideMines = GameMode.NORMAL.mines - mines;
            if (outsideMines < 0 || outsideMines > outsideCount) {
                return; // 剪枝：外部区域无法容纳剩余雷数
            }
            solutionCount++; // 找到一个合法解
            if (outsideMines > 0) {
                outsideCanContainMine = true; // 标记外部区域可能有雷
            }
            for (int i = 0; i < variables.size(); i++) {
                if (value[i]) {
                    mineSeen[i] = true; // 记录该变量在某个解中是雷
                }
            }
            return;
        }

        // 递归展开：尝试当前变量为雷
        assigned[k] = true;
        value[k] = true;
        recurse(k + 1);
        // 回溯：尝试当前变量不为雷
        value[k] = false;
        recurse(k + 1);
        assigned[k] = false; // 清理赋值状态
    }

    // 局部一致性检查：验证当前部分赋值是否与数字约束和全局雷数冲突
    boolean partiallyConsistent() {
        for (int n = 0; n < numbers.size(); n++) {
            Cell cell = numbers.get(n);
            int mines = 0; // 已确定的雷数
            int unknown = 0; // 未赋值的未知格子数
            for (int i = 0; i < DR.length; i++) {
                int nr = cell.r + DR[i];
                int nc = cell.c + DC[i];
                if (!inBounds(nr, nc)) {
                    continue; // 跳过越界邻居
                }
                if (isKnownMine(nr, nc)) {
                    mines++; // 已知雷计入
                } else {
                    int index = variableIndex[nr][nc];
                    if (index >= 0) {
                        if (assigned[index]) {
                            if (value[index]) {
                                mines++; // 已赋值为雷的变量计入
                            }
                        } else {
                            unknown++; // 未赋值的变量计入unknown
                        }
                    }
                }
            }
            int number = visible[cell.r][cell.c];
            if (mines > number || mines + unknown < number) {
                return false; // 剪枝：数字约束不满足
            }
        }
        int assignedMines = knownMineCount;
        for (int i = 0; i < variables.size(); i++) {
            if (assigned[i] && value[i]) {
                assignedMines++; // 统计已赋值且为雷的变量数
            }
        }
        return assignedMines <= GameMode.NORMAL.mines; // 全局雷数约束检查
    }

    // 寻找一个包含指定位置为雷的合法解
    boolean[][] findSolutionWithMine(int mineR, int mineC) {
        boolean[][] solution = new boolean[SIZE][SIZE];
        return searchSolution(0, mineR, mineC, solution) ? solution : null;
    }

    // 递归搜索含指定雷的第一个解
    private boolean searchSolution(int k, int mineR, int mineC, boolean[][] solution) {
        if (!partiallyConsistent()) {
            return false; // 剪枝：当前赋值不一致
        }
        int requiredIndex = variableIndex[mineR][mineC];
        if (requiredIndex >= 0 && assigned[requiredIndex] && !value[requiredIndex]) {
            return false; // 剪枝：指定位置已被赋值为非雷
        }
        if (k == variables.size()) {
            // 基准情况：所有变量已赋值
            int mines = knownMineCount;
            for (int i = 0; i < variables.size(); i++) {
                if (value[i]) {
                    mines++;
                }
            }
            int outsideMines = GameMode.NORMAL.mines - mines;
            if (outsideMines < 0 || outsideMines > outsideCount) {
                return false; // 剪枝：外部区域雷数不合法
            }
            if (requiredIndex >= 0) {
                if (!value[requiredIndex]) {
                    return false; // 验证：指定变量必须是雷
                }
            } else if (isUnknown(mineR, mineC)) {
                if (outsideMines <= 0) {
                    return false; // 验证：指定外部位置必须有雷可放
                }
            } else if (!isKnownMine(mineR, mineC)) {
                return false; // 验证：指定位置必须是已知雷
            }
            buildSolution(solution, mineR, mineC, outsideMines); // 构建解
            return true;
        }

        // 递归展开：先尝试为雷
        assigned[k] = true;
        value[k] = true;
        if (searchSolution(k + 1, mineR, mineC, solution)) {
            assigned[k] = false;
            return true; // 找到解立即返回
        }
        // 回溯：尝试不为雷
        value[k] = false;
        if (searchSolution(k + 1, mineR, mineC, solution)) {
            assigned[k] = false;
            return true; // 找到解立即返回
        }
        assigned[k] = false;
        return false;
    }

    // 构建包含指定雷的完整解（包括外部区域的雷分配）
    private void buildSolution(boolean[][] solution, int mineR, int mineC, int outsideMines) {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                solution[r][c] = isKnownMine(r, c); // 复制已知雷
            }
        }
        for (int i = 0; i < variables.size(); i++) {
            Cell cell = variables.get(i);
            solution[cell.r][cell.c] = value[i]; // 复制变量赋值
        }
        int requiredIndex = variableIndex[mineR][mineC];
        if (requiredIndex < 0 && isUnknown(mineR, mineC)) {
            solution[mineR][mineC] = true; // 指定外部位置设为雷
            outsideMines--;
        }
        for (int r = 0; r < SIZE && outsideMines > 0; r++) {
            for (int c = 0; c < SIZE && outsideMines > 0; c++) {
                if (visible[r][c] == HIDDEN && variableIndex[r][c] < 0 && !solution[r][c]) {
                    solution[r][c] = true; // 填充外部区域的雷
                    outsideMines--;
                }
            }
        }
    }
}
