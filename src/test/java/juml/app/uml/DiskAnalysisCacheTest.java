// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.db.DbBootstrap;
import juml.util.ProgressListener;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiskAnalysisCache} が Stage A 情報を round-trip し、旧 TSV
 * ディレクトリを {@code .legacy-<ts>/} へ退避することを検証する。
 */
public class DiskAnalysisCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testSaveAndLoadRoundTrip() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File srcDir = new File(projectRoot, "src/main/java/com/example");
        assertTrue(srcDir.mkdirs());
        File source = new File(srcDir, "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("package com.example; public class Hello {}");
        }

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        ClassIndex index = new ClassIndex();
        JavaClassInfo hello = makeClass("com.example", "Hello");
        index.put(hello, source, ":app");
        List<JavaClassInfo> classes = new ArrayList<>(Arrays.asList(hello));

        cache.save(projectRoot, classes, index);

        // DB ファイルが置かれる
        File dbFile = DbBootstrap.resolveDbFile(base, projectRoot);
        assertTrue("index.db should be created", dbFile.isFile());

        Optional<DiskAnalysisCache.Snapshot> snap = cache.load(projectRoot, ProgressListener.silent());
        assertTrue("snapshot must be present after save", snap.isPresent());
        assertEquals(1, snap.get().getClasses().size());
        assertEquals("com.example.Hello", snap.get().getClasses().get(0).getQualifiedName());
        // ClassIndex はモジュール紐付けを保つ
        assertEquals(":app", snap.get().getIndex().module("com.example.Hello").orElse(null));
        // ソースファイルも復元される
        File restoredSrc = snap.get().getIndex().source("com.example.Hello").orElse(null);
        assertNotNull(restoredSrc);
        assertEquals(source.getCanonicalPath(), restoredSrc.getCanonicalPath());
    }

    @Test
    public void testLoadReturnsEmptyWhenNoDb() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        DiskAnalysisCache cache = new DiskAnalysisCache(base);

        Optional<DiskAnalysisCache.Snapshot> snap = cache.load(projectRoot, null);
        assertFalse(snap.isPresent());
    }

    @Test
    public void testInvalidateDeletesDb() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File source = new File(tmp.newFolder("src"), "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("x");
        }
        ClassIndex idx = new ClassIndex();
        idx.put(makeClass("p", "Hello"), source, null);

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        cache.save(projectRoot, idx.headers(), idx);
        File dbFile = DbBootstrap.resolveDbFile(base, projectRoot);
        assertTrue(dbFile.isFile());

        cache.invalidate(projectRoot);
        assertFalse("db file must be deleted", dbFile.exists());
        assertFalse(cache.load(projectRoot, null).isPresent());
    }

    @Test
    public void testReSaveReplacesContent() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File src1 = new File(tmp.newFolder("src1"), "Hello.java");
        try (FileWriter w = new FileWriter(src1)) {
            w.write("x");
        }
        File src2 = new File(tmp.newFolder("src2"), "Bye.java");
        try (FileWriter w = new FileWriter(src2)) {
            w.write("y");
        }

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        ClassIndex idx1 = new ClassIndex();
        idx1.put(makeClass("com.example", "Hello"), src1, null);
        cache.save(projectRoot, idx1.headers(), idx1);
        assertEquals(1, cache.load(projectRoot, null).get().getClasses().size());

        ClassIndex idx2 = new ClassIndex();
        idx2.put(makeClass("com.example", "Bye"), src2, null);
        cache.save(projectRoot, idx2.headers(), idx2);

        Optional<DiskAnalysisCache.Snapshot> snap = cache.load(projectRoot, null);
        assertTrue(snap.isPresent());
        Set<String> qns = new HashSet<>();
        for (JavaClassInfo c : snap.get().getClasses()) {
            qns.add(c.getQualifiedName());
        }
        assertEquals("re-save must replace, not append",
                new HashSet<>(Arrays.asList("com.example.Bye")), qns);
    }

    @Test
    public void testClassWithoutSourceFileIsSkipped() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");

        ClassIndex idx = new ClassIndex();
        // source ファイル無しのクラス (依存 JAR 由来などを想定)
        idx.put(makeClass("ext", "ExtClass"), null, null);

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        cache.save(projectRoot, idx.headers(), idx);

        // DB は作られるが classes 0 件 → load は empty
        Optional<DiskAnalysisCache.Snapshot> snap = cache.load(projectRoot, null);
        assertFalse(snap.isPresent());
    }

    @Test
    public void testLegacyTsvDirIsArchivedOnFirstUse() throws Exception {
        File base = tmp.newFolder("base");
        // 旧 TSV キャッシュを 1 個用意
        File legacy = new File(base, "deadbeefcafebabe");
        assertTrue(legacy.mkdir());
        try (FileWriter w = new FileWriter(new File(legacy, "manifest.txt"))) {
            w.write("cacheVersion=v1\n");
        }
        try (FileWriter w = new FileWriter(new File(legacy, "classes.tsv"))) {
            w.write("fake\n");
        }

        File projectRoot = tmp.newFolder("proj");
        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        // 最初の load() で legacy が退避される
        cache.load(projectRoot, ProgressListener.silent());

        assertFalse("legacy dir must be moved away", legacy.exists());
        File[] siblings = base.listFiles();
        assertNotNull(siblings);
        boolean foundArchive = false;
        for (File f : siblings) {
            if (f.isDirectory() && f.getName().startsWith(".legacy-")) {
                foundArchive = true;
                break;
            }
        }
        assertTrue("archive dir should exist", foundArchive);
    }

    /**
     * 陳腐化チェック: DB 保存後にソースを変更 (mtime/size) すると、
     * currentJavaFiles を渡した load はキャッシュミス (empty) を返す。
     */
    @Test
    public void staleSourceIsDetectedAsCacheMiss() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File srcDir = new File(projectRoot, "src");
        assertTrue(srcDir.mkdirs());
        File source = new File(srcDir, "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("package p; public class Hello {}");
        }

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        ClassIndex idx = new ClassIndex();
        idx.put(makeClass("p", "Hello"), source, null);
        cache.save(projectRoot, idx.headers(), idx);

        List<File> current = new ArrayList<>(Arrays.asList(source));
        // 変更なし → キャッシュヒット
        assertTrue("変更なしならヒット",
                cache.load(projectRoot, ProgressListener.silent(), current).isPresent());

        // ソースを書き換えて size/mtime を変える → ミス
        try (FileWriter w = new FileWriter(source)) {
            w.write("package p; public class Hello { void added() {} }");
        }
        source.setLastModified(source.lastModified() + 5000);
        assertFalse("ソース変更後はキャッシュミス",
                cache.load(projectRoot, ProgressListener.silent(), current).isPresent());
    }

    /** 陳腐化チェック: 新規ファイルが増えるとキャッシュミス。 */
    @Test
    public void addedSourceIsDetectedAsCacheMiss() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File srcDir = new File(projectRoot, "src");
        assertTrue(srcDir.mkdirs());
        File source = new File(srcDir, "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("package p; public class Hello {}");
        }
        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        ClassIndex idx = new ClassIndex();
        idx.put(makeClass("p", "Hello"), source, null);
        cache.save(projectRoot, idx.headers(), idx);

        // 新規ファイルを含むファイル一覧を渡す → ミス
        File added = new File(srcDir, "New.java");
        try (FileWriter w = new FileWriter(added)) {
            w.write("package p; public class New {}");
        }
        List<File> current = new ArrayList<>(Arrays.asList(source, added));
        assertFalse("新規ファイルがあればキャッシュミス",
                cache.load(projectRoot, ProgressListener.silent(), current).isPresent());
    }

    /**
     * 陳腐化チェック: {@code .aidl} 由来のクラスも KIND_JAVA で永続化されるため、
     * currentFiles に {@code .aidl} を含めればヒットし、含め忘れると (旧挙動)
     * DB 行が「削除された」と誤判定されて毎回ミスすることを示す回帰テスト。
     */
    @Test
    public void aidlSourceMatchesOnlyWhenIncludedInCurrentFiles() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File srcDir = new File(projectRoot, "aidl/p");
        assertTrue(srcDir.mkdirs());
        File aidl = new File(srcDir, "IFoo.aidl");
        try (FileWriter w = new FileWriter(aidl)) {
            w.write("package p; interface IFoo {}");
        }
        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        ClassIndex idx = new ClassIndex();
        idx.put(makeClass("p", "IFoo"), aidl, null);
        cache.save(projectRoot, idx.headers(), idx);

        // .aidl を含めればヒット
        assertTrue(".aidl を含めればキャッシュヒット",
                cache.load(projectRoot, ProgressListener.silent(),
                        new ArrayList<>(Arrays.asList(aidl))).isPresent());
        // .aidl を含め忘れると DB 行が deleted 扱いになりミス (回帰の再現)
        assertFalse(".aidl を含め忘れると毎回ミス",
                cache.load(projectRoot, ProgressListener.silent(),
                        new ArrayList<>()).isPresent());
    }

    /** currentJavaFiles を渡さない従来 API は陳腐化チェックせず常にヒット。 */
    @Test
    public void nullCurrentFilesSkipsStalenessCheck() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File source = new File(tmp.newFolder("src"), "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("x");
        }
        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        ClassIndex idx = new ClassIndex();
        idx.put(makeClass("p", "Hello"), source, null);
        cache.save(projectRoot, idx.headers(), idx);

        assertTrue("null なら陳腐化チェックせずヒット",
                cache.load(projectRoot, ProgressListener.silent(), null).isPresent());
    }

    private static JavaClassInfo makeClass(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setOrigin(JavaClassInfo.Origin.SOURCE);
        return c;
    }
}
