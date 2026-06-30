// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.ArrayList;
import java.util.List;

/**
 * Smali の {@code .method} 宣言 1 件の解析結果 (シグネチャのみ。本体命令は保持しない)。
 *
 * <p>例: {@code .method public static foo(ILjava/lang/String;)Z} は
 * name={@code foo} / parameterTypes=[int, java.lang.String] / returnType={@code boolean} /
 * modifiers=[public, static] として保持される。コンストラクタ {@code <init>} と
 * 静的初期化子 {@code <clinit>} は名前で判別できる。</p>
 */
public final class SmaliMethodInfo {

    private final String name;
    private final List<String> parameterTypes;
    private final String returnType;
    private final List<String> modifiers;
    // メソッド本体から収集した invoke-* 命令 (シーケンス図用)。シグネチャと違い
    // 後追いで追加されるため可変リストにする。本体を解析しない場合は空のまま。
    private final List<SmaliInvoke> invokes = new ArrayList<>();

    public SmaliMethodInfo(String name, List<String> parameterTypes, String returnType,
                           List<String> modifiers) {
        this.name = name == null ? "" : name;
        this.parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
        this.returnType = returnType == null ? "" : returnType;
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
    }

    /** メソッド名 ({@code <init>} / {@code <clinit>} を含む)。 */
    public String getName() {
        return name;
    }

    /** 復号済みパラメータ型の並び。 */
    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    /** 復号済み戻り値型。 */
    public String getReturnType() {
        return returnType;
    }

    /** アクセス修飾子の並び。 */
    public List<String> getModifiers() {
        return modifiers;
    }

    /**
     * メソッド本体から収集した {@code invoke-*} 命令 (出現順)。シグネチャのみパースした
     * 場合は空。{@link SmaliParser} が本体を読むと埋まる。
     */
    public List<SmaliInvoke> getInvokes() {
        return invokes;
    }

    public boolean isStatic() {
        return modifiers.contains("static");
    }

    public boolean isAbstract() {
        return modifiers.contains("abstract");
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

    /** インスタンス/静的コンストラクタか ({@code <init>} もしくは {@code <clinit>})。 */
    public boolean isConstructor() {
        return "<init>".equals(name) || modifiers.contains("constructor");
    }

    /** 静的初期化子 {@code <clinit>} か。 */
    public boolean isStaticInitializer() {
        return "<clinit>".equals(name);
    }

    /** コンパイラ生成 ({@code synthetic} / {@code bridge}) メソッドか。クラス図では既定で隠す。 */
    public boolean isSynthetic() {
        return modifiers.contains("synthetic") || modifiers.contains("bridge");
    }

    /** PlantUML の可視性記号 (+ public / - private / # protected / ~ package)。 */
    public char visibilitySymbol() {
        return SmaliAccess.visibilitySymbol(modifiers);
    }

    /** {@code foo(int, java.lang.String): boolean} のような表示用シグネチャ。 */
    public String displaySignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('(');
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(SmaliTypeDescriptor.simpleName(parameterTypes.get(i)));
        }
        sb.append(')');
        if (!returnType.isEmpty() && !"void".equals(returnType) && !isConstructor()) {
            sb.append(": ").append(SmaliTypeDescriptor.simpleName(returnType));
        }
        return sb.toString();
    }
}
