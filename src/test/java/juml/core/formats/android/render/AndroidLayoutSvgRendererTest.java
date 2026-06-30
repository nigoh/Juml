// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import juml.core.formats.android.AndroidLayoutInfo;
import juml.core.formats.android.AndroidLayoutParser;
import juml.core.formats.android.LayoutViewNode;
import juml.core.formats.uml.PlantUmlRenderer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AndroidLayoutSvgRenderer} の SVG 出力のユニットテスト。
 */
public class AndroidLayoutSvgRendererTest {

    @Test(expected = IllegalArgumentException.class)
    public void nullLayoutThrows() {
        AndroidLayoutSvgRenderer.render(null);
    }

    @Test
    public void emitsWellFormedSvgEnvelope() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        String svg = AndroidLayoutSvgRenderer.render(info);
        assertNotNull(svg);
        assertTrue("XML 宣言", svg.startsWith("<?xml"));
        assertTrue("svg 要素", svg.contains("<svg"));
        assertTrue("閉じ svg", svg.trim().endsWith("</svg>"));
        // ボックスが矩形として描かれる。
        assertTrue("rect 要素", svg.contains("<rect"));
        // PlantUML レンダラが SVG をそのまま通すと判定できる。
        assertTrue(PlantUmlRenderer.looksLikeSvg(svg));
    }

    @Test
    public void rendersButtonTextFromSample() throws IOException {
        AndroidLayoutInfo info = parseSample("activity_main.xml");
        String svg = AndroidLayoutSvgRenderer.render(info);
        // ボタンのテキストが描画される (sanitize 後)。
        assertTrue("OK", svg.contains(">OK<"));
        assertTrue("Cancel", svg.contains(">Cancel<"));
        assertTrue("TextView text", svg.contains("Hello World"));
        // タイトルにファイル名と画面サイズが入る。
        assertTrue("title", svg.contains("activity_main.xml"));
    }

    @Test
    public void editTextShowsHintWhenNoText() {
        LayoutViewNode root = new LayoutViewNode("LinearLayout");
        LayoutViewNode edit = new LayoutViewNode("EditText");
        edit.setId("@+id/email");
        edit.setWidth("match_parent");
        edit.setHeight("wrap_content");
        edit.getExtraAttributes().put("android:hint", "Email address");
        root.getChildren().add(edit);
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setRoot(root);
        info.setFileName("login.xml");
        String svg = AndroidLayoutSvgRenderer.render(info);
        assertTrue("hint を表示", svg.contains("Email address"));
        assertFalse("id フォールバックは使わない", svg.contains("#email"));
    }

    @Test
    public void resolvesStringReferences() {
        LayoutViewNode root = new LayoutViewNode("LinearLayout");
        LayoutViewNode tv = new LayoutViewNode("TextView");
        tv.setText("@string/greeting");
        tv.setWidth("wrap_content");
        tv.setHeight("wrap_content");
        root.getChildren().add(tv);
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setRoot(root);
        info.setFileName("g.xml");
        AndroidLayoutSvgRenderer.Options o = new AndroidLayoutSvgRenderer.Options();
        o.stringResolver = ref -> "@string/greeting".equals(ref) ? "Konnichiwa" : null;
        String svg = AndroidLayoutSvgRenderer.render(info, o);
        assertTrue("解決済み文言", svg.contains("Konnichiwa"));
        assertFalse("参照は残らない", svg.contains("@string/greeting"));
    }

    @Test
    public void escapesXmlSpecialCharacters() {
        LayoutViewNode root = new LayoutViewNode("LinearLayout");
        LayoutViewNode tv = new LayoutViewNode("TextView");
        tv.setText("a<b>&\"c");
        tv.setWidth("wrap_content");
        tv.setHeight("wrap_content");
        root.getChildren().add(tv);
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setRoot(root);
        info.setFileName("e.xml");
        String svg = AndroidLayoutSvgRenderer.render(info);
        assertTrue(svg.contains("&lt;b&gt;"));
        assertTrue(svg.contains("&amp;"));
        assertFalse("生の < が本文に残らない", svg.contains("a<b>"));
    }

    @Test
    public void includeAndFragmentRenderAsPlaceholders() {
        LayoutViewNode root = new LayoutViewNode("FrameLayout");
        root.setWidth("match_parent");
        root.setHeight("match_parent");
        LayoutViewNode inc = new LayoutViewNode("include");
        inc.setIncludeLayoutRef("@layout/toolbar");
        LayoutViewNode frag = new LayoutViewNode("fragment");
        frag.setFragmentClassName("com.example.MyFragment");
        root.getChildren().add(inc);
        root.getChildren().add(frag);
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setRoot(root);
        info.setFileName("f.xml");
        String svg = AndroidLayoutSvgRenderer.render(info);
        assertTrue("include placeholder", svg.contains("include"));
        assertTrue("fragment placeholder", svg.contains("MyFragment"));
    }

    @Test
    public void landscapeQualifierWidensCanvas() {
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        LayoutViewNode root = new LayoutViewNode("FrameLayout");
        root.setWidth("match_parent");
        root.setHeight("match_parent");
        info.setRoot(root);
        info.setFileName("activity_main.xml");
        info.setConfigQualifier("land");
        String svg = AndroidLayoutSvgRenderer.render(info);
        // 横画面は幅 640dp が注記に出る。
        assertTrue("横画面サイズ", svg.contains("640") && svg.contains("360"));
    }

    private static AndroidLayoutInfo parseSample(String name) throws IOException {
        try (InputStream in = AndroidLayoutSvgRendererTest.class
                .getResourceAsStream("/samples/layouts/" + name)) {
            assertNotNull("sample not found: " + name, in);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            AndroidLayoutInfo info = AndroidLayoutParser.parse(
                    new String(bos.toByteArray(), StandardCharsets.UTF_8));
            info.setFileName(name);
            return info;
        }
    }
}
