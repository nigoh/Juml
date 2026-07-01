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
        // title に < > & が含まれる場合、PlantUML が HTML タグと誤認しないよう
        // &lt; &gt; &amp; に変換されること。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo {}");
        PlantUmlPackageDiagram.Options opts = new PlantUmlPackageDiagram.Options();
        opts.title = "A<B>&C";
        opts.includeLegend = false;
        String puml = PlantUmlPackageDiagram.generate(infos, opts);
        assertTrue("escaped title expected: " + puml,
                puml.contains("title A&lt;B&gt;&amp;C"));
        assertFalse("raw < must not appear in title: " + puml,
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
}
