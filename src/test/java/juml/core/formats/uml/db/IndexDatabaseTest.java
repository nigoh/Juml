// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link IndexDatabase} のオープン・スキーマ初期化・schema_version 不一致時の
 * 退避＋再構築の動作確認。
 */
public class IndexDatabaseTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testOpenCreatesDbAndInitializesMeta() throws Exception {
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/some/project", "1.6-test")) {
            assertTrue("db file should be created", dbFile.exists());
            assertEquals(Integer.toString(SchemaInitializer.SCHEMA_VERSION),
                    db.getMeta("schema_version"));
            assertEquals("/some/project", db.getMeta("project_root"));
            assertEquals("1.6-test", db.getMeta("tool_version"));
            assertNotNull(db.getMeta("created_at"));
            assertNotNull(db.getMeta("updated_at"));
        }
    }

    @Test
    public void testReopenIsIdempotent() throws Exception {
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "v")) {
            db.putMeta("custom_key", "custom_value");
        }

        try (IndexDatabase db2 = IndexDatabase.openOrCreate(dbFile, "/p", "v")) {
            // 既存 DB を素通しで開けて、書き込んだ値が残っている
            assertEquals("custom_value", db2.getMeta("custom_key"));
            assertEquals(Integer.toString(SchemaInitializer.SCHEMA_VERSION),
                    db2.getMeta("schema_version"));
        }
    }

    @Test
    public void testSchemaMismatchDiscardsOldDbAndRebuilds() throws Exception {
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        // 古いバージョンを偽装した DB を作る
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "v")) {
            db.putMeta("schema_version", "999");
            db.putMeta("legacy_marker", "should_be_discarded");
        }

        // 再オープン: schema_version=999 != current で退避＋再構築されるはず
        try (IndexDatabase db2 = IndexDatabase.openOrCreate(dbFile, "/p", "v")) {
            assertEquals(Integer.toString(SchemaInitializer.SCHEMA_VERSION),
                    db2.getMeta("schema_version"));
            assertNull("legacy marker must be wiped after rebuild",
                    db2.getMeta("legacy_marker"));
        }

        // 退避ファイルが残っている
        File[] siblings = dbFile.getParentFile().listFiles();
        assertNotNull(siblings);
        boolean foundDiscarded = false;
        for (File f : siblings) {
            if (f.getName().startsWith("index.db.discarded-")) {
                foundDiscarded = true;
                break;
            }
        }
        assertTrue("discarded backup must exist", foundDiscarded);
    }

    @Test
    public void testMissingMetaTableTreatedAsMismatch() throws Exception {
        File cacheDir = tmp.newFolder("cache");
        File dbFile = new File(cacheDir, "index.db");

        // meta テーブルを持たない最小限の SQLite DB を手動で作る
        Class.forName("org.sqlite.JDBC");
        try (Connection conn = java.sql.DriverManager.getConnection(
                "jdbc:sqlite:" + dbFile.getAbsolutePath());
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE foo (x INTEGER)");
        }
        assertTrue(dbFile.exists());

        // openOrCreate は meta が無いことを検出して退避＋再構築する
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "v")) {
            assertEquals(Integer.toString(SchemaInitializer.SCHEMA_VERSION),
                    db.getMeta("schema_version"));
        }
    }

    @Test
    public void testWalModeIsApplied() throws Exception {
        File dbFile = new File(tmp.newFolder("cache"), "index.db");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, "/p", "v");
             Statement st = db.connection().createStatement()) {
            try (var rs = st.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase());
            }
        }
    }

    @Test
    public void testResolveDbFileUsesShortHash() throws IOException {
        File baseDir = tmp.newFolder("base");
        File root = tmp.newFolder("proj");

        File dbFile = DbBootstrap.resolveDbFile(baseDir, root);
        assertEquals("index.db", dbFile.getName());
        assertEquals(baseDir, dbFile.getParentFile().getParentFile());
        // shortHash は 16 文字
        assertEquals(16, dbFile.getParentFile().getName().length());
    }

    @Test
    public void testResolveDbFileStableAcrossFileChanges() throws Exception {
        File baseDir = tmp.newFolder("base");
        File root = tmp.newFolder("proj");

        // ファイル追加・変更しても同じディレクトリが返る (= ルート canonical path だけで決まる)
        File a = new File(root, "a.txt");
        try (var out = new java.io.FileOutputStream(a)) {
            out.write("AAA".getBytes());
        }
        File first = DbBootstrap.resolveDbFile(baseDir, root);
        try (var out = new java.io.FileOutputStream(a)) {
            out.write("BBBBBBBB".getBytes());
        }
        File second = DbBootstrap.resolveDbFile(baseDir, root);
        assertEquals(first.getAbsolutePath(), second.getAbsolutePath());
    }
}
