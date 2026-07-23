// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployLink;
import juml.app.uml.sketch.DeploySketchModel.DeployNode;
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
 * 配置図の GUI デザイナー編集面 (ツールバー + {@link DeploySketchCanvas})。
 *
 * <p>対応構文は配置図の基本要素 (フラット構成) のみ ({@link DeploySketchCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class DeploySketchEditor implements SketchEditor {

    private final DeploySketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JComboBox<String> modeCombo;
    private Runnable onEdited = () -> { };

    /** モードコンボの並びに対応するリンク種別 (先頭 null = 選択/移動)。 */
    private static final DeployLink.Kind[] MODES = {
            null,
            DeployLink.Kind.ARROW,
            DeployLink.Kind.DEPENDENCY,
            DeployLink.Kind.LINK,
    };
    private static final String[] MODE_KEYS = {
            "sketch.mode.select", "sketch.depl.mode.arrow",
            "sketch.depl.mode.dependency", "sketch.depl.mode.link",
    };

    DeploySketchEditor() {
        canvas = new DeploySketchCanvas(new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editNodeRequested(DeployNode n) {
                if (DeploySketchDialogs.editNode(canvasComponent(), canvas.model(), n)) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }

            @Override public void linkModeCancelled() {
                modeCombo.setSelectedIndex(0);
            }

            @Override public void editLinkRequested(DeployLink l) {
                if (DeploySketchDialogs.editLink(canvasComponent(), l)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JComboBox<DeployNode.Kind> kindCombo =
                new JComboBox<>(DeployNode.Kind.values());
        toolbar.add(kindCombo);
        JButton addNode = new JButton(Messages.get("sketch.depl.toolbar.add"));
        addNode.addActionListener(e -> canvas.addNode(
                (DeployNode.Kind) kindCombo.getSelectedItem(), null));
        toolbar.add(addNode);
        toolbar.add(new JLabel(Messages.get("sketch.toolbar.mode")));
        String[] labels = new String[MODE_KEYS.length];
        for (int i = 0; i < MODE_KEYS.length; i++) {
            labels[i] = Messages.get(MODE_KEYS[i]);
        }
        modeCombo = new JComboBox<>(labels);
        modeCombo.addActionListener(
                e -> canvas.setLinkMode(MODES[modeCombo.getSelectedIndex()]));
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
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
    }

    @Override
    public String currentPuml() {
        return DeploySketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルのノード群。 */
    List<DeployNode> nodesForTest() {
        return canvas.model().getNodes();
    }

    /** テスト用: 現在の解析済みモデルのリンク群。 */
    List<DeployLink> linksForTest() {
        return canvas.model().getLinks();
    }

    /** テスト用: 実際の編集経路でノードを追加する。 */
    void addNodeForTest(DeployNode.Kind kind) {
        canvas.addNode(kind, null);
    }
}
