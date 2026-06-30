// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.List;

/**
 * smali メソッド本体中の 1 つの {@code invoke-*} 命令 (メソッド呼び出し) の解析結果。
 *
 * <p>例: {@code invoke-virtual {p0, v0}, Lcom/example/Presenter;->load(I)V} は
 * ownerClass={@code com.example.Presenter} / methodName={@code load} /
 * parameterTypes=[int] / returnType={@code void} / kind={@code virtual} として保持される。
 * シーケンス図の「メッセージ」を組み立てるための最小情報。</p>
 */
public final class SmaliInvoke {

    private final String ownerClass;
    private final String methodName;
    private final List<String> parameterTypes;
    private final String returnType;
    private final String kind;

    public SmaliInvoke(String ownerClass, String methodName, List<String> parameterTypes,
                       String returnType, String kind) {
        this.ownerClass = ownerClass == null ? "" : ownerClass;
        this.methodName = methodName == null ? "" : methodName;
        this.parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
        this.returnType = returnType == null ? "" : returnType;
        this.kind = kind == null ? "" : kind;
    }

    /** 呼び出し先クラスの FQN (例: {@code com.example.Presenter})。 */
    public String getOwnerClass() {
        return ownerClass;
    }

    /** 呼び出し先メソッド名 ({@code <init>} を含む)。 */
    public String getMethodName() {
        return methodName;
    }

    /** 復号済みパラメータ型の並び。 */
    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    /** 復号済み戻り値型。 */
    public String getReturnType() {
        return returnType;
    }

    /** {@code virtual} / {@code direct} / {@code static} / {@code interface} / {@code super}。 */
    public String getKind() {
        return kind;
    }

    /** {@code load(int)} のような表示用ラベル (パラメータは単純名)。 */
    public String displayLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append('(');
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(SmaliTypeDescriptor.simpleName(parameterTypes.get(i)));
        }
        sb.append(')');
        return sb.toString();
    }
}
