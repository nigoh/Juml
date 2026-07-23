// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.FlowLayout;
import java.util.List;

/**
 * コンポーネント図の GUI デザイナー編集面 (ツールバー + {@link ComponentSketchCanvas})。
 *
 * <p>対応構文はコンポーネント図の基本要素のみ ({@link ComponentSketchCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class ComponentSketchEditor implements SketchEditor {

    private final ComponentSketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JComboBox<String> modeCombo;
    private Runnable onEdited = () -> { };

    /** モードコンボの並びに対応する関係種別 (先頭 null = 選択/移動)。 */
    private static final ComponentRelation.Kind[] MODES = {
            null,
            ComponentRelation.Kind.ARROW,
            ComponentRelation.Kind.DEPENDENCY,
            ComponentRelation.Kind.LINK,
    };
    private static final String[] MODE_KEYS = {
            "sketch.mode.select", "sketch.comp.mode.arrow",
            "sketch.comp.mode.dependency", "sketch.comp.mode.link",
    };

    ComponentSketchEditor() {
        canvas = new ComponentSketchCanvas(new ComponentSketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editNodeRequested(ComponentNode n) {
                if (ComponentSketchDialogs.editNode(canvasComponent(), canvas.model(), n)) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }

            @Override public void relationModeCancelled() {
                modeCombo.setSelectedIndex(0);
            }

            @Override public void editRelationRequested(ComponentRelation r) {
                if (ComponentSketchDialogs.editRelation(canvasComponent(), r)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton addComponent = new JButton(Messages.get("sketch.comp.toolbar.addComponent"));
        addComponent.addActionListener(
                e -> canvas.addNode(ComponentNode.Kind.COMPONENT, null));
        toolbar.add(addComponent);
        JButton addInterface = new JButton(Messages.get("sketch.comp.toolbar.addInterface"));
        addInterface.addActionListener(
                e -> canvas.addNode(ComponentNode.Kind.INTERFACE, null));
        toolbar.add(addInterface);
        toolbar.add(new JLabel(Messages.get("sketch.toolbar.mode")));
        String[] labels = new String[MODE_KEYS.length];
        for (int i = 0; i < MODE_KEYS.length; i++) {
            labels[i] = Messages.get(MODE_KEYS[i]);
        }
        modeCombo = new JComboBox<>(labels);
        modeCombo.addActionListener(
                e -> canvas.setRelationMode(MODES[modeCombo.getSelectedIndex()]));
        toolbar.add(modeCombo);
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
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(pumlText);
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
        return ComponentSketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルの要素群。 */
    List<ComponentNode> nodesForTest() {
        return canvas.model().getNodes();
    }

    /** テスト用: 現在の解析済みモデルの関係群。 */
    List<ComponentRelation> relationsForTest() {
        return canvas.model().getRelations();
    }

    /** テスト用: 実際の編集経路で要素を追加する。 */
    void addNodeForTest(ComponentNode.Kind kind) {
        canvas.addNode(kind, null);
    }
}
