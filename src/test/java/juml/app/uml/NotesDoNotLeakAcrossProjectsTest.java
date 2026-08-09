// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * プロジェクトを切り替えても、付箋が<b>別プロジェクトの保存先へ漏れない</b>ことの回帰テスト。
 *
 * <p>ラウンド 25 は「プロジェクト切替でレイヤを空にしない」ことにした。空にすると画面に
 * 見えている唯一の実体が消えるうえ、履歴も消えて Ctrl+Z で戻らないからである。その判断
 * 自体は正しいが、「レイヤが空のときだけ反映する」という規則が掛かっているのは
 * <b>ロード経路だけ</b>で、<b>保存経路には掛かっていなかった</b>。</p>
 *
 * <p>その結果: 旧プロジェクトの付箋が載ったタブの保存先だけを新プロジェクトへ移すと、
 * 切替先の保存済み付箋は (レイヤが空でないので) 読み込まれず、その状態で付箋を 1 つ
 * 触った瞬間に画面上の<b>旧プロジェクトの一覧が切替先の notes.json を丸ごと上書き</b>する。
 * Untitled エディタのタブキーはセッションをまたいで再利用されるので衝突は現実に起きるし、
 * 衝突しなくても他プロジェクトのメモ本文が (git 共有前提の) ファイルへ混入する。</p>
 */
public class NotesDoNotLeakAcrossProjectsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String KEY = "PUML:untitled-1";

    private static SvgPreviewPanel panelWithNote(String text) {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));
        DiagramNote n = new DiagramNote();
        n.setAnchor(DiagramNote.Anchor.FREE);
        n.setX(10);
        n.setY(10);
        n.setWidth(80);
        n.setHeight(40);
        n.setText(text);
        panel.notes().setData(List.of(n), Collections.emptyList());
        return panel;
    }

    /** そのルートの保存済み付箋の本文 (無ければ空リスト)。 */
    private static List<String> stored(File root, String key) {
        List<DiagramNote> loaded = new DiagramNotesStore(root).load(key);
        return loaded.stream().map(DiagramNote::getText).collect(java.util.stream.Collectors.toList());
    }

    /**
     * プロジェクト A の付箋が載ったタブは、B へ切り替えても保存先を B へ移さないこと。
     */
    @Test
    public void switchingProjectsDoesNotRepointNotesThatBelongToTheOldOne() throws Exception {
        File a = tmp.newFolder("ProjA");
        File b = tmp.newFolder("ProjB");
        // B には既に別の付箋が保存されている (同じタブキー)。
        DiagramNote bNote = new DiagramNote();
        bNote.setText("B-SIDE-MEMO");
        new DiagramNotesStore(b).save(KEY, List.of(bNote), Collections.emptyList());

        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel panel = panelWithNote("A-SIDE-MEMO");
        binder.bind(panel, a, KEY);

        // A → B の切替。画面には A の付箋が載ったまま。
        binder.rebindForProject(panel, b, KEY);

        // 付箋を 1 つ触る (これが保存を発火させる)。
        panel.notes().addNoteAt(new java.awt.Point(120, 120), 1.0);
        binder.shutdown(); // 投入済みの保存タスクを流し切る

        assertTrue("B の保存済み付箋が A のもので上書きされないこと: " + stored(b, KEY),
                !stored(b, KEY).contains("A-SIDE-MEMO"));
        assertEquals("B の付箋はそのまま残ること",
                List.of("B-SIDE-MEMO"), stored(b, KEY));
    }

    /** 切替後も画面の付箋は消えないこと (ラウンド 25 が直した挙動の非退行)。 */
    @Test
    public void switchingProjectsKeepsWhatIsOnScreen() throws Exception {
        File a = tmp.newFolder("ProjA");
        File b = tmp.newFolder("ProjB");
        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel panel = panelWithNote("A-SIDE-MEMO");
        binder.bind(panel, a, KEY);

        binder.rebindForProject(panel, b, KEY);
        binder.shutdown();

        assertEquals("画面の付箋は残ること", 1, panel.notes().getNotes().size());
        assertEquals("A-SIDE-MEMO", panel.notes().getNotes().get(0).getText());
    }

    /**
     * まだ一度も実プロジェクトへ束ねていない付箋は、最初に開いたプロジェクトが引き取ること。
     *
     * <p>「プロジェクトを開く前にメモを書き、そのあとプロジェクトを開く」は正規の導線で、
     * このときの付箋は<b>どこのものでもない</b>ので、切替先が保存先になってよい。
     * 別プロジェクトのものを移さない、という規則とはっきり区別する。</p>
     */
    @Test
    public void notesMadeBeforeAnyProjectAreAdoptedByTheFirstOne() throws Exception {
        File first = tmp.newFolder("First");
        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel panel = panelWithNote("DRAFT-MEMO");

        binder.rebindForProject(panel, first, KEY);
        panel.notes().addNoteAt(new java.awt.Point(120, 120), 1.0);
        binder.shutdown();

        assertTrue("最初に開いたプロジェクトへ保存されること: " + stored(first, KEY),
                stored(first, KEY).contains("DRAFT-MEMO"));
    }

    /** 付箋が 1 件も無いタブは、切替先へ束ね直して切替先の付箋を読み込めること。 */
    @Test
    public void anEmptyTabIsRepointedToTheNewProject() throws Exception {
        File a = tmp.newFolder("ProjA");
        File b = tmp.newFolder("ProjB");
        DiagramNote bNote = new DiagramNote();
        bNote.setText("B-SIDE-MEMO");
        new DiagramNotesStore(b).save(KEY, List.of(bNote), Collections.emptyList());

        DiagramNotesBinder binder = new DiagramNotesBinder();
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));
        binder.bind(panel, a, KEY);

        binder.rebindForProject(panel, b, KEY);
        binder.shutdown();

        // 反映は EDT へ invokeLater されるので流し切る。
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        assertEquals("切替先の付箋が読み込まれること",
                List.of("B-SIDE-MEMO"),
                panel.notes().getNotes().stream().map(DiagramNote::getText)
                        .collect(java.util.stream.Collectors.toList()));
    }
}
