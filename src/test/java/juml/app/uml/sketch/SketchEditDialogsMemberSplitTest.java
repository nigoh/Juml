// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * bug-hunt R3 で発見: クラス編集ダイアログの Fields/Methods 欄と codec の {@code (} 判定が
 * 食い違うと、GUI 入力だけで次回ロード時に編集ロックになっていた。振り分けの正規化を検証する。
 */
public class SketchEditDialogsMemberSplitTest {

    @Test
    public void normalizeMemberSplit_movesLinesByParenthesisRule() {
        List<String> fields = new ArrayList<>(List.of("name: String", "init()", "-- sep --"));
        List<String> methods = new ArrayList<>(List.of("count: int", "run(): void"));
        SketchEditDialogs.normalizeMemberSplit(fields, methods);
        assertEquals(List.of("name: String", "-- sep --", "count: int"), fields);
        assertEquals(List.of("init()", "run(): void"), methods);
    }

    @Test
    public void normalizeMemberSplit_keepsConsistentInputUnchanged() {
        List<String> fields = new ArrayList<>(List.of("a: int", "b: long"));
        List<String> methods = new ArrayList<>(List.of("m(): void"));
        SketchEditDialogs.normalizeMemberSplit(fields, methods);
        assertEquals(List.of("a: int", "b: long"), fields);
        assertEquals(List.of("m(): void"), methods);
    }
}
