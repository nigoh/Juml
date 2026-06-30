// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.ErrorListener;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * lazy (Stage A) ロード時に左ツリーのクラスノードを展開すると、
 * {@link juml.core.formats.uml.ClassIndex#detail} で Stage B 昇格され、
 * メソッドノードが構築されることを headless で検証する。
 *
 * <p>これは #172 の対話 GUI 確認 (ツリー展開でメソッドが出るか) を自動テスト化したもの。
 * Swing ウィジェットの構築・モデル操作は headless でも可能なため Robot は使わない。</p>
 */
public class ProjectTreeLazyExpansionTest {

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

    private DefaultMutableTreeNode findByUserType(DefaultMutableTreeNode parent, Class<?> type) {
        Enumeration<?> e = parent.children();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) e.nextElement();
            if (type.isInstance(child.getUserObject())) {
                return child;
            }
        }
        return null;
    }

    @Test
    public void expandingClassNodeUnderLazyLoadPromotesAndShowsMethods() throws IOException {
        writeProject("src/x/A.java",
                "package x; class A { void run() { helper(); } void helper() {} }");

        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        ProjectAnalysisCache.LoadOptions opts = new ProjectAnalysisCache.LoadOptions();
        opts.lazyDetails = true;
        opts.useDiskCache = false;
        cache.load(tmp.getRoot(), ErrorListener.silent(), null, null, opts);
        assertTrue("前提: lazy ロード", cache.isLazy());

        ProjectTreePanel panel = new ProjectTreePanel();
        panel.populate(cache.getAnalysis(), cache.getClasses(), "proj",
                cache.getClassToModule(), cache.getIndex());

        DefaultMutableTreeNode root = panel.rootForTest();
        DefaultMutableTreeNode module = findByUserType(root, ModuleEntry.class);
        assertNotNull("モジュールノードがある", module);
        DefaultMutableTreeNode pkg = findByUserType(module, PackageEntry.class);
        assertNotNull("パッケージノードがある", pkg);

        // パッケージ展開でクラスノードを構築 (まだ Stage A)
        panel.expandNodeForTest(pkg);
        DefaultMutableTreeNode classNode = findByUserType(pkg, ClassEntry.class);
        assertNotNull("クラスノードがある", classNode);
        ClassEntry entry = (ClassEntry) classNode.getUserObject();
        assertFalse("展開前は Stage A (未昇格)", entry.info.isDetailed());
        assertTrue("メソッドを持ちうるので展開可能 (placeholder)", classNode.getChildCount() >= 1);

        // クラス展開で Stage B 昇格 → メソッドノード構築
        panel.expandNodeForTest(classNode);
        assertTrue("展開後は Stage B 昇格済み", entry.info.isDetailed());

        DefaultMutableTreeNode methodNode = findByUserType(classNode, MethodEntry.class);
        assertNotNull("昇格でメソッドノードが出る", methodNode);
    }

    @Test
    public void fullLoadTreeStillShowsMethodsWithoutLazyIndex() throws IOException {
        // 通常 (FULL) ロードでは index 無しでも従来どおりメソッドが出る (回帰防止)。
        writeProject("src/x/B.java", "package x; class B { void go() {} }");
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(tmp.getRoot(), ErrorListener.silent());

        ProjectTreePanel panel = new ProjectTreePanel();
        panel.populate(cache.getAnalysis(), cache.getClasses(), "proj",
                cache.getClassToModule(), cache.getIndex());
        DefaultMutableTreeNode root = panel.rootForTest();
        DefaultMutableTreeNode module = findByUserType(root, ModuleEntry.class);
        DefaultMutableTreeNode pkg = findByUserType(module, PackageEntry.class);
        panel.expandNodeForTest(pkg);
        DefaultMutableTreeNode classNode = findByUserType(pkg, ClassEntry.class);
        assertNotNull(classNode);
        panel.expandNodeForTest(classNode);
        assertNotNull("FULL ロードでもメソッドノードが出る",
                findByUserType(classNode, MethodEntry.class));
    }
}
