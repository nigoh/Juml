// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PlantUmlSvgRenderer.SvgTextItem;
import org.junit.Test;

import java.awt.geom.Rectangle2D;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramFindBar} のインクリメンタル検索ロジックを headless で検証する。
 *
 * <p>{@link SvgPreviewPanel} は匿名サブクラスでスパイ化し、
 * {@code clearSearchHighlights} / {@code setSearchHighlights} の呼び出し回数と
 * 渡された引数を記録する。{@code field} (private) はリフレクションで取得して
 * テキストを直接投入し、DocumentListener 経由で {@code run()} を駆動する。</p>
 */
public class DiagramFindBarTest {

    // -------------------------------------------------------------------------
    // スパイ実装
    // -------------------------------------------------------------------------

    /** SvgPreviewPanel のスパイ: clearSearchHighlights / setSearchHighlights を記録する。 */
    private static final class SpyPanel extends SvgPreviewPanel {
        int clearCount;
        final List<List<Rectangle2D>> setHitArgs = new ArrayList<>();
        final List<Integer> setIdxArgs = new ArrayList<>();

        @Override
        public void clearSearchHighlights() {
            clearCount++;
        }

        @Override
        public void setSearchHighlights(List<Rectangle2D> hits, int current) {
            setHitArgs.add(new ArrayList<>(hits));
            setIdxArgs.add(current);
        }
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** DiagramFindBar の private field "field" (JTextField) を取得する。 */
    private static javax.swing.JTextField getTextField(DiagramFindBar bar) throws Exception {
        Field f = DiagramFindBar.class.getDeclaredField("field");
        f.setAccessible(true);
        return (javax.swing.JTextField) f.get(bar);
    }

    /**
     * JTextField にテキストをセットして DocumentListener を発火させる。
     * EDT ではなくテストスレッドから直接呼ぶため invokeAndWait で包まない
     * (JTextField のモデル操作は EDT 外でも DocumentListener を同期呼び出しする)。
     */
    private static void setQuery(DiagramFindBar bar, String query) throws Exception {
        getTextField(bar).setText(query);
    }

    /** SpyPanel にテキスト要素を設定するショートカット。 */
    private static SvgTextItem item(String text) {
        return new SvgTextItem(text, 0, 0, text.length() * 7.0, 16);
    }

    // -------------------------------------------------------------------------
    // テスト: ゼロヒット時は clearSearchHighlights が呼ばれる
    // -------------------------------------------------------------------------

    @Test
    public void run_withNoMatchingItems_callsClearSearchHighlights() throws Exception {
        SpyPanel spy = new SpyPanel();
        spy.setTextItems(Arrays.asList(item("Alpha"), item("Beta")));
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        setQuery(bar, "ZZZ"); // ヒットなし

        assertTrue("ゼロヒット時は clearSearchHighlights が 1 回以上呼ばれること",
                spy.clearCount >= 1);
        assertTrue("ゼロヒット時は setSearchHighlights が呼ばれないこと",
                spy.setHitArgs.isEmpty());
    }

    // -------------------------------------------------------------------------
    // テスト: 空クエリでは clearSearchHighlights が呼ばれる
    // -------------------------------------------------------------------------

    @Test
    public void run_withEmptyQuery_callsClearSearchHighlights() throws Exception {
        SpyPanel spy = new SpyPanel();
        spy.setTextItems(Arrays.asList(item("Foo"), item("Bar")));
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        setQuery(bar, "Foo"); // まずヒットさせる
        int clearBefore = spy.clearCount;

        setQuery(bar, ""); // 空にする
        assertTrue("空クエリで clearSearchHighlights が追加呼び出しされること",
                spy.clearCount > clearBefore);
    }

    // -------------------------------------------------------------------------
    // テスト: 複数ヒット時に index=0 でフォーカスする
    // -------------------------------------------------------------------------

    @Test
    public void run_withMultipleHits_focusesFirstHitAtIndexZero() throws Exception {
        SpyPanel spy = new SpyPanel();
        spy.setTextItems(Arrays.asList(item("foo"), item("foo2"), item("bar")));
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        setQuery(bar, "foo");

        assertFalse("複数ヒット時は setSearchHighlights が呼ばれること",
                spy.setHitArgs.isEmpty());
        int lastIdx = spy.setIdxArgs.get(spy.setIdxArgs.size() - 1);
        assertEquals("最初のヒットにフォーカスするため index は 0 であること", 0, lastIdx);
        int lastHitCount = spy.setHitArgs.get(spy.setHitArgs.size() - 1).size();
        assertTrue("ヒット数が 1 以上であること", lastHitCount >= 1);
    }

