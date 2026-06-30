// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 図内検索 ({@link DiagramFindBar}) のヒットを {@link SvgPreviewPanel} に重ねて描画する
 * オーバーレイ層。ヒット矩形 (SVG 座標) と現在フォーカス中のヒット番号を保持し、
 * パネル座標へズーム変換して塗り + 枠で強調する。付箋・ミニマップと同じく
 * パネルから描画責務を分離する。
 */
final class DiagramSearchLayer {

    private static final Color FILL_OTHER = new Color(0xFF, 0xE9, 0xA8, 140);
    private static final Color LINE_OTHER = new Color(0xC8, 0xA0, 0x40);
    private static final Color FILL_CURRENT = new Color(0xFF, 0x96, 0x32, 120);
    private static final Color LINE_CURRENT = new Color(0xE0, 0x6A, 0x00);

    private final JComponent host;
    /** ヒット矩形 (SVG 座標)。空ならハイライト無し。 */
    private List<Rectangle2D> hits = Collections.emptyList();
    /** {@link #hits} のうち現在フォーカス中のヒット番号。範囲外なら強調なし。 */
    private int current = -1;

    DiagramSearchLayer(JComponent host) {
        this.host = host;
    }

    /** ヒット矩形と現在ヒット番号を差し替えて再描画する。{@code null}/空でクリア相当。 */
    void set(List<Rectangle2D> newHits, int currentIndex) {
        this.hits = (newHits == null || newHits.isEmpty())
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(newHits));
        this.current = currentIndex;
        host.repaint();
    }

    /** ハイライトを全消去して再描画する。 */
    void clear() {
        if (hits.isEmpty() && current < 0) {
            return;
        }
        reset();
        host.repaint();
    }

    /** 再描画を伴わずにヒットを捨てる (図差し替え時など、別途 repaint される文脈用)。 */
    void reset() {
        hits = Collections.emptyList();
        current = -1;
    }

    /** ヒット矩形 (SVG 座標) を余白付きでビューポートに収める。 */
    void scrollToVisible(double zoom, Rectangle2D svgRect) {
        if (svgRect == null || zoom <= 0) {
            return;
        }
        int margin = 48;
        int x = (int) Math.floor(svgRect.getX() * zoom) - margin;
        int y = (int) Math.floor(svgRect.getY() * zoom) - margin;
        int w = (int) Math.ceil(svgRect.getWidth() * zoom) + margin * 2;
        int h = (int) Math.ceil(svgRect.getHeight() * zoom) + margin * 2;
        host.scrollRectToVisible(new Rectangle(Math.max(0, x), Math.max(0, y), w, h));
    }

    /** ヒット矩形をパネル座標で描画する。現在ヒットはオレンジ、他は黄色。 */
    void paint(Graphics2D g2, double zoom) {
        if (hits.isEmpty() || zoom <= 0) {
            return;
        }
        // 呼び出し元 (SvgPreviewPanel) が設定したパネル基準変換 (スクロール込み) のまま描く。
        // identity へ戻すとスクロール時にヒット枠が図とずれてしまう。
        Stroke oldStroke = g2.getStroke();
        for (int i = 0; i < hits.size(); i++) {
            Rectangle2D r = hits.get(i);
            int x = (int) Math.round(r.getX() * zoom);
            int y = (int) Math.round(r.getY() * zoom);
            int w = Math.max(2, (int) Math.round(r.getWidth() * zoom));
            int h = Math.max(2, (int) Math.round(r.getHeight() * zoom));
            boolean cur = (i == current);
            g2.setColor(cur ? FILL_CURRENT : FILL_OTHER);
            g2.fillRect(x, y, w, h);
            g2.setColor(cur ? LINE_CURRENT : LINE_OTHER);
            g2.setStroke(new BasicStroke(cur ? 2f : 1f));
            g2.drawRect(x, y, w, h);
        }
        g2.setStroke(oldStroke);
    }
}
