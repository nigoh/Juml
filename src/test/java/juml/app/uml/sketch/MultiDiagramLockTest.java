// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 図が 2 つ入ったファイルを、<b>どの図種の設計器も</b>編集ロックすることの回帰テスト。
 *
 * <p>どの codec も開始行を「図名の上書き」として読み飛ばす作りで、2 つめの図の中身が
 * 1 つめのモデルへそのまま積み増されていた。未対応行ゼロ = 編集可能と判定されるので、
 * 設計器で 1 回動かしただけで書き戻しが全文を置き換え、図の区切りも先頭の図名も消えて
 * 1 枚に統合された図が残る。利用者が壊したつもりのない破壊なので、書き戻し自体を止める。</p>
 *
 * <p>この判定を 1 つの codec にだけ入れていたため、残り 9 つは同じ破壊を起こしたままだった。
 * 判定は {@link SketchMultiDiagram} へ寄せ、ここで<b>全 codec を一覧で</b>固定する。
 * 新しい図種を足したらこの表にも足すこと。</p>
 */
public class MultiDiagramLockTest {

    /** 1 図ぶんの本文と、その図種の開始/終了トークン。 */
    private static String two(String start, String end, String bodyA, String bodyB) {
        return start + " First\n" + bodyA + "\n" + end + "\n"
                + start + " Second\n" + bodyB + "\n" + end + "\n";
    }

    private static String one(String start, String end, String body) {
        return start + " Only\n" + body + "\n" + end + "\n";
    }

    private static void assertLocked(String kind, boolean fullySupported, List<String> unsupported) {
        assertFalse(kind + ": 複数図のファイルは編集ロックされること (統合して書き戻さない)",
                fullySupported);
        assertTrue(kind + ": 2 本目の開始行が未対応として報告されること: " + unsupported,
                unsupported.stream().anyMatch(l -> l.startsWith("@start")));
    }

