package com.ydh.minesweeper_xtreme.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.ydh.minesweeper_xtreme.game.GameEngine; // 请根据你真实的 Engine 包路径调整
import com.ydh.minesweeper_xtreme.R;

public class BoardView extends View {
    interface Listener {
        void onCellTap(int row, int col);
        void onCellLongPress(int row, int col);
        void onCellRightClick(int row, int col);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Rect bitmapSrc = new Rect();
    private Bitmap flagBitmap;
    private GameEngine engine;
    private Listener listener;
    private SkinManager skinManager; // 💡 引入皮肤管理器
    private int downRow = -1;
    private int downCol = -1;
    private boolean longPressFired;
    private long lastRightClickMillis;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (downRow >= 0 && listener != null) {
                longPressFired = true;
                listener.onCellLongPress(downRow, downCol);
                invalidate();
            }
        }
    };

    public BoardView(Context context) {
        super(context);
        init();
    }

    public BoardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BoardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    void setEngine(GameEngine engine) {
        this.engine = engine;
        invalidate();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    // 💡 注入皮肤管理器的方法
    public void setSkinManager(SkinManager skinManager) {
        this.skinManager = skinManager;
        invalidate();
    }

    private void init() {
        setFocusable(true);
        setClickable(true);
        flagBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.flag);
        if (flagBitmap != null) {
            bitmapSrc.set(0, 0, flagBitmap.getWidth(), flagBitmap.getHeight());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        if (size == 0) {
            size = width == 0 ? height : width;
        }
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float boardSize = Math.min(getWidth(), getHeight());
        float cell = boardSize / GameEngine.SIZE;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(28, 34, 45));
        canvas.drawRoundRect(new RectF(0, 0, boardSize, boardSize), 18, 18, paint);

        for (int r = 0; r < GameEngine.SIZE; r++) {
            for (int c = 0; c < GameEngine.SIZE; c++) {
                float left = c * cell + 4;
                float top = r * cell + 4;
                float right = (c + 1) * cell - 4;
                float bottom = (r + 1) * cell - 4;
                rect.set(left, top, right, bottom);
                drawCell(canvas, r, c, rect, cell);
            }
        }
        if (engine != null && engine.paused) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(170, 12, 18, 26));
            canvas.drawRoundRect(new RectF(0, 0, boardSize, boardSize), 18, 18, paint);
            drawCenteredText(canvas, "PAUSED", new RectF(0, 0, boardSize, boardSize),
                    cell * 0.7f, Color.rgb(255, 211, 105), true);
        }
    }

    private void drawCell(Canvas canvas, int r, int c, RectF cellRect, float cellSize) {
        if (engine == null) return;

        boolean showMine = engine.gameOver && engine.mines[r][c];
        boolean revealed = engine.revealed[r][c];
        boolean flagged  = engine.flagged[r][c];
        boolean skipped  = engine.skippedMine[r][c];

        // 💡 核心逻辑：获取当前激活的皮肤编号
        int currentSkinId = (skinManager != null) ? skinManager.getCurrentSkin() : 1;

        if (currentSkinId == 4) {
            // ═══════════════════════════════════════════════════════════════
            // 🌟 皮肤 4：走你原本优雅的“纯色 Canvas 绘制版”
            // ═══════════════════════════════════════════════════════════════
            paint.setStyle(Paint.Style.FILL);
            if (revealed) {
                paint.setColor(Color.rgb(232, 224, 204));
            } else if (skipped) {
                paint.setColor(Color.rgb(128, 81, 66));
            } else if (showMine) {
                paint.setColor(Color.rgb(90, 41, 48));
            } else {
                paint.setColor(Color.rgb(52, 70, 88));
            }
            canvas.drawRoundRect(cellRect, 10, 10, paint);

            if (flagged) {
                drawFlag(canvas, cellRect); // 渲染经典 Canvas 旗子
            } else if (skipped) {
                drawCenteredText(canvas, "x", cellRect, cellSize * 0.48f, Color.WHITE, true);
            } else if (showMine) {
                drawCenteredText(canvas, "*", cellRect, cellSize * 0.45f, Color.rgb(255, 107, 107), true);
            } else if (revealed && engine.numbers[r][c] > 0) {
                drawCenteredText(canvas, String.valueOf(engine.numbers[r][c]), cellRect,
                        cellSize * 0.42f, numberColor(engine.numbers[r][c]), true);
            }
        } else {
            // ═══════════════════════════════════════════════════════════════
            // 🖼️ 皮肤 1, 2, 3：走 SkinManager 的图片资源解码加载版
            // ═══════════════════════════════════════════════════════════════
            Bitmap tileBitmap = null;

            if (flagged) {
                tileBitmap = skinManager.getFlagTile();      // index 11
            } else if (skipped) {
                // 如果图片套装没有 skipped 状态，可用未翻开兜底或特殊处理，这里用 nullTile 配合上层处理
                tileBitmap = skinManager.getNullTile();      // index 10
            } else if (showMine) {
                tileBitmap = skinManager.getBoomTile();      // index 9
            } else if (revealed) {
                int num = engine.numbers[r][c];              // index 0..8
                tileBitmap = skinManager.getNumberTile(num);
            } else {
                tileBitmap = skinManager.getNullTile();      // index 10 (未翻开)
            }

            // 执行图片绘制
            if (tileBitmap != null && !tileBitmap.isRecycled()) {
                Rect srcRect = new Rect(0, 0, tileBitmap.getWidth(), tileBitmap.getHeight());
                canvas.drawBitmap(tileBitmap, srcRect, cellRect, paint);
            } else {
                // 兜底防御：若找不到图片，用默认深蓝底色铺垫，避免白屏
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(52, 70, 88));
                canvas.drawRoundRect(cellRect, 10, 10, paint);
            }

            // 特殊覆盖层：如果是图片皮肤，当踩雷失败时，可以在图片上覆盖一个白色的 "x" 辅助提示
            if (skipped) {
                drawCenteredText(canvas, "x", cellRect, cellSize * 0.48f, Color.WHITE, true);
            }
        }
    }

    private void drawFlag(Canvas canvas, RectF cellRect) {
        if (flagBitmap != null && !flagBitmap.isRecycled()) {
            RectF dst = new RectF(
                    cellRect.left + cellRect.width() * 0.16f,
                    cellRect.top + cellRect.height() * 0.12f,
                    cellRect.right - cellRect.width() * 0.16f,
                    cellRect.bottom - cellRect.height() * 0.12f);
            canvas.drawBitmap(flagBitmap, bitmapSrc, dst, paint);
            return;
        }

        float poleX = cellRect.left + cellRect.width() * 0.42f;
        float top = cellRect.top + cellRect.height() * 0.24f;
        float bottom = cellRect.bottom - cellRect.height() * 0.22f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(4f, cellRect.width() * 0.06f));
        paint.setColor(Color.rgb(255, 211, 105));
        canvas.drawLine(poleX, top, poleX, bottom, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 107, 107));
        canvas.drawRect(
                poleX,
                top,
                cellRect.right - cellRect.width() * 0.22f,
                top + cellRect.height() * 0.28f,
                paint);
        paint.setStrokeWidth(1f);
    }

    private void drawCenteredText(Canvas canvas, String text, RectF cellRect, float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(bold);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = cellRect.centerY() - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(text, cellRect.centerX(), y, paint);
        paint.setFakeBoldText(false);
    }

    private int numberColor(int number) {
        switch (number) {
            case 1: return Color.rgb(45, 103, 196);
            case 2: return Color.rgb(38, 135, 86);
            case 3: return Color.rgb(199, 61, 54);
            case 4: return Color.rgb(91, 68, 168);
            case 5: return Color.rgb(153, 82, 43);
            case 6: return Color.rgb(28, 145, 155);
            default: return Color.rgb(44, 44, 44);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (engine == null || listener == null) {
            return true;
        }
        int action = event.getActionMasked();
        if (isRightClick(event)) {
            removeCallbacks(longPressRunnable);
            int row = toCell(event.getY());
            int col = toCell(event.getX());
            if (row >= 0 && col >= 0
                    && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_BUTTON_PRESS)
                    && canAcceptRightClick()) {
                listener.onCellRightClick(row, col);
            }
            downRow = -1;
            downCol = -1;
            longPressFired = true;
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            downRow = toCell(event.getY());
            downCol = toCell(event.getX());
            longPressFired = false;
            if (downRow >= 0 && downCol >= 0) {
                postDelayed(longPressRunnable, 300L);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            removeCallbacks(longPressRunnable);
            int row = toCell(event.getY());
            int col = toCell(event.getX());
            if (!longPressFired && row == downRow && col == downCol && row >= 0) {
                listener.onCellTap(row, col);
            }
            downRow = -1;
            downCol = -1;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPressRunnable);
            downRow = -1;
            downCol = -1;
            return true;
        }
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (engine == null || listener == null) {
            return true;
        }
        if (isRightClick(event)) {
            int row = toCell(event.getY());
            int col = toCell(event.getX());
            if (row >= 0 && col >= 0 && canAcceptRightClick()) {
                listener.onCellRightClick(row, col);
                invalidate();
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private boolean isRightClick(MotionEvent event) {
        return (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0;
    }

    private boolean canAcceptRightClick() {
        long now = System.currentTimeMillis();
        if (now - lastRightClickMillis < 180L) {
            return false;
        }
        lastRightClickMillis = now;
        return true;
    }

    private int toCell(float coordinate) {
        float boardSize = Math.min(getWidth(), getHeight());
        if (coordinate < 0 || coordinate >= boardSize) {
            return -1;
        }
        int cell = (int) (coordinate / (boardSize / GameEngine.SIZE));
        if (cell < 0 || cell >= GameEngine.SIZE) {
            return -1;
        }
        return cell;
    }
}