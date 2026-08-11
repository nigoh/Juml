// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * 付箋の書き出しが「画面と同じもの」を出すことの回帰テスト (ラウンド 26 の続き)。
 *
 * <p>ラウンド 26 は SVG 注入にコネクタ・リーダー線・タグ帯を足して PNG 経路と一本化した。
 * 残っていた食い違いは<b>本文の中身と装飾の規則</b>の側にあった:</p>
 *
 * <ul>
 *   <li>空本文のとき、ラスタ経路だけが「ダブルクリックで編集…」という<b>画面の操作ヒント</b>を
 *       PNG / 画像コピーへ焼き込んでいた (SVG では無地)。空の付箋は色マーカーとしての
 *       正当な使い方がある。</li>
 *   <li>本文の見た目を決める CSS ({@code h1} のサイズ、リストの字下げ) は
 *       {@code wrapDocument} にしか無く、SVG 注入は素の HTML を入れていたので
 *       SVG だけ UA 既定サイズになって<b>縦にあふれて切れて</b>いた。</li>
 *   <li>タグ帯だけラスタ経路が倍率非追従だったので、同じ付箋のタグが PNG と SVG で
 *       別の大きさになっていた。</li>
 *   <li>入れ子が崩れた HTML (正規表現の置換なので起きうる) が混ざると、SVG は XML なので
 *       <b>ファイルごと開けなくなる</b>。ラスタ経路は寛容なパーサなので普通に描ける。</li>
 * </ul>
 */
public class ExportShowsWhatTheScreenShowsTest {

