// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.util.ProgressListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ディスクキャッシュが記録する {@code mtime}/{@code size} は、<b>パースした内容と対になる
 * 走査時点の値</b>でなければならないことの回帰テスト。
 *
 * <p>保存時に採り直すと、パース中に編集されたファイルへ「新しい stat + 古い解析結果」を
 * 書いてしまう。次回ロードの陳腐化チェックは stat 一致で通ってしまうため、そのファイルは
 * <b>編集しても二度と再解析されず、古いクラス一覧が出続ける</b> (キャッシュを手動で
 * 消すまで直らない)。走査時点の値を書けば、パース中の編集は次回「変更あり」と判定される。</p>
 */
public class DiskAnalysisCacheScanStatTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static JavaClassInfo makeClass(String pkg, String simple) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(simple);
        c.setKind(JavaClassInfo.Kind.CLASS);
        return c;
    }

    /** ファイルを書き換え、mtime も確実に進める (秒精度のファイルシステム対策)。 */
    private static void modify(File f, String content) throws Exception {
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        f.setLastModified(f.lastModified() + 5_000L);
    }

    @Test
    public void editDuringParseIsDetectedOnTheNextLoad() throws Exception {
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File srcDir = new File(projectRoot, "src/main/java/com/example");
        assertTrue(srcDir.mkdirs());
        File source = new File(srcDir, "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("package com.example; public class Hello {}");
        }

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        List<File> sources = new ArrayList<>(Arrays.asList(source));

        // 1) 走査 → stat 採取 (ここまでがパース前)
        List<DiskAnalysisCache.SourceStat> scanned = DiskAnalysisCache.statAll(sources);

        // 2) パース中にユーザがファイルを編集する
        modify(source, "package com.example; public class Hello { void added() {} }");

        // 3) パース結果を保存 (stat は 1) の値を使う)
        ClassIndex index = new ClassIndex();
        JavaClassInfo hello = makeClass("com.example", "Hello");
        index.put(hello, source, ":app");
        cache.saveScanned(projectRoot, new ArrayList<>(Arrays.asList(hello)), index, scanned);

        // 4) 次回ロード: 編集後の状態と食い違うので、キャッシュを捨てて再解析させる
        Optional<DiskAnalysisCache.Snapshot> snap =
                cache.load(projectRoot, ProgressListener.silent(), sources);
        assertFalse("パース中の編集は次回ロードで検出されるべき (恒久的な陳腐化を防ぐ)",
                snap.isPresent());
    }

    @Test
    public void unchangedProjectStillHitsTheCache() throws Exception {
        // 非退行: 何も編集していなければ従来どおりキャッシュに当たること。
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File srcDir = new File(projectRoot, "src/main/java/com/example");
        assertTrue(srcDir.mkdirs());
        File source = new File(srcDir, "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("package com.example; public class Hello {}");
        }

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        List<File> sources = new ArrayList<>(Arrays.asList(source));
        List<DiskAnalysisCache.SourceStat> scanned = DiskAnalysisCache.statAll(sources);

        ClassIndex index = new ClassIndex();
        JavaClassInfo hello = makeClass("com.example", "Hello");
        index.put(hello, source, ":app");
        cache.saveScanned(projectRoot, new ArrayList<>(Arrays.asList(hello)), index, scanned);

        assertTrue("無変更ならキャッシュヒットすること",
                cache.load(projectRoot, ProgressListener.silent(), sources).isPresent());
    }

    @Test
    public void editAfterSaveIsAlsoDetected() throws Exception {
        // 通常経路 (パース中ではなく、保存後に編集) も従来どおり検出されること。
        File base = tmp.newFolder("base");
        File projectRoot = tmp.newFolder("proj");
        File srcDir = new File(projectRoot, "src/main/java/com/example");
        assertTrue(srcDir.mkdirs());
        File source = new File(srcDir, "Hello.java");
        try (FileWriter w = new FileWriter(source)) {
            w.write("package com.example; public class Hello {}");
        }

        DiskAnalysisCache cache = new DiskAnalysisCache(base);
        List<File> sources = new ArrayList<>(Arrays.asList(source));
        ClassIndex index = new ClassIndex();
        JavaClassInfo hello = makeClass("com.example", "Hello");
        index.put(hello, source, ":app");
        cache.saveScanned(projectRoot, new ArrayList<>(Arrays.asList(hello)), index,
                DiskAnalysisCache.statAll(sources));

        modify(source, "package com.example; public class Hello { void later() {} }");

        assertFalse("保存後の編集も検出されること",
                cache.load(projectRoot, ProgressListener.silent(), sources).isPresent());
    }
}
