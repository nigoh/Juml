// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.aosp.AndroidBpModule;
import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidPermissionInfo;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;

import java.util.List;

// プロジェクトツリーのノード値型 (JTree の userObject)。表示は各 toString が担う。
// ProjectTreePanel から肥大化を避けて分離した package-private クラス群。
/** モジュール名を保持するノード値。 */
final class ModuleEntry {
    final String name;

    ModuleEntry(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "[module] " + name;
    }
}

/** パッケージ情報。expanded フラグと、遅延構築用に対象クラス一覧を保持する。 */
final class PackageEntry {
    final String name;
    final int count;
    final List<JavaClassInfo> classes;
    boolean expanded;

    PackageEntry(String name, int count, List<JavaClassInfo> classes) {
        this.name = name;
        this.count = count;
        this.classes = classes;
    }

    @Override
    public String toString() {
        return name + " (" + count + ")";
    }
}

/** クラス情報を保持するノード値。表示はシンプル名のみ。 */
final class ClassEntry {
    /** ノードのクラス情報。Stage A → 展開時に Stage B 昇格で差し替えるため非 final。 */
    JavaClassInfo info;
    boolean expanded;

    ClassEntry(JavaClassInfo info) {
        this.info = info;
    }

    @Override
    public String toString() {
        String kind;
        switch (info.getKind()) {
            case INTERFACE: kind = "I"; break;
            case ENUM: kind = "E"; break;
            case ANNOTATION: kind = "A"; break;
            case AIDL_INTERFACE: kind = "AIDL"; break;
            default: kind = "C"; break;
        }
        return "[" + kind + "] " + info.getSimpleName();
    }
}

/** メソッド情報を保持するノード値。シーケンス図起点として使える。 */
final class MethodEntry {
    final JavaClassInfo owner;
    final JavaMethodInfo method;

    MethodEntry(JavaClassInfo owner, JavaMethodInfo method) {
        this.owner = owner;
        this.method = method;
    }

    @Override
    public String toString() {
        Visibility v = method.getVisibility();
        String mark = v == null ? "~" : v.mark();
        return mark + " " + method.getName() + "()";
    }
}

/**
 * メソッドノードの子として並ぶ「図種別リーフ」ノード値。
 * {@link DiagramKind#SEQUENCE} (赤丸) と {@link DiagramKind#ACTIVITY} (青丸) の
 * 2 種類を {@link #addMethodNodes} で生成し、{@link ProjectTreeCellRenderer} が
 * 該当アイコンを描画する。
 */
final class MethodDiagramEntry {
    final JavaClassInfo owner;
    final JavaMethodInfo method;
    final DiagramKind kind;

    MethodDiagramEntry(JavaClassInfo owner, JavaMethodInfo method, DiagramKind kind) {
        this.owner = owner;
        this.method = method;
        this.kind = kind;
    }

    @Override
    public String toString() {
        switch (kind) {
            case SEQUENCE:
                return "sequence";
            case ACTIVITY:
                return "activity";
            default:
                return kind.name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}

/** 遅延構築前のダミー子ノード値。「+」表示を出すためだけに使う。 */
final class LazyPlaceholder {
    static final LazyPlaceholder INSTANCE = new LazyPlaceholder();

    @Override
    public String toString() {
        return "...";
    }
}

/** AndroidManifest.xml 単位のノード値。表示は sourceSet とパッケージ名。 */
final class ManifestEntry {
    final AndroidManifestInfo info;

    ManifestEntry(AndroidManifestInfo info) {
        this.info = info;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[manifest] AndroidManifest.xml");
        if (info.getSourceSet() != null) {
            sb.append(" (").append(info.getSourceSet()).append(")");
        }
        if (info.getPackageName() != null && !info.getPackageName().isEmpty()) {
            sb.append(" — ").append(info.getPackageName());
        }
        return sb.toString();
    }
}

/** Activities/Services/Receivers/Providers/Permissions/Features のグループ見出し。 */
final class ComponentGroupEntry {
    final String label;
    final int count;

    ComponentGroupEntry(String label, int count) {
        this.label = label;
        this.count = count;
    }

    @Override
    public String toString() {
        return label + " (" + count + ")";
    }
}

/** Manifest 配下の Activity / Service / Receiver / Provider ノード値。 */
final class ComponentEntry {
    final AndroidComponentInfo info;

    ComponentEntry(AndroidComponentInfo info) {
        this.info = info;
    }

    @Override
    public String toString() {
        String name = info.getName() == null || info.getName().isEmpty()
                ? "(unnamed)" : info.getName();
        int dot = name.lastIndexOf('.');
        String shortName = dot >= 0 ? name.substring(dot + 1) : name;
        StringBuilder sb = new StringBuilder();
        sb.append(kindBadge(info.getKind())).append(' ').append(shortName);
        if (info.isLauncher()) {
            sb.append(" [launcher]");
        }
        if (Boolean.TRUE.equals(info.getExported())) {
            sb.append(" [exported]");
        }
        return sb.toString();
    }

    private static String kindBadge(AndroidComponentInfo.Kind k) {
        switch (k) {
            case ACTIVITY: return "[A]";
            case SERVICE: return "[S]";
            case RECEIVER: return "[R]";
            case PROVIDER: return "[P]";
            default: return "[?]";
        }
    }
}

/** uses-permission ノード値。 */
final class PermissionEntry {
    final AndroidPermissionInfo info;

    PermissionEntry(AndroidPermissionInfo info) {
        this.info = info;
    }

    @Override
    public String toString() {
        return info.getShortName();
    }
}

/** 左ツリー最上位の「Soong (Android.bp)」グループ見出し。 */
final class SoongGroupEntry {
    final int fileCount;
    final int moduleCount;

    SoongGroupEntry(int fileCount, int moduleCount) {
        this.fileCount = fileCount;
        this.moduleCount = moduleCount;
    }

    @Override
    public String toString() {
        return "Soong (Android.bp) — " + moduleCount + " modules / " + fileCount + " files";
    }
}

/** 1 つの {@code Android.bp} ファイル (ルート相対パス表示)。 */
final class BpFileEntry {
    /** ルート相対パス (表示用)。 */
    final String relPath;
    final int moduleCount;

    BpFileEntry(String relPath, int moduleCount) {
        this.relPath = relPath;
        this.moduleCount = moduleCount;
    }

    @Override
    public String toString() {
        return "[bp] " + relPath + " (" + moduleCount + ")";
    }
}

/** {@code Android.bp} 内の 1 モジュール。クリックで Soong 依存図タブを開く。 */
final class BpModuleEntry {
    final AndroidBpModule module;

    BpModuleEntry(AndroidBpModule module) {
        this.module = module;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(module.getType()).append("] ").append(module.getName());
        if (module.isTest()) {
            sb.append(" ⚙");
        }
        String partition = module.getPartition();
        if (!"system".equals(partition)) {
            sb.append(" (").append(partition).append(')');
        }
        return sb.toString();
    }
}

/** uses-feature ノード値。 */
final class FeatureEntry {
    final String name;

    FeatureEntry(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