    // -------------------------------------------------------------------------
    // テスト: move() が最後のヒットから次で先頭へ折り返す
    // -------------------------------------------------------------------------

    @Test
    public void move_wrapsAroundFromLastHitToFirst() throws Exception {
        SpyPanel spy = new SpyPanel();
        spy.setTextItems(Arrays.asList(item("x"), item("x"), item("x")));
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        setQuery(bar, "x"); // 3 ヒット, index=0

        // move(+1) を 2 回呼んで index=2 へ
        Field moveField = DiagramFindBar.class.getDeclaredField("index");
        moveField.setAccessible(true);

        // DiagramFindBar.move() は private なのでリフレクションで呼ぶ
        java.lang.reflect.Method moveMethod =
                DiagramFindBar.class.getDeclaredMethod("move", int.class);
        moveMethod.setAccessible(true);
        moveMethod.invoke(bar, 1); // index=1
        moveMethod.invoke(bar, 1); // index=2

        int idxAtLast = (int) moveField.get(bar);
        assertEquals("最後のヒット (index=2) になっていること", 2, idxAtLast);

        moveMethod.invoke(bar, 1); // 折り返し → index=0
        int idxAfterWrap = (int) moveField.get(bar);
        assertEquals("最後からさらに +1 すると先頭 (index=0) へ折り返すこと", 0, idxAfterWrap);
    }

    // -------------------------------------------------------------------------
    // テスト: 大量ヒット時の上限ガード (5000 件)
    // -------------------------------------------------------------------------

    @Test
    public void run_withMassiveHits_limitsToFiveThousand() throws Exception {
        SpyPanel spy = new SpyPanel();
        List<SvgTextItem> items = new ArrayList<>();
        for (int i = 0; i < 6000; i++) {
            items.add(item("match" + i)); // すべて "match" を含む
        }
        spy.setTextItems(items);
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        setQuery(bar, "match");

        assertFalse("大量ヒットでも setSearchHighlights が呼ばれること",
                spy.setHitArgs.isEmpty());
        int hitCount = spy.setHitArgs.get(spy.setHitArgs.size() - 1).size();
        assertTrue("ヒット上限 (5001件) を超えないこと: actual=" + hitCount,
                hitCount <= 5001);
    }

    // -------------------------------------------------------------------------
    // テスト: activate() / close() の visibility
    // -------------------------------------------------------------------------

    @Test
    public void activate_makesBarVisible() throws Exception {
        SpyPanel spy = new SpyPanel();
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        assertFalse("初期状態は非表示", bar.isVisible());
        bar.activate();
        assertTrue("activate() 後は表示されること", bar.isVisible());
    }

    @Test
    public void close_hidesBarAndClearsHighlights() throws Exception {
        SpyPanel spy = new SpyPanel();
        spy.setTextItems(Arrays.asList(item("hello")));
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        bar.activate();
        setQuery(bar, "hello");
        assertTrue("close() 前はヒットがある", !spy.setHitArgs.isEmpty());

        int clearBefore = spy.clearCount;
        bar.close();

        assertFalse("close() 後は非表示", bar.isVisible());
        assertTrue("close() で clearSearchHighlights が呼ばれること",
                spy.clearCount > clearBefore);
    }

    // -------------------------------------------------------------------------
    // テスト: reset() で状態を完全クリアする
    // -------------------------------------------------------------------------

    @Test
    public void reset_clearsQueryAndHighlights() throws Exception {
        SpyPanel spy = new SpyPanel();
        spy.setTextItems(Arrays.asList(item("test")));
        DiagramFindBar bar = new DiagramFindBar(spy, null);

        bar.activate();
        setQuery(bar, "test");
        int clearBefore = spy.clearCount;

        bar.reset();

        assertFalse("reset() 後は非表示", bar.isVisible());
        assertTrue("reset() で clearSearchHighlights が呼ばれること",
                spy.clearCount > clearBefore);
        String currentText = getTextField(bar).getText();
        assertTrue("reset() でフィールドが空になること",
                currentText == null || currentText.isEmpty());
    }
}
