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

    public static SettingManager initialize() {
        File settingFile = new File(PathUtil.getBasePath(), "settings.xml");
        Setting setting;
        if (settingFile.exists()) {
            try {
                setting = Setting.loadFromFile(settingFile);
            } catch (IOException e) {
                setting = new Setting();
            }
        } else {
            setting = new Setting();
            try {
                setting.saveToFile(settingFile);
            } catch (IOException e) {
                juml.util.AppLog.warn(juml.util.ErrorCode.CFG_001, "SettingManager",
                        "Failed to save initial settings", e);
            }
        }
        instance = new SettingManager(setting, settingFile);
        return instance;
    }

    public Setting getSetting() {
        return setting;
    }

    public void save() {
        try {
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
