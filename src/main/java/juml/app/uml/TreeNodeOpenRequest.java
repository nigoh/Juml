// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

/**
 * ツリーノードを「新しいタブ」として開くリクエスト。
 *
 * <p>マウス中クリック時に {@link ProjectTreePanel} が組み立て、
 * {@link DiagramTabPane#addOrFocusTab} 等に渡される。</p>
 */
public final class TreeNodeOpenRequest {

    /** 対象タイプ。 */
    public enum Target { METHOD, CLASS, PACKAGE, MODULE, SOONG }

    public final Target target;
    public final DiagramKind kind;
    public final JavaClassInfo classInfo;
    public final JavaMethodInfo methodInfo;
    public final String name;

    private TreeNodeOpenRequest(Target target, DiagramKind kind,
                                 JavaClassInfo classInfo, JavaMethodInfo methodInfo,
                                 String name) {
        this.target = target;
        this.kind = kind;
        this.classInfo = classInfo;
        this.methodInfo = methodInfo;
        this.name = name;
    }

    public static TreeNodeOpenRequest method(JavaClassInfo owner, JavaMethodInfo method,
                                              DiagramKind kind) {
        return new TreeNodeOpenRequest(Target.METHOD, kind, owner, method, null);
    }

    public static TreeNodeOpenRequest classNode(JavaClassInfo c) {
        return new TreeNodeOpenRequest(Target.CLASS, DiagramKind.CLASS, c, null, null);
    }

    public static TreeNodeOpenRequest pkg(String packageName) {
        return new TreeNodeOpenRequest(Target.PACKAGE, DiagramKind.CLASS, null, null, packageName);
    }

    public static TreeNodeOpenRequest module(String moduleName) {
        return new TreeNodeOpenRequest(Target.MODULE, DiagramKind.CLASS, null, null, moduleName);
    }

    /**
     * Soong (Android.bp) 依存図タブを開くリクエスト。
     * {@code name} には選択元のモジュール名 (任意; ツールチップ等の補助表示用) を入れる。
     */
    public static TreeNodeOpenRequest soong(String moduleName) {
        return new TreeNodeOpenRequest(Target.SOONG, DiagramKind.SOONG, null, null, moduleName);
    }

    /** タブ識別用のキー (同じキーなら既存タブにフォーカスする)。 */
    public String tabKey() {
        switch (target) {
            case METHOD:
                // FQN を使う。単純名だと別パッケージの同名クラスでタブが衝突する。
                return kind.name() + ":" + classInfo.getQualifiedName()
                        + "." + methodInfo.getName();
            case CLASS:
                return "CLASS:" + classInfo.getQualifiedName();
            case PACKAGE:
                return "PKG:" + name;
            case MODULE:
                return "MOD:" + name;
            case SOONG:
                // Soong 依存図はプロジェクト単一なのでキーは固定 (既存タブにフォーカス)。
                return "KIND:SOONG";
            default:
                return target.name();
        }
    }

    /** タブヘッダ表示用の短いラベル。 */
    public String displayLabel() {
        switch (target) {
            case METHOD:
                String suffix = kind == DiagramKind.ACTIVITY ? " (act)"
                        : kind == DiagramKind.CALLGRAPH ? " (cg)" : "";
                return classInfo.getSimpleName() + "." + methodInfo.getName() + suffix;
            case CLASS:
                return classInfo.getSimpleName();
            case PACKAGE:
                return shortPackage(name);
            case MODULE:
                return name;
            case SOONG:
                return "Soong";
            default:
                return target.name();
        }
    }

    /**
     * 同名ラベルのタブが複数開いたときに曖昧さを解消する補足 (主にパッケージ名)。
     * 無ければ空文字。
     */
    public String disambiguator() {
        switch (target) {
            case METHOD:
            case CLASS:
                return classInfo != null ? packageOf(classInfo.getQualifiedName()) : "";
            case PACKAGE:
            case MODULE:
                return name == null ? "" : name;
            default:
                return "";
        }
    }

    /** FQN からパッケージ部分 (最後の {@code .} より前) を取り出す。無ければ空文字。 */
    private static String packageOf(String fqn) {
        if (fqn == null) {
            return "";
        }
        int i = fqn.lastIndexOf('.');
        return i > 0 ? fqn.substring(0, i) : "";
    }

    /** 長いパッケージ名の末尾 2 コンポーネントだけを返す。 */
    private static String shortPackage(String pkg) {
        if (pkg == null) return "";
        String[] parts = pkg.split("\\.");
        if (parts.length <= 2) return pkg;
        return "…" + parts[parts.length - 2] + "." + parts[parts.length - 1];
    }
}
