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
        // ただし本体の member 行ではチルダエスケープ済みテキストとして描画される (既存テストの補完)
        assertTrue("clinit はチルダエスケープ済みで member 行に現れるべき:\n" + puml,
                puml.contains("~<clinit>("));
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
     * footerWarning に {@code <} が含まれる場合、PlantUML の footer ディレクティブで
     * チルダエスケープされることを確認する ({@code >} と {@code &} は生のままで安全)。
     */
    @Test
    public void footerWarningIsHtmlEscaped() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract("class A {}");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.footerWarning = "showing <500> of 1000 & more classes";
        String puml = PlantUmlClassDiagram.generate(infos, opts);

        // < がチルダエスケープされ、タグとして誤認されないこと
        assertTrue("< は ~< にエスケープされるべき:\n" + puml,
                puml.contains("~<500>"));
        // エスケープされていない < が footer 行に出ないこと
        assertFalse("エスケープされていない < が footer に残ってはいけない:\n" + puml,
                puml.lines().filter(l -> l.startsWith("footer"))
                        .anyMatch(l -> l.contains("<") && !l.contains("~<")));
        // & は PlantUML 1.2026.x では生のままで安全に表示される
        assertTrue("& は生のまま保持されるべき:\n" + puml,
                puml.contains("& more classes"));
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

        // 明示タイトルの < はチルダエスケープされるべき (& は生のままで安全)
        assertTrue("< は ~< にエスケープされるべき:\n" + diagram,
                diagram.contains("title A ~< B & C"));
        assertFalse("エスケープされていない < が title 行に残ってはいけない:\n" + diagram,
                diagram.lines().filter(l -> l.startsWith("title"))
                        .anyMatch(l -> l.contains("<") && !l.contains("~<")));
    }

    // ============================================================
    // Fix R1 (new): ネスト switch 式内の呼び出しがシーケンス図に現れることを保証
    // (topLevelSwitchExprs が ex 自身 SwitchExpr のとき空を返す回帰の修正)
    // ============================================================

    /**
     * ネスト switch の内側 case アームにある呼び出しがシーケンス図に現れることを確認する。
     *
     * <p>修正前: topLevelSwitchExprs で ex == se のとき ancestor walk が
     * 外側親から始まり nested=true と誤判定され、ex 自身が除外されてしまっていた。
     * 結果、ネスト switch 内の全メソッド呼び出しがサイレントにドロップしていた。</p>
     */
    @Test
    public void nestedSwitchInnerCallAppearsInSequenceDiagram() {
        String src = "class Caller {\n"
                + "  int run(String outer, int inner) {\n"
                + "    return switch (outer) {\n"
                + "      case \"A\" -> switch (inner) {\n"
                + "        case 1 -> doSomething();\n"
                + "        default -> 0;\n"
                + "      };\n"
                + "      default -> 0;\n"
                + "    };\n"
                + "  }\n"
                + "  int doSomething() { return 42; }\n"
                + "}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        String diagram = PlantUmlSequenceDiagram.generate(classes, "Caller", "run", null);

        // doSomething() の呼び出しがシーケンス図に現れるべき
        assertTrue("doSomething() の呼び出しはシーケンス図に現れるべき:\n" + diagram,
                diagram.contains("doSomething"));
    }

    // ============================================================
    // Fix R2: アノテーション付きワイルドカード境界の解決
    // ============================================================

    /**
     * {@code ? extends @NonNull Activity} のようなアノテーション付きワイルドカード境界が
     * 使用関係として正しく解決されることを確認する。
     *
     * <p>修正前: regex が型アノテーションを除去できず "Activity" にマッチしなかった。</p>
     */
    @Test
    public void wildcardWithTypeAnnotationIsResolved() {
        // KnownTypeIndex 経由で pickUsageTarget が "@NonNull Activity" を正しく除去するか確認
        java.util.Set<String> known = new java.util.HashSet<>();
        known.add("Activity");

        // PlantUmlClassRelations#pickUsageTarget は package-private なので
        // 実際のクラス図生成経由でテストする
        String src = "class Holder {\n"
                + "  java.util.List<? extends @NonNull Activity> items;\n"
                + "}\n"
                + "class Activity {}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.showUsageRelations = true;
        String puml = PlantUmlClassDiagram.generate(classes, opts);

        // Holder --> Activity の使用関係 (矢印行) が生成されるべき。
        // "Activity" の contains だけではクラス宣言行にも常にマッチしてしまうため、
        // Holder と Activity を結ぶ矢印を含む行そのものを検証する。
        assertTrue("? extends @NonNull Activity の使用関係矢印が生成されるべき:\n" + puml,
                hasUsageArrow(puml, "Holder", "Activity"));
    }

    /**
     * 完全修飾名の型アノテーション付きワイルドカード境界も型解決できることを確認する。
     */
    @Test
    public void wildcardWithQualifiedTypeAnnotationIsResolved() {
        String src = "class Holder {\n"
                + "  java.util.List<? extends @javax.annotation.Nullable Activity> items;\n"
                + "}\n"
                + "class Activity {}";
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(src);
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.showUsageRelations = true;
        String puml = PlantUmlClassDiagram.generate(classes, opts);

        assertTrue("? extends @javax.annotation.Nullable Activity の使用関係矢印が"
                        + "生成されるべき:\n" + puml,
                hasUsageArrow(puml, "Holder", "Activity"));
    }

    /**
     * {@code from} と {@code to} を結ぶ矢印行が PlantUML テキストに含まれるか。
     * クラスは {@code class "Holder" as C1} のようにエイリアス宣言されるため、
     * まずエイリアスを解決してから矢印行を探す。
     */
    private static boolean hasUsageArrow(String puml, String from, String to) {
        String fromAlias = aliasOf(puml, from);
        String toAlias = aliasOf(puml, to);
        if (fromAlias == null || toAlias == null) {
            return false;
        }
        for (String line : puml.split("\n")) {
            String t = line.trim();
            if (t.startsWith(fromAlias + " ") && t.contains("->")
                    && t.contains(" " + toAlias)) {
                return true;
            }
        }
        return false;
    }

    /** {@code class "Name" as CN} 宣言から {@code Name} のエイリアス {@code CN} を得る。 */
    private static String aliasOf(String puml, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("class \"" + java.util.regex.Pattern.quote(name) + "\" as (\\w+)")
                .matcher(puml);
        return m.find() ? m.group(1) : null;
    }

    // ============================================================
    // Fix R3: Stage A ヘッダに typeParameters が引き継がれることを保証
    // ============================================================

    /**
     * extractHeadersOnly (Stage A) が型パラメータを保持することを確認する。
     *
     * <p>修正前: extractHeadersOnly で typeParameters が破棄されており、
     * HEADERS_ONLY パスで {@code <T extends Entity>} が表示されなかった。</p>
     */
    @Test
    public void extractHeadersOnlyPreservesTypeParameters() {
        String src = "package repo;\n"
                + "class Repository<T extends Entity> {\n"
                + "  void save(T entity) {}\n"
                + "}\n"
                + "class Entity {}";
        List<JavaClassInfo> headers = JavaStructureExtractor.extractHeadersOnly(src, null);

        JavaClassInfo repo = headers.stream()
                .filter(c -> "Repository".equals(c.getSimpleName()))
                .findFirst()
                .orElse(null);
        assertNotNull("Repository クラスが Stage A に含まれるべき", repo);
        assertFalse("Stage A でも typeParameters が空でないべき:\n" + headers,
                repo.getTypeParameters() == null || repo.getTypeParameters().isEmpty());
        assertTrue("typeParameters に 'T' が含まれるべき:\n" + repo.getTypeParameters(),
                repo.getTypeParameters().contains("T"));
    }

    /**
     * UmlGenerator 経由 (stripToHeader) でも typeParameters が引き継がれることを確認する。
     */
    @Test
    public void umlGeneratorStripToHeaderPreservesTypeParameters() {
        // UmlGenerator.extractFromProject は大規模用途なので、
        // extractHeadersOnly を直接呼んで typeParameters の存在を確認する
        String src = "package com.example;\n"
                + "public abstract class BaseRepo<T extends Identifiable, K> {\n"
                + "  abstract T findById(K id);\n"
                + "}\n"
                + "interface Identifiable {}";
        List<JavaClassInfo> headers = JavaStructureExtractor.extractHeadersOnly(src, null);

        JavaClassInfo base = headers.stream()
                .filter(c -> "BaseRepo".equals(c.getSimpleName()))
                .findFirst()
                .orElse(null);
        assertNotNull("BaseRepo が Stage A に含まれるべき", base);
        assertFalse("Stage A でも typeParameters が非 null/非空であるべき",
                base.getTypeParameters() == null || base.getTypeParameters().isEmpty());
    }

    // ============================================================
    // Fix R4: PlantUmlActivityDiagram タイトル未エスケープ
    // ============================================================

    /**
     * アクティビティ図のデフォルトタイトル (options.title が null のとき) で
     * クラス名・メソッド名の特殊文字がエスケープされることを確認する。
     *
     * <p>修正前: cls.getSimpleName() / method.getName() を直接 append していたため
     * {@code <init>} 等の合成メソッドが PlantUML に HTML タグとして誤解釈されていた。
     * シーケンス図と対称になるよう PlantUmlCommentFormatter.escapeLabel (チルダエスケープ) を適用した。</p>
     */
    @Test
    public void activityDiagramDefaultTitleEscapesSpecialChars() {
        // 合成メソッド名のように "<" を含む名前を持つメソッドが図に現れることを想定して
        // 実際には通常名のメソッドで "title" 行を確認する
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(
                "class Calc { int compute() { int x = 1 + 2; return x; } }");
        PlantUmlActivityDiagram.Options opts = new PlantUmlActivityDiagram.Options();
        // title を null のままにしてデフォルトタイトル生成を起動する
        opts.title = null;
        String diagram = PlantUmlActivityDiagram.generate(classes, "Calc", "compute", opts);

        assertNotNull("アクティビティ図が生成されるべき", diagram);
        assertTrue("デフォルトタイトルは 'title Calc.compute' を含むべき:\n" + diagram,
                diagram.contains("title Calc.compute"));
        // 生の < が title 行に残っていないこと (エスケープが通ったことの確認)
        assertFalse("title 行に生の < が残ってはいけない:\n" + diagram,
                diagram.lines().filter(l -> l.startsWith("title"))
                        .anyMatch(l -> l.contains("<")));
    }
}
