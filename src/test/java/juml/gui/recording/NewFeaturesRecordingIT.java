// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import juml.SettingManager;
import juml.app.uml.PumlTemplate;
import juml.app.uml.UmlMainFrame;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 3 つの新機能をアニメ GIF に録画する実装確認テスト（合否ではなく「動いて見える」記録が目的）。
 *
 * <p>対象:
 * <ol>
 *   <li>自由編集 PlantUML エディタタブ (デバウンス再描画・未保存マーク)</li>
 *   <li>GUI 図形デザイナー (Design サブタブ: クラス追加・PlantUML 同期)</li>
 *   <li>Git ビューアタブ (コミット履歴・diff・ブランチ一覧)</li>
 * </ol>
 *
 * <p>Xvfb 上で実行すること:
 * <pre>
 *   xvfb-run -a -s "-screen 0 1280x900x24" \
 *     /opt/gradle/bin/gradle test --tests 'juml.gui.recording.NewFeaturesRecordingIT'
 * </pre>
 */
public class NewFeaturesRecordingIT {

    private FrameFixture window;
    private ScreenRecorder recorder;
    private String gifName;

    @Before
    public void setUp() {
        Assume.assumeFalse("headless 環境では録画不可（xvfb-run でラップしてください）",
                GraphicsEnvironment.isHeadless());
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (recorder != null && gifName != null) {
            try {
                File gif = recorder.stopAndSave(
                        new File("/home/user/Juml/build/recordings/" + gifName + ".gif"));
                System.out.println("[NewFeaturesRecording] GIF: " + gif.getAbsolutePath()
                        + " (" + gif.length() + " bytes)");
            } catch (Exception ex) {
                System.err.println("[NewFeaturesRecording] GIF 書き出し失敗: " + ex.getMessage());
            }
        }
        if (window != null) {
            window.cleanUp();
        }
        SettingManager.resetForTest();
    }

    // =========================================================================
    // シナリオ 1: 自由編集 PlantUML エディタタブ
    // =========================================================================

    /**
     * シナリオ 1: プロジェクト未ロードで PlantUML エディタタブを開き、
     * テキスト末尾にクラスを追加してデバウンス後にプレビューが更新される様子を録る。
     * タブヘッダの未保存マーク (●) も確認できる。
     */
    @Test
    public void recordPumlEditor() throws Exception {
        gifName = "feature-1-puml-editor";
        UmlMainFrame frame = startFrame(1280, 900);

        recorder = new ScreenRecorder(frameArea(frame), 10);
        recorder.start();
        pause(400);

        // エディタタブを開く (openPumlEditorTab = private → リフレクション)
        invokeOpenPumlEditorTab(frame, PumlTemplate.CLASS.body(), null);
        pause(3500); // 初回 PlantUML 描画完了待ち

        // テキスト末尾に "class Extra" と "Example --> Extra" を追加
        // sourcePanel.setText() が DocumentListener を起動し、デバウンス (600ms) 後に再描画
        Object sourcePanel = getSourcePanelOfActiveTab(frame);
        if (sourcePanel != null) {
            GuiActionRunner.execute(() -> {
                try {
                    Method getText = sourcePanel.getClass().getMethod("getText");
                    String current = (String) getText.invoke(sourcePanel);
                    String newText = current.replace("@enduml",
                            "class Extra\nExample --> Extra\n@enduml");
                    Method setText = sourcePanel.getClass().getMethod("setText", String.class);
                    setText.invoke(sourcePanel, newText);
                } catch (Exception e) {
                    System.err.println("[Recording] setText failed: " + e.getMessage());
                }
            });
        }
        pause(2800); // デバウンス 600ms + PlantUML 描画完了待ち + 余裕
    }

    // =========================================================================
    // シナリオ 2: GUI 図形デザイナー (Design サブタブ)
    // =========================================================================

