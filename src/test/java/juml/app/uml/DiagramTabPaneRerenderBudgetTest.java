// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code rerenderAllTabs()} がメモリ予算のために解放したタブを実体化し直さないことの
 * 回帰テスト。
 *
 * <p>{@code startRender()} は先頭で {@code renderReleased = false} に戻すため、以前は
 * テーマや Graphviz 設定を 1 回変えるだけで<b>解放済みタブが全部いっぺんに再描画へ回り</b>、
 * 予算を守るための解放が予算と無関係な操作で帳消しになっていた。解放済みタブは
 * 再フォーカス時に描き直されるので、ここでスキップしても古い見た目は残らない。</p>
 */
public class DiagramTabPaneRerenderBudgetTest {

    private static final int FIXED = 1;

    private JTabbedPane tabs;
    private DiagramTabPane pane;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            tabs.addTab("Utility", new javax.swing.JPanel());
            pane = new DiagramTabPane(tabs, FIXED, new ProjectAnalysisCache(),
                    new DiagramState(), msg -> { }, zoom -> { });
        });
    }

    /** 開いているタブ (DiagramTab) を開いた順に取り出す。 */
    private static Collection<?> openTabsOf(DiagramTabPane pane) throws Exception {
        Field f = DiagramTabPane.class.getDeclaredField("openTabs");
        f.setAccessible(true);
        return ((Map<?, ?>) f.get(pane)).values();
    }

    private static void setRenderReleased(Object tab, boolean value) throws Exception {
        Field f = tab.getClass().getDeclaredField("renderReleased");
        f.setAccessible(true);
        f.setBoolean(tab, value);
    }

    private static boolean isRenderReleased(Object tab) throws Exception {
        Field f = tab.getClass().getDeclaredField("renderReleased");
        f.setAccessible(true);
        return f.getBoolean(tab);
    }

    @Test
    public void rerenderAllTabsLeavesReleasedTabsReleased() throws Exception {
        GuiActionRunner.execute(() -> {
            pane.openPumlEditor("@startuml\nclass Kept\n@enduml\n", null);
            pane.openPumlEditor("@startuml\nclass Freed\n@enduml\n", null);
        });

        Object[] open = openTabsOf(pane).toArray();
        assertTrue("2 タブ開いていること", open.length == 2);
        Object released = open[0];
        Object live = open[1];
        GuiActionRunner.execute(() -> {
            try {
                setRenderReleased(released, true);
                setRenderReleased(live, false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });

        GuiActionRunner.execute(pane::rerenderAllTabs);

        assertTrue("解放済みタブは解放されたままであること", isRenderReleased(released));
        assertFalse("解放していないタブは従来どおり再描画されること", isRenderReleased(live));
    }
}
