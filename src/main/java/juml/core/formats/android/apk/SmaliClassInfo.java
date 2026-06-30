// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.ArrayList;
import java.util.List;

/**
 * 1 つの {@code .smali} ファイル (= 1 クラス) の解析結果。
 *
 * <p>Apktool の逆アセンブル出力から、クラス名・スーパークラス・実装インタフェース・
 * フィールド・メソッドのシグネチャを取り出して保持する。バイトコードの命令本体は
 * 解析対象外で、UML 図に必要な構造ヘッダのみを抽出する点が方針 (ASM ヘッダ抽出と同様)。</p>
 */
public final class SmaliClassInfo {

    private final String className;
    private String superClass;
    private final List<String> interfaces = new ArrayList<>();
    private final List<String> modifiers = new ArrayList<>();
    private String sourceFile;
    private final List<SmaliFieldInfo> fields = new ArrayList<>();
    private final List<SmaliMethodInfo> methods = new ArrayList<>();

    public SmaliClassInfo(String className) {
        this.className = className == null ? "" : className;
    }

    /** ドット区切りの FQN (例: {@code com.example.MainActivity})。 */
    public String getClassName() {
        return className;
    }

    /** 単純名 (例: {@code MainActivity})。 */
    public String getSimpleName() {
        return SmaliTypeDescriptor.simpleName(className);
    }

    /** パッケージ名 (例: {@code com.example})。 */
    public String getPackageName() {
        return SmaliTypeDescriptor.packageOf(className);
    }

    /** スーパークラスの FQN (無ければ null。{@code java.lang.Object} の場合もそのまま保持)。 */
    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    /** 実装インタフェースの FQN 一覧。 */
    public List<String> getInterfaces() {
        return interfaces;
    }

    /** {@code public} / {@code abstract} / {@code final} / {@code interface} などの修飾子。 */
    public List<String> getModifiers() {
        return modifiers;
    }

    /** {@code .source} 指定のオリジナルソースファイル名 (無ければ null)。 */
    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public List<SmaliFieldInfo> getFields() {
        return fields;
    }

    public List<SmaliMethodInfo> getMethods() {
        return methods;
    }

    public boolean isInterface() {
        return modifiers.contains("interface");
    }

    public boolean isAbstract() {
        return modifiers.contains("abstract");
    }

    public boolean isEnum() {
        return modifiers.contains("enum");
    }

    public boolean isAnnotation() {
        return modifiers.contains("annotation");
    }

    public boolean isPublic() {
        return modifiers.contains("public");
    }

    /** コンパイラ生成 ({@code synthetic}) クラスか。 */
    public boolean isSynthetic() {
        return modifiers.contains("synthetic");
    }

    /** 内部クラスか ({@code Outer$Inner} 形式の判定)。 */
    public boolean isInnerClass() {
        return className.indexOf('$') >= 0;
    }

    /**
     * PlantUML のクラス種別キーワード ({@code class} / {@code abstract class} /
     * {@code interface} / {@code enum} / {@code annotation})。
     */
    public String umlKind() {
        if (isAnnotation()) {
            return "annotation";
        }
        if (isInterface()) {
            return "interface";
        }
        if (isEnum()) {
            return "enum";
        }
        if (isAbstract()) {
            return "abstract class";
        }
        return "class";
    }
}