    private static String baseSvg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg("@startuml\nclass A\nclass B\nA --> B\n@enduml\n", out);
        return out.toString("UTF-8");
    }

    private static boolean parsesAsXml(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f =
                    javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setValidating(false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            b.parse(new org.xml.sax.InputSource(new StringReader(xml)));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static DiagramNote note(String text, double w, double h, String... tags) {
        DiagramNote n = new DiagramNote(10, 10, w, h, text);
        if (tags.length > 0) {
            n.setTags(Arrays.asList(tags));
        }
        return n;
    }

    /** 入れ子が崩れる本文でも、書き出した SVG が XML として開けること。 */
    @Test
    public void malformedInlineMarkupDoesNotCorruptTheWholeSvg() throws IOException {
        String svg = NoteExport.injectIntoSvg(baseSvg(),
                DiagramNotesLayer.ExportOverlay.ofNotes(
                        List.of(note("[**a](http://x)**", 200, 100))));
        assertTrue("SVG が XML としてパースできること (装飾より開けることを優先)",
                parsesAsXml(svg));
    }

    /** 非退行: 普通の markdown は装飾を保ったまま整形式であること。 */
    @Test
    public void ordinaryMarkupKeepsItsMarkupAndStaysWellFormed() throws IOException {
        String svg = NoteExport.injectIntoSvg(baseSvg(),
                DiagramNotesLayer.ExportOverlay.ofNotes(
                        List.of(note("**bold** and [link](http://x)", 240, 150))));
        assertTrue("整形式であること", parsesAsXml(svg));
        assertTrue("装飾が落ちていないこと", svg.contains("<b>bold</b>"));
    }

    /** SVG 側にも画面 / PNG と同じ本文 CSS が入ること。 */
    @Test
    public void theSvgCarriesTheSameBodyCssAsTheScreen() throws IOException {
        String svg = NoteExport.injectIntoSvg(baseSvg(),
                DiagramNotesLayer.ExportOverlay.ofNotes(
                        List.of(note("# Heading\n- one\n- two", 240, 150))));
        // 出どころは MarkdownRenderer に 1 本化してあるので、そちらと突き合わせる。
        String rules = MarkdownRenderer.bodyRules(".juml-note-body ", 11);
        assertTrue("本文 CSS が SVG に埋め込まれていること", svg.contains(rules));
        assertTrue("見出しのサイズ指定が入っていること",
                svg.contains(".juml-note-body h1{font-size:15px"));
        assertTrue("リストの字下げ指定が入っていること", svg.contains(".juml-note-body ul"));
        assertTrue("整形式であること", parsesAsXml(svg));
    }

    /** 空本文の付箋に「ダブルクリックで編集…」を焼き込まないこと。 */
    @Test
    public void theExportDoesNotBakeInTheEditHint() throws Exception {
        assumeFalse("headless では描画できない", GraphicsEnvironment.isHeadless());
        JPanel owner = new JPanel();
        DiagramNotesLayer[] layer = new DiagramNotesLayer[1];
        SwingUtilities.invokeAndWait(() -> {
            layer[0] = new DiagramNotesLayer(owner);
            layer[0].setData(new ArrayList<>(List.of(note("", 240, 150))),
                    Collections.emptyList());
        });
        BufferedImage exported = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage onScreen = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            paintWhite(exported);
            Graphics2D g = exported.createGraphics();
            layer[0].paintForExport(g, 1.0);
            g.dispose();
            paintWhite(onScreen);
            Graphics2D g2 = onScreen.createGraphics();
            layer[0].paint(g2, 1.0);
            g2.dispose();
        });
        int diff = 0;
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 300; x++) {
                if (exported.getRGB(x, y) != onScreen.getRGB(x, y)) {
                    diff++;
                }
            }
        }
        assertTrue("書き出しだけヒント文字が出ないので画面と差が出ること (差 0 = 焼き込み)",
                diff > 0);
    }

    private static void paintWhite(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
    }

    /** タグ帯が倍率に追従すること (PNG は scale=2.0 で書き出す)。 */
    @Test
    public void theTagStripScalesWithTheExportZoom() throws Exception {
        assumeFalse("headless では描画できない", GraphicsEnvironment.isHeadless());
        JPanel owner = new JPanel();
        DiagramNotesLayer[] layer = new DiagramNotesLayer[1];
        SwingUtilities.invokeAndWait(() -> layer[0] = new DiagramNotesLayer(owner));
        int atOne = tagBandHeight(layer[0], 1.0);
        int atTwo = tagBandHeight(layer[0], 2.0);
        assertTrue("zoom=1 でタグ帯が描かれること", atOne > 0);
        assertTrue("zoom=2 では帯も倍率に追従して高くなること (" + atOne + " -> " + atTwo + ")",
                atTwo > atOne * 1.5);
    }

    /** タグ有り/無しの差分行数 = タグ帯の高さ。 */
    private static int tagBandHeight(DiagramNotesLayer layer, double zoom) throws Exception {
        int w = (int) (300 * zoom);
        int h = (int) (240 * zoom);
        BufferedImage[] shots = new BufferedImage[2];
        for (int k = 0; k < 2; k++) {
            final DiagramNote n = k == 0
                    ? note("body", 120, 80) : note("body", 120, 80, "marker");
            SwingUtilities.invokeAndWait(() -> layer.setData(
                    new ArrayList<>(List.of(n)), Collections.emptyList()));
            final BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> {
                paintWhite(img);
                Graphics2D g = img.createGraphics();
                layer.paintForExport(g, zoom);
                g.dispose();
            });
            shots[k] = img;
        }
        Set<Integer> rows = new TreeSet<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (shots[0].getRGB(x, y) != shots[1].getRGB(x, y)) {
                    rows.add(y);
                    break;
                }
            }
        }
        return rows.size();
    }

    /** ミニマップも書き出しキャンバスを見ること (図の寸法のままだと付箋へ行けない)。 */
    @Test
    public void theMinimapCoversTheNotesOutsideTheDiagram() throws Exception {
        assumeFalse("headless では描画できない", GraphicsEnvironment.isHeadless());
        SvgPreviewPanel[] panel = new SvgPreviewPanel[1];
        javax.swing.JScrollPane[] scroll = new javax.swing.JScrollPane[1];
        javax.swing.JFrame[] frame = new javax.swing.JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = new SvgPreviewPanel();
                panel[0].setSvgGraphicsNode(
                        new org.apache.batik.gvt.CompositeGraphicsNode(), 300, 200);
                scroll[0] = new javax.swing.JScrollPane(panel[0]);
                frame[0] = new javax.swing.JFrame();
                frame[0].setContentPane(scroll[0]);
                frame[0].setSize(400, 300);
                frame[0].setVisible(true);
            });
            Thread.sleep(300);
            SwingUtilities.invokeAndWait(() -> {
                // 図 (300x200) の右外へ付箋を置く -> 横スクロールが必要になる
                panel[0].notes().setData(
                        new ArrayList<>(List.of(new DiagramNote(1000, 20, 280, 120, "far"))),
                        Collections.emptyList());
                panel[0].revalidate();
            });
            Thread.sleep(300);
            int[] painted = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("書き出しキャンバスが付箋まで広がっていること",
                        1280.0, panel[0].exportCanvas()[0], 0.5);
                java.awt.Dimension ext = scroll[0].getViewport().getExtentSize();
                java.awt.Dimension ps = panel[0].getPreferredSize();
                BufferedImage img = new BufferedImage(
                        Math.max(ps.width, ext.width) + 40,
                        Math.max(ps.height, ext.height) + 40, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                new DiagramMinimap().paint(g, panel[0]);
                g.dispose();
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        if ((img.getRGB(x, y) >>> 24) != 0) {
                            painted[0]++;
                        }
                    }
                }
            });
            assertTrue("付箋ではみ出しているのだからミニマップが出ること", painted[0] > 0);
        } finally {
            if (frame[0] != null) {
                SwingUtilities.invokeAndWait(() -> frame[0].dispose());
            }
        }
    }
}
