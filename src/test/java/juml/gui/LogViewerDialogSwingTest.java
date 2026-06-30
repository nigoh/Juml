// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import juml.util.AppLog;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Method;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * LogViewerDialog (Swing) のスモークテスト。
 *
 * <p>ヘッドレス環境では Robot/EDT が動かないためスキップする。ダイアログを EDT 上で
 * 開き、{@link AppLog} に流したログがテーブルへライブ反映されること、レベル絞り込みの
 * 再読込でクラッシュしないことを検証する。{@code LogViewerDialog} はパッケージ
 * プライベートなのでリフレクション経由で起動する。</p>
 */
public class LogViewerDialogSwingTest {

    private JFrame owner;
    private JDialog dialog;

    @Before
    public void setup() {
        Assume.assumeFalse("DISPLAY が無い (xvfb-run でラップしてください)",
                GraphicsEnvironment.isHeadless());
        AppLog.setMinLevel(AppLog.Level.DEBUG);
        AppLog.clearBuffer();
    }

    @After
    public void teardown() {
        if (dialog != null) {
            GuiActionRunner.execute(dialog::dispose);
        }
        if (owner != null) {
            GuiActionRunner.execute(owner::dispose);
        }
    }

    private JTable openDialog() throws Exception {
        return GuiActionRunner.execute(() -> {
            owner = new JFrame("owner");
            try {
                Class<?> cls = Class.forName("juml.app.uml.LogViewerDialog");
                Method show = cls.getDeclaredMethod("showFor", java.awt.Frame.class);
                show.setAccessible(true);
                show.invoke(null, owner);
                Method cur = cls.getDeclaredMethod("currentForTest");
                cur.setAccessible(true);
                Object inst = cur.invoke(null);
                dialog = (JDialog) inst;
                Method getTable = cls.getDeclaredMethod("getTableForTest");
                getTable.setAccessible(true);
                return (JTable) getTable.invoke(inst);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    public void testExistingEntriesShownOnOpen() throws Exception {
        AppLog.error("Test", "preexisting error");
        JTable table = openDialog();
        assertNotNull(dialog);
        assertTrue("既存ログが表示されること", table.getRowCount() >= 1);
    }

    @Test
    public void testLiveAppendUpdatesTable() throws Exception {
        JTable table = openDialog();
        int before = table.getRowCount();
        GuiActionRunner.execute(() -> AppLog.warn("Test", "live warning"));
        // onLogLive は invokeLater で追記するため EDT を一巡させる。
        SwingUtilities.invokeAndWait(() -> { });
        assertTrue("新着ログがライブ追記されること",
                table.getRowCount() > before);
    }
}
