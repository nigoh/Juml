// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.apache.batik.gvt.GraphicsNode;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JViewport;

/**
 * {@link SvgPreviewPanel} の図本体を高速に再描画するためのラスタライズキャッシュ
 * (バックバッファ)。
 *
 * <p>巨大な図では Batik の {@code svgNode.paint()} が 1 フレームごとに GVT ツリー全体を
 * 走査・描画するため、パン / ズーム / 付箋ドラッグ / ラバーバンド選択のたびに重くなる。
 * そこで「可視領域 (ビューポート) + マージン」分だけを 1 度ラスタライズして
 * {@link #backBuffer} にキャッシュし、以降の再描画は drawImage (ブリット) で済ませる。
 * ズーム変更・内容差し替え時のみ再ラスタライズする。</p>
 *
 * <p>状態 (倍率・内容) は {@link SvgPreviewPanel} 側が保持し、描画のたびに
 * {@link #paint} へ渡す。本クラスは純粋にキャッシュ管理と描画だけを担う。</p>
 */
final class DiagramRenderCache {

    /** 可視領域の外側に余分にラスタライズしておくマージン (px)。連続パンの再生成を抑える。 */
    private static final int BUFFER_MARGIN = 384;

    private BufferedImage backBuffer;
    /** {@link #backBuffer} がカバーするパネル座標 (デバイス座標) の左上 X。 */
    private int bufX;
    /** {@link #backBuffer} がカバーするパネル座標 (デバイス座標) の左上 Y。 */
    private int bufY;
    /** {@link #backBuffer} がカバーするパネル座標での幅 (px)。バッファ画素幅は ×renderScale。 */
    private int bufW;
    /** {@link #backBuffer} がカバーするパネル座標での高さ (px)。バッファ画素高は ×renderScale。 */
    private int bufH;
    /** {@link #backBuffer} を生成したときの倍率 (不一致なら無効)。負値は未生成。 */
    private double bufZoom = -1;
    /** {@link #backBuffer} を生成したときのスーパーサンプリング倍率 (不一致なら無効)。 */
    private double bufRenderScale = -1;

    /** バックバッファを破棄して次回描画で再ラスタライズさせる。 */
    void invalidate() {
        backBuffer = null;
        bufZoom = -1;
        bufRenderScale = -1;
    }

