// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Point;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI 図形操作エディタ (デザイナー) のペイン: ツールバー + {@link SketchCanvas}。
 *
 * <p>PlantUML テキストとの双方向同期の窓口:
 * {@link #loadFrom(String)} でテキスト → モデル (Design タブ選択時に呼ぶ)、
 * キャンバス操作 → {@link #setOnPumlChange(Consumer)} で登録されたリスナーへ
 * 再生成テキストを通知 (エディタタブがテキスト欄へ反映する)。</p>
 *
 * <p>未対応構文を含むテキストは編集不可 (警告バナー表示) とし、GUI 操作による
 * テキストの破壊を防ぐ。対応構文はクラス図の基本要素のみ
 * ({@link SketchPumlCodec} 参照)。</p>
 */
public final class SketchPane extends JPanel {

    private static final int HISTORY_LIMIT = 100;

    private final SketchCanvas canvas;
    private JComboBox<String> modeCombo;
    private JButton undoButton;
    private JButton redoButton;
    private Consumer<String> onPumlChange;
    // Undo/Redo は PlantUML テキストのスナップショットで管理する (round-trip 実績を流用)。
    // baseline は現在のモデル状態のテキスト、restoring は復元適用中の再記録抑止フラグ。
    private final java.util.Deque<String> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<String> redoStack = new java.util.ArrayDeque<>();
    private String baseline = "@startuml\n@enduml\n";
    private boolean restoring;
    /** モードコンボの並びに対応する関係種別 (先頭 null = 選択/移動)。 */
    private static final SketchRelation.Kind[] MODES = {
            null,
            SketchRelation.Kind.EXTENDS,
            SketchRelation.Kind.IMPLEMENTS,
            SketchRelation.Kind.ASSOCIATION,
            SketchRelation.Kind.LINK,
            SketchRelation.Kind.AGGREGATION,
            SketchRelation.Kind.COMPOSITION,
            SketchRelation.Kind.DEPENDENCY,
    };
    private static final String[] MODE_KEYS = {
            "sketch.mode.select", "sketch.mode.extends", "sketch.mode.implements",
            "sketch.mode.association", "sketch.mode.link", "sketch.mode.aggregation",
            "sketch.mode.composition", "sketch.mode.dependency",
    };

    public SketchPane() {
        super(new BorderLayout());
        canvas = new SketchCanvas(new SketchCanvas.Listener() {
            @Override public void modelEdited() {
                firePumlChanged();
            }

            @Override public void editRequested(SketchClass c) {
                if (SketchEditDialogs.editClass(SketchPane.this, canvas.model(), c)) {
                    firePumlChanged();
                    canvas.repaint();
                }
            }

            @Override public void addClassRequested(Point at) {
                addClass(SketchClass.Kind.CLASS, at);
            }

            @Override public void relationModeCancelled() {
                // キャンバスで Esc が押されたらツールバーのモード表示も先頭 (選択/移動) へ戻す。
                if (modeCombo != null) {
                    modeCombo.setSelectedIndex(0);
                }
            }
        });

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        bar.add(addButton("sketch.toolbar.addClass", SketchClass.Kind.CLASS));
        bar.add(addButton("sketch.toolbar.addInterface", SketchClass.Kind.INTERFACE));
        bar.add(addButton("sketch.toolbar.addEnum", SketchClass.Kind.ENUM));
        bar.add(new JLabel(Messages.get("sketch.toolbar.mode")));
        String[] labels = new String[MODE_KEYS.length];
        for (int i = 0; i < MODE_KEYS.length; i++) {
            labels[i] = Messages.get(MODE_KEYS[i]);
        }
        modeCombo = new JComboBox<>(labels);
        modeCombo.addActionListener(
                e -> canvas.setRelationMode(MODES[modeCombo.getSelectedIndex()]));
        bar.add(modeCombo);

        undoButton = new JButton(Messages.get("sketch.toolbar.undo"));
        undoButton.setToolTipText(Messages.get("sketch.toolbar.undo.tip"));
        undoButton.addActionListener(e -> undo());
        redoButton = new JButton(Messages.get("sketch.toolbar.redo"));
        redoButton.setToolTipText(Messages.get("sketch.toolbar.redo.tip"));
        redoButton.addActionListener(e -> redo());
        bar.add(undoButton);
        bar.add(redoButton);
        installUndoRedoKeys();
        updateHistoryButtons();

        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(canvas), BorderLayout.CENTER);
    }

    /** Ctrl+Z=Undo、Ctrl+Y / Ctrl+Shift+Z=Redo をペイン全体に割り当てる。 */
    private void installUndoRedoKeys() {
        int mask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        javax.swing.InputMap im = getInputMap(JPanel.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        javax.swing.ActionMap am = getActionMap();
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, mask), "sketchUndo");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Y, mask), "sketchRedo");
        im.put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z,
                mask | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "sketchRedo");
        am.put("sketchUndo", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                undo();
            }
        });
        am.put("sketchRedo", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                redo();
            }
        });
    }

    private JButton addButton(String labelKey, SketchClass.Kind kind) {
        JButton b = new JButton(Messages.get(labelKey));
        b.addActionListener(e -> addClass(kind, null));
        return b;
    }

    /** キャンバス操作でモデルが変わったとき、再生成した PlantUML を受け取るリスナー。 */
    public void setOnPumlChange(Consumer<String> listener) {
        this.onPumlChange = listener;
    }

    /**
     * PlantUML テキストを解析してキャンバスへ反映する (Design タブ選択時に呼ぶ)。
     * 未対応構文が含まれる場合は編集不可として表示のみ行う。
     */
    public void loadFrom(String pumlText) {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
        // 新しい内容を読み込んだら Undo 履歴はリセットする (別セッション扱い)。
        baseline = currentPuml();
        undoStack.clear();
        redoStack.clear();
        updateHistoryButtons();
    }

    /** キャンバス操作を 1 手戻す。 */
    void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(currentPuml());
        applyPuml(undoStack.pop());
    }

    /** 戻した操作をやり直す。 */
    void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(currentPuml());
        applyPuml(redoStack.pop());
    }

    /** スナップショット (PlantUML) をモデルへ復元し、テキスト欄へも同期する。 */
    private void applyPuml(String puml) {
        restoring = true;
        try {
            SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(puml);
            canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
            baseline = puml;
            if (onPumlChange != null) {
                onPumlChange.accept(puml);
            }
        } finally {
            restoring = false;
        }
        canvas.revalidate();
        canvas.repaint();
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        if (undoButton != null) {
            undoButton.setEnabled(!undoStack.isEmpty());
        }
        if (redoButton != null) {
            redoButton.setEnabled(!redoStack.isEmpty());
        }
    }

    /** 現在のモデルから PlantUML を再生成して返す (テストおよび同期通知用)。 */
    public String currentPuml() {
        return SketchPumlCodec.toPuml(canvas.model());
    }

    /** GUI 編集が可能な状態か (未対応構文があると false)。 */
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    private void addClass(SketchClass.Kind kind, Point at) {
        if (!canvas.isModelEditable()) {
            return;
        }
        SketchModel model = canvas.model();
        String base = kind == SketchClass.Kind.INTERFACE ? "NewInterface"
                : kind == SketchClass.Kind.ENUM ? "NewEnum" : "NewClass";
        // 追加位置: 右クリック位置、無ければ既存数に応じて斜めにずらして重なりを避ける。
        int n = model.getClasses().size();
        int x = at != null ? at.x : 40 + (n % 8) * 30;
        int y = at != null ? at.y : 40 + (n % 8) * 26;
        model.getClasses().add(new SketchClass(model.uniqueName(base), kind, x, y));
        firePumlChanged();
        canvas.revalidate();
        canvas.repaint();
    }

    private void firePumlChanged() {
        String now = SketchPumlCodec.toPuml(canvas.model());
        // 復元適用中でなければ、変更前状態 (baseline) を Undo に積んで現在状態を baseline に更新。
        if (!restoring) {
            undoStack.push(baseline);
            while (undoStack.size() > HISTORY_LIMIT) {
                undoStack.removeLast();
            }
            redoStack.clear();
            baseline = now;
            updateHistoryButtons();
        }
        if (onPumlChange != null) {
            onPumlChange.accept(now);
        }
    }

    /** テスト用: 現在の解析済みモデル。 */
    List<SketchClass> classesForTest() {
        return canvas.model().getClasses();
    }

    /** テスト用: 実際の編集経路 (firePumlChanged 経由) でクラスを追加し Undo 履歴も積む。 */
    void addClassForTest(SketchClass.Kind kind) {
        addClass(kind, null);
    }
}
