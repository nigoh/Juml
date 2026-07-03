// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlCallGraphDiagram} の WBS ノードエスケープ処理を検証する。
 *
 * <p>クラス名・メソッド名に含まれる {@code <} は creole/HTML タグと誤認されるため、
 * {@code ~<} へエスケープされることを確認する。また通常の呼び出しグラフ生成
 * (ノード列挙) が正常に動作することも合わせて検証する。</p>
 */
public class PlantUmlCallGraphDiagramEscapeTest {

    @Test
    public void testClassNotFoundOutputContainsSentinel() {
        // クラスが見つからないときに "[Class not found] " + クラス名が出力されることを確認する。
        String puml = PlantUmlCallGraphDiagram.generate(
                Collections.emptyList(), "UnknownClass", "run", null);
        assertTrue("output should contain class-not-found sentinel: " + puml,
                puml.contains("[Class not found]"));
        assertTrue("output should contain the class name: " + puml,
                puml.contains("UnknownClass"));
        assertTrue("output should be valid WBS syntax: " + puml,
                puml.contains("@startwbs") && puml.contains("@endwbs"));
    }

    @Test
    public void testAngleBracketInClassNameEscapedWithTilde() {
        // entryClass に "<b>" を含む名前を渡すと、"~<b>" にエスケープされて出力されることを確認する。
        // PlantUmlCommentFormatter.escapeText() が "<" を "~<" に変換することの結合テスト。
        String puml = PlantUmlCallGraphDiagram.generate(
                Collections.emptyList(), "Foo<b>", "run", null);
        // クラスが見つからないので [Class not found] 経路を通る
        assertTrue("angle bracket in class name should be escaped with tilde: " + puml,
                puml.contains("~<b>"));
        // 生の "<b>" がタグとして残ったままではないことを確認する
        assertFalse("raw <b> should not appear unescaped: " + puml,
                puml.replace("~<", "").contains("<b>"));
    }

    @Test
    public void testNormalFlowHelperMethodAppearsInWbs() {
        // 通常の generate() フロー: Foo.run() が helper() を呼ぶとき、
        // WBS ノードに "helper()" が含まれることを確認する。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Foo { void run() { helper(); } void helper() {} }");
        String puml = PlantUmlCallGraphDiagram.generate(infos, "Foo", "run", null);
        assertTrue("output should be WBS format: " + puml,
                puml.contains("@startwbs") && puml.contains("@endwbs"));
        assertTrue("helper() call should appear as WBS node: " + puml,
                puml.contains("helper()"));
        // 起点ノードも含まれること
        assertTrue("entry method should appear in root node: " + puml,
                puml.contains("Foo.run()"));
    }

    @Test
    public void testMethodNotFoundOutputContainsSentinel() {
        // クラスが存在するがメソッドが見つからないとき "[Method not found] " が出力されることを確認する。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Bar { void existing() {} }");
        String puml = PlantUmlCallGraphDiagram.generate(infos, "Bar", "noSuchMethod", null);
        assertTrue("output should contain method-not-found sentinel: " + puml,
                puml.contains("[Method not found]"));
    }

    @Test
    public void testCallInsideLocalVarLambdaAppearsInWbs() {
        // ローカル変数初期化子のラムダ本体内の呼び出し (svc.work()) も WBS に現れること。
        // 以前は collectCalls が LocalVar を辿らず、この呼び出しを取りこぼしていた。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Svc { void work() {} }\n"
                        + "class A {\n"
                        + "  Svc svc = new Svc();\n"
                        + "  void run() { Runnable r = () -> svc.work(); r.run(); }\n"
                        + "}");
        String puml = PlantUmlCallGraphDiagram.generate(infos, "A", "run", null);
        assertTrue("ラムダ本体内の work() が WBS に現れるべき: " + puml,
                puml.contains("work()"));
    }
}
