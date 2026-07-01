// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;

import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link StatusBarView} の単体テスト。
 *
 * <p>アクティブタブ連動 ({@link StatusBarView#setFocusedTab}) によって
 * 「題材 · 図種」ラベルが正しく更新されることを検証する。</p>
 *
 * <p>{@link StatusBarView} は Swing コンポーネント ({@link JLabel}) を内部に持つため、
 * ヘッドレス環境では {@link java.awt.HeadlessException} が発生する。
 * {@link Assume#assumeFalse} でガードして headless CI ではスキップする。</p>
 */
public class StatusBarViewTest {

    private StatusBarView view;
    /** StatusBarView 内の "題材 · 図種" 表示ラベル。リフレクションで取得して内容を検証する。 */
    private JLabel subjectLabel;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では Swing コンポーネントの生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() throws Exception {
        GuiActionRunner.execute(() -> {
            JLabel statusLabel = new JLabel();
            JLabel loadProgress = new JLabel();
            JLabel zoomLabel = new JLabel();
            view = new StatusBarView(statusLabel, loadProgress, zoomLabel);
            return null;
        });
        // package-private フィールド "subject" をリフレクションで取得して検証に使う。
        Field f = StatusBarView.class.getDeclaredField("subject");
        f.setAccessible(true);
        subjectLabel = (JLabel) f.get(view);
    }

    // -------------------------------------------------------------------------
    // (a) null を渡すとラベルがクリアされる
    // -------------------------------------------------------------------------

    @Test
    public void setFocusedTab_null_clearsLabel() {
        GuiActionRunner.execute(() -> view.setFocusedTab(null));
        String text = GuiActionRunner.execute(() -> subjectLabel.getText());
        // null を渡すと空白 (スペース) にリセットされる。
        assertTrue("null を渡したとき subject ラベルは空白であるべき",
                text == null || text.isBlank());
    }

    @Test
    public void setFocusedTab_kindNull_clearsLabel() {
        DiagramTabPane.FocusedTab info = new DiagramTabPane.FocusedTab(null, null);
        GuiActionRunner.execute(() -> view.setFocusedTab(info));
        String text = GuiActionRunner.execute(() -> subjectLabel.getText());
        assertTrue("kind=null を渡したとき subject ラベルは空白であるべき",
                text == null || text.isBlank());
    }

    // -------------------------------------------------------------------------
    // (b) kind だけ指定したとき、ラベルに図種名が表示される
    // -------------------------------------------------------------------------

    @Test
    public void setFocusedTab_kindOnly_showsKindName() {
        DiagramTabPane.FocusedTab info = new DiagramTabPane.FocusedTab(null, DiagramKind.CLASS);
        GuiActionRunner.execute(() -> view.setFocusedTab(info));
        String text = GuiActionRunner.execute(() -> subjectLabel.getText());
        assertNotNull("ラベルテキストが null であってはならない", text);
        assertFalse("図種あり・題材なし のとき subject ラベルが空白であってはならない",
                text.isBlank());
    }

    // -------------------------------------------------------------------------
    // (c) treeSync も kind も指定したとき、「題材 · 図種」形式のラベルになる
    // -------------------------------------------------------------------------

    @Test
    public void setFocusedTab_withTreeSync_showsSubjectAndKind() {
        JavaClassInfo ci = new JavaClassInfo();
        ci.setSimpleName("MyActivity");
        ci.setPackageName("com.example");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);

        DiagramTabPane.FocusedTab info = new DiagramTabPane.FocusedTab(req, DiagramKind.CLASS);
        GuiActionRunner.execute(() -> view.setFocusedTab(info));
        String text = GuiActionRunner.execute(() -> subjectLabel.getText());

        assertNotNull("ラベルテキストが null であってはならない", text);
        assertTrue("ラベルにクラス名 'MyActivity' が含まれるべき",
                text.contains("MyActivity"));
        // 「題材 · 図種」の区切り文字が含まれること。
        assertTrue("ラベルに '·' 区切りが含まれるべき", text.contains("·"));
    }

    // -------------------------------------------------------------------------
    // (d) 別の図種に切り替えると表示が変わる
    // -------------------------------------------------------------------------

    @Test
    public void setFocusedTab_kindChange_updatesLabel() {
        JavaClassInfo ci = new JavaClassInfo();
        ci.setSimpleName("Foo");
        ci.setPackageName("com.demo");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);

        DiagramTabPane.FocusedTab classInfo = new DiagramTabPane.FocusedTab(req, DiagramKind.CLASS);
        DiagramTabPane.FocusedTab seqInfo = new DiagramTabPane.FocusedTab(req, DiagramKind.SEQUENCE);

        GuiActionRunner.execute(() -> view.setFocusedTab(classInfo));
        String classText = GuiActionRunner.execute(() -> subjectLabel.getText());

        GuiActionRunner.execute(() -> view.setFocusedTab(seqInfo));
        String seqText = GuiActionRunner.execute(() -> subjectLabel.getText());

        assertFalse("CLASS と SEQUENCE で subject ラベルが同じであってはならない",
                classText.equals(seqText));
    }

    // -------------------------------------------------------------------------
    // (e) getComponent() が非 null を返す (レイアウト挿入用)
    // -------------------------------------------------------------------------

    @Test
    public void getComponent_returnsNonNull() {
        javax.swing.JComponent comp = GuiActionRunner.execute(() -> view.getComponent());
        assertNotNull("getComponent() は SOUTH に配置するコンポーネントを返すべき", comp);
    }
}
