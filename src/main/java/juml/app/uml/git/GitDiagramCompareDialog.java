// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.PlantUmlSvgRenderer;
import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import juml.app.uml.SvgPreviewPanel;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.structdiff.ClassStructureDiff;
import juml.core.structdiff.PlantUmlStructureDiffDiagram;
import juml.util.Messages;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Window;
import java.util.List;

/**
 * git 履歴の 2 時点間で、Java クラス図を<b>左右に並べて</b>比較するモードレスダイアログ。
 *
 * <p>左に旧 (比較元)、右に新 (比較先) のクラス図を描き、各図の中で変更ノードを色付けする
 * (旧図: 削除=赤打ち消し / 変更=旧宣言・黄、新図: 追加=緑 / 変更=新宣言・黄)。設計書へ
 * 「変更前 → 変更後」を貼りやすい形。blob 取得 → 構造解析 → 差分 → 片側 PlantUML 生成 →
 * SVG レンダリングまでを {@link SwingWorker} で背景実行する。</p>
 */
final class GitDiagramCompareDialog extends JDialog {

    private final SvgPreviewPanel oldPanel = new SvgPreviewPanel();
    private final SvgPreviewPanel newPanel = new SvgPreviewPanel();
    private final JPanel oldHost = new JPanel(new BorderLayout());
    private final JPanel newHost = new JPanel(new BorderLayout());

    private static final class Result {
        final RenderedSvg oldSvg;
        final RenderedSvg newSvg;
        final String oldError;
        final String newError;

        Result(RenderedSvg oldSvg, RenderedSvg newSvg, String oldError, String newError) {
            this.oldSvg = oldSvg;
            this.newSvg = newSvg;
            this.oldError = oldError;
            this.newError = newError;
        }
    }

    /**
     * @param oldRev 比較元 rev。null なら {@code newRev} の第 1 親を背景で解決する
     * @param newRev 比較先 rev
     * @param newLabel 比較先の表示ラベル (短縮 SHA など)
     */
    GitDiagramCompareDialog(Window owner, GitRepoService svc, String relPath,
                            String oldRev, String newRev, String newLabel) {
        super(owner, Messages.get("git.diagcmp.title") + " - " + relPath,
                ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        oldHost.add(loading(), BorderLayout.CENTER);
        newHost.add(loading(), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titled(oldHost, Messages.get("git.diagcmp.old")),
                titled(newHost, Messages.get("git.diagcmp.new")));
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);
        setSize(1100, 720);
        setLocationRelativeTo(owner);

        startWorker(svc, relPath, oldRev, newRev, newLabel);
    }

    private static JLabel loading() {
        return new JLabel(Messages.get("git.umldiff.rendering"), SwingConstants.CENTER);
    }

    private static JPanel titled(JPanel host, String title) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel header = new JLabel(title, SwingConstants.CENTER);
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4));
        p.add(header, BorderLayout.NORTH);
        p.add(host, BorderLayout.CENTER);
        return p;
    }

    private void startWorker(GitRepoService svc, String relPath,
                             String oldRev, String newRev, String newLabel) {
        new SwingWorker<Result, Void>() {
            @Override protected Result doInBackground() throws Exception {
                String base = oldRev != null ? oldRev : svc.parentOf(newRev);
                String baseLabel = base != null
                        ? shortSha(base) : Messages.get("git.umldiff.emptyBase");
                String oldSrc = base != null ? svc.fileContentAt(base, relPath) : null;
                String newSrc = svc.fileContentAt(newRev, relPath);

                List<ClassStructureDiff.ClassDiff> diff = ClassStructureDiff.compare(
                        parseQuietly(oldSrc), parseQuietly(newSrc));

                PlantUmlStructureDiffDiagram.Options oldOpt =
                        new PlantUmlStructureDiffDiagram.Options();
                oldOpt.title = relPath + "  " + baseLabel;
                oldOpt.emptyMessage = Messages.get("git.umldiff.noChanges");
                PlantUmlStructureDiffDiagram.Options newOpt =
                        new PlantUmlStructureDiffDiagram.Options();
                newOpt.title = relPath + "  " + newLabel;
                newOpt.emptyMessage = Messages.get("git.umldiff.noChanges");

                String oldPuml = PlantUmlStructureDiffDiagram.generateSide(diff, true, oldOpt);
                String newPuml = PlantUmlStructureDiffDiagram.generateSide(diff, false, newOpt);

                RenderedSvg oldSvg = null;
                RenderedSvg newSvg = null;
                String oldErr = null;
                String newErr = null;
                try {
                    oldSvg = PlantUmlSvgRenderer.render(oldPuml);
                } catch (Exception ex) {
                    oldErr = errText(ex);
                }
                try {
                    newSvg = PlantUmlSvgRenderer.render(newPuml);
                } catch (Exception ex) {
                    newErr = errText(ex);
                }
                return new Result(oldSvg, newSvg, oldErr, newErr);
            }

            @Override protected void done() {
                try {
                    Result r = get();
                    show(oldHost, oldPanel, r.oldSvg, r.oldError);
                    show(newHost, newPanel, r.newSvg, r.newError);
                } catch (Exception ex) {
                    show(oldHost, oldPanel, null, errText(ex));
                    show(newHost, newPanel, null, errText(ex));
                }
            }
        }.execute();
    }

    private static void show(JPanel host, SvgPreviewPanel panel,
                             RenderedSvg svg, String error) {
        host.removeAll();
        if (svg != null) {
            panel.setSvgGraphicsNode(svg.getRoot(), svg.getWidth(), svg.getHeight());
            host.add(new JScrollPane(panel), BorderLayout.CENTER);
        } else {
            host.add(new JLabel(Messages.get("git.umldiff.renderFailed")
                    + (error != null ? error : ""), SwingConstants.CENTER),
                    BorderLayout.CENTER);
        }
        host.revalidate();
        host.repaint();
    }

    private static String errText(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    /** ソースを構造解析する。null/空や解析失敗時は空リスト。 */
    private static List<JavaClassInfo> parseQuietly(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        try {
            return JavaStructureExtractor.extract(source);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static String shortSha(String rev) {
        return rev != null && rev.length() >= 7 ? rev.substring(0, 7) : rev;
    }
}
