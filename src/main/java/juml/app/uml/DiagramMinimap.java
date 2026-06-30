// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.apache.batik.gvt.GraphicsNode;

import javax.swing.JViewport;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 図プレビュー右下に重ねる「ミニマップ」(全体俯瞰)。
 *
 * <p>VS Code のエディタミニマップ相当。図全体を縮小したサムネイルと、現在表示中の
 * ビューポートを示す矩形を描画する。ミニマップ上をクリック / ドラッグすると、その位置へ
 * 図をパン (ビューポートを移動) する。図がビューポートに収まりきっている (スクロール不要な)
 * ときは表示しない。</p>
 *
 * <p>描画ロジックは {@link SvgPreviewPanel} 本体から分離し、本体はサムネイル元データの
 * 提供と数行の配線だけを担う。サムネイルは内容が変わるまでキャッシュする。</p>
 */
final class DiagramMinimap {

    private static final int MAX_W = 190;
    private static final int MAX_H = 140;
    private static final int MARGIN = 12;

    /** サムネイルキャッシュ (内容 + サイズが変わるまで再利用)。 */
    private BufferedImage thumb;
    private Object thumbKey;

    /** ミニマップ上でドラッグ中か (本体のパン処理に流さないためのフラグ)。 */
    private boolean dragging;

    /** 図がビューポートに収まらず、ミニマップを出すべきか。 */
    private boolean shouldShow(SvgPreviewPanel panel, JViewport vp) {
        if (vp == null || !panel.hasContent()) {
            return false;
        }
        Dimension ext = vp.getExtentSize();
        double zoom = panel.getZoomLevel();
        double pw = panel.contentWidth() * zoom;
        double ph = panel.contentHeight() * zoom;
        // どちらかの軸でスクロールが必要なときだけ表示する (+数px の余裕)。
        return pw > ext.width + 4 || ph > ext.height + 4;
    }

    /** 現在のミニマップ矩形 (パネル座標)。表示しないなら null。 */
    private Rectangle bounds(SvgPreviewPanel panel, JViewport vp) {
        if (!shouldShow(panel, vp)) {
            return null;
        }
        double cw = panel.contentWidth();
        double ch = panel.contentHeight();
        if (cw <= 0 || ch <= 0) {
            return null;
        }
        double scale = Math.min(MAX_W / cw, MAX_H / ch);
        int tw = (int) Math.max(1, Math.round(cw * scale));
        int th = (int) Math.max(1, Math.round(ch * scale));
        Point pos = vp.getViewPosition();
        Dimension ext = vp.getExtentSize();
        int x = pos.x + ext.width - tw - MARGIN;
        int y = pos.y + ext.height - th - MARGIN;
        return new Rectangle(x, y, tw, th);
    }

    /** ミニマップを描画する。{@code g2} はパネル座標 (変換なし) で渡すこと。 */
    void paint(Graphics2D g2, SvgPreviewPanel panel) {
        JViewport vp = panel.getParentViewport();
        Rectangle r = bounds(panel, vp);
        if (r == null) {
            return;
        }
        double cw = panel.contentWidth();
        double ch = panel.contentHeight();
        double scale = r.width / cw;
        ensureThumb(panel, r.width, r.height, scale);

        Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            // 影 + 背景
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRoundRect(r.x + 2, r.y + 2, r.width, r.height, 8, 8);
            Color bg = UIManager.getColor("Panel.background");
            g.setColor(bg != null ? bg : Color.WHITE);
            g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            if (thumb != null) {
                g.drawImage(thumb, r.x, r.y, null);
            }
            // 枠線
            Color border = UIManager.getColor("Component.borderColor");
            g.setColor(border != null ? border : new Color(0x9E9E9E));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);

            // ビューポート指示枠 (現在表示中の領域)
            double zoom = panel.getZoomLevel();
            Point pos = vp.getViewPosition();
            Dimension ext = vp.getExtentSize();
            int ix = r.x + (int) Math.round((pos.x / zoom) * scale);
            int iy = r.y + (int) Math.round((pos.y / zoom) * scale);
            int iw = (int) Math.round((ext.width / zoom) * scale);
            int ih = (int) Math.round((ext.height / zoom) * scale);
            ix = Math.max(r.x, ix);
            iy = Math.max(r.y, iy);
            iw = Math.min(iw, r.x + r.width - ix);
            ih = Math.min(ih, r.y + r.height - iy);
            g.setColor(new Color(UiTheme.ACCENT.getRed(), UiTheme.ACCENT.getGreen(),
                    UiTheme.ACCENT.getBlue(), 40));
            g.fillRect(ix, iy, iw, ih);
            g.setColor(UiTheme.ACCENT);
            g.setStroke(new BasicStroke(1.4f));
            g.drawRect(ix, iy, iw, ih);
        } finally {
            g.dispose();
        }
    }

    /** サムネイルを (必要なら) 生成しキャッシュする。 */
    private void ensureThumb(SvgPreviewPanel panel, int tw, int th, double scale) {
        Object key = panel.minimapKey();
        if (thumb != null && key != null && key.equals(thumbKey)
                && thumb.getWidth() == tw && thumb.getHeight() == th) {
            return;
        }
        thumbKey = key;
        BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            Color bg = UIManager.getColor("TextPane.background");
            g.setColor(bg != null ? bg : Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.scale(scale, scale);
            BufferedImage raster = panel.minimapImage();
            GraphicsNode node = panel.minimapNode();
            if (raster != null) {
                g.drawImage(raster, 0, 0, null);
            } else if (node != null) {
                node.paint(g);
            }
        } catch (RuntimeException ex) {
            // サムネイル生成失敗時はキャッシュを無効化し、次回再試行する。
            thumbKey = null;
        } finally {
            g.dispose();
        }
        thumb = img;
    }

    /** ミニマップ内なら、その位置へパンして true を返す (本体パンに流さない)。 */
    boolean mousePressed(Point p, SvgPreviewPanel panel) {
        JViewport vp = panel.getParentViewport();
        Rectangle r = bounds(panel, vp);
        if (r == null || !r.contains(p)) {
            return false;
        }
        dragging = true;
        recenter(p, r, panel, vp);
        return true;
    }

    /** ドラッグ中ならパンを続け true を返す。 */
    boolean mouseDragged(Point p, SvgPreviewPanel panel) {
        if (!dragging) {
            return false;
        }
        JViewport vp = panel.getParentViewport();
        Rectangle r = bounds(panel, vp);
        if (r != null) {
            recenter(p, r, panel, vp);
        }
        return true;
    }

    /** ドラッグ終了。 */
    boolean mouseReleased() {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    /** ミニマップ上の点を図座標へ写像し、その点を中心にビューポートを移動する。 */
    private void recenter(Point p, Rectangle r, SvgPreviewPanel panel, JViewport vp) {
        double scale = r.width / panel.contentWidth();
        double contentX = (p.x - r.x) / scale;
        double contentY = (p.y - r.y) / scale;
        double zoom = panel.getZoomLevel();
        Dimension ext = vp.getExtentSize();
        Dimension size = panel.getPreferredSize();
        int nx = (int) Math.round(contentX * zoom - ext.width / 2.0);
        int ny = (int) Math.round(contentY * zoom - ext.height / 2.0);
        nx = Math.max(0, Math.min(nx, Math.max(0, size.width - ext.width)));
        ny = Math.max(0, Math.min(ny, Math.max(0, size.height - ext.height)));
        vp.setViewPosition(new Point(nx, ny));
    }

    /** 内容が変わったらサムネイルキャッシュを破棄する。 */
    void invalidate() {
        thumb = null;
        thumbKey = null;
    }
}
