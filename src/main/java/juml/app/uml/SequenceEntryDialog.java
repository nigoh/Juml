// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.PlantUmlSequenceDiagram;
import juml.core.formats.uml.Visibility;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * シーケンス図の起点となるメソッドを階層 (パッケージ → クラス → メソッド) から
 * 選択するモーダルダイアログ。
 *
 * <p>{@link PlantUmlSequenceDiagram#listCandidates(java.util.List)} で取得した
 * 候補メソッドを {@link JTree} に並べ、テキストフィールドで部分一致絞り込みできる。
 * OK / Enter / メソッドノードのダブルクリックで選択結果を確定し、
 * {@link #getSelectedEntry()} から {@code "Class.method"} 形式で取り出せる。
 * キャンセル時は null。</p>
 */
public class SequenceEntryDialog extends JDialog {

    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Sequence Entries");
    private final DefaultTreeModel treeModel = new DefaultTreeModel(root);
    private final JTree tree = new JTree(treeModel);
    private final JTextField filter = new JTextField();
    private final JLabel countLabel = new JLabel(" ");

    private final List<Entry> allEntries = new ArrayList<>();
    private String selectedEntry;
    private final Timer debounceTimer = new Timer(150, e -> rebuildTree(filter.getText()));

    public SequenceEntryDialog(Frame owner, List<JavaClassInfo> classes) {
        super(owner, Messages.get("dlg.selectSequenceEntry.title"), true);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        collectEntries(classes);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(new JLabel(Messages.get("dlg.seqEntry.filterLabel")),
                BorderLayout.NORTH);
        filter.putClientProperty("JTextField.placeholderText",
                Messages.get("dlg.seqEntry.filterPlaceholder"));
        north.add(filter, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        rebuildTree("");
        add(new JScrollPane(tree), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(countLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton ok = new JButton(Messages.get("dlg.ok"));
        JButton cancel = new JButton(Messages.get("dlg.cancel"));
        buttons.add(ok);
        buttons.add(cancel);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        ok.addActionListener(e -> commit());
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

        setPreferredSize(new Dimension(600, 600));
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(filter::requestFocusInWindow);
    }

    private void collectEntries(List<JavaClassInfo> classes) {
        // クラス名 → パッケージ名のマップ。Candidate にはパッケージ情報が含まれないため
        // ここで JavaClassInfo から逆引きする。
        Map<String, String> classToPackage = new LinkedHashMap<>();
        if (classes != null) {
            for (JavaClassInfo c : classes) {
                String pkg = c.getPackageName();
                classToPackage.put(c.getSimpleName(),
                        pkg == null || pkg.isEmpty() ? "(default)" : pkg);
            }
        }
        for (PlantUmlSequenceDiagram.Candidate c
                : PlantUmlSequenceDiagram.listCandidates(classes)) {
            String pkg = classToPackage.getOrDefault(c.className, "(default)");
            allEntries.add(new Entry(pkg, c.className, c.methodName,
                    c.callCount, c.visibility));
        }
    }

    private void rebuildTree(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        root.removeAllChildren();

        // パッケージ → クラス → メソッド の階層を組み立てる
        Map<String, Map<String, List<Entry>>> grouped = new TreeMap<>();
        int matched = 0;
        for (Entry e : allEntries) {
            if (!matches(e, q)) {
                continue;
            }
            grouped.computeIfAbsent(e.packageName, k -> new TreeMap<>())
                    .computeIfAbsent(e.className, k -> new ArrayList<>())
                    .add(e);
            matched++;
        }

        for (Map.Entry<String, Map<String, List<Entry>>> p : grouped.entrySet()) {
            int pkgCount = 0;
            for (List<Entry> v : p.getValue().values()) {
                pkgCount += v.size();
            }
            DefaultMutableTreeNode pkgNode = new DefaultMutableTreeNode(
                    new PackageNode(p.getKey(), pkgCount));
            for (Map.Entry<String, List<Entry>> c : p.getValue().entrySet()) {
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(
                        new ClassNode(c.getKey(), c.getValue().size()));
                List<Entry> methods = new ArrayList<>(c.getValue());
                methods.sort((a, b) -> {
                    if (a.callCount != b.callCount) {
                        return Integer.compare(b.callCount, a.callCount);
                    }
                    return a.methodName.compareTo(b.methodName);
                });
                for (Entry m : methods) {
                    classNode.add(new DefaultMutableTreeNode(new MethodNode(m)));
                }
                pkgNode.add(classNode);
            }
            root.add(pkgNode);
        }
        treeModel.reload();

        countLabel.setForeground(null);
        countLabel.setText(matched + " / " + allEntries.size()
                + " " + Messages.get("dlg.seqEntry.methods"));

        // フィルタあり: 全展開してマッチ結果を見渡せるように
        // フィルタなし: パッケージのみ展開し、クラス以下は折りたたんで一覧性を確保
        if (!q.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                tree.expandRow(i);
            }
        } else {
            for (int i = 0; i < root.getChildCount(); i++) {
                TreeNode child = root.getChildAt(i);
                tree.expandPath(new TreePath(new Object[]{root, child}));
            }
        }

        selectFirstMethod();
    }

    private boolean matches(Entry e, String q) {
        if (q.isEmpty()) {
            return true;
        }
        if (e.packageName.toLowerCase().contains(q)) {
            return true;
        }
        if (e.className.toLowerCase().contains(q)) {
            return true;
        }
        if (e.methodName.toLowerCase().contains(q)) {
            return true;
        }
        return (e.className + "." + e.methodName).toLowerCase().contains(q);
    }

    private void selectFirstMethod() {
        TreeNode found = findFirstMethodNode(root);
        if (found != null) {
            TreePath path = new TreePath(treeModel.getPathToRoot(found));
            tree.setSelectionPath(path);
            tree.scrollPathToVisible(path);
        }
    }

    private TreeNode findFirstMethodNode(TreeNode node) {
        if (node instanceof DefaultMutableTreeNode
                && ((DefaultMutableTreeNode) node).getUserObject() instanceof MethodNode) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TreeNode n = findFirstMethodNode(node.getChildAt(i));
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
            if (u instanceof MethodNode) {
                selectedEntry = ((MethodNode) u).entry.entry();
                dispose();
                return;
            }
        }
        countLabel.setForeground(java.awt.Color.RED);
        countLabel.setText(Messages.get("dlg.seqEntry.selectMethod"));
        java.awt.Toolkit.getDefaultToolkit().beep();
    }

    /** モーダル終了後、選択された {@code Class.method} を返す。キャンセル時は null。 */
    public String getSelectedEntry() {
        return selectedEntry;
    }

    public int getCandidateCount() {
        return allEntries.size();
    }

    /** ツリー構築用のフラットな (パッケージ, クラス, メソッド) エントリ。 */
    private static final class Entry {
        final String packageName;
        final String className;
        final String methodName;
        final int callCount;
        final Visibility visibility;

        Entry(String packageName, String className, String methodName,
              int callCount, Visibility visibility) {
            this.packageName = packageName;
            this.className = className;
            this.methodName = methodName;
            this.callCount = callCount;
            this.visibility = visibility == null ? Visibility.PACKAGE : visibility;
        }

        String entry() {
            return className + "." + methodName;
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
            return "[pkg] " + name + " (" + count + ")";
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

    private static final class MethodNode {
        final Entry entry;

        MethodNode(Entry entry) {
            this.entry = entry;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(entry.visibility.mark()).append(' ');
            sb.append(entry.methodName).append("()");
            if (entry.callCount > 0) {
                sb.append("  — ").append(entry.callCount).append(" call");
                if (entry.callCount != 1) {
                    sb.append('s');
                }
            }
            return sb.toString();
        }
    }
}
