// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link SeqSketchDialogs#isValidNewParticipantName(String, SeqSketchModel, SeqParticipant)}
 * の参加者名の形式チェック・重複チェックを検証する (モーダルを介さない純ロジックテスト。headless 可)。
 */
public class SeqSketchDialogsValidationTest {

    private SeqSketchModel model;
    private SeqParticipant user;
    private SeqParticipant server;

    @Before
    public void setUp() {
        model = new SeqSketchModel();
        user = new SeqParticipant("User", SeqParticipant.Kind.ACTOR, true);
        server = new SeqParticipant("Server", SeqParticipant.Kind.PARTICIPANT, true);
        model.getParticipants().add(user);
        model.getParticipants().add(server);
    }

    @Test
    public void isValidNewParticipantName_acceptsNewUniqueName() {
        assertTrue("未使用の妥当な名前は受理されるはず",
                SeqSketchDialogs.isValidNewParticipantName("Db", model, user));
    }

    @Test
    public void isValidNewParticipantName_rejectsNameUsedByAnotherParticipant() {
        assertFalse("既存の別参加者と同じ名前への変更は拒否されるはず",
                SeqSketchDialogs.isValidNewParticipantName("Server", model, user));
    }

    @Test
    public void isValidNewParticipantName_acceptsUnchangedOwnName() {
        assertTrue("自分自身と同じ名前 (リネームなし) は受理されるはず",
                SeqSketchDialogs.isValidNewParticipantName("User", model, user));
    }

    @Test
    public void isValidNewParticipantName_rejectsEmptyString() {
        assertFalse("空文字は参加者名として不正なはず",
                SeqSketchDialogs.isValidNewParticipantName("", model, user));
    }

    @Test
    public void isValidNewParticipantName_rejectsLeadingDigit() {
        assertFalse("先頭が数字の参加者名は不正なはず (PlantUML 識別子の境界)",
                SeqSketchDialogs.isValidNewParticipantName("1User", model, user));
    }

    @Test
    public void isValidNewParticipantName_rejectsInternalSpace() {
        assertFalse("空白を含む参加者名は不正なはず",
                SeqSketchDialogs.isValidNewParticipantName("U ser", model, user));
    }

    @Test
    public void isValidNewParticipantName_acceptsDotSeparatedName() {
        // 参加者名も引用符なしで書ける範囲としてドットを許容する。
        assertTrue("ドットを含む参加者名は受理されるはず",
                SeqSketchDialogs.isValidNewParticipantName("Order.Service", model, user));
    }
}
