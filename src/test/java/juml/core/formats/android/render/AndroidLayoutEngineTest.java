// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

import org.junit.Test;

import juml.core.formats.android.AndroidLayoutInfo;
import juml.core.formats.android.LayoutViewNode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AndroidLayoutEngine} の計測・配置ロジックのユニットテスト。
 * 実際の {@code layout_*} 値が寸法・位置に反映されることを検証する。
 */
public class AndroidLayoutEngineTest {

    private static final LayoutDevice DEV = new LayoutDevice(360, 640);

    private static AndroidLayoutInfo wrap(LayoutViewNode root) {
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        info.setRoot(root);
        info.setFileName("test.xml");
        return info;
    }

    private static LayoutViewNode view(String tag, String w, String h) {
        LayoutViewNode n = new LayoutViewNode(tag);
        n.setWidth(w);
        n.setHeight(h);
        return n;
    }

    private static void attr(LayoutViewNode n, String key, String value) {
        n.getExtraAttributes().put(key, value);
    }

    @Test
    public void nullLayoutReturnsNull() {
        assertNull(AndroidLayoutEngine.layout(null, DEV, null));
        assertNull(AndroidLayoutEngine.layout(new AndroidLayoutInfo(), DEV, null));
    }

    @Test
    public void matchParentRootFillsDevice() {
        LayoutViewNode root = view("LinearLayout", "match_parent", "match_parent");
        attr(root, "android:orientation", "vertical");
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        assertEquals(360, mv.getWidth(), 0.5);
        assertEquals(640, mv.getHeight(), 0.5);
        assertEquals(0, mv.getX(), 0.001);
        assertEquals(0, mv.getY(), 0.001);
    }

    @Test
    public void exactDpIsHonored() {
        LayoutViewNode root = view("FrameLayout", "200dp", "120dp");
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        assertEquals(200, mv.getWidth(), 0.001);
        assertEquals(120, mv.getHeight(), 0.001);
    }

    @Test
    public void verticalLinearStacksChildrenTopToBottom() {
        LayoutViewNode root = view("LinearLayout", "match_parent", "match_parent");
        attr(root, "android:orientation", "vertical");
        LayoutViewNode a = view("TextView", "match_parent", "40dp");
        LayoutViewNode b = view("TextView", "match_parent", "60dp");
        root.getChildren().add(a);
        root.getChildren().add(b);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        MeasuredView ma = mv.getChildren().get(0);
        MeasuredView mb = mv.getChildren().get(1);
        assertEquals(0, ma.getY(), 0.001);
        assertEquals(40, ma.getHeight(), 0.001);
        // 2 つ目は 1 つ目の真下 (40dp) に積まれる。
        assertEquals(40, mb.getY(), 0.001);
        assertEquals(60, mb.getHeight(), 0.001);
        // match_parent の子は親幅いっぱい。
        assertEquals(360, ma.getWidth(), 0.5);
    }

    @Test
    public void horizontalLinearPlacesChildrenLeftToRight() {
        LayoutViewNode root = view("LinearLayout", "match_parent", "wrap_content");
        attr(root, "android:orientation", "horizontal");
        LayoutViewNode ok = view("Button", "80dp", "48dp");
        LayoutViewNode cancel = view("Button", "80dp", "48dp");
        root.getChildren().add(ok);
        root.getChildren().add(cancel);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        MeasuredView mok = mv.getChildren().get(0);
        MeasuredView mcancel = mv.getChildren().get(1);
        assertEquals(0, mok.getX(), 0.001);
        assertEquals(80, mcancel.getX(), 0.001);
        assertTrue("cancel は ok の右", mcancel.getX() > mok.getX());
    }

    @Test
    public void verticalWeightsSplitLeftoverEvenly() {
        LayoutViewNode root = view("LinearLayout", "match_parent", "match_parent");
        attr(root, "android:orientation", "vertical");
        LayoutViewNode a = view("View", "match_parent", "0dp");
        LayoutViewNode b = view("View", "match_parent", "0dp");
        attr(a, "android:layout_weight", "1");
        attr(b, "android:layout_weight", "1");
        root.getChildren().add(a);
        root.getChildren().add(b);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        double ha = mv.getChildren().get(0).getHeight();
        double hb = mv.getChildren().get(1).getHeight();
        assertEquals("weight 1:1 は等分", ha, hb, 1.0);
        assertEquals("合計はデバイス高さ", 640, ha + hb, 2.0);
    }

