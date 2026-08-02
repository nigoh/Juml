// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlSyntaxChecker} の検出ルールを検証する。
 *
 * <p>主眼: クラス宣言で「色 → リンク」の誤順序 ({@code #color [[link]]}) を必ず捕まえる
 * こと。これは実際に「この図を描画できませんでした」を招いた既知のゴミで、
 * リグレッションをこのリンタで防ぐ。</p>
 */
public class PlantUmlSyntaxCheckerTest {

    @Test
    public void detectsColorBeforeLink() {
        String puml = "@startuml\n"
                + "class \"a.B\" as C0 #FFF3CD [[juml://class/a.B]] {\n}\n"
                + "@enduml\n";
        List<PlantUmlSyntaxChecker.Issue> issues = PlantUmlSyntaxChecker.check(puml);
        assertFalse("色→リンクの誤順序を検出すべき", issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.message.contains("[[link]] #color")));
        assertEquals("誤順序の行番号は 2", 2, issues.get(0).line);
    }

    @Test
    public void acceptsLinkBeforeColor() {
        String puml = "@startuml\n"
                + "class \"a.B\" as C0 [[juml://class/a.B]] #FFF3CD {\n}\n"
                + "@enduml\n";
        assertTrue("正しい順序 (リンク→色) は問題なし",
                PlantUmlSyntaxChecker.check(puml).isEmpty());
    }

    @Test
    public void acceptsColorOnlyAndLinkOnly() {
        assertTrue(PlantUmlSyntaxChecker.check(
                "@startuml\nclass X #FFF3CD {\n}\n@enduml\n").isEmpty());
        assertTrue(PlantUmlSyntaxChecker.check(
                "@startuml\nclass X [[juml://class/X]] {\n}\n@enduml\n").isEmpty());
    }

    @Test
    public void detectsNamedColorBeforeLink() {
        String puml = "@startuml\nclass X <<missing>> #LightYellow [[juml://class/X]] {\n}\n@enduml\n";
        assertFalse(PlantUmlSyntaxChecker.check(puml).isEmpty());
    }

    @Test
    public void detectsMissingStartAndUnbalancedEnds() {
        List<PlantUmlSyntaxChecker.Issue> issues =
                PlantUmlSyntaxChecker.check("class X {\n}\n@enduml\n");
        assertTrue(issues.stream().anyMatch(i -> i.message.contains("missing @startuml")));
    }

    @Test
    public void detectsUnbalancedLinkBrackets() {
        String puml = "@startuml\nclass X [[juml://class/X] {\n}\n@enduml\n";
        assertTrue(PlantUmlSyntaxChecker.check(puml).stream()
                .anyMatch(i -> i.message.contains("unbalanced link brackets")));
    }

    @Test
    public void emptyInputIsFlagged() {
        assertFalse(PlantUmlSyntaxChecker.check("").isEmpty());
        assertFalse(PlantUmlSyntaxChecker.check(null).isEmpty());
    }

    @Test
    public void summarizeIsEmptyForCleanDiagram() {
        assertEquals("", PlantUmlSyntaxChecker.summarize(
                "@startuml\nclass X [[juml://class/X]] #FFF3CD {\n}\n@enduml\n"));
    }

    // --- @startuml 以外の図種を「壊れている」と誤診しない -------------------------
    //
    // 回帰: @startuml だけを数えていたため、マインドマップ等は必ず
    // "missing @startuml" + count mismatch と診断されていた。この診断は
    // PlantUmlRenderer#buildRenderFailure が原因を分類する材料なので、レイアウト
    // 障害 (UML-R002) まで一律「構文エラー (UML-R001)」へ倒れ、利用者には
    // 見当違いの対処法が案内されていた。

    @Test
    public void acceptsNonUmlDiagramTypes() {
        for (String puml : new String[] {
            "@startmindmap\n* Root\n** Child\n@endmindmap\n",
            "@startwbs\n* Root\n** Child\n@endwbs\n",
            "@startsalt\n{\n  Name | \"  \"\n}\n@endsalt\n",
            "@startgantt\n[T1] lasts 3 days\n@endgantt\n",
            "@startjson\n{\"a\": 1}\n@endjson\n",
            "@startyaml\na: 1\n@endyaml\n"}) {
            assertEquals("正しい非 UML 図を問題なしと判定すること: " + puml,
                    "", PlantUmlSyntaxChecker.summarize(puml));
        }
    }

    @Test
    public void stillDetectsUnclosedNonUmlDiagram() {
        assertTrue("閉じ忘れは検出する",
                PlantUmlSyntaxChecker.check("@startmindmap\n* Root\n").stream()
                        .anyMatch(i -> i.message.contains("count mismatch")));
    }

    @Test
    public void ignoresDirectiveLikeTextInsideDiagram() {
        // 行頭でない "@startuml" (ノート本文やソース図化で頻出) を宣言と数えない。
        assertEquals("", PlantUmlSyntaxChecker.summarize(
                "@startuml\nnote as N\n  write @startuml first\nend note\n@enduml\n"));
    }
}
