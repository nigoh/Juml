// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

/**
 * Java/AIDL のメンバー可視性。
 */
public enum Visibility {
    PUBLIC("+"),
    PROTECTED("#"),
    PACKAGE("~"),
    PRIVATE("-");

    private final String mark;

    Visibility(String mark) {
        this.mark = mark;
    }

    /** PlantUML で使用される可視性記号 ({@code +}, {@code -}, {@code #}, {@code ~})。 */
    public String mark() {
        return mark;
    }

    /** 修飾子文字列のいずれかが含まれていれば対応する可視性を返す。 */
    public static Visibility fromModifiers(java.util.Collection<String> modifiers) {
        if (modifiers == null) {
            return PACKAGE;
        }
        if (modifiers.contains("public")) {
            return PUBLIC;
        }
        if (modifiers.contains("protected")) {
            return PROTECTED;
        }
        if (modifiers.contains("private")) {
            return PRIVATE;
        }
        return PACKAGE;
    }
}
