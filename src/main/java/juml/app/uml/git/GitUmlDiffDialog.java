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
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;

/**
 * git 履歴の 2 時点間で Java ファイルのクラス構造差分を UML クラス図として表示する
 * モードレスダイアログ。
 *
 * <p>blob の取得 → 構造解析 ({@link JavaStructureExtractor}) → 差分計算
 * ({@link ClassStructureDiff}) → PlantUML 生成・SVG レンダリングまでを
 * {@link SwingWorker} で背景実行し、完了後に図と PlantUML テキストのタブを表示する。
 * レンダリングに失敗した場合も PlantUML テキストは参照できる。</p>
 */
final class GitUmlDiffDialog extends JDialog {

    private final SvgPreviewPanel svgPanel = new SvgPreviewPanel();
    private final JTextArea pumlArea = new JTextArea();
    private final JPanel diagramHost = new JPanel(new BorderLayout());
    private final JLabel loadingLabel =
            new JLabel(Messages.get("git.umldiff.rendering"), SwingConstants.CENTER);
    /** dispose 済みフラグ (EDT のみで参照)。閉じた後の背景結果適用を無視するため。 */
    private boolean disposed;

    /** 背景処理の結果 (PlantUML テキストは常にあり、SVG は失敗時 null)。 */
    private static final class Result {
        final String puml;
        final RenderedSvg svg;
        final String error;

        Result(String puml, RenderedSvg svg, String error) {
            this.puml = puml;
            this.svg = svg;
            this.error = error;
        }
    }

    /**
     * @param oldRev 比較元 rev。null なら {@code newRev} の第 1 親を背景で解決する
     * @param newRev 比較先 rev (通常は履歴で選択したコミット SHA)
     * @param newLabel 比較先の表示ラベル (短縮 SHA など)
     */
    GitUmlDiffDialog(Window owner, GitRepoService svc, String relPath,
                     String oldRev, String newRev, String newLabel) {
        super(owner, Messages.get("git.umldiff.title") + " - " + relPath,
                ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        diagramHost.add(loadingLabel, BorderLayout.CENTER);
        pumlArea.setEditable(false);
        pumlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.get("git.umldiff.tab.diagram"), diagramHost);
        tabs.addTab(Messages.get("git.umldiff.tab.puml"), new JScrollPane(pumlArea));
        add(tabs, BorderLayout.CENTER);

        setSize(960, 720);
        setLocationRelativeTo(owner);

        startWorker(svc, relPath, oldRev, newRev, newLabel);
    }

    private void startWorker(GitRepoService svc, String relPath,
                             String oldRev, String newRev, String newLabel) {
        new SwingWorker<Result, Void>() {
            @Override protected Result doInBackground() throws Exception {
                String base = oldRev != null ? oldRev : svc.parentOf(newRev);
                String baseLabel = base != null
                        ? shortSha(base) : Messages.get("git.umldiff.emptyBase");
                // 親コミットとの比較でファイルがリネームされている場合、旧内容は「旧パス」
                // から読む必要がある。新パスのまま読むと base 側に存在せず null (= 全追加)
                // となり、リネームが「まるごと新規」と誤表示される。
                String oldPath = relPath;
                if (oldRev == null && base != null) {
                    String renamedFrom = svc.renamedFromPath(newRev, relPath);
                    if (renamedFrom != null) {
                        oldPath = renamedFrom;
                    }
                }
                String oldSrc = base != null ? svc.fileContentAt(base, oldPath) : null;
                String newSrc = svc.fileContentAt(newRev, relPath);

                List<ClassStructureDiff.ClassDiff> diff = ClassStructureDiff.compare(
                        parseQuietly(oldSrc), parseQuietly(newSrc));

                PlantUmlStructureDiffDiagram.Options opt =
                        new PlantUmlStructureDiffDiagram.Options();
                opt.title = relPath + "  " + baseLabel + " → " + newLabel;
                opt.emptyMessage = Messages.get("git.umldiff.noChanges");
                String puml = PlantUmlStructureDiffDiagram.generate(diff, opt);

                RenderedSvg svg = null;
                String error = null;
                try {
                    svg = PlantUmlSvgRenderer.render(puml);
                } catch (Exception ex) {
                    error = ex.getMessage() != null
                            ? ex.getMessage() : ex.getClass().getSimpleName();
                }
                return new Result(puml, svg, error);
            }

            @Override protected void done() {
                // ダイアログが既に閉じられていれば、破棄済みウィンドウの svgPanel/
                // diagramHost を更新しても無駄なので何もしない。
                // (未表示のまま生きているダイアログもあり得るため isDisplayable ではなく
                //  明示的な dispose フラグで判定する。)
                if (disposed) {
                    return;
                }
                try {
                    showResult(get());
                } catch (Exception ex) {
                    showError(ex.getMessage() != null
                            ? ex.getMessage() : ex.getClass().getSimpleName());
                }
            }
        }.execute();
    }

    @Override
    public void dispose() {
        disposed = true;
        super.dispose();
    }

    private void showResult(Result r) {
        pumlArea.setText(r.puml);
        pumlArea.setCaretPosition(0);
        diagramHost.removeAll();
        if (r.svg != null) {
            svgPanel.setSvgGraphicsNode(
                    r.svg.getRoot(), r.svg.getWidth(), r.svg.getHeight());
            diagramHost.add(new JScrollPane(svgPanel), BorderLayout.CENTER);
        } else {
            diagramHost.add(errorLabel(r.error), BorderLayout.CENTER);
        }
        diagramHost.revalidate();
        diagramHost.repaint();
    }

    private void showError(String message) {
        diagramHost.removeAll();
        diagramHost.add(errorLabel(message), BorderLayout.CENTER);
        diagramHost.revalidate();
        diagramHost.repaint();
    }

    private static JLabel errorLabel(String message) {
        return new JLabel(Messages.get("git.umldiff.renderFailed")
                + (message != null ? message : ""), SwingConstants.CENTER);
    }

    /** ソースを構造解析する。null/空や解析失敗時は空リスト (片側全追加/全削除として扱う)。 */
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
