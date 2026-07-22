// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.SettingManager;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 起動時の下書き復元プロンプト ({@link UmlMainFrame#promptDraftRecovery}) の
 * データ安全性を検証する GUI テスト (headless-skip)。
 *
 * <p>最重要: Esc / ウィンドウクローズ (CLOSED_OPTION) では下書きを破棄しない
 * (クラッシュ保護が最も自然な離脱操作でデータ消失を招かないため)。明示的な
 * 「いいえ」(NO_OPTION) のときだけ破棄する。</p>
 */
public class DraftRecoveryPromptTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private UmlMainFrame frame;
    private DraftStore store;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では UmlMainFrame の生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
    }

    @Before
    public void setUp() throws Exception {
        frame = GuiActionRunner.execute(() -> new UmlMainFrame(null));
        DiagramTabPane tabPane = getTabPane(frame);
        store = new DraftStore(tmp.newFolder("drafts"));
        GuiActionRunner.execute(() -> tabPane.setDraftStoreForTest(store));
    }

    @After
    public void tearDown() {
        if (frame != null) {
            GuiActionRunner.execute(frame::dispose);
        }
        SettingManager.resetForTest();
    }

    @Test
    public void escOrWindowClose_keepsDrafts() {
        store.save("PUML:untitled-1", "@startuml\nclass A\n@enduml\n", null, "A.puml");
        store.save("PUML:untitled-2", "@startuml\nclass B\n@enduml\n", null, "B.puml");
        // CLOSED_OPTION = Esc / ウィンドウの×。破棄してはならない。
        boolean asked = GuiActionRunner.execute(() ->
                frame.promptDraftRecovery(count -> JOptionPane.CLOSED_OPTION));
        assertTrue("下書きがあるので尋ねたはず", asked);
        assertEquals("Esc/クローズでは下書きを破棄しないはず", 2, store.loadAll().size());
    }

    @Test
    public void explicitNo_discardsDrafts() {
        store.save("PUML:untitled-1", "@startuml\nclass A\n@enduml\n", null, "A.puml");
        GuiActionRunner.execute(() ->
                frame.promptDraftRecovery(count -> JOptionPane.NO_OPTION));
        assertTrue("明示的な「いいえ」では下書きを破棄するはず", store.loadAll().isEmpty());
    }

    @Test
    public void noPendingDrafts_isNoOp() {
        boolean asked = GuiActionRunner.execute(() ->
                frame.promptDraftRecovery(count -> JOptionPane.YES_OPTION));
        assertFalse("下書きが無ければ尋ねないはず", asked);
    }

    private static DiagramTabPane getTabPane(UmlMainFrame frame)
            throws ReflectiveOperationException {
        Field f = UmlMainFrame.class.getDeclaredField("tabPane");
        f.setAccessible(true);
        return (DiagramTabPane) f.get(frame);
    }
}
