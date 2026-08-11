// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 画面の入れ物と書き出しキャンバスが<b>同じ規則</b>で決まることの回帰テスト。
 *
 * <p>ラウンド 25 は「モデルは利用者の意図をそのまま持ち、書き出しがキャンバスを広げる」
 * という 1 つの規則へ統一した。ところがその規則を教わったのは書き出し側
 * ({@link DiagramNotesLayer#exportBounds}) だけで、<b>画面側の入れ物</b>は図の寸法のまま
 * だった。結果、図の外へ出た付箋は Save SVG / Save PNG / 画像コピーには出るのに、
 * 画面ではスクロールしても到達できず、選ぶことも掴むこともできない — ラウンド 25 が
 * 消したはずの「画面と書き出しの食い違い」が<b>向きを変えて</b>残っていた。</p>
 */
public class PreviewSizeMatchesExportCanvasTest {

    private static SvgPreviewPanel panelWith(int w, int h) {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB));
        return panel;
    }

    private static DiagramNote noteAt(double x, double y, double w, double h) {
        DiagramNote n = new DiagramNote();
        n.setAnchor(DiagramNote.Anchor.FREE);
        n.setX(x);
        n.setY(y);
        n.setWidth(w);
        n.setHeight(h);
        n.setText("memo");
        return n;
    }

    /** 図の外へ出た付箋の分だけ、画面の入れ物も広がること。 */
    @Test
    public void theViewGrowsForNotesOutsideTheDiagram() {
        SvgPreviewPanel panel = panelWith(300, 200);
        panel.notes().setData(List.of(noteAt(500, 10, 240, 150)), Collections.emptyList());

        double[] box = panel.exportCanvas();
        Dimension pref = panel.getPreferredSize();

        assertEquals("入れ物の幅が書き出しキャンバスと一致すること",
                (int) box[0], pref.width);
        assertEquals("入れ物の高さが書き出しキャンバスと一致すること",
                (int) box[1], pref.height);
        assertTrue("図の幅より広がっていること: " + pref.width, pref.width >= 740);
    }

    /** 非退行: 図の中に収まる付箋では入れ物の大きさを変えないこと。 */
    @Test
    public void aNoteInsideTheDiagramLeavesTheViewAlone() {
        SvgPreviewPanel panel = panelWith(300, 200);
        panel.notes().setData(List.of(noteAt(10, 10, 50, 50)), Collections.emptyList());

        assertEquals(new Dimension(300, 200), panel.getPreferredSize());
    }

    /** 非退行: 付箋が 1 件も無ければ図の寸法そのままであること。 */
    @Test
    public void withoutNotesTheViewIsTheDiagram() {
        assertEquals(new Dimension(300, 200), panelWith(300, 200).getPreferredSize());
    }

    /** ズーム倍率も両方に等しく効くこと。 */
    @Test
    public void zoomAppliesToTheGrownView() {
        SvgPreviewPanel panel = panelWith(300, 200);
        panel.notes().setData(List.of(noteAt(500, 10, 240, 150)), Collections.emptyList());
        panel.setZoomLevel(2.0);

        double[] box = panel.exportCanvas();
        Dimension pref = panel.getPreferredSize();
        assertEquals((int) (box[0] * 2.0), pref.width);
        assertEquals((int) (box[1] * 2.0), pref.height);
    }

    /**
     * 図が縮んで付箋が外に取り残されたら、その場で入れ物が広がること。
     *
     * <p>ラウンド 25 の規則では、図が小さくなってもモデルの座標は<b>利用者が置いた
     * ままにする</b> (クランプで書き潰すと元に戻せない)。その結果、付箋は内容矩形の外に
     * 残る。入れ物の大きさを一度きり測って持っていると、スクロール範囲が増えないので
     * その付箋に画面上で到達できない — 書き出しには出るのに、である。
     * エディタのライブプレビューは入力途中の小さい図を流すので、通常の編集で起きる。</p>
     */
    @Test
    public void shrinkingTheDiagramGrowsTheViewToKeepNotesReachable() {
        SvgPreviewPanel panel = panelWith(1200, 900);
        panel.notes().setData(List.of(noteAt(900, 700, 240, 150)), Collections.emptyList());
        assertEquals(new Dimension(1200, 900), panel.getPreferredSize());

        // 同じタブで図を描き直す (フィルタ変更・編集中のライブプレビューなど)。
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));

        Dimension pref = panel.getPreferredSize();
        assertTrue("取り残された付箋まで入れ物が広がること: " + pref.width, pref.width >= 1140);
        assertTrue("縦も同じこと: " + pref.height, pref.height >= 850);
        assertEquals("書き出しキャンバスと一致すること",
                (int) panel.exportCanvas()[0], pref.width);
        assertEquals((int) panel.exportCanvas()[1], pref.height);
    }
}
