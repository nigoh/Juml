// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlCommentFormatter} の無害化ロジックのユニットテスト。
 */
public class PlantUmlCommentFormatterTest {

    @Test
    public void sanitizeEscapesHtmlSpecials() {
        // JavaDoc 由来の <...> はチルダエスケープしてタグ誤認を防ぐ
        // (PlantUML 1.2026.x は &lt; エンティティを解釈せずそのまま表示するため、
        //  creole のエスケープ文字 ~ を使う)。> と & は生のままで安全。
        assertEquals("Returns a List~<String> of names.",
                PlantUmlCommentFormatter.sanitizeInlineComment(
                        "Returns a List<String> of names.", 0));
        assertEquals("a & b",
                PlantUmlCommentFormatter.sanitizeInlineComment("a & b", 0));
    }

    @Test
    public void sanitizeCollapsesControlCharsAndTrailingDots() {
        // 制御文字の畳み込みと末尾 '..' の抑止は従来どおり
        assertEquals("a b c.",
                PlantUmlCommentFormatter.sanitizeInlineComment("a\tb\nc..", 0));
    }

    @Test
    public void sanitizeNeutralizesPlantUmlDirectives() {
        // 動的検証で発見: JavaDoc が @startuml/@enduml を例示していると別図と誤認され、
        // 図全体が構文エラーになる。@ の直後にゼロ幅スペースを挟んで無害化する。
        String out = PlantUmlCommentFormatter.sanitizeInlineComment(
                "emits @startuml then @ENDUML markers", 0);
        // 連続トークン @startuml / @enduml は分断され、表示用テキストは保持される
        assertFalse("raw @startuml token must be broken: " + out,
                out.contains("@startuml"));
        assertFalse("raw @ENDUML token must be broken: " + out,
                out.contains("@ENDUML"));
        assertTrue(out.contains("@\u200Bstartuml"));
        assertTrue(out.contains("@\u200BENDUML"));
    }

    @Test
    public void neutralizeLeavesOtherAtTokensUntouched() {
        // @Override など start/end 以外の @ トークンは変更しない
        assertEquals("see @Override and email a@b.com",
                PlantUmlCommentFormatter.neutralizePlantUmlDirectives(
                        "see @Override and email a@b.com"));
    }

    @Test
    public void escapeTextTildeEscapesTagStart() {
        // < のみチルダエスケープする。& と > は PlantUML 1.2026.x で生のまま安全。
        assertEquals("~<b>", PlantUmlCommentFormatter.escapeText("<b>"));
        assertEquals("a & b", PlantUmlCommentFormatter.escapeText("a & b"));
        assertEquals("x > 0", PlantUmlCommentFormatter.escapeText("x > 0"));
        assertEquals("", PlantUmlCommentFormatter.escapeText(""));
    }

    @Test
    public void escapeLabelHonorsCustomMaxLen() {
        // maxLen 以下なら切り詰めない
        assertEquals("abcdefghij", PlantUmlCommentFormatter.escapeLabel("abcdefghij", 10));
        // maxLen 超は (maxLen-3) 文字 + "..."
        assertEquals("abcde...", PlantUmlCommentFormatter.escapeLabel("abcdefghij", 8));
        // 0 以下は無制限
        assertEquals("abcdefghij", PlantUmlCommentFormatter.escapeLabel("abcdefghij", 0));
    }
}
