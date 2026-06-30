// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.ArrayList;
import java.util.List;

/**
 * Smali (Dalvik bytecode のテキスト表現) の型記述子を、人間が読める Java 風の
 * 型名へ復号するユーティリティ。
 *
 * <p>Apktool が逆アセンブルした {@code .smali} ファイルでは、型はすべて JVM/Dalvik の
 * フィールド記述子で書かれている。例:</p>
 * <ul>
 *   <li>{@code Lcom/example/Foo;} → {@code com.example.Foo}</li>
 *   <li>{@code [I} → {@code int[]}</li>
 *   <li>{@code [[Ljava/lang/String;} → {@code java.lang.String[][]}</li>
 *   <li>{@code V} → {@code void}、{@code Z} → {@code boolean} など</li>
 * </ul>
 *
 * <p>メソッド記述子 {@code (II)Ljava/lang/String;} はパラメータ列と戻り値型に分解できる。
 * 文字列処理のみで完結し、外部プロセス・ネットワークには一切依存しない。</p>
 */
public final class SmaliTypeDescriptor {

    private SmaliTypeDescriptor() {
    }

    /**
     * 単一の型記述子を Java 風の型名に復号する。配列は {@code []} を末尾に付与し、
     * プリミティブは Java 名へ、参照型 {@code L...;} はドット区切り FQN へ変換する。
     * null/空文字はそのまま返す。
     */
    public static String decode(String desc) {
        if (desc == null || desc.isEmpty()) {
            return desc;
        }
        int i = 0;
        int dims = 0;
        while (i < desc.length() && desc.charAt(i) == '[') {
            dims++;
            i++;
        }
        if (i >= desc.length()) {
            return desc;
        }
        String base;
        char c = desc.charAt(i);
        switch (c) {
            case 'V': base = "void"; break;
            case 'Z': base = "boolean"; break;
            case 'B': base = "byte"; break;
            case 'S': base = "short"; break;
            case 'C': base = "char"; break;
            case 'I': base = "int"; break;
            case 'J': base = "long"; break;
            case 'F': base = "float"; break;
            case 'D': base = "double"; break;
            case 'L':
                int semi = desc.indexOf(';', i);
                String inner = semi >= 0 ? desc.substring(i + 1, semi) : desc.substring(i + 1);
                base = inner.replace('/', '.');
                break;
            default:
                base = desc.substring(i);
                break;
        }
        if (dims == 0) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        for (int d = 0; d < dims; d++) {
            sb.append("[]");
        }
        return sb.toString();
    }

    /**
     * クラス記述子 {@code Lcom/foo/Bar;} をドット区切り FQN {@code com.foo.Bar} へ変換する。
     * 内部クラスの {@code $} 区切りは保持する (例: {@code com.foo.Bar$Inner})。
     * {@link #decode} のクラス専用エイリアスで、{@code .class} / {@code .super} /
     * {@code .implements} 行のオペランドに使う。
     */
    public static String decodeClassName(String desc) {
        return decode(desc);
    }

    /** FQN {@code com.foo.Bar$Inner} の単純名 {@code Bar$Inner} を返す。 */
    public static String simpleName(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return fqn;
        }
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    /** FQN {@code com.foo.Bar} のパッケージ部 {@code com.foo} を返す。無ければ空文字。 */
    public static String packageOf(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(0, dot) : "";
    }

    /** メソッド記述子の解析結果 (パラメータ型リストと戻り値型)。 */
    public static final class Method {
        private final List<String> parameterTypes;
        private final String returnType;

        Method(List<String> parameterTypes, String returnType) {
            this.parameterTypes = parameterTypes;
            this.returnType = returnType;
        }

        /** 復号済みパラメータ型の並び (空なら引数なし)。 */
        public List<String> getParameterTypes() {
            return parameterTypes;
        }

        /** 復号済み戻り値型 (例: {@code void} / {@code int} / {@code java.lang.String})。 */
        public String getReturnType() {
            return returnType;
        }
    }

    /**
     * メソッド記述子 {@code (params)Return} を解析する。例:
     * {@code (ILjava/lang/String;[B)Z} → params=[int, java.lang.String, byte[]], return=boolean。
     * 括弧が無いなど不正な入力では空パラメータ + 復号した全体を戻り値型として返す。
     */
    public static Method parseMethodDescriptor(String desc) {
        List<String> params = new ArrayList<>();
        if (desc == null || desc.isEmpty()) {
            return new Method(params, "");
        }
        int open = desc.indexOf('(');
        int close = desc.indexOf(')');
        if (open < 0 || close < 0 || close < open) {
            return new Method(params, decode(desc));
        }
        String paramPart = desc.substring(open + 1, close);
        String returnPart = desc.substring(close + 1);
        int i = 0;
        while (i < paramPart.length()) {
            int start = i;
            while (i < paramPart.length() && paramPart.charAt(i) == '[') {
                i++;
            }
            if (i >= paramPart.length()) {
                break;
            }
            char c = paramPart.charAt(i);
            if (c == 'L') {
                int semi = paramPart.indexOf(';', i);
                if (semi < 0) {
                    i = paramPart.length();
                } else {
                    i = semi + 1;
                }
            } else {
                i++;
            }
            params.add(decode(paramPart.substring(start, i)));
        }
        return new Method(params, decode(returnPart));
    }
}
