// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.Setting;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link WindowStateManager} の単体テスト。
 *
 * <p>位置/サイズ/分割位置/最大化フラグの保存・復元ロジックを {@link Setting} の
 * フェイクオブジェクト（素の {@code new Setting()}）を使って headful で検証する。
 * {@link JFrame} の生成に Display が必要なため、ヘッドレス環境では
 * {@link Assume#assumeFalse} でスキップする。</p>
 *
 * <p>注: 最大化状態 ({@link JFrame#MAXIMIZED_BOTH}) は実際の JFrame に
 * {@code setExtendedState} を呼んでから {@code save} することで検証する。
 * WM（ウィンドウマネージャ）が最大化を反映しない headful 環境でも、
 * {@code setExtendedState} は API レベルで状態を書き込むため read-back できる。</p>
 */
public class WindowStateManagerTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では JFrame の生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    // -------------------------------------------------------------------------
    // (a) 保存済みの位置とサイズが正しく復元される (通常状態)
    // -------------------------------------------------------------------------

    @Test
    public void restore_setsFrameLocationFromSetting() {
        Setting setting = new Setting();
        setting.setWindowX(150);
        setting.setWindowY(200);
        setting.setWindowLocationSaved(true);

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.pack();
            WindowStateManager.restoreLocationAndDivider(f, null, setting);
            return f;
        });

        try {
            int x = GuiActionRunner.execute(() -> frame.getX());
            int y = GuiActionRunner.execute(() -> frame.getY());
            assertEquals("保存済みの windowX が復元されるべき", 150, x);
            assertEquals("保存済みの windowY が復元されるべき", 200, y);
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    @Test
    public void restore_centersFrameWhenNoSavedPosition() {
        // X/Y が -1 (未保存) の場合は setLocationRelativeTo(null) = 画面中央。
        // 実際の座標は環境依存なので「例外が発生しない」だけを検証する。
        Setting setting = new Setting(); // windowX=-1, windowY=-1 がデフォルト

        GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.pack();
            WindowStateManager.restoreLocationAndDivider(f, null, setting);
            f.dispose();
            return null;
        });
        // ここに到達すれば OK (例外なし)。
    }

    @Test
    public void restore_setsDividerLocationWhenPositive() {
        Setting setting = new Setting();
        setting.setWindowX(0);
        setting.setWindowY(0);
        setting.setMainSplitLocation(280);

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.setSize(800, 600);
            f.setVisible(false);
            return f;
        });

        try {
            JSplitPane split = GuiActionRunner.execute(() -> {
                JSplitPane sp = new JSplitPane();
                sp.setDividerLocation(100); // 事前に別の値を設定
                WindowStateManager.restoreLocationAndDivider(frame, sp, setting);
                return sp;
            });
            int divider = GuiActionRunner.execute(() -> split.getDividerLocation());
            assertEquals("mainSplitLocation が JSplitPane に反映されるべき", 280, divider);
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    @Test
    public void restore_doesNotSetDividerWhenZeroOrNegative() {
        Setting setting = new Setting(); // mainSplitLocation=-1 がデフォルト

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.setSize(800, 600);
            return f;
        });

        try {
            JSplitPane split = GuiActionRunner.execute(() -> {
                JSplitPane sp = new JSplitPane();
                sp.setDividerLocation(150); // 元の値
                WindowStateManager.restoreLocationAndDivider(frame, sp, setting);
                return sp;
            });
            int divider = GuiActionRunner.execute(() -> split.getDividerLocation());
            // mainSplitLocation<=0 のときは上書きしない (元の 150 のまま)。
            assertEquals(
                    "mainSplitLocation<=0 のとき JSplitPane の分割位置は変更されないべき",
                    150, divider);
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    // -------------------------------------------------------------------------
    // (b) save() が Setting にウィンドウ状態を正しく書き込む
    // -------------------------------------------------------------------------

    @Test
    public void save_persistsWindowPositionAndSize() {
        Setting setting = new Setting();
        boolean[] persisted = {false};

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.setSize(900, 700);
            f.setLocation(50, 60);
            f.setVisible(false);
            return f;
        });

        try {
            GuiActionRunner.execute(() -> {
                WindowStateManager.save(frame, null, setting, () -> persisted[0] = true);
                return null;
            });

            assertTrue("persist コールバックが呼ばれるべき", persisted[0]);
            assertEquals("windowX が Setting に保存されるべき", 50, setting.getWindowX());
            assertEquals("windowY が Setting に保存されるべき", 60, setting.getWindowY());
            assertEquals("windowWidth が Setting に保存されるべき", 900, setting.getWindowWidth());
            assertEquals("windowHeight が Setting に保存されるべき", 700, setting.getWindowHeight());
            assertTrue("位置を保存したら windowLocationSaved=true になるべき",
                    setting.isWindowLocationSaved());
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    @Test
    public void save_persistsDividerLocation() {
        Setting setting = new Setting();

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.setSize(800, 600);
            f.setVisible(false);
            return f;
        });

        try {
            GuiActionRunner.execute(() -> {
                JSplitPane split = new JSplitPane();
                split.setDividerLocation(300);
                WindowStateManager.save(frame, split, setting, () -> { });
                return null;
            });
            assertEquals("mainSplitLocation が Setting に保存されるべき",
                    300, setting.getMainSplitLocation());
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    @Test
    public void save_doesNotSaveSizeWhenMaximized() {
        // ウィンドウマネージャが無い環境 (Xvfb 単体等) では MAXIMIZED_BOTH が
        // 一切反映されないため、この環境依存挙動を検証できない。
        Assume.assumeTrue(
                "ウィンドウマネージャが MAXIMIZED_BOTH をサポートしない環境ではスキップ",
                Toolkit.getDefaultToolkit().isFrameStateSupported(JFrame.MAXIMIZED_BOTH));
        // 最大化時は getWidth/Height が画面全体になるため、通常時の値を上書きしない。
        Setting setting = new Setting();
        setting.setWindowWidth(800);
        setting.setWindowHeight(600);

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.setSize(800, 600);
            f.setExtendedState(f.getExtendedState() | JFrame.MAXIMIZED_BOTH);
            f.setVisible(false);
            return f;
        });

        try {
            GuiActionRunner.execute(() -> {
                WindowStateManager.save(frame, null, setting, () -> { });
                return null;
            });
            // 最大化フラグが true に設定されるべき。
            assertTrue("最大化中は windowMaximized=true が保存されるべき",
                    setting.isWindowMaximized());
            // 最大化中はサイズを上書きしない (元の 800x600 のまま)。
            assertEquals("最大化中は windowWidth を上書きしないべき", 800, setting.getWindowWidth());
            assertEquals("最大化中は windowHeight を上書きしないべき", 600, setting.getWindowHeight());
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    // -------------------------------------------------------------------------
    // (c) restore() が最大化フラグを復元する
    // -------------------------------------------------------------------------

    @Test
    public void restore_setsMaximizedStateWhenFlagIsTrue() {
        Assume.assumeTrue(
                "ウィンドウマネージャが MAXIMIZED_BOTH をサポートしない環境ではスキップ",
                Toolkit.getDefaultToolkit().isFrameStateSupported(JFrame.MAXIMIZED_BOTH));
        Setting setting = new Setting();
        setting.setWindowX(0);
        setting.setWindowY(0);
        setting.setWindowMaximized(true);

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.pack();
            WindowStateManager.restoreLocationAndDivider(f, null, setting);
            return f;
        });

        try {
            int state = GuiActionRunner.execute(() -> frame.getExtendedState());
            assertTrue("windowMaximized=true のとき MAXIMIZED_BOTH フラグが設定されるべき",
                    (state & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH);
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }

    // -------------------------------------------------------------------------
    // (d) save() の persist が失敗しても RuntimeException を外に漏らさない
    // -------------------------------------------------------------------------

    @Test
    public void save_persistFailureIsSwallowed() {
        // ベストエフォート (失敗してもアプリ終了を妨げない) の検証。
        Setting setting = new Setting();

        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame();
            f.setSize(800, 600);
            f.setVisible(false);
            return f;
        });

        try {
            GuiActionRunner.execute(() -> {
                WindowStateManager.save(frame, null, setting, () -> {
                    throw new RuntimeException("simulated IO failure");
                });
                return null;
            });
            // 例外が外に漏れずここに到達すれば OK。
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }
}
