// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * PlantUmlSequenceDiagram の InlineComment 出力とオーバーロード解決に関するユニットテスト。
 *
 * <p>{@link PlantUmlSequenceDiagramTest} から分離: FileLength checkstyle 制約を
 * 超えないようにするため、比較的独立したテスト群をここに切り出している。</p>
 */
public class PlantUmlSequenceDiagramInlineCommentAndOverloadTest {

    @Test
    public void testWalkStatementsEmitsInlineCommentAsNote() {
        // メソッド本体内のインラインコメントが walkStatements 経由で
        // note over として出力されることを確認する。
        // JavaStructureExtractor がメソッド本体のインラインコメントを
        // InlineComment Statement として格納するかを検証する。
        List<JavaMethodInfo.Statement> stmts = new java.util.ArrayList<>();
        stmts.add(new JavaMethodInfo.InlineComment("処理開始"));
        stmts.add(new JavaMethodInfo.Call(null, "helper"));

        // JavaClassInfo を手動構築
        JavaClassInfo cls = new JavaClassInfo();
        cls.setPackageName("pkg");
        cls.setSimpleName("Svc");
        cls.setKind(JavaClassInfo.Kind.CLASS);
        cls.setDetailed(true);
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("run");
        m.setReturnType("void");
        m.getStatements().addAll(stmts);
        cls.getMethods().add(m);

        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(
                java.util.Collections.singletonList(cls), "Svc", "run", opts);
        // InlineComment が note over として出力されること
        assertTrue("InlineComment should appear as note over: " + puml,
                puml.contains("note over") && puml.contains("処理開始"));
    }

    @Test
    public void testWalkStatementsInlineCommentSpecialCharsEscaped() {
        // インラインコメントの < > & がエスケープされて note として安全に出力されること。
        List<JavaMethodInfo.Statement> stmts = new java.util.ArrayList<>();
        stmts.add(new JavaMethodInfo.InlineComment("型: List<String> & Map<K,V>"));

        JavaClassInfo cls = new JavaClassInfo();
        cls.setPackageName("pkg");
        cls.setSimpleName("Svc");
        cls.setKind(JavaClassInfo.Kind.CLASS);
        cls.setDetailed(true);
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("run");
        m.setReturnType("void");
        m.getStatements().addAll(stmts);
        cls.getMethods().add(m);

        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(
                java.util.Collections.singletonList(cls), "Svc", "run", opts);
        // エスケープ済みテキストが note に含まれること
        assertTrue("angle brackets must be escaped: " + puml,
                puml.contains("~<String>"));
        // エスケープされていない < が PlantUML テキストに残っていないこと
        assertFalse("unescaped < must not appear in note: " + puml,
                puml.contains("List<String>"));
    }

    // ----------------------------------------------------------------
    // オーバーロード解決テスト (修正 #1: findMethod の名前のみ検索では後続メソッドが選ばれない問題)
    // ----------------------------------------------------------------

    @Test
    public void testGenerateAllPicksCorrectOverloadedMethods() {
        // 同名だが引数の異なる 2 つのメソッドを持つクラス。
        // process(String) は dep.strOp() を呼び出し、
        // process(int)    は dep.intOp() を呼び出す。
        // generateAll が JavaMethodInfo を直接渡す修正により、
        // 両方のメソッドに対して正しいボディからシーケンス図が生成されることを確認する。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Handler {"
                        + " Dep dep;"
                        + " void process(String s) { dep.strOp(); }"
                        + " void process(int n)    { dep.intOp(); }"
                        + "}"
                        + "class Dep {"
                        + " void strOp() {}"
                        + " void intOp() {}"
                        + "}");
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        String allDiagram = PlantUmlSequenceDiagram.generateAll(infos, opts);
        // generateAll が両方のメソッドのボディを正しく反映していること:
        // strOp と intOp の呼び出しが両方含まれるはず
        assertTrue("strOp() の呼び出しが含まれるべき:\n" + allDiagram,
                allDiagram.contains("strOp()"));
        assertTrue("intOp() の呼び出しが含まれるべき:\n" + allDiagram,
                allDiagram.contains("intOp()"));
    }

    @Test
    public void testGenerateWithMethodInfoDirectly() {
        // generate(List, JavaClassInfo, JavaMethodInfo, Options) オーバーロードの直接テスト。
        // 後続定義のオーバーロードメソッドを JavaMethodInfo オブジェクトで指定して
        // 正しいボディが選ばれることを確認する。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Svc {"
                        + " Helper h;"
                        + " void exec(String s) { h.alpha(); }"
                        + " void exec(int n)    { h.beta(); }"
                        + "}"
                        + "class Helper { void alpha() {} void beta() {} }");
        JavaClassInfo svc = infos.stream()
                .filter(c -> "Svc".equals(c.getSimpleName()))
                .findFirst().orElseThrow(() -> new AssertionError("Svc not found"));
        // 2 つ目のオーバーロード (exec(int)) を直接取得して図を生成する
        JavaMethodInfo execInt = svc.getMethods().stream()
                .filter(m -> "exec".equals(m.getName())
                        && !m.getParameterTypes().isEmpty()
                        && m.getParameterTypes().get(0).equals("int"))
                .findFirst().orElseThrow(() -> new AssertionError("exec(int) not found"));
        PlantUmlSequenceDiagram.Options opts = new PlantUmlSequenceDiagram.Options();
        opts.includeLegend = false;
        String puml = PlantUmlSequenceDiagram.generate(infos, svc, execInt, opts);
        // exec(int) のボディ (h.beta()) が含まれること
        assertTrue("beta() が含まれるべき:\n" + puml, puml.contains("beta()"));
        // exec(String) のボディ (h.alpha()) は含まれないこと
        assertFalse("alpha() は exec(int) のボディではない:\n" + puml, puml.contains("alpha()"));
    }
}
