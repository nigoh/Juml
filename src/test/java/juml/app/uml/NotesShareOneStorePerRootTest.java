// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * 同じプロジェクトルートには<b>常に同じストア実体</b>が使われることの回帰テスト。
 *
 * <p>{@code storeFor} は「いまのルート」1 個しか覚えない単一フィールドで、ルートが
 * 変わるたびに作り直していた。{@link DiagramNotesStore} は初回にファイル全体をメモリへ
 * 読み、保存のたびに<b>全体を書き直す</b>ので、同じ {@code notes.json} に対して実体が
 * 2 つ生きると、古い像を持つ側の 1 回の保存が新しい像で書かれた他タブのエントリを
 * まとめて消す (後勝ち)。</p>
 *
 * <p>ルートが「いまのプロジェクト以外」へ振れる経路はラウンド 27 が入れた
 * {@code migrateKey} が作る。旧プロジェクトに束ねられたタブを Save As するとフィールドが
 * 旧ルートへ戻り、その後で現プロジェクトの図タブを開くとそのストアが 2 個目として
 * 生まれる。つまりこの穴は「別プロジェクトの付箋を載せたタブは保存先を移さない」という
 * 規則そのものが前提を作っていた。</p>
 */
public class NotesShareOneStorePerRootTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static DiagramNote note(String text, double x) {
        return new DiagramNote(x, 10, 120, 80, text);
    }

    private static List<String> savedTexts(File root, String key) {
        List<String> out = new ArrayList<>();
        for (DiagramNote n : new DiagramNotesStore(root).load(key)) {
            out.add(n.getText());
        }
        return out;
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 12; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(120);
        }
    }

    @Test
    public void aSaveFromOneTabDoesNotEraseAnotherTabsEntry() throws Exception {
        assumeFalse("headless では Swing コンポーネントを作れない",
                GraphicsEnvironment.isHeadless());
        File projectA = tmp.newFolder("storeA");
        File projectB = tmp.newFolder("storeB");
        String movedKey = "PUML:" + projectA.getPath() + "/moved.puml";

        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel[] t1 = new SvgPreviewPanel[1];
        SvgPreviewPanel[] t2 = new SvgPreviewPanel[1];
        try {
            // T1 は B に束ねる。
            SwingUtilities.invokeAndWait(() -> {
                t1[0] = new SvgPreviewPanel();
                binder.bind(t1[0], projectB, "T1");
                t1[0].notes().setData(
                        new ArrayList<>(List.of(note("T1-NOTE", 10))), Collections.emptyList());
            });
            settle();

            // A に束ねたタブの Save As で store フィールドを A へ振らせる
            // (これが「実体が 2 つになる」前提を作っていた)。
            SvgPreviewPanel[] old = new SvgPreviewPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                old[0] = new SvgPreviewPanel();
                binder.bind(old[0], projectA, "OLD");
                old[0].notes().setData(
                        new ArrayList<>(List.of(note("A-NOTE", 10))), Collections.emptyList());
            });
            settle();
            SwingUtilities.invokeAndWait(
                    () -> binder.migrateKey(old[0], projectB, "OLD", movedKey));
            settle();

            // 続けて B の 2 枚目のタブを開いて保存する。
            // setData は onChange を起こさないので、実際に保存させるため 1 つ足す。
            SwingUtilities.invokeAndWait(() -> {
                t2[0] = new SvgPreviewPanel();
                binder.bind(t2[0], projectB, "T2");
                t2[0].notes().setData(
                        new ArrayList<>(List.of(note("T2-NOTE", 10))), Collections.emptyList());
                t2[0].notes().addNoteAt(new Point(400, 400), 1.0);
            });
            settle();
            assertTrue("前提: T2 が保存されていること: " + savedTexts(projectB, "T2"),
                    savedTexts(projectB, "T2").contains("T2-NOTE"));

            // T1 を触るだけ。ここで T2 のエントリが消えていた。
            SwingUtilities.invokeAndWait(
                    () -> t1[0].notes().addNoteAt(new Point(400, 400), 1.0));
            settle();

            assertTrue("T1 の保存で T2 のエントリが消えないこと: " + savedTexts(projectB, "T2"),
                    savedTexts(projectB, "T2").contains("T2-NOTE"));
            assertTrue("T1 自身も保存されていること",
                    savedTexts(projectB, "T1").contains("T1-NOTE"));
            assertFalse("A 側の付箋が B へ漏れないこと",
                    savedTexts(projectB, "T2").contains("A-NOTE"));
        } finally {
            binder.shutdown();
        }
    }
}
