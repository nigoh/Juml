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

    @Test
    public void escapeTextStripsControlChars() {
        // ソース定数由来の NUL 等の制御文字は PlantUML/SVG を壊すため除去する
        assertEquals("ALL", PlantUmlCommentFormatter.escapeText("\u0000ALL"));
        assertEquals("ab", PlantUmlCommentFormatter.escapeText("a\u0001\u001Fb"));
        // タブは許容する
        assertEquals("a\tb", PlantUmlCommentFormatter.escapeText("a\tb"));
    }

    @Test
    public void escapeTextNeutralizesEmbeddedBlockDirectives() {
        // 定数値などに含まれる @startuml/@enduml をゼロ幅スペースで分断し
        // ブロック境界と誤認されないようにする
        String out = PlantUmlCommentFormatter.escapeText("join(\"@startuml\", \"@enduml\")");
        assertTrue(out, out.contains("@\u200Bstartuml"));
        assertTrue(out, out.contains("@\u200Benduml"));
        // uml が続かない @start/@end (アノテーション等) は変更しない
        assertEquals("@Startup", PlantUmlCommentFormatter.escapeText("@Startup"));
    }

    @Test
    public void escapeTextEscapesLinkBrackets() {
        // "[[" は PlantUML リンク構文と誤認されテキストが欠落するため
        // creole エスケープでリテラル表示させる
        assertEquals("[~[label]](url)", PlantUmlCommentFormatter.escapeText("[[label]](url)"));
        // 単独の "[" は無害なので変換しない
        assertEquals("a[0]", PlantUmlCommentFormatter.escapeText("a[0]"));
    }

    @Test
    public void escapeMemberKeepsShortFragmentsIntact() {
        // 安全上限以下の通常長の型/値はそのまま (エスケープのみ) 全文表示する
        assertEquals("Map~<String, Integer>",
                PlantUmlCommentFormatter.escapeMember("Map<String, Integer>", 500));
        // 60 文字程度の定数値は切り詰められない (既存の全文表示仕様と両立)
        String sixty = repeat('A', 60);
        assertEquals(sixty, PlantUmlCommentFormatter.escapeMember(sixty, 500));
    }

    @Test
    public void escapeMemberTruncatesPathologicallyLongFragments() {
        // 安全上限を超える巨大な断片 (base64 鍵・巨大 SQL 等) は末尾を … で省略し、
        // 1 行の幅がキャンバス上限を超えて描画失敗するのを防ぐ
        String huge = repeat('A', 5000);
        String out = PlantUmlCommentFormatter.escapeMember(huge, 500);
        assertTrue("should be truncated to the safety limit: len=" + out.length(),
                out.length() <= 500);
        assertTrue("truncation marker … should be appended", out.endsWith("…"));
        // 3 点リーダ "..." ではなく 1 文字の U+2026 を使う (全文判定と衝突させない)
        assertFalse("must not use ... marker: " + out, out.contains("..."));
    }

    @Test
    public void escapeMemberZeroOrNegativeMeansUnlimited() {
        // 0 以下は無制限 (従来どおり全文表示)
        String huge = repeat('A', 3000);
        assertEquals(huge, PlantUmlCommentFormatter.escapeMember(huge, 0));
        assertEquals(huge, PlantUmlCommentFormatter.escapeMember(huge, -1));
    }

    @Test
    public void escapeMemberHandlesNullAndEmpty() {
        assertEquals("", PlantUmlCommentFormatter.escapeMember(null, 500));
        assertEquals("", PlantUmlCommentFormatter.escapeMember("", 500));
    }

    @Test
    public void sanitizeInlineCappedBySafetyLimitWhenUnbounded() {
        // maxLen=0 (無指定) でも、INLINE コメントは折り返されないため描画安全上限で切り詰める
        String huge = repeat('x', 4000);
        String out = PlantUmlCommentFormatter.sanitizeInlineComment(huge, 0);
        assertTrue("unbounded inline comment must still be capped: len=" + out.length(),
                out.length() <= PlantUmlCommentFormatter.MEMBER_TEXT_SAFETY_LIMIT);
        assertTrue(out.endsWith("…"));
    }

    /** {@code String.repeat} 相当 (テスト内ヘルパ、可読性のため)。 */
    private static String repeat(char c, int n) {
        char[] a = new char[n];
        java.util.Arrays.fill(a, c);
        return new String(a);
    }

    @Test
    public void sanitizeNoteLineDefusesEndNoteLines() {
        // "end note" だけの行は note ブロックを打ち切る終端注入になるため、
        // 行頭にゼロ幅スペースを挟んで無害化する
        assertEquals("\u200Bend note",
                PlantUmlCommentFormatter.sanitizeNoteLine("end note"));
        assertEquals("\u200BEND NOTE",
                PlantUmlCommentFormatter.sanitizeNoteLine("END NOTE"));
        assertEquals("\u200Bendnote",
                PlantUmlCommentFormatter.sanitizeNoteLine("endnote"));
        // 終端キーワード以外は変更しない
        assertEquals("note the end",
                PlantUmlCommentFormatter.sanitizeNoteLine("note the end"));
        assertEquals("end noteworthy",
                PlantUmlCommentFormatter.sanitizeNoteLine("end noteworthy"));
    }

    @Test
    public void appendNoteBodyDefusesTerminatorInjection() {
        StringBuilder out = new StringBuilder();
        PlantUmlCommentFormatter.appendNoteBody(out,
                "1 行目\nend note\nalt 攻撃", "", 0);
        String body = out.toString();
        assertFalse("note 本文の end note が終端として残らないこと: " + body,
                body.contains("\n  end note\n"));
        assertTrue(body.contains("\u200Bend note"));
    }

    @Test
    public void sanitizeNoteLineDefusesPreprocessorAndCommentStarts() {
        // 行頭の ! はプリプロセッサ命令 (!theme 等) と誤認され構文エラーになる
        assertEquals("\u200B!theme / skinparam 行",
                PlantUmlCommentFormatter.sanitizeNoteLine("!theme / skinparam 行"));
        // 行頭の ' は行コメントとして本文が黙って消える
        assertEquals("\u200B'quoted word",
                PlantUmlCommentFormatter.sanitizeNoteLine("'quoted word"));
        // 行頭の /' はブロックコメント開始として後続を飲み込む
        assertEquals("\u200B/' block",
                PlantUmlCommentFormatter.sanitizeNoteLine("/' block"));
        // 行中の ! や // は無害なので変更しない
        assertEquals("a != b", PlantUmlCommentFormatter.sanitizeNoteLine("a != b"));
        assertEquals("// comment", PlantUmlCommentFormatter.sanitizeNoteLine("// comment"));
    }
}
