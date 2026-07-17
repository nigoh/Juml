// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import juml.core.formats.uml.DiagramStyle;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Setting クラスのユニットテスト。
 *
 * <p>2.0 で PAD 用のフィールド (フォント / 色 / ツールバー設定) は削除済み。
 * 現在保持しているのはウィンドウ位置・サイズと分割ペイン位置のみ。</p>
 */
public class SettingTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDefaultWindowSize() {
        Setting setting = new Setting();
        assertEquals(1200, setting.getWindowWidth());
        assertEquals(800, setting.getWindowHeight());
        assertEquals(-1, setting.getWindowX());
        assertEquals(-1, setting.getWindowY());
    }

    @Test
    public void testWindowStateProperties() {
        Setting setting = new Setting();
        setting.setWindowX(100);
        setting.setWindowY(200);
        setting.setWindowWidth(1024);
        setting.setWindowHeight(768);
        setting.setMainSplitLocation(300);
        setting.setLeftSplitLocation(400);

        assertEquals(100, setting.getWindowX());
        assertEquals(200, setting.getWindowY());
        assertEquals(1024, setting.getWindowWidth());
        assertEquals(768, setting.getWindowHeight());
        assertEquals(300, setting.getMainSplitLocation());
        assertEquals(400, setting.getLeftSplitLocation());
    }

    @Test
    public void testSaveAndLoad() throws IOException {
        Setting original = new Setting();
        original.setWindowX(123);
        original.setWindowY(456);
        original.setWindowWidth(1024);
        original.setWindowHeight(768);
        original.setMainSplitLocation(280);
        original.setLeftSplitLocation(150);
        original.setWindowLocationSaved(true);

        File file = tempFolder.newFile("settings.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertEquals(123, loaded.getWindowX());
        assertEquals(456, loaded.getWindowY());
        assertEquals(1024, loaded.getWindowWidth());
        assertEquals(768, loaded.getWindowHeight());
        assertEquals(280, loaded.getMainSplitLocation());
        assertEquals(150, loaded.getLeftSplitLocation());
        assertTrue("windowLocationSaved がラウンドトリップするべき", loaded.isWindowLocationSaved());
    }

    /** 旧設定ファイル (windowLocationSaved キー無し) は未保存扱い = 既定 false になる。 */
    @Test
    public void testWindowLocationSavedDefaultsFalseForOldFiles() {
        Setting fresh = new Setting();
        assertFalse("既定は未保存 (false)", fresh.isWindowLocationSaved());
    }

    @Test
    public void testDefaultStyleIsEmpty() {
        Setting setting = new Setting();
        DiagramStyle s = setting.getStyle();
        assertEquals("", s.getTheme());
        assertEquals("", s.getBackgroundColor());
        assertEquals("", s.getFontName());
        assertEquals(0, s.getFontSize());
        assertEquals(DiagramStyle.Direction.DEFAULT, s.getDirection());
        assertEquals(DiagramStyle.LineType.DEFAULT, s.getLineType());
        assertEquals(DiagramStyle.Shadowing.DEFAULT, s.getShadowing());
        assertEquals(0, s.getNodeSep());
        assertEquals(0, s.getRankSep());
        assertEquals("", s.getCustomSkinparam());
    }

    @Test
    public void testAutoFitOnRenderRoundTrip() throws IOException {
        assertTrue("既定は自動フィット ON", new Setting().isAutoFitOnRender());
        Setting original = new Setting();
        original.setAutoFitOnRender(false);
        File file = tempFolder.newFile("settings-autofit.xml");
        original.saveToFile(file);
        assertFalse("保存 → 読込で false が保持される",
                Setting.loadFromFile(file).isAutoFitOnRender());
    }

    @Test
    public void testAutoFitOnRenderDefaultsTrueWhenKeyMissing() throws IOException {
        // 旧バージョンが書いた (キーの無い) 設定を読んでも既定 ON になること。
        Setting original = new Setting();
        File file = tempFolder.newFile("settings-legacy.xml");
        original.saveToFile(file);
        // キー行を物理的に削って「未知バージョン」を再現。
        java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
        lines.removeIf(l -> l.contains("app.autoFitOnRender"));
        java.nio.file.Files.write(file.toPath(), lines);
        assertTrue("キーが無ければ既定 ON", Setting.loadFromFile(file).isAutoFitOnRender());
    }

    @Test
    public void testStyleRoundTrip() throws IOException {
        Setting original = new Setting();
        DiagramStyle style = new DiagramStyle();
        style.setTheme("cerulean");
        style.setBackgroundColor("#1E1E1E");
        style.setFontName("Helvetica");
        style.setFontSize(14);
        style.setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        style.setLineType(DiagramStyle.LineType.ORTHO);
        style.setShadowing(DiagramStyle.Shadowing.OFF);
        style.setNodeSep(45);
        style.setRankSep(65);
        style.setCustomSkinparam("skinparam shadowing false\n");
        original.setStyle(style);

        File file = tempFolder.newFile("settings-style.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        DiagramStyle out = loaded.getStyle();
        assertEquals("cerulean", out.getTheme());
        assertEquals("#1E1E1E", out.getBackgroundColor());
        assertEquals("Helvetica", out.getFontName());
        assertEquals(14, out.getFontSize());
        assertEquals(DiagramStyle.Direction.LEFT_TO_RIGHT, out.getDirection());
        assertEquals(DiagramStyle.LineType.ORTHO, out.getLineType());
        assertEquals(DiagramStyle.Shadowing.OFF, out.getShadowing());
        assertEquals(45, out.getNodeSep());
        assertEquals(65, out.getRankSep());
        assertEquals("skinparam shadowing false\n", out.getCustomSkinparam());
    }

    @Test
    public void testLoadWithoutStyleKeysUsesDefaults() throws IOException {
        // 旧バージョンが書き出した style.* キー無しの XML を読んでも、
        // デフォルトスタイルで起動できることを確認する。
        File file = tempFolder.newFile("legacy-no-style.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"windowWidth\">1024</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        DiagramStyle s = loaded.getStyle();
        assertEquals("", s.getTheme());
        assertEquals(DiagramStyle.Direction.DEFAULT, s.getDirection());
    }

    @Test
    public void testSequenceCommentDefaults() {
        Setting s = new Setting();
        assertTrue(s.isSequenceShowComments());
        assertEquals("INLINE", s.getSequenceCommentStyle());
        // 新しい既定: AT_CALL_SITE + qualifyMethodNames=true
        assertEquals("AT_CALL_SITE", s.getSequenceCommentPlacement());
        assertTrue(s.isSequenceQualifyMethodNames());
    }

    @Test
    public void testSequenceCommentRoundTrip() throws IOException {
        Setting original = new Setting();
        original.setSequenceShowComments(false);
        original.setSequenceCommentStyle("NOTE");
        original.setSequenceCommentPlacement("PARTICIPANT_TOP");
        original.setSequenceQualifyMethodNames(false);

        File file = tempFolder.newFile("settings-seq.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertFalse(loaded.isSequenceShowComments());
        assertEquals("NOTE", loaded.getSequenceCommentStyle());
        assertEquals("PARTICIPANT_TOP", loaded.getSequenceCommentPlacement());
        assertFalse(loaded.isSequenceQualifyMethodNames());
    }

    @Test
    public void testSequenceActivityDetailDefaults() {
        Setting s = new Setting();
        assertEquals(5, s.getSequenceMaxDepth());
        assertFalse(s.isSequenceShowCallArguments());
        assertTrue(s.isActivityExpandInlineCallbacks());
        assertTrue(s.isActivityShowLocalVars());
        assertTrue(s.isActivityShowAssignments());
        assertTrue(s.isActivityShowCallArguments());
        assertTrue(s.isActivityShowInlineComments());
    }

    @Test
    public void testSequenceMaxDepthClampsToRange() {
        Setting s = new Setting();
        s.setSequenceMaxDepth(0); // 0 = 無制限として許容
        assertEquals(0, s.getSequenceMaxDepth());
        s.setSequenceMaxDepth(-3); // 負値は 0 (無制限) に丸める
        assertEquals(0, s.getSequenceMaxDepth());
        s.setSequenceMaxDepth(99); // 上限 10 に丸める
        assertEquals(10, s.getSequenceMaxDepth());
    }

    @Test
    public void testSequenceActivityDetailRoundTrip() throws IOException {
        Setting original = new Setting();
        original.setSequenceMaxDepth(8);
        original.setSequenceShowCallArguments(true);
        original.setActivityExpandInlineCallbacks(false);
        original.setActivityShowLocalVars(false);
        original.setActivityShowAssignments(false);
        original.setActivityShowCallArguments(false);
        original.setActivityShowInlineComments(false);

        File file = tempFolder.newFile("settings-detail.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertEquals(8, loaded.getSequenceMaxDepth());
        assertTrue(loaded.isSequenceShowCallArguments());
        assertFalse(loaded.isActivityExpandInlineCallbacks());
        assertFalse(loaded.isActivityShowLocalVars());
        assertFalse(loaded.isActivityShowAssignments());
        assertFalse(loaded.isActivityShowCallArguments());
        assertFalse(loaded.isActivityShowInlineComments());
    }

    @Test
    public void testSequenceActivityDetailLegacyFallsBackToDefaults() throws IOException {
        // sequence.maxDepth / activity.* キーを持たない旧 XML を読んでも既定値で初期化される
        File file = tempFolder.newFile("legacy-no-detail.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"windowWidth\">1024</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        assertEquals(5, loaded.getSequenceMaxDepth());
        assertFalse(loaded.isSequenceShowCallArguments());
        assertTrue(loaded.isActivityExpandInlineCallbacks());
        assertTrue(loaded.isActivityShowLocalVars());
        assertTrue(loaded.isActivityShowAssignments());
        assertTrue(loaded.isActivityShowCallArguments());
        assertTrue(loaded.isActivityShowInlineComments());
    }

    @Test
    public void testSequenceCommentLegacyFallsBackToDefaults() throws IOException {
        // sequence.* キーを持たない旧 XML を読んでも既定値で初期化される
        File file = tempFolder.newFile("legacy-no-seq.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"windowWidth\">1024</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        assertTrue(loaded.isSequenceShowComments());
        assertEquals("INLINE", loaded.getSequenceCommentStyle());
        assertEquals("AT_CALL_SITE", loaded.getSequenceCommentPlacement());
        assertTrue(loaded.isSequenceQualifyMethodNames());
    }

    @Test
    public void testLoadIgnoresLegacyPadKeys() throws IOException {
        // 旧バージョンの XML に存在した PAD 専用キーを書き出して、読み込み時に
        // 無視されることを確認する (互換性: 既存ユーザの設定ファイルが壊れない)。
        File file = tempFolder.newFile("legacy.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"disableSaveMenu\">true</entry>"
                + "<entry key=\"editorFont.name\">Monospaced</entry>"
                + "<entry key=\"viewColor.rgb\">-16777216</entry>"
                + "<entry key=\"windowWidth\">999</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        // PAD 関連キーは無視され、ウィンドウ幅だけ反映される
        assertEquals(999, loaded.getWindowWidth());
    }

    @org.junit.Test
    public void testClassDiagramSettingsDefaults() {
        Setting s = new Setting();
        assertEquals("BALANCED", s.getClassDiagramLastPreset());
        assertTrue(s.isClassDiagramShowFields());
        assertTrue(s.isClassDiagramShowMethods());
        assertTrue(s.isClassDiagramShowAnnotations());
        assertFalse(s.isClassDiagramPublicOnly());
        assertFalse(s.isClassDiagramExcludeExternal());
        assertFalse(s.isClassDiagramMarkExternalSupertypes());
        assertFalse(s.isClassDiagramColorCodeRelations());
        assertEquals(0, s.getClassDiagramCommentMaxLength());
        assertEquals("Override,SuppressWarnings",
                s.getClassDiagramHiddenAnnotations());
    }

    @org.junit.Test
    public void testCommentMaxLengthLegacyDefaultMatchesFieldDefault()
            throws java.io.IOException {
        // classDiagram.commentMaxLength キーを持たない旧 XML を読んでも、
        // 新規 Setting と同じ既定値 (0 = 無制限) になること (round-trip 既定の一致)。
        File file = tempFolder.newFile("legacy-no-commentmax.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"windowWidth\">1024</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        assertEquals(new Setting().getClassDiagramCommentMaxLength(),
                loaded.getClassDiagramCommentMaxLength());
        assertEquals(0, loaded.getClassDiagramCommentMaxLength());
    }

    @org.junit.Test
    public void testClassDiagramSettingsRoundTrip() throws java.io.IOException {
        Setting s = new Setting();
        s.setClassDiagramLastPreset("MINIMAL");
        s.setClassDiagramShowFields(false);
        s.setClassDiagramShowMethods(true);
        s.setClassDiagramShowAnnotations(false);
        s.setClassDiagramPublicOnly(true);
        s.setClassDiagramExcludeExternal(true);
        s.setClassDiagramColorCodeRelations(true);
        s.setClassDiagramHideEmptyMembers(true);
        s.setClassDiagramHideUnlinked(true);
        s.setClassDiagramCommentMaxLength(0);
        s.setClassDiagramHiddenAnnotations("Override,Nullable,NonNull");

        java.io.File file = java.io.File.createTempFile("cd-settings", ".xml");
        file.deleteOnExit();
        s.saveToFile(file);
        Setting loaded = Setting.loadFromFile(file);

        assertEquals("MINIMAL", loaded.getClassDiagramLastPreset());
        assertFalse(loaded.isClassDiagramShowFields());
        assertTrue(loaded.isClassDiagramShowMethods());
        assertFalse(loaded.isClassDiagramShowAnnotations());
        assertTrue(loaded.isClassDiagramPublicOnly());
        assertTrue(loaded.isClassDiagramExcludeExternal());
        assertTrue(loaded.isClassDiagramColorCodeRelations());
        assertTrue(loaded.isClassDiagramHideEmptyMembers());
        assertTrue(loaded.isClassDiagramHideUnlinked());
        assertEquals(0, loaded.getClassDiagramCommentMaxLength());
        assertEquals("Override,Nullable,NonNull",
                loaded.getClassDiagramHiddenAnnotations());
    }

    @org.junit.Test
    public void testClassDiagramDensityTogglesDefaultOffAndBackCompat() throws java.io.IOException {
        // 新既定は false。旧 XML (キー無し) を読んでも false へフォールバックする。
        Setting fresh = new Setting();
        assertFalse(fresh.isClassDiagramHideEmptyMembers());
        assertFalse(fresh.isClassDiagramHideUnlinked());

        java.util.Properties p = new java.util.Properties();
        java.io.File legacy = java.io.File.createTempFile("cd-legacy", ".xml");
        legacy.deleteOnExit();
        try (java.io.OutputStream os = new java.io.FileOutputStream(legacy)) {
            p.storeToXML(os, "legacy without density keys");
        }
        Setting loaded = Setting.loadFromFile(legacy);
        assertFalse(loaded.isClassDiagramHideEmptyMembers());
        assertFalse(loaded.isClassDiagramHideUnlinked());
    }

    @org.junit.Test
    public void testClassDiagramSettingsCommentMaxLengthClampsNegative() {
        Setting s = new Setting();
        s.setClassDiagramCommentMaxLength(-5);
        assertEquals(0, s.getClassDiagramCommentMaxLength());
    }

    @Test
    public void testAppSettingsDefaults() {
        Setting s = new Setting();
        assertEquals("FLATLAF_LIGHT", s.getLookAndFeel());
        assertFalse(s.isRestoreLastProjectOnStartup());
    }

    @Test
    public void testAppSettingsRoundTrip() throws IOException {
        Setting original = new Setting();
        original.setLookAndFeel("NIMBUS");
        original.setRestoreLastProjectOnStartup(true);

        File file = tempFolder.newFile("settings-app.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertEquals("NIMBUS", loaded.getLookAndFeel());
        assertTrue(loaded.isRestoreLastProjectOnStartup());
    }

    @Test
    public void testLanguageDefaultIsJapanese() {
        Setting s = new Setting();
        assertEquals("ja", s.getLanguage());
    }

    @Test
    public void testLanguageSetterNormalizes() {
        Setting s = new Setting();
        s.setLanguage("en");
        assertEquals("en", s.getLanguage());
        s.setLanguage("EN");
        assertEquals("en", s.getLanguage());
        // 未知 / null / 空は日本語に丸める
        s.setLanguage("fr");
        assertEquals("ja", s.getLanguage());
        s.setLanguage(null);
        assertEquals("ja", s.getLanguage());
        s.setLanguage("");
        assertEquals("ja", s.getLanguage());
    }

    @Test
    public void testLanguageRoundTrip() throws IOException {
        Setting original = new Setting();
        original.setLanguage("en");

        File file = tempFolder.newFile("settings-lang.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertEquals("en", loaded.getLanguage());
    }

    @Test
    public void testLanguageLegacyFallsBackToJapanese() throws IOException {
        // app.language キーを持たない旧 XML を読んでも既定 (日本語) で初期化される
        File file = tempFolder.newFile("legacy-no-lang.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"windowWidth\">1024</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        assertEquals("ja", loaded.getLanguage());
    }

    @Test
    public void testLookAndFeelEmptyFallsBackToDefault() {
        Setting s = new Setting();
        s.setLookAndFeel("");
        assertEquals("FLATLAF_LIGHT", s.getLookAndFeel());
        s.setLookAndFeel(null);
        assertEquals("FLATLAF_LIGHT", s.getLookAndFeel());
    }

    @Test
    public void testDiagramRenderQualityDefaultIsAuto() {
        Setting s = new Setting();
        assertEquals("AUTO", s.getDiagramRenderQuality());
    }

    @Test
    public void testDiagramRenderQualitySetterFallsBackToAuto() {
        Setting s = new Setting();
        s.setDiagramRenderQuality("HIGH");
        assertEquals("HIGH", s.getDiagramRenderQuality());
        s.setDiagramRenderQuality("");
        assertEquals("AUTO", s.getDiagramRenderQuality());
        s.setDiagramRenderQuality(null);
        assertEquals("AUTO", s.getDiagramRenderQuality());
    }

    @Test
    public void testDiagramRenderQualityRoundTrip() throws IOException {
        Setting original = new Setting();
        original.setDiagramRenderQuality("ULTRA");

        File file = tempFolder.newFile("settings-render.xml");
        original.saveToFile(file);

        Setting loaded = Setting.loadFromFile(file);
        assertEquals("ULTRA", loaded.getDiagramRenderQuality());
    }

    @Test
    public void testDiagramRenderQualityLegacyFallsBackToAuto() throws IOException {
        // app.diagramRenderQuality キーを持たない旧 XML を読んでも既定 (AUTO) で初期化される
        File file = tempFolder.newFile("legacy-no-render.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"windowWidth\">1024</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        assertEquals("AUTO", loaded.getDiagramRenderQuality());
    }

    @Test
    public void testAppSettingsLegacyFallsBackToDefaults() throws IOException {
        // app.* キーを持たない旧 XML を読んでも既定値で初期化される
        File file = tempFolder.newFile("legacy-no-app.xml");
        String legacy = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE properties SYSTEM "
                + "\"http://java.sun.com/dtd/properties.dtd\">"
                + "<properties>"
                + "<entry key=\"windowWidth\">1024</entry>"
                + "</properties>";
        java.nio.file.Files.write(file.toPath(),
                legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        Setting loaded = Setting.loadFromFile(file);
        assertEquals("FLATLAF_LIGHT", loaded.getLookAndFeel());
        assertFalse(loaded.isRestoreLastProjectOnStartup());
    }
}
