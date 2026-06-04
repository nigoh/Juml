// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PlantUmlSvgRenderer.LinkArea;
import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * すべてのダイアグラムを対等な「タブ (= エディタ)」として管理する VS Code 風タブペイン。
 *
 * <p>外部から渡された {@link JTabbedPane} に動的タブを挿入する。
 * 末尾 {@code fixedSuffix} 本はユーティリティタブ (Manifest / Impact / References /
 * Func Diff / Functions / Members) として予約されており、動的タブはその手前に挿入される。
 * 特別扱いの「Home タブ」は存在しない。</p>
 *
 * <p>各タブには {@link SvgPreviewPanel} と {@link PumlSourcePanel} を
 * {@link JSplitPane} で上下に配置する (入れ子タブなし)。各タブは自身の
 * {@link DiagramRequest} を保持し、{@link DiagramService} で描画する。</p>
 *
 * <p>同じタブキーのタブが既にある場合は新規作成せずフォーカスのみ移す。</p>
 */
public final class DiagramTabPane {

    private final JTabbedPane tabs;
    private final int fixedSuffix;
    private final Map<String, DiagramTab> openTabs = new LinkedHashMap<>();
    private final ProjectAnalysisCache cache;
    private final DiagramState state;
    private final Consumer<String> statusReporter;
    private final DoubleConsumer zoomReporter;
    /** 動的タブにフォーカスが移ったとき、その情報 (由来ノード + 図種) を通知する。 */
    private Consumer<FocusedTab> onTabFocused;

    /** フォーカスが移ったタブの情報 (タブ ↔ ツリー ↔ ツールバー連動用)。 */
    public static final class FocusedTab {
        /** タブの由来ノード (ツリーでハイライトする対象)。汎用タブでは null。 */
        public final TreeNodeOpenRequest treeSync;
        /** タブが表示している図種。 */
        public final DiagramKind kind;

        FocusedTab(TreeNodeOpenRequest treeSync, DiagramKind kind) {
            this.treeSync = treeSync;
            this.kind = kind;
        }
    }

    /**
     * @param tabs           ダイアグラムタブを追加する外部 JTabbedPane
     * @param fixedSuffix    末尾に固定されたユーティリティタブ数
     * @param cache          解析キャッシュ
     * @param state          アクティブタブの描画結果を反映する共有状態 (エクスポート等が参照)
     * @param statusReporter ステータスバー更新コールバック
     * @param zoomReporter   アクティブタブのズーム率通知コールバック
     */
    public DiagramTabPane(JTabbedPane tabs, int fixedSuffix,
                          ProjectAnalysisCache cache, DiagramState state,
                          Consumer<String> statusReporter, DoubleConsumer zoomReporter) {
        this.tabs = tabs;
        this.fixedSuffix = fixedSuffix;
        this.cache = cache;
        this.state = state;
        this.statusReporter = statusReporter;
        this.zoomReporter = zoomReporter;
        tabs.addChangeListener(e -> handleTabSelectionChanged());
    }

    /**
     * 動的タブにフォーカスが移ったときに呼ぶコールバックを設定する。
     * 受け取り側は由来ノードのツリーハイライトと図種のツールバー反映に使う。
     */
    public void setOnTabFocused(Consumer<FocusedTab> listener) {
        this.onTabFocused = listener;
    }

    /** いま選択中のタブが動的ダイアグラムタブか (ユーティリティタブなら false)。 */
    public boolean dynamicTabFocused() {
        return tabs.getSelectedComponent() instanceof DiagramTab;
    }

    /** ダイアグラムタブが 1 つ以上開かれていて、かつ選択中か。 */
    public boolean hasActiveTab() {
        return tabs.getSelectedComponent() instanceof DiagramTab;
    }

    /** フォーカス中の動的タブの由来ノード。動的タブでない / 汎用タブなら null。 */
    public TreeNodeOpenRequest focusedTabRequest() {
        DiagramTab t = activeTab();
        return t != null ? t.treeSync : null;
    }

    /** フォーカス中タブの図種。動的タブでなければ null。 */
    public DiagramKind activeTabKind() {
        DiagramTab t = activeTab();
        return t != null ? t.spec.getKind() : null;
    }

