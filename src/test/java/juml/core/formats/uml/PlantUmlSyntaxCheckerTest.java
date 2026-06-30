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
}
