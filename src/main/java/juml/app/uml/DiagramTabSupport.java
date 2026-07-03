// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * {@link DiagramTabPane} の純粋なヘルパ (ツールチップ文言・アイコン選択・リンク href 解析)。
 *
 * <p>本体の肥大化を避けるため、状態を持たない静的ユーティリティをここへ切り出している。</p>
 */
final class DiagramTabSupport {

    private DiagramTabSupport() {
    }

    /** タブのツールチップ: 図種 + 完全名 (同名タブの曖昧さ解消)。 */
    static String tooltipFor(DiagramRequest spec, TreeNodeOpenRequest treeSync) {
        String kind = ToolBarBuilder.toolbarLabel(spec.getKind());
        if (treeSync != null) {
            switch (treeSync.target) {
                case METHOD:
                    return kind + " — " + treeSync.classInfo.getQualifiedName()
                            + "#" + treeSync.methodInfo.getName();
                case CLASS:
                    return kind + " — " + treeSync.classInfo.getQualifiedName();
                case PACKAGE:
                    return "Class — package " + treeSync.name;
                case MODULE:
                    return "Class — module " + treeSync.name;
                default:
                    break;
            }
        }
        return kind + " diagram (whole project)";
    }

    /** ツリー由来ノードの種別からタブヘッダのアイコンを選ぶ。 */
    static TreeNodeIcon iconFor(TreeNodeOpenRequest req) {
        if (req.target == TreeNodeOpenRequest.Target.METHOD) {
            return req.kind == DiagramKind.ACTIVITY ? TreeNodeIcon.ACTIVITY : TreeNodeIcon.SEQUENCE;
        }
        if (req.target == TreeNodeOpenRequest.Target.CLASS) {
            return TreeNodeIcon.CLASS;
        }
        if (req.target == TreeNodeOpenRequest.Target.PACKAGE) {
            return TreeNodeIcon.PACKAGE;
        }
        return TreeNodeIcon.MODULE;
    }


