// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JTextArea;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link SourceFindBar} のインクリメンタル検索ロジックを検証する。
 *
 * <p>対象 {@link JTextArea} のハイライタ越しにヒット件数を観測する。{@code JTextArea} は
 * ネイティブピアを必要としないためヘッドレスで完結する。検索クエリは選択テキストから
 * {@link SourceFindBar#activate()} 経由で投入する（{@code field} は private のため、
 * リフレクションに頼らず公開導線で駆動する）。</p>
 *
 * <p>注: 選択範囲そのものもハイライタに 1 件として現れるため、検索ヒット数は
 * 「選択直後のベースライン」との差分で数える。</p>
 */
public class SourceFindBarTest {

    private static int highlightCount(JTextArea area) {
        return area.getHighlighter().getHighlights().length;
    }

    @Test
    public void activate_highlightsAllOccurrencesOfSelectedText() {
        JTextArea area = new JTextArea("foo bar foo baz foo");
        SourceFindBar bar = new SourceFindBar(area, null);
        area.select(0, 3); // "foo" を選択 → activate でクエリになる
        area.getCaret().setSelectionVisible(true); // 選択ハイライトを baseline に含める
        int baseline = highlightCount(area);
        bar.activate();
        assertEquals("3 件の foo が全てハイライトされること",
                3, highlightCount(area) - baseline);
    }

    @Test
    public void reset_clearsHighlights() {
        JTextArea area = new JTextArea("alpha alpha alpha");
        SourceFindBar bar = new SourceFindBar(area, null);
        area.select(0, 5);
        area.getCaret().setSelectionVisible(true);
        int baseline = highlightCount(area);
        bar.activate();
        assertTrue("リセット前はヒットがあること", highlightCount(area) > baseline);

        bar.reset();
        assertEquals("reset で検索ハイライトが消えること", baseline, highlightCount(area));
        assertFalse("reset 後は非表示になること", bar.isVisible());
    }

    @Test
    public void close_clearsHighlightsAndHides() {
        JTextArea area = new JTextArea("xy xy xy");
        SourceFindBar bar = new SourceFindBar(area, null);
        area.select(0, 2);
        area.getCaret().setSelectionVisible(true);
        int baseline = highlightCount(area);
        bar.activate();
        assertTrue(highlightCount(area) > baseline);

        bar.close();
        assertEquals("close で検索ハイライトが消えること", baseline, highlightCount(area));
        assertFalse("close 後は非表示になること", bar.isVisible());
    }

    @Test
    public void activate_withNoSelection_addsNoHighlights() {
        JTextArea area = new JTextArea("nothing selected here");
        SourceFindBar bar = new SourceFindBar(area, null);
        int baseline = highlightCount(area);
        // 選択なし → クエリ空 → ヒットなし、例外も出ないこと
        bar.activate();
        assertEquals("空クエリではハイライトされないこと", baseline, highlightCount(area));
    }
}
