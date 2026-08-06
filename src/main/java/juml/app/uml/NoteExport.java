// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.Messages;
import org.apache.batik.gvt.GraphicsNode;

import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * UML 図のエクスポートに「Markdown 付箋メモ」を含めるためのヘルパ。
 *
 * <p>付箋はプレビュー上のオーバーレイなので、図 (PlantUML) からの素の出力には
 * 含まれない。このクラスが SVG には {@code <foreignObject>} 付き要素を注入し、
 * PNG には図 + 付箋を 1 枚にラスタライズして「見たまま」を保存する。</p>
 *
 * <p>付箋の座標は図 (SVG) 座標系で保持されており、PlantUML SVG の座標系と一致する
 * ため、そのまま注入・描画すれば位置が揃う。</p>
 */
final class NoteExport {

    private static final String BORDER = "#C9A227";

    private NoteExport() {
    }

    /**
     * {@code puml} を SVG にレンダリングし、付箋 ({@code notes}) を注入してファイル保存する。
     * 付箋が無ければ (null/空) 素の SVG をそのまま書き出す。
     *
     * <p>{@code notes} はアンカー解決済みの絶対座標
     * ({@link SvgPreviewPanel#notesForExport()}) を渡すこと。Swing コンポーネントに
     * 触れないため、バックグラウンドスレッドから呼んでよい。</p>
     */
    static void writeSvg(File target, String puml, List<DiagramNote> notes) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, buf);
        String svg = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (notes != null && !notes.isEmpty()) {
            svg = injectIntoSvg(svg, notes);
        }
        // 一時ファイルへ書いてから原子的に置換する。既存ファイルへ直接書いて途中で失敗
        // (ディスク満杯/権限変化) すると、直前の正しい SVG が破損した状態で残るため。
        final String out = svg;
        juml.util.AtomicFileWrite.write(target, os -> os.write(out.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * SVG 文字列の {@code </svg>} 直前に付箋メモを表す要素群を注入する。
     * 各付箋は色付き角丸矩形 + {@code <foreignObject>} 内の XHTML (Markdown 変換結果)。
     */
    static String injectIntoSvg(String svg, List<DiagramNote> notes) {
        if (svg == null || notes == null || notes.isEmpty()) {
            return svg;
        }
        int idx = svg.lastIndexOf("</svg>");
        if (idx < 0) {
            return svg;
        }
        StringBuilder f = new StringBuilder("<g class=\"juml-notes\">");
        for (DiagramNote n : notes) {
            double x = n.getX();
            double y = n.getY();
            double w = n.getWidth();
            double h = n.getHeight();
            f.append("<rect x=\"").append(num(x)).append("\" y=\"").append(num(y))
                    .append("\" width=\"").append(num(w)).append("\" height=\"").append(num(h))
                    .append("\" rx=\"8\" ry=\"8\" fill=\"")
                    .append(attr(NoteRenderer.normalizeColorHex(n.getColor())))
                    .append("\" stroke=\"").append(BORDER).append("\" stroke-width=\"1\"/>");
            f.append("<foreignObject x=\"").append(num(x + 6)).append("\" y=\"").append(num(y + 6))
                    .append("\" width=\"").append(num(Math.max(1, w - 12)))
                    .append("\" height=\"").append(num(Math.max(1, h - 12))).append("\">");
            f.append("<div xmlns=\"http://www.w3.org/1999/xhtml\" "
                    + "style=\"font:11px sans-serif;color:#222;overflow:hidden;\">");
            f.append(xhtml(MarkdownRenderer.toHtml(n.getText())));
            f.append("</div></foreignObject>");
        }
        f.append("</g>");
        return growCanvas(svg.substring(0, idx), notes) + f + svg.substring(idx);
    }

    /** {@code <svg …>} の開始タグ。 */
    private static final java.util.regex.Pattern SVG_TAG =
            java.util.regex.Pattern.compile("(?is)<svg\\b[^>]*>");

    /** 開始タグ内の {@code width="123px"} / {@code height="45"}。属性の順序に依存しない。 */
    private static java.util.regex.Pattern lengthAttr(String name) {
        return java.util.regex.Pattern.compile(
                "(?i)\\b" + name + "=\"([0-9.]+)([a-z%]*)\"");
    }

    /** 開始タグから長さ属性を読む。無ければ NaN。 */
    private static double lengthOf(String tag, String name) {
        java.util.regex.Matcher m = lengthAttr(name).matcher(tag);
        return m.find() ? Double.parseDouble(m.group(1)) : Double.NaN;
    }

    /**
     * 付箋が図の外へはみ出す分だけ {@code <svg>} のキャンバスを広げる。
     *
     * <p>付箋の座標は PlantUML SVG と同じ座標系なので、はみ出した付箋は viewBox の外に
     * なり<b>そのまま切り落とされる</b>。要素アンカーの既定配置は要素の右隣なので、
     * 図の右端の要素に貼ったメモはほぼ必ずここに当たる。座標を内容矩形へ寄せて解くと
     * 付箋どうしが重なって読めなくなるので、入れ物の側を広げる — PNG 経路
     * ({@link SvgPreviewPanel#renderDiagramWithNotes}) と同じ規則である。</p>
     */
    private static String growCanvas(String head, List<DiagramNote> notes) {
        double needW = 0;
        double needH = 0;
        for (DiagramNote n : notes) {
            needW = Math.max(needW, n.getX() + n.getWidth());
            needH = Math.max(needH, n.getY() + n.getHeight());
        }
        java.util.regex.Matcher m = SVG_TAG.matcher(head);
        if (!m.find()) {
            return head;
        }
        String tag = m.group();
        double w = lengthOf(tag, "width");
        double h = lengthOf(tag, "height");
        if (Double.isNaN(w) || Double.isNaN(h) || (needW <= w && needH <= h)) {
            return head;
        }
        double nw = Math.max(w, needW);
        double nh = Math.max(h, needH);
        String rebuilt = lengthAttr("width").matcher(tag)
                .replaceFirst("width=\"" + num(nw) + "$2\"");
        rebuilt = lengthAttr("height").matcher(rebuilt)
                .replaceFirst("height=\"" + num(nh) + "$2\"");
        // viewBox も同じだけ広げる (width/height だけ変えると図が引き伸ばされる)。
        rebuilt = rebuilt.replaceAll("(?is)\\bviewBox=\"\\s*([-0-9.]+)\\s+([-0-9.]+)\\s+"
                        + "[-0-9.]+\\s+[-0-9.]+\\s*\"",
                "viewBox=\"$1 $2 " + num(nw) + " " + num(nh) + "\"");
        return head.substring(0, m.start()) + rebuilt + head.substring(m.end());
    }

    /**
     * 図 + 付箋を 1 枚の PNG に描画して保存する。描画は EDT (呼び出し元) で行い、
     * ファイル書き込みのみ背景スレッドに逃がす。
     */
    static void savePng(SvgPreviewPanel preview, File target, Component parent,
                        Consumer<String> reporter) {
        final BufferedImage img = preview.renderDiagramWithNotes(2.0);
        if (img == null) {
            JOptionPane.showMessageDialog(parent, Messages.get("export.noDiagram"),
                    Messages.get("export.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (reporter != null) {
            reporter.accept(Messages.get("status.exportingPng"));
        }
        new SwingWorker<Void, Void>() {
            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    // SVG 側と同じく一時ファイル経由で置換する。対象へ直接書くと、
                    // エンコード失敗やディスク満杯で前回の PNG が壊れた状態で失われる。
                    juml.util.AtomicFileWrite.writeFile(target, tmp -> {
                        if (!ImageIO.write(img, "png", tmp)) {
                            throw new java.io.IOException("no PNG encoder available for export");
                        }
                    });
                } catch (Exception ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (failure != null) {
                    juml.util.AppLog.error(juml.util.ErrorCode.NOTE_001, "NoteExport",
                            "PNG export with notes failed: " + target.getAbsolutePath(), failure);
                    JOptionPane.showMessageDialog(parent,
                            Messages.get("export.failed") + failure.getMessage(),
                            Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
                } else if (reporter != null) {
                    reporter.accept(Messages.get("status.saved") + target.getAbsolutePath());
                }
            }
        }.execute();
    }

    /**
     * 図 ({@code svgNode}) + 付箋を 1 枚の {@link BufferedImage} に描画する。SVG 未表示なら null。
     * {@code desiredScale} は最大辺が 8000px を超えないよう内部でクランプする。EDT から呼ぶこと。
     */
    static BufferedImage rasterize(GraphicsNode svgNode, double w, double h,
                                   double desiredScale, DiagramNotesLayer notesLayer) {
        if (svgNode == null || w <= 0 || h <= 0) {
            return null;
        }
        double scale = desiredScale <= 0 ? 1.0 : desiredScale;
        if (Math.max(w, h) * scale > 8000) {
            scale = 8000.0 / Math.max(w, h);
        }
        int iw = (int) Math.ceil(w * scale);
        int ih = (int) Math.ceil(h * scale);
        BufferedImage img = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, iw, ih);
            g.scale(scale, scale);
            svgNode.paint(g);
            g.setTransform(new AffineTransform());
            notesLayer.paintForExport(g, scale);
        } finally {
            g.dispose();
        }
        return img;
    }

    /** {@link MarkdownRenderer} の HTML を foreignObject 用に XML 整形 (void 要素を自己終端化)。 */
    private static String xhtml(String html) {
        // br/hr/img 等の void 要素を XML 準拠で自己終端化する (将来の記法追加にも耐える)。
        return html.replaceAll("(?i)<(br|hr|img|input|meta|link)([^>]*?)/?>", "<$1$2/>");
    }

    private static String num(double d) {
        if (d == Math.rint(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static String attr(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
