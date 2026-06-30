// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.app.uml.ProjectAnalysisCache;
import juml.app.uml.ReferenceIndexCache;
import juml.util.ErrorListener;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
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
 * {@link ReverseReferencePanel} の基本動作テスト。
 */
public class ReverseReferencePanelTest {

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
    public void panelInstantiates() throws Exception {
        assumeFalse("Skipping in headless environment", java.awt.GraphicsEnvironment.isHeadless());
        ProjectAnalysisCache pc = new ProjectAnalysisCache();
        ReferenceIndexCache rc = new ReferenceIndexCache(pc);
        AtomicReference<ReverseReferencePanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new ReverseReferencePanel(rc)));
        assertNotNull(ref.get());
    }

    @Test
    public void findReferencesPopulatesTable() throws Exception {
        assumeFalse("Skipping in headless environment", java.awt.GraphicsEnvironment.isHeadless());
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

        AtomicReference<ReverseReferencePanel> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ref.set(new ReverseReferencePanel(rc)));
        ReverseReferencePanel panel = ref.get();
        SwingUtilities.invokeAndWait(() -> panel.findReferencesTo("x.Target.hit"));

        long deadline = System.currentTimeMillis() + 5000;
        int rows = 0;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Integer> rc2 = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                JTable t = findTable(panel);
                rc2.set(t == null ? 0 : t.getRowCount());
            });
            if (rc2.get() != null && rc2.get() > 0) {
                rows = rc2.get();
                break;
            }
            Thread.sleep(100);
        }
        assertTrue("Reference table should have at least 1 row", rows >= 1);
    }

    private static JTable findTable(java.awt.Container c) {
        for (java.awt.Component comp : c.getComponents()) {
            if (comp instanceof JTable) return (JTable) comp;
            if (comp instanceof java.awt.Container) {
                JTable t = findTable((java.awt.Container) comp);
                if (t != null) return t;
            }
        }
        return null;
    }
}