    /** フォーカス中タブの SVG プレビュー。動的タブでなければ null。 */
    public SvgPreviewPanel activePreviewPanel() {
        DiagramTab t = activeTab();
        return t != null ? t.previewPanel : null;
    }

    private DiagramTab activeTab() {
        java.awt.Component sel = tabs.getSelectedComponent();
        return (sel instanceof DiagramTab) ? (DiagramTab) sel : null;
    }

    private void handleTabSelectionChanged() {
        DiagramTab tab = activeTab();
        if (tab == null) {
            return;
        }
        tab.reportFocusStatus();
        tab.mirrorToState();
        if (zoomReporter != null) {
            zoomReporter.accept(tab.previewPanel.getZoomLevel());
        }
        if (onTabFocused != null) {
            onTabFocused.accept(new FocusedTab(tab.treeSync, tab.spec.getKind()));
        }
    }

    // -------------------------------------------------------------------------
    // タブを開く
    // -------------------------------------------------------------------------

    /** ツリー由来のリクエストに対応するタブを開く。既存タブがあればフォーカスのみ移す。 */
    public void addOrFocusTab(TreeNodeOpenRequest req) {
        if (req == null) {
            return;
        }
        openDiagram(req.tabKey(), req.displayLabel(), iconFor(req),
                toDiagramRequest(req), req);
    }

