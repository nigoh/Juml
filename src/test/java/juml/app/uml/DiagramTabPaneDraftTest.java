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
    public void exitDiscardThenCancel_keepsDraftOfDiscardedTab() {
        // タブ A で「破棄 (NO)」→ タブ B で「キャンセル」= 終了中止。
        // A は開いたまま dirty のままなので、クラッシュ復元用の下書きも残るべき。
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null, true));
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML + "' B\n", null, true));
        assertEquals(2, store.loadAll().size());
        java.util.concurrent.atomic.AtomicInteger asked =
                new java.util.concurrent.atomic.AtomicInteger();
        boolean canExit = GuiActionRunner.execute(() ->
                pane.confirmDiscardAllEdits(label ->
                        asked.getAndIncrement() == 0
                                ? javax.swing.JOptionPane.NO_OPTION
                                : javax.swing.JOptionPane.CANCEL_OPTION));
        assertTrue("キャンセルで終了は中止されるはず", !canExit);
        assertEquals("終了が中止されたら下書きは 1 件も消えないはず",
                2, store.loadAll().size());
    }

    @Test
    public void discardAllDrafts_leavesNothing() {
        store.save("k1", PUML, null, "a");
        store.save("k2", PUML, null, "b");
        GuiActionRunner.execute(() -> pane.discardAllDrafts());
        assertTrue(store.loadAll().isEmpty());
    }

    @Test
    public void discardDrafts_deletesOnlyListedDrafts() {
        store.save("k1", PUML, null, "a");
        store.save("k2", PUML, null, "b");
        List<DraftStore.Draft> listed = store.loadAll().stream()
                .filter(d -> d.tabKey.equals("k1")).toList();
        GuiActionRunner.execute(() -> pane.discardDrafts(listed));
        List<DraftStore.Draft> remaining = store.loadAll();
        assertEquals("提示した下書きだけが消え、他は残るはず", 1, remaining.size());
        assertEquals("k2", remaining.get(0).tabKey);
    }

    @Test
    public void exitDiscard_deletesDraft() {
        // 編集ありのエディタタブを開く (markDirty=true で下書きも即時退避される)。
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null, true));
        assertEquals(1, store.loadAll().size());
        // 終了時の未保存確認で「破棄 (NO)」を選ぶ → 下書きも消え、
        // 次回起動で偽のクラッシュ復元プロンプトが出ない。
        boolean canExit = GuiActionRunner.execute(() ->
                pane.confirmDiscardAllEdits(label -> javax.swing.JOptionPane.NO_OPTION));
        assertTrue("破棄を選んだので終了は続行できるはず", canExit);
        assertTrue("破棄した編集の下書きは残らないはず", store.loadAll().isEmpty());
    }

    /**
     * 回帰: 前セッションの下書きを復元するとき、復元で開いたタブが<b>まだ復元していない
     * 別の下書きのキーを奪わない</b>こと。
     *
     * <p>{@code untitledCounter} はセッションごとに 0 から数え直すため、
     * {@code untitled-2} を先に復元すると新しいタブが {@code untitled-1} を名乗り、
     * 続けて {@code untitled-1} を復元したときの {@code delete} がいま復元したタブの
     * 生きている下書きを消していた ({@code loadAll} の順序はファイルシステム依存なので、
     * この順で復元されるかどうかは運任せだった)。</p>
     */
    @Test
    public void restoringDraftsOutOfOrderKeepsBothDrafts() {
        // 前セッションが残した 2 件を模す。
        store.save("PUML:untitled-1", "@startuml\nclass One\n@enduml\n", null, "Untitled-1");
        store.save("PUML:untitled-2", "@startuml\nclass Two\n@enduml\n", null, "Untitled-2");

        List<DraftStore.Draft> pending = store.loadAll();
        assertEquals(2, pending.size());
        // 番号の大きい方から先に復元する (最悪ケースを固定する)。
        DraftStore.Draft two = pending.stream()
                .filter(d -> "PUML:untitled-2".equals(d.tabKey)).findFirst().orElseThrow();
        DraftStore.Draft one = pending.stream()
                .filter(d -> "PUML:untitled-1".equals(d.tabKey)).findFirst().orElseThrow();

        GuiActionRunner.execute(() -> pane.restoreDraft(two));
        GuiActionRunner.execute(() -> pane.restoreDraft(one));

        List<DraftStore.Draft> after = store.loadAll();
        assertEquals("復元した 2 タブぶんの下書きが残ること: " + after.size(), 2, after.size());
        java.util.Set<String> texts = new java.util.HashSet<>();
        for (DraftStore.Draft d : after) {
            texts.add(d.text);
        }
        assertTrue("One の内容が残ること: " + texts,
                texts.contains("@startuml\nclass One\n@enduml\n"));
        assertTrue("Two の内容が残ること: " + texts,
                texts.contains("@startuml\nclass Two\n@enduml\n"));
    }

    /**
     * 回帰: 復元プロンプトで Esc を押して下書きを<b>保持した</b>あと新規タブを作っても、
     * 保持した下書きが上書きされないこと。
     *
     * <p>{@code UmlMainFrame.promptDraftRecovery} は CLOSED_OPTION (Esc / ダイアログを閉じる)
     * を「破棄しない・次回また尋ねる」と明示している。しかし予約を復元経路にだけ置いていた
     * ため、Esc の場合はカウンタが 0 のままで、次の新規 Untitled タブが {@code untitled-1} を
     * 名乗り、その自動保存がクラッシュ時の下書きを上書きしていた。利用者は一度も
     * 「破棄」を選んでいないのに作業が失われる。
     */
    @Test
    public void newUntitledTabDoesNotOverwriteARetainedDraft() {
        // 前セッションのクラッシュ下書き (復元プロンプトでは Esc = 保持を選んだ想定)。
        store.save("PUML:untitled-1", "@startuml\nclass PreviousSession\n@enduml\n",
                null, "Untitled-1.puml");

        // 復元せずに新規 Untitled タブを作り、下書きへ退避させる。
        GuiActionRunner.execute(() -> pane.openPumlEditor(
                "@startuml\nclass BrandNew\n@enduml\n", null, true));

        List<DraftStore.Draft> after = store.loadAll();
        java.util.Set<String> texts = new java.util.HashSet<>();
        for (DraftStore.Draft d : after) {
            texts.add(d.text);
        }
        assertTrue("保持したクラッシュ下書きが残ること: " + texts,
                texts.contains("@startuml\nclass PreviousSession\n@enduml\n"));
        assertTrue("新規タブの下書きも並存すること: " + texts,
                texts.contains("@startuml\nclass BrandNew\n@enduml\n"));
        assertEquals("2 件が別キーで共存すること", 2, after.size());
    }

    /**
     * 回帰: 前セッションの下書きと同じキーになるファイル紐付きタブを、開いて何も打たずに
     * 閉じただけで下書きが消えないこと。
     *
     * <p>ファイル紐付きタブのキーは {@code PUML:<絶対パス>} なので、同じ .puml のクラッシュ
     * 下書きと<b>完全に同じキー</b>になる。{@code closeTab} が無条件に delete していたため、
     * 復元プロンプトで Esc (=「破棄しない」と明示された選択肢) を選んだあと、その .puml を
     * 開いて閉じるだけで失われていた。タブ予算によるクリーンタブの自動クローズなら、
     * 利用者の操作すら要らずに同じ削除が起きる。</p>
     */
    @Test
    public void closingACleanFileTabKeepsAnotherSessionsDraft() throws Exception {
        File f = tmp.newFile("shared.puml");
        Files.write(f.toPath(), "@startuml\nclass OnDisk\n@enduml\n".getBytes("UTF-8"));
        String crashed = "@startuml\nclass CrashedEdits\n@enduml\n";
        store.save("PUML:" + f.getAbsolutePath(), crashed, f, "shared.puml");
        assertEquals(1, store.loadAll().size());

        // 復元せずに同じファイルを開き、何も打たずに閉じる。
        GuiActionRunner.execute(() -> pane.openPumlEditor("@startuml\nclass OnDisk\n@enduml\n",
                f, false));
        GuiActionRunner.execute(() -> pane.closeActiveTab());

        List<DraftStore.Draft> after = store.loadAll();
        assertEquals("保持した下書きが残ること", 1, after.size());
        assertEquals(crashed, after.get(0).text);
    }

    /**
     * 非退行: 自分で書いた下書きは、閉じるときにきちんと消えること。
     * (dirty なタブを closeActiveTab で閉じるとモーダル確認が出るため、
     * 既存テストと同じく confirmDiscardAllEdits の「破棄」経路で閉じる。)
     */
    @Test
    public void closingATabThatWroteItsOwnDraftStillDeletesIt() {
        GuiActionRunner.execute(() -> pane.openPumlEditor(PUML, null, true));
        assertEquals(1, store.loadAll().size());

        boolean canExit = GuiActionRunner.execute(() ->
                pane.confirmDiscardAllEdits(label -> javax.swing.JOptionPane.NO_OPTION));

        assertTrue(canExit);
        assertTrue("自分の下書きは消えること", store.loadAll().isEmpty());
    }

    /**
     * 回帰: 保存経路も「自分が書いた下書きだけ」を消すこと。
     *
     * <p>閉じる経路には所有権の判定を入れたのに保存経路が無条件のままだったため、
     * 同じ下書きが「タブを閉じれば残る / Ctrl+S を押せば消える」と食い違っていた。
     * 復元プロンプトで Esc (=保持) を選んだあと、その .puml を開いて保存するだけで、
     * 利用者が一度も破棄を選んでいない未保存の作業が失われる。</p>
     */
    @Test
    public void savingACleanFileTabKeepsAnotherSessionsDraft() throws Exception {
        File f = tmp.newFile("saved.puml");
        Files.write(f.toPath(), "@startuml\nclass OnDisk\n@enduml\n".getBytes("UTF-8"));
        String crashed = "@startuml\nclass CrashedEdits\n@enduml\n";
        store.save("PUML:" + f.getAbsolutePath(), crashed, f, "saved.puml");

        GuiActionRunner.execute(() -> pane.openPumlEditor("@startuml\nclass OnDisk\n@enduml\n",
                f, false));
        boolean saved = GuiActionRunner.execute(() -> pane.closeActiveTabSavingToForTest(f));

        assertTrue("保存自体は成功すること", saved);
        List<DraftStore.Draft> after = store.loadAll();
        assertEquals("保持した下書きが残ること: " + after.size(), 1, after.size());
        assertEquals(crashed, after.get(0).text);
    }
}
