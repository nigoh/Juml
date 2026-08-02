// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ObjectSketchDialogs#isValidNewName(String, ObjectSketchModel, ObjectInstance)}
 * の名前形式チェック・重複チェックを検証する (モーダルを介さない純ロジックテスト。headless 可)。
 */
public class ObjectSketchDialogsValidationTest {

    private ObjectSketchModel model;
    private ObjectInstance alice;
    private ObjectInstance bob;

    @Before
    public void setUp() {
        model = new ObjectSketchModel();
        alice = new ObjectInstance("Alice", null, 0, 0);
        bob = new ObjectInstance("Bob", null, 100, 0);
        model.getObjects().add(alice);
        model.getObjects().add(bob);
    }

    @Test
    public void isValidNewName_acceptsNewUniqueName() {
        assertTrue("未使用の妥当な名前は受理されるはず",
                ObjectSketchDialogs.isValidNewName("Carol", model, alice));
    }

    @Test
    public void isValidNewName_rejectsNameUsedByAnotherObject() {
        assertFalse("既存の別オブジェクトと同じ名前への変更は拒否されるはず",
                ObjectSketchDialogs.isValidNewName("Bob", model, alice));
    }

    @Test
    public void isValidNewName_acceptsUnchangedOwnName() {
        assertTrue("自分自身と同じ名前 (リネームなし) は受理されるはず",
                ObjectSketchDialogs.isValidNewName("Alice", model, alice));
    }

    @Test
    public void isValidNewName_rejectsEmptyString() {
        assertFalse("空文字は名前として不正なはず",
                ObjectSketchDialogs.isValidNewName("", model, alice));
    }

    @Test
    public void isValidNewName_rejectsLeadingDigit() {
        assertFalse("先頭が数字の名前は不正なはず (PlantUML 識別子の境界)",
                ObjectSketchDialogs.isValidNewName("1Alice", model, alice));
    }

    @Test
    public void isValidNewName_rejectsInternalSpace() {
        assertFalse("空白を含む名前は不正なはず",
                ObjectSketchDialogs.isValidNewName("Al ice", model, alice));
    }

    @Test
    public void isValidNewName_acceptsDotSeparatedName() {
        // オブジェクト名は引用符なしで書ける範囲としてドットを許容する
        // ({@code List.first} のような修飾名を想定)。
        assertTrue("ドットを含む名前は受理されるはず",
                ObjectSketchDialogs.isValidNewName("List.first", model, alice));
    }

    @Test
    public void isValidNewName_rejectsLeadingDot() {
        assertFalse("先頭がドットの名前は不正なはず (先頭は識別子開始文字が必要)",
                ObjectSketchDialogs.isValidNewName(".first", model, alice));
    }
}