    /**
     * シナリオ 2: PlantUML エディタタブを開いて Design サブタブに切り替え、
     * 「+ Class」ボタンでクラスを追加し、PlantUML タブに戻るとテキストに反映されること。
     */
    @Test
    public void recordDesignTab() throws Exception {
        gifName = "feature-2-design-tab";
        UmlMainFrame frame = startFrame(1280, 900);

        recorder = new ScreenRecorder(frameArea(frame), 10);
        recorder.start();
        pause(400);

        invokeOpenPumlEditorTab(frame, PumlTemplate.CLASS.body(), null);
        pause(3000); // 初回描画待ち

        // Design サブタブへ切り替え
        JTabbedPane bottomTabs = getBottomTabsOfActiveTab(frame);
        if (bottomTabs == null) {
            pause(1000);
            return;
        }
        switchToDesignSubTab(bottomTabs);
        pause(1200); // キャンバス描画待ち

        // 「+ Class」ボタンを doClick() で押す
        clickFirstButton(getDesignComponent(bottomTabs));
        pause(1000); // 新クラスがキャンバスに反映されるのを録る

        // PlantUML タブへ戻る (テキストに '@pos' 行や新クラスが追加される)
        GuiActionRunner.execute(() -> bottomTabs.setSelectedIndex(0));
        pause(1800); // テキスト同期を録る
    }

    // =========================================================================
    // シナリオ 3: Git ビューアタブ
    // =========================================================================

    /**
     * シナリオ 3: Git タブを開いて空状態を見せ、Juml リポジトリ自体を
     * setRepositoryRoot() で読み込んでコミット履歴・diff・ブランチ一覧を録る。
     */
    @Test
    public void recordGitViewer() throws Exception {
        gifName = "feature-3-git-viewer";
        UmlMainFrame frame = startFrame(1280, 900);

        recorder = new ScreenRecorder(frameArea(frame), 10);
        recorder.start();
        pause(400);

        // Git タブへ移動 (末尾固定タブの最後)
        JTabbedPane mainTabs = getField(frame, "mainTabs");
        GuiActionRunner.execute(() -> mainTabs.setSelectedIndex(mainTabs.getTabCount() - 1));
        pause(800); // 空状態 (リポジトリ未選択) を録る

        // Juml リポジトリ自体を開く
        Object gitPanel = getField(frame, "gitPanel");
        invokeSetRepositoryRoot(gitPanel, new File("/home/user/Juml").getAbsoluteFile());

        // コミット履歴ロード完了を待つ
        JTable table = awaitCommitsTable(gitPanel, 15_000);
        if (table != null) {
            pause(600);
            // 最初のコミット行を選択 → diff 表示
            selectTableRow(table, 0);
            pause(2500); // diff ロードを録る

            // 「Branches & Tags」サブタブへ
            JTabbedPane gitSubTabs = findDescendant(gitPanel, JTabbedPane.class);
            if (gitSubTabs != null) {
                GuiActionRunner.execute(() -> gitSubTabs.setSelectedIndex(1));
                pause(1500);
            }
        } else {
            System.err.println("[Recording] コミット履歴のロードがタイムアウト");
            pause(2000);
        }
    }

    // =========================================================================
    // ヘルパー
    // =========================================================================

    private UmlMainFrame startFrame(int w, int h) {
        UmlMainFrame frame = GuiActionRunner.execute(() -> new UmlMainFrame(null));
        window = new FrameFixture(frame);
        window.show();
        GuiActionRunner.execute(() -> {
            frame.setSize(w, h);
            frame.setLocationRelativeTo(null);
        });
        return frame;
    }

    private static Rectangle frameArea(UmlMainFrame frame) {
        return GuiActionRunner.execute(() -> {
            return frame.getBounds();
        });
    }

    /** UmlMainFrame の private openPumlEditorTab を EDT 上でリフレクション呼び出し。 */
    private static void invokeOpenPumlEditorTab(UmlMainFrame frame, String text, File file) {
        GuiActionRunner.execute(() -> {
            try {
                Method m = frame.getClass().getDeclaredMethod(
                        "openPumlEditorTab", String.class, File.class);
                m.setAccessible(true);
                m.invoke(frame, text, file);
            } catch (Exception e) {
                System.err.println("[Recording] openPumlEditorTab: " + e.getMessage());
            }
        });
    }

    /** アクティブタブ (DiagramTab) の sourcePanel (PumlSourcePanel) を取得。 */
    private static Object getSourcePanelOfActiveTab(UmlMainFrame frame) {
        try {
            JTabbedPane mainTabs = getField(frame, "mainTabs");
            Component active = GuiActionRunner.execute(() -> mainTabs.getSelectedComponent());
            if (active == null) {
                return null;
            }
            return getField(active, "sourcePanel");
        } catch (ReflectiveOperationException e) {
            System.err.println("[Recording] getSourcePanel: " + e.getMessage());
            return null;
        }
    }

