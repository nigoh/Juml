// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.actions;

/**
 * ユーザー操作ハンドラの単一エントリ。
 */
public final class UiActionEntry {

    /** 操作の種類。ラベルは素人向けに「日本語 (元の API 名)」で表示する。 */
    public enum ActionType {
        ON_CLICK("タップ (onClick)"),
        ON_LONG_CLICK("長押し (onLongClick)"),
        ON_CHECKED_CHANGED("ON/OFF 切替 (onCheckedChanged)"),
        XML_ON_CLICK("タップ (XML android:onClick)"),
        COMPOSE_CLICK("タップ (Compose onClick)"),
        MENU_ITEM("メニュー選択 (onOptionsItemSelected)");

        public final String label;

        ActionType(String label) {
            this.label = label;
        }
    }

    /** UI コンポーネントの ID または説明。不明なら ""。 */
    public final String componentId;
    /** 操作の種類。 */
    public final ActionType actionType;
    /** ハンドラメソッド名 (クラス名.メソッド名、または メソッド名 のみ)。 */
    public final String handler;
    /** 検出ファイルパス。 */
    public final String file;
    /** 行番号 (-1 なら不明)。 */
    public final int line;

    public UiActionEntry(String componentId, ActionType actionType,
                          String handler, String file, int line) {
        this.componentId = componentId != null ? componentId : "";
        this.actionType = actionType;
        this.handler = handler != null ? handler : "";
        this.file = file != null ? file : "";
        this.line = line;
    }

    /** ファイル名のみを返す。 */
    public String shortFileName() {
        if (file.isEmpty()) return "";
        int sep = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
        return sep >= 0 ? file.substring(sep + 1) : file;
    }
}
