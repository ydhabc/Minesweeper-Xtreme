package com.ydh.minesweeper_xtreme.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ydh.minesweeper_xtreme.R;
import com.ydh.minesweeper_xtreme.game.GameCoordinator;
import com.ydh.minesweeper_xtreme.game.GameEngine;
import com.ydh.minesweeper_xtreme.game.GameMode;
import android.os.Handler;
import android.view.View;
public class MainActivity extends Activity implements BoardView.Listener {
    private static final String AUDIO_PREFS = "audio_settings";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_HOME_VOLUME = "home_volume";
    private static final String KEY_GAME_VOLUME = "game_volume";
    private static final int VOLUME_MAX = 100;

    private final Handler handler = new Handler();
    private final GameCoordinator gameCoordinator = new GameCoordinator();
    private final GameEngine engine = gameCoordinator.engine();

    // ── 面板 ──
    private View menuPanel;
    private View settingsPanel;
    private View rulesPanel;
    private View rulesListContainer;
    private View rulesDetailContainer;
    private View audioPanel;
    private View scoresPanel;
    private View continuePanel;
    private View gamePanel;
    private View shopPanel;          // 新增：皮肤商店面板

    // ── 文本 ──
    private TextView settingsModeText;
    private TextView ruleText;
    private TextView rulesTitleText;
    private TextView rulesSubtitleText;
    private TextView ruleDetailTitleText;
    private TextView rulesContentText;
    private TextView homeVolumeText;
    private TextView gameVolumeText;
    private TextView scoreNormalText;
    private TextView scoreNormalNoteText;
    private TextView scoreTwoHText;
    private TextView scoreTwoHNoteText;
    private TextView scoreTwoDText;
    private TextView scoreTwoDNoteText;
    private TextView scoreTwoGText;
    private TextView scoreTwoGNoteText;
    private TextView continueTitleText;
    private TextView continueBodyText;
    private TextView continueStatsText;
    private TextView gameModeText;
    private TextView statusLineText;
    private TextView timerLineText;
    private TextView messageText;
    private TextView resultTitleText;
    private TextView resultBodyText;
    private TextView shopCoinsText;      // 新增：商店内硬币显示
//    private TextView menuCoinsText;      // 新增：主菜单硬币显示

    // ── 控件 ──
    private CheckBox stepTimerCheck;
    private CheckBox expertCheck;
    private CheckBox muteCheck;
    private SeekBar homeVolumeSeek;
    private SeekBar gameVolumeSeek;

    // ── 按钮 ──
    private View resultPanel;
    private View resultSummaryContainer;
    private Button gameBackButton;
    private Button restartButton;
    private Button pauseButton;
    private Button rulesBackButton;
    private Button resultRestartButton;
    private Button resultMenuButton;
    private Button resultToggleMapButton;


    private LinearLayout skinCardsContainer;


    private SharedPreferences scores;
    private SharedPreferences audioPrefs;
    private GameMusicPlayer musicPlayer;
    private SkinManager skinManager;     // 新增
    private BoardView boardView;
    private GameMode selectedMode = GameMode.NORMAL;
    private boolean resultShown;
    private boolean resultMineMapVisible;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (gamePanel != null && gamePanel.getVisibility() == View.VISIBLE) {
                gameCoordinator.updateComboTimeout(now);
                if (gameCoordinator.handleStepTimeout(now)) {
                    showResultPage();
                }
                refreshGame();
            }
            handler.postDelayed(this, 500L);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final View splashLayout = findViewById(R.id.layout_splash);

