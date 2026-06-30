// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.JavaClassInfo;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link ProjectAnalysisCache} の lazy (Stage A) ロードと
 * {@link ProjectAnalysisCache#getDetailedClasses()} によるオンデマンド Stage B 昇格を検証する。
 *
 * <p>AOSP 級ツリーで「開いて操作可能になるまで」を短縮するため、GUI は AOSP 検出時に
 * lazyDetails でロードする。このときツリーやヘッダ系処理は Stage A で動き、メンバーが
 * 要る処理 (メンバー一覧・関数一覧・シーケンス起点) は getDetailedClasses() で昇格する。</p>
 */
public class ProjectAnalysisCacheLazyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private void writeProject(String relPath, String content) throws IOException {
        File f = new File(tmp.getRoot(), relPath);
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private JavaClassInfo classNamed(List<JavaClassInfo> classes, String simpleName) {
        return classes.stream()
                .filter(c -> simpleName.equals(c.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("class not found: " + simpleName));
    }

    @Test
    public void lazyLoadKeepsStageAThenPromotesOnDemand() throws IOException {
        writeProject("src/x/A.java",
                "package x; class A { void run() { helper(); } void helper() {} }");

        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        ProjectAnalysisCache.LoadOptions opts = new ProjectAnalysisCache.LoadOptions();
        opts.lazyDetails = true;
        opts.useDiskCache = false; // ディスクキャッシュ非依存で純粋に Stage A 経路を見る
        pc.load(tmp.getRoot(), ErrorListener.silent(), null, null, opts);

        assertTrue("lazy フラグが立つ", pc.isLazy());
        JavaClassInfo stageA = classNamed(pc.getClasses(), "A");
        assertFalse("getClasses() はヘッダのみ (Stage A)", stageA.isDetailed());

        JavaClassInfo detailed = classNamed(pc.getDetailedClasses(), "A");
        assertTrue("getDetailedClasses() は Stage B 昇格済み", detailed.isDetailed());
        assertTrue("昇格後はメソッドを持つ", detailed.getMethods().size() >= 2);
    }

    @Test
    public void detailedResultIsMemoized() throws IOException {
        writeProject("src/x/A.java", "package x; class A { void run() {} }");
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        ProjectAnalysisCache.LoadOptions opts = new ProjectAnalysisCache.LoadOptions();
        opts.lazyDetails = true;
        opts.useDiskCache = false;
        pc.load(tmp.getRoot(), ErrorListener.silent(), null, null, opts);

        assertSame("2 回目はメモ化結果を返す",
                pc.getDetailedClasses(), pc.getDetailedClasses());
    }

    @Test
    public void fullLoadIsNotLazyAndReturnsSameList() throws IOException {
        writeProject("src/x/A.java", "package x; class A { void run() {} }");
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        pc.load(tmp.getRoot(), ErrorListener.silent()); // 既定 FULL

        assertFalse("FULL ロードは lazy ではない", pc.isLazy());
        JavaClassInfo a = classNamed(pc.getClasses(), "A");
        assertTrue("FULL は最初から詳細", a.isDetailed());
        // 非 lazy では getDetailedClasses() は getClasses() をそのまま返す (no-op)。
        assertSame(pc.getClasses(), pc.getDetailedClasses());
    }
}
