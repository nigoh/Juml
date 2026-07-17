// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * {@link DiagramTabPane#editActiveAsPuml()} のガードロジックを検証する。
 *
 * <p>「PlantUML として編集」は生成図を編集タブへ複製する機能だが、複製しても無意味な
 * ケース (アクティブがエディタタブ / 生成図が無い) では何もせず false を返す必要がある。
 * 正の経路 (生成図 → 編集タブ複製) は描画完了 (非同期) に依存するため、ここでは
 * 決定的に判定できるガードのみを固定する。</p>
 */
public class DiagramTabPaneEditAsPumlTest {

    private static final int FIXED = 2;

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
            tabs.addTab("Utility1", new JPanel());
            tabs.addTab("Utility2", new JPanel());
            pane = new DiagramTabPane(tabs, FIXED, new ProjectAnalysisCache(),
                    new DiagramState(), msg -> { }, zoom -> { });
        });
    }

    @After
    public void tearDown() {
        GuiActionRunner.execute(() -> {
            if (tabs != null) {
                tabs.removeAll();
            }
        });
    }

    @Test
    public void returnsFalseWhenNoDiagramTabActive() {
        // ユーティリティタブしか無い状態では複製対象が無い。
        boolean forked = GuiActionRunner.execute(() -> pane.editActiveAsPuml());
        assertFalse("生成図が無いときは複製せず false", forked);
        assertEquals("タブ数は増えない", FIXED,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
    }

    @Test
    public void returnsFalseWhenActiveTabIsEditor() {
        // 自由編集エディタタブをフォーカスした状態では、エディタを更に複製しない。
        GuiActionRunner.execute(() ->
                pane.openPumlEditor("@startuml\nclass A\n@enduml\n", null));
        int before = GuiActionRunner.execute(() -> tabs.getTabCount());
        boolean forked = GuiActionRunner.execute(() -> pane.editActiveAsPuml());
        assertFalse("アクティブがエディタタブなら複製しない", forked);
        assertEquals("エディタタブは増えない", before,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
    }
}
