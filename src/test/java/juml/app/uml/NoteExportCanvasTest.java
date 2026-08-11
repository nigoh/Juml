// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 書き出しキャンバスが図と<b>全付箋</b>を含むこと、そして SVG と PNG が<b>同じ答え</b>を
 * 使うことの回帰テスト。
 *
 * <p>要素アンカー付箋の既定配置は「要素の右隣」なので、図の右端の要素に貼ったメモは
 * ほぼ必ず内容矩形の外に出る。以前は SVG 経路だけが座標を内容矩形へ寄せ、PNG 経路
 * ({@code renderDiagramWithNotes} → {@code rasterize} → {@code paintForExport}) は
 * 生座標のまま図の寸法のキャンバスへ描いていた。同じ右クリックメニューに並ぶ
 * Save SVG / Save PNG / Copy image で<b>結果が違う</b>という、利用者が原因を追えない形の
 * 食い違いになっていた。寄せると付箋どうしが重なるので、入れ物の側を広げる。</p>
 */
public class NoteExportCanvasTest {

    /** {@code <svg …>} の長さ属性を読む。 */
    private static double svgLength(String svg, String name) {
        Matcher tag = Pattern.compile("(?is)<svg\\b[^>]*>").matcher(svg);
        assertTrue("<svg> 開始タグがあること", tag.find());
        Matcher m = Pattern.compile("(?i)\\b" + name + "=\"([0-9.]+)").matcher(tag.group());
        assertTrue(name + " 属性があること", m.find());
        return Double.parseDouble(m.group(1));
    }

    private static String render(String puml) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        juml.core.formats.uml.PlantUmlRenderer.renderSvg(puml, buf);
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 図の右外へ出る付箋 1 枚。 */
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

    /** SVG の注入は、はみ出した付箋の分だけキャンバスを広げること。 */
    @Test
    public void injectingNotesGrowsTheSvgCanvas() throws IOException {
        String svg = render("@startuml\nclass A\nclass B\nA --> B\n@enduml\n");
        double w0 = svgLength(svg, "width");
        double h0 = svgLength(svg, "height");

        List<DiagramNote> notes = new ArrayList<>();
        notes.add(noteAt(w0 + 40, 10, 240, 150));
        String grown = NoteExport.injectIntoSvg(svg, DiagramNotesLayer.ExportOverlay.ofNotes(notes));

        assertTrue("付箋の右端まで広がること: " + svgLength(grown, "width"),
                svgLength(grown, "width") >= w0 + 40 + 240);
        assertTrue("縦は元のままでよいこと", svgLength(grown, "height") >= h0);
        assertTrue("viewBox も一緒に広げること (width だけだと図が伸びる)",
                grown.matches("(?s).*viewBox=\"0 0 " + (long) (w0 + 40 + 240) + " .*"));
    }

    /** 非退行: 図の中に収まる付箋ではキャンバスを変えないこと。 */
    @Test
    public void aNoteInsideTheDiagramLeavesTheCanvasAlone() throws IOException {
        String svg = render("@startuml\nclass A\nclass B\nA --> B\n@enduml\n");
        double w0 = svgLength(svg, "width");

        List<DiagramNote> notes = new ArrayList<>();
        notes.add(noteAt(0, 0, 10, 10));

        assertEquals(w0, svgLength(NoteExport.injectIntoSvg(svg,
                DiagramNotesLayer.ExportOverlay.ofNotes(notes)), "width"), 0.001);
    }

    /** レイヤが答える書き出し範囲は、図と全付箋を含むこと (PNG 経路が使う値)。 */
    @Test
    public void theLayerReportsACanvasThatContainsEveryNote() {
        DiagramNotesLayer layer = new DiagramNotesLayer(new javax.swing.JPanel());
        List<DiagramNote> notes = new ArrayList<>();
        notes.add(noteAt(500, 10, 240, 150));
        notes.add(noteAt(20, 900, 100, 80));
        layer.setData(notes, Collections.emptyList());

        double[] box = layer.exportBounds(300, 200);
        assertEquals("右端まで広がること", 740.0, box[0], 0.001);
        assertEquals("下端まで広がること", 980.0, box[1], 0.001);

        // 非退行: 図の中に収まっていれば図の寸法のまま。
        DiagramNotesLayer inside = new DiagramNotesLayer(new javax.swing.JPanel());
        inside.setData(List.of(noteAt(10, 10, 50, 50)), Collections.emptyList());
        double[] same = inside.exportBounds(300, 200);
        assertEquals(300.0, same[0], 0.001);
        assertEquals(200.0, same[1], 0.001);
    }

