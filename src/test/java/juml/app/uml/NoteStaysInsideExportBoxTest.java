// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * 追加した付箋が<b>書き出される図の範囲内</b>に置かれることの回帰テスト。
 *
 * <p>追加位置は下限 0 だけを見ていて上限が無かった。図がウィンドウより小さいとき
 * {@link javax.swing.JViewport} はビューをウィンドウ大まで引き伸ばすので、画面上は
 * どこにでも置けてしまう。しかし PNG も SVG も<b>図の内容矩形</b>で寸法が決まるため、
 * 内容矩形の外に置かれた付箋は書き出しから黙って消える — アプリでは見えているのに
 * 共有した成果物には無い、という一番たちの悪い形になっていた。</p>
 */
public class NoteStaysInsideExportBoxTest {

    /** 300x200 の図を持つプレビューパネル (画像モードで内容矩形を確定させる)。 */
    private static SvgPreviewPanel panelWithSmallDiagram() {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));
        return panel;
    }

    private static void assertInside(SvgPreviewPanel panel, String label) {
        List<DiagramNote> notes = panel.notes().getNotes();
        assertTrue(label + ": 付箋が 1 件あること", notes.size() == 1);
        DiagramNote n = notes.get(0);
        assertTrue(label + ": 原点が内容矩形の中にあること x=" + n.getX(),
                n.getX() >= 0 && n.getX() <= panel.contentWidth());
        assertTrue(label + ": 原点が内容矩形の中にあること y=" + n.getY(),
                n.getY() >= 0 && n.getY() <= panel.contentHeight());
    }

    /** ウィンドウ中央 (図の外) を指しても、内容矩形の中へ寄せること。 */
    @Test
    public void aNoteAddedFarOutsideTheDiagramIsPulledBackIn() {
        SvgPreviewPanel panel = panelWithSmallDiagram();

        // 実測された症状と同じ座標: 1197x797 のパネル中央。
        panel.notes().addNoteAt(new Point(598, 398), 1.0);

        assertInside(panel, "遠方");
    }

    /** 右クリック位置が図の少し外でも同じこと。 */
    @Test
    public void aNoteAddedJustOutsideTheDiagramIsPulledBackIn() {
        SvgPreviewPanel panel = panelWithSmallDiagram();

        panel.notes().addNoteAt(new Point(320, 210), 1.0);

        assertInside(panel, "近傍");
    }

    /** 非退行: 図の中に置いた付箋は動かさないこと。 */
    @Test
    public void aNoteAddedInsideTheDiagramKeepsItsPosition() {
        SvgPreviewPanel panel = panelWithSmallDiagram();

        panel.notes().addNoteAt(new Point(10, 20), 1.0);

        List<DiagramNote> notes = panel.notes().getNotes();
        assertTrue("x が動かないこと: " + notes.get(0).getX(), notes.get(0).getX() == 10);
        assertTrue("y が動かないこと: " + notes.get(0).getY(), notes.get(0).getY() == 20);
    }

    /**
     * 非退行: 図の大きさが分からないとき (内容なし) は従来どおり下限だけを見ること。
     *
     * <p>{@link DiagramNotesLayer} は単体でも使われるので、所有者が図の寸法を答えられない
     * 場合に位置を 0 へ潰してはいけない。</p>
     */
    @Test
    public void withoutContentOnlyTheLowerBoundApplies() {
        DiagramNotesLayer layer = new DiagramNotesLayer(new javax.swing.JPanel());

        layer.addNoteAt(new Point(400, 300), 1.0);

        DiagramNote n = layer.getNotes().get(0);
        assertTrue("下限だけが効くこと: " + n.getX() + "," + n.getY(),
                n.getX() == 400 && n.getY() == 300);
    }
}
