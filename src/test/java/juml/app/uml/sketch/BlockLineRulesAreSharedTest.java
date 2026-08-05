// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code &#123; … &#125;} ブロックの中の 1 行をどう読むかは、codec をまたいで<b>同じ規則</b>で
 * あることの回帰テスト。
 *
 * <p>どの codec も外側のループでは「コメント行はモデル化できないのでロックする」と決めて
 * いるのに、ブロックの中を読む兄弟の経路だけがばらばらだった。ロックしないまま取り込むと
 * 「編集可能」と表示されたまま、最初の 1 操作で原文が変質する — 利用者が壊したつもりの
 * ない破壊になる。</p>
 */
public class BlockLineRulesAreSharedTest {

    /**
     * ブロック内のコメント行は、どの図種でも「未対応 = 編集ロック」になること。
     *
     * <p>オブジェクト図では特に危険だった。属性は書き出しで {@code Name : attr} へ
     * 正規化されるため、行頭の {@code '} がコロンの後ろへ移って<b>コメントでなくなる</b> —
     * 元の図には描かれなかった行が、設計器で 1 回操作しただけで属性として現れる。</p>
     */
    @Test
    public void aCommentInsideABlockLocksEveryDesigner() {
        ObjectSketchCodec.ParseResult obj = ObjectSketchCodec.parse(
                "@startuml\nobject O {\n  ' a comment\n  a = 1\n}\n@enduml\n");
        assertFalse("オブジェクト図: コメントを含む本体は編集ロック",
                obj.isFullySupported());
        assertTrue("コメントが属性として取り込まれていないこと: "
                        + obj.model.getObjects().get(0).getAttributes(),
                obj.model.getObjects().get(0).getAttributes().stream()
                        .noneMatch(s -> s.startsWith("'")));

        SketchPumlCodec.ParseResult cls = SketchPumlCodec.parse(
                "@startuml\nclass A {\n  ' a comment\n  name : String\n}\n@enduml\n");
        assertFalse("クラス図: コメントを含む本体は編集ロック", cls.isFullySupported());

        ErSketchCodec.ParseResult er = ErSketchCodec.parse(
                "@startuml\nentity A {\n  ' a comment\n  id : int\n}\n@enduml\n");
        assertFalse("ER 図: 以前からロックしていた側", er.isFullySupported());
    }

    /**
     * 区切り線の判定は<b>記号だけの行</b>に限ること。
     *
     * <p>クラス図側だけが行頭一致 ({@code ^(--|==|__|\.\.).*$}) で見ていたため、
     * {@code __id : int} のようなごく普通のメンバー名 (Python / C++ / PHP 由来の
     * private 命名) を区切り線と誤認し、往復では何も壊れないのにクラス全体を
     * 編集不可にしていた。ER 図は同じ列名を問題なく受理する。</p>
     */
    @Test
    public void anUnderscoredMemberNameIsNotADivider() {
        SketchPumlCodec.ParseResult cls = SketchPumlCodec.parse(
                "@startuml\nclass A {\n  __id : int\n  name : String\n}\n@enduml\n");
        assertTrue("__id : int は区切り線ではないので編集できること: "
                + cls.unsupportedLines, cls.isFullySupported());
        assertEquals(2, cls.model.getClasses().get(0).getFields().size());

        ErSketchCodec.ParseResult er = ErSketchCodec.parse(
                "@startuml\nentity A {\n  __id : int\n}\n@enduml\n");
        assertTrue("ER 図と判定が一致すること", er.isFullySupported());
    }

    /** 記号だけの行は従来どおり区切り線として扱い、並び崩れの危険を報告すること。 */
    @Test
    public void aSymbolOnlyLineIsStillADivider() {
        SketchPumlCodec.ParseResult cls = SketchPumlCodec.parse(
                "@startuml\nclass A {\n  a : int\n  --\n  b : int\n}\n@enduml\n");
        assertFalse("区切り線を含む本体は編集ロックのまま", cls.isFullySupported());
    }

    /**
     * ER 図の区切り線は「書き戻しで同じ位置に引ける」ときだけ捨ててよいこと。
     *
     * <p>書き出しは PK 列と一般列の境目に 1 本引く。読んだ区切りがその境目に無いなら、
     * 編集すると<b>黙って消える</b>。読んだものが出てこないならロックする。</p>
     */
    @Test
    public void anErDividerIsOnlyDroppedWhenItWillBeWrittenBack() {
        ErSketchCodec.ParseResult atBoundary = ErSketchCodec.parse(
                "@startuml\nentity A {\n  * id : int\n  --\n  name : text\n}\n@enduml\n");
        assertTrue("PK と一般列の境目にある区切りは再現される: "
                + atBoundary.unsupportedLines, atBoundary.isFullySupported());
        assertTrue("書き戻しにも区切りが出ること",
                ErSketchCodec.toPuml(atBoundary.model).contains("--"));

        ErSketchCodec.ParseResult elsewhere = ErSketchCodec.parse(
                "@startuml\nentity A {\n  id : int\n  --\n  name : text\n}\n@enduml\n");
        assertFalse("PK が無く再現できない区切りはロックすること",
                elsewhere.isFullySupported());
    }

