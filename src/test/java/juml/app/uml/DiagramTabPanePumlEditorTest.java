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
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 自由編集 PlantUML エディタタブ ({@link DiagramTabPane#openPumlEditor}) の振る舞いを検証する。
 *
 * <p>検証対象:
 * <ol>
 *   <li>プロジェクト未ロード (cache 未ロード) でもエディタタブが開ける</li>
 *   <li>同一ファイルの 2 回目の open は既存タブへフォーカスするだけ</li>
 *   <li>エディタタブは図種 (activeTabKind) を持たない</li>
 *   <li>編集で未保存マーク (●) が付き、保存で消える + ファイルへ書き込まれる</li>
 *   <li>閉じたエディタタブを Ctrl+Shift+T 相当で復元できる</li>
 * </ol></p>
 */
public class DiagramTabPanePumlEditorTest {

    private static final int FIXED = 2;
    private static final String PUML = "@startuml\nclass A\n@enduml\n";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

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
            tabs.addTab("Utility1", new javax.swing.JPanel());
            tabs.addTab("Utility2", new javax.swing.JPanel());
            // エディタタブはプロジェクト未ロードでも開けることを検証するため、
            // cache には一切手を加えない (isLoaded() == false)。
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
    public void openPumlEditor_worksWithoutLoadedProject() {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        int count = GuiActionRunner.execute(() -> tabs.getTabCount());
        assertEquals("cache 未ロードでもエディタタブが開けるはず", FIXED + 1, count);
        assertTrue(GuiActionRunner.execute(() -> pane.activeTabIsPumlEditor()));
        assertTrue(GuiActionRunner.execute(() -> pane.hasActiveTab()));
    }

    @Test
    public void openPumlEditor_sameFile_focusesExistingTab() throws Exception {
        File f = tmp.newFile("diagram.puml");
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, f));
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(tabs.getTabCount() - 1));
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, f));
        assertEquals("同一ファイルの 2 回目の open はタブを増やさない",
                FIXED + 1, (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertEquals("既存のエディタタブへフォーカスが戻るはず",
                0, (int) GuiActionRunner.execute(() -> tabs.getSelectedIndex()));
    }

    @Test
    public void openPumlEditor_untitledTabsGetDistinctKeys() {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        assertEquals("Untitled タブは開くたびに別タブになるはず",
                FIXED + 2, (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
    }

    @Test
    public void activeTabKind_isNullForEditorTab() {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        assertNull("エディタタブは図種を持たない",
                GuiActionRunner.execute(() -> pane.activeTabKind()));
    }

    @Test
    public void editAndSave_marksDirtyThenWritesFile() throws Exception {
        File f = tmp.newFile("edit-target.puml");
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, f));
        assertFalse("開いた直後は未保存マークが無いはず",
                GuiActionRunner.execute(() -> tabs.getTitleAt(0)).startsWith("●"));

        String edited = "@startuml\nclass B\n@enduml\n";
        GuiActionRunner.execute(() -> pane.setActiveEditorText(edited));
        assertTrue("編集後はタブに未保存マーク (●) が付くはず",
                GuiActionRunner.execute(() -> tabs.getTitleAt(0)).startsWith("●"));
        assertEquals(edited, GuiActionRunner.execute(() -> pane.activeEditorText()));

        boolean saved = GuiActionRunner.execute(() -> pane.saveActivePumlEditor(false));
        assertTrue("保存先が決まっているタブの保存はダイアログなしで成功するはず", saved);
        assertEquals("編集内容がファイルへ書き込まれるはず",
                edited, PumlEditorSupport.read(f));
        assertFalse("保存後は未保存マークが消えるはず",
                GuiActionRunner.execute(() -> tabs.getTitleAt(0)).startsWith("●"));
    }

    @Test
    public void rerenderActiveEditor_keepsTextAndTabIntact() {
        // エディタタブの F5 相当 (rerenderActiveTab) がテキストやタブ状態を壊さないこと。
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        GuiActionRunner.execute(() -> pane.rerenderActiveTab());
        assertTrue("再描画後もエディタタブのまま",
                GuiActionRunner.execute(() -> pane.activeTabIsPumlEditor()));
        assertEquals("再描画でテキストは変わらない",
                PUML, GuiActionRunner.execute(() -> pane.activeEditorText()));
        assertEquals("タブ数は増えない", FIXED + 1,
                (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
    }

    @Test
    public void editThenSave_thenReopenAfterClose_isNotDirty() {
        // 保存済みタブを閉じて再オープンすると未保存(●)は付かない (dirty 保持は未保存時のみ)。
        File f;
        try {
            f = tmp.newFile("clean.puml");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, f));
        boolean saved = GuiActionRunner.execute(() -> pane.saveActivePumlEditor(false));
        assertTrue(saved);
        GuiActionRunner.execute(() -> pane.closeActiveTab());
        GuiActionRunner.execute(() -> pane.reopenLastClosedTab());
        assertFalse("保存済みで閉じたタブの再オープンは未保存マークが付かない",
                GuiActionRunner.execute(() -> tabs.getTitleAt(0)).startsWith("●"));
    }

    @Test
    public void openPumlEditor_markDirty_showsUnsavedMark() {
        // 閉じたタブの再オープンで未保存(●)状態を復元する機構を検証する。
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null, true));
        assertTrue("markDirty=true で開いたタブは未保存マーク(●)が付くはず",
                GuiActionRunner.execute(() -> tabs.getTitleAt(0)).startsWith("●"));
    }

    @Test
    public void confirmDiscardAllEdits_noDirtyTabs_returnsTrueWithoutDialog() {
        // 未保存タブが無ければ確認ダイアログを出さず true (終了を許可) を返す。
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        assertTrue("未保存タブが無ければ終了確認は true",
                GuiActionRunner.execute(() -> pane.confirmDiscardAllEdits()));
    }

    @Test
    public void saveActivePumlEditor_returnsFalseWhenNoEditorTab() {
        boolean saved = GuiActionRunner.execute(() -> pane.saveActivePumlEditor(false));
        assertFalse("エディタタブが無いときの保存は false を返すはず", saved);
    }

    @Test
    public void closeAndReopen_restoresEditorTabWithText() {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null));
        GuiActionRunner.execute(() -> pane.closeActiveTab());
        assertEquals(FIXED, (int) GuiActionRunner.execute(() -> tabs.getTabCount()));

        GuiActionRunner.execute(() -> pane.reopenLastClosedTab());
        assertEquals("閉じたエディタタブを再オープンできるはず",
                FIXED + 1, (int) GuiActionRunner.execute(() -> tabs.getTabCount()));
        assertTrue(GuiActionRunner.execute(() -> pane.activeTabIsPumlEditor()));
        assertEquals("再オープンでテキストが復元されるはず",
                PUML, GuiActionRunner.execute(() -> pane.activeEditorText()));
    }
}
