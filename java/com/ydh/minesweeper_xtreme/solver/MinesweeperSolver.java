package com.ydh.minesweeper_xtreme.solver;

import com.ydh.minesweeper_xtreme.game.GameMode;

public final class MinesweeperSolver {
    public static final int HIDDEN = -1; // 表示隐藏的未知格子
    public static final int KNOWN_MINE = -3; // 表示已知是雷的格子（被跳过）
    public static final int SIZE = 7; // 棋盘大小7x7

    public static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1}; // 8个方向的行偏移
    public static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1}; // 8个方向的列偏移

    private MinesweeperSolver() {
        // 私有构造函数，防止实例化
    }

    // 查找当前局面下确定安全的格子
    public static Result findCertainSafe(GameMode mode, int[][] visible, boolean[][] flags) {
        if (mode == GameMode.TWO_G) {
            return solveTwoG(visible, flags); // 2G模式的求解
        }
        if (mode == GameMode.TWO_D) {
            return solveTwoD(visible, flags); // 2D模式的求解
        }
        if (mode == GameMode.TWO_H) {
            return solveTwoH(visible, flags); // 2H模式的求解
        }
        return solveNormal(visible, flags); // 普通模式的求解
    }

    // 寻找一个包含指定位置为雷的合法解，返回雷分布图
    public static boolean[][] findMineMapWithMine(GameMode mode, int[][] visible, int mineR, int mineC) {
        if (!inBounds(mineR, mineC) || visible[mineR][mineC] >= 0) {
            return null; // 位置无效或已揭示，无法作为雷
        }
        boolean[][] flags = new boolean[SIZE][SIZE];
        if (mode == GameMode.TWO_G) {
            TwoGState state = new TwoGState(visible, flags);
            return state.findSolutionWithMine(mineR, mineC); // 2G模式搜索含指定雷的解
        }
        if (mode == GameMode.TWO_D) {
            TwoDState state = new TwoDState(visible, flags, bit(mineR, mineC)); // 将指定位置设为必需雷
            if (state.searchFirst(0, 0, 0L, 0L, state.requiredMask)) {
                return maskToBoard(state.solutionMask); // 将位掩码转换为二维数组
            }
            return null;
        }
        if (mode == GameMode.TWO_H) {
            TwoHState state = new TwoHState(visible, flags);
            return state.findSolutionWithMine(mineR, mineC); // 2H模式搜索含指定雷的解
        }
        NormalState state = new NormalState(visible, flags);
        return state.findSolutionWithMine(mineR, mineC); // 普通模式搜索含指定雷的解
    }

    // 求解结果类，存储安全格子和解的数量
    public static final class Result {
        public final boolean[][] safe = new boolean[SIZE][SIZE]; // 标记哪些格子一定安全
        public int solutionCount; // 找到的合法解总数

        // 检查是否存在确定安全的格子
        public boolean hasCertainSafe() {
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

    // 普通模式求解：枚举所有变量赋值，找出所冇合法解
    private static Result solveNormal(int[][] visible, boolean[][] flags) {
        NormalState state = new NormalState(visible, flags);
        state.recurse(0); // 从第0个变量开始递归搜索
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result; // 无解，直接返回
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (state.isUnknown(r, c)) {
                    int index = state.variableIndex[r][c];
                    if (index >= 0) {
                        result.safe[r][c] = !state.mineSeen[index]; // 如果从未在任何解中是雷，则安全
                    } else {
                        result.safe[r][c] = !state.outsideCanContainMine; // 外部区域不可能有雷则安全
                    }
                }
            }
        }
        return result;
    }


    // 2G模式求解：枚举所有2x2方块组合，找出所有合法解
    private static Result solveTwoG(int[][] visible, boolean[][] flags) {
        TwoGState state = new TwoGState(visible, flags);
        state.recurse(0, 0); // 从第0个方块位置开始，已放置0个方块
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result; // 无解，直接返回
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == HIDDEN) {
                    result.safe[r][c] = !state.mineSeen[r][c]; // 如果从未在任何解中是雷，则安全
                }
            }
        }
        return result;
    }

    // 2D模式求解：枚举所有骨牌组合，找出所有合法解
    private static Result solveTwoD(int[][] visible, boolean[][] flags) {
        TwoDState state = new TwoDState(visible, flags);
        state.search(0, 0, 0L, 0L, state.requiredMask); // 从第0个骨牌开始搜索
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result; // 无解，直接返回
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == HIDDEN) {
                    long bit = bit(r, c);
                    result.safe[r][c] = (state.mineSeenMask & bit) == 0L; // 如果从未在任何解中出现过雷，则安全
                }
            }
        }
        return result;
    }


    // 2H模式求解：逐行枚举合法的雷排列，找出所有合法解
    private static Result solveTwoH(int[][] visible, boolean[][] flags) {
        TwoHState state = new TwoHState(visible, flags);
        state.recurse(0, 0); // 从第0行开始，已放置0颗雷
        Result result = new Result();
        result.solutionCount = state.solutionCount;
        if (state.solutionCount == 0) {
            return result; // 无解，直接返回
        }
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (visible[r][c] == HIDDEN) {
                    result.safe[r][c] = !state.mineSeen[r][c]; // 如果从未在任何解中是雷，则安全
                }
            }
        }
        return result;
    }


    // 将二维坐标转换为位掩码中的对应位
    public static long bit(int r, int c) {
        return 1L << (r * SIZE + c);
    }

    // 将位掩码转换为二维布尔数组
    public static boolean[][] maskToBoard(long mask) {
        boolean[][] board = new boolean[SIZE][SIZE];
        for (int idx = 0; idx < SIZE * SIZE; idx++) {
            if (((mask >> idx) & 1L) == 1L) {
                board[idx / SIZE][idx % SIZE] = true; // 该位为1则对应格子为true
            }
        }
        return board;
    }

    // 构建某个格子周围8个邻居的位掩码
    public static long buildAdjacentMask(long cellBit) {
        int index = Long.numberOfTrailingZeros(cellBit); // 从位掩码提取索引
        int r = index / SIZE;
        int c = index % SIZE;
        long mask = 0L;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue; // 跳过自身
                }
                int nr = r + dr;
                int nc = c + dc;
                if (inBounds(nr, nc)) {
                    mask |= bit(nr, nc); // 将邻居位置加入掩码
                }
            }
        }
        return mask;
    }

    // 构建骨牌的连接掩码（与骨牌八方向接触但不包括骨牌自身的格子）
    public static long buildDominoConnectionMask(int r1, int c1, int r2, int c2) {
        long ownCells = bit(r1, c1) | bit(r2, c2); // 骨牌占用的两个格子
        long mask = orthogonalNeighborMask(r1, c1) | orthogonalNeighborMask(r2, c2); // 两个端点的正交邻居
        return mask & ~ownCells; // 排除骨牌自身
    }

    // 构建某个格子的正交邻居（上下左右）位掩码
    public static long orthogonalNeighborMask(int r, int c) {
        long mask = 0L;
        if (r > 0) {
            mask |= bit(r - 1, c); // 上方邻居
        }
        if (r < SIZE - 1) {
            mask |= bit(r + 1, c); // 下方邻居
        }
        if (c > 0) {
            mask |= bit(r, c - 1); // 左方邻居
        }
        if (c < SIZE - 1) {
            mask |= bit(r, c + 1); // 右方邻居
        }
        return mask;
    }

    // 检查一个7位的mask是否符合2H规则（没有孤立的雷，每颗雷左右至少有一个相邻雷）
    public static boolean isLegalTwoHRow(int mask) {
        for (int c = 0; c < SIZE; c++) {
            if (((mask >> c) & 1) == 0) {
                continue; // 该位置不是雷，跳过
            }
            boolean left = c > 0 && ((mask >> (c - 1)) & 1) == 1; // 左边是否有雷
            boolean right = c < SIZE - 1 && ((mask >> (c + 1)) & 1) == 1; // 右边是否有雷
            if (!left && !right) {
                return false; // 左右都没有雷，是孤立雷，不合法
            }
        }
        return true;
    }

    // 计算某个格子周围8个方向的雷数
    public static int countAdjacentMines(boolean[][] mines, int r, int c) {
        int count = 0;
        for (int i = 0; i < DR.length; i++) {
            int nr = r + DR[i];
            int nc = c + DC[i];
            if (inBounds(nr, nc) && mines[nr][nc]) {
                count++; // 邻居是雷则计数加1
            }
        }
        return count;
    }

    // 检查坐标是否在棋盘范围内
    public static boolean inBounds(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }

}
