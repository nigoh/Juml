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
 * ER 図の GUI デザイナー編集面 (ツールバー + {@link ErSketchCanvas})。
 *
 * <p>対応構文は ER 図の基本要素のみ ({@link ErSketchCodec} 参照)。図種の切り替え・
 * Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class ErSketchEditor implements SketchEditor {

    private final ErSketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JComboBox<String> modeCombo;
    private Runnable onEdited = () -> { };

    /** モードコンボの並び (先頭 = 選択/移動, 次 = リレーション追加)。 */
    private static final String[] MODE_KEYS = {
            "sketch.mode.select", "sketch.er.mode.relate",
    };

    ErSketchEditor() {
        canvas = new ErSketchCanvas(new ErSketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editEntityRequested(ErSketchModel.Entity e) {
                if (ErSketchDialogs.editEntity(canvasComponent(), canvas.model(), e)) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }

            @Override public void relationModeCancelled() {
                modeCombo.setSelectedIndex(0);
            }

            @Override public void editRelationRequested(ErSketchModel.Relation r) {
                if (ErSketchDialogs.editRelation(canvasComponent(), r)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton addEntity = new JButton(Messages.get("sketch.er.toolbar.addEntity"));
        addEntity.addActionListener(e -> canvas.addEntity(null));
        toolbar.add(addEntity);
        toolbar.add(new JLabel(Messages.get("sketch.toolbar.mode")));
        String[] labels = new String[MODE_KEYS.length];
        for (int i = 0; i < MODE_KEYS.length; i++) {
            labels[i] = Messages.get(MODE_KEYS[i]);
        }
        modeCombo = new JComboBox<>(labels);
        modeCombo.addActionListener(
                e -> canvas.setRelationMode(modeCombo.getSelectedIndex() == 1));
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
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
    }

    @Override
    public String currentPuml() {
        return ErSketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルのエンティティ群。 */
    List<ErSketchModel.Entity> entitiesForTest() {
        return canvas.model().getEntities();
    }

    /** テスト用: 現在の解析済みモデルのリレーション群。 */
    List<ErSketchModel.Relation> relationsForTest() {
        return canvas.model().getRelations();
    }

    /** テスト用: 実際の編集経路でエンティティを追加する。 */
    void addEntityForTest() {
        canvas.addEntity(null);
    }
}
