// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.util.Messages;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Supplier;

/**
 * アクティビティ図の GUI デザイナー編集面 (ツールバー + {@link ActivitySketchCanvas})。
 *
 * <p>対応構文は新形式アクティビティ図の基本要素のみ ({@link ActivitySketchCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class ActivitySketchEditor implements SketchEditor {

    private final ActivitySketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private Runnable onEdited = () -> { };

    ActivitySketchEditor() {
        canvas = new ActivitySketchCanvas(new ActivitySketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editRequested(ActivityNode node) {
                boolean applied = node.getKind() == ActivityNode.Kind.IF
                        ? ActivitySketchDialogs.editIf(canvasComponent(), node)
                        : ActivitySketchDialogs.editAction(canvasComponent(), node);
                if (applied) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        toolbar.add(addButton("sketch.act.toolbar.addAction",
                () -> ActivityNode.action("Action")));
        toolbar.add(addButton("sketch.act.toolbar.addIf", () -> {
            ActivityNode n = ActivityNode.branch("condition?", "yes", "no");
            n.ensureElseBranch();
            return n;
        }));
        toolbar.add(addButton("sketch.act.toolbar.addStart",
                () -> ActivityNode.terminal(ActivityNode.Kind.START)));
        toolbar.add(addButton("sketch.act.toolbar.addStop",
                () -> ActivityNode.terminal(ActivityNode.Kind.STOP)));

        scroll = new JScrollPane(canvas);
    }

    private JButton addButton(String labelKey, Supplier<ActivityNode> factory) {
        JButton b = new JButton(Messages.get(labelKey));
        b.addActionListener(e -> canvas.addNode(factory.get()));
        return b;
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
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
    }

    @Override
    public String currentPuml() {
        return ActivitySketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルの最上位ノード列。 */
    List<ActivityNode> nodesForTest() {
        return canvas.model().getNodes();
    }

    /** テスト用: 実際の編集経路 (onEdited 経由) でノードを末尾へ追加する。 */
    void addNodeForTest(ActivityNode node) {
        canvas.addNode(node);
    }
}
