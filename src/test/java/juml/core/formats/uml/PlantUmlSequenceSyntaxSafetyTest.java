// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * シーケンス図生成が「構文的に妥当な」PlantUML を出すことを保証する回帰テスト。
 *
 * <p>participant 宣言は常に引用符付き ({@code participant "Name"}) なのに、矢印や
 * activate/deactivate 行で同じ名前を素のまま参照していたため、引用符が必要な名前
 * (配列添字 {@code s[0]}・メソッド呼び出し {@code getX()}・内部クラスの {@code $} 等) で
 * 宣言と参照が食い違い、PlantUML が構文エラー画像を返していた。{@link
 * PlantUmlSequenceDiagram#idRef(String)} で参照側も同じ条件で引用符を付けることを検証する。</p>
 */
public class PlantUmlSequenceSyntaxSafetyTest {

    @Test
    public void idRefLeavesSimpleNamesBare() {
        assertEquals("A", PlantUmlSequenceDiagram.idRef("A"));
        assertEquals("foo", PlantUmlSequenceDiagram.idRef("foo"));
        assertEquals("IAudio", PlantUmlSequenceDiagram.idRef("IAudio"));
        assertEquals("my_Class1", PlantUmlSequenceDiagram.idRef("my_Class1"));
    }

    @Test
    public void idRefQuotesNamesNeedingEscaping() {
        assertEquals("\"s[0]\"", PlantUmlSequenceDiagram.idRef("s[0]"));
        assertEquals("\"getX()\"", PlantUmlSequenceDiagram.idRef("getX()"));
        assertEquals("\"Outer$Inner\"", PlantUmlSequenceDiagram.idRef("Outer$Inner"));
        assertEquals("\"a b\"", PlantUmlSequenceDiagram.idRef("a b"));
        assertEquals("\"List<String>\"", PlantUmlSequenceDiagram.idRef("List<String>"));
    }

    @Test
    public void escapeLabelCollapsesNewlines() {
        assertEquals("a b c", PlantUmlCommentFormatter.escapeLabel("a\nb\r\nc"));
        assertEquals("foo bar", PlantUmlCommentFormatter.escapeLabel("foo    bar"));
    }

    @Test
    public void escapeLabelEscapesHtmlSpecials() {
        // ラベル中の < > & は PlantUML が creole/HTML タグとして解釈するため
        // HTML エンティティへ変換する (例: filter(x > 0) / foo(List<String>))。
        assertEquals("filter(x &gt; 0)",
                PlantUmlCommentFormatter.escapeLabel("filter(x > 0)"));
        assertEquals("foo(List&lt;String&gt;)",
                PlantUmlCommentFormatter.escapeLabel("foo(List<String>)"));
        // & は最初に変換し、二重エスケープしないこと
        assertEquals("a &amp; b", PlantUmlCommentFormatter.escapeLabel("a & b"));
    }

    /**
     * 配列添字レシーバ ({@code s[0].go()}) は participant 名が {@code s[0]} になる。修正前は
     * {@code participant "s[0]"} と宣言しつつ矢印は {@code A -> s[0]} と未引用で出力し、PlantUML が
     * 構文エラー画像を返していた (origin/main で再現確認済み)。引用符が一致することを保証する。
     */
    @Test
    public void arrayIndexReceiverIsQuotedConsistently() throws IOException {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { Svc[] s; void run() { s[0].go(); } }");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue("arrow must quote the bracketed participant:\n" + puml,
                puml.contains("-> \"s[0]\""));
        assertFalse("arrow must not reference the bracketed name unquoted:\n" + puml,
                puml.contains("-> s[0]"));
        assertNoPlantUmlSyntaxError(puml);
    }

    @Test
    public void inlineCallbackDiagramRendersWithoutSyntaxError() throws IOException {
        // ラムダ/匿名クラスのコールバックは participant 名に "$" を含む ("A$run" など)。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "import java.util.List;\n"
                + "class A {\n"
                + "  List<String> items;\n"
                + "  void run() { items.forEach(s -> handle(s)); }\n"
                + "  void handle(String s) {}\n"
                + "}\n");
        String puml = PlantUmlSequenceDiagram.generate(infos, "A", "run", null);
        assertTrue("expected an inline ($) participant in: " + puml, puml.contains("$"));
        assertNoPlantUmlSyntaxError(puml);
    }

    @Test
    public void emptyDiagramRendersWithoutSyntaxError() throws IOException {
        String puml = PlantUmlSequenceDiagram.generate(
                JavaStructureExtractor.extract("class A {}"), "A", "missing", null);
        assertNoPlantUmlSyntaxError(puml);
    }

    /** 生成 PlantUML を実レンダリングし、PlantUML の構文エラー画像にならないことを確認する。 */
    private static void assertNoPlantUmlSyntaxError(String puml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML reported a syntax error for:\n" + puml,
                svg.contains("Syntax Error"));
    }
}
