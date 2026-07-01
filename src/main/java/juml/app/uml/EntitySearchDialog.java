// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * クラス・メソッド・フィールドを横断検索するモーダルダイアログ。
 *
 * <p>{@link SequenceEntryDialog} のメソッド限定検索を拡張し、
 * 部分一致フィルタで 3 種類のエンティティを同時に絞り込める。
 * 選択結果は {@link Entry} オブジェクトとして返し、呼び出し側が kind に応じて
 * クラス図/シーケンス図の表示に切り替える。</p>
 */
public class EntitySearchDialog extends JDialog {

    /** 検索エントリの種類。 */
    public enum Kind { CLASS, METHOD, FIELD }

    /** 検索結果の 1 項目。 */
    public static final class Entry {
        public final Kind kind;
        /** 所属クラスの完全修飾名 (CLASS kind なら自身の FQN)。 */
        public final String ownerQn;
        /** シンプル名 (クラス/メソッド/フィールドの名前)。 */
        public final String simpleName;
        /** 型情報またはシグネチャ (フィールド型 / メソッドシグネチャ / クラス kind 名)。 */
        public final String typeOrSignature;
        public final Visibility visibility;
        /** フィールドが inline メソッド (リスナ初期化子) を持つ場合 true。UI 上の印付け用。 */
        public final boolean hasInlineMethods;

        public Entry(Kind kind, String ownerQn, String simpleName,
                     String typeOrSignature, Visibility visibility,
                     boolean hasInlineMethods) {
            this.kind = kind;
            this.ownerQn = ownerQn;
            this.simpleName = simpleName;
            this.typeOrSignature = typeOrSignature == null ? "" : typeOrSignature;
            this.visibility = visibility == null ? Visibility.PACKAGE : visibility;
            this.hasInlineMethods = hasInlineMethods;
        }
    }

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Entities");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);
    private final JTextField filter = new JTextField();
    private final JLabel countLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");
    private final Timer errorClearTimer = new Timer(3000, e -> errorLabel.setText(" "));
    private final EnumMap<Kind, JCheckBox> kindFilters = new EnumMap<>(Kind.class);

    private final List<Entry> allEntries = new ArrayList<>();
    private Entry selectedEntry;
    /** OK ボタンの代わりに「Drill-down」を押したとき true。詳細図への遷移要求。 */
    private boolean drillDownRequested = false;
    private final Timer debounceTimer = new Timer(150, e -> rebuildTree(filter.getText()));

