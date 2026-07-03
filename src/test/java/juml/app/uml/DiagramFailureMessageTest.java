// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.PlantUmlRenderFailedException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramFailureMessage#reason} の境界分岐（null・メッセージ無し・長文切り詰め・
 * PlantUML 例外）を検証する。純粋な文字列操作のためヘッドレスで完結する。
 */
public class DiagramFailureMessageTest {

    @Test
    public void reason_null_returnsUnknown() {
        assertEquals("error が null なら unknown", "unknown", DiagramFailureMessage.reason(null));
    }

    @Test
    public void reason_nullMessage_returnsClassSimpleName() {
        Throwable e = new IllegalStateException(); // getMessage() == null
        assertEquals("メッセージ無しはクラス単純名を返すこと",
                "IllegalStateException", DiagramFailureMessage.reason(e));
    }

    @Test
    public void reason_longMessage_isTruncatedTo160WithEllipsis() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append('x');
        }
        String reason = DiagramFailureMessage.reason(new RuntimeException(sb.toString()));
        assertTrue("160 文字以内に切り詰められること: len=" + reason.length(),
                reason.length() <= 160);
        assertTrue("切り詰め時は末尾に省略記号が付くこと", reason.endsWith("…"));
    }

    @Test
    public void reason_shortMessage_isReturnedVerbatim() {
        assertEquals("短いメッセージはそのまま返すこと",
                "boom", DiagramFailureMessage.reason(new RuntimeException("boom")));
    }

    @Test
    public void reason_plantUmlRenderFailure_surfacesDetailMessage() {
        // 例外メッセージにリンタの原因候補が載っていれば、それをそのまま見せる
        // (原因切り分けを助けるため)。
        String detail = "PlantUML layout error (likely Smetana). "
                + "Possible cause — line 2: background color precedes [[link]]";
        String reason = DiagramFailureMessage.reason(
                new PlantUmlRenderFailedException(detail));
        assertTrue("例外メッセージの詳細を見せること: " + reason,
                reason.contains("background color precedes"));
    }

    @Test
    public void reason_plantUmlRenderFailure_emptyMessage_fallsBackToSmetana() {
        String reason = DiagramFailureMessage.reason(
                new PlantUmlRenderFailedException(""));
        assertTrue("メッセージ無しなら Smetana のフォールバック文言: " + reason,
                reason.contains("Smetana"));
    }

    // ── エラー ID 見出し / リンク (クローズド環境での転記支援) ──────────

    @Test
    public void forError_withCode_showsIdHeadlineAndLinks() {
        String html = DiagramFailureMessage.forError(
                new RuntimeException("boom"), null, juml.util.ErrorCode.UML_R002);
        assertTrue("ID が見出しとして含まれること: " + html, html.contains("UML-R002"));
        assertTrue("リファレンスへのリンクを含むこと",
                html.contains("juml-errcode:UML-R002"));
        assertTrue("詳細コピーのリンクを含むこと", html.contains("juml-copy:"));
    }

    @Test
    public void forError_withoutCode_hasNoIdLinks() {
        String html = DiagramFailureMessage.forError(new RuntimeException("boom"));
        assertTrue("ID 無しでは errcode リンクを含まないこと",
                !html.contains("juml-errcode:"));
    }

    @Test
    public void forError_noneCode_hasNoIdLinks() {
        String html = DiagramFailureMessage.forError(
                new RuntimeException("boom"), null, juml.util.ErrorCode.NONE);
        assertTrue("NONE では errcode リンクを含まないこと",
                !html.contains("juml-errcode:"));
    }
}
