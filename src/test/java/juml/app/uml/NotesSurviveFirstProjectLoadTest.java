// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;
import java.awt.Point;

import static org.junit.Assert.assertEquals;

/**
 * プロジェクト未ロードで作った付箋が、<b>最初にプロジェクトを開いたとき</b>に
 * 消えないことの回帰テスト。
 *
 * <p>プロジェクト切替では再バインドの前にレイヤを空にする。旧プロジェクトの付箋が
 * 残っていると切替先の付箋が読み込まれないためで、それ自体は正しい。しかし空にして
 * よいのは<b>旧ルートのストアへ既に保存済み</b>のときだけである。まだ一度も束ねて
 * いない付箋 (= プロジェクト未ロードで開いたエディタタブの付箋) はディスクのどこにも
 * 無いので、消すと唯一の実体が失われる。{@code setData} は履歴も消すので Ctrl+Z でも
 * 戻らない。「新規 PlantUML → 付箋でメモ → プロジェクトを開く」は正規の導線である。</p>
 */
public class NotesSurviveFirstProjectLoadTest {

    private static final int FIXED = 2;
    private static final String PUML = "@startuml\nAlice -> Bob\n@enduml\n";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private JTabbedPane tabs;
    private ProjectAnalysisCache cache;
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
            tabs.addTab("Utility1", new javax.swing.JPanel());
            tabs.addTab("Utility2", new javax.swing.JPanel());
            // プロジェクト未ロードの状態から始める (getProjectRoot() == null)。
            cache = new ProjectAnalysisCache();
            pane = new DiagramTabPane(tabs, FIXED, cache,
                    new DiagramState(), msg -> { }, zoom -> { });
        });
    }

    @After
    public void tearDown() {
        GuiActionRunner.execute(() -> {
            if (pane != null) {
                pane.notesBinder().shutdown();
            }
            if (tabs != null) {
                tabs.removeAll();
            }
        });
    }

    /** 最初のプロジェクト読込で、まだ保存先の無い付箋を捨てないこと。 */
    @Test
    public void notesMadeBeforeAnyProjectSurviveTheFirstLoad() {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        SvgPreviewPanel preview = GuiActionRunner.execute(() -> pane.activePreviewPanel());
        GuiActionRunner.execute(() -> {
            preview.notes().addNoteAt(new Point(10, 20), 1.0);
            preview.notes().getNotes().get(0).setText("あとで直す");
        });
        assertEquals("前提: 付箋が 1 件あること", 1, preview.notes().getNotes().size());

        // 初めてプロジェクトを開く。
        cache.setLoadedRootForTest(tmp.getRoot());
        GuiActionRunner.execute(() -> pane.onProjectSwitched());

        assertEquals("保存先の無い付箋を消さないこと",
                1, preview.notes().getNotes().size());
        assertEquals("あとで直す", preview.notes().getNotes().get(0).getText());
    }

    /**
     * 2 回目のプロジェクト切替でも消えないこと。
     *
     * <p>「保存済みか」を<b>保存先が割り当てられたか</b>で答えていたため、1 回目の切替で
     * 救った付箋がそのルートへ保存されないまま印だけ立ち、2 回目の切替で
     * {@code setData(empty, empty)} に消されていた — 防ごうとした失敗が 1 回分<b>遅れて</b>
     * 起きるだけだった。{@code setData} は履歴も消すので Ctrl+Z でも戻らない。
     * 引き取り時に実際に保存することで、この判定が意味を持つ。</p>
     */
    @Test
    public void notesSurviveASecondProjectSwitchToo() throws Exception {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        SvgPreviewPanel preview = GuiActionRunner.execute(() -> pane.activePreviewPanel());
        GuiActionRunner.execute(() -> {
            preview.notes().addNoteAt(new Point(10, 20), 1.0);
            preview.notes().getNotes().get(0).setText("あとで直す");
        });

        java.io.File projectA = tmp.newFolder("projectA");
        java.io.File projectB = tmp.newFolder("projectB");

        cache.setLoadedRootForTest(projectA);
        GuiActionRunner.execute(() -> pane.onProjectSwitched());
        assertEquals("1 回目の切替で残ること", 1, preview.notes().getNotes().size());

        cache.setLoadedRootForTest(projectB);
        GuiActionRunner.execute(() -> pane.onProjectSwitched());

        assertEquals("2 回目の切替でも残ること",
                1, preview.notes().getNotes().size());
        assertEquals("あとで直す", preview.notes().getNotes().get(0).getText());
    }
}
