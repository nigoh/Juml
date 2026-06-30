// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java;

/**
 * Java ソース字句解析器が生成するトークン。
 *
 * <p>{@code start}/{@code end} はソース文字列上の半開区間 [start, end)
 * を表す。これにより、後段の構文解析器はトークン間の空白を含めて
 * 元のソースをスライスすることでフォーマットを保ったまま式を再構築できる。</p>
 */
public final class JavaToken {

    /** トークン種別。 */
    public enum Type { IDENT, NUMBER, STRING, CHAR, PUNCT, OP, EOF }

    public final Type type;
    public final String text;
    public final int line;
    public final int start;
    public final int end;

    public JavaToken(Type type, String text, int line, int start, int end) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.start = start;
        this.end = end;
    }

    public boolean is(String s) {
        return text.equals(s);
    }

    public boolean isKw(String kw) {
        return type == Type.IDENT && text.equals(kw);
    }
}