    @Test
    public void horizontalWeightsSplitWidth() {
        LayoutViewNode root = view("LinearLayout", "match_parent", "48dp");
        attr(root, "android:orientation", "horizontal");
        LayoutViewNode a = view("Button", "0dp", "48dp");
        LayoutViewNode b = view("Button", "0dp", "48dp");
        attr(a, "android:layout_weight", "1");
        attr(b, "android:layout_weight", "3");
        root.getChildren().add(a);
        root.getChildren().add(b);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        double wa = mv.getChildren().get(0).getWidth();
        double wb = mv.getChildren().get(1).getWidth();
        // weight 1:3 → 概ね 90:270。
        assertEquals(90, wa, 3.0);
        assertEquals(270, wb, 3.0);
    }

    @Test
    public void marginPushesChildPosition() {
        LayoutViewNode root = view("FrameLayout", "match_parent", "match_parent");
        LayoutViewNode child = view("Button", "100dp", "48dp");
        attr(child, "android:layout_margin", "16dp");
        root.getChildren().add(child);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        MeasuredView mc = mv.getChildren().get(0);
        assertEquals(16, mc.getX(), 0.001);
        assertEquals(16, mc.getY(), 0.001);
    }

    @Test
    public void paddingInsetsChildren() {
        LayoutViewNode root = view("LinearLayout", "match_parent", "match_parent");
        attr(root, "android:orientation", "vertical");
        attr(root, "android:padding", "24dp");
        LayoutViewNode child = view("TextView", "match_parent", "40dp");
        root.getChildren().add(child);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        MeasuredView mc = mv.getChildren().get(0);
        assertEquals(24, mc.getX(), 0.001);
        assertEquals(24, mc.getY(), 0.001);
        // 幅は親幅 - 左右 padding。
        assertEquals(360 - 48, mc.getWidth(), 0.5);
    }

    @Test
    public void frameLayoutCentersWithGravity() {
        LayoutViewNode root = view("FrameLayout", "match_parent", "match_parent");
        LayoutViewNode child = view("Button", "100dp", "40dp");
        attr(child, "android:layout_gravity", "center");
        root.getChildren().add(child);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        MeasuredView mc = mv.getChildren().get(0);
        assertEquals((360 - 100) / 2.0, mc.getX(), 1.0);
        assertEquals((640 - 40) / 2.0, mc.getY(), 1.0);
    }

    @Test
    public void nestedChildrenGetAbsoluteCoordinates() {
        // 縦 LinearLayout: [タイトル TextView(40dp), 横 LinearLayout{OK, Cancel}]
        LayoutViewNode root = view("LinearLayout", "match_parent", "match_parent");
        attr(root, "android:orientation", "vertical");
        LayoutViewNode title = view("TextView", "match_parent", "40dp");
        LayoutViewNode row = view("LinearLayout", "match_parent", "wrap_content");
        attr(row, "android:orientation", "horizontal");
        LayoutViewNode ok = view("Button", "80dp", "48dp");
        LayoutViewNode cancel = view("Button", "80dp", "48dp");
        row.getChildren().add(ok);
        row.getChildren().add(cancel);
        root.getChildren().add(title);
        root.getChildren().add(row);
        MeasuredView mv = AndroidLayoutEngine.layout(wrap(root), DEV, null);
        MeasuredView mrow = mv.getChildren().get(1);
        MeasuredView mok = mrow.getChildren().get(0);
        MeasuredView mcancel = mrow.getChildren().get(1);
        // 横並び行はタイトル (40dp) の真下。
        assertEquals(40, mrow.getY(), 0.001);
        // 孫ボタンは行の絶対 y (=40) に揃う。相対 0 のままにならないこと。
        assertEquals(40, mok.getY(), 0.001);
        assertEquals(40, mcancel.getY(), 0.001);
        assertEquals(0, mok.getX(), 0.001);
        assertEquals(80, mcancel.getX(), 0.001);
    }

    @Test
    public void parsesVariousDimensionUnits() {
        assertEquals(16, AndroidLayoutEngine.parseDp("16dp"), 0.001);
        assertEquals(16, AndroidLayoutEngine.parseDp("16dip"), 0.001);
        assertEquals(24, AndroidLayoutEngine.parseDp("24px"), 0.001);
        assertEquals(12, AndroidLayoutEngine.parseDp("12sp"), 0.001);
        assertEquals(8, AndroidLayoutEngine.parseDp("8"), 0.001);
        assertEquals(0, AndroidLayoutEngine.parseDp("match_parent"), 0.001);
        assertEquals(0, AndroidLayoutEngine.parseDp(null), 0.001);
    }
}
