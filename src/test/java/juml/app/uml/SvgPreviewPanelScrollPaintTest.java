// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * スクロール時のオーバーレイ描画 (付箋・検索ハイライト) が、ビューポートが入れる
 * 平行移動 (スクロールオフセット) を尊重して図に貼り付いたまま描かれることの回帰テスト。
 *
 * <p>かつて {@link SvgPreviewPanel#paintComponent} は付箋等を描く直前に変換を identity へ
 * 戻しており、スクロールするとオフセット分が失われて付箋が画面に貼り付き、図とずれて
 * しまっていた。本テストはスクロール込みの {@link Graphics2D} を渡し、付箋が
 * 「図座標 − スクロール量」の位置に描かれることを画素で確認する。</p>
 */
public class SvgPreviewPanelScrollPaintTest {

    /** 図 (コンテンツ) のサイズ。ビューポートより十分大きくしてスクロール可能にする。 */
    private static final int CONTENT = 600;
    /** ビューポート (画面に見えている範囲) のサイズ。 */
    private static final int VIEW = 200;
    /** スクロール量 (図座標)。X=Y=250 までスクロールした状態を模す。 */
    private static final int SCROLL = 250;

    /** 付箋の図座標と色。検出しやすいよう純色にする。 */
    private static final int NOTE_X = 300;
    private static final int NOTE_Y = 300;
    private static final int NOTE_W = 100;
    private static final int NOTE_H = 100;
    private static final String NOTE_HEX = "#FF0000";

    private static SvgPreviewPanel panelWithRedNote() {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        BufferedImage content = new BufferedImage(CONTENT, CONTENT, BufferedImage.TYPE_INT_RGB);
        Graphics2D cg = content.createGraphics();
        try {
            cg.setColor(new Color(0xEEEEEE));
            cg.fillRect(0, 0, CONTENT, CONTENT);
        } finally {
            cg.dispose();
        }
        panel.setImage(content);
        panel.setSize(CONTENT, CONTENT);

        DiagramNote note = new DiagramNote(NOTE_X, NOTE_Y, NOTE_W, NOTE_H, "");
        note.setColor(NOTE_HEX);
        panel.notes().setNotes(Collections.singletonList(note));
        return panel;
    }

    /** ビューポートのスクロール状態を模した {@link Graphics2D} へ {@code paintComponent} する。 */
    private static BufferedImage paintScrolled(SvgPreviewPanel panel) {
        BufferedImage viewImg = new BufferedImage(VIEW, VIEW, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = viewImg.createGraphics();
        try {
            // JViewport がスクロール時に入れる平行移動 + 可視領域クリップを再現する。
            g.translate(-SCROLL, -SCROLL);
            g.setClip(new Rectangle(SCROLL, SCROLL, VIEW, VIEW));
            panel.paintComponent(g);
        } finally {
            g.dispose();
        }
        return viewImg;
    }

    @Test
    public void noteFollowsScrollOffsetInsteadOfStickingToScreen() {
        // パネル生成・setImage・paintComponent は EDT 上で実行する (EDT 規律)
        SvgPreviewPanel panel = GuiActionRunner.execute(() -> panelWithRedNote());
        BufferedImage view = GuiActionRunner.execute(() -> paintScrolled(panel));

        // 付箋中心 (図座標 350,350) は、スクロール後はビューポート内 (100,100) に来るはず。
        int cx = NOTE_X + NOTE_W / 2 - SCROLL;
        int cy = NOTE_Y + NOTE_H / 2 - SCROLL;
        assertTrue("付箋中心はビューポート内に収まる", cx >= 0 && cx < VIEW && cy >= 0 && cy < VIEW);

        Color hit = new Color(view.getRGB(cx, cy));
        assertEquals("付箋はスクロール後の位置 (図に貼り付いた位置) に赤で描かれる",
                0xFF0000, hit.getRGB() & 0xFFFFFF);

        // 退行検出: 変換を identity へ戻していた頃は付箋が図座標 (300,300) のまま描かれ、
        // ビューポート左上 (0,0) 付近は背景色のままになる。左上が赤くないことを確かめる。
        Color topLeft = new Color(view.getRGB(2, 2));
        assertTrue("ビューポート左上は付箋で塗られていない",
                (topLeft.getRGB() & 0xFFFFFF) != 0xFF0000);
    }
}
