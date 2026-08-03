// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * ステレオタイプ名に {@code <} を含めると PlantUML がステレオタイプとして解釈しない、
 * という描画劣化の回帰テスト。
 *
 * <p>AAOS の {@code @AddedInOrBefore} バッジは以前 {@code "API <=33"} を返しており、
 * {@code class Foo <<API <=33>>} は<b>描画エラーにならない</b>ものの、ギュメ {@code «…»} が
 * 付かず生の {@code <<API <=33>>} がクラス名として描かれ、さらに
 * {@code skinparam class { BackgroundColor<<…>> }} による色分けも当たらなかった
 * (成功扱いのまま見た目だけ壊れるため気付きにくい)。</p>
 *
 * <p>そのため文字列一致ではなく、実際に {@link PlantUmlRenderer#renderSvg} を通した
 * SVG のテキストと塗り色で検証する。</p>
 */
public class PlantUmlStereotypeRenderTest {

    /** SVG 中のギュメ (U+00AB «) の XML 数値参照。ステレオタイプ認識の証拠。 */
    private static final String GUILLEMET = "&#171;";

    private String savedFallbackFont;

    @Before
    public void setUp() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        savedFallbackFont = PlantUmlRenderer.getFallbackFontName();
    }

    @After
    public void tearDown() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        PlantUmlRenderer.setFallbackFontName(savedFallbackFont);
    }

    private static String render(String badge) throws Exception {
        String puml = "@startuml\n"
                + "skinparam class {\n  BackgroundColor<<" + badge + ">> #FFDDDD\n}\n"
                + "class Foo <<" + badge + ">>\n@enduml\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void apiLevelBadgeIsRenderedAsRealStereotype() throws Exception {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("android.car");
        c.setSimpleName("Foo");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("AddedInOrBefore(majorVersion=33)");
        String badge = AaosPattern.apiLevelBadge(c);

        String svg = render(badge);
        assertTrue("ステレオタイプとして認識され «…» が付くこと: " + badge,
                svg.contains(GUILLEMET));
        assertFalse("生の '<<' がクラス名として描かれないこと",
                svg.contains("&lt;&lt;"));
        assertTrue("ステレオタイプ別の背景色が当たること", svg.contains("#FFDDDD"));
    }

    @Test
    public void angleBracketBadgeIsNotRecognized() throws Exception {
        // 修正前の値。ステレオタイプとして解釈されないことを明示的に固定しておく
        // (将来 PlantUML 側が救済したら、この期待が落ちて気付ける)。
        String svg = render("API <=33");
        assertTrue("'<' 入りは生の '<<' として描かれる", svg.contains("&lt;&lt;"));
        assertFalse("'<' 入りでは色分けが当たらない", svg.contains("#FFDDDD"));
    }
}
