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

import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link PngBackgroundExporter#save} の done() 成功/エラー分岐を headful 環境で検証する。
 *
 * <p>エラー時に表示される {@link javax.swing.JOptionPane} は、期限付きポーリングで
 * 出現を検知して自動 dispose する ({@link NoteEditDialogTest} と同じパターン)。</p>
 */
public class PngBackgroundExporterTest {

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
    // ヘルパ: 期限付きポーリングで JDialog (JOptionPane) の出現を待ち、自動 dispose する
    // -------------------------------------------------------------------------

    /**
     * {@code triggerMs} ms 以内に新しい {@code Window} が出現したら dispose して返す。
     * 出現しない場合は null を返す。
     */
    private Window waitForAndDisposeDialog(long triggerMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + triggerMs;
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
    // テスト: 有効な puml で成功分岐 — onStatus に保存パスが渡される
    // -------------------------------------------------------------------------

    @Test
    public void save_withValidPuml_onStatusReceivesSavedPath() throws Exception {
        File outFile = tmp.newFile("out.png");
        AtomicReference<String> status = new AtomicReference<>("");
        String puml = "@startuml\nA -> B\n@enduml\n";
        JLabel dummy = GuiActionRunner.execute(() -> new JLabel());

        GuiActionRunner.execute(() -> {
            PngBackgroundExporter.save(puml, outFile, dummy, status::set);
            return null;
        });

        // done() の完了後のステータスはファイルパスを含む。
        // doInBackground が PNG を生成するまで待ち、done() が保存パスを設定するまでポーリング。
        // 多数のテストを並列実行して負荷が高いと PlantUML の初回描画 (コールドスタート) が
        // 遅れるため、単独実行では十分な 20s だと稀にタイムアウトしていた。フルスイート
        // 実行での安定性を優先し、成功待ちの猶予を広く取る (失敗時は結局アサートで落ちる)。
        String absPath = outFile.getAbsolutePath();
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (status.get().contains(absPath)) {
                break;
            }
            Thread.sleep(50);
        }

        String finalStatus = status.get();
        assertTrue("成功時の onStatus はファイルパスを含むこと: " + finalStatus,
                finalStatus.contains(absPath));
        assertTrue("出力ファイルが生成されること", outFile.length() > 0);
    }

    // -------------------------------------------------------------------------
    // テスト: null puml でエラー分岐 — JOptionPane が表示され onStatus に変化なし
    // -------------------------------------------------------------------------

    @Test
    public void save_withNullPuml_triggersErrorDialog() throws Exception {
        File outFile = tmp.newFile("out.png");
        AtomicReference<String> status = new AtomicReference<>("initial");
        JLabel dummy = GuiActionRunner.execute(() -> new JLabel());

        // PngBackgroundExporter.save() は onStatus に "生成中" を設定してから
        // SwingWorker を起動する。done() でエラー時は JOptionPane を表示する。
        GuiActionRunner.execute(() -> {
            PngBackgroundExporter.save(null, outFile, dummy, status::set);
            return null;
        });

        // エラーダイアログが出現するまで待ち、出現したら自動 dispose
        Window dlg = waitForAndDisposeDialog(10_000);
        assertNotNull("null puml 時にエラーダイアログが表示されること", dlg);
    }

    // -------------------------------------------------------------------------
    // テスト: 書き込み不可ファイルへの保存でエラー分岐
    // -------------------------------------------------------------------------

    @Test
    public void save_withUnwritableTarget_triggersErrorDialog() throws Exception {
        File outDir = tmp.newFolder("readonly");
        // ディレクトリを指定することで UmlExporter.export が失敗するようにする
        File outFile = new File(outDir, "sub/out.png"); // 存在しない中間ディレクトリ
        AtomicReference<String> status = new AtomicReference<>("");
        JLabel dummy = GuiActionRunner.execute(() -> new JLabel());

        String puml = "@startuml\nA -> B\n@enduml\n";
        // save() は SwingWorker 起動前に onStatus へ「生成中」を同期セットするため、
        // status が非空かどうかでは done() の完了を判定できない (常に即座に真になり、
        // 非同期に出るエラーダイアログを待たずテストが抜けて orphan dialog を残してしまう)。
        // このケースでは失敗 (エラーダイアログ表示) だけを判定基準にする。
        GuiActionRunner.execute(() -> {
            PngBackgroundExporter.save(puml, outFile, dummy, status::set);
            return null;
        });

        Window foundDlg = waitForAndDisposeDialog(10_000);

        assertTrue("書き込み不可ファイルへの保存でエラーダイアログが表示されること",
                foundDlg != null);
    }
}
