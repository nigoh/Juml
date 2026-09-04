// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt R3 で発見: 比較ダイアログの PNG 保存 / コピーが、SVG の 2 倍ラスタライズを
 * EDT 上で実行して大きな図で UI を固めていた。合成が背景スレッドで走ることを検証する。
 */
public class DiagramExportToolbarTest {

    private static List<AbstractButton> buttonsOf(Component root) {
        List<AbstractButton> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<AbstractButton> out) {
        if (c instanceof AbstractButton b) {
            out.add(b);
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, out);
            }
        }
    }

    @Test
    public void compositeSupplierRunsOffTheEventDispatchThread() throws Exception {
        AtomicBoolean onEdt = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        JComponent bar = GuiActionRunner.execute(() -> (JComponent) DiagramExport.toolbar(
                null, "demo", () -> {
                    onEdt.set(SwingUtilities.isEventDispatchThread());
                    calls.incrementAndGet();
                    return (BufferedImage) null; // 描画前 = 保存もコピーも行わない
                }));
        List<AbstractButton> buttons = buttonsOf(bar);
        assertEquals("保存とコピーの 2 ボタン", 2, buttons.size());
        AbstractButton copy = buttons.get(1);
        GuiActionRunner.execute(() -> {
            copy.doClick();
            return null;
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (calls.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals("supplier が 1 度呼ばれること", 1, calls.get());
        assertFalse("合成は EDT 以外で走ること", onEdt.get());
        // ワーカー完了後にボタンが戻ることも確認する (無効のまま残さない)。
        deadline = System.currentTimeMillis() + 5_000;
        while (!GuiActionRunner.execute(copy::isEnabled)
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue("完了後はボタンが再び押せること",
                GuiActionRunner.execute(copy::isEnabled));
    }
}
