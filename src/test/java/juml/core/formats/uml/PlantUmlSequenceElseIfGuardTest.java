// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * {@code else if} の条件式に含まれる呼び出しが、正しい分岐の中に描かれることの回帰テスト。
 *
 * <p>以前は条件式の呼び出しを<b>外側のリスト</b>へ積んでいた。alt ブロック自体は既に
 * その外側リストへ入っているため、ガードの呼び出しが<b>alt を閉じたあと</b>に並び、
 * 「どの分岐を通っても最後に必ず呼ばれる」という嘘の図になっていた。実際には先行分岐が
 * 成立したときガードは評価されない。先頭の {@code if} の条件は block を積む前に処理して
 * いるので元から正しく、{@code else if} だけがずれていた。</p>
 */
public class PlantUmlSequenceElseIfGuardTest {

    private static final String SRC = ""
            + "class A {\n"
            + "  void run(int k) {\n"
            + "    if (first()) { p(); }\n"
            + "    else if (second()) { q(); }\n"
            + "    else { r(); }\n"
            + "    done();\n"
            + "  }\n"
            + "  boolean first(){return true;} boolean second(){return true;}\n"
            + "  void p(){} void q(){} void r(){} void done(){}\n"
            + "}";

    private static String diagram() {
        List<JavaClassInfo> classes = JavaStructureExtractor.extract(SRC);
        return PlantUmlSequenceDiagram.generate(classes, "A", "run", null);
    }

    /** インデントを潰した行の並びから、指定文字列を含む最初の行番号を返す (無ければ -1)。 */
    private static int lineOf(String diagram, String needle) {
        String[] lines = diagram.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void elseIfGuardIsDrawnInsideTheAltNotAfterIt() {
        String d = diagram();
        int guard = lineOf(d, "A.second()");
        int end = lineOf(d, "end");

        assertTrue("else if のガード呼び出しが描かれること:\n" + d, guard >= 0);
        assertTrue("alt が閉じられること:\n" + d, end >= 0);
        assertTrue("ガードは alt の内側に来ること (end より前):\n" + d, guard < end);
    }

    @Test
    public void elseIfGuardPrecedesItsOwnBranchBody() {
        String d = diagram();
        int guard = lineOf(d, "A.second()");
        int body = lineOf(d, "A.q()");

        assertTrue(guard >= 0 && body >= 0);
        assertTrue("ガードは自分の分岐の本体より先に評価されること:\n" + d, guard < body);
    }

    @Test
    public void theLeadingIfGuardStaysBeforeTheAlt() {
        // 非退行: 先頭の if の条件は従来どおり alt の手前 (無条件に評価される)。
        String d = diagram();
        int guard = lineOf(d, "A.first()");
        int alt = lineOf(d, "alt ");

        assertTrue(guard >= 0 && alt >= 0);
        assertTrue("先頭 if のガードは alt より前のままであること:\n" + d, guard < alt);
    }

    @Test
    public void theStatementAfterTheIfStaysAfterTheAlt() {
        // 非退行: if の後ろの通常の呼び出しは alt の外に残る。
        String d = diagram();
        assertTrue("done() が alt の後に来ること:\n" + d,
                lineOf(d, "A.done()") > lineOf(d, "end"));
    }
}
