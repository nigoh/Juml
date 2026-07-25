// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link UseCaseSketchDialogs#isValidNewId(String, UseCaseSketchModel, UseCaseNode)}
 * の id 形式チェック・重複チェックを検証する (モーダルを介さない純ロジックテスト。headless 可)。
 */
public class UseCaseSketchDialogsValidationTest {

    private UseCaseSketchModel model;
    private UseCaseNode user;
    private UseCaseNode login;

    @Before
    public void setUp() {
        model = new UseCaseSketchModel();
        user = new UseCaseNode(UseCaseNode.Kind.ACTOR, "User", null, 0, 0);
        login = new UseCaseNode(UseCaseNode.Kind.USECASE, "Login", null, 100, 0);
        model.getNodes().add(user);
        model.getNodes().add(login);
    }

    @Test
    public void isValidNewId_acceptsNewUniqueId() {
        assertTrue("未使用の妥当な id は受理されるはず",
                UseCaseSketchDialogs.isValidNewId("Logout", model, user));
    }

    @Test
    public void isValidNewId_rejectsIdUsedByAnotherNode() {
        assertFalse("既存の別要素と同じ id への変更は拒否されるはず",
                UseCaseSketchDialogs.isValidNewId("Login", model, user));
    }

    @Test
    public void isValidNewId_acceptsUnchangedOwnId() {
        assertTrue("自分自身と同じ id (リネームなし) は受理されるはず",
                UseCaseSketchDialogs.isValidNewId("User", model, user));
    }

    @Test
    public void isValidNewId_rejectsEmptyString() {
        assertFalse("空文字は id として不正なはず",
                UseCaseSketchDialogs.isValidNewId("", model, user));
    }

    @Test
    public void isValidNewId_rejectsLeadingDigit() {
        assertFalse("先頭が数字の id は不正なはず (PlantUML 識別子の境界)",
                UseCaseSketchDialogs.isValidNewId("1User", model, user));
    }

    @Test
    public void isValidNewId_rejectsInternalSpace() {
        assertFalse("空白を含む id は不正なはず",
                UseCaseSketchDialogs.isValidNewId("Us er", model, user));
    }

    @Test
    public void isValidNewId_rejectsHyphen() {
        assertFalse("ハイフンを含む id は不正なはず (\\w に含まれない記号)",
                UseCaseSketchDialogs.isValidNewId("Us-er", model, user));
    }
}
