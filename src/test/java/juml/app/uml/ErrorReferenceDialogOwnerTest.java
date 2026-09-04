// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JFrame;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt R1 で発見: ログビューアの「対処法」から開いたエラーリファレンスが、
 * ログビューアを閉じると (所有ウィンドウとして) 一緒に破棄されていた。
 * 単一インスタンスのリファレンスは最上位ウィンドウを親にして生き残ることを検証する。
 */
public class ErrorReferenceDialogOwnerTest {

    private JFrame frame;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Window 生成不可のためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @After
    public void tearDown() {
        GuiActionRunner.execute(() -> {
            ErrorReferenceDialog ref = ErrorReferenceDialog.currentForTest();
            if (ref != null) {
                ref.dispose();
            }
            LogViewerDialog lv = LogViewerDialog.currentForTest();
            if (lv != null) {
                lv.dispose();
            }
            if (frame != null) {
                frame.dispose();
            }
        });
    }

    @Test
    public void rootWindowOf_walksOwnerChainToTop() {
        Window[] r = GuiActionRunner.execute(() -> {
            frame = new JFrame();
            javax.swing.JDialog child = new javax.swing.JDialog(frame);
            javax.swing.JDialog grandChild = new javax.swing.JDialog(child);
            return new Window[] {ErrorReferenceDialog.rootWindowOf(grandChild), frame};
        });
        assertSame(r[1], r[0]);
        assertTrue(ErrorReferenceDialog.rootWindowOf(null) == null);
    }

    @Test
    public void referenceOpenedFromLogViewer_survivesClosingLogViewer() {
        GuiActionRunner.execute(() -> {
            frame = new JFrame();
            frame.setVisible(true);
            LogViewerDialog.showFor(frame);
        });
        LogViewerDialog lv = GuiActionRunner.execute(LogViewerDialog::currentForTest);
        assertNotNull(lv);
        GuiActionRunner.execute(() -> ErrorReferenceDialog.showFor(lv, "UML-R001"));
        ErrorReferenceDialog ref = GuiActionRunner.execute(ErrorReferenceDialog::currentForTest);
        assertNotNull(ref);
        GuiActionRunner.execute(lv::dispose);
        assertTrue("ログビューアを閉じてもリファレンスは生き残ること",
                GuiActionRunner.execute(ref::isDisplayable));
    }
}
