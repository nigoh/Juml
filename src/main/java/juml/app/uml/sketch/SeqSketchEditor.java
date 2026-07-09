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
import java.util.List;

/**
 * シーケンス図の GUI デザイナー編集面 (ツールバー + {@link SeqSketchCanvas})。
 *
 * <p>対応構文はシーケンス図の基本要素のみ ({@link SeqSketchCodec} 参照)。
 * 図種の切り替え・Undo/Redo・テキスト同期は {@link SketchPane} が担う。</p>
 */
final class SeqSketchEditor implements SketchEditor {

    private final SeqSketchCanvas canvas;
    private final JPanel toolbar;
    private final JScrollPane scroll;
    private final JComboBox<String> modeCombo;
    private Runnable onEdited = () -> { };

    /** モードコンボの並びに対応する矢印種別 (先頭 null = 選択/移動)。 */
    private static final SeqItem.Arrow[] MODES = {
            null,
            SeqItem.Arrow.SYNC,
            SeqItem.Arrow.ASYNC,
            SeqItem.Arrow.REPLY,
    };
    private static final String[] MODE_KEYS = {
            "sketch.seq.mode.select", "sketch.seq.mode.sync",
            "sketch.seq.mode.async", "sketch.seq.mode.reply",
    };

    SeqSketchEditor() {
        canvas = new SeqSketchCanvas(new SeqSketchCanvas.Listener() {
            @Override public void modelEdited() {
                onEdited.run();
            }

            @Override public void editMessageRequested(SeqItem message) {
                if (SeqSketchDialogs.editMessage(canvasComponent(), canvas.model(), message)) {
                    onEdited.run();
                    canvas.repaint();
                }
            }

            @Override public void editParticipantRequested(SeqParticipant participant) {
                if (SeqSketchDialogs.editParticipant(
                        canvasComponent(), canvas.model(), participant)) {
                    onEdited.run();
                    canvas.revalidate();
                    canvas.repaint();
                }
            }

            @Override public void messageModeCancelled() {
                // キャンバスで Esc が押されたらツールバーのモード表示も先頭 (選択/移動) へ戻す。
                modeCombo.setSelectedIndex(0);
            }
        });

        toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton addParticipant = new JButton(Messages.get("sketch.seq.toolbar.addParticipant"));
        addParticipant.addActionListener(
                e -> canvas.addParticipant(SeqParticipant.Kind.PARTICIPANT));
        toolbar.add(addParticipant);
        JButton addActor = new JButton(Messages.get("sketch.seq.toolbar.addActor"));
        addActor.addActionListener(e -> canvas.addParticipant(SeqParticipant.Kind.ACTOR));
        toolbar.add(addActor);
        toolbar.add(new JLabel(Messages.get("sketch.toolbar.mode")));
        String[] labels = new String[MODE_KEYS.length];
        for (int i = 0; i < MODE_KEYS.length; i++) {
            labels[i] = Messages.get(MODE_KEYS[i]);
        }
        modeCombo = new JComboBox<>(labels);
        modeCombo.addActionListener(
                e -> canvas.setMessageMode(MODES[modeCombo.getSelectedIndex()]));
        toolbar.add(modeCombo);

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
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(pumlText);
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
    }

    @Override
    public String currentPuml() {
        return SeqSketchCodec.toPuml(canvas.model());
    }

    @Override
    public boolean isEditable() {
        return canvas.isModelEditable();
    }

    @Override
    public void setOnEdited(Runnable onEdited) {
        this.onEdited = onEdited != null ? onEdited : () -> { };
    }

    /** テスト用: 現在の解析済みモデルの参加者。 */
    List<SeqParticipant> participantsForTest() {
        return canvas.model().getParticipants();
    }

    /** テスト用: 現在の解析済みモデルのタイムライン項目。 */
    List<SeqItem> itemsForTest() {
        return canvas.model().getItems();
    }

    /** テスト用: 実際の編集経路 (onEdited 経由) で参加者を追加する。 */
    void addParticipantForTest(SeqParticipant.Kind kind) {
        canvas.addParticipant(kind);
    }
}
