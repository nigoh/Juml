// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * エディタタブの自動保存 (下書き) 配線の統合テスト (GUI, headless-skip)。
 *
 * <p>復元マーク付きで開いたタブが即座に下書きへ退避されること、正常保存で下書きが
 * 消えること、下書きからの復元でエディタタブが再現されることを固定する。</p>
 */
public class DiagramTabPaneDraftTest {

    private static final int FIXED = 1;
    private static final String PUML = "@startuml\nclass Draft\n@enduml\n";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private JTabbedPane tabs;
    private DiagramTabPane pane;
    private DraftStore store;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() throws Exception {
        File draftsDir = tmp.newFolder("drafts");
        GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            tabs.addTab("Utility", new javax.swing.JPanel());
            pane = new DiagramTabPane(tabs, FIXED, new ProjectAnalysisCache(),
                    new DiagramState(), msg -> { }, zoom -> { });
            store = new DraftStore(draftsDir);
            pane.setDraftStoreForTest(store);
        });
    }

    @Test
    public void openWithMarkDirty_savesDraftImmediately() {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null, true));
        List<DraftStore.Draft> drafts = store.loadAll();
        assertEquals("復元マーク付きで開いた内容は即座に下書きへ退避されるはず",
                1, drafts.size());
        assertEquals(PUML, drafts.get(0).text);
    }

    @Test
    public void saveEditor_deletesDraft() throws Exception {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null, true));
        assertEquals(1, store.loadAll().size());
        File target = new File(tmp.getRoot(), "saved.puml");
        boolean saved = GuiActionRunner.execute(() -> pane.saveActiveEditorToForTest(target));
        assertTrue("テスト用保存経路が成功するはず", saved);
        assertTrue("正常保存後は下書きが消えるはず", store.loadAll().isEmpty());
        assertEquals(PUML, Files.readString(target.toPath()));
    }

    @Test
    public void restoreDraft_reopensEditorWithSameText() throws Exception {
        store.save("PUML:untitled-99", PUML, null, "Untitled-99.puml");
        DraftStore.Draft draft = pane.pendingDrafts().get(0);
        GuiActionRunner.execute(() -> pane.restoreDraft(draft));
        assertTrue(GuiActionRunner.execute(() -> pane.activeTabIsPumlEditor()));
        // 復元されたタブの内容を保存して確認する (内容 round-trip)。
        File target = new File(tmp.getRoot(), "restored.puml");
        assertTrue(GuiActionRunner.execute(() -> pane.saveActiveEditorToForTest(target)));
        assertEquals(PUML, Files.readString(target.toPath()));
    }

    @Test
    public void restoreDraft_reprotectsUnderNewKeyUntilSaved() {
        store.save("PUML:untitled-99", PUML, null, "Untitled-99.puml");
        DraftStore.Draft draft = pane.pendingDrafts().get(0);
        GuiActionRunner.execute(() -> pane.restoreDraft(draft));
        List<DraftStore.Draft> after = store.loadAll();
        assertEquals("復元後も保存されるまで新キーで下書き保護が続くはず", 1, after.size());
        assertTrue("旧キーの下書きは新キーへ置き換わるはず",
                !"PUML:untitled-99".equals(after.get(0).tabKey));
    }

    @Test
    public void discardAllDrafts_leavesNothing() {
        store.save("k1", PUML, null, "a");
        store.save("k2", PUML, null, "b");
        GuiActionRunner.execute(() -> pane.discardAllDrafts());
        assertTrue(store.loadAll().isEmpty());
    }
}
