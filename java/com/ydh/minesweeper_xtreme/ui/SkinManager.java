package com.ydh.minesweeper_xtreme.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * 皮肤管理器：
 * 皮肤编号：1 = 默认a（免费），2 = 套装b，3 = 套装c
 */
public final class SkinManager {

    public static final int SKIN_COUNT = 4;
    public static final int DEFAULT_SKIN = 1;

    /** 各套皮肤的硬币价格（套装1免费） */
    public static final int[] SKIN_PRICES = {0, 0, 300, 500 , 800};

    /** 各套皮肤的显示名称（index 1-based） */
    public static final String[] SKIN_NAMES = {"", "经典", "暗夜", "荒野","极客"};

    // 💡 建立皮肤 ID 到前缀字母的映射 (index 0留空，1->'a', 2->'b', 3->'c')
    private static final String[] SKIN_PREFIXES = {"", "a", "b", "c", "d"};

    private static final String PREFS_NAME = "skin_prefs";
    private static final String KEY_CURRENT_SKIN = "current_skin";
    private static final String KEY_COINS = "coins";
    private static final String KEY_SKIN_OWNED_PREFIX = "skin_owned_";

    private final SharedPreferences prefs;
    private final Context context;

    // 缓存当前皮肤的 Bitmap
    private int cachedSkinId = -1;
    private final Bitmap[] cachedTiles = new Bitmap[12];

    public SkinManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public int getCoins() {
        return prefs.getInt(KEY_COINS, 0);
    }

    public void addCoins(int amount) {
        int newTotal = getCoins() + amount;
        prefs.edit().putInt(KEY_COINS, newTotal).apply();
    }

    /**
     * 尝试购买皮肤。返回 true 表示购买成功，false 表示硬币不足或已拥有。
     */
    public boolean purchaseSkin(int skinId) {
        if (skinId < 1 || skinId > SKIN_COUNT) return false;
        if (isSkinOwned(skinId)) return false;
        int price = SKIN_PRICES[skinId];
        if (getCoins() < price) return false;
        prefs.edit()
                .putInt(KEY_COINS, getCoins() - price)
                .putBoolean(KEY_SKIN_OWNED_PREFIX + skinId, true)
                .apply();
        return true;
    }

    public boolean isSkinOwned(int skinId) {
        if (skinId == DEFAULT_SKIN) return true;
        return prefs.getBoolean(KEY_SKIN_OWNED_PREFIX + skinId, false);
    }

    // 当前皮肤

    public int getCurrentSkin() {
        return prefs.getInt(KEY_CURRENT_SKIN, DEFAULT_SKIN);
    }

    /**
     * 切换皮肤
     */
    public boolean setCurrentSkin(int skinId) {
        if (!isSkinOwned(skinId)) return false;
        prefs.edit().putInt(KEY_CURRENT_SKIN, skinId).apply();
        invalidateCache();
        return true;
    }


    public Bitmap getTile(int index) {
        int skin = getCurrentSkin();
        if (skin != cachedSkinId) {
            invalidateCache();
            cachedSkinId = skin;
        }
        if (cachedTiles[index] != null) {
            return cachedTiles[index];
        }
        String name = buildResourceName(skin, index);
        int resId = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (resId == 0) return null;
        Bitmap bm = BitmapFactory.decodeResource(context.getResources(), resId);
        cachedTiles[index] = bm;
        return bm;
    }


    public Bitmap getNumberTile(int number) {
        return getTile(number); // 0..8
    }

    public Bitmap getBoomTile() {
        return getTile(9);
    }

    public Bitmap getNullTile() {
        return getTile(10);
    }


    public Bitmap getFlagTile() {
        return getTile(11);
    }

   private String buildResourceName(int skin, int index) {
        String prefix = "a"; // 默认安全兜底
        if (skin >= 1 && skin < SKIN_PREFIXES.length) {
            prefix = SKIN_PREFIXES[skin];
        }

        String suffix;
        if (index <= 8) {
            suffix = String.valueOf(index);
        } else if (index == 9) {
            suffix = "boom";
        } else if (index == 10) {
            suffix = "null";
        } else {
            suffix = "flag";
        }

        return prefix + "_" + suffix;
    }

    private void invalidateCache() {
        for (int i = 0; i < cachedTiles.length; i++) {
            if (cachedTiles[i] != null) {
                cachedTiles[i].recycle();
                cachedTiles[i] = null;
            }
        }
        cachedSkinId = -1;
    }

    /**
     * 每局结束后根据分数发放硬币。
     * 规则：每 50 分 = 1 枚硬币，上限 50 枚/局。
     */
    public int earnCoinsForGame(int score, boolean won) {
        int base = score / 50;
        if (!won) base = base / 2;
        int earned = Math.min(base, 50);
        if (earned > 0) addCoins(earned);
        return earned;
    }
}