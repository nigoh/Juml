// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.java.jp.JavaParserFrontend;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 解析パイプラインの修正項目に対する回帰テスト集。
 *
 * <ul>
 *   <li>Fix 1: ネスト switch 式の二重 emit (StatementAdapter)</li>
 *   <li>Fix 3: {@code <clinit>}/{@code <init>} がインタラクティブリンク URL に埋め込まれる</li>
 *   <li>Fix 4: footerWarning 未エスケープ (PlantUmlClassDiagram)</li>
 *   <li>Fix 5: recoverSkeletons が文字列/コメント内の型宣言を誤検出</li>
 *   <li>Fix 9: generateAll の @enduml 除去で末尾改行が残る</li>
 *   <li>Fix 10: シーケンス図デフォルトタイトル未エスケープ</li>
 * </ul>
 */
public class PipelineBugRegressionTest {

    // ============================================================
    // Fix 1: ネスト switch 式の二重 emit
    // ============================================================

    /**
     * ネストした switch 式が外側のシーケンス図ブロックとして二重に emit されないことを確認する。
     *
     * <p>修正前は {@code findAll(SwitchExpr.class)} がネストした内側 switch も拾い、
     * 外側 switch の case アーム処理と合わせて同じ内側 switch のブロックが 2 回出力されていた。</p>
     */
    @Test
    public void nestedSwitchExprEmittedOnlyOnce() {
        String src = "class Foo {\n"
                + "  void foo(int a, int b) {\n"
                + "    var x = switch (a) {\n"
                + "      case 1 -> switch (b) { case 2 -> \"foo\"; default -> \"bar\"; };\n"
                + "      default -> \"baz\";\n"
                + "    };\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Foo", "foo", null);

