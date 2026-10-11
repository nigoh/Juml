// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import juml.core.formats.uml.PlantUmlRenderer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * VHAL 解析パイプラインの衛生の回帰テスト (ラウンド 29)。
 *
 * <ul>
 *   <li>コメントアウト・文字列リテラル中の {@code getProperty(...)} を
 *       実アクセスとして計上しない (レポートと図に存在しない行が並んでいた)。</li>
 *   <li>Markdown 表のセルはエスケープする。Area は AAOS ではビット OR
 *       ({@code AREA_L | AREA_R}) で書くのが定石なので、素の {@code |} は列を割って
 *       Location 列を表の外へ押し出していた。兄弟の {@code MarkdownBuildNinjaReport} は
 *       以前から {@code escapeCell} を持っていた。</li>
 *   <li>フロー図のラベルは共通エスケープを通す。引数が複数行に折り返されていると
 *       生改行がクォート内に入り、<b>折り返し 1 箇所で図が 1 枚も出なかった</b>。
 *       判定は文字列一致ではなく実レンダリングで行う。</li>
 * </ul>
 */
public class VhalReportHygieneTest {

    private static List<VhalAccess> analyze(String body) {
        return new VhalAnalyzer().analyzeSource(
                "package p;\nclass H {\n  CarPropertyManager mCpm;\n  void f() {\n"
                        + body + "  }\n}\n", "H.java");
    }

    /** コメント・文字列の中の呼び出しは数えない。実呼び出しは数える。 */
    @Test
    public void commentedOutAndQuotedCallsAreNotCounted() {
        List<VhalAccess> acc = analyze(
                "    // mCpm.getProperty(OLD_PROP, 0);\n"
                + "    /* mCpm.setProperty(Float.class, DEAD_PROP, 0, 1f); */\n"
                + "    String s = \"mCpm.getProperty(FAKE, 1)\";\n"
                + "    mCpm.getProperty(REAL_PROP, 0);\n");
        assertEquals("実呼び出しの 1 件だけが数えられること: " + acc, 1, acc.size());
        assertEquals("REAL_PROP", acc.get(0).getPropertyShortName());
    }

    /** Area のビット OR がセル区切りとして解釈されないこと。 */
    @Test
    public void aBitwiseOrInTheAreaDoesNotSplitTheTableRow() {
        String md = MarkdownVhalReport.render(analyze(
                "    mCpm.getProperty(PROP, AREA_L | AREA_R);\n"), null);
        String accessRow = null;
        for (String line : md.split("\n")) {
            if (line.contains("AREA_L")) {
                accessRow = line;
            }
        }
        assertTrue("Access sites 行が出ること", accessRow != null);
        assertTrue("セル内の | はエスケープされること: " + accessRow,
                accessRow.contains("AREA_L \\| AREA_R"));
        // エスケープを除いた実区切りは 7 本 (6 列)。
        long delims = accessRow.replace("\\|", "").chars().filter(c -> c == '|').count();
        assertEquals("列数が壊れないこと: " + accessRow, 7, delims);
    }

    /** 複数行に折り返された引数でも図がレンダリングできること。 */
    @Test
    public void aWrappedArgumentStillRenders() throws IOException {
        List<VhalAccess> acc = analyze(
                "    mCpm.getProperty(\n"
                + "        propIdForSeat(\n"
                + "                mSeat),\n"
                + "        0);\n");
        assertEquals(1, acc.size());
        String puml = PlantUmlVhalFlowDiagram.render(acc);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, out);
        assertTrue("SVG が出力されること", out.size() > 0);
    }
}
