// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 整形式でない断片へ落ちたときの平文フォールバックが、<b>本文の文字を変えない</b>
 * ことの回帰テスト。
 *
 * <p>ラウンド 27 は「飾りが減ることと、ファイルが開けないことなら前者を選ぶ」として
 * 平文フォールバックを入れた。その実装は既にエスケープ済みの文字列へもう一度
 * エスケープを掛けていたので、{@code &amp;} が {@code &amp;amp;} として<b>見えてしまう</b> —
 * 画面と PNG は {@code &amp;} と正しく出るので、ラウンド 27 が潰したのと同じ
 * 「同じ付箋が書き出し形式で別物になる」失敗モードに戻っていた。</p>
 *
 * <p>タグ剥がしも {@code <[^>]*>} では足りない。この分岐へ来る断片は入れ子が崩れている
 * ので、属性値の中の {@code <} でマッチが途中で切れ、{@code href} の断片が本文として
 * 残る (実測: 本文 {@code t} が {@code a"&gt;t} になった)。</p>
 */
public class ExportFallbackKeepsTheTextTest {

    private static final String BASE =
            "<svg width=\"300\" height=\"200\" viewBox=\"0 0 300 200\"></svg>";

    /** 付箋 div の中身 (本文) を取り出す。 */
    private static String noteBody(String svg) {
        int i = svg.indexOf("class=\"juml-note-body\"");
        if (i < 0) {
            return "";
        }
        int s = svg.indexOf('>', i) + 1;
        int e = svg.indexOf("</div>", s);
        return e > s ? svg.substring(s, e) : "";
    }

    private static String exportOf(String body) {
        return NoteExport.injectIntoSvg(BASE, DiagramNotesLayer.ExportOverlay.ofNotes(
                List.of(new DiagramNote(10, 10, 240, 150, body))));
    }

    /** {@code &} が二重エスケープされないこと。 */
    @Test
    public void theFallbackDoesNotDoubleEscapeEntities() {
        String out = noteBody(exportOf("Rate & limit: see [**docs](http://h/x)** & retry"));
        assertTrue("XML 上は &amp; 1 段であること (表示は &): " + out,
                out.contains("Rate &amp; limit"));
        assertFalse("二重エスケープしないこと: " + out, out.contains("&amp;amp;"));
    }

    /** タグを剥がすとき属性値が本文へ漏れないこと。 */
    @Test
    public void theFallbackDoesNotLeakAttributeValues() {
        String out = noteBody(exportOf("[t](http://x/*a*)"));
        assertFalse("href の断片が残らないこと: " + out, out.contains("http"));
        assertFalse("引用符の断片が残らないこと: " + out, out.contains("&quot;"));
        assertTrue("本文そのものは残ること: " + out, out.contains("t"));
    }

    /** 非退行: 整形式な本文は装飾を保ったまま素通りすること。 */
    @Test
    public void wellFormedMarkupIsUntouched() {
        String out = noteBody(exportOf("**bold** and [link](http://x) & co"));
        assertTrue("装飾が残ること: " + out, out.contains("<b>bold</b>"));
        assertTrue("リンクが残ること: " + out, out.contains("<a href=\"http://x\">link</a>"));
        assertTrue("エスケープは 1 段のまま: " + out, out.contains("&amp; co"));
        assertFalse(out.contains("&amp;amp;"));
    }
}
