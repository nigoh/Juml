// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import java.awt.FlowLayout;
import java.util.List;

/**
 * 状態遷移図の GUI デザイナー編集面 (ツールバー + {@link StateSketchCanvas})。
 *
 * <p>対応構文は状態遷移図の基本要素のみ ({@link StateSketchCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class StateSketchEditor implements SketchEditor {

    private final StateSketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JToggleButton transitionToggle;
    private Runnable onEdited = () -> { };

    StateSketchEditor() {
        canvas = new StateSketchCanvas(new StateSketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editStateRequested(StateNode s) {
                if (StateSketchDialogs.editState(canvasComponent(), canvas.model(), s)) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }

            @Override public void transitionModeCancelled() {
                transitionToggle.setSelected(false);
            }

            @Override public void editTransitionRequested(StateTransition t) {
                if (StateSketchDialogs.editTransition(canvasComponent(), t)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton addState = new JButton(Messages.get("sketch.state.toolbar.addState"));
        addState.addActionListener(e -> canvas.addState(null));
        toolbar.add(addState);
        transitionToggle = new JToggleButton(Messages.get("sketch.state.toolbar.transition"));
        transitionToggle.setToolTipText(Messages.get("sketch.state.toolbar.transition.tip"));
        transitionToggle.addActionListener(
                e -> canvas.setTransitionMode(transitionToggle.isSelected()));
        toolbar.add(transitionToggle);
        JCheckBox snap = new JCheckBox(Messages.get("sketch.toolbar.snap"), true);
        snap.addActionListener(e -> canvas.setSnapToGrid(snap.isSelected()));
        toolbar.add(snap);

        scroll = new JScrollPane(canvas);
    }

    @Override
    public JComponent toolbarComponent() {
        return toolbar;
    }

    @Override
    public JComponent canvasComponent() {
        return scroll;
    }

    @Override
    public void load(String pumlText) {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
        updateToolbarEnabled();
    }

    /**
     * 編集ロック中 (未対応構文を含む図) はツールバー操作を無効表示にする。押しても
     * 無反応なコントロールが有効に見える誤解を避ける (バナーで理由は別途表示済み)。
     */
    private void updateToolbarEnabled() {
        boolean on = canvas.isModelEditable();
        for (java.awt.Component comp : toolbar.getComponents()) {
            comp.setEnabled(on);
        }
    }

    @Override
    public String currentPuml() {
        return StateSketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルの状態群。 */
    List<StateNode> statesForTest() {
        return canvas.model().getStates();
    }

    /** テスト用: 現在の解析済みモデルの遷移群。 */
    List<StateTransition> transitionsForTest() {
        return canvas.model().getTransitions();
    }

    /** テスト用: 実際の編集経路 (modelEdited 経由) で状態を追加する。 */
    void addStateForTest() {
        canvas.addState(null);
    }
}
