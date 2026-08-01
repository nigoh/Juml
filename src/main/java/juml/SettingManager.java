// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import juml.util.PathUtil;

import java.io.File;
import java.io.IOException;

/**
 * Settingの読み込み・保存を管理するクラス。
 * グローバル静的状態を排除するための中間ステップとして、
 * シングルトンパターンで管理する。
 */
public class SettingManager {
    private static SettingManager instance;

    private Setting setting;
    private final File settingFile;

    private SettingManager(Setting setting, File settingFile) {
        this.setting = setting;
        this.settingFile = settingFile;
    }

    public static SettingManager getInstance() {
        if (instance == null) {
            throw new RuntimeException("SettingManager is not initialized");
        }
        return instance;
    }

    /**
     * 設定を読み込む (無ければ既定値で作成する)。
     *
     * <p>保存先はユーザー単位の {@code ~/.juml/settings.xml} ({@link PathUtil#getUserDataDir()})。
     * 以前は起動時のカレントディレクトリ配下に置いていたため、起動する場所を変えるだけで
     * 設定 (テーマ・L&amp;F・言語・ウィンドウサイズ等) が毎回初期値へ戻り、行った先々に
     * {@code settings.xml} が作られ、インストール先が書込不可だと保存自体できなかった。
     * 解析キャッシュ・下書き・プロジェクト履歴と同じ場所へ揃える。</p>
     *
     * <p>旧位置に設定がありユーザー領域にまだ無い場合は、一度だけ引き継ぐ (旧ファイルは
     * 消さずに残す。別バージョンの Juml がまだ参照している可能性があるため)。</p>
     */
    public static SettingManager initialize() {
        return initialize(PathUtil.getUserDataDir());
    }

    /** 保存先ディレクトリを明示して初期化する (テスト用シーム)。 */
    static SettingManager initialize(File dir) {
        File settingFile = new File(dir, "settings.xml");
        Setting setting = null;
        if (settingFile.exists()) {
            setting = tryLoad(settingFile);
        } else {
            File legacy = new File(PathUtil.getBasePath(), "settings.xml");
            if (legacy.isFile()) {
                setting = tryLoad(legacy);
                if (setting != null) {
                    juml.util.AppLog.info("SettingManager",
                            "Migrated settings from " + legacy.getAbsolutePath()
                                    + " to " + settingFile.getAbsolutePath());
                }
            }
        }
        boolean isNew = setting == null;
        if (isNew) {
            setting = new Setting();
        }
        SettingManager created = new SettingManager(setting, settingFile);
        if (!settingFile.exists()) {
            // 新規作成でも旧位置からの引き継ぎでも、ユーザー領域へ 1 度書き出して確定させる。
            created.save();
        }
        instance = created;
        return instance;
    }

    /** 読み込みに失敗したら null (呼び出し側が既定値へフォールバックする)。 */
    private static Setting tryLoad(File file) {
        try {
            return Setting.loadFromFile(file);
        } catch (IOException e) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_001, "SettingManager",
                    "Failed to load settings: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    public Setting getSetting() {
        return setting;
    }

    public void save() {
        try {
            // 初回はユーザー領域 (~/.juml) がまだ無いことがあるので作ってから書く。
            File parent = settingFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            setting.saveToFile(settingFile);
        } catch (IOException e) {
            juml.util.AppLog.warn(juml.util.ErrorCode.CFG_001, "SettingManager",
                    "Failed to save settings", e);
        }
    }

    /**
     * テスト専用: シングルトンインスタンスを null にリセットする。
     *
     * <p>テスト間で SettingManager の状態がリークしないよう、各テストの @After/@AfterClass
     * から呼び出すことを想定する。本番コードからは呼ばないこと。</p>
     */
    public static void resetForTest() {
        instance = null;
    }
}
