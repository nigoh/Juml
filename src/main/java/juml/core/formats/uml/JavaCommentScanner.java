// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java/AIDL ソース文字列から {@code // ...} / {@code /* ... *}{@code /} / {@code /** ... *}{@code /}
 * のコメント位置を収集する補助クラス。
 *
 * <p>{@link JavaLexer} はトークン化時にコメントを捨ててしまうため、JavaDoc / 行コメントを
 * クラス・フィールド・メソッドに割り当てたい場合にはこのスキャナで別途位置情報を抽出する。
 * 文字列・文字リテラル内の {@code //} や {@code /*} はコメントとして拾わない。</p>
 */
public final class JavaCommentScanner {

    /** コメント種別。 */
    public enum Kind { LINE, BLOCK, JAVADOC }

    /** スキャン結果の 1 件。位置はソース文字列のオフセット (半開区間 [start, end))。 */
    public static final class Comment {
        public final int start;
        public final int end;
        public final int line;
        public final Kind kind;
        /** デリミタ ({@code //}, {@code /*}, {@code *}{@code /}) を含まない生本文。 */
        public final String text;

        public Comment(int start, int end, int line, Kind kind, String text) {
            this.start = start;
            this.end = end;
            this.line = line;
            this.kind = kind;
            this.text = text;
        }
    }

    /** ソース文字列を走査してコメント一覧を返す。出現順 (start 昇順)。 */
    public static List<Comment> scan(String src) {
        List<Comment> out = new ArrayList<>();
        if (src == null || src.isEmpty()) {
            return out;
        }
        int n = src.length();
        int i = 0;
        int line = 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (c == '"' && i + 2 < n
                    && src.charAt(i + 1) == '"'
                    && src.charAt(i + 2) == '"') {
                int afterStart = i + 3;
                int j = afterStart;
                while (j + 2 < n) {
                    char cj = src.charAt(j);
                    if (cj == '\\' && j + 1 < n) {
                        if (src.charAt(j + 1) == '\n') {
                            line++;
                        }
                        j += 2;
                        continue;
                    }
                    if (cj == '"' && src.charAt(j + 1) == '"' && src.charAt(j + 2) == '"') {
                        j += 3;
                        break;
                    }
                    if (cj == '\n') {
                        line++;
                    }
                    j++;
                }
                i = Math.max(j, afterStart);
                continue;
            }
            if (c == '"') {
                i = skipStringLiteral(src, i, n);
                continue;
            }
            if (c == '\'') {
                i = skipCharLiteral(src, i, n);
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int s = i;
                int startLine = line;
                i += 2;
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                out.add(new Comment(s, i, startLine, Kind.LINE, src.substring(s + 2, i)));
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int s = i;
                int startLine = line;
                // JavaDoc は /** ... */。/**/ は空コメントなので JavaDoc 扱いしない。
                boolean doc = i + 2 < n && src.charAt(i + 2) == '*'
                        && !(i + 3 < n && src.charAt(i + 3) == '/');
                i += 2;
                while (i + 1 < n
                        && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        line++;
                    }
                    i++;
                }
                int contentEnd = i;
                i = Math.min(i + 2, n);
                String body = src.substring(s + 2, contentEnd);
                out.add(new Comment(s, i, startLine, doc ? Kind.JAVADOC : Kind.BLOCK, body));
                continue;
            }
            i++;
        }
        return out;
    }

    /** {@link Comment} の本文から表示用テキストを整形する (先頭の {@code *} 除去、空行除去、{@code @tag} 除去)。 */
    public static String cleanText(Comment c) {
        if (c == null || c.text == null) {
            return "";
        }
        String[] lines = c.text.replace("\r", "").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        boolean seenContent = false;
        for (String raw : lines) {
            String t = raw.trim();
            if (t.startsWith("*")) {
                t = t.substring(1).trim();
            }
            if (t.isEmpty()) {
                continue;
            }
            // JavaDoc の @param/@return 等の構造化タグは図には載せない
            if (c.kind == Kind.JAVADOC && t.startsWith("@")) {
                continue;
            }
            if (seenContent) {
                sb.append('\n');
            }
            sb.append(t);
            seenContent = true;
        }
        // インラインタグ展開は結合後にまとめて行う。{@link が行末で開き次行で閉じる
        // 複数行タグも残骸 ({@link) を残さず展開できるようにするため。
        return stripInlineTags(sb.toString()).trim();
    }

    /** JavaDoc インラインタグ ({@code {@link ...}} / {@code {@code ...}} 等) を表示用に展開する。 */
    private static final Pattern INLINE_TAG =
            Pattern.compile("\\{@(\\w+)(?:\\s+([^}]*))?\\}");

    static String stripInlineTags(String s) {
        if (s == null || s.indexOf("{@") < 0) {
            return s;
        }
        Matcher m = INLINE_TAG.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String tag = m.group(1);
            String content = m.group(2) == null ? "" : m.group(2).trim();
            String display;
            if (("link".equals(tag) || "linkplain".equals(tag)) && !content.isEmpty()) {
                display = linkDisplay(content);
            } else {
                // {@code x} / {@literal x} / {@value X} などは中身をそのまま使う
                display = content;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(display));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * {@code {@link ref label}} の表示文字列を決める。ラベルがあればラベルを、
     * 無ければ参照を返す。参照とラベルの境界は括弧の外側の空白で判定するため、
     * {@code Foo#bar(int, String[])} のように参照内に空白/カンマがあっても壊れない。
     * 先頭の '#' (同クラス参照) は落とす。
     */
    private static String linkDisplay(String content) {
        int depth = 0;
        int refEnd = content.length();
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (Character.isWhitespace(ch) && depth <= 0) {
                refEnd = i;
                break;
            }
        }
        String label = content.substring(refEnd).trim();
        if (!label.isEmpty()) {
            return label;
        }
        String ref = content.substring(0, refEnd).trim();
        return ref.startsWith("#") ? ref.substring(1) : ref;
    }

    /** 整形済みコメントの 1 行目だけを返す (インライン表示用)。 */
    public static String firstLine(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) {
            return "";
        }
        int nl = cleaned.indexOf('\n');
        return (nl < 0 ? cleaned : cleaned.substring(0, nl)).trim();
    }

    /**
     * {@code pos} の直前に隣接する (間に非空白文字を挟まない) コメントを整形して返す。
     * 連続する行コメント ({@code //}) は 1 ブロックとしてマージする。
     * 該当が無ければ null。
     */
    public static String findCommentBefore(String src, List<Comment> comments, int pos) {
        if (pos < 0 || comments == null || comments.isEmpty()) {
            return null;
        }
        int hit = -1;
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).end <= pos) {
                hit = i;
            } else {
                break;
            }
        }
        if (hit < 0 || !isOnlyWhitespace(src, comments.get(hit).end, pos)) {
            return null;
        }
        int first = hit;
        if (comments.get(hit).kind == Kind.LINE) {
            while (first > 0 && comments.get(first - 1).kind == Kind.LINE
                    && isOnlyWhitespace(src,
                            comments.get(first - 1).end,
                            comments.get(first).start)) {
                first--;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = first; i <= hit; i++) {
            String t = cleanText(comments.get(i));
            if (t.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(t);
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isOnlyWhitespace(String src, int from, int to) {
        for (int i = from; i < to; i++) {
            if (!Character.isWhitespace(src.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int skipStringLiteral(String src, int from, int n) {
        int i = from + 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '"') {
                return i + 1;
            }
            if (c == '\n') {
                // 文字列内改行 (テキストブロック以外では構文エラーだが、走査は継続する)
                return i;
            }
            i++;
        }
        return i;
    }

    private static int skipCharLiteral(String src, int from, int n) {
        int i = from + 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\\' && i + 1 < n) {
                i += 2;
                continue;
            }
            if (c == '\'') {
                return i + 1;
            }
            if (c == '\n') {
                return i;
            }
            i++;
        }
        return i;
    }

    private JavaCommentScanner() {
    }
}
