// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.ErSketchModel.Entity;

import org.junit.Before;
import org.junit.Test;

import javax.swing.table.DefaultTableModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test
    public void isValidNewAlias_acceptsJapaneseAlias() {
        // 実機 (PlantUML 1.2026.6) は entity ユーザ をそのまま描画できる。
        assertTrue("日本語の別名は受理されるはず",
                ErSketchDialogs.isValidNewAlias("ユーザ", model, user));
    }

    @Test
    public void firstUnwritableColumn_acceptsIdentifierNames() {
        assertNull(ErSketchDialogs.firstUnwritableColumn(
                columnsOf("id", "氏名", "created_at")));
    }

    @Test
    public void firstUnwritableColumn_rejectsUnderscoreDivider() {
        // 回帰: __ は識別子としては妥当なので識別子チェックだけでは通ってしまうが、
        // 区切りトークンでもあるため、型を空にすると書き出し行が仕切りと同形になり
        // 読み直しで列が消える (警告も編集ロックも無い)。
        assertEquals("__", ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "__")));
        assertEquals("____", ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "____")));
        assertNull("単独の _ は区切りにならないので受理すること",
                ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "_")));
        assertNull("___ (奇数個) も区切りにならないので受理すること",
                ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "___")));
    }

    @Test
    public void firstUnwritableColumn_rejectsDividerLookalikes() {
        // 回帰: 区切り線と同じ名前は書き出した瞬間に PK ブロックの仕切りとして
        // 読み直され、警告も編集ロックも無いまま列が消えていた。
        assertEquals("--", ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "--")));
        assertEquals("==", ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "==")));
        assertEquals("..", ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "..")));
    }

    @Test
    public void firstUnwritableColumn_rejectsNamesThatBreakTheColumnBlock() {
        assertEquals("}", ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "}")));
        assertEquals("first name",
                ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "first name")));
    }

    @Test
    public void firstUnwritableColumn_ignoresBlankRows() {
        // 空行は applyColumns が捨てるだけなので拒否理由にはしない。
        assertNull(ErSketchDialogs.firstUnwritableColumn(columnsOf("id", "  ")));
    }

    /** 列テーブル (PK / 名前 / 型) を検証用に組み立てる。 */
    private static DefaultTableModel columnsOf(String... names) {
        DefaultTableModel m = new DefaultTableModel(
                new Object[]{"PK", "Column", "Type"}, 0);
        for (String n : names) {
            m.addRow(new Object[]{Boolean.FALSE, n, "int"});
        }
        return m;
    }
}
