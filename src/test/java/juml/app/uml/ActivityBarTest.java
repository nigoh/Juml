// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ActivityBar} のアクション結線テスト。
 *
 * <p>{@link ActivityBar.Actions} の各フィールドに {@link AtomicBoolean} を仕込んだ
 * コールバックを設定して {@link ActivityBar} を構築し、対応するボタンを
 * {@link JButton#doClick()} することでコールバックが発火するかを検証する。</p>
 *
 * <p>ボタンは MaterialIcons を使った描画を持つため display が必要。
 * ヘッドレス環境では {@link Assume} でスキップする。</p>
 */
public class ActivityBarTest {

    private ActivityBar bar;
    private AtomicBoolean openProjectFired;
    private AtomicBoolean toggleSidebarFired;
    private AtomicBoolean searchFired;
    private AtomicBoolean commandPaletteFired;
    private AtomicBoolean preferencesFired;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        openProjectFired = new AtomicBoolean(false);
        toggleSidebarFired = new AtomicBoolean(false);
        searchFired = new AtomicBoolean(false);
        commandPaletteFired = new AtomicBoolean(false);
        preferencesFired = new AtomicBoolean(false);

        ActivityBar.Actions actions = new ActivityBar.Actions();
        actions.openProject = () -> openProjectFired.set(true);
        actions.toggleSidebar = () -> toggleSidebarFired.set(true);
        actions.search = () -> searchFired.set(true);
        actions.commandPalette = () -> commandPaletteFired.set(true);
        actions.preferences = () -> preferencesFired.set(true);

        bar = GuiActionRunner.execute(() -> new ActivityBar(actions));
    }

    @After
    public void tearDown() {
        bar = null;
    }

    // -------------------------------------------------------------------------
    // ボタン発火検証 (1 ボタン = 1 アクション)
    // -------------------------------------------------------------------------

    @Test
    public void openProject_buttonFiresCallback() {
        clickButtonAt(bar, 1); // FOLDER_OPEN は 2 番目のボタン（toggleSidebar の次）
        assertTrue("openProject ボタンクリックで openProject コールバックが呼ばれるはず",
                openProjectFired.get());
    }

    @Test
    public void toggleSidebar_buttonFiresCallback() {
        clickButtonAt(bar, 0); // SIDEBAR は先頭のボタン
        assertTrue("toggleSidebar ボタンクリックで toggleSidebar コールバックが呼ばれるはず",
                toggleSidebarFired.get());
    }

    @Test
    public void search_buttonFiresCallback() {
        clickButtonAt(bar, 2); // SEARCH は 3 番目のボタン
        assertTrue("search ボタンクリックで search コールバックが呼ばれるはず", searchFired.get());
    }

    @Test
    public void commandPalette_buttonFiresCallback() {
        clickButtonAt(bar, 3); // TERMINAL は 4 番目のボタン
        assertTrue("commandPalette ボタンクリックで commandPalette コールバックが呼ばれるはず",
                commandPaletteFired.get());
    }

    @Test
    public void preferences_buttonFiresCallback() {
        // preferences は SOUTH (bottom) の最初のボタン — 全体の 5 番目。
        List<JButton> buttons = GuiActionRunner.execute(() -> collectButtons(bar));
        assertEquals("ActivityBar には合計 5 本のボタンが存在するはず", 5, buttons.size());
        GuiActionRunner.execute(() -> buttons.get(4).doClick());
        assertTrue("preferences ボタンクリックで preferences コールバックが呼ばれるはず",
                preferencesFired.get());
    }

    // -------------------------------------------------------------------------
    // null アクションはボタンを生成しない
    // -------------------------------------------------------------------------

    @Test
    public void nullAction_doesNotCreateButton() {
        ActivityBar.Actions actions = new ActivityBar.Actions();
        // openProject だけ設定、他は null。
        actions.openProject = () -> { };
        ActivityBar sparse = GuiActionRunner.execute(() -> new ActivityBar(actions));
        List<JButton> buttons = GuiActionRunner.execute(() -> collectButtons(sparse));
        assertEquals("null アクションに対してはボタンが生成されないはず", 1, buttons.size());
    }

    @Test
    public void allNullActions_createsNoButtons() {
        ActivityBar.Actions empty = new ActivityBar.Actions();
        ActivityBar noButtons = GuiActionRunner.execute(() -> new ActivityBar(empty));
        List<JButton> buttons = GuiActionRunner.execute(() -> collectButtons(noButtons));
        assertTrue("全アクション null では 1 本もボタンが生成されないはず", buttons.isEmpty());
    }

    // -------------------------------------------------------------------------
    // アクション混線チェック: ボタン i の発火でボタン j (i≠j) が発火しない
    // -------------------------------------------------------------------------

    @Test
    public void distinctButtons_doNotCrossWire() {
        // toggleSidebar ボタンをクリックしたとき openProject は発火しない。
        clickButtonAt(bar, 0);
        assertTrue("toggleSidebar が発火するはず", toggleSidebarFired.get());
        org.junit.Assert.assertFalse("openProject は発火してはならない", openProjectFired.get());
    }

    // -------------------------------------------------------------------------
    // ヘルパ
    // -------------------------------------------------------------------------

    /** コンテナ内の全 JButton を深さ優先で収集する。 */
    private static List<JButton> collectButtons(Container root) {
        List<JButton> result = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JButton) {
                result.add((JButton) c);
            }
            if (c instanceof Container) {
                result.addAll(collectButtons((Container) c));
            }
        }
        return result;
    }

    /** index 番目のボタン (0 始まり) を EDT 上で doClick する。 */
    private void clickButtonAt(ActivityBar actBar, int index) {
        List<JButton> buttons = GuiActionRunner.execute(() -> collectButtons(actBar));
        assertTrue("ボタンインデックス " + index + " が存在するはず", index < buttons.size());
        GuiActionRunner.execute(() -> buttons.get(index).doClick());
    }
}
