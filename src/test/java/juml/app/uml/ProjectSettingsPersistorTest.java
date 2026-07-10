// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.Setting;
import juml.core.formats.uml.DiagramStyle;
import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectSettingsPersistorTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private juml.ProjectRepository repo;
    private Connection conn;
    private DiagramStyle savedGlobalStyle;

    @Before
    public void setUp() throws Exception {
        // ProjectRepository をインメモリ DB に差し替え、永続層を汚さずラウンドトリップを検証する。
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("PRAGMA foreign_keys = ON");
        }
        Constructor<juml.ProjectRepository> ctor =
                juml.ProjectRepository.class.getDeclaredConstructor(Connection.class);
        ctor.setAccessible(true);
        repo = ctor.newInstance(conn);
        Method init = juml.ProjectRepository.class.getDeclaredMethod("createSchema", Connection.class);
        init.setAccessible(true);
        init.invoke(null, conn);
        Field f = juml.ProjectRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, repo);
        // PlantUmlRenderer のスタイルはグローバル状態なのでテスト前後で退避・復元する。
        savedGlobalStyle = PlantUmlRenderer.getStyle();
    }

    @After
    public void tearDown() throws Exception {
        PlantUmlRenderer.setStyle(savedGlobalStyle);
        if (repo != null) {
            repo.close();
        }
        Field f = juml.ProjectRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    @Test
    public void restoreAndPersist_withNullRoot_isNoOp() {
        AtomicBoolean called = new AtomicBoolean(false);
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> null, () -> called.set(true));
        p.restoreAndPersist(null);
        assertFalse("null root では onStyleRestored を呼ばないこと", called.get());
    }

    @Test
    public void saveCurrentProjectSettings_withNullRoot_isNoOp() {
        // null root では設定サプライヤすら参照せず即 return すること
        AtomicBoolean supplierQueried = new AtomicBoolean(false);
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> {
                    supplierQueried.set(true);
                    return null;
                }, () -> {});
        p.saveCurrentProjectSettings(null);
        assertFalse("null root では保存処理を行わないこと", supplierQueried.get());
    }

    @Test
    public void restoreAndPersist_withNullSetting_doesNotThrow() {
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> null, () -> {});
        // 未知プロジェクト (存在しないディレクトリ) でも例外が出ないこと
        p.restoreAndPersist(new File("/nonexistent/project"));
    }

    @Test
    public void restoreAndPersist_roundtrip_restoresStyleAndCallsCallback() throws Exception {
        File root = tempDir.newFolder("Roundtrip");
        repo.touch(root);
        Map<String, String> saved = new LinkedHashMap<>();
        saved.put("style.theme", "crt-green");
        saved.put("style.fontSize", "18");
        saved.put("style.direction", DiagramStyle.Direction.LEFT_TO_RIGHT.name());
        repo.saveSettings(root, saved);

        Setting setting = new Setting();
        AtomicBoolean restored = new AtomicBoolean(false);
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> setting, () -> restored.set(true));
        p.restoreAndPersist(root);

        DiagramStyle style = setting.getStyle();
        assertEquals("テーマが復元されること", "crt-green", style.getTheme());
        assertEquals("フォントサイズが復元されること", 18, style.getFontSize());
        assertEquals("方向 enum が復元されること",
                DiagramStyle.Direction.LEFT_TO_RIGHT, style.getDirection());
        assertTrue("復元後に onStyleRestored が呼ばれること", restored.get());
    }

    @Test
    public void restoreAndPersist_invalidEnumValue_isIgnored() throws Exception {
        File root = tempDir.newFolder("BadEnum");
        repo.touch(root);
        Map<String, String> saved = new LinkedHashMap<>();
        saved.put("style.theme", "amber");
        saved.put("style.direction", "BOGUS_DIRECTION"); // 不正 enum 値
        repo.saveSettings(root, saved);

        Setting setting = new Setting();
        AtomicBoolean restored = new AtomicBoolean(false);
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> setting, () -> restored.set(true));
        p.restoreAndPersist(root);

        DiagramStyle style = setting.getStyle();
        assertEquals("有効なキーは復元されること", "amber", style.getTheme());
        assertEquals("不正 enum 値は無視され既定のままであること",
                DiagramStyle.Direction.DEFAULT, style.getDirection());
        assertTrue("不正値があっても onStyleRestored は呼ばれること", restored.get());
    }

    @Test
    public void restoreAndPersist_restoresSequenceAndActivityDetailSettings() throws Exception {
        File root = tempDir.newFolder("DetailRestore");
        repo.touch(root);
        Map<String, String> saved = new LinkedHashMap<>();
        saved.put("sequence.maxDepth", "7");
        saved.put("sequence.showCallArguments", "true");
        saved.put("activity.expandInlineCallbacks", "false");
        saved.put("activity.showLocalVars", "false");
        saved.put("activity.showAssignments", "false");
        saved.put("activity.showCallArguments", "false");
        saved.put("activity.showInlineComments", "false");
        repo.saveSettings(root, saved);

        Setting setting = new Setting();
        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> setting, () -> {});
        p.restoreAndPersist(root);

        assertEquals("シーケンス図の展開深さが復元されること", 7, setting.getSequenceMaxDepth());
        assertTrue("シーケンス図の引数表示フラグが復元されること",
                setting.isSequenceShowCallArguments());
        assertFalse("コールバック展開フラグが復元されること",
                setting.isActivityExpandInlineCallbacks());
        assertFalse("ローカル変数表示フラグが復元されること", setting.isActivityShowLocalVars());
        assertFalse("代入表示フラグが復元されること", setting.isActivityShowAssignments());
        assertFalse("引数表示フラグが復元されること", setting.isActivityShowCallArguments());
        assertFalse("インラインコメント表示フラグが復元されること",
                setting.isActivityShowInlineComments());
    }

    @Test
    public void saveCurrentProjectSettings_persistsSequenceAndActivityDetailSettings()
            throws Exception {
        File root = tempDir.newFolder("DetailSave");
        repo.touch(root);
        Setting setting = new Setting();
        setting.setSequenceMaxDepth(9);
        setting.setSequenceShowCallArguments(true);
        setting.setActivityExpandInlineCallbacks(false);
        setting.setActivityShowLocalVars(false);
        setting.setActivityShowAssignments(false);
        setting.setActivityShowCallArguments(false);
        setting.setActivityShowInlineComments(false);

        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                () -> setting, () -> {});
        p.saveCurrentProjectSettings(root);

        Map<String, String> loaded = repo.loadSettings(root);
        assertEquals("シーケンス図の展開深さが保存されること", "9", loaded.get("sequence.maxDepth"));
        assertEquals("コールバック展開フラグが保存されること",
                "false", loaded.get("activity.expandInlineCallbacks"));
        assertEquals("ローカル変数表示フラグが保存されること",
                "false", loaded.get("activity.showLocalVars"));
        assertEquals("代入表示フラグが保存されること",
                "false", loaded.get("activity.showAssignments"));
        assertEquals("引数表示フラグが保存されること",
                "false", loaded.get("activity.showCallArguments"));
        assertEquals("シーケンス図の引数表示フラグが保存されること",
                "true", loaded.get("sequence.showCallArguments"));
        assertEquals("インラインコメント表示フラグが保存されること",
                "false", loaded.get("activity.showInlineComments"));
    }

    @Test
    public void saveCurrentProjectSettings_persistsStyleToRepository() throws Exception {
        File root = tempDir.newFolder("SaveTarget");
        repo.touch(root);
        DiagramStyle style = new DiagramStyle();
        style.setTheme("solarized");
        style.setFontSize(13);
        PlantUmlRenderer.setStyle(style);

        ProjectSettingsPersistor p = new ProjectSettingsPersistor(
                Setting::new, () -> {});
        p.saveCurrentProjectSettings(root);

        Map<String, String> loaded = repo.loadSettings(root);
        assertEquals("現在のスタイルがプロジェクト設定へ保存されること",
                "solarized", loaded.get("style.theme"));
        assertEquals("フォントサイズも保存されること", "13", loaded.get("style.fontSize"));
    }
}
