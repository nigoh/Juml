// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.List;

/**
 * Smali のアクセス修飾子トークン列に対する共通ヘルパ。
 *
 * <p>Smali ではアクセスフラグが {@code public static final} のようなキーワード列として
 * 書かれる。クラス/フィールド/メソッドで共通する「可視性記号への変換」だけをここに集約し、
 * 各モデルの重複を避ける。</p>
 */
final class SmaliAccess {

    private SmaliAccess() {
    }

    /**
     * 修飾子列から PlantUML のメンバ可視性記号を求める。
     * {@code +} public / {@code -} private / {@code #} protected / {@code ~} package-private。
     */
    static char visibilitySymbol(List<String> modifiers) {
        if (modifiers.contains("public")) {
            return '+';
        }
        if (modifiers.contains("private")) {
            return '-';
        }
        if (modifiers.contains("protected")) {
            return '#';
        }
        return '~';
    }
}
