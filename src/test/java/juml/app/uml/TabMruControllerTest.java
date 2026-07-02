// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * {@link TabMruController} の MRU 順序と巡回ロジック検証 (ヘッドレス: オーバーレイは
 * HeadlessException で無効化されるが選択切替ロジックは動作する)。
 */
public class TabMruControllerTest {

    /** 動的 3 タブ (A,B,C) + 固定 1 タブ (X) のペインを作る。 */
    private static JTabbedPane pane(JPanel[] out) {
        JTabbedPane tabs = new JTabbedPane();
        String[] names = {"A", "B", "C", "X"};
        for (int i = 0; i < names.length; i++) {
            JPanel p = new JPanel();
            p.setName(names[i]);
            tabs.addTab(names[i], p);
            if (i < 3) {
                out[i] = p;
            }
        }
        return tabs;
    }

    private static String order(List<Component> cs) {
        StringBuilder sb = new StringBuilder();
        for (Component c : cs) {
            sb.append(c.getName());
        }
        return sb.toString();
    }

    @Test
    public void mostRecentlyActivatedComesFirst() {
        JPanel[] dyn = new JPanel[3];
        // Swing コンポーネントの生成は EDT 上で行う (EDT 規律)
        JTabbedPane tabs = GuiActionRunner.execute(() -> pane(dyn));
        TabMruController mru = new TabMruController(tabs, () -> 3);
        mru.onActivated(dyn[0], "A");
        mru.onActivated(dyn[1], "B");
        mru.onActivated(dyn[2], "C"); // 直近 = C
        // MRU 順は C, B, A。固定タブ X は含まれない。
        assertEquals("CBA", order(mru.currentMruDynamic(3)));
    }

    @Test
    public void closingDropsFromMru() {
        JPanel[] dyn = new JPanel[3];
        JTabbedPane tabs = GuiActionRunner.execute(() -> pane(dyn));
        TabMruController mru = new TabMruController(tabs, () -> 3);
        mru.onActivated(dyn[0], "A");
        mru.onActivated(dyn[1], "B");
        mru.onClosed(dyn[1]);
        // B を閉じた後、残りは MRU の A が先頭、未登録の C は索引順で補完される。
        assertEquals("AC", order(mru.currentMruDynamic(3)).replace("B", ""));
    }

    @Test
    public void cycleSelectsInMruOrder() {
        JPanel[] dyn = new JPanel[3];
        JTabbedPane tabs = GuiActionRunner.execute(() -> pane(dyn));
        TabMruController mru = new TabMruController(tabs, () -> 3);
        mru.onActivated(dyn[0], "A");
        mru.onActivated(dyn[1], "B");
        mru.onActivated(dyn[2], "C"); // MRU: C,B,A
        // Swing コンポーネントへの変異・読み取りは EDT 上で行う (EDT 規律)
        GuiActionRunner.execute(() -> tabs.setSelectedComponent(dyn[2])); // 現在 = C (MRU 先頭)
        mru.cycle(1); // 次の MRU = B
        assertEquals("B", GuiActionRunner.execute(() -> tabs.getSelectedComponent().getName()));
        mru.cycle(1); // 次 = A
        assertEquals("A", GuiActionRunner.execute(() -> tabs.getSelectedComponent().getName()));
        mru.cycle(1); // 一周して C へ
        assertEquals("C", GuiActionRunner.execute(() -> tabs.getSelectedComponent().getName()));
    }

    @Test
    public void cycleBackwardSelectsInReverseMruOrder() {
        JPanel[] dyn = new JPanel[3];
        JTabbedPane tabs = GuiActionRunner.execute(() -> pane(dyn));
        TabMruController mru = new TabMruController(tabs, () -> 3);
        mru.onActivated(dyn[0], "A");
        mru.onActivated(dyn[1], "B");
        mru.onActivated(dyn[2], "C"); // MRU: C,B,A
        GuiActionRunner.execute(() -> tabs.setSelectedComponent(dyn[2])); // 現在 = C (MRU 先頭)

        // Ctrl+Shift+Tab 相当: 逆順巡回は MRU の末尾から回り込む (floorMod)。
        mru.cycle(-1); // C → (回り込み) A
        assertEquals("A", GuiActionRunner.execute(() -> tabs.getSelectedComponent().getName()));
        mru.cycle(-1); // A → B
        assertEquals("B", GuiActionRunner.execute(() -> tabs.getSelectedComponent().getName()));
        mru.cycle(-1); // B → C
        assertEquals("C", GuiActionRunner.execute(() -> tabs.getSelectedComponent().getName()));
    }

    @Test
    public void forwardThenBackwardReturnsToStart() {
        JPanel[] dyn = new JPanel[3];
        JTabbedPane tabs = GuiActionRunner.execute(() -> pane(dyn));
        TabMruController mru = new TabMruController(tabs, () -> 3);
        mru.onActivated(dyn[0], "A");
        mru.onActivated(dyn[1], "B");
        mru.onActivated(dyn[2], "C"); // MRU: C,B,A
        GuiActionRunner.execute(() -> tabs.setSelectedComponent(dyn[2])); // C

        mru.cycle(1);  // → B
        mru.cycle(-1); // → C (往復で戻る)
        assertEquals("往復巡回で開始タブへ戻る", "C",
                GuiActionRunner.execute(() -> tabs.getSelectedComponent().getName()));
    }
}
