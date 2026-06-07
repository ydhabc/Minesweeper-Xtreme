package com.ydh.minesweeper_xtreme.game;

public enum GameMode {
    NORMAL("普通模式", "经典扫雷规则。点开所有非雷格即可获胜。", 10),
    TWO_H("变体一 ", "每一颗雷的左右方向必须至少有一颗雷相邻。可以横向连续三个或更多，没有孤雷。", 12),
    TWO_D("变体二 ", "雷必须组成1×2或2×1的骨牌，且骨牌之间不能八方向接触。", 12),
    TWO_G("变体三 ", "雷只以2×2的方块形式出现。没有单独的雷或1×2的雷。", 12);

    public final String title;
    public final String rule;
    public final int mines;

    GameMode(String title, String rule, int mines) {
        this.title = title;
        this.rule = rule;
        this.mines = mines;
    }

    public String highScoreKey() {
        if (this == TWO_H) {
            return "best_2h";
        }
        if (this == TWO_D) {
            return "best_2d";
        }
        if (this == TWO_G) {
            return "best_2g";
        }
        return "best_normal";
    }
}