    public EntitySearchDialog(Frame owner, List<JavaClassInfo> classes) {
        super(owner, Messages.get("dlg.searchEntities.title"), true);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        collectEntries(classes);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(new JLabel(juml.util.Messages.get("dlg.search.filter")),
                BorderLayout.NORTH);
        filter.putClientProperty("JTextField.placeholderText",
                Messages.get("dlg.search.placeholder"));
        north.add(filter, BorderLayout.CENTER);
        north.add(buildKindFilterBar(), BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        rebuildTree("");
        add(new JScrollPane(tree), BorderLayout.CENTER);

        errorClearTimer.setRepeats(false);
        errorLabel.setForeground(java.awt.Color.RED);
        errorLabel.setVisible(false);

        JPanel south = new JPanel(new BorderLayout());
        JPanel infoPanel = new JPanel(new java.awt.GridLayout(2, 1));
        infoPanel.add(countLabel);
        infoPanel.add(errorLabel);
        south.add(infoPanel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton drill = new JButton(juml.util.Messages.get("dlg.search.drilldown"));
        drill.setToolTipText(juml.util.Messages.get("dlg.search.drilldown.tip"));
        JButton ok = new JButton(Messages.get("dlg.ok"));
        JButton cancel = new JButton(Messages.get("dlg.cancel"));
        buttons.add(drill);
        buttons.add(ok);
        buttons.add(cancel);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        ok.addActionListener(e -> commit());
        drill.addActionListener(e -> commitDrillDown());
        cancel.addActionListener(e -> dispose());
        DialogUtils.installEscapeAndDefault(this, ok);

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    commit();
                }
            }
        });
        tree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    commit();
                    e.consume();
                }
            }
        });
        filter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    commit();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    tree.requestFocusInWindow();
                    e.consume();
                }
            }
        });
        debounceTimer.setRepeats(false);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounceTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounceTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounceTimer.restart();
            }
        });

        setPreferredSize(new Dimension(640, 640));
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(filter::requestFocusInWindow);
    }

    /**
     * Kind フィルタ (Class / Method / Field の 3 チェックボックス) を組み立てる。
     * 既定はすべて ON、各チェック切り替えでツリーを再構築する。
     */
    private JPanel buildKindFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(new JLabel(Messages.get("dlg.search.kind")));
        // ButtonGroup は使わない (排他にしない) — マルチ選択
        java.awt.event.ActionListener handler = e -> rebuildTree(filter.getText());
        for (Kind k : Kind.values()) {
            JCheckBox cb = new JCheckBox(k.name().toLowerCase(), true);
            cb.addActionListener(handler);
            kindFilters.put(k, cb);
            bar.add(cb);
        }
        return bar;
    }

    private void collectEntries(List<JavaClassInfo> classes) {
        allEntries.addAll(collectEntriesStatic(classes));
    }

    /**
     * UI を起こさずに検索エントリ一覧を構築する。テストおよび pure-Java 経路から
     * 利用される。クラス・メソッド (abstract 除外)・フィールドの全てを列挙する。
     */
    public static List<Entry> collectEntriesStatic(List<JavaClassInfo> classes) {
        List<Entry> out = new ArrayList<>();
        if (classes == null) {
            return out;
        }
        for (JavaClassInfo c : classes) {
            String qn = c.getQualifiedName();
            out.add(new Entry(Kind.CLASS, qn, c.getSimpleName(),
                    c.getKind().name(), null, false));
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.isAbstract()) {
                    continue;
                }
                out.add(new Entry(Kind.METHOD, qn, m.getName(),
                        buildMethodSignature(m), m.getVisibility(), false));
            }
            for (JavaFieldInfo f : c.getFields()) {
                boolean hasInline = !f.getInlineMethods().isEmpty();
                out.add(new Entry(Kind.FIELD, qn, f.getName(),
                        f.getType() == null ? "" : f.getType(),
                        f.getVisibility(), hasInline));
            }
        }
        return out;
    }

    private static String buildMethodSignature(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        List<String> types = m.getParameterTypes();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(types.get(i) == null ? "?" : types.get(i));
        }
        sb.append(')');
        if (m.getReturnType() != null && !m.getReturnType().isEmpty()) {
            sb.append(": ").append(m.getReturnType());
        }
        return sb.toString();
    }

    /**
     * 検索クエリと kind フィルタに従って、結果ツリーを再構築する。
     * 上位ノードは Kind (Class / Method / Field) → パッケージ → クラス → 項目 の階層。
     */
    private void rebuildTree(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        root.removeAllChildren();

        // Kind → Package → Class → List<Entry>
        Map<Kind, Map<String, Map<String, List<Entry>>>> grouped = new EnumMap<>(Kind.class);
        for (Kind k : Kind.values()) {
            grouped.put(k, new TreeMap<>());
        }
        int matched = 0;
        for (Entry e : allEntries) {
            if (!isKindEnabled(e.kind)) {
                continue;
            }
            if (!matches(e, q)) {
                continue;
            }
            String pkg = extractPackage(e.ownerQn);
            String cls = extractSimpleClass(e.ownerQn);
            grouped.get(e.kind)
                    .computeIfAbsent(pkg, k -> new TreeMap<>())
                    .computeIfAbsent(cls, k -> new ArrayList<>())
                    .add(e);
            matched++;
        }

        for (Kind k : Kind.values()) {
            Map<String, Map<String, List<Entry>>> pkgs = grouped.get(k);
            if (pkgs.isEmpty()) {
                continue;
            }
            int kCount = 0;
            for (Map<String, List<Entry>> v : pkgs.values()) {
                for (List<Entry> es : v.values()) {
                    kCount += es.size();
                }
            }
            DefaultMutableTreeNode kindNode = new DefaultMutableTreeNode(
                    new KindNode(k, kCount));
            for (Map.Entry<String, Map<String, List<Entry>>> pe : pkgs.entrySet()) {
                int pkgCount = 0;
                for (List<Entry> es : pe.getValue().values()) {
                    pkgCount += es.size();
                }
                DefaultMutableTreeNode pkgNode = new DefaultMutableTreeNode(
                        new PackageNode(pe.getKey(), pkgCount));
                for (Map.Entry<String, List<Entry>> ce : pe.getValue().entrySet()) {
                    if (k == Kind.CLASS) {
                        // クラス階層は冗長になるので CLASS kind では package 直下に並べる
                        for (Entry entry : ce.getValue()) {
                            pkgNode.add(new DefaultMutableTreeNode(new EntryNode(entry)));
                        }
                    } else {
                        DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(
                                new ClassNode(ce.getKey(), ce.getValue().size()));
                        for (Entry entry : ce.getValue()) {
                            classNode.add(new DefaultMutableTreeNode(new EntryNode(entry)));
                        }
                        pkgNode.add(classNode);
                    }
                }
                kindNode.add(pkgNode);
            }
            root.add(kindNode);
        }
        treeModel.reload();

        errorLabel.setVisible(false);
        countLabel.setText(matched + " / " + allEntries.size()
                + " " + Messages.get("dlg.search.entries"));

        if (!q.isEmpty()) {
            // フィルタあり: 全展開で結果を見渡せるように
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        } else {
            // フィルタなし: kind ノードまでだけ展開
            for (int i = 0; i < root.getChildCount(); i++) {
                TreeNode child = root.getChildAt(i);
                tree.expandPath(new TreePath(new Object[]{root, child}));
            }
        }

        selectFirstEntry();
    }

    private boolean isKindEnabled(Kind k) {
        JCheckBox cb = kindFilters.get(k);
        return cb == null || cb.isSelected();
    }

    /** インスタンスメソッド経由 (UI 用)。実体は {@link #matchesEntry(Entry, String)} に委譲。 */
    private boolean matches(Entry e, String q) {
        return matchesEntry(e, q);
    }

    /** 検索クエリ (lower-case 済) に対するマッチ判定。pure-Java 経路で再利用される。 */
    public static boolean matchesEntry(Entry e, String q) {
        if (q == null || q.isEmpty()) {
            return true;
        }
        if (e.simpleName.toLowerCase().contains(q)) {
            return true;
        }
        if (e.ownerQn.toLowerCase().contains(q)) {
            return true;
        }
        if (e.typeOrSignature.toLowerCase().contains(q)) {
            return true;
        }
        return false;
    }

    private static String extractPackage(String qn) {
        int dot = qn.lastIndexOf('.');
        if (dot < 0) {
            return "(default)";
        }
        return qn.substring(0, dot);
    }

    private static String extractSimpleClass(String qn) {
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
    }

    private void selectFirstEntry() {
        TreeNode found = findFirstEntryNode(root);
        if (found != null) {
            TreePath path = new TreePath(treeModel.getPathToRoot(found));
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private TreeNode findFirstEntryNode(TreeNode node) {
        if (node instanceof DefaultMutableTreeNode
                && ((DefaultMutableTreeNode) node).getUserObject() instanceof EntryNode) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode n = findFirstEntryNode(node.getChildAt(i));
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    private void commit() {
        Object last = tree.getLastSelectedPathComponent();
        if (last instanceof DefaultMutableTreeNode) {
            Object u = ((DefaultMutableTreeNode) last).getUserObject();
            if (u instanceof EntryNode) {
                selectedEntry = ((EntryNode) u).entry;
                dispose();
                return;
            }
        }
        errorLabel.setText(Messages.get("dlg.search.selectEntry"));
        errorLabel.setVisible(true);
        errorClearTimer.restart();
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    /** 選択中のエントリを「ドリルダウン要求」として確定し、ダイアログを閉じる。 */
    private void commitDrillDown() {
        Object last = tree.getLastSelectedPathComponent();
        if (last instanceof DefaultMutableTreeNode) {
            Object u = ((DefaultMutableTreeNode) last).getUserObject();
            if (u instanceof EntryNode) {
                selectedEntry = ((EntryNode) u).entry;
                drillDownRequested = true;
                dispose();
            }
        }
    }

    /** モーダル終了後に取得する選択結果。キャンセル時は null。 */
    public Entry getResult() {
        return selectedEntry;
    }

    /** OK ではなく Drill-down ボタンで閉じた場合 true。{@link #getResult()} とセットで参照する。 */
    public boolean isDrillDownRequested() {
        return drillDownRequested;
    }

    /** テスト用: 総候補件数。 */
    public int getCandidateCount() {
        return allEntries.size();
    }

    /**
     * テストおよびヘッドレス環境向け: 与えたクエリでフィルタした結果の Entry リストを返す。
     * Swing インスタンスを生成しないので、X11 が無い環境でも動作する。
     */
    public static List<Entry> filter(List<JavaClassInfo> classes, String query) {
        List<Entry> all = collectEntriesStatic(classes);
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (matchesEntry(e, q)) {
                out.add(e);
            }
        }
        return out;
    }

    // --- ノードクラス ---

    private static final class KindNode {
        final Kind kind;
        final int count;

        KindNode(Kind kind, int count) {
            this.kind = kind;
            this.count = count;
        }

        @Override
        public String toString() {
            return "[" + kind.name() + "] (" + count + ")";
        }
    }

    private static final class PackageNode {
        final String name;
        final int count;

        PackageNode(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public String toString() {
            return name + " (" + count + ")";
        }
    }

    private static final class ClassNode {
        final String name;
        final int count;

        ClassNode(String name, int count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public String toString() {
            return "[C] " + name + " (" + count + ")";
        }
    }

    private static final class EntryNode {
        final Entry entry;

        EntryNode(Entry entry) {
            this.entry = entry;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            switch (entry.kind) {
                case CLASS:
                    sb.append("[C] ").append(entry.simpleName);
                    if (!entry.typeOrSignature.isEmpty()
                            && !"CLASS".equals(entry.typeOrSignature)) {
                        sb.append("  <").append(entry.typeOrSignature.toLowerCase())
                                .append('>');
                    }
                    break;
                case METHOD:
                    sb.append(entry.visibility.mark()).append(' ')
                            .append(entry.simpleName)
                            .append(entry.typeOrSignature);
                    break;
                case FIELD:
                    sb.append(entry.visibility.mark()).append(' ')
                            .append(entry.simpleName);
                    if (!entry.typeOrSignature.isEmpty()) {
                        sb.append(": ").append(entry.typeOrSignature);
                    }
                    if (entry.hasInlineMethods) {
                        // フィールド宣言時に匿名クラス/ラムダで登録されたリスナー本体を保有
                        sb.append("  [+inline]");
                    }
                    break;
                default:
                    sb.append(entry.simpleName);
                    break;
            }
            return sb.toString();
        }
    }
}
