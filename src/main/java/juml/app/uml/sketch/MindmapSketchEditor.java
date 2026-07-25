// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.FlowLayout;
import java.awt.Point;

/**
 * マインドマップの GUI デザイナー編集面 (ツールバー + {@link MindmapSketchCanvas})。
 *
 * <p>対応構文はプレーンなマインドマップの基本要素のみ ({@link MindmapSketchCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class MindmapSketchEditor implements SketchEditor {

    private final MindmapSketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JComboBox<String> sideCombo;
    private Runnable onEdited = () -> { };

    /** side コンボの並びに対応する左右指定 (Auto / Left / Right)。 */
    private static final MindmapNode.Side[] SIDES = {
            MindmapNode.Side.AUTO, MindmapNode.Side.LEFT, MindmapNode.Side.RIGHT,
    };
    private static final String[] SIDE_KEYS = {
            "sketch.mm.side.auto", "sketch.mm.side.left", "sketch.mm.side.right",
    };

    MindmapSketchEditor() {
        canvas = new MindmapSketchCanvas(new MindmapSketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editRequested(MindmapNode node) {
                if (MindmapSketchDialogs.editNode(canvasComponent(), node)) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }

            @Override public void addRootRequested(Point at) {
                canvas.addChild(null);
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton addChild = new JButton(Messages.get("sketch.mm.toolbar.addChild"));
        addChild.addActionListener(e -> canvas.addChild(canvas.selectedForTest()));
        toolbar.add(addChild);
        JButton addSibling = new JButton(Messages.get("sketch.mm.toolbar.addSibling"));
        addSibling.addActionListener(e -> canvas.addSibling(canvas.selectedForTest()));
        toolbar.add(addSibling);
        JButton delete = new JButton(Messages.get("sketch.mm.toolbar.delete"));
        delete.addActionListener(e -> canvas.deleteSelected());
        toolbar.add(delete);
        toolbar.add(new JLabel(Messages.get("sketch.mm.toolbar.side")));
        String[] labels = new String[SIDE_KEYS.length];
        for (int i = 0; i < SIDE_KEYS.length; i++) {
            labels[i] = Messages.get(SIDE_KEYS[i]);
        }
        sideCombo = new JComboBox<>(labels);
        sideCombo.addActionListener(
                e -> canvas.setSideOfSelected(SIDES[sideCombo.getSelectedIndex()]));
        toolbar.add(sideCombo);

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
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(pumlText);
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
        return MindmapSketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルのルート (空図なら null)。 */
    MindmapNode rootForTest() {
        return canvas.model().getRoot();
    }

    /** テスト用: 実際の編集経路で子ノードを追加する (ルートが無ければルートを作る)。 */
    void addChildForTest() {
        canvas.addChild(canvas.model().getRoot());
    }

    /** テスト用: 実際の編集経路で選択ノードの兄弟を追加する。 */
    void addSiblingForTest(MindmapNode after) {
        canvas.addSibling(after);
    }

    /** テスト用: 実際の編集経路で選択ノードの左右指定を変更する。 */
    void setSideForTest(MindmapNode node, MindmapNode.Side side) {
        canvas.setSelectedForTest(node);
        canvas.setSideOfSelected(side);
    }
}
