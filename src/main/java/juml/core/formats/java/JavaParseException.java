// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java;

/**
 * Java ソースの解析時に発生する例外。
 * 完全な Java コンパイラではないため、原因不明の構文の場合は
 * 例外を投げず可能な限り処理を続行するが、明らかな致命的エラーは本例外を投げる。
 */
public class JavaParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int line;

    public JavaParseException(String message) {
        this(message, -1);
    }

    public JavaParseException(String message, int line) {
        super(line >= 0 ? ("line " + line + ": " + message) : message);
        this.line = line;
    }

    public int getLine() {
        return line;
    }
}
