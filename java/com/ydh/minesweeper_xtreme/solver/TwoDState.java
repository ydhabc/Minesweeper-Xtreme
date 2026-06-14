package com.ydh.minesweeper_xtreme.solver;

import com.ydh.minesweeper_xtreme.game.GameMode;

import java.util.ArrayList;

import static com.ydh.minesweeper_xtreme.solver.MinesweeperSolver.*;

final class TwoDState {
    private static final int DOMINO_COUNT = GameMode.TWO_D.mines / 2; // 需要放置的骨牌数量（6个）

    final int[][] visible; // 可见棋盘
    final boolean[][] flags; // 旗标标记
    final long allowedMask; // 允许放置骨牌的格子位掩码
    final long requiredMask; // 必须覆盖的雷位置位掩码（如已知雷）
    final long hiddenMask; // 所有隐藏格子的位掩码
    final ArrayList<DominoPlacement> placements = new ArrayList<DominoPlacement>(); // 所有可能的骨牌放置位置列表
    final ArrayList<Integer>[] placementsByCell; // 反向索引：每个格子被哪些骨牌覆盖
    final ArrayList<Clue> clues = new ArrayList<Clue>(); // 数字线索列表
    long mineSeenMask; // 记录在所有解中出现过雷的位置位掩码
    long solutionMask; // 存储找到的第一个解的位掩码
    int solutionCount; // 找到的合法解总数

    @SuppressWarnings("unchecked")
    // 构造函数（无额外必需雷）
    TwoDState(int[][] visible, boolean[][] flags) {
        this(visible, flags, 0L);
    }

