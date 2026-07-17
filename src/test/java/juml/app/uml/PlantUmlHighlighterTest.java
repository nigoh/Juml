// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.SourceHighlighter.Span;
import org.junit.Test;

import java.awt.Color;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link PlantUmlHighlighter} のトークン走査を検証する純ロジックテスト (headless)。
 *
 * <p>色そのものではなく「その位置にスパンが付くか」を、テキスト内のオフセットで確認する。
 * 空文字・null でも例外を出さないこと、コメント/文字列内はキーワード着色しないことも見る。</p>
 */
public class PlantUmlHighlighterTest {

    /** {@code text} 中の {@code sub} の開始位置を含むスパンがあれば、その色を返す。無ければ null。 */
    private static Color colorOf(String text, List<Span> spans, String sub) {
        int at = text.indexOf(sub);
        for (Span s : spans) {
            if (at >= s.start && at < s.start + s.length) {
                return s.color;
            }
        }
        return null;
    }

    @Test
    public void keywordsDirectivesArrowsAndLiteralsGetSpans() {
        String t = "@startuml\nclass Foo {\n}\nFoo --> Bar : uses\n@enduml\n";
        List<Span> spans = PlantUmlHighlighter.highlight(t);
        assertTrue("@startuml はディレクティブ着色されるはず", colorOf(t, spans, "@startuml") != null);
        assertTrue("class はキーワード着色されるはず", colorOf(t, spans, "class") != null);
        assertTrue("--> は矢印着色されるはず", colorOf(t, spans, "-->") != null);
    }

    @Test
    public void stringsAndCommentsAndStereotypesGetSpans() {
        String t = "@startuml\n' a comment\nclass A <<entity>>\nnote right: \"hi\"\n@enduml\n";
        List<Span> spans = PlantUmlHighlighter.highlight(t);
        assertTrue("行コメントは着色されるはず", colorOf(t, spans, "' a comment") != null);
        assertTrue("ステレオタイプ <<entity>> は着色されるはず",
                colorOf(t, spans, "<<entity>>") != null);
        assertTrue("文字列 \"hi\" は着色されるはず", colorOf(t, spans, "\"hi\"") != null);
    }

    @Test
    public void keywordInsideCommentIsNotColoredAsKeyword() {
        // コメント内の "class" はキーワードではなくコメント色 (= コメントスパンの一部)。
        String t = "@startuml\n' this class is fake\n@enduml\n";
        List<Span> spans = PlantUmlHighlighter.highlight(t);
        Color commentColor = colorOf(t, spans, "' this");
        Color classColor = colorOf(t, spans, "class is fake");
        // コメント内の class はコメントスパンに含まれる (別のキーワードスパンにならない)。
        assertTrue("コメント色が付くはず", commentColor != null);
        assertTrue("コメント内 class はコメント色のまま", commentColor.equals(classColor));
    }

    @Test
    public void nullAndEmptyAreSafe() {
        assertTrue(PlantUmlHighlighter.highlight(null).isEmpty());
        assertTrue(PlantUmlHighlighter.highlight("").isEmpty());
    }

    @Test
    public void plainIdentifierIsNotColored() {
        // キーワードでない識別子には (キーワード) スパンが付かない。
        String t = "@startuml\nSomeRandomName\n@enduml\n";
        List<Span> spans = PlantUmlHighlighter.highlight(t);
        assertFalse("非キーワード識別子は着色しない",
                spans.stream().anyMatch(s -> {
                    int at = t.indexOf("SomeRandomName");
                    return at >= s.start && at < s.start + s.length;
                }));
    }
}
