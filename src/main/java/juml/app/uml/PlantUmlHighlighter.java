// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.SourceHighlighter.Span;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PlantUML テキストの簡易シンタックスハイライト。完全な字句解析ではなく、
 * 自由編集エディタ ({@link PumlSourcePanel}) と生成ソース表示の見やすさを優先した
 * ヒューリスティックで着色スパンを返す。
 *
 * <p>{@link SourceHighlighter} の設計 (Span / テーマ追従 Palette / 1 パス走査) に倣い、
 * PlantUML のトークン — {@code @start…/@end…} などのディレクティブ、図種キーワード
 * (class/interface/participant/state/…)、矢印 ({@code -->} / {@code ..>} / {@code <|--})、
 * 文字列、行/ブロックコメント ({@code '…} / {@code /' … '/})、ステレオタイプ ({@code <<…>>})、
 * 色リテラル ({@code #RRGGBB}) — を着色する。あらゆる図種のテキスト編集を読みやすくする。</p>
 */
final class PlantUmlHighlighter {

    // ディレクティブ (@start…/!define など) の制御色。VS Code の control keyword に寄せる。
    private static final Color COL_DIRECTIVE = new Color(0xAF00DB);
    private static final Color COL_DIRECTIVE_DARK = new Color(0xC586C0);
    // 矢印・関係演算子の色 (type/operator 系)。
    private static final Color COL_ARROW = new Color(0x267F99);
    private static final Color COL_ARROW_DARK = new Color(0x4EC9B0);

    /** 1 回の走査で使うトークン配色 (テーマで切り替え)。 */
    private static final class Palette {
        final Color keyword;
        final Color string;
        final Color comment;
        final Color stereotype;
        final Color color;
        final Color directive;
        final Color arrow;

        Palette(boolean dark) {
            keyword = dark ? SourceHighlighter.COL_KEYWORD_DARK : SourceHighlighter.COL_KEYWORD;
            string = dark ? SourceHighlighter.COL_STRING_DARK : SourceHighlighter.COL_STRING;
            comment = dark ? SourceHighlighter.COL_COMMENT_DARK : SourceHighlighter.COL_COMMENT;
            stereotype = dark
                    ? SourceHighlighter.COL_ANNOTATION_DARK : SourceHighlighter.COL_ANNOTATION;
            color = dark ? SourceHighlighter.COL_NUMBER_DARK : SourceHighlighter.COL_NUMBER;
            directive = dark ? COL_DIRECTIVE_DARK : COL_DIRECTIVE;
            arrow = dark ? COL_ARROW_DARK : COL_ARROW;
        }
    }

    /** 図種宣言・制御フロー・レイアウトなど PlantUML で頻出する語 (全図種横断)。 */
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            // 構造・ノード
            "abstract", "class", "interface", "enum", "entity", "annotation", "object",
            "package", "namespace", "node", "folder", "frame", "cloud", "component",
            "artifact", "rectangle", "card", "database", "queue", "stack", "storage",
            "usecase", "actor", "agent", "person", "boundary", "control", "collections",
            // シーケンス
            "participant", "activate", "deactivate", "autonumber", "create", "destroy",
            "note", "over", "ref", "as", "of", "link",
            // 制御フロー・フラグメント
            "alt", "opt", "loop", "par", "break", "critical", "group", "else", "end",
            "if", "then", "elseif", "endif", "repeat", "repeatwhile", "while", "endwhile",
            "fork", "forkagain", "split", "again", "partition", "start", "stop",
            "return", "backward", "detach", "kill",
            // 状態・タイミング
            "state", "concise", "robust", "clock", "binary", "choice",
            // レイアウト・装飾
            "skinparam", "title", "header", "footer", "legend", "caption", "scale",
            "hide", "show", "remove", "together", "left", "right", "top", "bottom",
            "up", "down", "direction", "order", "newpage", "also", "is",
            // ArchiMate / salt など補助
            "archimate", "sprite", "salt"));

    private PlantUmlHighlighter() {
    }

    /** テキストを 1 パス走査して着色スパンを作る。 */
    static List<Span> highlight(String t) {
        List<Span> out = new ArrayList<>();
        if (t == null || t.isEmpty()) {
            return out;
        }
        Palette p = new Palette(EditorColors.isDark());
        int n = t.length();
        int i = 0;
        while (i < n) {
            char c = t.charAt(i);
            // ブロックコメント /' … '/
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '\'') {
                int s = i;
                i += 2;
                while (i + 1 < n && !(t.charAt(i) == '\'' && t.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
                out.add(new Span(s, i - s, p.comment));
                continue;
            }
            // 行コメント '…
            if (c == '\'') {
                int s = i;
                while (i < n && t.charAt(i) != '\n') {
                    i++;
                }
                out.add(new Span(s, i - s, p.comment));
                continue;
            }
            // 文字列 "…"
            if (c == '"') {
                int s = i;
                i++;
                while (i < n && t.charAt(i) != '"' && t.charAt(i) != '\n') {
                    i++;
                }
                i = Math.min(n, i + 1);
                out.add(new Span(s, i - s, p.string));
                continue;
            }
            // ステレオタイプ <<…>>
            if (c == '<' && i + 1 < n && t.charAt(i + 1) == '<') {
                int close = t.indexOf(">>", i + 2);
                int nl = t.indexOf('\n', i);
                if (close >= 0 && (nl < 0 || close < nl)) {
                    out.add(new Span(i, close + 2 - i, p.stereotype));
                    i = close + 2;
                    continue;
                }
            }
            // 色リテラル #RRGGBB / #colorName
            if (c == '#' && i + 1 < n && (isHexOrLetter(t.charAt(i + 1)))) {
                int s = i;
                i++;
                while (i < n && isHexOrLetter(t.charAt(i))) {
                    i++;
                }
                out.add(new Span(s, i - s, p.color));
                continue;
            }
            // ディレクティブ @start… / @end… / @word、プリプロセッサ !define など
            if ((c == '@' || c == '!') && i + 1 < n
                    && Character.isLetter(t.charAt(i + 1))) {
                int s = i;
                i++;
                while (i < n && (Character.isLetterOrDigit(t.charAt(i)) || t.charAt(i) == '_')) {
                    i++;
                }
                out.add(new Span(s, i - s, p.directive));
                continue;
            }
            // 矢印・関係演算子: -.<>| の 2 文字以上の連なり (--> / ..> / <|-- / -> など)
            if (isArrowChar(c)) {
                int s = i;
                while (i < n && isArrowChar(t.charAt(i))) {
                    i++;
                }
                if (i - s >= 2) {
                    out.add(new Span(s, i - s, p.arrow));
                }
                continue;
            }
            // 識別子 / キーワード
            if (Character.isLetter(c) || c == '_') {
                int s = i;
                while (i < n && (Character.isLetterOrDigit(t.charAt(i)) || t.charAt(i) == '_')) {
                    i++;
                }
                if (KEYWORDS.contains(t.substring(s, i))) {
                    out.add(new Span(s, i - s, p.keyword));
                }
                continue;
            }
            i++;
        }
        return out;
    }

    private static boolean isArrowChar(char c) {
        return c == '-' || c == '.' || c == '<' || c == '>' || c == '|';
    }

    private static boolean isHexOrLetter(char c) {
        return Character.isLetterOrDigit(c);
    }
}
