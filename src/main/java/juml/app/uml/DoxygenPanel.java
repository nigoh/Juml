// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.doxygen.DoxCompound;
import juml.core.formats.doxygen.DoxMember;
import juml.core.formats.doxygen.DoxModel;
import juml.core.formats.doxygen.DoxParam;
import juml.core.formats.doxygen.DoxygenLocator;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

/**
 * Doxygen で対象 Java プロジェクトを解析し、生成された XML をネイティブ表示する解析タブ。
 *
 * <p>「Run Doxygen」で {@link DoxygenRunner} を {@link SwingWorker} 上で起動し、生成 XML を
 * {@link DoxygenXmlParser} でパースする。左ペインにクラス/インタフェース → メンバーのツリー、
 * 右ペインに選択シンボルの API リファレンス (brief/detailed・{@code @param}/{@code @return}/
 * {@code @throws}) を整形表示する (R2)。doxygen の生成 HTML は使わず、自前の簡易 HTML を
 * {@link JEditorPane} で描く。</p>
 *
 * <p>doxygen 未検出時は「Locate doxygen...」で実行ファイルを手動選択できる
 * ({@link DoxygenLocator#useDoxygenBinary(File)})。同梱 ({@code bundle/doxygen/}) の配線は後段。</p>
 */
public final class DoxygenPanel extends JPanel {

    private final ProjectAnalysisCache projectCache;
    private final DoxygenResultCache resultCache;
    private final JButton runButton = new JButton(Messages.get("doxygen.btn.run"));
    private final JButton locateButton = new JButton(Messages.get("doxygen.btn.locate"));
    private final JLabel statusLabel = new JLabel(" ");
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Doxygen");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JEditorPane detail = new JEditorPane();

    public DoxygenPanel(ProjectAnalysisCache projectCache, DoxygenResultCache resultCache) {
        super(new BorderLayout(0, 4));
        if (projectCache == null || resultCache == null) {
            throw new IllegalArgumentException("projectCache/resultCache");
        }
        this.projectCache = projectCache;
        this.resultCache = resultCache;
        resultCache.addListener(this::onModelUpdated);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        input.add(runButton);
        input.add(locateButton);
        input.add(new JLabel(Messages.get("doxygen.hint")));
        add(input, BorderLayout.NORTH);

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> showSelected());