    /**
     * キャンバスを広げるとき、{@code <svg>} の<b>inline style</b> も一緒に書き換えること。
     *
     * <p>PlantUML はキャンバス寸法を 4 か所に書く — {@code width} 属性・{@code height} 属性・
     * {@code viewBox}・そして {@code style="width:98px;height:144px;background:#FFF;"}。
     * SVG2 では {@code width}/{@code height} は geometry presentation property なので
     * <b>inline style が属性に勝つ</b>。3 か所だけ広げると、ブラウザは旧サイズでレイアウト
     * するのに viewBox は新サイズ、しかも PlantUML は {@code preserveAspectRatio="none"} を
     * 付けるので、図も付箋も<b>非等方に潰れて</b>表示される (実測: 横 0.22 倍・縦 0.9 倍)。
     * 「はみ出したらキャンバスを広げる」という 1 つの規則を、寸法を書いている場所の
     * 一部にしか適用していなかった、という形である。</p>
     */
    @Test
    public void growingTheCanvasAlsoRewritesTheInlineStyle() throws IOException {
        String svg = render("@startuml\nclass Alpha\nclass Beta\nAlpha --> Beta\n@enduml\n");
        double w0 = svgLength(svg, "width");
        double h0 = svgLength(svg, "height");

        List<DiagramNote> notes = new ArrayList<>();
        notes.add(noteAt(w0 + 40, h0 + 20, 240, 150));
        String grown = NoteExport.injectIntoSvg(svg,
                DiagramNotesLayer.ExportOverlay.ofNotes(notes));

        String tag = rootTag(grown);
        assertTrue("inline style に旧幅が残らないこと: " + tag,
                !tag.contains("width:" + (long) w0 + "px"));
        assertTrue("inline style に旧高さが残らないこと: " + tag,
                !tag.contains("height:" + (long) h0 + "px"));
        assertEquals("style の幅が属性と一致すること",
                svgLength(grown, "width"), styleLength(tag, "width"), 0.001);
        assertEquals("style の高さが属性と一致すること",
                svgLength(grown, "height"), styleLength(tag, "height"), 0.001);
        assertTrue("背景色など他の宣言は残すこと: " + tag, tag.contains("background:"));
    }

    /** {@code <svg …>} 開始タグ。 */
    private static String rootTag(String svg) {
        Matcher m = Pattern.compile("(?is)<svg\\b[^>]*>").matcher(svg);
        assertTrue("<svg> 開始タグがあること", m.find());
        return m.group();
    }

    /** 開始タグの inline style から {@code width:…px} を読む。 */
    private static double styleLength(String tag, String name) {
        Matcher m = Pattern.compile("(?i)\\b" + name + "\\s*:\\s*([0-9.]+)px").matcher(tag);
        assertTrue("style に " + name + " があること: " + tag, m.find());
        return Double.parseDouble(m.group(1));
    }

    /**
     * SVG 注入は、ラスタ経路が描くもの (コネクタ・リーダー線・タグ帯) も書くこと。
     *
     * <p>注入は矩形と本文しか書いていなかったので、同じ右クリックメニューの Save SVG と
     * Save PNG / 画像コピーで<b>中身が違って</b>いた。とくにコネクタは利用者が明示的に
     * 引いた線なので、SVG で保存したときだけ<b>付箋どうしの関係が失われる</b>。
     * 端点は {@link NoteRenderer#borderPoint} をラスタ経路と共有する。</p>
     */
    @Test
    public void theSvgOverlayDrawsWhatTheRasterOverlayDraws() throws IOException {
        String svg = render("@startuml\nclass A\nclass B\nA --> B\n@enduml\n");

        DiagramNotesLayer layer = new DiagramNotesLayer(new javax.swing.JPanel());
        DiagramNote a = noteAt(10, 10, 120, 60);
        DiagramNote b = noteAt(300, 200, 120, 60);
        a.setTags(List.of("todo"));
        List<DiagramNote> notes = new ArrayList<>();
        notes.add(a);
        notes.add(b);
        DiagramConnector c = new DiagramConnector(a.getId(), b.getId());
        layer.setData(notes, List.of(c));

        String out = NoteExport.injectIntoSvg(svg, layer.exportOverlay());

        assertTrue("コネクタの線が出ること", out.contains("<line") && out.contains("#6B7280"));
        assertTrue("矢じりが出ること", out.contains("<polygon"));
        assertTrue("タグ帯の文字が出ること", out.contains("#todo"));
    }

    /** 非退行: コネクタもタグも無ければ余計な要素を書かないこと。 */
    @Test
    public void withoutConnectorsOrTagsNothingExtraIsWritten() throws IOException {
        String svg = render("@startuml\nclass A\n@enduml\n");
        DiagramNotesLayer layer = new DiagramNotesLayer(new javax.swing.JPanel());
        layer.setData(List.of(noteAt(5, 5, 60, 40)), Collections.emptyList());

        String out = NoteExport.injectIntoSvg(svg, layer.exportOverlay());
        String overlay = out.substring(out.indexOf("<g class=\"juml-notes\">"));
        assertTrue("線を書かないこと", !overlay.contains("<line"));
        assertTrue("矢じりを書かないこと", !overlay.contains("<polygon"));
    }
}
