// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import juml.SettingManager;
import juml.app.uml.DiagramKind;
import juml.app.uml.DiagramTabPane;
import juml.app.uml.UmlMainFrame;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JToggleButtonFixture;
import org.assertj.swing.fixture.JTreeFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;

/**
 * README 掲載用のデモ GIF を録画するテスト（合否ではなく「動いて見える」記録が目的）。
 *
 * <p>Juml 自身のソースツリー ({@code src/main/java/juml/core/formats/java}、
 * {@code java} / {@code java.jp} の 2 パッケージ・14 クラス) をプロジェクトとして開き、
 * 「読み込み → 既定の Package 図表示 → ツリーでサブパッケージを選択して Class 図に切替
 * → ツールバーで Inheritance / Package / Class 図種を切り替える」様子を記録する。
 *
 * <p>あえて小さめの実サブツリーを選んでいる。全体図の Class / Inheritance には
 * 「クラス数が多いプロジェクトへの警告ダイアログ」(閾値 40) があるため、録画を
 * 止めないよう対象クラス数をそれ未満に抑えている。
 *
 * <p>Xvfb 上で実行すること:
 * <pre>
 *   xvfb-run -a -s "-screen 0 1280x900x24" \
 *     gradle test --tests 'juml.gui.recording.ReadmeDemoRecordingIT'
 * </pre>
 */
public class ReadmeDemoRecordingIT {

