// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

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
}