    /** ツリー由来リクエストを、対応する図の {@link DiagramRequest} に変換する。 */
    static DiagramRequest toDiagramRequest(TreeNodeOpenRequest req) {
        switch (req.target) {
            case METHOD:
                if (req.kind == DiagramKind.ACTIVITY) {
                    return DiagramRequest.forActivity(
                            req.classInfo.getSimpleName(), req.methodInfo.getName(), true);
                }
                if (req.kind == DiagramKind.CALLGRAPH) {
                    return DiagramRequest.forCallGraph(
                            req.classInfo.getSimpleName(), req.methodInfo.getName(), true);
                }
                return new DiagramRequest(DiagramKind.SEQUENCE,
                        req.classInfo.getSimpleName(), req.methodInfo.getName(), true);
            case CLASS: {
                String fqn = req.classInfo.getQualifiedName();
                // 1-hop 近傍に絞ったうえで、クリックしたクラス自身を焦点として強調表示する。
                DiagramScope cs = DiagramScope.builder()
                        .seed(fqn).neighborHops(1).focusClass(fqn).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, cs, true);
            }
            case PACKAGE: {
                DiagramScope ps = DiagramScope.builder().includePackage(req.name).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, ps, true);
            }
            case MODULE: {
                DiagramScope ms = DiagramScope.builder().includeModule(req.name).build();
                return new DiagramRequest(DiagramKind.CLASS, null, null, true, ms, true);
            }
            case SOONG:
                return new DiagramRequest(DiagramKind.SOONG);
            default:
                return new DiagramRequest(DiagramKind.CLASS);
        }
    }

    /**
     * 図タブの PlantUML を指定形式でファイル保存する (右クリック/エクスポート用)。
     * {@code preview} に付箋メモがあれば、PNG/SVG にはそれを含めて「見たまま」を保存する。
     *
     * @param parent   ダイアログ親コンポーネント
     * @param puml     保存対象の PlantUML テキスト (null/空なら案内のみ)
     * @param preview  付箋を含めるための SVG プレビュー (null 可)
     * @param fmt      出力形式 (SVG/PNG/PUML)
     * @param reporter ステータスバー通知
     */
    static void exportPuml(java.awt.Component parent, String puml, SvgPreviewPanel preview,
                           UmlExporter.Format fmt,
                           java.util.function.Consumer<String> reporter) {
        if (puml == null || puml.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(parent,
                    juml.util.Messages.get("export.noDiagram"),
                    juml.util.Messages.get("export.title"),
                    javax.swing.JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String ext = fmt.getExtension();
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setDialogTitle(juml.util.Messages.get("export.saveDiagramAs")
                + " " + ext.toUpperCase());
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                ext.toUpperCase() + " (*." + ext + ")", ext));
        if (fc.showSaveDialog(javax.swing.SwingUtilities.getWindowAncestor(parent))
                != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File chosen = fc.getSelectedFile();
        if (!chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + ext)) {
            chosen = new java.io.File(chosen.getAbsolutePath() + "." + ext);
        }
        boolean withNotes = preview != null && preview.hasNotes();
        if (fmt == UmlExporter.Format.PNG) {
            if (withNotes) {
                NoteExport.savePng(preview, chosen, parent, reporter); // 図 + 付箋を 1 枚に
            } else {
                // PNG はラスタライズが重いため共通ヘルパで背景実行し、EDT のフリーズを防ぐ。
                PngBackgroundExporter.save(puml, chosen, parent, reporter);
            }
            return;
        }
        try {
            if (fmt == UmlExporter.Format.SVG && withNotes) {
                NoteExport.writeSvg(chosen, puml, preview); // 付箋を foreignObject で注入
            } else {
                UmlExporter.export(fmt, chosen, puml, null); // PUML は付箋を埋め込めない
            }
            if (reporter != null) {
                reporter.accept(juml.util.Messages.get("status.saved")
                        + chosen.getAbsolutePath());
            }
        } catch (java.io.IOException | juml.util.JumlException ex) {
            // SVG エクスポート中の描画失敗は unchecked (PlantUmlRenderFailedException)
            // で飛んでくるため、IOException と合わせてここで拾いダイアログ表示する。
            juml.util.AppLog.error(
                    juml.util.JumlException.codeOf(ex, juml.util.ErrorCode.EXP_001),
                    "DiagramTabSupport",
                    fmt + " export failed: " + chosen.getAbsolutePath(), ex);
            javax.swing.JOptionPane.showMessageDialog(parent,
                    juml.util.Messages.get("export.failed") + ex.getMessage(),
                    juml.util.Messages.get("dlg.error.title"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * タブの題材クラスの実ソースを解決して {@code panel} に表示する。
     * 単一クラスでないタブ (パッケージ/モジュール図) や解決不能時は案内メッセージを出す。
     */
    static void showSource(JavaSourcePanel panel, TreeNodeOpenRequest treeSync,
                           ProjectAnalysisCache cache) {
        showSource(panel, treeSync, cache, treeSync != null ? treeSync.methodInfo : null);
    }

    /**
     * {@link #showSource(JavaSourcePanel, TreeNodeOpenRequest, ProjectAnalysisCache)} の
     * メソッド上書き版。図上のメソッドリンクなど、タブの題材とは別のメソッドへ
     * ジャンプしたいときに {@code methodOverride} を渡す。
     *
     * <p>着地行は解析インデックスの宣言行 ({@code JavaMethodInfo#getStartLine()} /
     * {@code JavaClassInfo#getStartLine()}) を優先し、無ければ従来の
     * ヒューリスティック探索にフォールバックする。</p>
     */
    static void showSource(JavaSourcePanel panel, TreeNodeOpenRequest treeSync,
                           ProjectAnalysisCache cache,
                           juml.core.formats.uml.JavaMethodInfo methodOverride) {
        String fqn = (treeSync != null && treeSync.classInfo != null)
                ? treeSync.classInfo.getQualifiedName() : null;
        if (fqn == null) {
            boolean multi = treeSync != null
                    && (treeSync.target == TreeNodeOpenRequest.Target.PACKAGE
                        || treeSync.target == TreeNodeOpenRequest.Target.MODULE);
            panel.showMessage(juml.util.Messages.get(
                    multi ? "source.noClassForScope" : "source.selectClass"));
            return;
        }
        java.io.File src = cache.getIndex().source(fqn).orElse(null);
        if (src == null) {
            panel.showMessage(juml.util.Messages.get("source.notFound"));
            return;
        }
        String method = methodOverride != null ? methodOverride.getName() : null;
        int lineHint = 0;
        if (methodOverride != null && methodOverride.getStartLine() > 0) {
            lineHint = methodOverride.getStartLine();
        } else if (method != null) {
            // リンククリック由来のスタブ (名前のみ) は、インデックスの詳細情報から宣言行を引く。
            lineHint = methodStartLineFromIndex(cache, fqn, method);
        } else {
            // クラスタブなど: クラス宣言行があればファイル先頭ではなく宣言へ着地する。
            lineHint = classStartLine(treeSync.classInfo, cache, fqn);
        }
        panel.showFile(src, method, lineHint);
    }

    /** インデックスの詳細情報 (Stage B) から同名メソッドの宣言行を引く。無ければ 0。 */
    private static int methodStartLineFromIndex(ProjectAnalysisCache cache,
                                                String fqn, String method) {
        try {
            juml.core.formats.uml.JavaClassInfo detail = cache.getIndex()
                    .detail(fqn, juml.util.ErrorListener.silent());
            if (detail == null) {
                return 0;
            }
            for (juml.core.formats.uml.JavaMethodInfo m : detail.getMethods()) {
                if (method.equals(m.getName()) && m.getStartLine() > 0) {
                    return m.getStartLine();
                }
            }
        } catch (RuntimeException ex) {
            // 詳細昇格に失敗してもジャンプ自体は諦めない (ヒューリスティックへ)。
        }
        return 0;
    }

    /** クラス宣言行を treeSync のヘッダ → インデックスの順で引く。無ければ 0。 */
    private static int classStartLine(juml.core.formats.uml.JavaClassInfo ci,
                                      ProjectAnalysisCache cache, String fqn) {
        if (ci.getStartLine() > 0) {
            return ci.getStartLine();
        }
        return cache.getIndex().header(fqn)
                .map(juml.core.formats.uml.JavaClassInfo::getStartLine)
                .orElse(0);
    }
}