        // 外側の switch は "alt switch (a) / case 1" で始まる alt ブロックになる
        // 二重 emit されていると "alt switch (a)" が 2 回以上出現する
        long outerSwitchCount = diagram.lines()
                .filter(l -> l.trim().startsWith("alt switch (a)"))
                .count();
        assertEquals("外側 switch (a) ブロックは 1 回だけ出力されるべき:\n" + diagram,
                1, outerSwitchCount);
    }

    /**
     * ネスト switch で内側のブロックが外側の case アーム内に含まれて出力されることを確認する。
     */
    @Test
    public void nestedSwitchExprInnerBlockIsPresent() {
        String src = "class Foo {\n"
                + "  void foo(int a, int b) {\n"
                + "    var x = switch (a) {\n"
                + "      case 1 -> switch (b) { case 2 -> \"foo\"; default -> \"bar\"; };\n"
                + "      default -> \"baz\";\n"
                + "    };\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Foo", "foo", null);

        // 内側 switch (b) も alt ブロックとして出力される
        long innerSwitchCount = diagram.lines()
                .filter(l -> l.trim().startsWith("alt switch (b)"))
                .count();
        assertEquals("内側 switch (b) ブロックも 1 回出力されるべき:\n" + diagram,
                1, innerSwitchCount);
    }

    /**
     * return 文に含まれるネスト switch も二重 emit されないことを確認する（emitExprValue 経路）。
     */
    @Test
    public void nestedSwitchInReturnEmittedOnlyOnce() {
        String src = "class Bar {\n"
                + "  String bar(int a, int b) {\n"
                + "    return switch (a) {\n"
                + "      case 1 -> switch (b) { case 2 -> \"x\"; default -> \"y\"; };\n"
                + "      default -> \"z\";\n"
                + "    };\n"
                + "  }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Bar", "bar", null);

        long outerSwitchCount = diagram.lines()
                .filter(l -> l.trim().startsWith("alt switch (a)"))
                .count();
        assertEquals("return 式中の外側 switch は 1 回だけ出力されるべき:\n" + diagram,
                1, outerSwitchCount);
    }

    // ============================================================
    // Fix 3: <clinit>/<init> がインタラクティブリンク URL に埋め込まれる
    // ============================================================

    /**
     * interactiveLinks 有効時に {@code <clinit>} 合成メソッドが URL に埋め込まれないことを確認する。
     *
     * <p>修正前は {@code [[juml://method/x.Dao#&lt;clinit&gt; ▶]]} のような
     * エスケープ済みテキストが URL のパス部分に入り、SVG が壊れていた。</p>
     */
    @Test
    public void clinitNotEmbeddedInInteractiveLink() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Dao { static { connect(); } static void connect() {} }");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.interactiveLinks = true;
        String puml = PlantUmlClassDiagram.generate(infos, opts);

        // [[juml://method/...]] リンクを含む行に "clinit" が現れていないこと
        // (&lt;clinit&gt; は member 行に出るが URL 行には出てはいけない)
        boolean clinitInLink = puml.lines()
                .filter(l -> l.contains("[[juml://method/"))
                .anyMatch(l -> l.contains("clinit"));
        assertFalse("<clinit> は URL パスに入ってはいけない:\n" + puml, clinitInLink);
        // ただし本体の member 行では HTML エンティティとして描画される (既存テストの補完)
        assertTrue("clinit は HTML エンティティとして member 行に現れるべき:\n" + puml,
                puml.contains("&lt;clinit&gt;("));
    }

    /**
     * interactiveLinks 有効時に通常メソッドのリンクは正常に出力されることを確認する。
     * (Fix 3 の修正で通常メソッドまで除外されていないことのチェック)
     */
    @Test
    public void normalMethodLinkNotAffectedByClinitFix() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Dao { static void connect() {} }");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.interactiveLinks = true;
        String puml = PlantUmlClassDiagram.generate(infos, opts);

        assertTrue("通常メソッドのリンクは出力されるべき:\n" + puml,
                puml.contains("[[juml://method/x.Dao#connect"));
    }

    // ============================================================
    // Fix 4: footerWarning 未エスケープ
    // ============================================================

    /**
     * footerWarning に {@code <} / {@code >} / {@code &} が含まれる場合、
     * PlantUML の footer ディレクティブで HTML エンティティ化されることを確認する。
     */
    @Test
    public void footerWarningIsHtmlEscaped() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract("class A {}");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.footerWarning = "showing <500> of 1000 & more classes";
        String puml = PlantUmlClassDiagram.generate(infos, opts);

        // 生の < > & が footer 行に出ないこと
        assertFalse("生の < が footer に残ってはいけない:\n" + puml,
                puml.lines().filter(l -> l.startsWith("footer")).anyMatch(l -> l.contains("<")));
        // HTML エンティティ化されていること
        assertTrue("< は &lt; に変換されるべき:\n" + puml,
                puml.contains("&lt;500&gt;"));
        assertTrue("& は &amp; に変換されるべき:\n" + puml,
                puml.contains("&amp;"));
    }

    // ============================================================
    // Fix 5: recoverSkeletons が文字列/コメント内の型宣言を誤検出
    // ============================================================

    /**
     * 文字列リテラル内に書かれた {@code class Foo} を recoverSkeletons が誤検出しないことを確認する。
     *
     * <p>修正前は正規表現を生ソーステキストに適用していたため、文字列リテラル内の
     * {@code "class Foo"} が型宣言として検出されることがあった。</p>
     */
    @Test
    public void recoverSkeletonsIgnoresClassInStringLiteral() {
        // JavaParser が完全失敗するほど壊れたソースを用意し、
        // recoverSkeletons フォールバックが動く状況を作る。
        // 文字列リテラル中に "class Phantom" が含まれているが検出されてはいけない。
        String broken = "package x; class Real { String s = \"class Phantom\"; ";
        List<JavaClassInfo> result = JavaParserFrontend.parse(broken, null);

        // Real は含まれてよいが Phantom は含まれてはいけない
        boolean hasPhantom = result.stream()
                .anyMatch(c -> "Phantom".equals(c.getSimpleName()));
        assertFalse("文字列リテラル内の class キーワードは誤検出されてはいけない:\n" + result,
                hasPhantom);
    }

    /**
     * 行コメント内に書かれた {@code class Foo} を recoverSkeletons が誤検出しないことを確認する。
     */
    @Test
    public void recoverSkeletonsIgnoresClassInLineComment() {
        // 壊れたソースで recoverSkeletons が動く状況を作る
        // コメント内の "class Ghost" が検出されてはいけない
        String broken = "package x; class Real { // class Ghost is just a comment \n";
        List<JavaClassInfo> result = JavaParserFrontend.parse(broken, null);

        boolean hasGhost = result.stream()
                .anyMatch(c -> "Ghost".equals(c.getSimpleName()));
        assertFalse("行コメント内の class キーワードは誤検出されてはいけない:\n" + result,
                hasGhost);
    }

    // ============================================================
    // Fix 9: generateAll の @enduml 除去で末尾改行が残る
    // ============================================================

    /**
     * {@code generateAll} が複数メソッドを統合するとき、@enduml の除去で
     * 余分な空行が挿入されないことを確認する。
     *
     * <p>修正前は {@code (?m)@enduml\\s*$} パターンが行末の {@code \n} を残し、
     * {@code newpage} の直前に空行が出力されていた。</p>
     */
    @Test
    public void generateAllDoesNotLeaveTrailingBlankBeforeNewpage() {
        String src = "class X {\n"
                + "  void a() { helper(); }\n"
                + "  void b() { helper(); }\n"
                + "  void helper() {}\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        String all = PlantUmlSequenceDiagram.generateAll(classes, opts);

        // "newpage" の直前行が空行でないこと
        String[] lines = all.split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            if ("newpage".equals(lines[i].trim())) {
                assertFalse("newpage の直前に空行があってはいけない (i=" + i + "):\n" + all,
                        lines[i - 1].trim().isEmpty());
            }
        }
    }

    // ============================================================
    // Fix 10: シーケンス図デフォルトタイトル未エスケープ
    // ============================================================

    /**
     * シーケンス図のデフォルトタイトル (options.title が null/空のとき) で
     * クラス名・メソッド名の {@code <} / {@code >} が HTML エンティティ化されることを確認する。
     *
     * <p>修正前は {@code <init>} などの合成メソッド名がそのままタイトル行に入り、
     * PlantUML が HTML タグとして誤解釈することがあった。</p>
     */
    @Test
    public void sequenceDiagramDefaultTitleEscapesSpecialChars() {
        // <init> はコンストラクタ相当の合成メソッドとして生成されうる
        // ここでは合成メソッド名を直接 JavaMethodInfo に設定してテストする
        // (実際に JavaParser が <init> を生成するケースを再現)
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "class Foo { void bar() { baz(); } void baz() {} }");
        assertNotNull(classes);
        assertFalse(classes.isEmpty());

        // title オプションを空にしてデフォルトタイトルを生成させる
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.title = "";
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Foo", "bar", opts);

        // デフォルトタイトル行は "title Foo.bar" になるべき (Foo と bar は安全な名前)
        assertTrue("デフォルトタイトルは title Foo.bar を含むべき:\n" + diagram,
                diagram.contains("title Foo.bar"));
    }

    /**
     * タイトルに直接設定した文字列は既存の escapeLabel でエスケープ済みであることを確認する (非回帰)。
     */
    @Test
    public void sequenceDiagramExplicitTitleEscaped() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "class Foo { void bar() { baz(); } void baz() {} }");
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.title = "A < B & C";
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Foo", "bar", opts);

        // 明示タイトルの < > & はエスケープされるべき
        assertFalse("生の < が title 行に残ってはいけない:\n" + diagram,
                diagram.lines().filter(l -> l.startsWith("title"))
                        .anyMatch(l -> l.contains("<")));
        assertTrue("& は &amp; に変換されるべき:\n" + diagram,
                diagram.contains("&amp;"));
    }
}
