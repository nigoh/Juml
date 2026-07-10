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
 * クラス図の GUI デザイナー編集面 (ツールバー + {@link SketchCanvas})。
 *
 * <p>対応構文はクラス図の基本要素のみ ({@link SketchPumlCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class ClassSketchEditor implements SketchEditor {

    private final SketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JComboBox<String> modeCombo;
    private Runnable onEdited = () -> { };

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

    ClassSketchEditor() {
        canvas = new SketchCanvas(new SketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editRequested(SketchClass c) {
                if (SketchEditDialogs.editClass(canvasComponent(), canvas.model(), c)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }

            @Override public void addClassRequested(Point at) {
                addClass(SketchClass.Kind.CLASS, at);
            }

            @Override public void relationModeCancelled() {
                // キャンバスで Esc が押されたらツールバーのモード表示も先頭 (選択/移動) へ戻す。
                modeCombo.setSelectedIndex(0);
            }

            @Override public void editRelationRequested(SketchRelation relation) {
                if (SketchEditDialogs.editRelation(canvasComponent(), relation)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        toolbar.add(addButton("sketch.toolbar.addClass", SketchClass.Kind.CLASS));
        toolbar.add(addButton("sketch.toolbar.addInterface", SketchClass.Kind.INTERFACE));
        toolbar.add(addButton("sketch.toolbar.addEnum", SketchClass.Kind.ENUM));
        toolbar.add(new JLabel(Messages.get("sketch.toolbar.mode")));
        String[] labels = new String[MODE_KEYS.length];
        for (int i = 0; i < MODE_KEYS.length; i++) {
            labels[i] = Messages.get(MODE_KEYS[i]);
        }
        modeCombo = new JComboBox<>(labels);
        // 矢印表記を文字で読まなくても形で選べるよう、各モードのプレビューを添える。
        javax.swing.Icon[] icons = new javax.swing.Icon[MODES.length];
        for (int i = 0; i < MODES.length; i++) {
            icons[i] = SketchToolIcon.forRelation(MODES[i]);
        }
        SketchToolIcon.install(modeCombo, icons);
        modeCombo.addActionListener(
                e -> canvas.setRelationMode(MODES[modeCombo.getSelectedIndex()]));
        toolbar.add(modeCombo);
        JCheckBox snap = new JCheckBox(Messages.get("sketch.toolbar.snap"), true);
        snap.addActionListener(e -> canvas.setSnapToGrid(snap.isSelected()));
        toolbar.add(snap);

        scroll = new JScrollPane(canvas);
    }

    private JButton addButton(String labelKey, SketchClass.Kind kind) {
        JButton b = new JButton(Messages.get(labelKey));
        b.addActionListener(e -> addClass(kind, null));
        return b;
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
        onEdited.run();
        canvas.revalidate();
        canvas.repaint();
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
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
    }

    @Override
    public String currentPuml() {
        return SketchPumlCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデル。 */
    List<SketchClass> classesForTest() {
        return canvas.model().getClasses();
    }

    /** テスト用: 実際の編集経路 (onEdited 経由) でクラスを追加する。 */
    void addClassForTest(SketchClass.Kind kind) {
        addClass(kind, null);
    }
}
