// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI 図形操作エディタ (デザイナー) のペイン: 図種別ツールバー + キャンバス。
 *
 * <p>PlantUML テキストから図種 (クラス図 / シーケンス図 / アクティビティ図) を
 * {@link SketchDiagramType#detect(String)} で自動判定し、対応する
 * {@link SketchEditor} へ切り替える。</p>
 *
 * <p>PlantUML テキストとの双方向同期の窓口:
 * {@link #loadFrom(String)} でテキスト → モデル (Design タブ選択時に呼ぶ)、
 * キャンバス操作 → {@link #setOnPumlChange(Consumer)} で登録されたリスナーへ
 * 再生成テキストを通知 (エディタタブがテキスト欄へ反映する)。</p>
 *
 * <p>未対応構文を含むテキストは編集不可 (警告バナー表示) とし、GUI 操作による
 * テキストの破壊を防ぐ。対応構文は各図種の基本要素のみ
 * ({@link SketchPumlCodec} / {@link SeqSketchCodec} / {@link ActivitySketchCodec} 参照)。</p>
 */
public final class SketchPane extends JPanel {

    private static final int HISTORY_LIMIT = 100;

    private final ClassSketchEditor classEditor = new ClassSketchEditor();
    private final SeqSketchEditor seqEditor = new SeqSketchEditor();
    private final ActivitySketchEditor activityEditor = new ActivitySketchEditor();
    private final StateSketchEditor stateEditor = new StateSketchEditor();
    private final UseCaseSketchEditor usecaseEditor = new UseCaseSketchEditor();
    private final ComponentSketchEditor componentEditor = new ComponentSketchEditor();
    private final ObjectSketchEditor objectEditor = new ObjectSketchEditor();
    private final ErSketchEditor erEditor = new ErSketchEditor();
    private SketchEditor active = classEditor;
    private SketchDiagramType activeType = SketchDiagramType.CLASS;

    private final CardLayout toolbarCards = new CardLayout();
    private final CardLayout canvasCards = new CardLayout();
    private final JPanel toolbarPanel = new JPanel(toolbarCards);
    private final JPanel canvasPanel = new JPanel(canvasCards);
    private final JButton undoButton;
    private final JButton redoButton;

    private Consumer<String> onPumlChange;
    // Undo/Redo は PlantUML テキストのスナップショットで管理する (round-trip 実績を流用)。
    // baseline は現在のモデル状態のテキスト、restoring は復元適用中の再記録抑止フラグ。
    private final java.util.Deque<String> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<String> redoStack = new java.util.ArrayDeque<>();
    private String baseline = "@startuml\n@enduml\n";
    private boolean restoring;

    public SketchPane() {
        super(new BorderLayout());
        for (SketchDiagramType type : SketchDiagramType.values()) {
            SketchEditor editor = editorFor(type);
            editor.setOnEdited(this::firePumlChanged);
            toolbarPanel.add(editor.toolbarComponent(), type.name());
            canvasPanel.add(editor.canvasComponent(), type.name());
        }

        JPanel historyBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        undoButton = new JButton(Messages.get("sketch.toolbar.undo"));
        undoButton.setToolTipText(Messages.get("sketch.toolbar.undo.tip"));
        undoButton.addActionListener(e -> undo());
        redoButton = new JButton(Messages.get("sketch.toolbar.redo"));
        redoButton.setToolTipText(Messages.get("sketch.toolbar.redo.tip"));
        redoButton.addActionListener(e -> redo());
        historyBar.add(undoButton);
        historyBar.add(redoButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(toolbarPanel, BorderLayout.CENTER);
        north.add(historyBar, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);
        add(canvasPanel, BorderLayout.CENTER);
        installUndoRedoKeys();
        updateHistoryButtons();
    }

    private SketchEditor editorFor(SketchDiagramType type) {
        switch (type) {
            case SEQUENCE: return seqEditor;
            case ACTIVITY: return activityEditor;
            case STATE:    return stateEditor;
            case USECASE:  return usecaseEditor;
            case COMPONENT: return componentEditor;
            case OBJECT:   return objectEditor;
            case ER:       return erEditor;
            default:       return classEditor;
        }
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

    /** キャンバス操作でモデルが変わったとき、再生成した PlantUML を受け取るリスナー。 */
    public void setOnPumlChange(Consumer<String> listener) {
        this.onPumlChange = listener;
    }

    /**
     * PlantUML テキストを解析してキャンバスへ反映する (Design タブ選択時に呼ぶ)。
     * 図種を自動判定して対応するエディタへ切り替える。
     * 未対応構文が含まれる場合は編集不可として表示のみ行う。
     */
    public void loadFrom(String pumlText) {
        applyText(pumlText);
        // 新しい内容を読み込んだら Undo 履歴はリセットする (別セッション扱い)。
        baseline = active.currentPuml();
        undoStack.clear();
        redoStack.clear();
        updateHistoryButtons();
    }

    /** テキストを図種判定つきでアクティブエディタへ反映する (履歴は触らない)。 */
    private void applyText(String pumlText) {
        SketchDiagramType type = SketchDiagramType.detect(pumlText);
        active = editorFor(type);
        activeType = type;
        toolbarCards.show(toolbarPanel, type.name());
        canvasCards.show(canvasPanel, type.name());
        active.load(pumlText);
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
            applyText(puml);
            baseline = puml;
            if (onPumlChange != null) {
                onPumlChange.accept(puml);
            }
        } finally {
            restoring = false;
        }
        revalidate();
        repaint();
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        undoButton.setEnabled(!undoStack.isEmpty());
        redoButton.setEnabled(!redoStack.isEmpty());
    }

    /** 現在のモデルから PlantUML を再生成して返す (テストおよび同期通知用)。 */
    public String currentPuml() {
        return active.currentPuml();
    }

    /** GUI 編集が可能な状態か (未対応構文があると false)。 */
    public boolean isEditable() {
        return active.isEditable();
    }

    private void firePumlChanged() {
        String now = active.currentPuml();
        // 実質変化ゼロの編集 (スナップで元位置へ戻る微小ドラッグ等) は無視する。放置すると
        // 無変更なのに Undo 履歴が積まれ Redo が破棄され、テキスト欄が偽 dirty になる。
        if (now.equals(baseline)) {
            return;
        }
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

    /** テスト用: いま表示中の図種。 */
    SketchDiagramType activeTypeForTest() {
        return activeType;
    }

    /** テスト用: 現在の解析済みクラス図モデル。 */
    List<SketchClass> classesForTest() {
        return classEditor.classesForTest();
    }

    /** テスト用: 現在の解析済みシーケンス図モデルの参加者。 */
    List<SeqParticipant> seqParticipantsForTest() {
        return seqEditor.participantsForTest();
    }

    /** テスト用: 現在の解析済みシーケンス図モデルのタイムライン項目。 */
    List<SeqItem> seqItemsForTest() {
        return seqEditor.itemsForTest();
    }

    /** テスト用: 現在の解析済みアクティビティ図モデルの最上位ノード列。 */
    List<ActivityNode> activityNodesForTest() {
        return activityEditor.nodesForTest();
    }

    /** テスト用: 現在の解析済み状態遷移図モデルの状態群。 */
    List<StateNode> statesForTest() {
        return stateEditor.statesForTest();
    }

    /** テスト用: 現在の解析済み状態遷移図モデルの遷移群。 */
    List<StateTransition> transitionsForTest() {
        return stateEditor.transitionsForTest();
    }

    /** テスト用: 実際の編集経路で状態を追加し Undo 履歴も積む。 */
    void addStateForTest() {
        stateEditor.addStateForTest();
    }

    /** テスト用: 現在の解析済みユースケース図モデルの要素群。 */
    List<UseCaseNode> usecaseNodesForTest() {
        return usecaseEditor.nodesForTest();
    }

    /** テスト用: 現在の解析済みユースケース図モデルの関係群。 */
    List<UseCaseRelation> usecaseRelationsForTest() {
        return usecaseEditor.relationsForTest();
    }

    /** テスト用: 実際の編集経路でユースケース図要素を追加し Undo 履歴も積む。 */
    void addUseCaseNodeForTest(UseCaseNode.Kind kind) {
        usecaseEditor.addNodeForTest(kind);
    }

    /** テスト用: 現在の解析済みコンポーネント図モデルの要素群。 */
    List<ComponentNode> componentNodesForTest() {
        return componentEditor.nodesForTest();
    }

    /** テスト用: 現在の解析済みコンポーネント図モデルの関係群。 */
    List<ComponentRelation> componentRelationsForTest() {
        return componentEditor.relationsForTest();
    }

    /** テスト用: 実際の編集経路でコンポーネント図要素を追加し Undo 履歴も積む。 */
    void addComponentNodeForTest(ComponentNode.Kind kind) {
        componentEditor.addNodeForTest(kind);
    }

    /** テスト用: 現在の解析済みオブジェクト図モデルのオブジェクト群。 */
    List<ObjectInstance> objectsForTest() {
        return objectEditor.objectsForTest();
    }

    /** テスト用: 現在の解析済みオブジェクト図モデルのリンク群。 */
    List<ObjectLink> linksForTest() {
        return objectEditor.linksForTest();
    }

    /** テスト用: 実際の編集経路でオブジェクトを追加し Undo 履歴も積む。 */
    void addObjectForTest() {
        objectEditor.addObjectForTest();
    }

    /** テスト用: 現在の解析済み ER 図モデルのエンティティ群。 */
    List<ErSketchModel.Entity> erEntitiesForTest() {
        return erEditor.entitiesForTest();
    }

    /** テスト用: 現在の解析済み ER 図モデルのリレーション群。 */
    List<ErSketchModel.Relation> erRelationsForTest() {
        return erEditor.relationsForTest();
    }

    /** テスト用: 実際の編集経路で ER エンティティを追加し Undo 履歴も積む。 */
    void addErEntityForTest() {
        erEditor.addEntityForTest();
    }

    /** テスト用: 実際の編集経路 (firePumlChanged 経由) でクラスを追加し Undo 履歴も積む。 */
    void addClassForTest(SketchClass.Kind kind) {
        classEditor.addClassForTest(kind);
    }

    /** テスト用: 実際の編集経路で参加者を追加し Undo 履歴も積む。 */
    void addParticipantForTest(SeqParticipant.Kind kind) {
        seqEditor.addParticipantForTest(kind);
    }

    /** テスト用: 実際の編集経路でアクティビティノードを末尾へ追加し Undo 履歴も積む。 */
    void addActivityNodeForTest(ActivityNode node) {
        activityEditor.addNodeForTest(node);
    }

    /**
     * テスト用: モデルを変えずに編集通知だけを発火する (スナップで元位置へ戻る微小ドラッグ相当)。
     * 実質変化ゼロなので Undo 履歴を積まず Redo も消さないことを検証するためのシーム。
     */
    void fireNoOpEditForTest() {
        firePumlChanged();
    }

    /** テスト用: Undo 可能な履歴があるか。 */
    boolean canUndoForTest() {
        return !undoStack.isEmpty();
    }

    /** テスト用: Redo 可能な履歴があるか。 */
    boolean canRedoForTest() {
        return !redoStack.isEmpty();
    }
}
