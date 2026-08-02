// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link StateSketchDialogs#isValidNewName(String, StateSketchModel, StateNode)}
 * の状態名の形式チェック・重複チェックを検証する (モーダルを介さない純ロジックテスト。headless 可)。
 */
public class StateSketchDialogsValidationTest {

    private StateSketchModel model;
    private StateNode idle;
    private StateNode running;

    @Before
    public void setUp() {
        model = new StateSketchModel();
        idle = new StateNode("Idle", 0, 0);
        running = new StateNode("Running", 100, 0);
        model.getStates().add(idle);
        model.getStates().add(running);
    }

    @Test
    public void isValidNewName_acceptsNewUniqueName() {
        assertTrue("未使用の妥当な名前は受理されるはず",
                StateSketchDialogs.isValidNewName("Stopped", model, idle));
    }

    @Test
    public void isValidNewName_rejectsNameUsedByAnotherState() {
        assertFalse("既存の別状態と同じ名前への変更は拒否されるはず",
                StateSketchDialogs.isValidNewName("Running", model, idle));
    }

    @Test
    public void isValidNewName_acceptsUnchangedOwnName() {
        assertTrue("自分自身と同じ名前 (リネームなし) は受理されるはず",
                StateSketchDialogs.isValidNewName("Idle", model, idle));
    }

    @Test
    public void isValidNewName_rejectsEmptyString() {
        assertFalse("空文字は状態名として不正なはず",
                StateSketchDialogs.isValidNewName("", model, idle));
    }

    @Test
    public void isValidNewName_rejectsLeadingDigit() {
        assertFalse("先頭が数字の状態名は不正なはず (PlantUML 識別子の境界)",
                StateSketchDialogs.isValidNewName("1Idle", model, idle));
    }

    @Test
    public void isValidNewName_rejectsInternalSpace() {
        assertFalse("空白を含む状態名は不正なはず",
                StateSketchDialogs.isValidNewName("Id le", model, idle));
    }

    @Test
    public void isValidNewName_rejectsHyphen() {
        assertFalse("ハイフンを含む状態名は不正なはず (\\w に含まれない記号)",
                StateSketchDialogs.isValidNewName("Id-le", model, idle));
    }
}
