// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java;

import java.util.ArrayList;
import java.util.List;

/**
 * Java ソース → JavaToken 列への変換を行う簡易字句解析器。
 *
 * <p>完全な Java 言語仕様を網羅したものではないが、コメント、文字列・文字リテラル、
 * 識別子、数値、複合演算子 (>>, <=, &&, など) を扱える。
 * 制御構造解析に必要なトークンが正しく切り出せれば十分である。</p>
 */
public final class JavaLexer {

    private static final String[] LONG_OPS3 = {">>>", "<<=", ">>=", "..."};
    private static final String[] LONG_OPS2 = {
            "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "<<", ">>", "->", "::"
    };

    private final String src;
    private final int len;
    private int pos;
    private int line;

    public JavaLexer(String src) {
        this.src = src;
        this.len = src.length();
        this.pos = 0;
        this.line = 1;
    }

    public List<JavaToken> tokenize() {
        List<JavaToken> out = new ArrayList<>();
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            if (c == '/' && pos + 1 < len && src.charAt(pos + 1) == '/') {
                while (pos < len && src.charAt(pos) != '\n') {
                    pos++;
                }
                continue;
            }
            if (c == '/' && pos + 1 < len && src.charAt(pos + 1) == '*') {
                skipBlockComment();
                continue;
            }
            if (c == '"' && pos + 2 < len
                    && src.charAt(pos + 1) == '"'
                    && src.charAt(pos + 2) == '"') {
                out.add(readTextBlock());
                continue;
            }
            if (c == '"') {
                out.add(readString('"', JavaToken.Type.STRING));
                continue;
            }
            if (c == '\'') {
                out.add(readString('\'', JavaToken.Type.CHAR));
                continue;
            }
            if (Character.isDigit(c)) {
                out.add(readNumber());
                continue;
            }
            if (isIdentStart(c)) {
                out.add(readIdent());
                continue;
            }
            out.add(readSymbol());
        }
        out.add(new JavaToken(JavaToken.Type.EOF, "", line, len, len));
        return out;
    }

    public String getSource() {
        return src;
    }

    /**
     * Java ソース中の Unicode エスケープ (バックスラッシュ + u + 4 桁の 16 進) を
     * 対応する文字に展開する (JLS 3.3)。u を複数並べる形式も許可する。
     * 直前の連続するバックスラッシュの個数が偶数の場合はエスケープ済みとみなして
     * 展開しない。
     *
     * <p>展開は 1 パスで非再帰的に行う: 展開結果のバックスラッシュが新たな
     * Unicode エスケープを開始することはない。</p>
     */
    public static String expandUnicodeEscapes(String src) {
        if (src == null || src.indexOf("\\u") < 0) {
            return src;
        }
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c != '\\') {
                sb.append(c);
                i++;
                continue;
            }
            // 連続する \\ をまとめて数える
            int slashStart = i;
            while (i < n && src.charAt(i) == '\\') {
                i++;
            }
            int slashes = i - slashStart;
            // 直後が u で、先行する \\ の個数が奇数なら Unicode エスケープ候補
            if (i < n && src.charAt(i) == 'u' && (slashes % 2 == 1)) {
                // 末尾 1 つ以外の \\ は通常文字として出力
                for (int k = 0; k < slashes - 1; k++) {
                    sb.append('\\');
                }
                int uStart = i;
                while (i < n && src.charAt(i) == 'u') {
                    i++;
                }
                if (i + 4 <= n
                        && isHexDigit(src.charAt(i))
                        && isHexDigit(src.charAt(i + 1))
                        && isHexDigit(src.charAt(i + 2))
                        && isHexDigit(src.charAt(i + 3))) {
                    int code = Integer.parseInt(src.substring(i, i + 4), 16);
                    sb.append((char) code);
                    i += 4;
                } else {
                    // 妥当なエスケープではない: 巻き戻して元の \\u... を温存
                    sb.append('\\');
                    sb.append(src, uStart, i);
                }
            } else {
                // u が後続しない / 偶数個の \\ → そのまま出力
                for (int k = 0; k < slashes; k++) {
                    sb.append('\\');
                }
            }
        }
        return sb.toString();
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }

    private void skipBlockComment() {
        pos += 2;
        while (pos + 1 < len
                && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
            if (src.charAt(pos) == '\n') {
                line++;
            }
            pos++;
        }
        pos = Math.min(pos + 2, len);
    }

    /**
     * テキストブロック {@code """ ... """} を読み取る (Java 15+)。
     * 開始は呼び出し時に {@code pos} が最初の {@code "} を指している前提。
     */
    private JavaToken readTextBlock() {
        int start = pos;
        int startLine = line;
        pos += 3; // 開始 """
        while (pos + 2 < len) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < len) {
                if (src.charAt(pos + 1) == '\n') {
                    line++;
                }
                pos += 2;
                continue;
            }
            if (c == '"' && src.charAt(pos + 1) == '"' && src.charAt(pos + 2) == '"') {
                pos += 3;
                return new JavaToken(JavaToken.Type.STRING, src.substring(start, pos), startLine, start, pos);
            }
            if (c == '\n') {
                line++;
            }
            pos++;
        }
        // 終端が見つからないまま EOF: 残りを 1 トークンとして返す
        pos = len;
        return new JavaToken(JavaToken.Type.STRING, src.substring(start, pos), startLine, start, pos);
    }

    private JavaToken readString(char quote, JavaToken.Type type) {
        int start = pos;
        int startLine = line;
        pos++;
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < len) {
                if (src.charAt(pos + 1) == '\n') {
                    line++;
                }
                pos += 2;
                continue;
            }
            if (c == quote) {
                pos++;
                break;
            }
            if (c == '\n') {
                line++;
                break;
            }
            pos++;
        }
        return new JavaToken(type, src.substring(start, pos), startLine, start, pos);
    }

    private JavaToken readNumber() {
        int start = pos;
        int startLine = line;
        while (pos < len) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        return new JavaToken(JavaToken.Type.NUMBER, src.substring(start, pos), startLine, start, pos);
    }

    private JavaToken readIdent() {
        int start = pos;
        int startLine = line;
        while (pos < len) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                pos++;
            } else {
                break;
            }
        }
        return new JavaToken(JavaToken.Type.IDENT, src.substring(start, pos), startLine, start, pos);
    }

    private JavaToken readSymbol() {
        int startLine = line;
        int symStart = pos;
        if (pos + 3 <= len) {
            String s3 = src.substring(pos, pos + 3);
            for (String op : LONG_OPS3) {
                if (op.equals(s3)) {
                    pos += 3;
                    return new JavaToken(JavaToken.Type.OP, s3, startLine, symStart, pos);
                }
            }
        }
        if (pos + 2 <= len) {
            String s2 = src.substring(pos, pos + 2);
            for (String op : LONG_OPS2) {
                if (op.equals(s2)) {
                    pos += 2;
                    return new JavaToken(JavaToken.Type.OP, s2, startLine, symStart, pos);
                }
            }
        }
        char c = src.charAt(pos);
        pos++;
        String s = String.valueOf(c);
        JavaToken.Type t = isPunct(c) ? JavaToken.Type.PUNCT : JavaToken.Type.OP;
        return new JavaToken(t, s, startLine, symStart, pos);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isPunct(char c) {
        return c == '(' || c == ')' || c == '{' || c == '}'
                || c == '[' || c == ']' || c == ','
                || c == ';' || c == '.' || c == ':'
                || c == '@' || c == '?';
    }
}