    @SuppressWarnings("unchecked")
    // 构造函数：初始化状态，提取线索和构建骨牌列表
    TwoDState(int[][] visible, boolean[][] flags, long extraRequiredMask) {
        this.visible = visible;
        this.flags = flags;
        long allowed = 0L;
        long required = extraRequiredMask; // 初始化为额外必需的雷
        long hidden = 0L;

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                long cellBit = bit(r, c);
                if (visible[r][c] >= 0) {
                    clues.add(new Clue(cellBit, visible[r][c])); // 收集数字线索
                } else {
                    allowed |= cellBit; // 隐藏格子加入允许区域
                    if (visible[r][c] == HIDDEN) {
                        hidden |= cellBit; // 统计隐藏格子
                    }
                    if (visible[r][c] == KNOWN_MINE) {
                        required |= cellBit; // 已知雷加入必需区域
                    }
                }
            }
        }

        allowedMask = allowed;
        requiredMask = required;
        hiddenMask = hidden;
        placementsByCell = new ArrayList[SIZE * SIZE];
        for (int i = 0; i < placementsByCell.length; i++) {
            placementsByCell[i] = new ArrayList<Integer>(); // 初始化反向索引
        }
        buildPlacements(); // 构建所有可能的骨牌放置位置
    }

    // 搜索所有合法解的入口函数
    void search(int start, int placed, long occupiedMask, long blockedMask, long ignoredRemainingRequiredMask) {
        searchAll(start, placed, occupiedMask, blockedMask);
    }

    // 搜索第一个合法解的入口函数
    boolean searchFirst(int start, int placed, long occupiedMask, long blockedMask, long ignoredRemainingRequiredMask) {
        return searchOne(start, placed, occupiedMask, blockedMask);
    }

    // 递归搜索所有合法解
    private void searchAll(int start, int placed, long occupiedMask, long blockedMask) {
        if (solutionCount > 0 && (mineSeenMask & hiddenMask) == hiddenMask) {
            return; // 早期终止：已找到足够多的解且所有隐藏格子都已确定为雷
        }

        if (placed == DOMINO_COUNT) {
            // 基准情况：已放置6个骨牌
            if ((occupiedMask & requiredMask) != requiredMask) {
                return; // 验证：必需雷未全部覆盖
            }
            if (!allCluesMatch(occupiedMask)) {
                return; // 验证：线索约束不满足
            }
            solutionCount++; // 找到一个合法解
            mineSeenMask |= occupiedMask; // 记录雷位置
            return;
        }

        int needed = DOMINO_COUNT - placed; // 计算还需放置的骨牌数
        if (placements.size() - start < needed) {
            return; // 剪枝：剩余可选骨牌不足
        }
        if (!cluesRemainPossible(occupiedMask, blockedMask)) {
            return; // 剪枝：线索约束无法满足
        }

        long remainingRequiredMask = requiredMask & ~occupiedMask; // 计算剩余必需雷
        if (remainingRequiredMask != 0L
                && !requiredCellsStillCoverable(start, occupiedMask, blockedMask, remainingRequiredMask)) {
            return; // 剪枝：剩余必需雷无法被覆盖
        }

        // 将可选骨牌分为两类：优先选择能覆盖必需雷的
        ArrayList<Integer> preferred = new ArrayList<Integer>();
        ArrayList<Integer> others = new ArrayList<Integer>();
        for (int i = start; i < placements.size(); i++) {
            DominoPlacement placement = placements.get(i);
            if (!isCompatible(placement, occupiedMask, blockedMask)) {
                continue; // 跳过不兼容的骨牌
            }
            if ((placement.cellsMask & remainingRequiredMask) != 0L) {
                preferred.add(Integer.valueOf(i)); // 能覆盖必需雷的骨牌
            } else {
                others.add(Integer.valueOf(i)); // 其他骨牌
            }
        }

        searchPlacementList(preferred, placed, occupiedMask, blockedMask); // 先搜索优选列表
        if (solutionCount > 0 && (mineSeenMask & hiddenMask) == hiddenMask) {
            return; // 早期终止检查
        }
        searchPlacementList(others, placed, occupiedMask, blockedMask); // 再搜索其他列表
    }

    // 遍历骨牌列表并递归搜索
    private void searchPlacementList(ArrayList<Integer> indexes, int placed, long occupiedMask, long blockedMask) {
        for (int j = 0; j < indexes.size(); j++) {
            int index = indexes.get(j).intValue();
            DominoPlacement placement = placements.get(index);
            long newOccupiedMask = occupiedMask | placement.cellsMask; // 更新占用掩码
            long newBlockedMask = blockedMask | placement.connectionMask; // 更新禁止掩码

            long remainingRequiredMask = requiredMask & ~newOccupiedMask;
            if ((placement.connectionMask & remainingRequiredMask) != 0L) {
                continue; // 剪枝：骨牌的连接区域会阻断剩余必需雷
            }
            if (!cluesRemainPossible(newOccupiedMask, newBlockedMask)) {
                continue; // 剪枝：放置后线索约束无法满足
            }

            searchAll(index + 1, placed + 1, newOccupiedMask, newBlockedMask); // 递归搜索
            if (solutionCount > 0 && (mineSeenMask & hiddenMask) == hiddenMask) {
                return; // 早期终止
            }
        }
    }

    // 递归搜索第一个合法解
    private boolean searchOne(int start, int placed, long occupiedMask, long blockedMask) {
        if (placed == DOMINO_COUNT) {
            // 基准情况：已放置6个骨牌
            if ((occupiedMask & requiredMask) != requiredMask) {
                return false; // 验证：必需雷未全部覆盖
            }
            if (!allCluesMatch(occupiedMask)) {
                return false; // 验证：线索约束不满足
            }
            solutionMask = occupiedMask; // 保存解
            return true;
        }

        int needed = DOMINO_COUNT - placed;
        if (placements.size() - start < needed) {
            return false; // 剪枝：剩余骨牌不足
        }
        if (!cluesRemainPossible(occupiedMask, blockedMask)) {
            return false; // 剪枝：线索无法满足
        }

        long remainingRequiredMask = requiredMask & ~occupiedMask;
        if (remainingRequiredMask != 0L
                && !requiredCellsStillCoverable(start, occupiedMask, blockedMask, remainingRequiredMask)) {
            return false; // 剪枝：必需雷无法覆盖
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
            return true; // 在优选列表中找到解
        }
        return searchOnePlacementList(others, placed, occupiedMask, blockedMask); // 在其他列表中查找
    }

    // 遍历骨牌列表搜索第一个解
    private boolean searchOnePlacementList(ArrayList<Integer> indexes, int placed, long occupiedMask, long blockedMask) {
        for (int j = 0; j < indexes.size(); j++) {
            int index = indexes.get(j).intValue();
            DominoPlacement placement = placements.get(index);
            long newOccupiedMask = occupiedMask | placement.cellsMask;
            long newBlockedMask = blockedMask | placement.connectionMask;

            long remainingRequiredMask = requiredMask & ~newOccupiedMask;
            if ((placement.connectionMask & remainingRequiredMask) != 0L) {
                continue; // 剪枝：连接区域阻断必需雷
            }
            if (!cluesRemainPossible(newOccupiedMask, newBlockedMask)) {
                continue; // 剪枝：线索无法满足
            }
            if (searchOne(index + 1, placed + 1, newOccupiedMask, newBlockedMask)) {
                return true; // 找到解立即返回
            }
        }
        return false;
    }

    // 检查剩余必需雷是否仍可被覆盖（前瞻性剪枝）
    private boolean requiredCellsStillCoverable(int start, long occupiedMask, long blockedMask, long remainingRequiredMask) {
        long bits = remainingRequiredMask;
        while (bits != 0L) {
            long requiredCellBit = bits & -bits; // 提取最低位的1
            int cellIndex = Long.numberOfTrailingZeros(requiredCellBit); // 获取格子索引
            boolean possible = false;
            ArrayList<Integer> options = placementsByCell[cellIndex]; // 获取能覆盖该格子的所有骨牌
            for (int i = 0; i < options.size(); i++) {
                int placementIndex = options.get(i).intValue();
                if (placementIndex < start) {
                    continue; // 跳过已处理过的骨牌
                }
                DominoPlacement placement = placements.get(placementIndex);
                if (isCompatible(placement, occupiedMask, blockedMask)) {
                    long restRequired = remainingRequiredMask & ~placement.cellsMask;
                    if ((placement.connectionMask & restRequired) == 0L) {
                        possible = true; // 找到可放置且不阻断其他必需雷的骨牌
                        break;
                    }
                }
            }
            if (!possible) {
                return false; // 某个必需雷无法被覆盖
            }
            bits &= bits - 1; // 清除最低位的1，处理下一个必需雷
        }
        return true;
    }

    // 检查骨牌是否可以放置（兼容性检查）
    private boolean isCompatible(DominoPlacement placement, long occupiedMask, long blockedMask) {
        if ((placement.cellsMask & allowedMask) != placement.cellsMask) {
            return false; // 骨牌超出允许区域
        }
        if ((placement.cellsMask & occupiedMask) != 0L) {
            return false; // 骨牌与已占用格子重叠
        }
        if ((placement.cellsMask & blockedMask) != 0L) {
            return false; // 骨牌与禁止区域重叠
        }
        return (placement.connectionMask & occupiedMask) == 0L; // 骨牌的连接区域不与已占用格子冲突
    }

    // 检查线索约束是否仍可能满足（剪枝）
    private boolean cluesRemainPossible(long occupiedMask, long blockedMask) {
        for (int i = 0; i < clues.size(); i++) {
            Clue clue = clues.get(i);
            int current = Long.bitCount(occupiedMask & clue.adjacentMask); // 当前已放置的雷数
            if (current > clue.number) {
                return false; // 剪枝：已超过线索数字
            }
            long available = clue.adjacentMask & allowedMask & ~occupiedMask & ~blockedMask; // 剩余可用格子
            if (current + Long.bitCount(available) < clue.number) {
                return false; // 剪枝：即使全放也不够
            }
        }
        return true;
    }

    // 验证所有线索是否完全匹配
    private boolean allCluesMatch(long occupiedMask) {
        for (int i = 0; i < clues.size(); i++) {
            Clue clue = clues.get(i);
            if (Long.bitCount(occupiedMask & clue.adjacentMask) != clue.number) {
                return false; // 线索相邻区域的雷数与提示不符
            }
        }
        return true;
    }

    // 构建所有可能的骨牌放置位置
    private void buildPlacements() {
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE - 1; c++) {
                addPlacement(r, c, r, c + 1); // 水平骨牌
            }
        }
        for (int r = 0; r < SIZE - 1; r++) {
            for (int c = 0; c < SIZE; c++) {
                addPlacement(r, c, r + 1, c); // 垂直骨牌
            }
        }
    }

    // 添加一个骨牌放置位置到列表
    private void addPlacement(int r1, int c1, int r2, int c2) {
        long cellsMask = bit(r1, c1) | bit(r2, c2); // 骨牌占用的两个格子
        if ((cellsMask & ~allowedMask) != 0L) {
            return; // 骨牌超出允许区域，跳过
        }
        DominoPlacement placement = new DominoPlacement(cellsMask, buildDominoConnectionMask(r1, c1, r2, c2));
        int index = placements.size();
        placements.add(placement); // 加入骨牌列表
        placementsByCell[r1 * SIZE + c1].add(Integer.valueOf(index)); // 更新反向索引
        placementsByCell[r2 * SIZE + c2].add(Integer.valueOf(index));
    }
}
