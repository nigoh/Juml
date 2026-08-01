// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * シーケンス図の note ブロックへ、解析対象ソースの JavaDoc から PlantUML 構文が
 * 注入されないことを検証する回帰テスト。
 *
 * <p>note 本文は {@code sanitizeNoteLine} で無害化する契約になっているが、
 * AT_CALL_SITE + NOTE の経路 ({@link SeqEmitters#emitNoteBlockAtCall}) だけ適用漏れが
 * あり、(1) {@code end note} だけの行で note が早期終端して以降が生の PlantUML として
 * 解釈され図全体が描画失敗する、(2) {@code !theme x} がプリプロセッサ命令として実際に
 * 実行される、という問題があった。文字列一致だけでなく実レンダリングまで確認する。</p>
 */
public class PlantUmlSequenceNoteInjectionTest {

    /** {@code sanitizeNoteLine} が無害化に使うゼロ幅スペース。 */
    private static final String ZWSP = "​";

    private static String generateCallSiteNote(String calleeJavadoc) {
        String src =
                "class A {\n"
              + "  B b;\n"
              + "  void run() { b.doIt(); }\n"
              + "}\n"
              + "class B {\n"
              + "  /** " + calleeJavadoc + " */\n"
              + "  void doIt() {}\n"
              + "}\n";
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(src);
        PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        return PlantUmlSequenceDiagram.generate(infos, "A", "run", o);
    }

    private static void assertRendersWithoutSyntaxError(String puml) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(puml, out);
        String svg = new String(out.toByteArray(), StandardCharsets.UTF_8);
        assertFalse("PlantUML が構文エラーを報告した:\n" + puml, svg.contains("Syntax Error"));
        assertTrue("SVG が生成されるはず", svg.contains("<svg"));
    }

    @Test
    public void callSiteNote_endNoteLineInJavadoc_isNeutralizedAndStillRenders()
            throws IOException {
        String puml = generateCallSiteNote(
                "呼ばれる側。\n   * end note\n   * これも本文のはず。");
        assertTrue("note ブロックが出ること:\n" + puml, puml.contains("note right of \"B\""));
        assertTrue("本文の end note が無害化されていない:\n" + puml,
                puml.contains(ZWSP + "end note"));
        assertTrue("無害化後も後続の本文が残ること:\n" + puml, puml.contains("これも本文のはず。"));
        assertRendersWithoutSyntaxError(puml);
    }

    @Test
    public void callSiteNote_preprocessorDirectiveInJavadoc_isNeutralized() throws IOException {
        // '!' 始まりの行は PlantUML のプリプロセッサ命令として実行され得るため無害化する
        // (解析対象ソースのコメントから任意のディレクティブを注入できてはいけない)。
        String puml = generateCallSiteNote("説明。\n   * !theme spacelab");
        assertTrue("!theme が無害化されていない:\n" + puml,
                puml.contains(ZWSP + "!theme spacelab"));
        assertRendersWithoutSyntaxError(puml);
    }

    @Test
    public void callSiteNote_plantUmlCommentInJavadoc_isNeutralized() throws IOException {
        // シングルクォート始まりは PlantUML の行コメントとして本文が消えてしまう。
        String puml = generateCallSiteNote("説明。\n   * 'これはコメントではなく本文");
        assertTrue("' 始まりの行が無害化されていない:\n" + puml,
                puml.contains(ZWSP + "'これはコメントではなく本文"));
        assertRendersWithoutSyntaxError(puml);
    }

    @Test
    public void callSiteNote_normalJavadoc_isUnchangedAndRenders() throws IOException {
        // 通常の JavaDoc には余計なゼロ幅スペースを入れない (無害化は該当行だけ)。
        String puml = generateCallSiteNote("doIt の処理\n   * 2 行目の説明");
        assertTrue(puml, puml.contains("doIt の処理"));
        assertTrue(puml, puml.contains("2 行目の説明"));
        assertFalse("通常行にゼロ幅スペースを混ぜないこと:\n" + puml, puml.contains(ZWSP));
        assertRendersWithoutSyntaxError(puml);
    }
}
