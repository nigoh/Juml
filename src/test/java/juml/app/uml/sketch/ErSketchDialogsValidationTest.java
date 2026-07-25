// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.ErSketchModel.Entity;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ErSketchDialogs#isValidNewAlias(String, ErSketchModel, Entity)}
 * の別名 (alias) 形式チェック・重複チェックを検証する
 * (モーダルを介さない純ロジックテスト。headless 可)。
 */
public class ErSketchDialogsValidationTest {

    private ErSketchModel model;
    private Entity user;
    private Entity post;

    @Before
    public void setUp() {
        model = new ErSketchModel();
        user = new Entity("e_user", "User", 0, 0);
        post = new Entity("e_post", "Post", 100, 0);
        model.getEntities().add(user);
        model.getEntities().add(post);
    }

    @Test
    public void isValidNewAlias_acceptsNewUniqueAlias() {
        assertTrue("未使用の妥当な別名は受理されるはず",
                ErSketchDialogs.isValidNewAlias("e_comment", model, user));
    }

    @Test
    public void isValidNewAlias_rejectsAliasUsedByAnotherEntity() {
        assertFalse("既存の別エンティティと同じ別名への変更は拒否されるはず",
                ErSketchDialogs.isValidNewAlias("e_post", model, user));
    }

    @Test
    public void isValidNewAlias_acceptsUnchangedOwnAlias() {
        assertTrue("自分自身と同じ別名 (リネームなし) は受理されるはず",
                ErSketchDialogs.isValidNewAlias("e_user", model, user));
    }

    @Test
    public void isValidNewAlias_rejectsEmptyString() {
        assertFalse("空文字は別名として不正なはず",
                ErSketchDialogs.isValidNewAlias("", model, user));
    }

    @Test
    public void isValidNewAlias_rejectsLeadingDigit() {
        assertFalse("先頭が数字の別名は不正なはず (PlantUML 識別子の境界)",
                ErSketchDialogs.isValidNewAlias("1user", model, user));
    }

    @Test
    public void isValidNewAlias_rejectsInternalSpace() {
        assertFalse("空白を含む別名は不正なはず",
                ErSketchDialogs.isValidNewAlias("e user", model, user));
    }

    @Test
    public void isValidNewAlias_rejectsHyphen() {
        assertFalse("ハイフンを含む別名は不正なはず (\\w に含まれない記号)",
                ErSketchDialogs.isValidNewAlias("e-user", model, user));
    }
}
