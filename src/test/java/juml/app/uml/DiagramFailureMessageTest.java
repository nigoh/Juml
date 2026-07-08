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

    // ── engineOutput: レンダリングエンジン stderr 末尾の抽出 ──────────────

    @Test
    public void engineOutput_plantUmlFailure_returnsStderrTail() {
        String stderr = "java.lang.UnsupportedOperationException\n"
                + "  at smetana.core.Macro.UNSUPPORTED(...)";
        String out = DiagramFailureMessage.engineOutput(
                new PlantUmlRenderFailedException(
                        juml.util.ErrorCode.UML_R002, "layout", -1, "", stderr),
                0);
        assertTrue("stderr 末尾をそのまま返すこと: " + out,
                out.contains("smetana.core.Macro.UNSUPPORTED"));
    }

    @Test
    public void engineOutput_nonPlantUmlError_returnsEmpty() {
        assertEquals("PlantUML 由来でなければ空文字",
                "", DiagramFailureMessage.engineOutput(new RuntimeException("boom"), 0));
        assertEquals("null でも空文字",
                "", DiagramFailureMessage.engineOutput(null, 0));
    }

    @Test
    public void engineOutput_emptyStderr_returnsEmpty() {
        assertEquals("stderr が無ければ空文字", "",
                DiagramFailureMessage.engineOutput(
                        new PlantUmlRenderFailedException("no stderr"), 0));
    }

    @Test
    public void engineOutput_longStderr_isTruncatedFromHead() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append('x');
        }
        String tail = "ROOT_CAUSE_MARKER";
        String out = DiagramFailureMessage.engineOutput(
                new PlantUmlRenderFailedException(
                        juml.util.ErrorCode.UML_R002, "layout", -1, "",
                        sb + tail),
                100);
        assertTrue("100 文字 + 省略記号以内に収まること: len=" + out.length(),
                out.length() <= 101);
        assertTrue("先頭を省略し末尾 (根本原因) を残すこと: " + out,
                out.startsWith("…") && out.endsWith(tail));
    }

    @Test
    public void forError_withStderr_showsTechnicalDetailSection() {
        String stderr = "ENGINE_STDERR_LINE";
        String html = DiagramFailureMessage.forError(
                new PlantUmlRenderFailedException(
                        juml.util.ErrorCode.UML_R002, "layout failed", -1, "", stderr),
                null, juml.util.ErrorCode.UML_R002);
        assertTrue("技術的な詳細セクションにエンジン出力を含めること: " + html,
                html.contains("ENGINE_STDERR_LINE"));
    }

    // ── fullReason: 原因チェーンの展開 ───────────────────────────────────

    @Test
    public void fullReason_includesCauseChain() {
        Throwable root = new IllegalStateException("disk full");
        Throwable wrapped = new RuntimeException("could not save", root);
        String reason = DiagramFailureMessage.fullReason(wrapped);
        assertTrue("上位メッセージを含むこと: " + reason, reason.contains("could not save"));
        assertTrue("根本原因も連結して見せること: " + reason, reason.contains("disk full"));
    }

    @Test
    public void fullReason_null_returnsUnknown() {
        assertEquals("error が null なら unknown",
                "unknown", DiagramFailureMessage.fullReason(null));
    }
}
