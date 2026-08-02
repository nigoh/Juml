// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 向き指定 ({@code left to right direction} / {@code top to bottom direction}) を
 * シーケンス図・アクティビティ図へ前置するとレンダリングエラーになる不具合の回帰テスト。
 *
 * <p>これらの図種は向き指定ディレクティブを受け付けないため、
 * {@link PlantUmlRenderer#injectLayout(String)} は図種を判別して向き指定を抑制する。
 * クラス図など向き指定が有効な図種では従来どおり出力する。</p>
 */
public class PlantUmlDirectionInjectionTest {

    private static final String SEQ =
            "@startuml\ntitle T\nparticipant A\nparticipant B\nA -> B : hi\n@enduml\n";
    private static final String ACT =
            "@startuml\ntitle T\nstart\n:Step 1;\n:Step 2;\nstop\n@enduml\n";
    private static final String CLS =
            "@startuml\nclass A\nclass B\nA --> B : uses\n@enduml\n";
    private static final String STATE =
            "@startuml\n[*] --> S1\nS1 --> S2 : go\nS2 --> [*]\n@enduml\n";

    private String savedFallbackFont;

    @Before
    public void setUp() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        savedFallbackFont = PlantUmlRenderer.getFallbackFontName();
        PlantUmlRenderer.setFallbackFontName("");
    }

    @After
    public void tearDown() {
        PlantUmlRenderer.setStyle(DiagramStyle.defaults());
        PlantUmlRenderer.setGraphvizAvailable(false);
        PlantUmlRenderer.setFallbackFontName(savedFallbackFont);
    }

    private static void setDirection(DiagramStyle.Direction d) {
        DiagramStyle s = new DiagramStyle();
        s.setDirection(d);
        PlantUmlRenderer.setStyle(s);
    }

    // --- supportsDirection の単体判定 ----------------------------------------

    @Test
    public void supportsDirection_falseForSequenceAndActivity() {
        assertFalse("sequence は向き指定不可", PlantUmlRenderer.supportsDirection(SEQ));
        assertFalse("activity は向き指定不可", PlantUmlRenderer.supportsDirection(ACT));
    }

    @Test
    public void supportsDirection_trueForGraphDiagrams() {
        assertTrue("class は向き指定可", PlantUmlRenderer.supportsDirection(CLS));
        assertTrue("state は向き指定可", PlantUmlRenderer.supportsDirection(STATE));
        // usecase (actor) を誤って sequence と判定しないこと
        assertTrue("usecase は向き指定可",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nactor User\nUser --> (Do)\n@enduml\n"));
    }

    // --- ER 図 / 配置図の entity・database を participant と誤認しない --------------
    //
    // 回帰: entity / database / queue はシーケンス図の参加者宣言でもあるため、
    // これだけで「シーケンス図」と決めつけていた。その結果 ER 図・配置図では
    // ユーザがスタイルで選んだ向き指定が黙って捨てられていた (実機では両図種とも
    // 向き指定を正しく受け付ける)。

    /** {@code hide circle} + 列ブロック付き entity = Juml の ER 図出力。 */
    private static final String ER =
            "@startuml\nhide circle\nentity \"User\" as u {\n  * id : int\n}\n"
            + "entity \"Post\" as p {\n  * id : int\n}\nu ||--o{ p\n@enduml\n";
    /** node に入れ子の database = Juml の配置図出力。 */
    private static final String DEPLOY =
            "@startuml\nnode Server {\n  database DB\n}\ncomponent C\nC --> DB\n@enduml\n";

    @Test
    public void supportsDirection_trueForErAndDeployment() {
        assertTrue("ER (entity) は向き指定可", PlantUmlRenderer.supportsDirection(ER));
        assertTrue("配置図 (database) は向き指定可", PlantUmlRenderer.supportsDirection(DEPLOY));
    }

    @Test
    public void injectLayout_keepsDirectionForErAndDeployment() {
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        assertTrue("ER に向き指定が届くこと", PlantUmlRenderer.injectLayout(ER)
                .contains("left to right direction"));
        assertTrue("配置図に向き指定が届くこと", PlantUmlRenderer.injectLayout(DEPLOY)
                .contains("left to right direction"));
    }

    @Test
    public void erAndDeploymentRenderWithDirection() throws Exception {
        for (String puml : new String[] {ER, DEPLOY}) {
            setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PlantUmlRenderer.renderSvg(puml, out);
            assertTrue("向き指定付きでも描画できること", out.size() > 0);
        }
    }

    // --- note / legend / title の本文 (散文) を図種判定に使わない ------------------
    //
    // 回帰: 本文の "state transitions are logged" が state 宣言と誤認されて構造図判定になり、
    // シーケンス図へ向き指定が注入されて実レンダリングが構文エラーになっていた。

    /** note 本文に構造図キーワードらしい散文を含むシーケンス図。 */
    private static final String SEQ_WITH_NOTE =
            "@startuml\ndatabase DB\nUI -> DB : select\nnote over UI\n"
            + "state transitions are logged\nend note\n@enduml\n";
    /** legend 本文に散文を含むシーケンス図。 */
    private static final String SEQ_WITH_LEGEND =
            "@startuml\ndatabase DB\nUI -> DB : select\nlegend\n"
            + "class diagram of the same flow\nendlegend\n@enduml\n";
    /** title / 1 行 note に散文を含むシーケンス図。 */
    private static final String SEQ_WITH_TITLE =
            "@startuml\ntitle node placement overview\ndatabase DB\nUI -> DB : select\n"
            + "note right of DB : entity lifecycle\n@enduml\n";

    @Test
    public void supportsDirection_ignoresFreeTextBodies() {
        assertFalse("note 本文の散文で構造図判定にしない",
                PlantUmlRenderer.supportsDirection(SEQ_WITH_NOTE));
        assertFalse("legend 本文の散文で構造図判定にしない",
                PlantUmlRenderer.supportsDirection(SEQ_WITH_LEGEND));
        assertFalse("title / 1 行 note の散文で構造図判定にしない",
                PlantUmlRenderer.supportsDirection(SEQ_WITH_TITLE));
    }

    @Test
    public void sequenceWithFreeTextRendersWithDirectionSelected() throws Exception {
        for (String puml : new String[] {SEQ_WITH_NOTE, SEQ_WITH_LEGEND, SEQ_WITH_TITLE}) {
            setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 修正前は向き指定が注入され PlantUmlRenderFailedException になっていた。
            PlantUmlRenderer.renderSvg(puml, out);
            assertTrue("向き指定を選んでいても描画できること", out.size() > 0);
        }
    }

    // --- 大文字キーワード / 閉じない浮動ノート / 暗黙参加者 -----------------------
    //
    // PlantUML のキーワードは大文字小文字を区別しない。判定側が小文字だけを見ていたため、
    // Note/Participant と書いた図で自由記述の除外もシーケンス判定も効かなかった。

    @Test
    public void supportsDirection_handlesCapitalisedKeywords() {
        assertFalse("大文字 Note の本文も除外する",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\ndatabase DB\nUI -> DB : select\nNote over UI\n"
                        + "state transitions are logged\nEnd note\n@enduml\n"));
        assertFalse("大文字 Participant もシーケンス判定に効く",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nParticipant Alice\nParticipant Bob\n"
                        + "Alice -> Bob : hi\n@enduml\n"));
        assertFalse("大文字 Start もアクティビティ判定に効く",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nStart\n:do;\nStop\n@enduml\n"));
    }

    @Test
    public void supportsDirection_floatingNoteDoesNotSwallowTheDiagram() {
        // `note "x" as N1` は 1 行で完結する浮動ノート。ブロック開始と誤解すると
        // end note が来ないままファイル末尾まで全行を捨て、構造図の手掛かりが消える。
        assertTrue("浮動ノートの後ろの宣言が生きること",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\ndatabase DB\nnote \"Legend of colors\" as N1\n"
                        + "node Server\ncomponent App\nApp --> DB\n@enduml\n"));
    }

    @Test
    public void supportsDirection_bareEndDoesNotCloseANoteBlock() {
        // note 本文の "end" (JavaDoc をそのまま入れる Juml のシーケンス図で普通に起きる)
        // でブロックが閉じると、以降の散文が図の構造として読まれる。
        assertTrue("本文の end ではブロックを閉じない",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nclass A\nnote as N\nend\nstart\nend note\n@enduml\n"));
    }

    @Test
    public void supportsDirection_ignoresBlockComments() {
        assertFalse("/' ... '/ の中の宣言は数えない",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\n/'\nnode Server\n'/\ndatabase DB\n"
                        + "UI -> DB : select\n@enduml\n"));
    }

    @Test
    public void supportsDirection_falseForImplicitParticipantSequence() {
        // 宣言が 1 つも無くメッセージだけの図は PlantUML がシーケンス図と解釈する。
        // 向き指定を入れると黙ってクラス図に化ける (実機で data-diagram-type が変わる)。
        assertFalse("メッセージだけの図はシーケンス扱い",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nAlice -> Bob : hi\nBob --> Alice : ok\n@enduml\n"));
        assertFalse("ラベル無しの矢印だけでも同じ",
                PlantUmlRenderer.supportsDirection("@startuml\nA --> B\n@enduml\n"));
        assertTrue("class 宣言があればクラス図として向き指定可",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nclass A\nclass B\nA --> B\n@enduml\n"));
    }

    @Test
    public void implicitSequenceKeepsItsDiagramTypeWithDirectionSelected() throws Exception {
        // 実描画で図種が変わらないことまで見る (描画は成功するので文字列では気付けない)。
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg("@startuml\nAlice -> Bob : hi\n@enduml\n", out);
        String svg = out.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("シーケンス図のまま描画されること: "
                        + svg.substring(0, Math.min(400, svg.length())),
                svg.contains("data-diagram-type=\"SEQUENCE\""));
    }

    @Test
    public void supportsDirection_stillSeesRealStructuralDeclarations() {
        // 本文除去で構造図の手掛かりまで落とさないこと (ER/配置図の判定は生きている)。
        assertTrue("note があっても本物の ER 宣言は効く",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nhide circle\nnote as N\nsome prose\nend note\n"
                        + "entity \"User\" as u {\n  * id : int\n}\n@enduml\n"));
        assertTrue("note があっても本物の配置図宣言は効く",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nnote as N\nprose\nend note\nnode Server {\n"
                        + "  database DB\n}\n@enduml\n"));
    }

    @Test
    public void supportsDirection_ignoresCommentedOutDeclarations() {
        // コメントアウトされた宣言も図の構造ではない。
        assertFalse("コメント行の宣言は数えない",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\n' node Server\ndatabase DB\nUI -> DB : select\n@enduml\n"));
    }

    @Test
    public void supportsDirection_falseForSequenceDeclaringEntity() {
        // 逆方向の保険: participant と entity が同居するシーケンス図は従来どおり抑制する
        // (実機で向き指定を入れると構文エラーになる)。
        assertFalse("entity を宣言するシーケンス図は向き指定不可",
                PlantUmlRenderer.supportsDirection(
                        "@startuml\nentity E\nparticipant B\nE -> B : m\n@enduml\n"));
    }

    // --- injectLayout の挿入抑制 ---------------------------------------------

    @Test
    public void injectLayout_omitsDirectionForSequence() {
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        String out = PlantUmlRenderer.injectLayout(SEQ);
        assertFalse("sequence に direction を入れない: " + out,
                out.contains("direction"));
    }

    @Test
    public void injectLayout_omitsDirectionForActivity() {
        setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        String out = PlantUmlRenderer.injectLayout(ACT);
        assertFalse("activity に direction を入れない: " + out,
                out.contains("direction"));
    }

    @Test
    public void injectLayout_keepsDirectionForClass() {
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        String out = PlantUmlRenderer.injectLayout(CLS);
        assertTrue("class には direction を出力する: " + out,
                out.contains("left to right direction"));
    }

    // --- レンダリング回帰 (ユーザ報告のエラー再現) ---------------------------

    @Test
    public void sequenceRendersWithoutErrorForEveryDirection() throws Exception {
        for (DiagramStyle.Direction d : DiagramStyle.Direction.values()) {
            setDirection(d);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // 修正前は LEFT_TO_RIGHT / TOP_TO_BOTTOM で
            // PlantUmlRenderFailedException が投げられていた。
            PlantUmlRenderer.renderSvg(SEQ, out);
            assertTrue("SVG 出力が空でない (dir=" + d + ")", out.size() > 0);
        }
    }

    @Test
    public void activityRendersWithoutErrorForEveryDirection() throws Exception {
        for (DiagramStyle.Direction d : DiagramStyle.Direction.values()) {
            setDirection(d);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PlantUmlRenderer.renderSvg(ACT, out);
            assertTrue("SVG 出力が空でない (dir=" + d + ")", out.size() > 0);
        }
    }

    // --- ビルダ直書きの向きを明示設定が上書きする (設定が無視されない) ----------

    /** 本体に top to bottom 直書きがある図でも、横向き設定で上書きされること。 */
    private static final String CLASS_TTB =
            "@startuml\ntop to bottom direction\nclass A\nclass B\nA --> B\n@enduml\n";

    @Test
    public void explicitLeftToRightOverridesHardcodedTopToBottom() {
        setDirection(DiagramStyle.Direction.LEFT_TO_RIGHT);
        String out = PlantUmlRenderer.injectLayout(CLASS_TTB);
        assertFalse("直書きの top to bottom は除去される: " + out,
                out.contains("top to bottom direction"));
        assertTrue("ユーザ指定の left to right が効く: " + out,
                out.contains("left to right direction"));
    }

    @Test
    public void explicitTopToBottomKeepsSingleDirectionLine() {
        setDirection(DiagramStyle.Direction.TOP_TO_BOTTOM);
        String out = PlantUmlRenderer.injectLayout(CLASS_TTB);
        // 直書き分は除去され、prelude 側の 1 行だけが残る (重複しない)。
        int first = out.indexOf("top to bottom direction");
        assertTrue("top to bottom が残る", first >= 0);
        assertFalse("top to bottom は重複しない",
                out.indexOf("top to bottom direction", first + 1) >= 0);
    }

    @Test
    public void defaultDirectionKeepsHardcodedTopToBottom() {
        setDirection(DiagramStyle.Direction.DEFAULT);
        String out = PlantUmlRenderer.injectLayout(CLASS_TTB);
        // 未指定なら従来どおりビルダの既定 (縦) をそのまま尊重する。
        assertTrue("DEFAULT では直書きの縦をそのまま残す: " + out,
                out.contains("top to bottom direction"));
        assertFalse("DEFAULT では横を足さない: " + out,
                out.contains("left to right direction"));
    }
}
