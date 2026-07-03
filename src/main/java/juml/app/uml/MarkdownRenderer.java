// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依存ライブラリを増やさないための、Markdown サブセット → HTML 変換。
 *
 * <p>付箋メモの表示用途に絞った最小実装。対応構文: 見出し ({@code #}〜{@code ###})、
 * 箇条書き ({@code - } / {@code * })、番号付き ({@code 1. })、引用 ({@code > })、
 * コードフェンス ({@code ```})、インラインの {@code **太字**} / {@code *斜体*} /
 * {@code `コード`} / {@code [text](url)}。空行で段落区切り、行内改行は {@code <br>}。</p>
 *
 * <p>出力は Swing の {@link javax.swing.JEditorPane} (HTML 3.2 相当) で描画する前提。</p>
 */
final class MarkdownRenderer {

    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?!\\*)");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    private MarkdownRenderer() {
    }

    /** Markdown サブセットを HTML 本体断片 (body 中身) に変換する。 */
    static String toHtml(String markdown) {
        StringBuilder out = new StringBuilder();
        String[] lines = (markdown == null ? "" : markdown).replace("\r\n", "\n").split("\n", -1);
        boolean inCode = false;
        String listTag = null; // "ul" / "ol" / null
        StringBuilder para = new StringBuilder();

        for (String raw : lines) {
            String line = raw;
            if (line.trim().startsWith("```")) {
                flushParagraph(out, para);
                if (inCode) {
                    out.append("</pre>");
                    inCode = false;
                } else {
                    listTag = closeList(out, listTag);
                    out.append("<pre>");
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                out.append(escape(line)).append('\n');
                continue;
            }
            if (line.trim().isEmpty()) {
                flushParagraph(out, para);
                listTag = closeList(out, listTag);
                continue;
            }
            Matcher heading = Pattern.compile("^(#{1,3})\\s+(.*)$").matcher(line);
            if (heading.matches()) {
                flushParagraph(out, para);
                listTag = closeList(out, listTag);
                // # -> h1 / ## -> h2 / ### -> h3。見た目のサイズは wrapDocument の
                // インライン CSS で抑えるため、記法の意味論 (見出しレベル) は保つ。
                int level = heading.group(1).length();
                out.append("<h").append(level).append('>')
                        .append(inline(heading.group(2)))
                        .append("</h").append(level).append('>');
                continue;
            }
            if (line.matches("^\\s*[-*]\\s+.*")) {
                flushParagraph(out, para);
                listTag = openList(out, listTag, "ul");
                out.append("<li>").append(inline(line.replaceFirst("^\\s*[-*]\\s+", "")))
                        .append("</li>");
                continue;
            }
            if (line.matches("^\\s*\\d+\\.\\s+.*")) {
                flushParagraph(out, para);
                listTag = openList(out, listTag, "ol");
                out.append("<li>").append(inline(line.replaceFirst("^\\s*\\d+\\.\\s+", "")))
                        .append("</li>");
                continue;
            }
            if (line.matches("^\\s*>\\s?.*")) {
                flushParagraph(out, para);
                listTag = closeList(out, listTag);
                out.append("<blockquote>")
                        .append(inline(line.replaceFirst("^\\s*>\\s?", "")))
                        .append("</blockquote>");
                continue;
            }
            // 通常テキスト行: 段落に蓄積 (行内改行は <br>)
            if (para.length() > 0) {
                para.append("<br>");
            }
            para.append(inline(line));
        }
        if (inCode) {
            out.append("</pre>");
        }
        flushParagraph(out, para);
        closeList(out, listTag);
        return out.toString();
    }

    /**
     * 本体 HTML 断片を JEditorPane (HTML 3.2) 用の完全文書に包む。見出し/リスト/引用の
     * サイズと余白をインライン CSS で抑え、小さな付箋でも破綻しにくくする。付箋表示
     * (margin 0) とプレビュー (margin 6) で共通利用し、見た目の一貫性を保つ。
     *
     * @param body      {@link #toHtml} 等で得た body 中身
     * @param marginPx  body マージン
     * @param bodyFontPx 本文フォント (見出しはこれを基準に +1〜+4)
     */
    static String wrapDocument(String body, int marginPx, int bodyFontPx) {
        return "<html><head><style>"
                + "body{margin:" + marginPx + "px;font-family:sans-serif;font-size:"
                + bodyFontPx + "px;color:#222;}"
                + "h1{font-size:" + (bodyFontPx + 4) + "px;margin:2px 0;}"
                + "h2{font-size:" + (bodyFontPx + 2) + "px;margin:2px 0;}"
                + "h3{font-size:" + (bodyFontPx + 1) + "px;margin:2px 0;}"
                + "p{margin:2px 0;}ul,ol{margin:2px 0 2px 16px;}"
                + "blockquote{margin:2px 0;padding-left:6px;color:#555;}"
                + "pre{margin:2px 0;}code{font-family:monospace;}"
                + "</style></head><body>" + body + "</body></html>";
    }

    private static void flushParagraph(StringBuilder out, StringBuilder para) {
        if (para.length() > 0) {
            out.append("<p>").append(para).append("</p>");
            para.setLength(0);
        }
    }

    private static String openList(StringBuilder out, String current, String want) {
        if (current != null && current.equals(want)) {
            return current;
        }
        if (current != null) {
            out.append("</").append(current).append('>');
        }
        out.append('<').append(want).append('>');
        return want;
    }

    private static String closeList(StringBuilder out, String current) {
        if (current != null) {
            out.append("</").append(current).append('>');
        }
        return null;
    }

    /** インライン装飾を適用する。エスケープ後に置換で行う。 */
    private static String inline(String text) {
        String s = escape(text);
        // インラインコードを先に処理し、中身は Markdown 的にリテラル扱いにする。
        // 生成した <code>…</code> をそのまま残すと後段の LINK/BOLD/ITALIC が
        // コード内を再装飾してしまうため、一旦プレースホルダへ退避して最後に戻す。
        java.util.List<String> codeSpans = new java.util.ArrayList<>();
        s = replace(s, CODE, m -> {
            codeSpans.add("<code>" + m.group(1) + "</code>");
            return "\uE000" + (codeSpans.size() - 1) + "\uE001";
        });
        // href は属性値なので引用符も無害化する (escape 済み文字列に対してさらに ")。
        s = replace(s, LINK, m -> "<a href=\"" + m.group(2).replace("\"", "&quot;") + "\">"
                + m.group(1) + "</a>");
        s = replace(s, BOLD, m -> "<b>" + m.group(1) + "</b>");
        s = replace(s, ITALIC, m -> "<i>" + m.group(1) + "</i>");
        for (int i = 0; i < codeSpans.size(); i++) {
            s = s.replace("\uE000" + i + "\uE001", codeSpans.get(i));
        }
        return s;
    }

    private interface Repl {
        String apply(Matcher m);
    }

    private static String replace(String s, Pattern p, Repl r) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(r.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String escape(String s) {
        // コードスパンのプレースホルダに私用領域文字 (U+E000/U+E001) を使うため、
        // 入力に紛れ込んだ同領域の文字は先に除去してプレースホルダ衝突を防ぐ
        // (通常テキストには現れないが、貼り付け由来の制御文字対策)。
        String cleaned = PUA.matcher(s).replaceAll("");
        return cleaned.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 私用領域 (Private Use Area) の文字。プレースホルダ衝突防止のため除去する。 */
    private static final Pattern PUA = Pattern.compile("[\\uE000-\\uF8FF]");
}