    /** 題材: Juml 自身のソースの一部 (java パーサ実装, 14 クラス / 2 パッケージ)。 */
    private static final File DEMO_PROJECT = new File(
            "src/main/java/juml/core/formats/java");

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
                        new File("build/recordings/readme-demo.gif"));
                System.out.println("[ReadmeDemoRecording] GIF recorded: "
                        + gif.getAbsolutePath() + " (" + gif.length() + " bytes)");
            } catch (Exception ex) {
                System.err.println("[ReadmeDemoRecording] GIF 書き出し失敗: " + ex.getMessage());
            }
        }
        if (window != null) {
            window.cleanUp();
        }
        SettingManager.resetForTest();
    }

    @Test
    public void recordReadmeDemoScenario() throws Exception {
        Assume.assumeTrue("題材ディレクトリが見つからない: " + DEMO_PROJECT.getAbsolutePath(),
                DEMO_PROJECT.isDirectory());

        // 1. アプリ起動 → プロジェクトを渡して構築
        UmlMainFrame frame = GuiActionRunner.execute(
                () -> new UmlMainFrame(DEMO_PROJECT.getAbsoluteFile()));
        window = new FrameFixture(frame);
        window.show();

        Rectangle area = GuiActionRunner.execute(() -> {
            frame.setSize(1000, 700);
            frame.setLocationRelativeTo(null);
            return frame.getBounds();
        });
        recorder = new ScreenRecorder(area, 6); // 6fps: GIF 肥大を抑える
        recorder.start();

        JTabbedPane mainTabs = getField(frame, "mainTabs");

        // 起動フレームを少し録る
        pause(500);

        // 2. プロジェクトロード完了を待つ（既定タブとして Package 図が自動で開く）
        JTreeFixture tree = awaitLoadedTree();
        if (tree == null) {
            System.err.println(
                    "[ReadmeDemoRecording] ツリーのロードがタイムアウト → 以降の操作をスキップ");
            pause(500);
            return;
        }
        // 初回の描画は PlantUML/Graphviz のウォームアップで数秒かかることがあるため、
        // 固定 sleep ではなく「描画中スピナーが消えるまで」を期限付きでポーリングする。
        awaitRenderIdle(mainTabs, 15_000);
        pause(900); // 完成した Package 図を少し見せる

        // 3. ツリーでサブパッケージ (java.jp) を選択 → 対応する Class 図タブが開く
        TreePath jpPackagePath = GuiActionRunner.execute(
                () -> findPathByPrefix(tree.target(), "juml.core.formats.java.jp"));
        DiagramTabPane tabPane = getTabPane(frame);
        if (jpPackagePath != null) {
            GuiActionRunner.execute(() -> tree.target().setSelectionPath(jpPackagePath));
            awaitDiagramTabFocused(tabPane, 8_000);
            awaitRenderIdle(mainTabs, 10_000);
            pause(900); // scoped Class 図の描画結果を見せる
        } else {
            System.err.println(
                    "[ReadmeDemoRecording] java.jp パッケージノードが見つからない → 図種切替のみ続行");
        }

        // 4. 図種ツールバー: Class → Inheritance → Package → Class と実クリックで切り替える
        clickDiagramToggleAndAwait(frame, mainTabs, DiagramKind.INHERITANCE);
        clickDiagramToggleAndAwait(frame, mainTabs, DiagramKind.PACKAGE);
        clickDiagramToggleAndAwait(frame, mainTabs, DiagramKind.CLASS);

        // 最終フレームを録る
        pause(400);
    }

    // ---- ヘルパー --------------------------------------------------------

    /**
     * プロジェクトのロード完了 (ツリーが表示され、ルート以下 2 行以上展開) を待つ。
     * UmlMainFrameSwingTest.awaitLoadedTree に倣う。
     */
    private JTreeFixture awaitLoadedTree() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                JTreeFixture t = window.tree();
                int rows = GuiActionRunner.execute(() -> t.target().getRowCount());
                if (rows >= 2) {
                    return t;
                }
            } catch (org.assertj.swing.exception.ComponentLookupException notYet) {
                // ロード中 → リトライ
            }
            Thread.sleep(200);
        }
        return null;
    }

    /** ダイアグラムタブがフォーカスされるまで待つ。UmlMainFrameSwingTest に倣う。 */
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

    /**
     * アクティブタブに「描画中」スピナー ({@link JProgressBar}) が表示されなくなるまで待つ。
     * PlantUML/Graphviz の初回ウォームアップで数秒かかることがあるため、固定 sleep ではなく
     * 期限付きポーリングにする（録画演出用。テスト合否には使わない）。期限内に収まらなくても
     * 例外にはせず、そのまま次のフレームを録り続ける（録画を止めないため）。
     */
    private static void awaitRenderIdle(JTabbedPane mainTabs, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            boolean rendering = GuiActionRunner.execute(() -> {
                Component sel = mainTabs.getSelectedComponent();
                return sel != null && containsVisibleProgressBar(sel);
            });
            if (!rendering) {
                return;
            }
            Thread.sleep(150);
        }
    }

    private static boolean containsVisibleProgressBar(Component c) {
        if (c instanceof JProgressBar && c.isShowing()) {
            return true;
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                if (containsVisibleProgressBar(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Swing ツリーで「先頭が prefix で始まるノード」最初のひとつへの TreePath を返す。
     * 行未生成のサブツリーは深さ 1 段だけ expand してから再探索する。
     */
    private static TreePath findPathByPrefix(JTree jtree, String prefix) {
        TreePath found = searchRows(jtree, prefix);
        if (found != null) {
            return found;
        }
        Object root = jtree.getModel().getRoot();
        for (int i = 0; i < jtree.getModel().getChildCount(root); i++) {
            jtree.expandRow(i);
        }
        return searchRows(jtree, prefix);
    }

    private static TreePath searchRows(JTree jtree, String prefix) {
        for (int i = 0; i < jtree.getRowCount(); i++) {
            TreePath p = jtree.getPathForRow(i);
            if (p == null) {
                continue;
            }
            Object last = p.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode) {
                String text = String.valueOf(((DefaultMutableTreeNode) last).getUserObject());
                if (text.startsWith(prefix)) {
                    return p;
                }
            }
        }
        return null;
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
     * 図種ツールバーの実トグルボタンを AssertJ-Swing 経由で物理クリックし、描画完了
     * (スピナー消失) を待ってから、完成した図を少し見せるための短い待ちを挟む
     * （テスト合否には使わない録画専用の待ち）。
     */
    private void clickDiagramToggleAndAwait(UmlMainFrame frame, JTabbedPane mainTabs,
            DiagramKind kind) throws Exception {
        EnumMap<DiagramKind, JToggleButton> toggles = getField(frame, "diagramToggles");
        JToggleButton button = toggles.get(kind);
        if (button == null) {
            System.err.println("[ReadmeDemoRecording] トグルボタンが見つからない: " + kind);
            return;
        }
        new JToggleButtonFixture(window.robot(), button).click();
        awaitRenderIdle(mainTabs, 10_000);
        pause(900);
    }

    /** 録画演出用の短い待ち（テスト合否には使わない）。 */
    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
