// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * エディタタブの {@code .puml} エクスポートが「最後に描けたテキスト」ではなく
 * <b>現在のバッファ</b>を書き出すことの回帰テスト。
 *
 * <p>{@code renderedPuml} はライブプレビューの 600ms デバウンス待ちのあいだ古いままで、
 * 編集中のテキストが構文エラーなら「直前の正常な図を保持する」仕様上さらに古い。
 * 以前は 3 形式すべてがそれを書き出していたため、打ち込んだばかりの行が黙って欠けた
 * {@code .puml} が保存されていた。画像 (SVG/PNG) は「見えている図」を出すのが正しいので
 * 従来どおり {@code renderedPuml} を使う。</p>
 */
public class EditorPumlExportUsesBufferTest {

    private static final int FIXED = 1;
    private static final String RENDERED = "@startuml\nclass Old\n@enduml\n";
    private static final String TYPED = "@startuml\nclass Old\nclass JustTyped\n@enduml\n";

    private JTabbedPane tabs;
    private DiagramTabPane pane;

    @Before
    public void requireDisplay() {
        Assume.assumeFalse(
                "ヘッドレス環境では DiagramTab の Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    @Before
    public void setUp() {
        GuiActionRunner.execute(() -> {
            tabs = new JTabbedPane();
            tabs.addTab("Utility", new javax.swing.JPanel());
            pane = new DiagramTabPane(tabs, FIXED, new ProjectAnalysisCache(),
                    new DiagramState(), msg -> { }, zoom -> { });
        });
    }

    private static Object onlyOpenTab(DiagramTabPane pane) throws Exception {
        Field f = DiagramTabPane.class.getDeclaredField("openTabs");
        f.setAccessible(true);
        return ((Map<?, ?>) f.get(pane)).values().iterator().next();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String pumlForExport(Object tab, UmlExporter.Format fmt) throws Exception {
        Method m = tab.getClass().getDeclaredMethod("pumlForExport", UmlExporter.Format.class);
        m.setAccessible(true);
        return (String) m.invoke(tab, fmt);
    }

    /** 描画済みテキストとバッファがずれているエディタタブを作る。 */
    private Object editorTabWithUnrenderedEdit() throws Exception {
        GuiActionRunner.execute(() -> pane.openPumlEditor(RENDERED, null));
        Object tab = onlyOpenTab(pane);
        GuiActionRunner.execute(() -> {
            try {
                // 最後に描けたのは RENDERED、いま画面にあるのは TYPED という状態。
                setField(tab, "renderedPuml", RENDERED);
                Field sp = tab.getClass().getDeclaredField("sourcePanel");
                sp.setAccessible(true);
                ((PumlSourcePanel) sp.get(tab)).setText(TYPED);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return null;
        });
        return tab;
    }

    @Test
    public void pumlExportTakesTheCurrentBuffer() throws Exception {
        Object tab = editorTabWithUnrenderedEdit();
        assertEquals("打ち込んだばかりの行を含む現在のバッファを書き出すこと",
                TYPED, pumlForExport(tab, UmlExporter.Format.PUML));
    }

    @Test
    public void imageExportStillTakesTheRenderedText() throws Exception {
        // 非退行: 画像は「いま見えている図」なので最後に描けたテキストのまま。
        Object tab = editorTabWithUnrenderedEdit();
        assertEquals(RENDERED, pumlForExport(tab, UmlExporter.Format.SVG));
        assertEquals(RENDERED, pumlForExport(tab, UmlExporter.Format.PNG));
    }

    /**
     * 回帰: {@code .puml} を書き出す経路は 3 つある (タブの右クリック / File メニュー・
     * Ctrl+Shift+S / Export All Open Tabs) のに、当初は 1 つしか直していなかった。
     * どれも同じ現在のバッファを返すこと。経路ごとに答えが違うのが一番たちが悪い。
     */
    @Test
    public void allPumlExportRoutesAgreeOnTheCurrentBuffer() throws Exception {
        editorTabWithUnrenderedEdit();

        // 1. File メニュー / Ctrl+Shift+S / 共有 URL が使う経路
        assertEquals("activeSourcePuml が現在のバッファを返すこと",
                TYPED, pane.activeSourcePuml());

        // 2. Export All Open Tabs が使う経路
        java.util.List<BulkTabExporter.Snapshot> snaps =
                GuiActionRunner.execute(() -> pane.exportSnapshots());
        assertEquals(1, snaps.size());
        assertEquals("バルク書き出しのソース側も現在のバッファを使うこと",
                TYPED, snaps.get(0).sourcePuml);
        assertEquals("画像側は最後に描けたテキストのままであること",
                RENDERED, snaps.get(0).renderedPuml);
    }

    @Test
    public void activeRenderedPumlStillReportsTheRenderedText() throws Exception {
        // 非退行: 画像経路が使う「最後に描けたテキスト」は据え置き。
        editorTabWithUnrenderedEdit();
        assertEquals(RENDERED, pane.activeRenderedPuml());
    }
}
