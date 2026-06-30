// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 単体クラス図 (GUI のツリーから 1 クラスを開いた状態) のリグレッションテスト。
 *
 * <p>GUI はその図種で <b>フォーカス強調色 + インタラクティブリンク</b> を併用する。
 * 以前はクラス宣言を {@code #color [[link]]} の順で出していたため PlantUML が
 * レイアウトエラーになり「この図を描画できませんでした」と表示されていた。
 * 正しい {@code [[link]] #color} 順で実際に Smetana 描画が通ることを保証する。</p>
 */
public class PlantUmlClassFocusLinkRenderTest {

    private static String generate(String src, String focusFqn) {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.interactiveLinks = true;        // GUI 既定 (リンク有効)
        o.focusClass = focusFqn;          // GUI: クリックしたクラスを焦点強調
        o.includeLegend = true;
        return PlantUmlClassDiagram.generate(classes, o);
    }

    private static void assertRenders(String puml) {
        // 生成 PlantUML 自体がリンタを通ること
        assertEquals("生成 PlantUML にゴミが無いこと: "
                + PlantUmlSyntaxChecker.summarize(puml),
                "", PlantUmlSyntaxChecker.summarize(puml));
        // GUI 既定の Smetana で実際に描画が通ること (エラー画像にならない)
        PlantUmlRenderer.setGraphvizAvailable(false);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PlantUmlRenderer.renderSvg(puml, bos);
            assertTrue("非空の SVG が出ること", bos.size() > 0);
        } catch (Exception e) {
            throw new AssertionError("単体クラス図 (focus+links) の描画に失敗: "
                    + e.getMessage() + "\n--- PUML ---\n" + puml, e);
        }
    }

    @Test
    public void focusedClassWithLinksRenders() {
        String src = "package demo;\n"
                + "import java.util.List;\n"
                + "public class Sample<T extends Comparable<T>> {\n"
                + "  private List<T> data;\n"
                + "  public T head() { return null; }\n"
                + "}\n";
        assertRenders(generate(src, "demo.Sample"));
    }

    @Test
    public void focusedClassWithNeighborRenders() {
        String src = "package a.b;\n"
                + "import java.util.Map;\n"
                + "public class Outer {\n"
                + "  public class Inner {}\n"
                + "  private Map<String, Outer.Inner> m;\n"
                + "  public Outer.Inner make() { return null; }\n"
                + "}\n";
        assertRenders(generate(src, "a.b.Outer"));
    }

    @Test
    public void emittedClassLineHasLinkBeforeColor() {
        String src = "package demo;\npublic class Foo { public int x; }\n";
        String puml = generate(src, "demo.Foo");
        // リンクが色より前に来ていること (描画失敗の直接原因だった順序の固定化)
        int link = puml.indexOf("[[juml://class/demo.Foo]]");
        int color = puml.indexOf("#FFF3CD");
        assertTrue("リンクと色の両方が出ること", link >= 0 && color >= 0);
        assertTrue("リンクは色より前に出ること ([[link]] #color)", link < color);
    }
}
