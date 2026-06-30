// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidPermissionInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * {@link ApkAnalysis} を Markdown レポートに整形する。
 *
 * <p>apktool.yml のメタ情報・AndroidManifest の構成・smali クラスの統計
 * (総数・パッケージ別件数・主要クラス) を 1 枚にまとめ、APK の概観を素早く掴めるようにする。</p>
 */
public final class ApkSummaryReport {

    private ApkSummaryReport() {
    }

    /** APK 解析結果を Markdown 文字列に変換する。 */
    public static String toMarkdown(ApkAnalysis a) {
        if (a == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# APK 解析サマリー (apktool)\n\n");
        emitApktoolSection(sb, a.getApktoolInfo());
        emitManifestSection(sb, a.getManifest());
        emitSmaliSection(sb, a);
        return sb.toString();
    }

    private static void emitApktoolSection(StringBuilder sb, ApktoolYmlInfo y) {
        sb.append("## apktool.yml\n\n");
        if (y == null) {
            sb.append("_apktool.yml が見つかりませんでした。_\n\n");
            return;
        }
        sb.append("| 項目 | 値 |\n|---|---|\n");
        row(sb, "apkFileName", y.getApkFileName());
        row(sb, "apktool version", y.getApktoolVersion());
        row(sb, "minSdkVersion", y.getMinSdkVersion());
        row(sb, "targetSdkVersion", y.getTargetSdkVersion());
        row(sb, "versionCode", y.getVersionCode());
        row(sb, "versionName", y.getVersionName());
        row(sb, "isFrameworkApk", String.valueOf(y.isFrameworkApk()));
        if (!y.getDoNotCompress().isEmpty()) {
            row(sb, "doNotCompress", String.join(", ", y.getDoNotCompress()));
        }
        sb.append('\n');
    }

    private static void emitManifestSection(StringBuilder sb, AndroidManifestInfo m) {
        sb.append("## AndroidManifest.xml\n\n");
        if (m == null) {
            sb.append("_AndroidManifest.xml が見つかりませんでした。_\n\n");
            return;
        }
        sb.append("| 項目 | 値 |\n|---|---|\n");
        row(sb, "package", m.getPackageName());
        row(sb, "application", m.getApplicationClass());
        row(sb, "minSdk", m.getMinSdkVersion() == null ? null
                : String.valueOf(m.getMinSdkVersion()));
        row(sb, "targetSdk", m.getTargetSdkVersion() == null ? null
                : String.valueOf(m.getTargetSdkVersion()));
        row(sb, "activities", String.valueOf(m.getActivities().size()));
        row(sb, "services", String.valueOf(m.getServices().size()));
        row(sb, "receivers", String.valueOf(m.getReceivers().size()));
        row(sb, "providers", String.valueOf(m.getProviders().size()));
        row(sb, "uses-permission", String.valueOf(m.getPermissions().size()));
        sb.append('\n');

        if (!m.getPermissions().isEmpty()) {
            sb.append("### uses-permission\n\n");
            for (AndroidPermissionInfo p : m.getPermissions()) {
                sb.append("- ").append(p.getName()).append('\n');
            }
            sb.append('\n');
        }
        List<AndroidComponentInfo> comps = m.allComponents();
        if (!comps.isEmpty()) {
            sb.append("### コンポーネント\n\n");
            sb.append("| 種別 | クラス | exported |\n|---|---|---|\n");
            for (AndroidComponentInfo c : comps) {
                sb.append("| ").append(c.getKind().label())
                        .append(" | ").append(nz(c.getName()))
                        .append(" | ").append(c.getExported() == null ? "" : c.getExported())
                        .append(" |\n");
            }
            sb.append('\n');
        }
    }

    private static void emitSmaliSection(StringBuilder sb, ApkAnalysis a) {
        sb.append("## smali クラス\n\n");
        List<SmaliClassInfo> classes = a.getClasses();
        if (classes.isEmpty()) {
            sb.append("_smali クラスが見つかりませんでした。_\n\n");
            return;
        }
        int interfaces = 0;
        int abstracts = 0;
        int enums = 0;
        int annotations = 0;
        for (SmaliClassInfo c : classes) {
            if (c.isInterface()) {
                interfaces++;
            } else if (c.isAbstract()) {
                abstracts++;
            }
            if (c.isEnum()) {
                enums++;
            }
            if (c.isAnnotation()) {
                annotations++;
            }
        }
        sb.append("| 指標 | 件数 |\n|---|---|\n");
        row(sb, "クラス総数", String.valueOf(classes.size()));
        row(sb, "interface", String.valueOf(interfaces));
        row(sb, "abstract class", String.valueOf(abstracts));
        row(sb, "enum", String.valueOf(enums));
        row(sb, "annotation", String.valueOf(annotations));
        row(sb, "推定アプリパッケージ", a.getApplicationPackage());
        sb.append('\n');

        sb.append("### パッケージ別クラス数\n\n");
        sb.append("| package | classes |\n|---|---|\n");
        for (Map.Entry<String, Integer> e : a.classCountByPackage().entrySet()) {
            String pkg = e.getKey().isEmpty() ? "(default)" : e.getKey();
            sb.append("| ").append(pkg).append(" | ").append(e.getValue()).append(" |\n");
        }
        sb.append('\n');

        sb.append("### メソッド数が多いクラス (上位 10)\n\n");
        List<SmaliClassInfo> sorted = new ArrayList<>(classes);
        sorted.sort(Comparator.comparingInt(
                (SmaliClassInfo c) -> c.getMethods().size()).reversed());
        sb.append("| class | methods | fields |\n|---|---|---|\n");
        int limit = Math.min(10, sorted.size());
        for (int i = 0; i < limit; i++) {
            SmaliClassInfo c = sorted.get(i);
            sb.append("| ").append(c.getClassName())
                    .append(" | ").append(c.getMethods().size())
                    .append(" | ").append(c.getFields().size()).append(" |\n");
        }
        sb.append('\n');
    }

    private static void row(StringBuilder sb, String key, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        sb.append("| ").append(key).append(" | ").append(value).append(" |\n");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
