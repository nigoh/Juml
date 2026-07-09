// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@link PumlSourcePanel#editorLineForError(int, int)} の行番号写像 (純関数, headless)。
 *
 * <p>PlantUML は prelude (!pragma layout / skinparam 等) を {@code @startuml} 直後へ
 * 挿入した「生成ソース」の行番号を返すため、エディタ上の行へ戻すには挿入行数を引く。
 * 実測: エディタ行 3 の構文エラーが、挿入 2 行を含む生成ソースでは行 5 と報告される。</p>
 */
public class PumlSourcePanelErrorLineTest {

    @Test
    public void subtractsInjectedLines() {
        // 生成ソースの行 5 - 挿入 2 行 = エディタ行 3。
        assertEquals(3, PumlSourcePanel.editorLineForError(5, 2));
    }

    @Test
    public void startumlLineIsUnchanged() {
        assertEquals(1, PumlSourcePanel.editorLineForError(1, 2));
    }

    @Test
    public void noInjectionKeepsLineNumber() {
        assertEquals(4, PumlSourcePanel.editorLineForError(4, 0));
    }

    @Test
    public void clampsToFirstLineWhenErrorFallsInInjectedPrelude() {
        assertEquals(1, PumlSourcePanel.editorLineForError(2, 5));
    }

    // ── 内容ベースの写像 (editorLineForError(String, String, int)) ──

    /**
     * prelude が 2 行挿入されたケース。生成行 5 (= "C -> D") はエディタ行 3 にある。
     * 純差分方式でも当たるが、内容一致でも同じ行に写像されることを確認する。
     */
    @Test
    public void contentMapping_withPreludeInsertion() {
        String editor = "@startuml\nA -> B\nC -> D\n@enduml";
        String generated = "@startuml\n!pragma layout smetana\nskinparam x y\nA -> B\nC -> D\n@enduml";
        assertEquals(5, findLine(generated, "C -> D"));
        assertEquals(3, PumlSourcePanel.editorLineForError(editor, generated, 5));
    }

    /**
     * 除去された direction 行がエラーより下にあるケース。純差分方式 (挿入2 − 除去1 = 実質1) は
     * エラー行を過剰補正して 1 行ずらすが、内容ベースは正しい行へ写像する。
     * editor:  1 @startuml / 2 A -> B (エラー) / 3 top to bottom direction / 4 @enduml
     * gen:     1 @startuml / 2 prelude1 / 3 prelude2 / 4 A -> B (エラー) / 5 @enduml (direction 除去)
     */
    @Test
    public void contentMapping_directionRemovedBelowErrorDoesNotShift() {
        String editor = "@startuml\nA -> B\ntop to bottom direction\n@enduml";
        String generated = "@startuml\nprelude1\nprelude2\nA -> B\n@enduml";
        // エラーは生成行 4 ("A -> B") → エディタ行 2 に写像されるべき。
        assertEquals(2, PumlSourcePanel.editorLineForError(editor, generated, 4));
    }

    /** {@code @startuml} が 1 行目でないケースでも内容一致で正しく写像する。 */
    @Test
    public void contentMapping_startumlNotOnFirstLine() {
        String editor = "' header comment\n@startuml\nA -> B\n@enduml";
        String generated = "' header comment\n@startuml\nprelude\nA -> B\n@enduml";
        // 生成行 4 ("A -> B") → エディタ行 3。
        assertEquals(3, PumlSourcePanel.editorLineForError(editor, generated, 4));
    }

    /** prelude 由来の行 (エディタに存在しない) は行数差の数値補正へフォールバックする。 */
    @Test
    public void contentMapping_preludeLineFallsBackToNumeric() {
        String editor = "@startuml\nA -> B\n@enduml";
        String generated = "@startuml\n!pragma layout smetana\nA -> B\n@enduml";
        // 生成行 2 (prelude) はエディタに無い → 数値補正 (挿入1) で行 1 付近へ。
        int mapped = PumlSourcePanel.editorLineForError(editor, generated, 2);
        assertEquals(1, mapped);
    }

    /**
     * 同一内容の行が複数あるケース (PlantUML では "}" や重複する関連行が頻出)。
     * 距離基準を生成行番号のままにすると、常に正解より下の複製行が選ばれる下方バイアスが
     * あった。挿入補正後の期待位置で最も近い複製を選ぶことを検証する。
     */
    @Test
    public void contentMapping_duplicateLines_picksNearInjectionCorrectedPosition() {
        String editor = "@startuml\nclass A {\n}\nclass B {\n}\n@enduml";
        String generated = "@startuml\n!pragma layout smetana\nskinparam x y\n"
                + "class A {\n}\nclass B {\n}\n@enduml";
        // generated: 1:@startuml 2:!pragma 3:skinparam 4:class A { 5:} 6:class B { 7:} 8:@enduml
        // editor:    1:@startuml 2:class A { 3:} 4:class B { 5:} 6:@enduml
        assertEquals("最初の '}' (生成行5) はエディタ行 3 に写像されるべき (下方の行 5 でなく)",
                3, PumlSourcePanel.editorLineForError(editor, generated, 5));
        assertEquals("2 個目の '}' (生成行7) はエディタ行 5 に写像されるべき",
                5, PumlSourcePanel.editorLineForError(editor, generated, 7));
    }

    /** null 入力・範囲外でも例外を投げず安全な行番号を返す。 */
    @Test
    public void contentMapping_nullAndOutOfRangeAreSafe() {
        assertEquals(3, PumlSourcePanel.editorLineForError(null, "x", 3));
        assertEquals(1, PumlSourcePanel.editorLineForError("a\nb", "a\nb", 0));
    }

    private static int findLine(String text, String needle) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(needle)) {
                return i + 1;
            }
        }
        return -1;
    }
}
