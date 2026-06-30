// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java / Kotlin ソースの簡易シンタックスハイライト。完全な字句解析ではなく、
 * 表示用の見やすさを優先したヒューリスティックで着色スパンを返す。
 *
 * <p>{@link JavaSourcePanel} から分離して 1 ファイルの責務を絞っている
 * (色の定義とトークン走査のみ)。Java text block / Kotlin raw string
 * ({@code """ ... """}) の複数行文字列にも対応する。</p>
 */
final class SourceHighlighter {

    // VS Code (light) に寄せたトークン配色。
    static final Color COL_KEYWORD = new Color(0x0000FF);
    static final Color COL_STRING = new Color(0xA31515);
    static final Color COL_COMMENT = new Color(0x008000);
    static final Color COL_ANNOTATION = new Color(0x808000);
    static final Color COL_NUMBER = new Color(0x098658);

    // VS Code Dark+ に寄せたトークン配色 (ダークテーマ時に使用)。
    static final Color COL_KEYWORD_DARK = new Color(0x569CD6);
    static final Color COL_STRING_DARK = new Color(0xCE9178);
    static final Color COL_COMMENT_DARK = new Color(0x6A9955);
    static final Color COL_ANNOTATION_DARK = new Color(0xDCDCAA);
    static final Color COL_NUMBER_DARK = new Color(0xB5CEA8);

    /** 1 回の走査で使うトークン配色 (テーマで切り替え)。 */
    private static final class Palette {
        final Color keyword;
        final Color string;
        final Color comment;
        final Color annotation;
        final Color number;

        Palette(boolean dark) {
            keyword = dark ? COL_KEYWORD_DARK : COL_KEYWORD;
            string = dark ? COL_STRING_DARK : COL_STRING;
            comment = dark ? COL_COMMENT_DARK : COL_COMMENT;
            annotation = dark ? COL_ANNOTATION_DARK : COL_ANNOTATION;
            number = dark ? COL_NUMBER_DARK : COL_NUMBER;
        }
    }

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            // Java
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "var", "record", "sealed", "permits",
            "yield", "true", "false", "null",
            // Kotlin 追加分
            "fun", "val", "object", "when", "is", "in", "out", "data", "companion",
            "override", "open", "internal", "suspend", "lateinit", "init", "constructor",
            "typealias", "by", "where"));

    private SourceHighlighter() {
    }

    /** 着色スパン (開始オフセット / 長さ / 色)。 */
    static final class Span {
        final int start;
        final int length;
        final Color color;

        Span(int start, int length, Color color) {
            this.start = start;
            this.length = length;
            this.color = color;
        }
    }

    /**
     * テキストを 1 パス走査して着色スパンを作る。
     *
     * @param t      ソース全文
     * @param kotlin Kotlin として扱うか (現状はフラグのみ保持、走査は共通)
     */
    static List<Span> highlight(String t, boolean kotlin) {
        List<Span> out = new ArrayList<>();
        Palette p = new Palette(EditorColors.isDark());
        int n = t.length();
        int i = 0;
        while (i < n) {
            char c = t.charAt(i);
            // 行コメント
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '/') {
                int s = i;
                while (i < n && t.charAt(i) != '\n') {
                    i++;
                }
                out.add(new Span(s, i - s, p.comment));
                continue;
            }
            // ブロックコメント
            if (c == '/' && i + 1 < n && t.charAt(i + 1) == '*') {
                int s = i;
                i += 2;
                while (i + 1 < n && !(t.charAt(i) == '*' && t.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(n, i + 2);
                out.add(new Span(s, i - s, p.comment));
                continue;
            }
            // 三連クオート文字列 (text block / raw string)
            if (c == '"' && i + 2 < n && t.charAt(i + 1) == '"' && t.charAt(i + 2) == '"') {
                int s = i;
                i += 3;
                while (i + 2 < n && !(t.charAt(i) == '"' && t.charAt(i + 1) == '"'
                        && t.charAt(i + 2) == '"')) {
                    i++;
                }
                i = Math.min(n, i + 3);
                out.add(new Span(s, i - s, p.string));
                continue;
            }
            // 文字列 (ダブルクオート)
            if (c == '"') {
                i = scanQuoted(t, n, i, '"', out, p.string);
                continue;
            }
            // 文字 (シングルクオート)
            if (c == '\'') {
                i = scanQuoted(t, n, i, '\'', out, p.string);
                continue;
            }
            // アノテーション @Name
            if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(t.charAt(i + 1))) {
                int s = i;
                i++;
                while (i < n && Character.isJavaIdentifierPart(t.charAt(i))) {
                    i++;
                }
                out.add(new Span(s, i - s, p.annotation));
                continue;
            }
            // 数値
            if (Character.isDigit(c)
                    && (i == 0 || !Character.isJavaIdentifierPart(t.charAt(i - 1)))) {
                int s = i;
                while (i < n && (Character.isLetterOrDigit(t.charAt(i)) || t.charAt(i) == '.'
                        || t.charAt(i) == '_' || t.charAt(i) == 'x' || t.charAt(i) == 'X')) {
                    i++;
                }
                out.add(new Span(s, i - s, p.number));
                continue;
            }
            // 識別子 / キーワード
            if (Character.isJavaIdentifierStart(c)) {
                int s = i;
                while (i < n && Character.isJavaIdentifierPart(t.charAt(i))) {
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

    /** {@code quote} で始まる文字列/文字リテラルを走査し、終端の次位置を返す。 */
    private static int scanQuoted(String t, int n, int start, char quote, List<Span> out,
                                  Color stringColor) {
        int i = start + 1;
        while (i < n && t.charAt(i) != quote) {
            if (t.charAt(i) == '\\' && i + 1 < n) {
                i++;
            } else if (t.charAt(i) == '\n') {
                break;
            }
            i++;
        }
        i = Math.min(n, i + 1);
        out.add(new Span(start, i - start, stringColor));
        return i;
    }
}
