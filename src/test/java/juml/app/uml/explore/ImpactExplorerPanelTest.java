// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.app.uml.ProjectAnalysisCache;
import juml.app.uml.ReferenceIndexCache;
import juml.util.ErrorListener;

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * {@link ImpactExplorerPanel} の基本動作テスト。
 *
 * <p>ヘッドレス環境を考慮し、{@code java.awt.GraphicsEnvironment.isHeadless()}
 * が true の場合は skip する。Swing コンポーネントの構築のみ検証する。</p>
 */
public class ImpactExplorerPanelTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeProject(String relPath, String content) throws IOException {
        File f = new File(tmp.getRoot(), relPath);
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
        return f;
    }

    @Test
    public void panelInstantiatesWithoutProject() throws Exception {
        assumeFalse("Skipping in headless environment",
                java.awt.GraphicsEnvironment.isHeadless());
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        ReferenceIndexCache rc = new ReferenceIndexCache(pc);
        AtomicReference<ImpactExplorerPanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new ImpactExplorerPanel(rc)));
        assertNotNull(ref.get());
    }

    @Test
    public void analyzeWithLoadedProjectPopulatesTree() throws Exception {
        assumeFalse("Skipping in headless environment",
                java.awt.GraphicsEnvironment.isHeadless());
        writeProject("src/Target.java",
                "package x; public class Target { public void hit() {} }");
        writeProject("src/Caller.java",
                "package x; public class Caller {"
                        + " private Target t;"
                        + " void run() { t.hit(); }"
                        + "}");
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        pc.load(tmp.getRoot(), ErrorListener.silent());
        ReferenceIndexCache rc = new ReferenceIndexCache(pc);

        AtomicReference<ImpactExplorerPanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new ImpactExplorerPanel(rc)));
        ImpactExplorerPanel panel = ref.get();

        // analyze(...) は SwingWorker でバックグラウンド実行されるので
        // panel 内のツリーが置き換わるのを待つ。最大 5 秒。
        SwingUtilities.invokeAndWait(() -> panel.analyze("x.Target.hit"));
        long deadline = System.currentTimeMillis() + 5000;
        DefaultMutableTreeNode root = null;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<DefaultMutableTreeNode> rootRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                JTree tree = findTree(panel);
                if (tree != null) {
                    TreeModel m = tree.getModel();
                    if (m.getRoot() instanceof DefaultMutableTreeNode) {
                        DefaultMutableTreeNode r = (DefaultMutableTreeNode) m.getRoot();
                        if (r.getUserObject() != null
                                && r.getUserObject().toString().contains("x.Target")) {
                            rootRef.set(r);
                        }
                    }
                }
            });
            if (rootRef.get() != null) {
                root = rootRef.get();
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull("Tree must be populated within timeout", root);
        assertTrue("Root label should mention target",
                root.getUserObject().toString().contains("x.Target"));
    }

    private static JTree findTree(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof JTree) return (JTree) comp;
            if (comp instanceof java.awt.Container) {
                JTree t = findTree((java.awt.Container) comp);
                if (t != null) return t;
            }
        }
        return null;
    }
}
