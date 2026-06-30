// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * 「リスト系ユーティリティタブ」(Functions / Members) の共通土台。
 *
 * <p>かつてはどちらも全プロジェクトをそのままテキストへ流し込む {@link JTextArea} だけだった。
 * これは「アクティブな図と無関係なクラスまで一覧化される」「絞り込み手段が OS の検索しかない」
 * という使い勝手の問題があったため、共通のヘッダバー (表示範囲セレクタ + インクリメンタル絞り込み)
 * をここに集約する。</p>
 *
 * <ul>
 *   <li><b>表示範囲セレクタ</b>: {@link Scope#ACTIVE_TAB}（いま開いている図の題材だけ）か
 *       {@link Scope#PROJECT}（プロジェクト全体）かを選ぶ。変更時は
 *       {@link #setOnScopeChanged(Runnable)} で登録した再生成処理を呼ぶ。</li>
 *   <li><b>絞り込みフィールド</b>: 入力に応じて表示行をその場で間引く（再生成不要）。
 *       どの行を「構造行」として常に残すかは {@link #filter(String, String)} に委譲する。</li>
 *   <li><b>コンテキストラベル</b>: いま何を対象に何件表示しているかを 1 行で明示する。</li>
 * </ul>
 */
abstract class FilteredListPanel extends JPanel {

    /** リストの表示範囲。 */
    public enum Scope {
        /** いまフォーカスしている図タブの題材（クラス / パッケージ）だけ。 */
        ACTIVE_TAB,
        /** プロジェクト全体。 */
        PROJECT
    }

    protected final JTextArea textArea = new JTextArea();
    private final JTextField filterField = new JTextField(16);
    private final JComboBox<Scope> scopeCombo = new JComboBox<>(Scope.values());
    private final JLabel contextLabel = new JLabel(" ");
    private String fullText = "";
    private Runnable onScopeChanged;
    /**
     * 絞り込みのデバウンス。1 打鍵ごとに全文を再フィルタ + {@code textArea.setText}
     * していると、大きな一覧 (数千行) では入力がカクつく。最後の打鍵から一定時間後に
     * 1 度だけ適用する。
     */
    private final javax.swing.Timer filterDebounce;

    FilteredListPanel() {
        super(new BorderLayout());
        filterDebounce = new javax.swing.Timer(160, e -> applyFilter());
        filterDebounce.setRepeats(false);
        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setTabSize(2);
        add(buildHeader(), BorderLayout.NORTH);
        add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    /** ヘッダバー (表示範囲セレクタ + 絞り込み欄 + コンテキスト行) を組み立てる。 */
    private JComponent buildHeader() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        controls.add(new JLabel(Messages.get("list.scope.label")));
        scopeCombo.setRenderer(new ScopeRenderer());
        scopeCombo.setToolTipText(Messages.get("list.scope.tooltip"));
        scopeCombo.addActionListener(e -> {
            if (onScopeChanged != null) {
                onScopeChanged.run();
            }
        });
        controls.add(scopeCombo);
        controls.add(new JLabel(Messages.get("list.filter.label")));
        filterField.setToolTipText(Messages.get("list.filter.tooltip"));
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                filterDebounce.restart();
            }

            @Override public void removeUpdate(DocumentEvent e) {
                filterDebounce.restart();
            }

            @Override public void changedUpdate(DocumentEvent e) {
                filterDebounce.restart();
            }
        });
        controls.add(filterField);

        contextLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 3, 6));

        JPanel header = new JPanel(new BorderLayout());
        header.add(controls, BorderLayout.CENTER);
        header.add(contextLabel, BorderLayout.SOUTH);
        return header;
    }

    /** 現在選択中の表示範囲。 */
    public Scope getScope() {
        Scope s = (Scope) scopeCombo.getSelectedItem();
        return s != null ? s : Scope.ACTIVE_TAB;
    }

    /** 表示範囲が切り替わったときに呼ぶ再生成処理を登録する。 */
    public void setOnScopeChanged(Runnable r) {
        this.onScopeChanged = r;
    }

    /** コンテキスト行（対象・件数）の文言を差し替える。 */
    public void setContextInfo(String text) {
        contextLabel.setText(text == null || text.isEmpty() ? " " : text);
    }

    /** 表示する全文（未絞り込み）を差し替え、現在の絞り込み条件を適用する。 */
    public void setText(String text) {
        fullText = text == null ? "" : text;
        applyFilter();
    }

    /** 絞り込み前の全文を返す（エクスポートやテスト用）。 */
    public String getText() {
        return fullText;
    }

    private void applyFilter() {
        String q = filterField.getText().trim();
        String shown = q.isEmpty() ? fullText : filter(fullText, q);
        textArea.setText(shown);
        textArea.setCaretPosition(0);
    }

    /**
     * 絞り込みクエリにマッチする行だけを残す。ヘッダ行など「常に残すべき構造行」の判定は
     * 表示形式（Markdown テーブル / CSV）ごとに異なるためサブクラスへ委譲する。
     *
     * @param full  絞り込み前の全文
     * @param query 空でない検索語（呼び出し側で trim 済み）
     */
    protected abstract String filter(String full, String query);

    /** 表示範囲セレクタを「ENUM 名」ではなく多言語ラベルで描画する。 */
    private static final class ScopeRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Scope) {
                setText(value == Scope.ACTIVE_TAB
                        ? Messages.get("list.scope.activeTab")
                        : Messages.get("list.scope.project"));
            }
            return this;
        }
    }
}
