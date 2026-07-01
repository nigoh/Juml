// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidLayoutInfo;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Layout 図のターゲット {@code res/layout/*.xml} を選択するモーダルダイアログ。
 *
 * <p>{@link SequenceEntryDialog} と同じ構成で、フィルタ + 一覧 + OK/Cancel ボタンを持つ。
 * 選択結果は {@link AndroidLayoutInfo#getKey()} 形式の文字列として
 * {@link #getSelectedKey()} で取り出せる。キャンセル時は null。</p>
 */
public class LayoutFileChooserDialog extends JDialog {

    private final DefaultListModel<AndroidLayoutInfo> model = new DefaultListModel<>();
    private final JList<AndroidLayoutInfo> list = new JList<>(model);
    private final JTextField filter = new JTextField();
    private final JLabel countLabel = new JLabel(" ");

    private final List<AndroidLayoutInfo> allLayouts = new ArrayList<>();
    private String selectedKey;

    public LayoutFileChooserDialog(Frame owner, List<AndroidLayoutInfo> layouts) {
        super(owner, Messages.get("dlg.selectLayout.title"), true);
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        if (layouts != null) {
            allLayouts.addAll(layouts);
            allLayouts.sort(Comparator
                    .comparing(AndroidLayoutInfo::getModuleName)
                    .thenComparing(AndroidLayoutInfo::getSourceSet)
                    .thenComparing(AndroidLayoutInfo::getFileName)
                    .thenComparing(AndroidLayoutInfo::getConfigQualifier));
        }

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(new JLabel(Messages.get("dlg.filter.layout")),
                BorderLayout.NORTH);
        north.add(filter, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> jlist, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(jlist, value, index,
                        isSelected, cellHasFocus);
                if (value instanceof AndroidLayoutInfo) {
                    setText(((AndroidLayoutInfo) value).displayLabel());
                }
                return this;
            }
        });
        rebuildList("");
        add(new JScrollPane(list), BorderLayout.CENTER);

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

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    commit();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
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
                    list.requestFocusInWindow();
                    e.consume();
                }
            }
        });
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuildList(filter.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuildList(filter.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuildList(filter.getText());
            }
        });

        setPreferredSize(new Dimension(560, 480));
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(filter::requestFocusInWindow);
    }

    /** 選択候補の総件数 (空チェック用)。 */
    public int getCandidateCount() {
        return allLayouts.size();
    }

    /** OK 確定後に呼ぶと選択された layout の {@link AndroidLayoutInfo#getKey()} を返す。 */
    public String getSelectedKey() {
        return selectedKey;
    }

    /**
     * プロジェクトキャッシュから layout を取得しモーダルダイアログを表示する、
     * GUI 呼び出し向けのワンショットヘルパー。
     *
     * <p>ロード未完了・layout が 0 件のときは案内ダイアログを出して null を返す。
     * ユーザーがキャンセルしたときも null を返す。</p>
     */
    public static String chooseLayoutKey(Frame parent, ProjectAnalysisCache cache) {
        if (cache == null || !cache.isLoaded()) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("dlg.noProject.message"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        AndroidProjectAnalysis analysis = cache.getAnalysis();
        List<AndroidLayoutInfo> layouts = analysis != null
                ? analysis.allLayouts()
                : Collections.emptyList();
        if (layouts.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("dlg.noLayout.message"),
                    Messages.get("dlg.noLayout.title"), JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        LayoutFileChooserDialog dlg = new LayoutFileChooserDialog(parent, layouts);
        dlg.setVisible(true);
        return dlg.getSelectedKey();
    }

    private void rebuildList(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        model.clear();
        int matched = 0;
        for (AndroidLayoutInfo info : allLayouts) {
            if (!matches(info, q)) {
                continue;
            }
            model.addElement(info);
            matched++;
        }
        countLabel.setText(java.text.MessageFormat.format(
                Messages.get("dlg.filter.countLayouts"), matched, allLayouts.size()));
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private static boolean matches(AndroidLayoutInfo info, String q) {
        if (q.isEmpty()) {
            return true;
        }
        if (info.getFileName().toLowerCase().contains(q)) {
            return true;
        }
        if (info.getModuleName().toLowerCase().contains(q)) {
            return true;
        }
        if (info.getSourceSet().toLowerCase().contains(q)) {
            return true;
        }
        if (info.getConfigQualifier() != null
                && info.getConfigQualifier().toLowerCase().contains(q)) {
            return true;
        }
        return false;
    }

    private void commit() {
        AndroidLayoutInfo picked = list.getSelectedValue();
        if (picked == null) {
            return;
        }
        selectedKey = picked.getKey();
        dispose();
    }
}
