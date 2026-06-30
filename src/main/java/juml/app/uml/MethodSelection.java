// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

/**
 * メソッドノード選択時のコールバックに渡される値。
 *
 * <p>シーケンス図起点として {@code owner.getSimpleName() + "." + method.getName()}
 * を組み立てれば良い ({@link #getEntry()})。{@link ProjectTreePanel} から分離して
 * 1 ファイルの責務を絞っている。</p>
 */
public final class MethodSelection {
    private final JavaClassInfo owner;
    private final JavaMethodInfo method;

    public MethodSelection(JavaClassInfo owner, JavaMethodInfo method) {
        this.owner = owner;
        this.method = method;
    }

    public JavaClassInfo getOwner() {
        return owner;
    }

    public JavaMethodInfo getMethod() {
        return method;
    }

    /** {@code "Class.method"} 形式のシーケンス図起点文字列。 */
    public String getEntry() {
        return owner.getSimpleName() + "." + method.getName();
    }
}