    /** アクティブタブ (DiagramTab) の bottomTabs を取得。 */
    private static JTabbedPane getBottomTabsOfActiveTab(UmlMainFrame frame) {
        try {
            JTabbedPane mainTabs = getField(frame, "mainTabs");
            Component active = GuiActionRunner.execute(() -> mainTabs.getSelectedComponent());
            if (active == null) {
                return null;
            }
            return getField(active, "bottomTabs");
        } catch (ReflectiveOperationException e) {
            System.err.println("[Recording] getBottomTabs: " + e.getMessage());
            return null;
        }
    }

    /** bottomTabs の中から Design タブへ切り替える。 */
    private static void switchToDesignSubTab(JTabbedPane bottomTabs) {
        GuiActionRunner.execute(() -> {
            for (int i = 0; i < bottomTabs.getTabCount(); i++) {
                String t = bottomTabs.getTitleAt(i);
                // "Design" (en) または "デザイン" (ja)
                if (t != null && (t.contains("Design") || t.contains("デザ"))) {
                    bottomTabs.setSelectedIndex(i);
                    return;
                }
            }
            if (bottomTabs.getTabCount() > 1) {
                bottomTabs.setSelectedIndex(1);
            }
        });
    }

    /** bottomTabs の Design タブのコンポーネント (SketchPane) を取得。 */
    private static Component getDesignComponent(JTabbedPane bottomTabs) {
        return GuiActionRunner.execute(() -> {
            for (int i = 0; i < bottomTabs.getTabCount(); i++) {
                String t = bottomTabs.getTitleAt(i);
                if (t != null && (t.contains("Design") || t.contains("デザ"))) {
                    return bottomTabs.getComponentAt(i);
                }
            }
            return bottomTabs.getTabCount() > 1 ? bottomTabs.getComponentAt(1) : null;
        });
    }

    /** コンポーネント内の最初の JButton を doClick() で押す。 */
    private static void clickFirstButton(Component root) {
        if (!(root instanceof Container)) {
            return;
        }
        Component btn = findDescendantOfType((Container) root, JButton.class);
        if (btn instanceof JButton) {
            final JButton jbtn = (JButton) btn;
            GuiActionRunner.execute(() -> {
                jbtn.doClick();
            });
        }
    }

    /** gitPanel.setRepositoryRoot(root) を EDT 上で呼び出す。 */
    private static void invokeSetRepositoryRoot(Object gitPanel, File root) {
        GuiActionRunner.execute(() -> {
            try {
                Method m = gitPanel.getClass().getMethod("setRepositoryRoot", File.class);
                m.invoke(gitPanel, root);
            } catch (Exception e) {
                System.err.println("[Recording] setRepositoryRoot: " + e.getMessage());
            }
        });
    }

    /** Git コミットテーブルがロードされるまで最大 timeoutMs 待ち、テーブルを返す。 */
    private static JTable awaitCommitsTable(Object gitPanel, long timeoutMs)
            throws InterruptedException {
        JTable table = findDescendant(gitPanel, JTable.class);
        if (table == null) {
            return null;
        }
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            int rows = GuiActionRunner.execute(() -> table.getRowCount());
            if (rows > 0) {
                return table;
            }
            Thread.sleep(300);
        }
        return null;
    }

    /** テーブルの指定行を EDT 上で選択する。 */
    private static void selectTableRow(JTable table, int row) {
        GuiActionRunner.execute(() -> {
            if (table.getRowCount() > row) {
                table.setRowSelectionInterval(row, row);
            }
        });
    }

    // =========================================================================
    // 汎用リフレクション / コンポーネント探索
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    /** コンポーネントツリーから最初の type 型インスタンスを深さ優先で探す。 */
    @SuppressWarnings("unchecked")
    private static <T extends Component> T findDescendant(Object root, Class<T> type) {
        if (!(root instanceof Component)) {
            return null;
        }
        Component c = (Component) root;
        if (type.isInstance(c)) {
            return type.cast(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                T found = findDescendant(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Container 内の最初の type 型コンポーネントを深さ優先で探す。 */
    private static <T extends Component> Component findDescendantOfType(
            Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return child;
            }
            if (child instanceof Container) {
                Component found = findDescendantOfType((Container) child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** 録画演出用の短い待ち（テスト合否には使わない）。 */
    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
