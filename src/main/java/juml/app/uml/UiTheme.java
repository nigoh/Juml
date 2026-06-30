// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import com.formdev.flatlaf.FlatLaf;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FlatLaf を VS Code 風の洗練トーンへ寄せるグローバル既定値をまとめるユーティリティ。
 *
 * <p>FlatLaf の {@link FlatLaf#setGlobalExtraDefaults(Map)} に登録した値は、
 * Light / Dark / 別テーマへ切り替えても <b>FlatLaf を再セットアップするたびに自動で
 * 再適用</b>される。そのため Look &amp; Feel のライブ切替 (再起動不要) を行っても
 * 角丸・アクセント・スクロールバー等の調整が失われない。</p>
 *
 * <p>非 FlatLaf な L&amp;F (System / Nimbus) ではこれらのキーは無視されるため、
 * 安全に一度だけ登録しておけばよい。起動時 ({@link UmlApp}) から
 * {@link #installGlobalDefaults()} を呼ぶ。</p>
 */
final class UiTheme {

    /** VS Code の既定アクセント (青) に寄せたアクセントカラー。 */
    static final Color ACCENT = new Color(0x007ACC);

    private UiTheme() {
    }

    /**
     * FlatLaf のグローバル既定値を登録する。Look &amp; Feel を適用する <b>前</b> に
     * 一度だけ呼ぶこと。以降の FlatLaf セットアップ全てに反映される。
     */
    static void installGlobalDefaults() {
        Map<String, String> d = new LinkedHashMap<>();

        // アクセントカラー: 選択・フォーカス・既定ボタン・タブ下線などが VS Code 青になる。
        d.put("@accentColor", "#007ACC");

        // 角丸を少し付けてモダンな印象にする (VS Code のコントロールに近い丸み)。
        d.put("Component.arc", "8");
        d.put("Button.arc", "10");
        d.put("TextComponent.arc", "8");
        d.put("CheckBox.arc", "4");
        d.put("ProgressBar.arc", "8");

        // フォーカスリングを細く上品にする。
        d.put("Component.focusWidth", "1");
        d.put("Component.innerFocusWidth", "0.5");

        // スクロールバーを細く・丸く・オーバーレイ風にする (VS Code のスリムなバー)。
        d.put("ScrollBar.width", "12");
        d.put("ScrollBar.thumbArc", "999");
        d.put("ScrollBar.thumbInsets", "2,2,2,2");
        d.put("ScrollBar.showButtons", "false");
        d.put("ScrollBar.track", "@background");

        // タブ: 高さを確保し区切り線を出して、エディタタブらしい見た目にする。
        d.put("TabbedPane.tabHeight", "30");
        d.put("TabbedPane.showTabSeparators", "true");
        d.put("TabbedPane.tabSeparatorsFullHeight", "false");
        d.put("TabbedPane.tabsOpaque", "false");
        d.put("TabbedPane.selectedBackground", "@background");

        // ツリー / リストの行を少し高くして、アイコン + テキストの可読性を上げる。
        d.put("Tree.rowHeight", "22");
        d.put("List.selectionArc", "6");

        // メニュー項目の選択も角丸にして統一感を出す。
        d.put("MenuItem.selectionArc", "6");
        d.put("MenuItem.selectionInsets", "0,4,0,4");

        FlatLaf.setGlobalExtraDefaults(d);
    }
}
