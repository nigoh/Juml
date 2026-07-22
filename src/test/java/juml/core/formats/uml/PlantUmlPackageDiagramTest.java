// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlPackageDiagram のユニットテスト。
 */
public class PlantUmlPackageDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        PlantUmlPackageDiagram.generate(null);
    }

    @Test
    public void testEmptyClasses() {
        String puml = PlantUmlPackageDiagram.generate(java.util.Collections.emptyList());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testSinglePackageEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.x; class Foo {} class Bar {}");
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue(puml, puml.contains("package \"com.x"));
        assertTrue(puml, puml.contains("2 classes"));
    }

    @Test
    public void testDependencyArrowFromFieldType() {
        // Foo (com.a) が Bar (com.b) を保持 → com.a → com.b の矢印
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class Foo { com.b.Bar bar; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class Bar {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);
        // com.a と com.b のパッケージノードが出ること
        assertTrue(puml, puml.contains("package \"com.a"));
        assertTrue(puml, puml.contains("package \"com.b"));
        // パッケージ間の矢印 (--> ) が 1 本以上含まれること
        // 具体的なエイリアスは生成順に依存するので、--> の存在のみ検証
        assertTrue(puml, puml.contains(" --> "));
    }

    @Test
    public void testCyclicDependencyRenderedAsBidirectionalArrow() {
        // com.a と com.b が相互参照する循環依存は、双方向矢印 <--> 1 本にまとまる
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class A { com.b.B b; }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class B { com.a.A a; }"));
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue("cycle should use a bidirectional arrow:\n" + puml,
                puml.contains(" <--> "));
        // 一方向矢印は出さない (二重描画しない)
        assertFalse("cycle must not also emit one-way arrows:\n" + puml,
                puml.contains(" --> "));
    }

    @Test
    public void testDependencyArrowFromInheritance() {
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class Foo extends com.b.Bar {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class Bar {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue(puml, puml.contains(" --> "));
    }

    @Test
    public void testSelfLoopSuppressed() {
        // 同一パッケージ内のクラス参照は自己ループとして抑止される
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.a; class Foo { Bar b; } class Bar {}");
        String puml = PlantUmlPackageDiagram.generate(infos);
        // 同一パッケージのみで矢印が出ないこと
        assertFalse("self loop should be suppressed", puml.contains(" --> "));
    }

    @Test
    public void testUnknownTypeIgnored() {
        // 既知のクラス集合に含まれない型 (java.util.List 等) は無視される
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.a; import java.util.List; class Foo { List<String> xs; }");
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertFalse("external library types should not produce arrows",
                puml.contains(" --> "));
    }

    @Test
    public void testIncludeLegend() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.includeLegend = true;
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertTrue(puml, puml.contains("legend top left"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testSuppressLegend() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertFalse(puml, puml.contains("legend top left"));
    }

    @Test
    public void testTitle() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.title = "My Project";
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertTrue(puml, puml.contains("title My Project"));
    }

    @Test
    public void testDefaultPackageLabel() {
        // パッケージ宣言なし → "(default)" ラベルで出力
        List<JavaClassInfo> infos = JavaStructureExtractor.extract("class Foo {}");
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue(puml, puml.contains("(default)"));
    }

    // ---- [High] title エスケープ修正 テスト ----

    @Test
    public void testTitleWithSpecialCharsEscaped() {
        // title に < が含まれる場合、PlantUML がタグと誤認しないよう
        // ~< にチルダエスケープされること (> & は生のままで安全)。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.title = "A<B>&C";
        opts.includeLegend = false;
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertTrue("escaped title expected: " + puml,
                puml.contains("title A~<B>&C"));
        assertFalse("unescaped < must not appear in title: " + puml,
                puml.contains("title A<B>"));
    }

    // ---- [Medium] 依存エッジ: メソッドの戻り値型・引数型 テスト ----

    @Test
    public void testDependencyArrowFromMethodReturnType() {
        // メソッドの戻り値型が別パッケージのクラスを参照する場合にも依存矢印が出ること。
        // フィールドを持たずメソッドシグネチャだけで型を使うクラスのケース。
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class Foo { com.b.Bar getBar() { return null; } }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class Bar {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue("return type ref should produce arrow:\n" + puml,
                puml.contains(" --> "));
    }

    @Test
    public void testDependencyArrowFromMethodParamType() {
        // メソッドの引数型が別パッケージのクラスを参照する場合にも依存矢印が出ること。
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract(
                "package com.a; class Foo { void setBar(com.b.Bar b) {} }"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.b; class Bar {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);
        assertTrue("param type ref should produce arrow:\n" + puml,
                puml.contains(" --> "));
    }

    // ---- 回帰: 参照元クラスの import を単純名解決より優先する (resolvePackage) ----

    @Test
    public void testImportPrioritizedOverAmbiguousSimpleName() {
        // com.a.Foo と com.b.Foo が同じ単純名 "Foo" を持つ。修正前は resolvePackage が
        // 走査順で最初に登録されたパッケージ (pkgBySimple.putIfAbsent 由来、ここでは
        // com.a) を常に選んでしまい、com.c.Bar が明示的に import com.b.Foo していても
        // 誤って com.c -> com.a のエッジになっていた。修正後は参照元クラスの import
        // マップを優先し、正しく com.c -> com.b になる。
        //
        // この検証は superclass (extends) 参照。extends/implements は addRef に単純名の
        // まま渡り resolvePackage の import 優先が直接効く。フィールド/戻り値/引数型は
        // pickUsageTarget が KnownTypeIndex#suffixMatch で先に FQN 化するため、別途
        // usageTargetWithImport が import を再優先する
        // (testImportPrioritizedForFieldAndMethodTypes で検証)。
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract("package com.a; class Foo {}"));
        infos.addAll(JavaStructureExtractor.extract("package com.b; class Foo {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.c; import com.b.Foo; class Bar extends Foo {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);

        String aliasA = packageAlias(puml, "com.a");
        String aliasB = packageAlias(puml, "com.b");
        String aliasC = packageAlias(puml, "com.c");
        assertNotNull("com.a のパッケージノードが見つかるべき:\n" + puml, aliasA);
        assertNotNull("com.b のパッケージノードが見つかるべき:\n" + puml, aliasB);
        assertNotNull("com.c のパッケージノードが見つかるべき:\n" + puml, aliasC);

        assertTrue("import で指定した com.b への依存エッジが出るべき:\n" + puml,
                puml.contains(aliasC + " --> " + aliasB));
        assertFalse("import 先ではない com.a への誤ったエッジが出てはいけない:\n" + puml,
                puml.contains(aliasC + " --> " + aliasA));
    }

    @Test
    public void testImportPrioritizedForFieldAndMethodTypes() {
        // フィールド/戻り値/引数の型でも import を優先する回帰。pickUsageTarget は
        // KnownTypeIndex#suffixMatch で曖昧な単純名 "Foo" を辞書順最小 (com.a.Foo) へ
        // 確定させてしまうため、com.z.Foo を import していても修正前は com.c -> com.a に
        // なっていた。usageTargetWithImport が解決結果の単純名を import マップと再照合し、
        // 明示 import 先が既知クラスならそちらを優先する。
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract("package com.a; class Foo {}"));
        infos.addAll(JavaStructureExtractor.extract("package com.z; class Foo {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.c; import com.z.Foo; class Bar {"
                        + " private Foo f; Foo make() { return null; } void take(Foo x) {} }"));
        String puml = PlantUmlPackageDiagram.generate(infos);

        String aliasA = packageAlias(puml, "com.a");
        String aliasZ = packageAlias(puml, "com.z");
        String aliasC = packageAlias(puml, "com.c");
        assertNotNull(aliasA);
        assertNotNull(aliasZ);
        assertNotNull(aliasC);
        assertTrue("フィールド/シグネチャ型でも import 先 com.z への依存が出るべき:\n" + puml,
                puml.contains(aliasC + " --> " + aliasZ));
        assertFalse("辞書順最小 com.a への誤ったエッジが出てはいけない:\n" + puml,
                puml.contains(aliasC + " --> " + aliasA));
    }

    @Test
    public void testFullyQualifiedFieldTypeNotOverriddenByImport() {
        // Round 2 回帰: フィールド型が完全修飾 (com.a.Foo f) で書かれている場合、Java では
        // FQN 参照が import より優先され com.a.Foo を指す。usageTargetWithImport が単純名だけを
        // 見て import 先 (com.z.Foo) へ張り替えると誤結線になる。明示 FQN は上書きしない
        // (resolvePackage の FQN-first と一貫)。
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract("package com.a; class Foo {}"));
        infos.addAll(JavaStructureExtractor.extract("package com.z; class Foo {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.c; import com.z.Foo; class Bar { com.a.Foo f; }"));
        String puml = PlantUmlPackageDiagram.generate(infos);

        String aliasA = packageAlias(puml, "com.a");
        String aliasZ = packageAlias(puml, "com.z");
        String aliasC = packageAlias(puml, "com.c");
        assertNotNull(aliasA);
        assertNotNull(aliasZ);
        assertNotNull(aliasC);
        assertTrue("明示 FQN の com.a への依存が出るべき:\n" + puml,
                puml.contains(aliasC + " --> " + aliasA));
        assertFalse("import 先 com.z への誤ったエッジが出てはいけない:\n" + puml,
                puml.contains(aliasC + " --> " + aliasZ));
    }

    @Test
    public void testImportPointingToUnknownClassFallsBackToSimpleNameHeuristic() {
        // import 先が既知クラス一覧に無い場合 (未解析の依存先など) は、
        // 例外を投げずに従来の単純名ヒューリスティックへフォールバックすること
        // (resolvePackage の importedPkg == null → 継続分岐の回帰)。
        List<JavaClassInfo> infos = new java.util.ArrayList<>();
        infos.addAll(JavaStructureExtractor.extract("package com.a; class Foo {}"));
        infos.addAll(JavaStructureExtractor.extract(
                "package com.c; import com.zzz.Foo; class Bar extends Foo {}"));
        String puml = PlantUmlPackageDiagram.generate(infos);

        String aliasA = packageAlias(puml, "com.a");
        String aliasC = packageAlias(puml, "com.c");
        assertNotNull(aliasA);
        assertNotNull(aliasC);
        // import 先 (com.zzz.Foo) は既知クラスに無いため、単純名一致の com.a へ
        // フォールバックする (クラッシュせず既存挙動を維持)。
        assertTrue("フォールバックで com.a への依存エッジが出るべき:\n" + puml,
                puml.contains(aliasC + " --> " + aliasA));
    }

    /**
     * {@code package "<pkgName>\n<n> class(es)" as <alias> {} } 宣言からエイリアスを取り出す。
     * ラベル内の改行は PlantUML クオート内表記のため、生成コードと同じ 2 文字の
     * リテラル {@code \n} (バックスラッシュ + n) がそのまま出力される点に注意。
     */
    private static String packageAlias(String puml, String pkgName) {
        String marker = "package \"" + pkgName + "\\n";
        int idx = puml.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int asIdx = puml.indexOf(" as ", idx);
        if (asIdx < 0) {
            return null;
        }
        int start = asIdx + 4;
        int end = start;
        while (end < puml.length() && Character.isLetterOrDigit(puml.charAt(end))) {
            end++;
        }
        return puml.substring(start, end);
    }
}