        // 伪加载：设置 2.5 秒后自动隐藏
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // 简单的淡出动画，让体验更丝滑
                splashLayout.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                splashLayout.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }, 2500); // 2500 毫秒 = 2.5 秒




        scores = getSharedPreferences("scores", MODE_PRIVATE);
        audioPrefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
        skinManager = new SkinManager(this);   // 初始化皮肤管理器
        musicPlayer = new GameMusicPlayer(this);
        bindViews();
        bindActions();
        initRulesText();
        initAudioSettings();
        boardView.setEngine(engine);
        boardView.setListener(this);
        boardView.setSkinManager(skinManager); // 注入皮肤管理器
        showMenu();
        musicPlayer.start();
        musicPlayer.setMuted(muteCheck.isChecked());
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (musicPlayer != null) musicPlayer.stop();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        if (musicPlayer != null) musicPlayer.stop();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
        if (musicPlayer != null) {
            musicPlayer.start();
            syncMusicScene();
        }
    }


    @Override
    public void onCellTap(int row, int col) {
        if (engine.gameOver || engine.paused) return;
        gameCoordinator.reveal(row, col, System.currentTimeMillis());
        if (engine.won) updateRecordAndEvaluation();
        if (engine.gameOver) showResultPage();
        refreshGame();
    }

    @Override
    public void onCellLongPress(int row, int col) {
        gameCoordinator.toggleFlag(row, col);
        refreshGame();
    }

    @Override
    public void onCellRightClick(int row, int col) {
        gameCoordinator.toggleFlag(row, col);
        refreshGame();
    }

    private void bindViews() {
        menuPanel             = findViewById(R.id.menu_panel);
        settingsPanel         = findViewById(R.id.settings_panel);
        rulesPanel            = findViewById(R.id.rules_panel);
        rulesListContainer    = findViewById(R.id.rules_list_container);
        rulesDetailContainer  = findViewById(R.id.rules_detail_container);
        audioPanel            = findViewById(R.id.audio_panel);
        scoresPanel           = findViewById(R.id.scores_panel);
        continuePanel         = findViewById(R.id.continue_panel);
        gamePanel             = findViewById(R.id.game_panel);
        shopPanel             = findViewById(R.id.shop_panel);          // 新增

        settingsModeText      = (TextView) findViewById(R.id.txt_settings_mode);
        ruleText              = (TextView) findViewById(R.id.txt_rule);
        rulesTitleText        = (TextView) findViewById(R.id.txt_rules_title);
        rulesSubtitleText     = (TextView) findViewById(R.id.txt_rules_subtitle);
        ruleDetailTitleText   = (TextView) findViewById(R.id.txt_rule_detail_title);
        rulesContentText      = (TextView) findViewById(R.id.txt_rules_content);
        homeVolumeText        = (TextView) findViewById(R.id.txt_home_volume);
        gameVolumeText        = (TextView) findViewById(R.id.txt_game_volume);
        scoreNormalText       = (TextView) findViewById(R.id.txt_score_normal);
        scoreNormalNoteText   = (TextView) findViewById(R.id.txt_score_normal_note);
        scoreTwoHText         = (TextView) findViewById(R.id.txt_score_2h);
        scoreTwoHNoteText     = (TextView) findViewById(R.id.txt_score_2h_note);
        scoreTwoDText         = (TextView) findViewById(R.id.txt_score_2d);
        scoreTwoDNoteText     = (TextView) findViewById(R.id.txt_score_2d_note);
        scoreTwoGText         = (TextView) findViewById(R.id.txt_score_2g);
        scoreTwoGNoteText     = (TextView) findViewById(R.id.txt_score_2g_note);
        continueTitleText     = (TextView) findViewById(R.id.txt_continue_title);
        continueBodyText      = (TextView) findViewById(R.id.txt_continue_body);
        continueStatsText     = (TextView) findViewById(R.id.txt_continue_stats);
        gameModeText          = (TextView) findViewById(R.id.txt_game_mode);
        statusLineText        = (TextView) findViewById(R.id.txt_status_line);
        timerLineText         = (TextView) findViewById(R.id.txt_timer_line);
        messageText           = (TextView) findViewById(R.id.txt_message);
        resultTitleText       = (TextView) findViewById(R.id.txt_result_title);
        resultBodyText        = (TextView) findViewById(R.id.txt_result_body);
        shopCoinsText         = (TextView) findViewById(R.id.txt_shop_coins);     // 新增
//        menuCoinsText         = (TextView) findViewById(R.id.txt_menu_coins);     // 新增

        stepTimerCheck        = (CheckBox) findViewById(R.id.check_step_timer);
        expertCheck           = (CheckBox) findViewById(R.id.check_expert);
        muteCheck             = (CheckBox) findViewById(R.id.check_mute);
        homeVolumeSeek        = (SeekBar)  findViewById(R.id.seek_home_volume);
        gameVolumeSeek        = (SeekBar)  findViewById(R.id.seek_game_volume);

        resultPanel             = findViewById(R.id.result_panel);
        resultSummaryContainer  = findViewById(R.id.result_summary_container);
        gameBackButton          = (Button) findViewById(R.id.btn_game_back);
        restartButton           = (Button) findViewById(R.id.btn_restart);
        pauseButton             = (Button) findViewById(R.id.btn_pause);
        rulesBackButton         = (Button) findViewById(R.id.btn_rules_back);
        resultRestartButton     = (Button) findViewById(R.id.btn_result_restart);
        resultMenuButton        = (Button) findViewById(R.id.btn_result_menu);
        resultToggleMapButton   = (Button) findViewById(R.id.btn_result_toggle_map);
        boardView               = (BoardView) findViewById(R.id.board_view);
        skinCardsContainer      = (LinearLayout) findViewById(R.id.skin_cards_container); // 新增
    }


    private void bindActions() {
        ((Button) findViewById(R.id.btn_normal)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseMode(GameMode.NORMAL);
            }
        });
        ((Button) findViewById(R.id.btn_2h)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseMode(GameMode.TWO_H);
            }
        });
        ((Button) findViewById(R.id.btn_2d)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseMode(GameMode.TWO_D);
            }
        });
        ((Button) findViewById(R.id.btn_2g)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseMode(GameMode.TWO_G);
            }
        });

        ((Button) findViewById(R.id.btn_scores)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHighScores();
            }
        });
        ((Button) findViewById(R.id.btn_rules)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRulesPanel();
            }
        });
        ((Button) findViewById(R.id.btn_audio)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAudioPanel();
            }
        });
        ((Button) findViewById(R.id.btn_shop)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showShopPanel(); // 新增
            }
        });

        ((Button) findViewById(R.id.btn_back_menu)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        ((Button) findViewById(R.id.btn_start_game)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });

        gameBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                leaveGameToMenu();
            }
        });
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameCoordinator.togglePause(System.currentTimeMillis());
                refreshGame();
            }
        });
        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });
        resultRestartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });
        resultMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        resultToggleMapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleResultMineMap();
            }
        });

        rulesBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (rulesDetailContainer.getVisibility() == View.VISIBLE) showRulesIndex();
                else showMenu();
            }
        });
        ((Button) findViewById(R.id.btn_rule_basic)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("普通扫雷规则", basicRulesText());
            }
        });
        ((Button) findViewById(R.id.btn_rule_modes)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("变体模式规则", modeRulesText());
            }
        });
        ((Button) findViewById(R.id.btn_rule_score)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("积分规则", scoreRulesText());
            }
        });
        ((Button) findViewById(R.id.btn_rule_expert)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("专家模式", expertRulesText());
            }
        });
        ((Button) findViewById(R.id.btn_rule_timer)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("倒计时规则", timerRulesText());
            }
        });
        ((Button) findViewById(R.id.btn_rule_records)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("历史记录", recordRulesText());
            }
        });
        ((Button) findViewById(R.id.btn_rule_evaluation)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("开发者评价", evaluationRulesText());
            }
        });
        ((Button) findViewById(R.id.btn_rule_tips)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRuleDetail("提示", tipRulesText());
            }
        });

        ((Button) findViewById(R.id.btn_audio_back)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        ((Button) findViewById(R.id.btn_scores_back)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        ((Button) findViewById(R.id.btn_shop_back)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(); // 新增
            }
        });

        ((Button) findViewById(R.id.btn_continue_resume)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resumeGame();
            }
        });
        ((Button) findViewById(R.id.btn_continue_restart)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                restartActiveMode();
            }
        });
        ((Button) findViewById(R.id.btn_continue_cancel)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });


        muteCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveAudioSettings();
                if (musicPlayer != null) musicPlayer.setMuted(isChecked);
            }
        });

        homeVolumeSeek.setMax(VOLUME_MAX);
        gameVolumeSeek.setMax(VOLUME_MAX);
        SeekBar.OnSeekBarChangeListener volumeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeLabels();
                saveAudioSettings();
                if (musicPlayer != null)
                    musicPlayer.setVolumes(homeVolumeSeek.getProgress() / 100f,
                            gameVolumeSeek.getProgress() / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        homeVolumeSeek.setOnSeekBarChangeListener(volumeListener);
        gameVolumeSeek.setOnSeekBarChangeListener(volumeListener);
    }

    private void showShopPanel() {
        hideAllPanels();
        shopPanel.setVisibility(View.VISIBLE);
        refreshShopCoins();
        buildSkinCards();
        syncMusicScene();
    }

    /** 更新商店顶部硬币显示 */
    private void refreshShopCoins() {
        if (shopCoinsText != null)
            shopCoinsText.setText("硬币：" + skinManager.getCoins());
    }



    private void buildSkinCards() {
        if (skinCardsContainer == null) return;
        skinCardsContainer.removeAllViews();

        int currentSkin = skinManager.getCurrentSkin();

        for (int skinId = 1; skinId <= SkinManager.SKIN_COUNT; skinId++) {
            // 💡 声明为 final 变量，确保 Java 7 匿名内部类能够安全访问
            final int id = skinId;
            boolean owned   = skinManager.isSkinOwned(skinId);
            boolean current = (skinId == currentSkin);
            final int currentPrice = SkinManager.SKIN_PRICES[skinId];
            String name     = SkinManager.SKIN_NAMES[skinId];

            // 卡片容器
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setPadding(24, 20, 24, 20);
            int cardColor = current ? Color.rgb(40, 60, 90) : Color.rgb(28, 34, 45);
            card.setBackgroundColor(cardColor);
            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 12);
            card.setLayoutParams(cardParams);

            // 左侧文字信息
            LinearLayout infoBox = new LinearLayout(this);
            infoBox.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            infoBox.setLayoutParams(infoParams);

            TextView nameText = new TextView(this);
            nameText.setText("套装 " + skinId + "  " + name + (current ? "  ✓ 当前" : ""));
            nameText.setTextColor(Color.WHITE);
            nameText.setTextSize(16f);
            infoBox.addView(nameText);

            TextView statusText = new TextView(this);
            if (skinId == 1) {
                statusText.setText("默认免费");
            } else if (owned) {
                statusText.setText("已拥有");
            } else {
                statusText.setText("价格：" + currentPrice + " 硬币");
            }
            statusText.setTextColor(Color.rgb(180, 190, 210));
            statusText.setTextSize(13f);
            infoBox.addView(statusText);

            card.addView(infoBox);

            // 右侧按钮
            Button actionBtn = new Button(this);
            LinearLayout.LayoutParams btnParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            actionBtn.setLayoutParams(btnParams);

            if (current) {
                actionBtn.setText("已装备");
                actionBtn.setEnabled(false);
                actionBtn.setAlpha(0.5f);
            } else if (owned) {
                actionBtn.setText("装备");
                // 💡 将 Lambda 改为传统的匿名内部类
                actionBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        skinManager.setCurrentSkin(id);
                        boardView.invalidate();
                        buildSkinCards();  // 刷新卡片状态
                        Toast.makeText(MainActivity.this, "已切换到 " + SkinManager.SKIN_NAMES[id], Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                actionBtn.setText("购买");
                actionBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean success = skinManager.purchaseSkin(id);
                        if (success) {
                            refreshShopCoins();
                            buildSkinCards();
                            Toast.makeText(MainActivity.this, "购买成功！", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "硬币不足（需要 " + currentPrice + " 硬币）", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
            card.addView(actionBtn);

            skinCardsContainer.addView(card);
        }

        // 底部说明
        TextView hint = new TextView(this);
        hint.setText("每局游戏结束后可获得硬币（胜利更多）。\n每 50 分 = 1 硬币，每局上限 50 枚。");
        hint.setTextColor(Color.rgb(140, 150, 170));
        hint.setTextSize(12f);
        hint.setPadding(8, 16, 8, 0);
        skinCardsContainer.addView(hint);
    }


    private void chooseMode(final GameMode mode) {
        if (hasActiveGame(mode)) { showContinuePanel(mode); return; }
        openSettings(mode);
    }

    private boolean hasActiveGame(GameMode mode) {
        return engine.generated && !engine.gameOver && engine.mode == mode;
    }

    private void openSettings(GameMode mode) {
        selectedMode = mode;
        settingsModeText.setText("当前模式：" + mode.title);
        ruleText.setText(mode.rule);
        hideAllPanels();
        settingsPanel.setVisibility(View.VISIBLE);
        syncMusicScene();
    }

    private void resumeGame() {
        selectedMode = engine.mode;
        gameCoordinator.resumeFromMenu(System.currentTimeMillis());
        gameModeText.setText(engine.mode.title);
        hideAllPanels();
        gamePanel.setVisibility(View.VISIBLE);
        refreshGame();
        syncMusicScene();
    }

    private void restartActiveMode() {
        selectedMode = engine.mode;
        stepTimerCheck.setChecked(engine.stepTimerEnabled);
        expertCheck.setChecked(engine.expertModeEnabled);
        startGame();
    }

    private void startGame() {
        gameCoordinator.startGame(selectedMode, stepTimerCheck.isChecked(), expertCheck.isChecked());
        resultShown = false;
        resultMineMapVisible = false;
        setGameControlsEnabled(true);
        gameModeText.setText(selectedMode.title);
        hideAllPanels();
        gamePanel.setVisibility(View.VISIBLE);
        refreshGame();
        syncMusicScene();
    }

    private void leaveGameToMenu() {
        gameCoordinator.pauseFromMenu(System.currentTimeMillis());
        showMenu();
    }

    private void showMenu() {
        resultMineMapVisible = false;
        hideAllPanels();
        menuPanel.setVisibility(View.VISIBLE);
//        refreshMenuCoins();   // 更新硬币显示
        syncMusicScene();
    }

    private void refreshGame() {
        long now = System.currentTimeMillis();
        statusLineText.setText("分数 " + engine.score
                + "   连击 x" + engine.combo
                + "   总雷 " + engine.totalMinesDisplay()
                + "   剩余雷 " + engine.remainingMinesDisplay());
        String stepText = engine.stepTimerEnabled
                ? "单步 " + engine.stepRemainingSeconds(now) + "s"
                : "单步计时关闭";
        timerLineText.setText("全局 " + engine.elapsedSeconds(now) + "s   " + stepText
                + (engine.expertModeEnabled ? "   专家模式" : "")
                + (engine.gameOver ? "   已结算" : "")
                + (engine.paused ? "   已暂停" : ""));
        pauseButton.setText(engine.gameOver ? "已结算" : (engine.paused ? "继续" : "暂停"));
        messageText.setText(engine.message);
        boardView.invalidate();
    }

    private void showResultPage() {
        if (resultShown) return;
        resultShown = true;
        resultMineMapVisible = false;

       int earned = skinManager.earnCoinsForGame(engine.score, engine.won);
        String coinMsg = earned > 0 ? "\n获得 " + earned + " 硬币！（共 " + skinManager.getCoins() + "）" : "";

        resultTitleText.setText(engine.won ? "挑战成功" : "挑战失败");
        resultBodyText.setText("模式：" + engine.mode.title
                + "\n分数：" + engine.score
                + "\n用时：" + engine.elapsedSeconds(System.currentTimeMillis()) + " 秒"
                + "\n\n" + engine.message
                + coinMsg);
        resultPanel.setVisibility(View.VISIBLE);
        updateResultDisplayMode();
        boardView.invalidate();
        syncMusicScene();
    }


    private void showHighScores() {
        updateScoreCard(scoreNormalText, scoreNormalNoteText, GameMode.NORMAL);
        updateScoreCard(scoreTwoHText, scoreTwoHNoteText, GameMode.TWO_H);
        updateScoreCard(scoreTwoDText, scoreTwoDNoteText, GameMode.TWO_D);
        updateScoreCard(scoreTwoGText, scoreTwoGNoteText, GameMode.TWO_G);
        hideAllPanels();
        scoresPanel.setVisibility(View.VISIBLE);
        syncMusicScene();
    }

    private void showContinuePanel(GameMode mode) {
        long now = System.currentTimeMillis();
        continueTitleText.setText(mode.title + " 仍在进行");
        continueBodyText.setText("你从主页再次进入了这个模式。保留当前进度继续，或用同样设置开一局新的。");
        continueStatsText.setText("分数 " + engine.score
                + "\n连击 x" + engine.combo
                + "\n用时 " + engine.elapsedSeconds(now) + " 秒"
                + "\n剩余雷 " + engine.remainingMinesDisplay() + " / " + engine.totalMinesDisplay()
                + "\n单步计时：" + (engine.stepTimerEnabled ? engine.stepRemainingSeconds(now) + " 秒" : "关闭")
                + "\n专家模式：" + (engine.expertModeEnabled ? "开启" : "关闭")
                + "\n暂停记录：" + (engine.pauseUsed ? "本局已使用过暂停" : "尚未使用暂停"));
        hideAllPanels();
        continuePanel.setVisibility(View.VISIBLE);
        syncMusicScene();
    }

    private void showRulesPanel() {
        hideAllPanels();
        rulesPanel.setVisibility(View.VISIBLE);
        showRulesIndex();
        syncMusicScene();
    }

    private void showRulesIndex() {
        rulesBackButton.setText("返回");
        rulesTitleText.setText("规则说明");
        rulesSubtitleText.setText("选择一个主题查看完整说明。");
        rulesListContainer.setVisibility(View.VISIBLE);
        rulesDetailContainer.setVisibility(View.GONE);
    }

    private void showRuleDetail(String title, String content) {
        rulesBackButton.setText("目录");
        rulesTitleText.setText(title);
        rulesSubtitleText.setText("阅读完后可返回目录查看其他规则。");
        ruleDetailTitleText.setText(title);
        rulesContentText.setText(content);
        rulesListContainer.setVisibility(View.GONE);
        rulesDetailContainer.setVisibility(View.VISIBLE);
    }

    private void showAudioPanel() {
        hideAllPanels();
        audioPanel.setVisibility(View.VISIBLE);
        updateVolumeLabels();
        syncMusicScene();
    }

    private void hideAllPanels() {
        menuPanel.setVisibility(View.GONE);
        settingsPanel.setVisibility(View.GONE);
        rulesPanel.setVisibility(View.GONE);
        audioPanel.setVisibility(View.GONE);
        scoresPanel.setVisibility(View.GONE);
        continuePanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.GONE);
        resultPanel.setVisibility(View.GONE);
        shopPanel.setVisibility(View.GONE);
    }

    private void toggleResultMineMap() {
        if (!resultShown || engine.won) return;
        resultMineMapVisible = !resultMineMapVisible;
        updateResultDisplayMode();
    }

    private void updateResultDisplayMode() {
        if (!resultShown || engine.won) {
            resultPanel.setBackgroundColor(Color.parseColor("#EE0E141C"));
            resultSummaryContainer.setVisibility(View.VISIBLE);
            setResultActionsEnabled(true);
            setGameControlsEnabled(true);
            resultToggleMapButton.setVisibility(View.GONE);
            return;
        }
        if (resultMineMapVisible) {
            resultPanel.setBackgroundColor(Color.TRANSPARENT);
            resultSummaryContainer.setVisibility(View.GONE);
            setResultActionsEnabled(false);
            setGameControlsEnabled(false);
            resultToggleMapButton.setText("返回");
        } else {
            resultPanel.setBackgroundColor(Color.parseColor("#EE0E141C"));
            resultSummaryContainer.setVisibility(View.VISIBLE);
            setResultActionsEnabled(true);
            setGameControlsEnabled(true);
            resultToggleMapButton.setText("看雷图");
        }
        resultToggleMapButton.setVisibility(View.VISIBLE);
        boardView.invalidate();
    }

    private void setResultActionsEnabled(boolean enabled) {
        resultRestartButton.setEnabled(enabled);
        resultMenuButton.setEnabled(enabled);
        resultRestartButton.setAlpha(enabled ? 1f : 0.35f);
        resultMenuButton.setAlpha(enabled ? 1f : 0.35f);
    }

    private void setGameControlsEnabled(boolean enabled) {
        pauseButton.setEnabled(enabled);
        gameBackButton.setEnabled(enabled);
        restartButton.setEnabled(enabled);
        pauseButton.setAlpha(enabled ? 1f : 0.35f);
        gameBackButton.setAlpha(enabled ? 1f : 0.35f);
        restartButton.setAlpha(enabled ? 1f : 0.35f);
    }

    private void initRulesText() { showRulesIndex(); }

    private String basicRulesText() {
        return "1. 目标是找出所有非雷格，并且不要点到雷。\n"
                + "2. 点击格子后，如果不是雷，就会显示数字；数字表示它周围 8 个格子里有多少颗雷。\n"
                + "3. 如果一个格子显示 0，会自动展开周围安全区域。\n"
                + "4. 你可以右键或长按给格子做雷标记。标记只是辅助判断，不会因为标错而扣分或惩罚。\n"
                + "5. 在无解情况下，即使点到雷也不会判负，但是连续操作的加分会清零。";
    }

    private String modeRulesText() {
        return "普通模式\n" + GameMode.NORMAL.rule + "\n\n"
                + "变体一：水平双雷 (2H)\n" + GameMode.TWO_H.rule + "\n\n"
                + "变体二：骨牌雷 (2D)\n" + GameMode.TWO_D.rule + "\n\n"
                + "变体三：2x2 雷块 (2G)\n" + GameMode.TWO_G.rule;
    }

    private String scoreRulesText() {
        return "1. 点开格子会获得积分。\n"
                + "2. 连续快速点开格子将会获得更高的倍率，快速指的是 30 秒内进行单步操作。\n"
                + "3. 以极快的速度点开格子会有奖励积分。\n"
                + "4. 快速完成胜利会有大量的奖励积分。\n"
                + "5. 在无解情况下，点到雷会导致连续操作的倍率清零。";
    }

    private String expertRulesText() {
        return "1. 这是更严格的游戏模式，不允许猜测。\n"
                + "2. 在有确定安全格的情况下，如果点到非百分百安全的格子，将会直接判负。\n"
                + "3. 请务必先判断当前局面是否存在确定安全解。";
    }

    private String timerRulesText() {
        return "1. 开启单步计时后，每一步都有 60 秒限制。\n"
                + "2. 这一手超过时间仍未完成，系统会直接判负。\n"
                + "3. 暂停时计时不会继续走，恢复后再继续计算。\n"
                + "4. 使用暂停后，本局不会触发开发者评价，也不会计入历史记录。";
    }

    private String recordRulesText() {
        return "1. 只有同时开启“单步计时”和“专家模式”并且取得胜利时，本局才会参与历史记录。\n"
                + "2. 在局内使用暂停会导致本局无法计入历史记录。\n"
                + "3. 每个模式会单独保存自己的最高分。";
    }

    private String evaluationRulesText() {
        return "1. 只有同时开启“单步计时”和“专家模式”并且取得胜利，本局才会触发开发者评价。\n"
                + "2. 在局内使用暂停也会导致开发者评价无法触发。\n"
                + "3. 不同的分数会触发不同的开发者评价。\n"
                + "4. 同一分数段可能会触发不同开发者的评价，可以多挑战几次。";
    }

    private String tipRulesText() {
        return "主页返回游戏时，会先自动进入暂停。\n"
                + "再次点同一模式，可以选择继续游戏或用相同设置重新开始。\n"
                + "皮肤商店：每局游戏后自动发放硬币，用于解锁更多皮肤。";
    }

    private void initAudioSettings() {
        boolean muted = audioPrefs.getBoolean(KEY_MUTED, false);
        int homeProgress = audioPrefs.getInt(KEY_HOME_VOLUME, 28);
        int gameProgress = audioPrefs.getInt(KEY_GAME_VOLUME, 38);
        homeVolumeSeek.setProgress(homeProgress);
        gameVolumeSeek.setProgress(gameProgress);
        updateVolumeLabels();
        muteCheck.setChecked(muted);
        if (musicPlayer != null) {
            musicPlayer.setVolumes(homeProgress / 100f, gameProgress / 100f);
            musicPlayer.setMuted(muted);
        }
    }

    private void updateVolumeLabels() {
        homeVolumeText.setText("主页音量：" + homeVolumeSeek.getProgress() + "%");
        gameVolumeText.setText("游戏音量：" + gameVolumeSeek.getProgress() + "%");
    }

    private void saveAudioSettings() {
        audioPrefs.edit()
                .putBoolean(KEY_MUTED, muteCheck.isChecked())
                .putInt(KEY_HOME_VOLUME, homeVolumeSeek.getProgress())
                .putInt(KEY_GAME_VOLUME, gameVolumeSeek.getProgress())
                .apply();
    }

    private void syncMusicScene() {
        if (musicPlayer == null) return;
        musicPlayer.setScene(gamePanel.getVisibility() == View.VISIBLE);
        musicPlayer.setMuted(muteCheck.isChecked());
        musicPlayer.setVolumes(homeVolumeSeek.getProgress() / 100f,
                gameVolumeSeek.getProgress() / 100f);
    }

    private void updateScoreCard(TextView scoreText, TextView noteText, GameMode mode) {
        int score = scores.getInt(mode.highScoreKey(), 0);
        if (score > 0) {
            scoreText.setText(score + " 分");
            noteText.setText("当前模式最高纪录，继续刷新它。");
        } else {
            scoreText.setText("暂无记录");
            noteText.setText("完成一局符合记录条件的胜利后会显示在这里。");
        }
    }

    private void updateRecordAndEvaluation() {
        if (!engine.stepTimerEnabled || !engine.expertModeEnabled) return;
        if (engine.pauseUsed) {
            engine.message = engine.message + "\n本局使用过暂停，不触发最高分记录和开发者评价。";
            return;
        }
        int best = scores.getInt(engine.mode.highScoreKey(), 0);
        String suffix = "\n" + evaluation(engine.score);
        if (engine.score > best) {
            scores.edit().putInt(engine.mode.highScoreKey(), engine.score).apply();
            engine.newRecord = true;
            engine.message = engine.message + "\n新纪录！" + suffix;
        } else {
            engine.message = engine.message + suffix;
        }
    }

    private String evaluation(int score) {
        if (score >= 2300) return "好强啊，你超过了我目前所能达到的最高分，如果玩这个游戏能给你带来一些乐趣，那真是太好了";
        if (score >= 1900) return "太强了，你一定是扫雷的高手，感谢你游玩我的游戏！";
        if (score >= 1500) return "不错哦";
        if (score >= 1100) return "或许你可以达到更高的分数，我坚定的这么认为";
        return "还需要多加练习，不过，恭喜通关！";
    }
}