// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * ソースビューア / 図プレビューなど「エディタ面」の配色をテーマ追従で解決するヘルパ。
 *
 * <p>FlatLaf Light / Dark を切り替えても破綻しないよう、背景・前景は {@link UIManager}
 * から引き、ガター/現在行などの派生色は背景の明度から計算する。トークン配色
 * ({@link SourceHighlighter}) のように {@code UIManager} に無い色は {@link #isDark()}
 * 判定でライト/ダークのパレットを選び分ける。</p>
 */
final class EditorColors {

    private EditorColors() {
    }

    /** 現在のテーマがダーク基調か (テキスト面の背景明度で判定)。 */
    static boolean isDark() {
        Color bg = UIManager.getColor("TextPane.background");
        if (bg == null) {
            bg = UIManager.getColor("Panel.background");
        }
        return bg != null && luminance(bg) < 0.5;
    }

    /** 本文テキスト色 (テーマ追従)。 */
    static Color text() {
        return uiOr("TextPane.foreground", isDark() ? 0xD4D4D4 : 0x1E1E1E);
    }

    /** テキスト面の背景 (テーマ追従)。 */
    static Color background() {
        return uiOr("TextPane.background", isDark() ? 0x1E1E1E : 0xFFFFFF);
    }

    /** 行番号ガターの背景 (本文背景よりわずかに差をつける)。 */
    static Color gutterBackground() {
        Color panel = UIManager.getColor("Panel.background");
        if (panel != null) {
            return panel;
        }
        return new Color(isDark() ? 0x2B2B2B : 0xF3F3F3);
    }

    /** 行番号ガターの文字色 (淡色)。 */
    static Color gutterForeground() {
        return uiOr("Label.disabledForeground", isDark() ? 0x808080 : 0x999999);
    }

    /** 現在行ハイライト色 (背景からわずかに持ち上げ/沈める)。 */
    static Color currentLine() {
        Color bg = background();
        return isDark() ? shift(bg, 18) : shift(bg, -12);
    }

    // ── ヘルパ ──
    private static Color uiOr(String key, int rgbFallback) {
        Color c = UIManager.getColor(key);
        return c != null ? new Color(c.getRGB()) : new Color(rgbFallback);
    }

    private static double luminance(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }

    /** 各チャンネルを {@code delta} (±) ずらす (0..255 でクランプ)。 */
    private static Color shift(Color c, int delta) {
        return new Color(
                clamp(c.getRed() + delta),
                clamp(c.getGreen() + delta),
                clamp(c.getBlue() + delta));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
