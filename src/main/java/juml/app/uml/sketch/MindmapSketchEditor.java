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
    /** side コンボを選択連動で書き換える間 true。ユーザー操作と誤認して側変更しないためのガード。 */
    private boolean syncingSide;

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

            @Override public void selectionChanged(MindmapNode selected) {
                syncSideCombo(selected);
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
        sideCombo.addActionListener(e -> {
            if (syncingSide) {
                return; // 選択連動での再表示中はユーザー操作でないので側を変えない。
            }
            canvas.setSideOfSelected(SIDES[sideCombo.getSelectedIndex()]);
            // 実際に反映された実効 side をコンボへ再表示する。選択が無ければ setSideOfSelected は
            // no-op なので Auto へ戻り、「有効に見えるのに無反応で表示だけ変わる」誤解を防ぐ。
            // 深いノードでは枝起点へ正規化された結果の side を映す (コンボとテキストの食い違い防止)。
            syncSideCombo(canvas.selectedForTest());
        });
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
        this.unsupported = r.unsupportedLines != null
                ? r.unsupportedLines : java.util.List.of();
        canvas.setModel(r.model, r.isFullySupported(), r.unsupportedLines);
        updateToolbarEnabled();
    }

    /** 直近の {@link #load} で未対応だった行 (他の設計器と同じ契約)。 */
    @Override
    public java.util.List<String> unsupportedLines() {
        return unsupported;
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

    /**
     * side コンボの表示を選択ノードの<b>実効 side</b> (枝の起点の side) に合わせる。選択が
     * 変わってもコンボが前回値のまま残る「未反映」を防ぐ。書き換え中は {@link #syncingSide} で
     * ActionListener をガードし、同期がユーザーの側変更として誤発火しないようにする。
     */
    private void syncSideCombo(MindmapNode selected) {
        MindmapNode.Side eff = selected == null
                ? MindmapNode.Side.AUTO : canvas.model().effectiveSideOf(selected);
        int idx = 0;
        for (int i = 0; i < SIDES.length; i++) {
            if (SIDES[i] == eff) {
                idx = i;
                break;
            }
        }
        syncingSide = true;
        try {
            sideCombo.setSelectedIndex(idx);
        } finally {
            syncingSide = false;
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

    /**
     * 直近の {@link #load} で未対応だった行。これを返さないと
     * {@code SketchPane.isCommentOnlyLock} が常に「未対応行なし」と見なし、
     * マインドマップだけ「編集を有効化」ボタンが永久に出なかった
     * (コメント 1 行でロックされたまま 1 クリックでは解除できない)。
     */
    private java.util.List<String> unsupported = java.util.List.of();

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

    /** テスト用: 実際の選択経路 (canvas) でノードを選択する (side コンボ同期を発火する)。 */
    void selectForTest(MindmapNode node) {
        canvas.setSelectedForTest(node);
    }

    /** テスト用: side コンボが現在示している side (選択連動の検証用)。 */
    MindmapNode.Side comboSideForTest() {
        return SIDES[sideCombo.getSelectedIndex()];
    }

    /** テスト用: ユーザーがコンボから side を選ぶ操作を模擬する (本物の ActionListener を通す)。 */
    void userPickSideForTest(MindmapNode.Side side) {
        for (int i = 0; i < SIDES.length; i++) {
            if (SIDES[i] == side) {
                sideCombo.setSelectedIndex(i); // syncingSide=false のままなのでリスナーが発火する
                return;
            }
        }
    }
}
