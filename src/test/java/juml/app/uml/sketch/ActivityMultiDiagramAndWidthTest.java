// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * アクティビティ設計器の 2 件の回帰テスト。
 *
 * <p>1 つは複数図の統合。{@code parseBlock} がどの {@code @startuml} も「図名の上書き」として
 * 飲み込むため、2 つの図が入ったファイルが未対応行ゼロ = 編集可能と判定され、GUI で 1 回
 * 編集しただけで<b>1 図へ統合された本文</b>が書き戻されていた (区切りも先頭の図名も消える)。</p>
 *
 * <p>もう 1 つはレイアウト。{@code blockWidth} が IF ダイヤ自身の幅を勘定に入れていなかったため、
 * 条件が長い IF が<b>負の X</b>に置かれ、左端が画面外へ出たままスクロールでも出せず
 * クリックもできなかった。</p>
 */
public class ActivityMultiDiagramAndWidthTest {

    private static final String TWO_DIAGRAMS =
            "@startuml Login\nstart\n:login;\nstop\n@enduml\n"
            + "@startuml Logout\nstart\n:logout;\nstop\n@enduml\n";

    @Test
    public void twoDiagramsInOneFileLockEditingInsteadOfMerging() {
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(TWO_DIAGRAMS);

        assertFalse("複数図のファイルは編集ロックされること (統合して書き戻さない)",
                r.isFullySupported());
        assertTrue("2 本目の @startuml が未対応として報告されること: " + r.unsupportedLines,
                r.unsupportedLines.stream().anyMatch(l -> l.startsWith("@startuml")));
    }

    @Test
    public void singleDiagramStaysEditable() {
        // 非退行: 図が 1 つだけのファイルは従来どおり編集できること。
        ActivitySketchCodec.ParseResult r = ActivitySketchCodec.parse(
                "@startuml Login\nstart\n:login;\nstop\n@enduml\n");

        assertTrue("単一図はロックされないこと: " + r.unsupportedLines, r.isFullySupported());
        assertEquals("Login", r.model.getDiagramName());
    }

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレスでは FontMetrics が取れないためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Test
    public void longIfConditionStaysInsideTheCanvas() throws Exception {
        ActivitySketchModel model = new ActivitySketchModel();
        ActivityNode ifNode = ActivityNode.branch(
                "userIsLoggedIn && hasValidSubscription && featureFlagEnabled?", "yes", "no");
        model.getNodes().add(ifNode);

        ActivitySketchCanvas canvas = org.assertj.swing.edt.GuiActionRunner.execute(() -> {
            ActivitySketchCanvas c = new ActivitySketchCanvas(new ActivitySketchCanvas.Listener() {
                @Override public void modelEdited() { }

                @Override public void editRequested(ActivityNode node) { }
            });
            c.setModel(model, true, java.util.List.of());
            return c;
        });

        // レイアウトは relayout() が作る。getPreferredSize() がその入口なので、
        // 矩形を読む前に必ず呼ぶ (doLayout() は relayout を駆動しない)。
        Dimension preferred = org.assertj.swing.edt.GuiActionRunner.execute(canvas::getPreferredSize);
        Rectangle b = boundsOf(canvas, ifNode);

        assertTrue("ダイヤが負の X に置かれないこと: " + b, b.x >= 0);
        assertTrue("推奨サイズがダイヤを収めること: " + preferred + " vs " + b,
                preferred.width >= b.x + b.width);
    }

    /** キャンバスが最後のレイアウトで置いたノード矩形を取り出す。 */
    private static Rectangle boundsOf(ActivitySketchCanvas canvas, ActivityNode n)
            throws Exception {
        java.lang.reflect.Field f = ActivitySketchCanvas.class.getDeclaredField("bounds");
        f.setAccessible(true);
        Object r = ((java.util.Map<?, ?>) f.get(canvas)).get(n);
        if (!(r instanceof Rectangle)) {
            throw new AssertionError("ノード矩形が無い: " + r);
        }
        return (Rectangle) r;
    }
}
