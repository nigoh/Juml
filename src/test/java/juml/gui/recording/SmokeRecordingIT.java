// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import juml.SettingManager;
import juml.app.uml.DiagramKind;
import juml.app.uml.DiagramTabPane;
import juml.app.uml.UmlMainFrame;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JTreeFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 回帰スモーク録画テスト（合否ではなく「動いて見える」記録が目的）。
 *
 * <p>対象シナリオ:
 * <ol>
 *   <li>アプリ起動 → ウィンドウ表示</li>
 *   <li>easypermissions サンプルをプロジェクトとして開く → ツリーにノード表示</li>
 *   <li>ツリーノードをクリック → ダイアグラムタブが開く</li>
 *   <li>図種切替（CLASS → PACKAGE）</li>
 *   <li>テーマ切替（plain テーマ適用）</li>
 * </ol>
 *
 * <p>Xvfb 上で実行すること:
 * <pre>
 *   xvfb-run -a -s "-screen 0 1280x900x24" \
 *     ./gradlew test --tests 'juml.gui.recording.SmokeRecordingIT'
 * </pre>
 */
public class SmokeRecordingIT {

    private static final File SAMPLE_PROJECT = new File(
            "src/test/resources/samples/easypermissions");

    private FrameFixture window;
    private ScreenRecorder recorder;

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
        if (recorder != null) {
            try {
                File gif = recorder.stopAndSave(
                        new File("build/recordings/smoke-regression.gif"));
                System.out.println("[SmokeRecording] GIF recorded: " + gif.getAbsolutePath()
                        + " (" + gif.length() + " bytes)");
            } catch (Exception ex) {
                System.err.println("[SmokeRecording] GIF 書き出し失敗: " + ex.getMessage());
            }
        }
        if (window != null) {
            window.cleanUp();
        }
        SettingManager.resetForTest();
    }

    @Test
    public void recordSmokeScenario() throws Exception {
        // 1. アプリ起動 → ウィンドウ表示
        UmlMainFrame frame = GuiActionRunner.execute(
                () -> new UmlMainFrame(SAMPLE_PROJECT.getAbsoluteFile()));
        window = new FrameFixture(frame);
        window.show();

        // 2. ウィンドウが可視化されてから録画開始
        Rectangle area = GuiActionRunner.execute(() -> {
            frame.setSize(1280, 900);
            frame.setLocationRelativeTo(null);
            return frame.getBounds();
        });
        recorder = new ScreenRecorder(area, 10); // 10fps
        recorder.start();

        // ---- 起動フレームをしばらく録る（800ms 演出待ち）----
        pause(800);

        // 3. プロジェクトロード完了を待つ（最大 30 秒）
        JTreeFixture tree = awaitLoadedTree();
        if (tree == null) {
            System.err.println("[SmokeRecording] ツリーのロードがタイムアウト → 以降の操作をスキップ");
            pause(500);
            return;
        }

        // ツリー表示フレームを録る
        pause(600);

        // 4. ツリーの最初のノードをクリック → ダイアグラムタブが開く
        TreePath firstPath = GuiActionRunner.execute(
                () -> findFirstLeafPath(tree.target()));
        if (firstPath != null) {
            GuiActionRunner.execute(() -> tree.target().setSelectionPath(firstPath));
            pause(400);
            // タブが開くまで待つ（最大 8 秒）
            DiagramTabPane tabPane = getTabPane(frame);
            awaitDiagramTabFocused(tabPane, 8_000);
            pause(600);
        }

        // 5. 図種切替: CLASS → PACKAGE（コントローラ経由でシミュレート）
        try {
            invokeSelectDiagramKind(frame, DiagramKind.PACKAGE);
            pause(800);
        } catch (Exception ex) {
            System.err.println("[SmokeRecording] 図種切替スキップ: " + ex.getMessage());
        }

        // 6. テーマ切替: plain テーマを適用（settingSaver 削除後の隣接パス確認）
        try {
            invokeApplyTheme(frame, "plain");
            pause(800);
        } catch (Exception ex) {
            System.err.println("[SmokeRecording] テーマ切替スキップ: " + ex.getMessage());
        }

        // 7. 元のテーマ（無し）に戻す
        try {
            invokeApplyTheme(frame, "");
            pause(600);
        } catch (Exception ex) {
            System.err.println("[SmokeRecording] テーマ復元スキップ: " + ex.getMessage());
        }

        // 最終フレームを録る
        pause(400);
    }

    // ---- ヘルパー --------------------------------------------------------

    /**
     * プロジェクトのロード完了を待つ（ツリーが 2 行以上になるまで）。
     * UmlMainFrameSwingTest の awaitLoadedTree に倣う。
     */
    private JTreeFixture awaitLoadedTree() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                JTreeFixture tree = window.tree();
                int rows = GuiActionRunner.execute(
                        () -> tree.target().getRowCount());
                if (rows >= 2) {
                    return tree;
                }
            } catch (org.assertj.swing.exception.ComponentLookupException notYet) {
                // ロード中 → リトライ
            }
            Thread.sleep(200);
        }
        return null;
    }

    /**
     * ダイアグラムタブがフォーカスされるまで待つ。
     * UmlMainFrameSwingTest.waitForDiagramTabFocused に倣う。
     */
    private static boolean awaitDiagramTabFocused(DiagramTabPane tabPane, long timeoutMs)
            throws Exception {
        if (tabPane == null) {
            return false;
        }
        Method m = tabPane.getClass().getMethod("dynamicTabFocused");
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean focused = GuiActionRunner.execute(() -> {
                try {
                    return (Boolean) m.invoke(tabPane);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            if (focused) {
                return true;
            }
            Thread.sleep(80);
        }
        return false;
    }

    /** ツリーの最初の「葉」に近いパスを返す（module / package / class のどれか）。 */
    private static TreePath findFirstLeafPath(JTree tree) {
        String[] prefixes = {"[module]", "[C]", "[I]", "[E]", "com.", "de.", "android."};
        for (String prefix : prefixes) {
            for (int i = 0; i < tree.getRowCount(); i++) {
                TreePath p = tree.getPathForRow(i);
                if (p == null) {
                    continue;
                }
                Object last = p.getLastPathComponent();
                if (last instanceof DefaultMutableTreeNode) {
                    String text = String.valueOf(
                            ((DefaultMutableTreeNode) last).getUserObject());
                    if (text.startsWith(prefix)) {
                        return p;
                    }
                }
            }
        }
        // フォールバック: 行 1（ルート直下）
        return tree.getRowCount() > 1 ? tree.getPathForRow(1) : null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static DiagramTabPane getTabPane(UmlMainFrame frame) {
        try {
            return getField(frame, "tabPane");
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    /**
     * DiagramController.selectDiagramKind を EDT 上で呼び出す（リフレクション）。
     * controller フィールドが変更されている場合は例外になり、呼び出し元でキャッチ。
     */
    private static void invokeSelectDiagramKind(UmlMainFrame frame, DiagramKind kind)
            throws ReflectiveOperationException {
        Object controller = getField(frame, "controller");
        Method m = controller.getClass().getMethod("selectDiagramKind", DiagramKind.class);
        GuiActionRunner.execute(() -> {
            try {
                m.invoke(controller, kind);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * UmlMainFrame.applyTheme を EDT 上で呼び出す（private メソッドへのリフレクション）。
     * settingSaver 削除後の隣接パス（設定/テーマ復元）を刺激する。
     */
    private static void invokeApplyTheme(UmlMainFrame frame, String theme)
            throws ReflectiveOperationException {
        Method m = frame.getClass().getDeclaredMethod("applyTheme", String.class);
        m.setAccessible(true);
        GuiActionRunner.execute(() -> {
            try {
                m.invoke(frame, theme);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** 録画演出用の短い待ち（テスト合否には使わない）。 */
    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
