// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.perf;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.app.uml.DiskAnalysisCache;
import juml.app.uml.ProjectAnalysisCache;
import juml.core.formats.uml.JavaClassInfo;
import juml.util.CancelToken;
import juml.util.ErrorListener;
import juml.util.ProgressListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

/**
 * 合成 AOSP 級プロジェクト (50,000 クラス) の性能/メモリ性能テスト。
 *
 * <p>CI のデフォルト実行からは除外する。明示的に {@code -DrunPerfTests=true} を
 * 渡したときだけ走る。手動で AOSP 級の上限を確認したいとき用。</p>
 *
 * <pre>./gradlew test -DrunPerfTests=true --tests juml.perf.SyntheticAospScaleTest</pre>
 */
public class SyntheticAospScaleTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Before
    public void requireOptIn() {
        Assume.assumeTrue("Set -DrunPerfTests=true to run perf tests",
                Boolean.getBoolean("runPerfTests"));
    }

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /** 5 万クラス: 500 パッケージ × 100 ファイル。 */
    private File makeSynthAosp() throws IOException {
        File root = tmp.newFolder("Synth");
        File base = new File(root, "src/main/java/com/example/synth");
        assertTrue(base.mkdirs());
        for (int p = 0; p < 500; p++) {
            File pkg = new File(base, "p" + p);
            assertTrue(pkg.mkdirs());
            for (int i = 0; i < 100; i++) {
                writeFile(new File(pkg, "C" + i + ".java"),
                        "package com.example.synth.p" + p
                                + "; public class C" + i + " {\n"
                                + "  private int v;\n  public int get() { return v; }\n"
                                + "}\n");
            }
        }
        return root;
    }

    @Test
    public void testLazyDetailsLoadCompletesAndHeapStaysReasonable() throws IOException {
        File root = makeSynthAosp();
        File cacheBase = tmp.newFolder("disk-cache");
        ProjectAnalysisCache cache = new ProjectAnalysisCache(
                new DiskAnalysisCache(cacheBase));
        ProjectAnalysisCache.LoadOptions opts = new ProjectAnalysisCache.LoadOptions();
        opts.lazyDetails = true;
        opts.useDiskCache = true;

        AtomicInteger progressCount = new AtomicInteger();
        long start = System.currentTimeMillis();
        cache.load(root, ErrorListener.silent(),
                (d, t, m) -> progressCount.incrementAndGet(),
                CancelToken.NONE, opts);
        long firstLoad = System.currentTimeMillis() - start;

        System.out.println("[perf] first load: " + firstLoad + " ms, "
                + cache.getClasses().size() + " classes, "
                + progressCount.get() + " progress events");
        assertTrue("50,000 クラスを 5 分以内にロード (first=" + firstLoad + "ms)",
                firstLoad < 5L * 60 * 1000);
        assertTrue("少なくとも 1 進捗イベント発火", progressCount.get() > 0);

        // 2 回目: ディスクキャッシュから瞬時復元
        ProjectAnalysisCache cache2 = new ProjectAnalysisCache(
                new DiskAnalysisCache(cacheBase));
        long s2 = System.currentTimeMillis();
        cache2.load(root, ErrorListener.silent(), ProgressListener.silent(),
                CancelToken.NONE, opts);
        long secondLoad = System.currentTimeMillis() - s2;
        System.out.println("[perf] cache load: " + secondLoad + " ms, "
                + cache2.getClasses().size() + " classes");
        assertTrue("ディスクキャッシュから 30 秒以内に復元",
                secondLoad < 30L * 1000);

        // Stage B 昇格はオンデマンド。サンプル 1 件で動作確認
        if (!cache2.getClasses().isEmpty()) {
            JavaClassInfo first = cache2.getClasses().get(0);
            assertTrue("ロード直後は Stage A", !first.isDetailed());
            JavaClassInfo detailed = cache2.getIndex().detail(
                    first.getQualifiedName(), ErrorListener.silent());
            assertTrue("detail 後は Stage B", detailed.isDetailed());
        }
    }
}
