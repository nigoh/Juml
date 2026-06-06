// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Messages i18nユーティリティのテスト。
 */
public class MessagesTest {

    @Test
    public void testGetExistingKey() {
        String result = Messages.get("menu.file");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertNotEquals("menu.file", result); // 実際の値を返すべき
    }

    @Test
    public void testGetMissingKeyReturnsFallback() {
        String result = Messages.get("this.key.does.not.exist.xyz");
        assertEquals("this.key.does.not.exist.xyz", result);
    }

    @Test
    public void testMenuKeys() {
        // 基本メニューキーの存在確認
        assertNotEquals("menu.file.new", Messages.get("menu.file.new"));
        assertNotEquals("menu.file.open", Messages.get("menu.file.open"));
        assertNotEquals("menu.file.save", Messages.get("menu.file.save"));
        assertNotEquals("menu.output.png", Messages.get("menu.output.png"));
        assertNotEquals("menu.output.svg", Messages.get("menu.output.svg"));
        assertNotEquals("menu.help.about", Messages.get("menu.help.about"));
    }

    @Test
    public void testNewFeatureKeys() {
        // UML 専用 GUI でも再利用するキー
        assertNotEquals("menu.file.print", Messages.get("menu.file.print"));
        assertNotEquals("menu.edit.copyDiagram", Messages.get("menu.edit.copyDiagram"));
        assertNotEquals("menu.output.pdf", Messages.get("menu.output.pdf"));
        assertNotEquals("menu.view.zoomIn", Messages.get("menu.view.zoomIn"));
        assertNotEquals("menu.view.zoomOut", Messages.get("menu.view.zoomOut"));
        assertNotEquals("menu.view.zoomFit", Messages.get("menu.view.zoomFit"));
        assertNotEquals("menu.view.zoomReset", Messages.get("menu.view.zoomReset"));
    }

    @Test
    public void testStatusKeys() {
        assertNotEquals("status.line", Messages.get("status.line"));
        assertNotEquals("status.column", Messages.get("status.column"));
        assertNotEquals("status.parseOk", Messages.get("status.parseOk"));
        assertNotEquals("status.zoom", Messages.get("status.zoom"));
    }

    /** 既定 (未設定) は日本語であること。要件: 「デフォルトは日本語」。 */
    @Test
    public void testDefaultLanguageIsJapanese() {
        Messages.setLanguage(null);
        assertEquals("ja", Messages.getLanguage());
        assertEquals("ファイル", Messages.get("menubar.file"));
    }

    /** 英語へ切り替えると、日本語環境であってもベース (英語) リソースが選ばれること。 */
    @Test
    public void testSwitchToEnglish() {
        Messages.setLanguage("en");
        assertEquals("en", Messages.getLanguage());
        assertEquals("File", Messages.get("menubar.file"));
        assertEquals("Settings", Messages.get("menubar.settings"));
    }

    /** 日本語へ切り替えると日本語リソースが選ばれること。 */
    @Test
    public void testSwitchToJapanese() {
        Messages.setLanguage("ja");
        assertEquals("ja", Messages.getLanguage());
        assertEquals("ファイル", Messages.get("menubar.file"));
        assertEquals("設定", Messages.get("menubar.settings"));
    }

    /** 未知の言語キーは日本語にフォールバックすること。 */
    @Test
    public void testUnknownLanguageFallsBackToJapanese() {
        Messages.setLanguage("fr");
        assertEquals("ja", Messages.getLanguage());
        assertEquals("ファイル", Messages.get("menubar.file"));
    }

    /** 他テストへ影響しないよう、既定 (日本語) に戻す。 */
    @After
    public void resetLanguage() {
        Messages.setLanguage("ja");
    }
}
