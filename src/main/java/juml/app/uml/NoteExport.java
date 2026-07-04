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
import java.nio.file.Files;
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
        java.nio.file.Path targetPath = target.toPath();
        java.nio.file.Path tmp = targetPath.resolveSibling(targetPath.getFileName() + ".tmp");
        Files.write(tmp, svg.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(tmp, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
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
        return svg.substring(0, idx) + f + svg.substring(idx);
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
                    ImageIO.write(img, "png", target);
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
