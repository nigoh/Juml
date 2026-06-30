// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

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
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.Assert.*;

public class ProjectRepositoryTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private ProjectRepository repo;
    private Connection conn;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("PRAGMA foreign_keys = ON");
        }
        Constructor<ProjectRepository> ctor =
                ProjectRepository.class.getDeclaredConstructor(Connection.class);
        ctor.setAccessible(true);
        repo = ctor.newInstance(conn);
        Method init = ProjectRepository.class.getDeclaredMethod("createSchema", Connection.class);
        init.setAccessible(true);
        init.invoke(null, conn);
        Field f = ProjectRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, repo);
    }

    @After
    public void tearDown() throws Exception {
        if (repo != null) repo.close();
        Field f = ProjectRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    @Test
    public void touch_registersProject() throws Exception {
        File root = tempDir.newFolder("MyApp");
        repo.touch(root);
        List<ProjectRecord> recent = repo.listRecent(10);
        assertEquals(1, recent.size());
        assertEquals("MyApp", recent.get(0).getName());
    }

    @Test
    public void touch_updatesLastOpenedAt() throws Exception {
        File root = tempDir.newFolder("App");
        repo.touch(root);
        long first = repo.listRecent(1).get(0).getLastOpenedAt();
        Thread.sleep(5);
        repo.touch(root);
        long second = repo.listRecent(1).get(0).getLastOpenedAt();
        assertTrue(second >= first);
        assertEquals("UPSERT なので件数は増えない", 1, repo.listRecent(10).size());
    }

    @Test
    public void saveAndLoadSettings_roundTrip() throws Exception {
        File root = tempDir.newFolder("AppB");
        repo.touch(root);
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("style.theme", "crt-green");
        settings.put("classDiagram.showFields", "false");
        repo.saveSettings(root, settings);

        Map<String, String> loaded = repo.loadSettings(root);
        assertEquals("crt-green", loaded.get("style.theme"));
        assertEquals("false", loaded.get("classDiagram.showFields"));
    }

    @Test
    public void loadSettings_emptyWhenNoProject() throws Exception {
        File root = tempDir.newFolder("Unknown");
        assertTrue(repo.loadSettings(root).isEmpty());
    }

    @Test
    public void listRecent_orderedByLastOpenedDescending() throws Exception {
        File a = tempDir.newFolder("Alpha");
        File b = tempDir.newFolder("Beta");
        repo.touch(a);
        Thread.sleep(5);
        repo.touch(b);
        List<ProjectRecord> recent = repo.listRecent(10);
        assertEquals("Beta", recent.get(0).getName());
        assertEquals("Alpha", recent.get(1).getName());
    }

    @Test
    public void listRecent_respectsLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            repo.touch(tempDir.newFolder("proj" + i));
        }
        assertEquals(3, repo.listRecent(3).size());
    }

    @Test
    public void deleteById_removesProjectFromRecent() throws Exception {
        File root = tempDir.newFolder("ToDelete");
        repo.touch(root);
        long id = repo.listRecent(10).get(0).getId();

        assertTrue("削除に成功するはず", repo.deleteById(id));
        assertTrue("一覧から消えているはず", repo.listRecent(10).isEmpty());
    }

    @Test
    public void deleteById_returnsFalseForUnknownId() throws Exception {
        assertFalse(repo.deleteById(99999L));
    }

    @Test
    public void deleteByFile_removesMatchingProject() throws Exception {
        File a = tempDir.newFolder("KeepMe");
        File b = tempDir.newFolder("DropMe");
        repo.touch(a);
        repo.touch(b);

        assertTrue(repo.delete(b));
        List<ProjectRecord> recent = repo.listRecent(10);
        assertEquals(1, recent.size());
        assertEquals("KeepMe", recent.get(0).getName());
    }

    @Test
    public void deleteById_cascadesToProjectSettings() throws Exception {
        File root = tempDir.newFolder("WithSettings");
        repo.touch(root);
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("style.theme", "crt-green");
        repo.saveSettings(root, settings);
        long id = repo.listRecent(10).get(0).getId();

        repo.deleteById(id);

        // 同名フォルダを再登録すると新しい id になり、設定は引き継がれない (CASCADE 削除済み)
        repo.touch(root);
        assertTrue("関連設定も削除されているはず", repo.loadSettings(root).isEmpty());
    }

    @Test
    public void saveSettings_overwritesExistingKey() throws Exception {
        File root = tempDir.newFolder("App");
        repo.touch(root);
        Map<String, String> s1 = new LinkedHashMap<>();
        s1.put("style.theme", "old");
        repo.saveSettings(root, s1);
        Map<String, String> s2 = new LinkedHashMap<>();
        s2.put("style.theme", "new");
        repo.saveSettings(root, s2);
        assertEquals("new", repo.loadSettings(root).get("style.theme"));
    }
}
