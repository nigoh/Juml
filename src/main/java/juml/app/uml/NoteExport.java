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
    /** ラスタ経路 ({@link NoteRenderer}) と同じ色 — 図種で見た目が変わらないようにする。 */
    private static final String LEADER = "#C9A227";
    private static final String CONNECTOR = "#6B7280";
    private static final String TAG_BG = "#FFFFFF";
    private static final String TAG_FG = "#3A6EA5";
    /** タグ帯の高さ (10px フォントの行高に合わせた固定値)。 */
    private static final int TAG_STRIP_H = 13;

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
    static void writeSvg(File target, String puml,
                         DiagramNotesLayer.ExportOverlay overlay) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, buf);
        String svg = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (overlay != null && overlay.notes != null && !overlay.notes.isEmpty()) {
            svg = injectIntoSvg(svg, overlay);
        }
        // 一時ファイルへ書いてから原子的に置換する。既存ファイルへ直接書いて途中で失敗
        // (ディスク満杯/権限変化) すると、直前の正しい SVG が破損した状態で残るため。
        final String out = svg;
        juml.util.AtomicFileWrite.write(target, os -> os.write(out.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * オーバーレイ全体 (付箋 + コネクタ + リーダー線 + タグ帯) を SVG へ注入する。
     *
     * <p>以前は矩形と本文しか書いていなかったため、同じ右クリックメニューの
     * Save SVG と Save PNG / 画像コピーで<b>中身が違って</b>いた — 利用者が明示的に
     * 引いたコネクタも、要素へのリーダー線も、タグ帯も、SVG のときだけ消えていた。
     * 端点の計算は {@link NoteRenderer#borderPoint} をラスタ経路と共有する。</p>
     */
    static String injectIntoSvg(String svg, DiagramNotesLayer.ExportOverlay overlay) {
        if (svg == null || overlay == null || overlay.notes == null || overlay.notes.isEmpty()) {
            return svg;
        }
        List<DiagramNote> notes = overlay.notes;
        int idx = svg.lastIndexOf("</svg>");
        if (idx < 0) {
            return svg;
        }
        StringBuilder f = new StringBuilder("<g class=\"juml-notes\">");
        // 本文の見た目を決める CSS を 1 度だけ置き、各付箋の div へクラスで効かせる。
        // インライン style だけでは見出し・リストが UA 既定になり、画面 / PNG と
        // 別サイズになってあふれる (規則の出どころは MarkdownRenderer に 1 本化)。
        f.append("<style>").append(MarkdownRenderer.bodyRules(".juml-note-body ", 11))
                .append("</style>");
        // リーダー線・コネクタは付箋の下へ潜らせる (ラスタ経路と同じ描画順)。
        for (double[] l : overlay.leaderLines) {
            f.append("<line x1=\"").append(num(l[0])).append("\" y1=\"").append(num(l[1]))
                    .append("\" x2=\"").append(num(l[2])).append("\" y2=\"").append(num(l[3]))
                    .append("\" stroke=\"").append(LEADER)
                    .append("\" stroke-width=\"1.2\" fill=\"none\"/>");
            f.append("<circle cx=\"").append(num(l[2])).append("\" cy=\"").append(num(l[3]))
                    .append("\" r=\"3\" fill=\"").append(LEADER).append("\"/>");
        }
        for (double[] c : overlay.connectorLines) {
            f.append("<line x1=\"").append(num(c[0])).append("\" y1=\"").append(num(c[1]))
                    .append("\" x2=\"").append(num(c[2])).append("\" y2=\"").append(num(c[3]))
                    .append("\" stroke=\"").append(CONNECTOR)
                    .append("\" stroke-width=\"1.4\" fill=\"none\"/>");
            f.append(arrowHead(c[0], c[1], c[2], c[3]));
        }
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
            f.append("<div xmlns=\"http://www.w3.org/1999/xhtml\" class=\"juml-note-body\" "
                    + "style=\"font:11px sans-serif;color:#222;overflow:hidden;\">");
            f.append(xhtml(MarkdownRenderer.toHtml(n.getText())));
            f.append("</div></foreignObject>");
            f.append(tagStrip(n, x, y, w, h));
        }
        f.append("</g>");
        return growCanvas(svg.substring(0, idx), notes) + f + svg.substring(idx);
    }

    /** 付箋下端のタグ帯 ({@code #a  #b})。タグが無ければ空文字。 */
    private static String tagStrip(DiagramNote n, double x, double y, double w, double h) {
        String tags = NoteRenderer.tagStrip(n);
        if (tags.isEmpty() || h < TAG_STRIP_H + 4) {
            return "";
        }
        double sy = y + h - TAG_STRIP_H;
        return "<rect x=\"" + num(x + 1) + "\" y=\"" + num(sy)
                + "\" width=\"" + num(Math.max(1, w - 2)) + "\" height=\"" + TAG_STRIP_H
                + "\" fill=\"" + TAG_BG + "\" fill-opacity=\"0.75\"/>"
                + "<text x=\"" + num(x + 4) + "\" y=\"" + num(y + h - 4)
                + "\" font-family=\"sans-serif\" font-size=\"10\" fill=\"" + TAG_FG + "\">"
                + attr(tags) + "</text>";
    }

    /** {@code (x2,y2)} に {@code (x1,y1)} から向かう矢じり (ラスタ経路と同じ形)。 */
    private static String arrowHead(double x1, double y1, double x2, double y2) {
        double ang = Math.atan2(y2 - y1, x2 - x1);
        double spread = Math.toRadians(22);
        double len = 9;
        double ax = x2 - len * Math.cos(ang - spread);
        double ay = y2 - len * Math.sin(ang - spread);
        double bx = x2 - len * Math.cos(ang + spread);
        double by = y2 - len * Math.sin(ang + spread);
        return "<polygon points=\"" + num(x2) + "," + num(y2) + " " + num(ax) + "," + num(ay)
                + " " + num(bx) + "," + num(by) + "\" fill=\"" + CONNECTOR + "\"/>";
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
        rebuilt = rewriteStyleLengths(rebuilt, nw, nh);
        return head.substring(0, m.start()) + rebuilt + head.substring(m.end());
    }

    /**
     * 開始タグの inline style にある {@code width:…px} / {@code height:…px} も書き換える。
     *
     * <p>PlantUML はキャンバス寸法を<b>4 か所</b>に書く — {@code width} 属性・
     * {@code height} 属性・{@code viewBox}・そして
     * {@code style="width:98px;height:144px;background:#FFFFFF;"}。SVG2 では
     * {@code width}/{@code height} は geometry presentation property なので
     * <b>inline style が属性に勝つ</b>。3 か所だけ広げると、ブラウザは旧サイズで
     * レイアウトするのに viewBox は新サイズ、しかも PlantUML は
     * {@code preserveAspectRatio="none"} を付けるため、図も付箋も<b>非等方に潰れて</b>
     * 表示される (実測: 横 0.22 倍・縦 0.9 倍)。背景色など他の宣言は残す。</p>
     */
    private static String rewriteStyleLengths(String tag, double nw, double nh) {
        java.util.regex.Matcher sm = STYLE_ATTR.matcher(tag);
        if (!sm.find()) {
            return tag;
        }
        String style = sm.group(1)
                .replaceAll("(?i)\\bwidth\\s*:\\s*[0-9.]+px", "width:" + num(nw) + "px")
                .replaceAll("(?i)\\bheight\\s*:\\s*[0-9.]+px", "height:" + num(nh) + "px");
        return tag.substring(0, sm.start(1)) + style + tag.substring(sm.end(1));
    }

    /** 開始タグ内の {@code style="…"}。 */
    private static final java.util.regex.Pattern STYLE_ATTR =
            java.util.regex.Pattern.compile("(?is)\\bstyle=\"([^\"]*)\"");

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

    /**
     * {@link MarkdownRenderer} の HTML を foreignObject 用に XML 整形 (void 要素を自己終端化)。
     *
     * <p>整形できても<b>整形式 (well-formed) とは限らない</b>。{@code MarkdownRenderer}
     * のインライン装飾は正規表現の置換なので、強調記号が既に挿入されたタグの境界を
     * またぐと {@code <a …><b>a</a></b>} のような入れ子崩れを出しうる。SVG は XML
     * なので、これが 1 つ混ざるとファイル全体が致命的パースエラーになり、ブラウザでも
     * Juml 自身の Batik でも<b>開けなくなる</b> — 同じ本文をラスタ経路は
     * {@code JEditorPane} の寛容なパーサで普通に描くので、SVG で保存したときだけ
     * 成果物が丸ごと失われる形だった。</p>
     *
     * <p>整形式でなければ装飾を捨てて平文へ落とす。<b>飾りが減ること</b>と
     * <b>ファイルが開けないこと</b>なら、前者を選ぶ。</p>
     */
    private static String xhtml(String html) {
        // br/hr/img 等の void 要素を XML 準拠で自己終端化する (将来の記法追加にも耐える)。
        String x = html.replaceAll("(?i)<(br|hr|img|input|meta|link)([^>]*?)/?>", "<$1$2/>");
        if (isWellFormedFragment(x)) {
            return x;
        }
        return stripTags(x);
    }

    /**
     * タグを落として本文だけにする。<b>本文の文字は変えない</b>。
     *
     * <p>入力は {@code MarkdownRenderer.escape} を通った後の文字列なので、{@code &amp;} は
     * 既に {@code &amp;amp;} になっている。ここで再エスケープすると {@code &amp;amp;amp;} になり、
     * 利用者には {@code &amp;amp;} という文字列がそのまま見える — 画面と PNG は
     * {@code &amp;} と正しく出るので、<b>同じ付箋が書き出し形式で別物になる</b>。</p>
     *
     * <p>タグ剥がしも {@code <[^>]*>} ではいけない。この分岐へ来る断片は入れ子が
     * 崩れているので、属性値の中の {@code <} でマッチが途中で切れ、{@code href} の
     * 断片が本文として残る (実測: 本文 {@code t} が {@code a"&gt;t} になった)。
     * 引用符の中を数えながら剥がす。</p>
     *
     * <p>この関数の言明は「<b>装飾</b>を捨てる」であって、本文の文字を変えることではない。</p>
     */
    private static String stripTags(String html) {
        StringBuilder out = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            char c = html.charAt(i);
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            // タグの終わりを探す。属性値の引用符の中の '>' はタグを閉じない。
            int j = i + 1;
            char quote = 0;
            while (j < html.length()) {
                char d = html.charAt(j);
                if (quote != 0) {
                    if (d == quote) {
                        quote = 0;
                    }
                } else if (d == '"' || d == '\'') {
                    quote = d;
                } else if (d == '>') {
                    break;
                }
                j++;
            }
            i = j < html.length() ? j + 1 : html.length();
        }
        return out.toString();
    }

    /** 断片が XML として整形式か (foreignObject へ入れて壊さないか)。 */
    private static boolean isWellFormedFragment(String fragment) {
        try {
            javax.xml.parsers.DocumentBuilderFactory dbf =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(false);
            dbf.setValidating(false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
            db.setErrorHandler(null);
            String doc = "<juml-fragment>" + fragment + "</juml-fragment>";
            db.parse(new org.xml.sax.InputSource(new java.io.StringReader(doc)));
            return true;
        } catch (Exception ex) {
            return false;
        }
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
