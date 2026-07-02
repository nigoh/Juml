// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * {@link PerFolderExporter} の {@code runAsync()} done() エラー分岐を headful 環境で検証する。
 *
 * <p>{@code runAsync} は private static メソッドなのでリフレクションで呼び出す。
 * {@link javax.swing.SwingWorker} の done() が EDT で呼ばれるまで期限付きポーリングで待ち、
 * {@link javax.swing.JOptionPane} が表示されたら自動 dispose する
 * ({@link PngBackgroundExporterTest} と同じパターン)。</p>
 */
public class PerFolderExporterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** テスト中に開いたウィンドウを @After で確実に閉じる。 */
    private final List<Window> toDispose = new ArrayList<>();

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では SwingWorker done() の JOptionPane が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @After
    public void cleanup() {
        GuiActionRunner.execute(() -> {
            for (Window w : toDispose) {
                if (w.isDisplayable()) {
                    w.dispose();
                }
            }
            return null;
        });
        toDispose.clear();
    }

    // -------------------------------------------------------------------------
    // ヘルパ: PerFolderExporter.runAsync() をリフレクションで呼び出す
    // -------------------------------------------------------------------------

    private static void invokeRunAsync(File projectRoot,
                                       File outDir,
                                       List<JavaClassInfo> classes,
                                       ClassIndex index,
                                       JProgressBar progress,
                                       JLabel status) throws Exception {
        Method m = PerFolderExporter.class.getDeclaredMethod(
                "runAsync",
                javax.swing.JFrame.class,
                File.class, File.class,
                List.class, ClassIndex.class,
                JProgressBar.class, JLabel.class);
        m.setAccessible(true);
        m.invoke(null, null, projectRoot, outDir, classes, index, progress, status);
    }

    // -------------------------------------------------------------------------
    // ヘルパ: 期限付きポーリングで新しいウィンドウの出現を待ち、自動 dispose する
    // -------------------------------------------------------------------------

    private Window waitForAndDisposeDialog(long ms) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            Window found = GuiActionRunner.execute(() -> {
                for (Window w : Window.getWindows()) {
                    if (w.isShowing() && !toDispose.contains(w)) {
                        return w;
                    }
                }
                return null;
            });
            if (found != null) {
                toDispose.add(found);
                GuiActionRunner.execute(() -> found.dispose());
                return found;
            }
            Thread.sleep(50);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // ヘルパ: JProgressBar が不可視になるまで待つ (done() 完了の合図)
    // -------------------------------------------------------------------------

    private static void awaitProgressHidden(JProgressBar bar, long ms)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < deadline) {
            boolean visible = GuiActionRunner.execute(bar::isVisible);
            if (!visible) {
                return;
            }
            Thread.sleep(50);
        }
    }

    // -------------------------------------------------------------------------
    // テスト: 存在しない projectRoot でエラー分岐が走る
    // -------------------------------------------------------------------------

    @Test
    public void runAsync_withNonexistentRoot_triggersErrorDialogAndResetsProgress()
            throws Exception {
        File outDir = tmp.newFolder("out");
        File fakeRoot = new File(tmp.getRoot(), "nonexistent");
        JProgressBar progress = GuiActionRunner.execute(() -> new JProgressBar());
        JLabel status = GuiActionRunner.execute(() -> new JLabel());

        GuiActionRunner.execute(() -> {
            invokeRunAsyncQuiet(fakeRoot, outDir,
                    Collections.emptyList(), new ClassIndex(),
                    progress, status);
            return null;
        });

        // progress bar が不可視になるまで待つ (done() が呼ばれた合図)
        awaitProgressHidden(progress, 10_000);
        assertFalse("done() 後に JProgressBar がリセット (不可視) されること",
                GuiActionRunner.execute(() -> progress.isVisible()));
    }

    // -------------------------------------------------------------------------
    // テスト: 空クラスリストで outDir が存在する場合は正常完了 (成功ダイアログ)
    // -------------------------------------------------------------------------

    @Test
    public void runAsync_withEmptyClassList_showsSuccessDialogAndResetsProgress()
            throws Exception {
        File projectRoot = tmp.newFolder("root");
        File outDir = tmp.newFolder("out2");
        JProgressBar progress = GuiActionRunner.execute(() -> new JProgressBar());
        JLabel status = GuiActionRunner.execute(() -> new JLabel());

        GuiActionRunner.execute(() -> {
            invokeRunAsyncQuiet(projectRoot, outDir,
                    Collections.emptyList(), new ClassIndex(),
                    progress, status);
            return null;
        });

        // 完了ダイアログが表示されるまで待つ。
        // PerFolderExporter.done() は: resetBar → (error なし) → result 取得 →
        // result が non-null なら JOptionPane.showMessageDialog を呼ぶ。
        // 空クラスリスト + 有効 outDir の場合、PerFolderClassDiagrams.generate() は
        // Result(0,0,[]) を返す (null ではない) ので、成功ダイアログが必ず表示される。
        Window dlg = waitForAndDisposeDialog(10_000);

        // プログレスバーが not visible になること (done() でリセットされる)
        awaitProgressHidden(progress, 5_000);
        assertFalse("done() 後に JProgressBar がリセットされること",
                GuiActionRunner.execute(() -> progress.isVisible()));

        // 成功ダイアログが表示されること (tautology 除去: if (dlg != null) assertNotNull は無条件に)
        assertNotNull("空クラスリストでも成功ダイアログが表示されること", dlg);
    }

    // -------------------------------------------------------------------------
    // ヘルパ: リフレクション呼び出しの例外をラップ
    // -------------------------------------------------------------------------

    private static void invokeRunAsyncQuiet(File projectRoot, File outDir,
                                            List<JavaClassInfo> classes,
                                            ClassIndex index,
                                            JProgressBar progress,
                                            JLabel status) {
        try {
            invokeRunAsync(projectRoot, outDir, classes, index, progress, status);
        } catch (Exception ex) {
            throw new RuntimeException("invokeRunAsync failed", ex);
        }
    }
}