    /**
     * 任意の図種・スコープのダイアグラムをタブとして開く。
     * 既存タブ ({@code key} 一致) があればフォーカスのみ移す。
     *
     * @param key      タブ識別キー (同一なら既存タブにフォーカス)
     * @param label    タブヘッダのラベル
     * @param icon     タブヘッダのアイコン
     * @param spec     描画リクエスト
     * @param treeSync ツリーハイライト用の由来ノード (無ければ null)
     */
    public void openDiagram(String key, String label, TreeNodeIcon icon,
                            DiagramRequest spec, TreeNodeOpenRequest treeSync) {
        if (spec == null || !cache.isLoaded()) {
            return;
        }
        DiagramTab existing = openTabs.get(key);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return;
        }
        DiagramTab tab = new DiagramTab(key, label, spec, treeSync);
        openTabs.put(key, tab);
        int insertAt = tabs.getTabCount() - fixedSuffix;
        if (insertAt < 0) {
            insertAt = 0;
        }
        String tip = tooltipFor(spec, treeSync);
        tabs.insertTab(label, null, tab, tip, insertAt);
        tabs.setTabComponentAt(insertAt, buildHeader(label, icon, tab, key, tip));
        tabs.setSelectedIndex(insertAt);
        tab.startRender();
    }

    /** タブのツールチップ: 図種 + 完全名 (同名タブの曖昧さ解消)。 */
    private static String tooltipFor(DiagramRequest spec, TreeNodeOpenRequest treeSync) {
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

    /** アクティブタブの描画リクエストを差し替えて再描画する (スコープ/プリセット適用など)。 */
    public void setActiveTabSpecAndRender(DiagramRequest spec) {
        DiagramTab t = activeTab();
        if (t != null && spec != null) {
            t.spec = spec;
            t.startRender();
        }
    }

    /** アクティブタブを現在のリクエストで再描画する (F5 / Refresh)。 */
    public void rerenderActiveTab() {
        DiagramTab t = activeTab();
        if (t != null) {
            t.startRender();
        }
    }

    /** 開いているすべてのダイアグラムタブを再描画する (スタイル変更時など)。 */
    public void rerenderAllTabs() {
        for (DiagramTab t : new ArrayList<>(openTabs.values())) {
            t.startRender();
        }
    }

    public void zoomInActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomIn();
        }
    }

    public void zoomOutActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomOut();
        }
    }

    public void zoomResetActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomReset();
        }
    }

    public void zoomToFitActive() {
        SvgPreviewPanel p = activePreviewPanel();
        if (p != null) {
            p.zoomToFit();
        }
    }

    private JPanel buildHeader(String label, TreeNodeIcon icon, DiagramTab tab,
                               String key, String tooltip) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        header.setOpaque(false);
        header.setToolTipText(tooltip);
        if (icon != null) {
            header.add(new JLabel(icon));
        }
        JLabel title = new JLabel(label);
        title.setFont(title.getFont().deriveFont(Font.PLAIN, 11f));
        title.setToolTipText(tooltip);
        header.add(title);

        JButton close = new JButton("×");
        close.setMargin(new java.awt.Insets(0, 3, 0, 3));
        close.setFocusable(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.setForeground(new Color(0x888888));
        close.setToolTipText("Close tab");
        close.addActionListener(e -> closeTab(tab, key));
        header.add(close);

        // 中クリックで閉じる / 右クリックで「閉じる・他を閉じる・すべて閉じる」メニュー。
        java.awt.event.MouseAdapter ma = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
                    closeTab(tab, key);
                } else if (e.isPopupTrigger()) {
                    showTabMenu(tab, key, e);
                } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    tabs.setSelectedComponent(tab);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTabMenu(tab, key, e);
                }
            }
        };
        header.addMouseListener(ma);
        title.addMouseListener(ma);
        return header;
    }

    private void showTabMenu(DiagramTab tab, String key, MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(a -> closeTab(tab, key));
        menu.add(close);
        JMenuItem others = new JMenuItem("Close Others");
        others.addActionListener(a -> closeOtherTabs(key));
        others.setEnabled(openTabs.size() > 1);
        menu.add(others);
        JMenuItem all = new JMenuItem("Close All");
        all.addActionListener(a -> closeAllTabs());
        all.setEnabled(!openTabs.isEmpty());
        menu.add(all);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    /** {@code keepKey} 以外のすべてのダイアグラムタブを閉じる。 */
    private void closeOtherTabs(String keepKey) {
        for (Map.Entry<String, DiagramTab> en : new ArrayList<>(openTabs.entrySet())) {
            if (!en.getKey().equals(keepKey)) {
                closeTab(en.getValue(), en.getKey());
            }
        }
    }

    /** すべてのダイアグラムタブを閉じる (ユーティリティタブは残す)。 */
    private void closeAllTabs() {
        for (Map.Entry<String, DiagramTab> en : new ArrayList<>(openTabs.entrySet())) {
            closeTab(en.getValue(), en.getKey());
        }
    }

    private static TreeNodeIcon iconFor(TreeNodeOpenRequest req) {
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

    private void closeTab(DiagramTab tab, String key) {
        int index = tabs.indexOfComponent(tab);
        if (index >= 0) {
            tabs.remove(index);
        }
        openTabs.remove(key);
    }

    private void reportStatus(String msg) {
        if (statusReporter != null) {
            statusReporter.accept(msg);
        }
    }

    /**
     * 1 タブ分の内容。SVG プレビューと PlantUML ソースを JSplitPane で上下に配置。
     * 自身の {@link DiagramRequest} を保持し、差し替え再描画にも対応する。
     */
    private final class DiagramTab extends JPanel {
        private final String key;
        private final String label;
        /** ツリーハイライト用の由来ノード (汎用タブでは null)。 */
        private final TreeNodeOpenRequest treeSync;
        /** このタブが描画するリクエスト (差し替え可能)。 */
        private DiagramRequest spec;
        private final SvgPreviewPanel previewPanel = new SvgPreviewPanel();
        private final PumlSourcePanel sourcePanel  = new PumlSourcePanel();
        /** プレビュー / メッセージ(描画中・失敗・空) を切り替えるカード。 */
        private final java.awt.CardLayout cards = new java.awt.CardLayout();
        private final JPanel viewCards = new JPanel(cards);
        private final JLabel messageLabel = new JLabel("", javax.swing.SwingConstants.CENTER);
        private String renderedPuml;
        private String renderedSvgXml;
        private String lastStatus;

        DiagramTab(String key, String label, DiagramRequest spec, TreeNodeOpenRequest treeSync) {
            super(new java.awt.BorderLayout());
            this.key = key;
            this.label = label;
            this.spec = spec;
            this.treeSync = treeSync;

            viewCards.add(new JScrollPane(previewPanel), "view");
            JPanel msgPanel = new JPanel(new java.awt.GridBagLayout());
            msgPanel.setBackground(java.awt.Color.WHITE);
            messageLabel.setForeground(new Color(0x555555));
            msgPanel.add(messageLabel);
            viewCards.add(msgPanel, "msg");

            JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, viewCards, sourcePanel);
            split.setResizeWeight(0.85);
            split.setDividerLocation(0.85);
            add(split, java.awt.BorderLayout.CENTER);
            previewPanel.setOnLinkClick(this::handleLinkClick);
            previewPanel.setOnLinkPopup(this::handleLinkPopup);
            previewPanel.setCopyFeedbackListener(DiagramTabPane.this::reportStatus);
            previewPanel.setZoomChangeListener(() -> {
                if (isActive() && zoomReporter != null) {
                    zoomReporter.accept(previewPanel.getZoomLevel());
                }
            });
        }

        private boolean isActive() {
            return tabs.getSelectedComponent() == this;
        }

        /** プレビュー(SVG)カードを前面に出す。 */
        private void showPreviewCard() {
            cards.show(viewCards, "view");
        }

        /** メッセージカード(描画中 / 失敗 / 空)を中央寄せ HTML で表示する。 */
        private void showMessageCard(String html) {
            messageLabel.setText("<html><div style='text-align:center;width:460px'>"
                    + html + "</div></html>");
            cards.show(viewCards, "msg");
        }

        private void setStatus(String msg) {
            lastStatus = msg;
            reportStatus(msg);
        }

        void reportFocusStatus() {
            reportStatus(lastStatus != null ? lastStatus : label + " (tab)");
        }

        /** 描画結果と図のパラメータを共有 {@link DiagramState} に反映する (エクスポート/ダイアログ用)。 */
        void mirrorToState() {
            if (state == null) {
                return;
            }
            state.currentPuml = renderedPuml;
            state.currentSvgXml = renderedSvgXml;
            state.currentScope = spec.getScope();
            state.sequenceEntry = null;
            state.activityEntry = null;
            state.callGraphEntry = null;
            switch (spec.getKind()) {
                case SEQUENCE:
                    state.sequenceEntry = entryOf(spec);
                    state.sequenceHiddenParticipants.clear();
                    state.sequenceHiddenParticipants.addAll(spec.getSequenceHiddenParticipants());
                    break;
                case ACTIVITY:
                    state.activityEntry = entryOf(spec);
                    break;
                case CALLGRAPH:
                    state.callGraphEntry = entryOf(spec);
                    break;
                case LAYOUT:
                case LAYOUT_SCREEN:
                    state.currentLayoutKey = spec.getLayoutKey();
                    break;
                case NAVIGATION:
                    state.currentNavigationKey = spec.getNavigationGraphKey();
                    break;
                default:
                    break;
            }
        }

        private String entryOf(DiagramRequest r) {
            String cls = r.getSequenceEntryClass();
            String method = r.getSequenceEntryMethod();
            if (cls == null || method == null) {
                return null;
            }
            return cls + "." + method;
        }

        void startRender() {
            setStatus("Rendering " + label + " ...");
            showMessageCard("<b>Rendering " + esc(label) + " …</b>");
            final DiagramRequest dreq = spec;
            new SwingWorker<RenderResult, Void>() {
                private Throwable error;
                private String pumlOnError;

                @Override
                protected RenderResult doInBackground() {
                    try {
                        String puml = DiagramService.generatePuml(dreq, cache);
                        pumlOnError = puml;
                        RenderedSvg svg = PlantUmlSvgRenderer.render(puml);
                        return new RenderResult(puml, svg);
                    } catch (Throwable ex) {
                        error = ex;
                        return null;
                    }
                }

                @Override
                protected void done() {
                    if (error != null) {
                        if (pumlOnError != null) {
                            sourcePanel.setText(pumlOnError);
                        }
                        previewPanel.setSvgGraphicsNode(null, 0, 0);
                        renderedPuml = pumlOnError;
                        renderedSvgXml = null;
                        if (isActive()) {
                            mirrorToState();
                        }
                        showMessageCard(failureMessage(error));
                        setStatus(label + ": rendering failed -- " + failureReason(error));
                        return;
                    }
                    try {
                        RenderResult r = get();
                        if (r == null || r.svg == null) {
                            sourcePanel.setText(r != null ? r.puml : "");
                            renderedPuml = r != null ? r.puml : null;
                            renderedSvgXml = null;
                            if (isActive()) {
                                mirrorToState();
                            }
                            showMessageCard("<b>No diagram to display.</b><br><br>"
                                    + "Pick a class, package or method from the tree, "
                                    + "or choose another diagram kind from the toolbar.");
                            setStatus(label + ": (no diagram)");
                            return;
                        }
                        previewPanel.setSvgGraphicsNode(r.svg.getRoot(),
                                r.svg.getWidth(), r.svg.getHeight());
                        previewPanel.setLinkAreas(r.svg.getLinkAreas());
                        previewPanel.setTextItems(r.svg.getTextItems());
                        sourcePanel.setText(r.puml);
                        renderedPuml = r.puml;
                        renderedSvgXml = r.svg.getSvgXml();
                        showPreviewCard();
                        if (isActive()) {
                            mirrorToState();
                        }
                        setStatus(label + " rendered ("
                                + (int) Math.round(r.svg.getWidth()) + "x"
                                + (int) Math.round(r.svg.getHeight()) + ", SVG)");
                    } catch (Exception ex) {
                        showMessageCard(failureMessage(ex));
                        setStatus(label + ": " + ex.getMessage());
                    }
                }
            }.execute();
        }

        /** 描画失敗時にタブ内へ表示する案内 (原因 + 対処)。 */
        private String failureMessage(Throwable error) {
            StringBuilder sb = new StringBuilder();
            sb.append("<b>Couldn't render this diagram.</b><br>")
              .append(esc(failureReason(error)))
              .append("<br><br>");
            if (!juml.core.formats.uml.PlantUmlRenderer.isGraphvizAvailable()) {
                // Graphviz 無効時は純 Java の Smetana レイアウトになり、大きな図で破綻しやすい。
                // dot を有効化すれば描画できる可能性が高いので、最優先で案内する。
                sb.append("Graphviz が無効のため、純 Java の Smetana レイアウトを使用しています。")
                  .append("Smetana は大きな図を描画できないことがあります。<br>")
                  .append("<b>Graphviz (dot) を有効にすると改善します:</b><br>")
                  .append("• Diagram → Enable Graphviz (dot)… で検出 / 指定<br>")
                  .append("• 未インストールなら graphviz.org からインストール<br><br>")
                  .append("または図を小さくする:<br>");
            } else {
                sb.append("The diagram may be too large for the layout engine. Try:<br>");
            }
            sb.append("• selecting a single package / class / method in the tree<br>")
              .append("• applying a preset (Diagram → Preset) to reduce detail<br>")
              .append("• narrowing the scope (Diagram → Scope…)<br><br>")
              .append("The generated PlantUML is shown in the lower pane.");
            return sb.toString();
        }

        private String failureReason(Throwable error) {
            if (error instanceof juml.core.formats.uml.PlantUmlRenderFailedException) {
                return "PlantUML layout error (Smetana)";
            }
            String m = error != null ? error.getMessage() : null;
            return m == null ? (error != null ? error.getClass().getSimpleName() : "unknown")
                    : (m.length() > 160 ? m.substring(0, 159) + "…" : m);
        }

        private String esc(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private void handleLinkClick(LinkArea link, MouseEvent event) {
            if (link == null) {
                return;
            }
            String href = link.getHref();
            if (href == null) {
                return;
            }
            if (href.startsWith("juml://method/")) {
                showMethodMenuInTab(href, event);
                return;
            }
            String fqn = parseClassFqnFromHref(href);
            if (fqn == null) {
                return;
            }
            cache.getIndex().header(fqn).ifPresent(
                    ci -> addOrFocusTab(TreeNodeOpenRequest.classNode(ci)));
        }

        private void showMethodMenuInTab(String href, MouseEvent event) {
            String path = href.substring("juml://method/".length());
            int hash = path.lastIndexOf('#');
            if (hash < 0) {
                return;
            }
            String classFqn = path.substring(0, hash);
            String methodName = path.substring(hash + 1);
            if (classFqn.isEmpty() || methodName.isEmpty()) {
                return;
            }
            JavaClassInfo classInfo = cache.getIndex().header(classFqn).orElse(null);
            if (classInfo == null) {
                classInfo = new JavaClassInfo();
                classInfo.setSimpleName(extractSimpleClass(classFqn));
            }
            JavaMethodInfo methodInfo = new JavaMethodInfo();
            methodInfo.setName(methodName);
            final JavaClassInfo ci = classInfo;
            final JavaMethodInfo mi = methodInfo;
            JPopupMenu menu = new JPopupMenu();
            JMenuItem seqItem = new JMenuItem("Sequence Diagram");
            seqItem.addActionListener(e -> addOrFocusTab(
                    TreeNodeOpenRequest.method(ci, mi, DiagramKind.SEQUENCE)));
            menu.add(seqItem);
            JMenuItem actItem = new JMenuItem("Activity Diagram");
            actItem.addActionListener(e -> addOrFocusTab(
                    TreeNodeOpenRequest.method(ci, mi, DiagramKind.ACTIVITY)));
            menu.add(actItem);
            menu.show(event.getComponent(), event.getX(), event.getY());
        }

        private void handleLinkPopup(LinkArea link, MouseEvent event) {
            if (event == null) {
                return;
            }
            JPopupMenu popup = new JPopupMenu("Export");
            JMenuItem saveSvg = new JMenuItem("Save as SVG...");
            saveSvg.addActionListener(e -> exportTabAs(UmlExporter.Format.SVG));
            popup.add(saveSvg);
            JMenuItem savePng = new JMenuItem("Save as PNG...");
            savePng.addActionListener(e -> exportTabAs(UmlExporter.Format.PNG));
            popup.add(savePng);
            JMenuItem savePuml = new JMenuItem("Save as PlantUML...");
            savePuml.addActionListener(e -> exportTabAs(UmlExporter.Format.PUML));
            popup.add(savePuml);
            popup.show(event.getComponent(), event.getX(), event.getY());
        }

        private void exportTabAs(UmlExporter.Format fmt) {
            String puml = renderedPuml;
            if (puml == null || puml.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "No diagram to export yet.", "Export",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            String ext = fmt.getExtension();
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Save diagram as " + ext.toUpperCase());
            fc.setAcceptAllFileFilterUsed(false);
            fc.setFileFilter(new FileNameExtensionFilter(
                    ext.toUpperCase() + " (*." + ext + ")", ext));
            Window owner = SwingUtilities.getWindowAncestor(this);
            int r = fc.showSaveDialog(owner);
            if (r != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File chosen = fc.getSelectedFile();
            if (!chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + ext)) {
                chosen = new File(chosen.getAbsolutePath() + "." + ext);
            }
            try {
                java.awt.image.BufferedImage img = null;
                if (fmt == UmlExporter.Format.PNG) {
                    img = PlantUmlImageRenderer.toBufferedImage(puml);
                }
                UmlExporter.export(fmt, chosen, puml, img);
                reportStatus("Saved: " + chosen.getAbsolutePath());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static DiagramRequest toDiagramRequest(TreeNodeOpenRequest req) {
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
                DiagramScope cs = DiagramScope.builder().seed(fqn).neighborHops(1).build();
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
            default:
                return new DiagramRequest(DiagramKind.CLASS);
        }
    }

    private static final class RenderResult {
        final String puml;
        final RenderedSvg svg;

        RenderResult(String puml, RenderedSvg svg) {
            this.puml = puml;
            this.svg = svg;
        }
    }

    private static String parseClassFqnFromHref(String href) {
        if (href == null) {
            return null;
        }
        final String prefix = "juml://class/";
        if (!href.startsWith(prefix)) {
            return null;
        }
        String s = href.substring(prefix.length()).trim();
        return s.isEmpty() ? null : s;
    }

    private static String extractSimpleClass(String qn) {
        if (qn == null || qn.isEmpty()) {
            return "";
        }
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
    }
}