    /**
     * 図本体をバックバッファ経由で描画する。
     * 必要領域 (= 再描画クリップ ∩ 図の範囲) が現在のバッファで賄えるならブリットのみ、
     * 賄えない (倍率変更・パンでマージン外へ移動など) 場合だけ再ラスタライズする。
     *
     * @param src         描画対象 (SVG ノード or 画像 + 図の固有サイズ)。
     * @param zoom        表示倍率。
     * @param renderScale スーパーサンプリング倍率。HiDPI でのボケ防止に
     *                    {@link DiagramRenderQuality} が算出する値を渡す (1.0 で等倍)。
     * @param viewport    可視領域 (パネル座標)。スクロールペイン外なら null。
     */
    void paint(Graphics2D g2, Rectangle clip, Source src,
               double zoom, double renderScale, Rectangle viewport) {
        double rs = (renderScale > 0) ? renderScale : 1.0;
        Rectangle content = contentDeviceBounds(src.contentW, src.contentH, zoom);
        Rectangle need = neededRegion(clip, content, viewport);
        if (need.isEmpty()) {
            return;
        }
        if (!bufferCovers(need, zoom, rs)) {
            rebuildBuffer(src, zoom, content, viewport, rs);
        }
        if (backBuffer != null) {
            // バッファは renderScale 倍の画素を持つので、パネル上の被覆矩形へ縮小して貼る。
            Object oldInterp = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            if (rs != 1.0) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            }
            g2.drawImage(backBuffer, bufX, bufY, bufX + bufW, bufY + bufH,
                    0, 0, backBuffer.getWidth(), backBuffer.getHeight(), null);
            if (rs != 1.0) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        oldInterp != null ? oldInterp
                                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            }
        }
    }

    /** 現在のバックバッファが必要領域 {@code need} を完全に含み、倍率も一致しているか。 */
    private boolean bufferCovers(Rectangle need, double zoom, double renderScale) {
        if (backBuffer == null
                || Math.abs(bufZoom - zoom) > 1e-9
                || Math.abs(bufRenderScale - renderScale) > 1e-9) {
            return false;
        }
        return new Rectangle(bufX, bufY, bufW, bufH).contains(need);
    }

    /** ズーム適用後の図全体の矩形 (デバイス座標, 原点 0,0)。 */
    private static Rectangle contentDeviceBounds(double contentW, double contentH, double zoom) {
        int w = (int) Math.ceil(contentW * zoom);
        int h = (int) Math.ceil(contentH * zoom);
        return new Rectangle(0, 0, Math.max(0, w), Math.max(0, h));
    }

    /** 今回の描画で最低限必要な領域 (= クリップ ∩ 図範囲、無ければ可視領域 ∩ 図範囲)。 */
    private static Rectangle neededRegion(Rectangle clip, Rectangle content, Rectangle viewport) {
        if (clip != null) {
            return clip.intersection(content);
        }
        Rectangle base = (viewport != null) ? viewport : content;
        return base.intersection(content);
    }

    /** ビューポートの可視矩形を取得する (パネル座標)。スクロールペイン外なら null。 */
    static Rectangle viewportRect(JViewport vp) {
        if (vp == null) {
            return null;
        }
        Point pos = vp.getViewPosition();
        Dimension ext = vp.getExtentSize();
        return new Rectangle(pos.x, pos.y, ext.width, ext.height);
    }

    /**
     * 図本体を「可視領域 + マージン ∩ 図範囲」でラスタライズし直す。
     * バッファは {@code renderScale} 倍の画素を持ち (スーパーサンプリング)、
     * 描画時にパネル被覆矩形へ縮小して貼ることで HiDPI でも鮮明にする。
     */
    private void rebuildBuffer(Source src,
                               double zoom, Rectangle content, Rectangle viewport,
                               double renderScale) {
        Rectangle base = (viewport != null) ? new Rectangle(viewport) : new Rectangle(content);
        base.grow(BUFFER_MARGIN, BUFFER_MARGIN);
        Rectangle target = base.intersection(content);
        if (target.isEmpty()) {
            invalidate();
            return;
        }
        // パネル上の被覆サイズ (target) と、実際に確保する画素サイズ (×renderScale) を分ける。
        int pxW = Math.max(1, (int) Math.ceil(target.width * renderScale));
        int pxH = Math.max(1, (int) Math.ceil(target.height * renderScale));
        BufferedImage buf = backBuffer;
        if (buf == null || buf.getWidth() != pxW || buf.getHeight() != pxH) {
            buf = new BufferedImage(pxW, pxH, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D bg = buf.createGraphics();
        try {
            // 再利用バッファを透明にクリア
            bg.setComposite(AlphaComposite.Clear);
            bg.fillRect(0, 0, buf.getWidth(), buf.getHeight());
            bg.setComposite(AlphaComposite.SrcOver);
            bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            bg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // 画素はパネル座標の renderScale 倍。まず縮小分を補い、左上 (target) を原点へ寄せ、倍率を適用。
            bg.scale(renderScale, renderScale);
            bg.translate(-target.x, -target.y);
            bg.scale(zoom, zoom);
            if (src.svgNode != null) {
                src.svgNode.paint(bg);
            } else if (src.image != null) {
                bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                bg.drawImage(src.image, 0, 0, null);
            }
        } finally {
            bg.dispose();
        }
        backBuffer = buf;
        bufX = target.x;
        bufY = target.y;
        bufW = target.width;
        bufH = target.height;
        bufZoom = zoom;
        bufRenderScale = renderScale;
    }

    /**
     * ラスタライズ対象。ベクター SVG ({@link GraphicsNode}) かラスタ画像のどちらかと、
     * 図の固有 (倍率 1.0 時) サイズを束ねる。{@link #paint} の引数を絞るための小さな束。
     */
    static final class Source {
        final GraphicsNode svgNode;
        final BufferedImage image;
        final double contentW;
        final double contentH;

        Source(GraphicsNode svgNode, BufferedImage image,
               double contentW, double contentH) {
            this.svgNode = svgNode;
            this.image = image;
            this.contentW = contentW;
            this.contentH = contentH;
        }
    }
}
