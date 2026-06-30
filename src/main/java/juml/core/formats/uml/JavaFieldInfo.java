// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * フィールド宣言情報。クラス図生成に必要な最小限のメタデータを保持する。
 */
public class JavaFieldInfo {

    private String name;
    private String type;
    private Visibility visibility = Visibility.PACKAGE;
    private boolean isStatic;
    private boolean isFinal;
    private final List<String> annotations = new ArrayList<>();
    private String comment;
    private String constantValue;
    private final List<JavaMethodInfo> inlineMethods = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    /** {@code static final} 定数の初期化値文字列（{@code "100"} 等）。なければ null。 */
    public String getConstantValue() {
        return constantValue;
    }

    public void setConstantValue(String constantValue) {
        this.constantValue = constantValue;
    }

    /** JavaDoc / 直前コメントを整形した文字列。未取得時は null。 */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * フィールド初期化子が匿名クラスやラムダだった場合、その内部本体から抽出したメソッド一覧。
     * 例: {@code OnClickListener l = new OnClickListener() { onClick(...) { ... } }} なら、
     * {@code onClick} の {@link JavaMethodInfo} が 1 件入る。
     * シーケンス図生成側で {@code l.onClick()} を解決する際に本体を walk するために使う。
     */
    public List<JavaMethodInfo> getInlineMethods() {
        return inlineMethods;
    }
}
