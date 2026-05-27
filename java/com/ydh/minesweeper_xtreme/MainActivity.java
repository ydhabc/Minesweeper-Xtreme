package com.ydh.minesweeper_xtreme;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity implements BoardView.Listener {
    private static final String AUDIO_PREFS = "audio_settings";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_HOME_VOLUME = "home_volume";
    private static final String KEY_GAME_VOLUME = "game_volume";
    private static final int VOLUME_MAX = 100;

    private final Handler handler = new Handler();
    private final GameEngine engine = new GameEngine();

    private View menuPanel;
    private View settingsPanel;
    private View rulesPanel;
    private View audioPanel;
    private View gamePanel;
    private TextView settingsModeText;
    private TextView ruleText;
    private TextView rulesContentText;
    private TextView homeVolumeText;
    private TextView gameVolumeText;
    private CheckBox stepTimerCheck;
    private CheckBox expertCheck;
    private CheckBox muteCheck;
    private SeekBar homeVolumeSeek;
    private SeekBar gameVolumeSeek;
    private TextView gameModeText;
    private TextView statusLineText;
    private TextView timerLineText;
    private TextView messageText;
    private TextView resultTitleText;
    private TextView resultBodyText;
    private BoardView boardView;
    private View resultPanel;
    private View resultSummaryContainer;
    private Button gameBackButton;
    private Button restartButton;
    private Button pauseButton;
    private Button resultRestartButton;
    private Button resultMenuButton;
    private Button resultToggleMapButton;
    private SharedPreferences scores;
    private SharedPreferences audioPrefs;
    private GameMusicPlayer musicPlayer;
    private GameMode selectedMode = GameMode.NORMAL;
    private boolean resultShown;
    private boolean resultMineMapVisible;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (gamePanel != null && gamePanel.getVisibility() == View.VISIBLE) {
                if (!engine.paused) {
                    engine.updateComboTimeout(now);
                }
                if (!engine.paused && engine.stepTimerEnabled && engine.generated && !engine.gameOver
                        && engine.stepRemainingSeconds(now) <= 0) {
                    engine.loseByTimeout(now);
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
        scores = getSharedPreferences("scores", MODE_PRIVATE);
        audioPrefs = getSharedPreferences(AUDIO_PREFS, MODE_PRIVATE);
        musicPlayer = new GameMusicPlayer(this);
        bindViews();
        bindActions();
        initRulesText();
        initAudioSettings();
        boardView.setEngine(engine);
        boardView.setListener(this);
        showMenu();
        musicPlayer.start();
        musicPlayer.setMuted(muteCheck.isChecked());
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (musicPlayer != null) {
            musicPlayer.stop();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        if (musicPlayer != null) {
            musicPlayer.stop();
        }
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
        if (engine.gameOver || engine.paused) {
            return;
        }
        engine.reveal(row, col, System.currentTimeMillis());
        if (engine.won) {
            updateRecordAndEvaluation();
        }
        if (engine.gameOver) {
            showResultPage();
        }
        refreshGame();
    }

    @Override
    public void onCellLongPress(int row, int col) {
        engine.toggleFlag(row, col);
        refreshGame();
    }

    @Override
    public void onCellRightClick(int row, int col) {
        engine.toggleFlag(row, col);
        refreshGame();
    }

    private void bindViews() {
        menuPanel = findViewById(R.id.menu_panel);
        settingsPanel = findViewById(R.id.settings_panel);
        rulesPanel = findViewById(R.id.rules_panel);
        audioPanel = findViewById(R.id.audio_panel);
        gamePanel = findViewById(R.id.game_panel);
        settingsModeText = (TextView) findViewById(R.id.txt_settings_mode);
        ruleText = (TextView) findViewById(R.id.txt_rule);
        rulesContentText = (TextView) findViewById(R.id.txt_rules_content);
        homeVolumeText = (TextView) findViewById(R.id.txt_home_volume);
        gameVolumeText = (TextView) findViewById(R.id.txt_game_volume);
        stepTimerCheck = (CheckBox) findViewById(R.id.check_step_timer);
        expertCheck = (CheckBox) findViewById(R.id.check_expert);
        muteCheck = (CheckBox) findViewById(R.id.check_mute);
        homeVolumeSeek = (SeekBar) findViewById(R.id.seek_home_volume);
        gameVolumeSeek = (SeekBar) findViewById(R.id.seek_game_volume);
        gameModeText = (TextView) findViewById(R.id.txt_game_mode);
        statusLineText = (TextView) findViewById(R.id.txt_status_line);
        timerLineText = (TextView) findViewById(R.id.txt_timer_line);
        messageText = (TextView) findViewById(R.id.txt_message);
        resultTitleText = (TextView) findViewById(R.id.txt_result_title);
        resultBodyText = (TextView) findViewById(R.id.txt_result_body);
        resultPanel = findViewById(R.id.result_panel);
        resultSummaryContainer = findViewById(R.id.result_summary_container);
        resultToggleMapButton = (Button) findViewById(R.id.btn_result_toggle_map);
        resultRestartButton = (Button) findViewById(R.id.btn_result_restart);
        resultMenuButton = (Button) findViewById(R.id.btn_result_menu);
        gameBackButton = (Button) findViewById(R.id.btn_game_back);
        restartButton = (Button) findViewById(R.id.btn_restart);
        pauseButton = (Button) findViewById(R.id.btn_pause);
        boardView = (BoardView) findViewById(R.id.board_view);
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
                engine.togglePause(System.currentTimeMillis());
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
        ((Button) findViewById(R.id.btn_rules_back)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        ((Button) findViewById(R.id.btn_audio_back)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });
        muteCheck.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                saveAudioSettings();
                if (musicPlayer != null) {
                    musicPlayer.setMuted(isChecked);
                }
            }
        });
        homeVolumeSeek.setMax(VOLUME_MAX);
        gameVolumeSeek.setMax(VOLUME_MAX);
        homeVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeLabels();
                saveAudioSettings();
                if (musicPlayer != null) {
                    musicPlayer.setVolumes(homeVolumeSeek.getProgress() / 100f, gameVolumeSeek.getProgress() / 100f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        gameVolumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVolumeLabels();
                saveAudioSettings();
                if (musicPlayer != null) {
                    musicPlayer.setVolumes(homeVolumeSeek.getProgress() / 100f, gameVolumeSeek.getProgress() / 100f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void chooseMode(final GameMode mode) {
        if (hasActiveGame(mode)) {
            new AlertDialog.Builder(this)
                    .setTitle(mode.title)
                    .setMessage("这个模式有一局未结束的游戏。")
                    .setPositiveButton("继续游戏", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            resumeGame();
                        }
                    })
                    .setNegativeButton("重新开始", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            restartActiveMode();
                        }
                    })
                    .setNeutralButton("取消", null)
                    .show();
            return;
        }
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
        engine.resumeFromMenu(System.currentTimeMillis());
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
        engine.reset(selectedMode, stepTimerCheck.isChecked(), expertCheck.isChecked());
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
        engine.pauseFromMenu(System.currentTimeMillis());
        showMenu();
    }

    private void showMenu() {
        resultMineMapVisible = false;
        hideAllPanels();
        menuPanel.setVisibility(View.VISIBLE);
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
        if (resultShown) {
            return;
        }
        resultShown = true;
        resultMineMapVisible = false;
        resultTitleText.setText(engine.won ? "挑战成功" : "挑战失败");
        resultBodyText.setText("模式：" + engine.mode.title
                + "\n分数：" + engine.score
                + "\n用时：" + engine.elapsedSeconds(System.currentTimeMillis()) + " 秒"
                + "\n\n" + engine.message);
        resultPanel.setVisibility(View.VISIBLE);
        updateResultDisplayMode();
        boardView.invalidate();
        syncMusicScene();
    }

    private void showHighScores() {
        String message = "普通模式：" + formatScore(GameMode.NORMAL) + "\n\n"
                + "变体一 (2H)：" + formatScore(GameMode.TWO_H) + "\n\n"
                + "变体二 (2D)：" + formatScore(GameMode.TWO_D) + "\n\n"
                + "变体三 (2G)：" + formatScore(GameMode.TWO_G);
        new AlertDialog.Builder(this)
                .setTitle("历史最高分")
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showRulesPanel() {
        hideAllPanels();
        rulesPanel.setVisibility(View.VISIBLE);
        syncMusicScene();
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
        gamePanel.setVisibility(View.GONE);
        resultPanel.setVisibility(View.GONE);
    }

    private void toggleResultMineMap() {
        if (!resultShown || engine.won) {
            return;
        }
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

    private void initRulesText() {
        rulesContentText.setText(
                "普通扫雷规则\n"
                        + "1. 目标是找出所有非雷格，并且不要点到雷。\n"
                        + "2. 点击格子后，如果不是雷，就会显示数字；数字表示它周围 8 个格子里有多少颗雷。\n"
                        + "3. 如果一个格子显示 0，会自动展开周围安全区域。\n"
                        + "4. 你可以右键或长按给格子做雷标记。标记只是辅助判断，不会因为标错而扣分或惩罚。\n"
                        + "5. 在无解情况下，即使点到雷也不会判负，但是连续操作的加分会清零\n"
                        + "\n"
                        + "积分规则\n"
                        + "1. 点开格子会获得积分\n"
                        + "2. 连续快速点开格子将会获得更高的倍率，快速指的是30秒内进行单步操作\n"
                        + "3. 快速完成胜利会有大量的奖励积分\n"
                        + "4. 在无解情况下，点到雷会导致连续操作的倍率清零\n"
                        + "\n"
                        + "专家模式\n"
                        + "1. 这是更严格的游戏模式，不允许猜测，在有解的情况下如果点到非百分百安全的格子将会直接判负\n"
                        + "2. 请务必谨慎判断当前情况是否有解哦\n"
                        + "\n"
                        + "倒计时规则\n"
                        + "1. 开启单步计时后，每一步都有 60 秒限制。\n"
                        + "2. 这一手超过时间仍未完成，系统会直接判负。\n"
                        + "3. 暂停时计时不会继续走，恢复后再继续计算。但是使用后本局将不会触发开发者评价，也不会计入历史最高分\n"
                        + "\n"
                        + "历史最高分\n"
                        + "1. 只有同时开启“单步计时”和“专家模式”并且取得胜利时，本局才会参与历史最高分。\n"
                        + "2. 在局内使用暂停也会导致历史最高分无法计入\n"
                        + "3. 不断挑战自己，创下新的记录吧！\n"
                        + "\n"
                        + "开发者评价\n"
                        + "1. 只有同时开启“单步计时”和“专家模式”并且取得胜利，本局才会触发开发者评价。\n"
                        + "2. 在局内使用暂停也会导致开发者评价无法触发\n"
                        + "3. 不同的分数会触发不同的开发者评价哦！\n"
                        + "4. 同一分数段可能会触发不同开发者的评价，尽可能多看到一些我们的评价吧！\n"
                        + "\n"
                        + "提示\n"
                        + "主页返回游戏时，会先自动进入暂停；再次点同一模式，可以选择继续游戏或重新开始。"
        );
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
        if (musicPlayer == null) {
            return;
        }
        musicPlayer.setScene(gamePanel.getVisibility() == View.VISIBLE);
        musicPlayer.setMuted(muteCheck.isChecked());
        musicPlayer.setVolumes(homeVolumeSeek.getProgress() / 100f, gameVolumeSeek.getProgress() / 100f);
    }

    private String formatScore(GameMode mode) {
        int score = scores.getInt(mode.highScoreKey(), 0);
        return score > 0 ? score + " 分" : "暂无";
    }

    private void updateRecordAndEvaluation() {
        if (!engine.stepTimerEnabled || !engine.expertModeEnabled) {
            return;
        }
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
        if (score >= 2100) {
            return "好强啊，你超过了我目前所能达到的最高分，如果玩这个游戏能给你带来一些乐趣，那真是太好了";
        }
        if (score >= 1800) {
            return "太强了，你一定是扫雷的高手，感谢你游玩我的游戏！";
        }
        if (score >= 1400) {
            return "不错哦";
        }
        if (score >= 1000) {
            return "或许你可以达到更高的分数，我坚定的这么认为";
        }
        return "还需要多加练习，不过，恭喜通关！";
    }
}
