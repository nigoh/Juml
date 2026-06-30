// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.dao.RefsDao;
import juml.core.formats.uml.db.ingest.RefsIngestor;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link RefsIngestor} → DB → {@link IndexReader#loadReferenceIndex} の
 * 双方向 round-trip と、caller ファイル差し替え時の CASCADE 削除を検証する。
 */
public class RefsRoundTripTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File newDbFile() throws Exception {
        return new File(tmp.newFolder("cache"), "index.db");
    }

    @Test
    public void testRefsRoundTrip() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            // caller クラスを classes テーブルに置く (RefsIngestor が file_id を引けるように)
            new IndexWriter(db.connection()).upsertFile(
                    "src/com/example/Caller.java", IndexWriter.KIND_JAVA, 1L, 1L,
                    null, null,
                    Collections.singletonList(simpleClass("com.example", "Caller")), null);

            // メモリ ReferenceIndex に 3 種類 (CALL / EXTENDS / TYPE_REFERENCE) を積む
            ReferenceIndex original = new ReferenceIndex();
            original.addReference(
                    ReferenceKey.ofMethod("com.example.Callee", "doIt"),
                    new ReferenceSite("com.example.Caller", "handle", "Caller.java", 12,
                            ReferenceSite.Kind.CALL));
            original.addReference(
                    ReferenceKey.ofClass("com.example.Base"),
                    new ReferenceSite("com.example.Caller", "", "Caller.java", 1,
                            ReferenceSite.Kind.EXTENDS));
            original.addReference(
                    ReferenceKey.ofField("com.example.Constants", "MAX"),
                    new ReferenceSite("com.example.Caller", "handle", "Caller.java", 20,
                            ReferenceSite.Kind.TYPE_REFERENCE));
            original.addUnresolved("UnknownSym");
            original.addUnresolved("AnotherUnknown");

            // DB へ ingest
            int inserted = RefsIngestor.ingest(db.connection(), original);
            assertEquals(3, inserted);
            assertEquals(3, new IndexReader(db.connection()).referenceCount());

            // DB から再構成
            IndexReader reader = new IndexReader(db.connection());
            ReferenceIndex restored = reader.loadReferenceIndex();

            assertEquals(3, restored.symbolCount());
            assertEquals(3, restored.totalSites());

            // 各キーの site が一致 (ファイルパスは files.path に置換される)
            List<ReferenceSite> callSites = restored.sites(
                    ReferenceKey.ofMethod("com.example.Callee", "doIt"));
            assertEquals(1, callSites.size());
            ReferenceSite cs = callSites.get(0);
            assertEquals("com.example.Caller", cs.getCallerFqn());
            assertEquals("handle", cs.getCallerMethod());
            assertEquals(12, cs.getLineHint());
            assertEquals(ReferenceSite.Kind.CALL, cs.getKind());
            assertEquals("src/com/example/Caller.java", cs.getFile());

            assertEquals(1, restored.sites(ReferenceKey.ofClass("com.example.Base")).size());
            assertEquals(ReferenceSite.Kind.EXTENDS,
                    restored.sites(ReferenceKey.ofClass("com.example.Base")).get(0).getKind());

            List<ReferenceSite> fieldSites = restored.sites(
                    ReferenceKey.ofField("com.example.Constants", "MAX"));
            assertEquals(1, fieldSites.size());
            assertEquals(ReferenceSite.Kind.TYPE_REFERENCE, fieldSites.get(0).getKind());

            assertEquals(new HashSet<>(Arrays.asList("UnknownSym", "AnotherUnknown")),
                    new HashSet<>(restored.unresolved()));
        }
    }

    @Test
    public void testSitesForClassAggregatesAcrossSymbols() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            new IndexWriter(db.connection()).upsertFile(
                    "src/Caller.java", IndexWriter.KIND_JAVA, 1L, 1L, null, null,
                    Collections.singletonList(simpleClass("com.example", "Caller")), null);

            ReferenceIndex idx = new ReferenceIndex();
            // 同じオーナー (com.example.Bar) に 3 種類の参照
            idx.addReference(ReferenceKey.ofClass("com.example.Bar"),
                    new ReferenceSite("com.example.Caller", "", "", -1, ReferenceSite.Kind.EXTENDS));
            idx.addReference(ReferenceKey.ofMethod("com.example.Bar", "x"),
                    new ReferenceSite("com.example.Caller", "m", "", -1, ReferenceSite.Kind.CALL));
            idx.addReference(ReferenceKey.ofField("com.example.Bar", "y"),
                    new ReferenceSite("com.example.Caller", "m", "", -1, ReferenceSite.Kind.TYPE_REFERENCE));

            RefsIngestor.ingest(db.connection(), idx);

            // DAO 直叩き: sitesForClass がクラス/メソッド/フィールドを横断する
            List<ReferenceSite> all = RefsDao.sitesForClass(db.connection(), "com.example.Bar");
            assertEquals(3, all.size());
            Set<ReferenceSite.Kind> kinds = new HashSet<>();
            for (ReferenceSite s : all) {
                kinds.add(s.getKind());
            }
            assertEquals(new HashSet<>(Arrays.asList(
                    ReferenceSite.Kind.EXTENDS,
                    ReferenceSite.Kind.CALL,
                    ReferenceSite.Kind.TYPE_REFERENCE)), kinds);

            // ReferenceIndex 経由でも一致 (= ReferenceIndex.sitesForClass と同等)
            ReferenceIndex restored = new IndexReader(db.connection()).loadReferenceIndex();
            assertEquals(3, restored.sitesForClass("com.example.Bar").size());
        }
    }

    @Test
    public void testReupsertCallerFileCascadesRefs() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            IndexWriter writer = new IndexWriter(db.connection());
            writer.upsertFile(
                    "src/Caller.java", IndexWriter.KIND_JAVA, 1L, 1L, null, null,
                    Collections.singletonList(simpleClass("com.example", "Caller")), null);

            ReferenceIndex idx = new ReferenceIndex();
            idx.addReference(ReferenceKey.ofMethod("com.example.Callee", "x"),
                    new ReferenceSite("com.example.Caller", "m", "", -1, ReferenceSite.Kind.CALL));
            idx.addReference(ReferenceKey.ofMethod("com.example.Callee", "y"),
                    new ReferenceSite("com.example.Caller", "m", "", -1, ReferenceSite.Kind.CALL));
            RefsIngestor.ingest(db.connection(), idx);

            assertEquals(2, new IndexReader(db.connection()).referenceCount());

            // caller のファイルを上書き (中身は同じ Caller クラスを再投入)
            // → 旧 file_id を持つ refs 行はすべて CASCADE で消える
            writer.upsertFile(
                    "src/Caller.java", IndexWriter.KIND_JAVA, 2L, 2L, null, null,
                    Collections.singletonList(simpleClass("com.example", "Caller")), null);

            assertEquals(
                    "refs originating from the replaced file must be cascade-deleted",
                    0, new IndexReader(db.connection()).referenceCount());
        }
    }

    @Test
    public void testIngestWithoutCallerClassRowKeepsFileIdNull() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        // classes テーブルに caller_qn の行が無いケース: file_id は引けないので NULL のまま入る。
        // CASCADE は効かないが、ImpactAnalyzer 系の機能には影響しない (file_id は診断情報)。
        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            ReferenceIndex idx = new ReferenceIndex();
            idx.addReference(ReferenceKey.ofMethod("com.example.Callee", "x"),
                    new ReferenceSite("com.example.GhostCaller", "m", "", -1, ReferenceSite.Kind.CALL));
            assertEquals(1, RefsIngestor.ingest(db.connection(), idx));

            ReferenceIndex restored = new IndexReader(db.connection()).loadReferenceIndex();
            assertEquals(1, restored.totalSites());
            ReferenceSite s = restored.sites(
                    ReferenceKey.ofMethod("com.example.Callee", "x")).get(0);
            assertEquals("com.example.GhostCaller", s.getCallerFqn());
            assertNotNull(s.getKind());
        }
    }

    @Test
    public void testEmptyIndexInsertsNothing() throws Exception {
        File dbFile = newDbFile();
        File projectRoot = tmp.newFolder("proj");

        try (IndexDatabase db = IndexDatabase.openOrCreate(dbFile, projectRoot.getAbsolutePath(), "test")) {
            assertEquals(0, RefsIngestor.ingest(db.connection(), new ReferenceIndex()));
            assertEquals(0, new IndexReader(db.connection()).referenceCount());
            assertTrue(new IndexReader(db.connection()).loadReferenceIndex().keys().isEmpty());
        }
    }

    private static JavaClassInfo simpleClass(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setOrigin(JavaClassInfo.Origin.SOURCE);
        return c;
    }
}