    /**
     * ブロックコメント {@code /' … '/} も行コメントと同じ扱いであること。
     *
     * <p>{@code '} だけを見ていたため素通りしてオブジェクト図の属性になり、書き出しで
     * {@code Foo : /' hidden '/} になった。PlantUML はブロックコメントを除去してから
     * 解析するので {@code Foo :} だけが残り、<b>構文エラーで図が描けなくなる</b> —
     * 設計器で 1 回操作しただけで描けない図に変わっていた。</p>
     */
    @Test
    public void aBlockCommentInsideABlockLocksTheDesignerToo() {
        ObjectSketchCodec.ParseResult obj = ObjectSketchCodec.parse(
                "@startuml\nobject Foo {\n  a = 1\n  /' hidden '/\n  b = 2\n}\n@enduml\n");
        assertFalse("ブロックコメントを含む本体は編集ロック", obj.isFullySupported());
        assertTrue("属性として取り込まれていないこと: "
                        + obj.model.getObjects().get(0).getAttributes(),
                obj.model.getObjects().get(0).getAttributes().stream()
                        .noneMatch(s -> s.startsWith("/'")));
    }

    /**
     * 区切り線の<b>トークン</b>も往復すること。書き出しは常に {@code --} を引くので、
     * {@code ==} / {@code __} / {@code ..} で書かれた図は編集で化ける。
     *
     * <p>同梱 PlantUML 1.2026.6 で 4 種は別の線として描かれる (実測: {@code ==} は
     * 二重線、{@code ..} は破線)。ラウンド 20 は区切りの<b>位置</b>しか見ておらず、
     * トークンの違いが素通しだった。</p>
     */
    @Test
    public void anErDividerTokenIsNotSilentlyReplaced() {
        for (String token : new String[]{"==", "__", ".."}) {
            ErSketchCodec.ParseResult r = ErSketchCodec.parse(
                    "@startuml\nhide circle\nentity E {\n  * id : int\n  " + token
                            + "\n  name : text\n}\n@enduml\n");
            assertFalse(token + " は書き戻しで -- に化けるのでロックすること",
                    r.isFullySupported());
            assertEquals("報告するのは原文の行であること (利用者のファイルに無い行を出さない)",
                    List.of(token), r.unsupportedLines);
        }
    }

    /**
     * 区切り線が無くても、列の並べ替えが起きるならロックすること。
     *
     * <p>書き出しは PK 列 → 一般列の順に固定されている。原文で PK が後ろにあると
     * 設計器で 1 回動かしただけで列順が変わり、元は無かった {@code --} まで挿入される。
     * ラウンド 20 の検出は区切り線があるときにしか働いていなかった。</p>
     */
    @Test
    public void reorderingColumnsLocksEvenWithoutADivider() {
        ErSketchCodec.ParseResult reordered = ErSketchCodec.parse(
                "@startuml\nhide circle\nentity M {\n  name : text\n  * id : int\n}\n@enduml\n");
        assertFalse("PK が後ろにある = 書き出しで前へ動く", reordered.isFullySupported());

        ErSketchCodec.ParseResult interleaved = ErSketchCodec.parse(
                "@startuml\nhide circle\nentity O {\n  * order_id : int\n  qty : int\n"
                        + "  * product_id : int\n}\n@enduml\n");
        assertFalse("複合キーが割れる形もロック", interleaved.isFullySupported());

        ErSketchCodec.ParseResult ordered = ErSketchCodec.parse(
                "@startuml\nhide circle\nentity P {\n  * id : int\n  name : text\n}\n@enduml\n");
        assertTrue("並びが書き出しと同じなら編集できること: " + ordered.unsupportedLines,
                ordered.isFullySupported());
    }

    /**
     * ロックする規則と<b>解除できる規則</b>が一致していること。
     *
     * <p>ブロックコメントをロック対象にしたのに、解除側の判定は {@code '} しか見ていなかった。
     * その結果、ブロックコメントを含む図は読み取り専用のまま<b>解除の手段が提示されない</b> —
     * 同じ図を行コメントで書けば 1 クリックで解除できるのに、である。ロックする規則を
     * 増やすときは、出口の規則も同じだけ増やさなければならない。</p>
     *
     * <p>複数行に跨るブロックコメントは対象外。解除は行単位で消すので、先頭行だけを
     * 消すとファイルが壊れる (押しても何も起きないボタンを出さない、という既存の
     * {@code '@pos} の判断と同じ)。</p>
     */
    @Test
    public void whatLocksTheDesignerIsAlsoWhatTheUnlockRemoves() {
        SketchPumlCodec.ParseResult block = SketchPumlCodec.parse(
                "@startuml\nclass A {\n  /' TODO: fix later '/\n  x : int\n}\n@enduml\n");
        assertFalse("ブロックコメントはロックする", block.isFullySupported());
        assertTrue("そのロックは解除の対象でもあること: " + block.unsupportedLines,
                block.unsupportedLines.stream()
                        .allMatch(SketchDiagramType::isRemovableComment));

        SketchPumlCodec.ParseResult line = SketchPumlCodec.parse(
                "@startuml\nclass A {\n  ' TODO\n  x : int\n}\n@enduml\n");
        assertTrue("行コメントは従来どおり解除対象",
                line.unsupportedLines.stream()
                        .allMatch(SketchDiagramType::isRemovableComment));

        // 閉じていないブロックコメントは行単位で消せないので解除対象にしない。
        assertFalse("複数行のブロックコメントは解除対象にしない",
                SketchDiagramType.isRemovableComment("/' 複数行の"));
        // レイアウトコメントは従来どおり残す。
        assertFalse("'@pos は消さない",
                SketchDiagramType.isRemovableComment("'@pos A 10 20"));
    }
}
