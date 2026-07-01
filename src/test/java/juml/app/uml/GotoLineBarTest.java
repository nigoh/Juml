// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link GotoLineBar} の commit()/activate()/close() を headless で検証する。
 *
 * <p>{@link javax.swing.JTextField} と {@link javax.swing.JPanel} はネイティブピアが不要なため
 * ヘッドレス環境でも生成できる。{@code commit()} は private なのでリフレクションで呼び出す。
 * {@code onJump} (IntConsumer) の呼び出しを AtomicInteger でカウントして検証する。</p>
 */
public class GotoLineBarTest {

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** GotoLineBar の private field "field" (JTextField) を返す。 */
    private static javax.swing.JTextField getTextField(GotoLineBar bar) throws Exception {
        java.lang.reflect.Field f = GotoLineBar.class.getDeclaredField("field");
        f.setAccessible(true);
        return (javax.swing.JTextField) f.get(bar);
    }

    /** GotoLineBar の private method "commit()" を呼び出す。 */
    private static void invokeCommit(GotoLineBar bar) throws Exception {
        Method m = GotoLineBar.class.getDeclaredMethod("commit");
        m.setAccessible(true);
        m.invoke(bar);
    }

    // -------------------------------------------------------------------------
    // テスト: 正常な行番号入力で onJump が呼ばれる
    // -------------------------------------------------------------------------

    @Test
    public void commit_withValidLineNumber_invokesOnJump() throws Exception {
        List<Integer> received = new ArrayList<>();
        IntConsumer onJump = received::add;
        GotoLineBar bar = new GotoLineBar(onJump, null);

        getTextField(bar).setText("42");
        invokeCommit(bar);

        assertEquals("正常な行番号で onJump が 1 回呼ばれること", 1, received.size());
        assertEquals("受け取った行番号が 42 であること", 42, (int) received.get(0));
    }

    // -------------------------------------------------------------------------
    // テスト: 非数値入力で onJump が呼ばれない
    // -------------------------------------------------------------------------

    @Test
    public void commit_withNonNumericInput_doesNotInvokeOnJump() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        GotoLineBar bar = new GotoLineBar(n -> callCount.incrementAndGet(), null);

        getTextField(bar).setText("abc");
        invokeCommit(bar);

        assertEquals("非数値入力では onJump が呼ばれないこと", 0, callCount.get());
    }

    // -------------------------------------------------------------------------
    // テスト: 空文字列入力で onJump が呼ばれない
    // -------------------------------------------------------------------------

    @Test
    public void commit_withEmptyInput_doesNotInvokeOnJump() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);
        GotoLineBar bar = new GotoLineBar(n -> callCount.incrementAndGet(), null);

        getTextField(bar).setText("");
        invokeCommit(bar);

        assertEquals("空文字列入力では onJump が呼ばれないこと", 0, callCount.get());
    }

    // -------------------------------------------------------------------------
    // テスト: activate() 後は isVisible() == true
    // -------------------------------------------------------------------------

    @Test
    public void activate_makesBarVisible() {
        GotoLineBar bar = new GotoLineBar(n -> { }, null);

        assertFalse("初期状態は非表示であること", bar.isVisible());
        bar.activate(1, 100);
        assertTrue("activate() 後は表示されること", bar.isVisible());
    }

    // -------------------------------------------------------------------------
    // テスト: activate() がフィールドに現在行番号を設定する
    // -------------------------------------------------------------------------

    @Test
    public void activate_setsCurrentLineInTextField() throws Exception {
        GotoLineBar bar = new GotoLineBar(n -> { }, null);

        bar.activate(55, 200);
        String text = getTextField(bar).getText();
        assertEquals("activate() でテキストフィールドに現在行番号がセットされること", "55", text);
    }

    // -------------------------------------------------------------------------
    // テスト: close() 後は isVisible() == false
    // -------------------------------------------------------------------------

    @Test
    public void close_hidesBar() throws Exception {
        GotoLineBar bar = new GotoLineBar(n -> { }, null);

        bar.activate(1, 50);
        assertTrue("close() 前は表示されていること", bar.isVisible());

        Method closeMethod = GotoLineBar.class.getDeclaredMethod("close");
        closeMethod.setAccessible(true);
        closeMethod.invoke(bar);

        assertFalse("close() 後は非表示になること", bar.isVisible());
    }

    // -------------------------------------------------------------------------
    // テスト: commit() は close() を呼ぶ (commit 後は非表示)
    // -------------------------------------------------------------------------

    @Test
    public void commit_closesBarAfterJump() throws Exception {
        GotoLineBar bar = new GotoLineBar(n -> { }, null);

        bar.activate(1, 100);
        getTextField(bar).setText("10");
        invokeCommit(bar);

        assertFalse("commit() 後は非表示になること (内部で close() を呼ぶ)", bar.isVisible());
    }

    // -------------------------------------------------------------------------
    // テスト: onLayoutChange が activate / close で呼ばれる
    // -------------------------------------------------------------------------

    @Test
    public void activate_andClose_invokeOnLayoutChange() throws Exception {
        AtomicInteger layoutCount = new AtomicInteger(0);
        GotoLineBar bar = new GotoLineBar(n -> { }, layoutCount::incrementAndGet);

        bar.activate(1, 50);
        int afterActivate = layoutCount.get();
        assertTrue("activate() で onLayoutChange が呼ばれること", afterActivate >= 1);

        Method closeMethod = GotoLineBar.class.getDeclaredMethod("close");
        closeMethod.setAccessible(true);
        closeMethod.invoke(bar);
        assertTrue("close() で onLayoutChange が呼ばれること",
                layoutCount.get() > afterActivate);
    }

    // -------------------------------------------------------------------------
    // テスト: 負の行番号でも onJump は呼ばれる (バリデーションは呼び出し側の責務)
    // -------------------------------------------------------------------------

    @Test
    public void commit_withNegativeNumber_invokesOnJump() throws Exception {
        List<Integer> received = new ArrayList<>();
        GotoLineBar bar = new GotoLineBar(received::add, null);

        getTextField(bar).setText("-5");
        invokeCommit(bar);

        assertEquals("負の行番号でも onJump は呼ばれること", 1, received.size());
        assertEquals("受け取った行番号が -5 であること", -5, (int) received.get(0));
    }
}
