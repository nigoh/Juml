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

        ActivitySketchCanvas canvas = newCanvas(model);

        // レイアウトは relayout() が作る。getPreferredSize() がその入口なので、
        // 矩形を読む前に必ず呼ぶ (doLayout() は relayout を駆動しない)。
        Dimension preferred = org.assertj.swing.edt.GuiActionRunner.execute(canvas::getPreferredSize);
        Rectangle b = boundsOf(canvas, ifNode);

        assertTrue("ダイヤが負の X に置かれないこと: " + b, b.x >= 0);
        assertTrue("推奨サイズがダイヤを収めること: " + preferred + " vs " + b,
                preferred.width >= b.x + b.width);
    }

    /**
     * 回帰: 長い分岐ラベルが画面外に置かれないこと。
     *
     * <p>幅の計算はノードと入れ子ブロックしか測っていなかった。分岐ラベルは
     * {@code thenCX - 文字幅 - 6} に描かれるので、長いラベルは負の X に行き
     * (実測 x=-197)、右側の else ラベルは preferred size を超えて末尾が切れていた
     * (右端 557 に対し推奨幅 360)。どちらもスクロールでは出せない。</p>
     */
    @Test
    public void longBranchLabelsStayInsideTheCanvas() throws Exception {
        String label = "when the user has already accepted the terms";
        ActivitySketchModel model = new ActivitySketchModel();
        ActivityNode ifNode = ActivityNode.branch("ok?", label, label);
        ifNode.getThenBranch().add(ActivityNode.action("A"));
        ifNode.ensureElseBranch().add(ActivityNode.action("B"));
        model.getNodes().add(ifNode);

        ActivitySketchCanvas canvas = newCanvas(model);
        Dimension preferred = org.assertj.swing.edt.GuiActionRunner.execute(canvas::getPreferredSize);

        int[] span = labelSpan(canvas, label);
        assertTrue("then ラベルが負の X に置かれないこと: x=" + span[0], span[0] >= 0);
        assertTrue("else ラベルの右端が推奨幅に収まること: 右端=" + span[1]
                + " 推奨幅=" + preferred.width, preferred.width >= span[1]);
    }

    /**
     * 回帰: IF を入れ子にしても幅が段ごとに倍化しないこと。
     *
     * <p>幅を「中心軸を中心とする対称な 1 つの数」で持っていたため、else の無い IF を
     * 入れ子にすると<b>左だけ</b>伸びたぶんを左右両方へ積み、段ごとに倍化していた。
     * 実測で 8 段 19,564px / 10 段 77,932px / 14 段 1,245,292px。図はほぼ空白で、
     * 横スクロールのつまみは数 px、しかも再描画のたびにその範囲を歩く。</p>
     */
    @Test
    public void nestedIfsGrowLinearlyNotGeometrically() {
        int w8 = preferredWidthOfNestedIfs(8);
        int w10 = preferredWidthOfNestedIfs(10);
        int w12 = preferredWidthOfNestedIfs(12);

        assertTrue("10 段でも常識的な幅に収まること (実測 77932px だった): " + w10, w10 < 3000);
        // 線形なら 1 段あたりの増分は一定。倍化していれば増分自体が倍になる。
        int perLevelA = (w10 - w8) / 2;
        int perLevelB = (w12 - w10) / 2;
        assertEquals("1 段あたりの増分が一定であること: " + perLevelA + " vs " + perLevelB,
                perLevelA, perLevelB);
    }

    /** {@code n} 段の入れ子 IF (else 無し) を持つ図の推奨幅。 */
    private static int preferredWidthOfNestedIfs(int n) {
        ActivitySketchModel model = new ActivitySketchModel();
        java.util.List<ActivityNode> where = model.getNodes();
        for (int i = 0; i < n; i++) {
            ActivityNode branch = ActivityNode.branch("c" + i, null, null);
            where.add(branch);
            where = branch.getThenBranch();
        }
        where.add(ActivityNode.action("A"));
        return org.assertj.swing.edt.GuiActionRunner.execute(
                () -> newCanvasOnEdt(model).getPreferredSize()).width;
    }

    /** 非退行: 入れ子の無い単純な図のレイアウトが変わらないこと。 */
    @Test
    public void aSimpleDiagramIsLaidOutUnchanged() throws Exception {
        ActivitySketchModel model = new ActivitySketchModel();
        ActivityNode start = ActivityNode.terminal(ActivityNode.Kind.START);
        ActivityNode act = ActivityNode.action("do the thing");
        model.getNodes().add(start);
        model.getNodes().add(act);

        ActivitySketchCanvas canvas = newCanvas(model);
        org.assertj.swing.edt.GuiActionRunner.execute(canvas::getPreferredSize);

        Rectangle rs = boundsOf(canvas, start);
        Rectangle ra = boundsOf(canvas, act);
        assertTrue("開始ノードが画面内にあること: " + rs, rs.x >= 0);
        assertTrue("アクションが画面内にあること: " + ra, ra.x >= 0);
        assertEquals("同じ中心軸に並ぶこと",
                rs.x + rs.width / 2, ra.x + ra.width / 2);
    }

    private static ActivitySketchCanvas newCanvasOnEdt(ActivitySketchModel model) {
        ActivitySketchCanvas c = new ActivitySketchCanvas(new ActivitySketchCanvas.Listener() {
            @Override public void modelEdited() { }

            @Override public void editRequested(ActivityNode node) { }
        });
        c.setModel(model, true, java.util.List.of());
        return c;
    }

    private static ActivitySketchCanvas newCanvas(ActivitySketchModel model) {
        return org.assertj.swing.edt.GuiActionRunner.execute(() -> newCanvasOnEdt(model));
    }

    /** 指定テキストの分岐ラベルの {@code {左端, 右端}} (見つからなければ AssertionError)。 */
    private static int[] labelSpan(ActivitySketchCanvas canvas, String text) throws Exception {
        java.lang.reflect.Field f = ActivitySketchCanvas.class.getDeclaredField("branchLabels");
        f.setAccessible(true);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        java.awt.FontMetrics fm = canvas.getFontMetrics(canvas.getFont());
        for (Object o : (java.util.List<?>) f.get(canvas)) {
            Object[] entry = (Object[]) o;
            if (!text.equals(entry[0])) {
                continue;
            }
            int x = (Integer) entry[1];
            min = Math.min(min, x);
            max = Math.max(max, x + fm.stringWidth(text));
        }
        if (min == Integer.MAX_VALUE) {
            throw new AssertionError("分岐ラベルが無い: " + text);
        }
        return new int[]{min, max};
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
