// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.Setting;

import javax.swing.JFrame;
import javax.swing.JSplitPane;

/**
 * ウィンドウの位置・サイズ・サイドバー分割位置の保存/復元を担うヘルパ。
 *
 * <p>{@link Setting} にスキーマ ({@code windowX/Y/Width/Height} ・
 * {@code mainSplitLocation}) は揃っているので、それを実際に読み書きする。</p>
 */
final class WindowStateManager {

    private WindowStateManager() {
    }

    /**
     * pack 済みフレームに、保存済みのウィンドウ位置とサイドバー分割位置を復元する。
     * 位置が未保存なら画面中央に配置する。
     */
    static void restoreLocationAndDivider(JFrame frame, JSplitPane split, Setting setting) {
        if (setting.getWindowX() >= 0 && setting.getWindowY() >= 0) {
            frame.setLocation(setting.getWindowX(), setting.getWindowY());
        } else {
            frame.setLocationRelativeTo(null);
        }
        if (split != null && setting.getMainSplitLocation() > 0) {
            split.setDividerLocation(setting.getMainSplitLocation());
        }
        if (setting.isWindowMaximized()) {
            frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
    }

    /** 現在のウィンドウ位置/サイズ/分割位置を {@link Setting} へ保存する (ベストエフォート)。 */
    static void save(JFrame frame, JSplitPane split, Setting setting, Runnable persist) {
        try {
            boolean maximized =
                    (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            setting.setWindowMaximized(maximized);
            // 最大化中は getWidth/Height が画面全体になるため、通常時の bounds を上書きしない。
            if (!maximized) {
                setting.setWindowX(frame.getX());
                setting.setWindowY(frame.getY());
                setting.setWindowWidth(frame.getWidth());
                setting.setWindowHeight(frame.getHeight());
            }
            if (split != null) {
                setting.setMainSplitLocation(split.getDividerLocation());
            }
            persist.run();
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート (失敗してもアプリ終了を妨げない)
        }
    }
}