        detail.setEditable(false);
        detail.setContentType("text/html");
        detail.setText(placeholderHtml());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree), new JScrollPane(detail));
        split.setResizeWeight(0.4);
        split.setDividerLocation(360);
        add(split, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);

        runButton.addActionListener(this::onRun);
        locateButton.addActionListener(this::onLocate);
        refreshLocateVisibility();
    }

    /** doxygen が見つかっていれば「Locate」ボタンを隠す。 */
    private void refreshLocateVisibility() {
        locateButton.setVisible(!DoxygenLocator.isAvailable());
    }

    private void onLocate(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(Messages.get("doxygen.selectExe"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        if (DoxygenLocator.useDoxygenBinary(fc.getSelectedFile())) {
            statusLabel.setText(java.text.MessageFormat.format(
                    Messages.get("doxygen.status.set"), DoxygenLocator.getDoxygenPath()));
            refreshLocateVisibility();
        } else {
            statusLabel.setText(java.text.MessageFormat.format(
                    Messages.get("doxygen.status.notExe"), fc.getSelectedFile()));
        }
    }

    private void onRun(ActionEvent e) {
        if (!projectCache.isLoaded()) {
            statusLabel.setText(Messages.get("dlg.noProject.message"));
            return;
        }
        File root = projectCache.getProjectRoot();
        if (root == null || !root.isDirectory()) {
            statusLabel.setText(Messages.get("doxygen.status.needsDir"));
            return;
        }
        if (!DoxygenLocator.isAvailable() && !DoxygenLocator.redetect()) {
            statusLabel.setText(Messages.get("doxygen.status.notFound"));
            refreshLocateVisibility();
            return;
        }
        resultCache.runAsync(root,
                () -> {
                    runButton.setEnabled(false);
                    locateButton.setEnabled(false);
                    statusLabel.setText(Messages.get("doxygen.status.running"));
                },
                msg -> statusLabel.setText(java.text.MessageFormat.format(
                        Messages.get("doxygen.status.failed"), msg)),
                () -> {
                    runButton.setEnabled(true);
                    locateButton.setEnabled(true);
                    refreshLocateVisibility();
                });
    }

    /** 共有キャッシュ更新時にツリーを作り直す (自タブの実行・他タブの実行どちらでも)。 */
    private void onModelUpdated() {
        DoxModel model = resultCache.getModel();
        if (model != null) {
            populateTree(model);
        }
    }

    /** パース結果をツリーへ流し込む。 */
    private void populateTree(DoxModel model) {
        rootNode.removeAllChildren();
        int memberCount = 0;
        for (DoxCompound c : model.getCompounds()) {
            DefaultMutableTreeNode cNode = new DefaultMutableTreeNode(new CompoundLabel(c));
            for (DoxMember m : c.getMembers()) {
                cNode.add(new DefaultMutableTreeNode(new MemberLabel(m)));
                memberCount++;
            }
            rootNode.add(cNode);
        }
        treeModel.reload();
        detail.setText(placeholderHtml());
        if (model.isEmpty()) {
            statusLabel.setText(Messages.get("doxygen.status.noTypes"));
        } else {
            tree.expandPath(new TreePath(rootNode.getPath()));
            statusLabel.setText(model.getCompounds().size() + " types, "
                    + memberCount + " members. Select one to see its API reference.");
        }
    }

    /** ツリー選択に応じて右ペインへ API リファレンスを描く。 */
    private void showSelected() {
        Object node = tree.getLastSelectedPathComponent();
        if (!(node instanceof DefaultMutableTreeNode)) {
            return;
        }
        Object user = ((DefaultMutableTreeNode) node).getUserObject();
        if (user instanceof CompoundLabel) {
            detail.setText(renderCompound(((CompoundLabel) user).compound));
            detail.setCaretPosition(0);
        } else if (user instanceof MemberLabel) {
            detail.setText(renderMember(((MemberLabel) user).member));
            detail.setCaretPosition(0);
        }
    }

    /** compound (クラス/インタフェース等) の API リファレンス HTML。 */
    private static String renderCompound(DoxCompound c) {
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif'>");
        sb.append("<h2>").append(esc("[" + c.getKind() + "] " + c.getName())).append("</h2>");
        appendPara(sb, c.getBrief(), true);
        appendPara(sb, c.getDetailed(), false);
        appendNameList(sb, "Base types", c.getBaseTypes());
        appendNameList(sb, "Known subtypes", c.getDerivedTypes());
        sb.append("</body></html>");
        return sb.toString();
    }

    /** メンバー (メソッド/フィールド等) の API リファレンス HTML。 */
    private static String renderMember(DoxMember m) {
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif'>");
        String sig = (m.getType().isEmpty() ? "" : m.getType() + " ") + m.displayLabel();
        sb.append("<h2>").append(esc(sig)).append("</h2>");
        sb.append("<p style='color:gray'>").append(esc(m.getKind())).append("</p>");
        appendPara(sb, m.getBrief(), true);
        appendPara(sb, m.getDetailed(), false);
        appendParams(sb, "Parameters", m.getParams());
        if (!m.getReturns().isEmpty()) {
            sb.append("<p><b>Returns:</b> ").append(esc(m.getReturns())).append("</p>");
        }
        appendParams(sb, "Throws", m.getThrows());
        appendNameList(sb, "References", m.getReferences());
        appendNameList(sb, "Referenced by", m.getReferencedBy());
        sb.append("</body></html>");
        return sb.toString();
    }

    /** 名前リスト (基底型 / 参照先など) を箇条書きで追記する。空なら何もしない。 */
    private static void appendNameList(StringBuilder sb, String title, List<String> names) {
        if (names.isEmpty()) {
            return;
        }
        sb.append("<p><b>").append(title).append(":</b></p><ul>");
        for (String n : names) {
            sb.append("<li><code>").append(esc(n)).append("</code></li>");
        }
        sb.append("</ul>");
    }

    private static void appendPara(StringBuilder sb, String text, boolean bold) {
        if (text == null || text.isEmpty()) {
            return;
        }
        sb.append("<p>");
        if (bold) {
            sb.append("<b>").append(esc(text)).append("</b>");
        } else {
            sb.append(esc(text));
        }
        sb.append("</p>");
    }

    private static void appendParams(StringBuilder sb, String title, List<DoxParam> params) {
        if (params.isEmpty()) {
            return;
        }
        sb.append("<p><b>").append(title).append(":</b></p><ul>");
        for (DoxParam p : params) {
            sb.append("<li><code>").append(esc(p.getName())).append("</code>");
            if (!p.getDescription().isEmpty()) {
                sb.append(" &mdash; ").append(esc(p.getDescription()));
            }
            sb.append("</li>");
        }
        sb.append("</ul>");
    }

    private static String placeholderHtml() {
        return "<html><body style='font-family:sans-serif;color:gray'>"
                + "<p>" + esc(Messages.get("doxygen.placeholder")) + "</p></body></html>";
    }

    /** ツリー上の compound ノード表示 (種別 + 短い名前、brief を淡色で添える)。 */
    private static final class CompoundLabel {
        private final DoxCompound compound;

        CompoundLabel(DoxCompound compound) {
            this.compound = compound;
        }

        @Override
        public String toString() {
            String label = "[" + compound.getKind() + "] " + compound.simpleName();
            return compound.getBrief().isEmpty() ? label : "<html>" + esc(label)
                    + " &nbsp;<font color=\"gray\">" + esc(compound.getBrief()) + "</font></html>";
        }
    }

    /** ツリー上のメンバーノード表示。 */
    private static final class MemberLabel {
        private final DoxMember member;

        MemberLabel(DoxMember member) {
            this.member = member;
        }

        @Override
        public String toString() {
            String label = member.displayLabel();
            return member.getBrief().isEmpty() ? label : "<html>" + esc(label)
                    + " &nbsp;<font color=\"gray\">" + esc(member.getBrief()) + "</font></html>";
        }
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
