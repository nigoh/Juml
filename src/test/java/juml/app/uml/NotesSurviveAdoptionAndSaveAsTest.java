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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

/**
 * 付箋の保存先を移す 2 つの経路が、<b>移した先の保存済み付箋を消さない</b>ことの回帰テスト。
 *
 * <p>ラウンド 26 は「別プロジェクトの付箋が載ったタブは保存先を移さない」という規則を
 * {@code rebindForProject} へ入れた。しかし同じメソッドの<b>「引き取り」分岐</b>
 * (まだ一度も実プロジェクトへ束ねていないタブ) にはその規則が掛かっておらず、
 * 引き取り先に保存済み付箋があると、ロード側の「レイヤが空のときだけ反映する」規則で
 * それが読み込まれないまま、次に付箋を 1 つ触った瞬間に画面の一覧が引き取り先の
 * notes.json を丸ごと上書きしていた。Untitled のタブキーはセッションごとに 0 から
 * 振り直されるので、この衝突は例外ではなく<b>既定</b>である。</p>
 *
 * <p>もう 1 つの兄弟経路が Save As ({@code migrateEditorTabKey})。こちらは
 * {@code rebindForProject} の規則も「移行先が null なら再バインドしない」規則も
 * どちらも通らずに無条件で bind していたため、A に束ねたタブを B へ切り替えた後に
 * Save As すると、A の付箋が B の保存済み付箋を上書きして消していた。</p>
 */
public class NotesSurviveAdoptionAndSaveAsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String KEY = "PUML:untitled-1";

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

    /** 投入済みの IO タスクが片付くまで EDT と IO スレッドを回す。 */
    private static void settle() throws Exception {
        for (int i = 0; i < 12; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(120);
        }
    }

    /**
     * プロジェクト未ロードで書いた付箋を最初のプロジェクトが引き取るとき、
     * 引き取り先に既にある保存済み付箋を消さないこと。
     */
    @Test
    public void adoptingScratchNotesDoesNotEraseTheProjectsSavedNotes() throws Exception {
        assumeFalse("headless では Swing コンポーネントを作れない",
                GraphicsEnvironment.isHeadless());
        File projectA = tmp.newFolder("projA");
        new DiagramNotesStore(projectA).save(KEY,
                List.of(note("IMPORTANT-A", 10)), Collections.emptyList());

        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel[] p = new SvgPreviewPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new SvgPreviewPanel();
                binder.bind(p[0], null, KEY); // プロジェクト未ロード = どこのものでもない
                p[0].notes().setData(
                        new ArrayList<>(List.of(note("scratch", 200))), Collections.emptyList());
            });
            settle();
            SwingUtilities.invokeAndWait(() -> binder.rebindForProject(p[0], projectA, KEY));
            settle();

            List<String> onScreen = new ArrayList<>();
            SwingUtilities.invokeAndWait(() -> {
                for (DiagramNote n : p[0].notes().getNotes()) {
                    onScreen.add(n.getText());
                }
            });
            assertTrue("引き取り先の保存済み付箋も画面へ出ること: " + onScreen,
                    onScreen.contains("IMPORTANT-A"));
            assertTrue("画面にあった付箋も残ること: " + onScreen, onScreen.contains("scratch"));

            // 付箋を 1 つ触ると保存が走る。ここで上書きが起きていた。
            SwingUtilities.invokeAndWait(
                    () -> p[0].notes().addNoteAt(new Point(400, 400), 1.0));
            settle();
            assertTrue("保存済み付箋がファイルからも消えないこと: " + savedTexts(projectA, KEY),
                    savedTexts(projectA, KEY).contains("IMPORTANT-A"));
        } finally {
            binder.shutdown();
        }
    }

    /**
     * Save As は<b>タブがいま束ねられているストアの中で</b>キーを移すこと。
     * 現在のプロジェクトへ移すと、そちらの保存済み付箋を消す。
     */
    @Test
    public void saveAsKeepsNotesInTheStoreTheTabIsBoundTo() throws Exception {
        assumeFalse("headless では Swing コンポーネントを作れない",
                GraphicsEnvironment.isHeadless());
        File projectA = tmp.newFolder("projA2");
        File projectB = tmp.newFolder("projB2");
        String newKey = "PUML:" + projectB.getPath() + "/x.puml";
        new DiagramNotesStore(projectB).save(newKey,
                List.of(note("B-KEEP", 10)), Collections.emptyList());

        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel[] p = new SvgPreviewPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new SvgPreviewPanel();
                binder.bind(p[0], projectA, KEY);
                p[0].notes().setData(
                        new ArrayList<>(List.of(note("A-SECRET", 10))), Collections.emptyList());
            });
            settle();
            // B へ切替。R26 の規則でタブは A に束ねられたまま (これは正しい挙動)。
            SwingUtilities.invokeAndWait(() -> binder.rebindForProject(p[0], projectB, KEY));
            settle();
            // Save As。移行先の既定値として現在のプロジェクト (B) を渡しても、
            // 束ねられている A の中でキーが移ること。
            SwingUtilities.invokeAndWait(
                    () -> binder.migrateKey(p[0], projectB, KEY, newKey));
            settle();
            SwingUtilities.invokeAndWait(
                    () -> p[0].notes().addNoteAt(new Point(400, 400), 1.0));
            settle();

            assertEquals("B の保存済み付箋が A の付箋で上書きされないこと",
                    List.of("B-KEEP"), savedTexts(projectB, newKey));
            assertTrue("A 側の新キーへ移っていること: " + savedTexts(projectA, newKey),
                    savedTexts(projectA, newKey).contains("A-SECRET"));
        } finally {
            binder.shutdown();
        }
    }


    /**
     * bug-hunt R2 で発見: 引き取った付箋は次に何か 1 つ触るまで保存されず、そのまま
     * 終了すると失われていた。引き取り直後に合流結果が保存されること。
     */
    @Test
    public void adoptedScratchNotesArePersistedWithoutFurtherEdit() throws Exception {
        assumeFalse("headless では Swing コンポーネントを作れない",
                GraphicsEnvironment.isHeadless());
        File projectA = tmp.newFolder("projA2");
        new DiagramNotesStore(projectA).save(KEY,
                List.of(note("IMPORTANT-A", 10)), Collections.emptyList());
        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel[] p = new SvgPreviewPanel[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                p[0] = new SvgPreviewPanel();
                binder.bind(p[0], null, KEY);
                p[0].notes().setData(
                        new ArrayList<>(List.of(note("scratch", 200))), Collections.emptyList());
            });
            settle();
            SwingUtilities.invokeAndWait(() -> binder.rebindForProject(p[0], projectA, KEY));
            settle();
            List<String> saved = savedTexts(projectA, KEY);
            assertTrue("引き取った付箋が編集なしで保存されること: " + saved, saved.contains("scratch"));
            assertTrue("引き取り先の付箋も残ること: " + saved, saved.contains("IMPORTANT-A"));
        } finally {
            binder.shutdown();
        }
    }
}
