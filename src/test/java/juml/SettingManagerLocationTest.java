// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import juml.util.PathUtil;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 設定ファイルが「起動場所に依存しないユーザー単位の場所」へ保存されることを検証する。
 *
 * <p>以前は {@code user.dir}(カレントディレクトリ) 配下に置いていたため、起動する場所を
 * 変えるだけで設定が毎回初期値へ戻り、行った先々に {@code settings.xml} が作られ、
 * インストール先が書込不可なら保存自体できなかった。</p>
 */
public class SettingManagerLocationTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @After
    public void tearDown() {
        SettingManager.resetForTest();
        System.clearProperty(PathUtil.USER_DATA_DIR_PROPERTY);
    }

    @Test
    public void userDataDir_isNotTheWorkingDirectory() {
        // 既定の解決先が user.dir でないこと (これが本件の本質)。
        System.clearProperty(PathUtil.USER_DATA_DIR_PROPERTY);
        File dataDir = PathUtil.getUserDataDir();
        assertFalse("ユーザー領域が作業ディレクトリと同じであってはならない: " + dataDir,
                dataDir.getAbsolutePath().equals(PathUtil.getBasePath()));
    }

    @Test
    public void userDataDir_canBeOverriddenForTests() {
        File dir = tmp.getRoot();
        System.setProperty(PathUtil.USER_DATA_DIR_PROPERTY, dir.getAbsolutePath());
        assertEquals(dir.getAbsolutePath(), PathUtil.getUserDataDir().getAbsolutePath());
    }

    @Test
    public void initialize_writesSettingsIntoGivenDirectory() {
        File dir = new File(tmp.getRoot(), "nested/juml"); // 未作成の階層
        SettingManager.initialize(dir);
        File settings = new File(dir, "settings.xml");
        assertTrue("親ディレクトリを作って設定を書き出すこと: " + settings, settings.isFile());
    }

    @Test
    public void initialize_migratesLegacySettingsOnce() throws Exception {
        // 旧位置 (user.dir/settings.xml) に設定がある状態で、ユーザー領域にはまだ無い場合、
        // 内容を引き継いで新しい場所へ書き出すこと。旧ファイルは消さない。
        File legacy = new File(PathUtil.getBasePath(), "settings.xml");
        boolean legacyExisted = legacy.isFile();
        File backup = new File(tmp.getRoot(), "legacy-backup.xml");
        if (legacyExisted) {
            java.nio.file.Files.copy(legacy.toPath(), backup.toPath());
        }
        try {
            Setting seed = new Setting();
            seed.setLastExportDirectory(tmp.getRoot().getAbsolutePath());
            seed.saveToFile(legacy);

            File dir = new File(tmp.getRoot(), "userdata");
            SettingManager sm = SettingManager.initialize(dir);

            assertTrue("新しい場所へ書き出されること",
                    new File(dir, "settings.xml").isFile());
            assertEquals("旧設定の内容が引き継がれること",
                    tmp.getRoot().getAbsolutePath(),
                    sm.getSetting().getLastExportDirectory());
            assertTrue("旧ファイルは残す (他バージョンが参照しうる)", legacy.isFile());
        } finally {
            if (legacyExisted) {
                java.nio.file.Files.copy(backup.toPath(), legacy.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                java.nio.file.Files.deleteIfExists(legacy.toPath());
            }
        }
    }

    @Test
    public void initialize_prefersUserDataDirOverLegacy() throws Exception {
        // ユーザー領域に既に設定があれば、旧位置は見ない (移行は 1 度きり)。
        File legacy = new File(PathUtil.getBasePath(), "settings.xml");
        boolean legacyExisted = legacy.isFile();
        File backup = new File(tmp.getRoot(), "legacy-backup2.xml");
        if (legacyExisted) {
            java.nio.file.Files.copy(legacy.toPath(), backup.toPath());
        }
        try {
            Setting old = new Setting();
            old.setLastExportDirectory("/legacy/value");
            old.saveToFile(legacy);

            File dir = new File(tmp.getRoot(), "userdata2");
            assertTrue(dir.mkdirs());
            Setting current = new Setting();
            current.setLastExportDirectory("/current/value");
            current.saveToFile(new File(dir, "settings.xml"));

            SettingManager sm = SettingManager.initialize(dir);
            assertEquals("ユーザー領域の設定が優先されること",
                    "/current/value", sm.getSetting().getLastExportDirectory());
        } finally {
            if (legacyExisted) {
                java.nio.file.Files.copy(backup.toPath(), legacy.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                java.nio.file.Files.deleteIfExists(legacy.toPath());
            }
        }
    }
}
