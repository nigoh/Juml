// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 実物の {@link GitCommitsPane} をオフスクリーン描画して PNG に保存する開発用ユーティリティ。
 *
 * <p>CI では {@code -Djuml.screenshot} 未指定のため即 return する no-op。手元で
 * {@code gradle test --tests '*GitCommitsPaneScreenshot' -Djuml.screenshot=1
 * -Djuml.screenshot.out=/path/out.png} のように起動して見た目を確認する。実レンダラを
 * そのまま使うため、スクショと実 UI が乖離しない。</p>
 */
public class GitCommitsPaneScreenshot {

    @Test
    public void capture() throws Exception {
        if (System.getProperty("juml.screenshot") == null) {
            return; // CI ではスキップ
        }
        String out = System.getProperty("juml.screenshot.out",
                "/tmp/git-commits-pane.png");
        int w = Integer.getInteger("juml.screenshot.w", 1150);
        int h = Integer.getInteger("juml.screenshot.h", 760);

        GitRepoService svc = GitRepoService.open(new File("."));
        if (svc == null) {
            throw new IllegalStateException("no git repo at .");
        }
        final BufferedImage[] holder = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            applyLaf(System.getProperty("juml.screenshot.laf", "dark"));
            GitCommitsPane pane = new GitCommitsPane(new StubContext(svc));
            pane.setDiffModeForTest(System.getProperty("juml.screenshot.diff", "unified"));
            try {
                pane.loadForTest(svc, "HEAD");
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            pane.setSize(w, h);
            layoutTree(pane);
            // サイズ確定後に分割比を明示して再レイアウト。
            for (JSplitPane sp : splits(pane)) {
                sp.setDividerLocation(
                        sp.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? 0.6 : 0.45);
            }
            layoutTree(pane);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            pane.printAll(g);
            g.dispose();
            holder[0] = img;
        });
        File file = new File(out);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        ImageIO.write(holder[0], "png", file);
        System.out.println("WROTE " + file.getAbsolutePath());
        svc.close();
    }

    private static void applyLaf(String which) {
        try {
            String cls = "light".equalsIgnoreCase(which)
                    ? "com.formdev.flatlaf.FlatLightLaf"
                    : "com.formdev.flatlaf.FlatDarkLaf";
            javax.swing.UIManager.setLookAndFeel(cls);
        } catch (Exception ignored) {
            // LaF が無ければ既定のまま描画する
        }
    }

    private static void layoutTree(Component c) {
        c.doLayout();
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                child.setSize(child.getWidth() > 0 ? child.getWidth() : c.getWidth(),
                        child.getHeight() > 0 ? child.getHeight() : c.getHeight());
                layoutTree(child);
            }
        }
    }

    private static java.util.List<JSplitPane> splits(Container root) {
        java.util.List<JSplitPane> found = new java.util.ArrayList<>();
        collectSplits(root, found);
        return found;
    }

    private static void collectSplits(Container c, java.util.List<JSplitPane> found) {
        for (Component child : c.getComponents()) {
            if (child instanceof JSplitPane) {
                found.add((JSplitPane) child);
            }
            if (child instanceof Container) {
                collectSplits((Container) child, found);
            }
        }
    }

    /** loadForTest しか使わないため、大半のメソッドは呼ばれない最小スタブ。 */
    private static final class StubContext implements GitPanel.GitContext {
        private final GitRepoService svc;

        StubContext(GitRepoService svc) {
            this.svc = svc;
        }

        @Override public GitRepoService service() {
            return svc;
        }

        @Override public String selectedRef() {
            return "HEAD";
        }

        @Override public void reportStatus(String msg) {
            // no-op
        }

        @Override public void selectRef(String ref) {
            // no-op
        }
    }
}
