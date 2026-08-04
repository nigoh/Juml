// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PumlCompletionItem.Kind;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PumlCompletion#items} の候補生成 (あいまい一致・スニペット・矢印・文脈値・
 * 図種による並べ替え) を検証する純ロジックテスト (headless)。
 */
public class PumlCompletionEngineTest {

    private static final String SEQ =
            "@startuml\nparticipant Alice\nparticipant Bob\nAlice -> Bob : hi\n";
    private static final String CLS =
            "@startuml\nclass Foo\nclass Bar\nFoo <|-- Bar\n";

    /** 末尾に {@code typed} を足した状態で補完候補を求める。 */
    private static List<PumlCompletionItem> at(String doc, String typed, boolean explicit) {
        String text = doc + typed;
        return PumlCompletion.items(text, text.length(), explicit);
    }

    private static List<String> labels(List<PumlCompletionItem> items) {
        List<String> out = new ArrayList<>();
        for (PumlCompletionItem i : items) {
            out.add(i.label());
        }
        return out;
    }

    private static PumlCompletionItem first(List<PumlCompletionItem> items, Kind kind) {
        for (PumlCompletionItem i : items) {
            if (i.kind() == kind) {
                return i;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // あいまい一致
    // -------------------------------------------------------------------------

    @Test
    public void abbreviation_matchesBySubsequence() {
        // 打鍵を減らす要。"pt" の 2 打で participant まで届く。
        assertTrue(PumlCompletion.matchScore("participant", "pt") >= 0);
        assertTrue(PumlCompletion.matchScore("skinparam", "sp") >= 0);
        assertTrue(labels(at(SEQ, "pt", false)).contains("participant"));
    }

    @Test
    public void prefixMatch_outranksFuzzyMatch() {
        assertTrue("前方一致はあいまい一致より上",
                PumlCompletion.matchScore("participant", "par")
                        > PumlCompletion.matchScore("participant", "pt"));
    }

    @Test
    public void fuzzyMatch_requiresSharedFirstLetter() {
        // 頭文字を共有しない部分列まで拾うと候補が無関係で埋まり、選ぶ手間が増える。
        assertTrue(PumlCompletion.matchScore("participant", "xt") < 0);
        assertTrue(PumlCompletion.matchScore("class", "las") < 0);
    }

    @Test
    public void fuzzyMatch_needsAtLeastTwoCharacters() {
        assertTrue("1 文字のあいまい一致はノイズなので拒む",
                PumlCompletion.matchScore("participant", "t") < 0);
        assertTrue("1 文字でも前方一致なら通す",
                PumlCompletion.matchScore("participant", "p") >= 0);
    }

    @Test
    public void exactCaseMatch_outranksCaseInsensitiveMatch() {
        assertTrue(PumlCompletion.matchScore("class", "cla")
                > PumlCompletion.matchScore("class", "CLA"));
        assertTrue("大小違いでも候補には残す", PumlCompletion.matchScore("class", "CLA") >= 0);
    }

    @Test
    public void shorterCandidate_winsAtEqualMatchQuality() {
        assertTrue(PumlCompletion.matchScore("note", "no")
                > PumlCompletion.matchScore("noteBorderColor", "no"));
    }

    // -------------------------------------------------------------------------
    // スニペット
    // -------------------------------------------------------------------------

    @Test
    public void snippet_isOfferedAtLineStartAndExpandsToABlock() {
        List<PumlCompletionItem> items = at(SEQ, "al", false);
        PumlCompletionItem snip = first(items, Kind.SNIPPET);
        assertEquals("alt", snip.label());
        assertTrue("ブロックを丸ごと展開する候補であること", snip.isTemplate());
        assertTrue(snip.insert().contains("else"));
        assertTrue(snip.insert().contains("end"));
    }

    @Test
    public void snippet_outranksThePlainKeywordOfTheSameName() {
        // 同名で「語だけ」と「ブロック展開」が並ぶ。打鍵を減らす側を上に出す。
        List<PumlCompletionItem> items = at(SEQ, "al", false);
        assertEquals(Kind.SNIPPET, items.get(0).kind());
        assertEquals("alt", items.get(0).label());
    }

    @Test
    public void snippet_isSuppressedInTheMiddleOfALine() {
        // 行の途中に複数行ブロックを挿し込むのは事故にしかならない。
        List<PumlCompletionItem> items = at(SEQ, "Alice -> al", false);
        assertTrue("行中ではスニペットを出さない",
                items.stream().noneMatch(i -> i.kind() == Kind.SNIPPET));
        assertTrue(labels(items).contains("alt"));
    }

    @Test
    public void everySnippetIsReachableByItsOwnTrigger() {
        // トリガを打ってもその雛形が出てこないスニペットがあると、補完から到達できない。
        for (PumlSnippets.Snippet s : PumlSnippets.all()) {
            String text = "@startuml\n" + s.trigger();
            List<PumlCompletionItem> items =
                    PumlCompletion.items(text, text.length(), false);
            assertTrue("トリガから引けないスニペット: " + s.trigger(),
                    items.stream().anyMatch(i -> i.kind() == Kind.SNIPPET
                            && i.label().equals(s.trigger())));
        }
    }

    // -------------------------------------------------------------------------
    // 図種による並べ替え
    // -------------------------------------------------------------------------

    @Test
    public void flavor_liftsOnTopicKeywordsOverOffTopicOnes() {
        // クラス図で "co" と打ったとき、component/collections よりクラス図の語を先に。
        int classCtx = scoreOf(at(CLS, "cl", false), "class");
        int seqCtx = scoreOf(at(SEQ, "cl", false), "class");
        assertTrue("クラス図の文脈では class が高く出る", classCtx > seqCtx);
    }

    @Test
    public void flavor_demotesKeywordsThatBelongToOtherDiagramsOnly() {
        // 同じ語でも、その図種で使うときと使わないときで順位が変わること。
        int inUseCase = scoreOf(at("@startuml\nusecase (A)\nusecase (B)\n(A) ..> (B)"
                + " : <<include>>\n", "usec", false), "usecase");
        int inSequence = scoreOf(at(SEQ, "usec", false), "usecase");
        assertTrue("ユースケース図の文脈でこそ usecase を上げる", inUseCase > inSequence);
    }

    @Test
    public void flavor_keepsOnTopicKeywordsAheadOfOffTopicOnesWithEqualPrefix() {
        // シーケンス図で "co" と打ったとき、参加者種別の collections が
        // コンポーネント図専用の component より先に出ること。
        List<PumlCompletionItem> items = at(SEQ, "co", false);
        assertTrue(scoreOf(items, "collections") > scoreOf(items, "component"));
    }

    @Test
    public void unknownFlavor_doesNotReorderAnything() {
        // 図種を判定できていないときに上下させると、誤判定がそのまま順位の歪みになる。
        String doc = "@startuml\n";
        List<PumlCompletionItem> items = at(doc, "cl", false);
        int cls = scoreOf(items, "class");
        int cloud = scoreOf(items, "cloud");
        assertEquals("同じ一致品質なら図種で差を付けない", cls - cloud,
                PumlCompletion.matchScore("class", "cl")
                        - PumlCompletion.matchScore("cloud", "cl"));
    }

    @Test
    public void valueOnlyWords_areDemotedOutsideTheirArgumentPosition() {
        // "toy" は !theme の引数でしか意味がない。同じ打鍵で引ける本物のキーワード
        // ("together" / "top") より先に来ると、選ぶ手間だけが増える。
        List<PumlCompletionItem> items = at(CLS, "to", false);
        assertTrue("引数位置の外ではテーマ名を実キーワードより下げる",
                scoreOf(items, "together") > scoreOf(items, "toy"));
        assertTrue(scoreOf(items, "top") > scoreOf(items, "toy"));
    }

    @Test
    public void valueOnlyWords_areLiftedInsideTheirArgumentPosition() {
        // 逆に !theme の引数位置では、素の文脈で沈めていたテーマ名を出す。
        List<PumlCompletionItem> items = at("@startuml\n", "!theme to", false);
        assertTrue(labels(items).contains("toy"));
        assertTrue("引数位置では値以外を混ぜない",
                items.stream().allMatch(i -> i.kind() == Kind.VALUE));
    }

    // -------------------------------------------------------------------------
    // 文脈依存の候補
    // -------------------------------------------------------------------------

    @Test
    public void themeArgument_offersThemeNamesOnly() {
        List<PumlCompletionItem> items = at("@startuml\n", "!theme ce", false);
        assertFalse(items.isEmpty());
        assertTrue(items.stream().allMatch(i -> i.kind() == Kind.VALUE));
        assertTrue(labels(items).contains("cerulean"));
    }

    @Test
    public void skinparamArgument_offersAttributesOnly() {
        List<PumlCompletionItem> items = at("@startuml\n", "skinparam back", false);
        assertTrue(items.stream().allMatch(i -> i.kind() == Kind.VALUE));
        assertTrue(labels(items).contains("backgroundColor"));
    }

    @Test
    public void notePosition_isOfferedRightAfterTheNoteKeyword() {
        // "note " の次に書けるのは位置指定だけ。ここで全キーワードを出しても選べない。
        List<PumlCompletionItem> items = at(SEQ, "note ov", false);
        assertTrue(items.stream().allMatch(i -> i.kind() == Kind.VALUE));
        assertTrue(labels(items).contains("over"));
    }

    @Test
    public void notePosition_completesMultiWordPhrases() {
        // "le" の 2 打で "left of" まで入る。
        assertTrue(labels(at(CLS, "note le", false)).contains("left of"));
    }

    @Test
    public void hideTarget_isOfferedAfterHideAndShow() {
        assertTrue(labels(at(CLS, "hide emp", false)).contains("empty members"));
        assertTrue(labels(at(CLS, "show cir", false)).contains("circle"));
    }

    @Test
    public void stereotype_completesIncludingTheClosingAngles() {
        // "<<in" の後に "clude>>" まで入れて、閉じ記号の打鍵も肩代わりする。
        List<PumlCompletionItem> items = at(SEQ, "(A) ..> (B) : <<in", false);
        assertTrue(labels(items).contains("include>>"));
        assertTrue(items.stream().allMatch(i -> i.kind() == Kind.VALUE));
    }

    @Test
    public void argumentPosition_fallsBackToGeneralCandidatesWhenNothingMatches() {
        // "note" の後でも、位置指定に無い語を打っているなら普通の候補へ戻す
        // (規則に当てはまっただけでポップアップを空にすると打ち止まりになる)。
        List<PumlCompletionItem> items = at(CLS, "note Foo", false);
        assertTrue("位置指定に該当しなければ通常候補へ戻る",
                items.stream().anyMatch(i -> i.kind() != Kind.VALUE));
    }

    @Test
    public void comments_getNoCandidates() {
        // コメントは散文。説明を書いている間ずっとポップアップが出ると邪魔にしかならない。
        assertTrue(at(CLS, "' this is a cla", false).isEmpty());
        assertTrue(at(CLS, "  ' indented cla", false).isEmpty());
        assertTrue("矢印の打ちかけもコメント中では出さない",
                at(CLS, "' a note --", false).isEmpty());
    }

    @Test
    public void blockComments_getNoCandidatesEither() {
        assertTrue(at(CLS, "/' explaining\nthe cla", false).isEmpty());
        assertFalse("閉じたあとは通常どおり候補を出す",
                at(CLS, "/' explaining '/\ncla", false).isEmpty());
    }

    @Test
    public void identifiers_nearestToTheCaretComeFirst() {
        // 大きな図では「さっき書いた名前」をまた書くことが多い。
        String doc = "@startuml\nclass AlphaOne\nclass AlphaTwo\nclass AlphaThree\n"
                + "AlphaOne <|-- AlphaTwo\n";
        List<PumlCompletionItem> items = at(doc, "AlphaT", false);
        assertEquals("直前に書いた AlphaTwo が先", "AlphaTwo", items.get(0).label());
    }

    @Test
    public void arrowGlyphs_areCompletedWhileTypingDashes() {
        List<PumlCompletionItem> items = at(CLS, "Foo --", false);
        assertFalse(items.isEmpty());
        assertTrue("矢印を打ちかけているときは矢印だけを出す",
                items.stream().allMatch(i -> i.kind() == Kind.ARROW));
        assertTrue(labels(items).contains("-->"));
        assertTrue(labels(items).contains("--|>"));
        assertFalse("打ち終わったものは候補にしない", labels(items).contains("--"));
    }

    @Test
    public void arrowCompletion_carriesItsMeaningAsDetail() {
        // <|-- と *-- の違いは覚えにくい。説明が無ければ候補として役に立たない。
        for (String typed : new String[] {"Foo <|-", "Foo --", "Foo ..", "Foo *-"}) {
            List<PumlCompletionItem> items = at(CLS, typed, false);
            assertFalse("矢印候補が出ていない: " + typed, items.isEmpty());
            for (PumlCompletionItem i : items) {
                assertEquals(Kind.ARROW, i.kind());
                assertFalse("矢印候補には意味の説明が要る: " + i.label(), i.detail().isBlank());
            }
        }
    }

    @Test
    public void arrowCompletion_needsADashOrDotBeforeItTriggers() {
        // PlantUML の矢印は必ず - か . を含む。それを条件にしないと "<<" が
        // 矢印扱いになってステレオタイプ補完と食い合い、"Foo o" のような
        // 打ちかけの語まで矢印と誤認する。
        assertEquals("", PumlCompletion.arrowPrefix("Foo <|", 6));
        assertEquals("", PumlCompletion.arrowPrefix("(A) ..> (B) : <<", 16));
        assertEquals("<|-", PumlCompletion.arrowPrefix("Foo <|-", 7));
    }

    @Test
    public void wordRun_isNotMistakenForAnArrow() {
        // "foo" の末尾の o を矢印文字として拾ってはいけない。
        assertEquals("", PumlCompletion.arrowPrefix("Foo", 3));
        assertEquals("", PumlCompletion.arrowPrefix("Foo o", 5));
        assertEquals("--", PumlCompletion.arrowPrefix("Foo --", 6));
        assertEquals("o--", PumlCompletion.arrowPrefix("Foo o--", 7));
    }

    @Test
    public void afterAnArrow_theOtherEndsNameComesFirst() {
        List<PumlCompletionItem> items = at(SEQ, "Alice -> Bo", false);
        assertEquals(Kind.IDENTIFIER, items.get(0).kind());
        assertEquals("Bob", items.get(0).label());
    }

    @Test
    public void directiveSpellings_doNotLeakInAsIdentifiers() {
        // "@startuml" の "startuml" を裸の識別子として拾うと、辞書の候補と二重に並ぶ。
        List<PumlCompletionItem> items = at(SEQ, "Alice -> st", false);
        assertFalse(labels(items).contains("startuml"));
    }

    @Test
    public void explicitInvoke_onEmptyPrefixListsContextCandidates() {
        List<PumlCompletionItem> items = at(SEQ, "", true);
        assertFalse(items.isEmpty());
        assertTrue("行頭の明示起動ではその図種のスニペットが先に出る",
                items.get(0).kind() == Kind.SNIPPET);
        assertTrue(items.size() <= PumlCompletion.MAX_CANDIDATES);
    }

    @Test
    public void explicitInvoke_afterANameOffersArrows() {
        List<PumlCompletionItem> items = at(CLS, "Foo ", true);
        assertTrue(items.stream().allMatch(i -> i.kind() == Kind.ARROW));
    }

    @Test
    public void candidateCount_neverExceedsTheCap() {
        for (String typed : new String[] {"", "a", "e", "s", "no", "cl"}) {
            assertTrue("候補が上限を超えた: '" + typed + "'",
                    at(SEQ, typed, true).size() <= PumlCompletion.MAX_CANDIDATES);
        }
    }

    @Test
    public void completedKeyword_isNotOfferedAsAPlainWordAgain() {
        // 打ち終わった語をそのまま入れ直す候補は無意味。ただし同名のスニペット
        // (class → class Name { }) は「続きを埋める」候補なので残す。
        List<PumlCompletionItem> items = at(CLS, "class", false);
        assertTrue(items.stream().noneMatch(
                i -> i.kind() == Kind.KEYWORD && i.label().equals("class")));
        assertTrue("同名のスニペットは打ち終わってからこそ効く",
                items.stream().anyMatch(
                        i -> i.kind() == Kind.SNIPPET && i.label().equals("class")));
    }

    private static int scoreOf(List<PumlCompletionItem> items, String label) {
        for (PumlCompletionItem i : items) {
            if (i.label().equals(label) && i.kind() != Kind.SNIPPET) {
                return i.score();
            }
        }
        return Integer.MIN_VALUE;
    }
}
