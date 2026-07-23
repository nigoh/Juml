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
import java.awt.Point;
import java.util.List;

/**
 * オブジェクト図の GUI デザイナー編集面 (ツールバー + {@link ObjectSketchCanvas})。
 *
 * <p>対応構文はオブジェクト図の基本要素のみ ({@link ObjectSketchCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class ObjectSketchEditor implements SketchEditor {

    private final ObjectSketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JComboBox<String> modeCombo;
    private Runnable onEdited = () -> { };

    /** モードコンボの並びに対応するリンク種別 (先頭 null = 選択/移動)。 */
    private static final ObjectLink.Kind[] MODES = {
            null,
            ObjectLink.Kind.ARROW,
            ObjectLink.Kind.LINK,
            ObjectLink.Kind.DEPENDENCY,
    };
    private static final String[] MODE_KEYS = {
            "sketch.mode.select", "sketch.obj.mode.arrow",
            "sketch.obj.mode.link", "sketch.obj.mode.dependency",
    };

    ObjectSketchEditor() {
        canvas = new ObjectSketchCanvas(new ObjectSketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editObjectRequested(ObjectInstance o) {
                if (ObjectSketchDialogs.editObject(canvasComponent(), canvas.model(), o)) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }

            @Override public void addObjectRequested(Point at) {
                canvas.addObject(at);
            }

            @Override public void relationModeCancelled() {
                modeCombo.setSelectedIndex(0);
            }

            @Override public void editLinkRequested(ObjectLink link) {
                if (ObjectSketchDialogs.editLink(canvasComponent(), link)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton addObject = new JButton(Messages.get("sketch.obj.toolbar.addObject"));
        addObject.addActionListener(e -> canvas.addObject(null));
        toolbar.add(addObject);
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
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
    }

    @Override
    public String currentPuml() {
        return ObjectSketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルのオブジェクト群。 */
    List<ObjectInstance> objectsForTest() {
        return canvas.model().getObjects();
    }

    /** テスト用: 現在の解析済みモデルのリンク群。 */
    List<ObjectLink> linksForTest() {
        return canvas.model().getLinks();
    }

    /** テスト用: 実際の編集経路でオブジェクトを追加する。 */
    void addObjectForTest() {
        canvas.addObject(null);
    }
}