    @Test
    public void classDesignerLocksTwoDiagrams() {
        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(
                two("@startuml", "@enduml", "class Foo", "class Bar"));
        assertLocked("class", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void sequenceDesignerLocksTwoDiagrams() {
        SeqSketchCodec.ParseResult r = SeqSketchCodec.parse(
                two("@startuml", "@enduml", "Alice -> Bob : hi", "Carl -> Dan : yo"));
        assertLocked("sequence", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void stateDesignerLocksTwoDiagrams() {
        StateSketchCodec.ParseResult r = StateSketchCodec.parse(
                two("@startuml", "@enduml", "state Idle", "state Busy"));
        assertLocked("state", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void objectDesignerLocksTwoDiagrams() {
        ObjectSketchCodec.ParseResult r = ObjectSketchCodec.parse(
                two("@startuml", "@enduml", "object Foo", "object Bar"));
        assertLocked("object", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void erDesignerLocksTwoDiagrams() {
        ErSketchCodec.ParseResult r = ErSketchCodec.parse(
                two("@startuml", "@enduml", "entity Foo {\n}", "entity Bar {\n}"));
        assertLocked("er", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void componentDesignerLocksTwoDiagrams() {
        ComponentSketchCodec.ParseResult r = ComponentSketchCodec.parse(
                two("@startuml", "@enduml", "component Foo", "component Bar"));
        assertLocked("component", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void useCaseDesignerLocksTwoDiagrams() {
        UseCaseSketchCodec.ParseResult r = UseCaseSketchCodec.parse(
                two("@startuml", "@enduml", "usecase Foo", "usecase Bar"));
        assertLocked("usecase", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void deployDesignerLocksTwoDiagrams() {
        DeploySketchCodec.ParseResult r = DeploySketchCodec.parse(
                two("@startuml", "@enduml", "node Foo", "node Bar"));
        assertLocked("deploy", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void activityDesignerLocksTwoDiagrams() {
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(
                two("@startuml", "@enduml", "start\n:login;\nstop", "start\n:logout;\nstop"));
        assertLocked("activity", r.isFullySupported(), r.unsupportedLines);
    }

    @Test
    public void mindmapDesignerLocksTwoDiagrams() {
        MindmapSketchCodec.ParseResult r = MindmapSketchCodec.parse(
                two("@startmindmap", "@endmindmap", "* Root\n** A", "* Root2\n** B"));
        assertLocked("mindmap", r.isFullySupported(), r.unsupportedLines);
    }

    // -------------------------------------------------------------------------
    // 非退行: 図が 1 つだけのファイルは従来どおり編集できること。
    // 誤ってロックすると、その図種の設計器がまるごと使えなくなる。
    // -------------------------------------------------------------------------

    @Test
    public void singleDiagramsStayEditable() {
        assertTrue("class", SketchPumlCodec.parse(
                one("@startuml", "@enduml", "class Foo")).isFullySupported());
        assertTrue("sequence", SeqSketchCodec.parse(
                one("@startuml", "@enduml", "Alice -> Bob : hi")).isFullySupported());
        assertTrue("state", StateSketchCodec.parse(
                one("@startuml", "@enduml", "state Idle")).isFullySupported());
        assertTrue("object", ObjectSketchCodec.parse(
                one("@startuml", "@enduml", "object Foo")).isFullySupported());
        assertTrue("er", ErSketchCodec.parse(
                one("@startuml", "@enduml", "entity Foo {\n}")).isFullySupported());
        assertTrue("component", ComponentSketchCodec.parse(
                one("@startuml", "@enduml", "component Foo")).isFullySupported());
        assertTrue("usecase", UseCaseSketchCodec.parse(
                one("@startuml", "@enduml", "usecase Foo")).isFullySupported());
        assertTrue("deploy", DeploySketchCodec.parse(
                one("@startuml", "@enduml", "node Foo")).isFullySupported());
        assertTrue("activity", ActivitySketchCodec.parse(
                one("@startuml", "@enduml", "start\n:login;\nstop")).isFullySupported());
        assertTrue("mindmap", MindmapSketchCodec.parse(
                one("@startmindmap", "@endmindmap", "* Root\n** A")).isFullySupported());
    }

    /**
     * 回帰: PlantUML の複数図記法 {@code @startuml(id=NAME)} も 2 本目として数えること。
     *
     * <p>番人だけが「トークンの直後は行末か空白」と余計に厳しく、codec 側は残りを図名として
     * 素通しで受け入れていた。ずれた結果、{@code (} が直後に来るこの記法では番人が何も
     * 報告せず、番人を入れる前とまったく同じ統合が起きていた。判定は codec と 1 行の狂いも
     * なく同じでなければならない。</p>
     */
    @Test
    public void theIdFormOfASecondDiagramIsCounted() {
        String text = "@startuml(id=FIRST)\nclass Alpha\n@enduml\n"
                + "@startuml(id=SECOND)\nclass Beta\n@enduml\n";

        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(text);

        assertLocked("class(id=)", r.isFullySupported(), r.unsupportedLines);
    }

    /** 回帰: 素の記法と id 記法が混在していても 2 本目を数えること。 */
    @Test
    public void aMixOfPlainAndIdFormsIsCounted() {
        String text = "@startuml\nclass Alpha\n@enduml\n@startuml(id=B)\nclass Beta\n@enduml\n";

        SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(text);

        assertLocked("class(mixed)", r.isFullySupported(), r.unsupportedLines);
    }

    /**
     * 回帰: 開始行を<b>読んだとおりに</b>書き戻すこと。
     *
     * <p>組み立てが「トークン + 空白 + 図名」の 1 形だけだった。PlantUML の
     * {@code @startuml(id=NAME)} はトークンに {@code (} が接していることが構文なので、
     * 空白が入ると意味が変わる — 実測で {@code (id=FIRST)} が<b>出力ファイル名</b>と
     * 解釈される。単一図なら番人は鳴らないので設計器は編集可能で開き、1 回動かしただけで
     * id が失われ、成果物の名前が変わり {@code !include file!ID} も解決しなくなる。</p>
     */
    @Test
    public void theStartLineRoundTripsAsWritten() {
        for (String start : List.of("@startuml(id=FIRST)", "@startuml Named", "@startuml")) {
            SketchPumlCodec.ParseResult r = SketchPumlCodec.parse(start + "\nclass Alpha\n@enduml\n");
            assertTrue("単一図は編集可能のままであること: " + start, r.isFullySupported());
            String out = SketchPumlCodec.toPuml(r.model);
            assertEquals("開始行が読んだとおりに戻ること", start,
                    out.substring(0, out.indexOf('\n')));
        }
    }

    /** 回帰: mindmap の id 記法も同じこと。 */
    @Test
    public void theMindmapStartLineRoundTripsAsWritten() {
        String start = "@startmindmap(id=M)";
        MindmapSketchCodec.ParseResult r =
                MindmapSketchCodec.parse(start + "\n* Root\n** A\n@endmindmap\n");

        assertTrue(r.isFullySupported());
        String out = MindmapSketchCodec.toPuml(r.model);
        assertEquals(start, out.substring(0, out.indexOf('\n')));
    }

    /**
     * 番人の数え方が codec の数え方と一致していること。
     *
     * <p>両者がずれた瞬間に、取りこぼす (統合が起きる) か誤ってロックする (設計器が
     * 使えなくなる) かのどちらかになる。ここでは codec が開始行として受け入れる形を
     * 並べ、番人が同じだけ数えることを固定する。</p>
     */
    @Test
    public void theGuardCountsExactlyWhatTheCodecCountsAsAStartLine() {
        for (String second : List.of("@startuml", "@startuml Named", "@startuml(id=X)",
                "  @startuml  ", "@startumlish")) {
            java.util.List<String> unsupported = new java.util.ArrayList<>();
            SketchMultiDiagram.reportExtraDiagrams(
                    new String[]{"@startuml A", "class C", "@enduml", second}, "@startuml",
                    unsupported);
            // codec 側は trim().startsWith("@startuml") で開始行と判定する。同じ条件。
            assertTrue("codec が開始行と見なす形は番人も数えること: [" + second + "] -> " + unsupported,
                    !unsupported.isEmpty());
        }
    }

    /** 図名なしの 2 本目 (トークンだけの行) もきちんと数えること。 */
    @Test
    public void anUnnamedSecondDiagramIsStillCounted() {
        java.util.List<String> unsupported = new java.util.ArrayList<>();
        SketchMultiDiagram.reportExtraDiagrams(
                new String[]{"@startuml", "class A", "@enduml", "@startuml", "class B", "@enduml"},
                "@startuml", unsupported);

        assertTrue("図名なしでも数えること: " + unsupported, unsupported.contains("@startuml"));
    }
}
