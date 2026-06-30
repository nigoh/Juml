// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.List;

/**
 * Smali の {@code .field} 宣言 1 件の解析結果。
 *
 * <p>例: {@code .field private static final TAG:Ljava/lang/String; = "Foo"} は
 * name={@code TAG} / type={@code java.lang.String} / modifiers=[private, static, final] /
 * constantValue={@code "Foo"} として保持される。</p>
 */
public final class SmaliFieldInfo {

    private final String name;
    private final String type;
    private final List<String> modifiers;
    private final String constantValue;

    public SmaliFieldInfo(String name, String type, List<String> modifiers,
                          String constantValue) {
        this.name = name == null ? "" : name;
        this.type = type == null ? "" : type;
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        this.constantValue = constantValue;
    }

    /** フィールド名。 */
    public String getName() {
        return name;
    }

    /** 復号済みフィールド型 (例: {@code java.lang.String}, {@code int[]})。 */
    public String getType() {
        return type;
    }

    /** {@code public} / {@code static} / {@code final} などのアクセス修飾子の並び。 */
    public List<String> getModifiers() {
        return modifiers;
    }

    /** static final フィールドの初期値リテラル (無ければ null)。 */
    public String getConstantValue() {
        return constantValue;
    }

    public boolean isStatic() {
        return modifiers.contains("static");
    }

    public boolean isFinal() {
        return modifiers.contains("final");
    }

    public boolean isPublic() {
        return modifiers.contains("public");
    }

    public boolean isPrivate() {
        return modifiers.contains("private");
    }

    public boolean isProtected() {
        return modifiers.contains("protected");
    }

    /** コンパイラ生成 ({@code synthetic}) フィールドか。クラス図では既定で隠す対象。 */
    public boolean isSynthetic() {
        return modifiers.contains("synthetic");
    }

    /** PlantUML の可視性記号 (+ public / - private / # protected / ~ package)。 */
    public char visibilitySymbol() {
        return SmaliAccess.visibilitySymbol(modifiers);
    }
}
