// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AndroidProjectAnalysis を人が読みやすい Markdown レポートに整形する。
 */
public final class TextSummaryReport {

    /**
     * AndroidManifest のみに絞った Markdown サマリーを生成する。
     *
     * <p>{@link #toMarkdown(AndroidProjectAnalysis)} は Gradle モジュール情報も含むため、
     * UI 側で「manifest だけを見たい」ケースでは情報過多になる。本メソッドは
     * {@code <application>} 属性 / コンポーネント / permissions / features のみを
     * モジュール × sourceSet 単位で並べる。</p>
     */
    public static String toManifestMarkdown(AndroidProjectAnalysis analysis) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# AndroidManifest Summary — アプリ構成の概要\n\n");
        if (analysis.getManifestsByModule().isEmpty()) {
            sb.append("AndroidManifest.xml が見つかりませんでした "
                    + "(no AndroidManifest.xml found)。\n");
            return sb.toString();
        }
        for (Map.Entry<String, List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            String module = e.getKey();
            List<AndroidManifestInfo> manifests = e.getValue();
            sb.append("## Module `").append(module).append("`");
            if (manifests.size() > 1) {
                sb.append(" — ").append(manifests.size()).append(" manifests");
            }
            sb.append("\n\n");
            for (AndroidManifestInfo m : manifests) {
                appendManifestDetail(sb, m);
            }
        }
        sb.append("## Permissions (権限: アプリが必要とする許可)\n\n");
        appendPermissionsSection(sb, analysis);
        sb.append("## Features (必要な端末機能)\n\n");
        appendFeaturesSection(sb, analysis);
        return sb.toString();
    }

    private static void appendManifestDetail(StringBuilder sb, AndroidManifestInfo m) {
        sb.append("### sourceSet `").append(m.getSourceSet()).append("`\n\n");
        appendIf(sb, "Package", m.getPackageName());
        appendIf(sb, "Application class", m.getApplicationClass());
        appendIf(sb, "Application label", m.getApplicationLabel());
        appendIf(sb, "Theme", m.getApplicationTheme());
        if (m.getApplicationDebuggable() != null) {
            sb.append("- debuggable: `").append(m.getApplicationDebuggable()).append("`\n");
        }
        if (m.getApplicationAllowBackup() != null) {
            sb.append("- allowBackup: `").append(m.getApplicationAllowBackup()).append("`\n");
        }
        appendModernAppAttrs(sb, m);
        appendSdkSection(sb, m);
        if (!m.getApplicationMetaData().isEmpty()) {
            sb.append("\n**Application meta-data:**\n\n");
            for (Map.Entry<String, String> me : m.getApplicationMetaData().entrySet()) {
                sb.append("- `").append(me.getKey()).append("` = `")
                        .append(me.getValue()).append("`\n");
            }
        }
        appendPropertyList(sb, "Application properties", m.getApplicationProperties());
        appendComponentList(sb, "Activities", m.getActivities());
        appendComponentList(sb, "Services", m.getServices());
        appendComponentList(sb, "Receivers", m.getReceivers());
        appendComponentList(sb, "Providers", m.getProviders());
        appendCustomPermissionList(sb, m.getCustomPermissions());
        appendFgsPermissionMatch(sb, m);
        appendDeepLinkList(sb, m);
        sb.append('\n');
    }

    private static void appendModernAppAttrs(StringBuilder sb, AndroidManifestInfo m) {
        boolean any = m.getApplicationUsesCleartextTraffic() != null
                || m.getApplicationNetworkSecurityConfig() != null
                || m.getApplicationEnableOnBackInvokedCallback() != null
                || m.getApplicationLocaleConfig() != null
                || m.getApplicationDataExtractionRules() != null
                || m.getApplicationHardwareAccelerated() != null
                || m.getApplicationLargeHeap() != null
                || m.getApplicationAppCategory() != null;
        if (!any) {
            return;
        }
        sb.append("\n**Application attributes (アプリ全体の追加設定, Android 12+/13+/14+):**\n\n");
        if (m.getApplicationUsesCleartextTraffic() != null) {
            sb.append("- usesCleartextTraffic: `")
                    .append(m.getApplicationUsesCleartextTraffic()).append("`\n");
        }
        if (m.getApplicationNetworkSecurityConfig() != null) {
            sb.append("- networkSecurityConfig: `")
                    .append(m.getApplicationNetworkSecurityConfig()).append("`\n");
        }
        if (m.getApplicationEnableOnBackInvokedCallback() != null) {
            sb.append("- enableOnBackInvokedCallback: `")
                    .append(m.getApplicationEnableOnBackInvokedCallback())
                    .append("` *(Android 13+ Predictive Back: 戻る操作のプレビュー表示)*\n");
        }
        if (m.getApplicationLocaleConfig() != null) {
            sb.append("- localeConfig: `")
                    .append(m.getApplicationLocaleConfig()).append("` *(Android 13+)*\n");
        }
        if (m.getApplicationDataExtractionRules() != null) {
            sb.append("- dataExtractionRules: `")
                    .append(m.getApplicationDataExtractionRules()).append("` *(Android 12+)*\n");
        }
        if (m.getApplicationHardwareAccelerated() != null) {
            sb.append("- hardwareAccelerated: `")
                    .append(m.getApplicationHardwareAccelerated()).append("`\n");
        }
        if (m.getApplicationLargeHeap() != null) {
            sb.append("- largeHeap: `").append(m.getApplicationLargeHeap()).append("`\n");
        }
        if (m.getApplicationAppCategory() != null) {
            sb.append("- appCategory: `").append(m.getApplicationAppCategory()).append("`\n");
        }
    }

    private static void appendPropertyList(StringBuilder sb, String label,
                                              List<AndroidPropertyInfo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        sb.append("\n**").append(label).append(" (Android 12+):**\n\n");
        for (AndroidPropertyInfo p : list) {
            sb.append("- `").append(p.getName()).append('`');
            String v = p.effectiveValue();
            if (v != null) {
                sb.append(" = `").append(v).append('`');
            }
            sb.append('\n');
        }
    }

    private static void appendFgsPermissionMatch(StringBuilder sb, AndroidManifestInfo m) {
        // Android 14+ では service の foregroundServiceType に対応する
        // FOREGROUND_SERVICE_* permission の宣言が必須。要求 service の有無で対応表を作る。
        java.util.Set<String> declaredPerms = new java.util.HashSet<>();
        for (AndroidPermissionInfo p : m.getPermissions()) {
            if (p.getName() != null) {
                declaredPerms.add(p.getName());
            }
        }
        java.util.Set<String> requestedTypes = new java.util.LinkedHashSet<>();
        for (AndroidComponentInfo c : m.getServices()) {
            for (String t : c.getForegroundServiceTypeList()) {
                String type = t.trim();
                if (!type.isEmpty()) {
                    requestedTypes.add(type);
                }
            }
        }
        if (requestedTypes.isEmpty()) {
            return;
        }
        sb.append("\n**Foreground Service Types (Android 14+/15+):**\n\n");
        sb.append("> フォアグラウンドサービス = 通知を出しながら裏で動き続ける処理"
                + "(音楽再生・位置追跡など)。種類ごとに必要な権限が決まっています。\n\n");
        sb.append("| 種類 (Type) | 必要な最低 API (Min API) | 必要な権限 (Required Permission) "
                + "| 宣言済み (Declared) |\n");
        sb.append("|---|---|---|---|\n");
        for (String type : requestedTypes) {
            ForegroundServiceTypeCatalog.Entry e = ForegroundServiceTypeCatalog.get(type);
            String perm = e != null ? e.getRuntimePermission() : "(unknown type)";
            String min = e != null ? String.valueOf(e.getMinApiLevel()) : "?";
            boolean ok = perm != null && declaredPerms.contains(perm);
            sb.append("| `").append(type).append("` | ").append(min).append(" | `")
                    .append(perm == null ? "-" : perm).append("` | ")
                    .append(perm == null ? "n/a" : (ok ? "yes" : "**MISSING**"))
                    .append(" |\n");
        }
    }

    private static void appendSdkSection(StringBuilder sb, AndroidManifestInfo m) {
        if (m.getMinSdkVersion() == null && m.getTargetSdkVersion() == null
                && m.getMaxSdkVersion() == null) {
            return;
        }
        sb.append("\n**uses-sdk:**\n\n");
        if (m.getMinSdkVersion() != null) {
            sb.append("- minSdkVersion: `").append(m.getMinSdkVersion()).append("`\n");
        }
        if (m.getTargetSdkVersion() != null) {
            sb.append("- targetSdkVersion: `").append(m.getTargetSdkVersion()).append("`\n");
        }
        if (m.getMaxSdkVersion() != null) {
            sb.append("- maxSdkVersion: `").append(m.getMaxSdkVersion()).append("`\n");
        }
    }

    private static void appendCustomPermissionList(StringBuilder sb,
                                                     List<AndroidCustomPermission> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        sb.append("\n**Custom Permissions (declared by app):**\n\n");
        for (AndroidCustomPermission p : list) {
            sb.append("- `").append(p.getName()).append('`');
            if (p.getProtectionLevel() != null) {
                sb.append(" — protectionLevel: `").append(p.getProtectionLevel()).append('`');
            }
            if (p.getPermissionGroup() != null) {
                sb.append(", group: `").append(p.getPermissionGroup()).append('`');
            }
            sb.append('\n');
        }
    }

    private static void appendDeepLinkList(StringBuilder sb, AndroidManifestInfo m) {
        // VIEW + BROWSABLE を持つ Activity の Deep Link URI を平坦に列挙する。
        boolean header = false;
        for (AndroidComponentInfo c : m.getActivities()) {
            for (AndroidIntentFilter f : c.getIntentFilters()) {
                if (!f.isViewDeepLink()) {
                    continue;
                }
                for (AndroidDataSpec d : f.getDataSpecs()) {
                    String uri = d.toDeepLinkUri();
                    if (uri == null) {
                        continue;
                    }
                    if (!header) {
                        sb.append("\n**Deep Links:**\n\n");
                        header = true;
                    }
                    sb.append("- `").append(uri).append('`')
                            .append(" → `").append(c.getName()).append('`');
                    if (Boolean.TRUE.equals(f.getAutoVerify())) {
                        sb.append(" *(autoVerify)*");
                    }
                    sb.append('\n');
                }
            }
        }
    }

    /** Markdown サマリーを生成。 */
    public static String toMarkdown(AndroidProjectAnalysis analysis) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Android Project Summary — プロジェクト概要\n\n");

        sb.append("## Modules (モジュール: Gradle が管理する分割単位)\n\n");
        if (analysis.getRootSettings() != null
                && !analysis.getRootSettings().getSubprojects().isEmpty()) {
            sb.append("settings.gradle includes:\n");
            for (String m : analysis.getRootSettings().getSubprojects()) {
                sb.append("- ").append(m).append('\n');
            }
            sb.append('\n');
        }
        if (analysis.getGradleByModule().isEmpty()) {
            sb.append("(no build.gradle files parsed)\n\n");
        } else {
            for (Map.Entry<String, GradleProjectInfo> e
                    : analysis.getGradleByModule().entrySet()) {
                appendModuleSection(sb, e.getKey(), e.getValue());
            }
        }

        sb.append("## Components (アプリの部品: 画面・サービス等)\n\n");
        if (analysis.allComponents().isEmpty()) {
            sb.append("(no AndroidManifest.xml components)\n\n");
        } else {
            appendComponentsSection(sb, analysis);
        }

        sb.append("## Permissions (権限: アプリが必要とする許可)\n\n");
        appendPermissionsSection(sb, analysis);

        sb.append("## Features (必要な端末機能)\n\n");
        appendFeaturesSection(sb, analysis);

        return sb.toString();
    }

    private static void appendModuleSection(StringBuilder sb, String name,
                                             GradleProjectInfo info) {
        sb.append("### `").append(name).append("`\n\n");
        if (info.isAndroidApplication()) {
            sb.append("- Type: **Android Application**\n");
        } else if (info.isAndroidLibrary()) {
            sb.append("- Type: **Android Library**\n");
        }
        appendIf(sb, "Application ID", info.getApplicationId());
        appendIf(sb, "Namespace", info.getNamespace());
        appendIf(sb, "compileSdk", str(info.getCompileSdk()));
        appendIf(sb, "minSdk", str(info.getMinSdk()));
        appendIf(sb, "targetSdk", str(info.getTargetSdk()));
        appendIf(sb, "versionCode", str(info.getVersionCode()));
        appendIf(sb, "versionName", info.getVersionName());
        if (!info.getPlugins().isEmpty()) {
            sb.append("- Plugins: ").append(String.join(", ", info.getPlugins())).append('\n');
        }
        if (!info.getFlavorDimensions().isEmpty()) {
            sb.append("- flavorDimensions: ")
                    .append(String.join(", ", info.getFlavorDimensions())).append('\n');
        }
        if (!info.getBuildTypes().isEmpty()) {
            sb.append("\n**Build Types:**\n\n");
            for (GradleBuildType bt : info.getBuildTypes().values()) {
                sb.append("- `").append(bt.getName()).append("`");
                if (bt.getMinifyEnabled() != null) {
                    sb.append(" minifyEnabled=").append(bt.getMinifyEnabled());
                }
                if (bt.getDebuggable() != null) {
                    sb.append(" debuggable=").append(bt.getDebuggable());
                }
                if (bt.getApplicationIdSuffix() != null) {
                    sb.append(" appIdSuffix=").append(bt.getApplicationIdSuffix());
                }
                sb.append('\n');
            }
        }
        if (!info.getProductFlavors().isEmpty()) {
            sb.append("\n**Product Flavors:**\n\n");
            for (GradleProductFlavor pf : info.getProductFlavors().values()) {
                sb.append("- `").append(pf.getName()).append("`");
                if (pf.getDimension() != null) {
                    sb.append(" dimension=").append(pf.getDimension());
                }
                sb.append('\n');
            }
        }
        if (!info.getSigningConfigs().isEmpty()) {
            sb.append("\n**Signing Configs (機密値は表示しない):**\n\n");
            for (GradleSigningConfig sc : info.getSigningConfigs().values()) {
                sb.append("- `").append(sc.getName()).append("`");
                if (sc.getKeyAlias() != null) {
                    sb.append(" keyAlias=").append(sc.getKeyAlias());
                }
                sb.append('\n');
            }
        }
        if (!info.getDependencies().isEmpty()) {
            sb.append("\n**Dependencies:**\n\n");
            sb.append("| Scope | Notation |\n|---|---|\n");
            for (GradleDependency d : info.getDependencies()) {
                sb.append("| ").append(d.getScope())
                        .append(" | ").append(d.getNotation()).append(" |\n");
            }
        }
        sb.append('\n');
    }

    private static void appendComponentsSection(StringBuilder sb,
                                                  AndroidProjectAnalysis analysis) {
        for (Map.Entry<String, List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            List<AndroidManifestInfo> manifests = e.getValue();
            sb.append("### Module `").append(e.getKey()).append("`");
            if (manifests.size() > 1) {
                sb.append(" — ").append(manifests.size()).append(" manifests");
            }
            sb.append("\n\n");
            for (AndroidManifestInfo m : manifests) {
                sb.append("#### sourceSet `").append(m.getSourceSet()).append("`\n\n");
                sb.append("- Package: `").append(m.getPackageName()).append("`\n");
                if (m.getApplicationClass() != null) {
                    sb.append("- Application class: `")
                            .append(m.getApplicationClass()).append("`\n");
                }
                appendModernAppAttrs(sb, m);
                appendSdkSection(sb, m);
                appendPropertyList(sb, "Application properties", m.getApplicationProperties());
                appendComponentList(sb, "Activities", m.getActivities());
                appendComponentList(sb, "Services", m.getServices());
                appendComponentList(sb, "Receivers", m.getReceivers());
                appendComponentList(sb, "Providers", m.getProviders());
                appendCustomPermissionList(sb, m.getCustomPermissions());
                appendFgsPermissionMatch(sb, m);
                appendDeepLinkList(sb, m);
                sb.append('\n');
            }
        }
    }

    private static void appendComponentList(StringBuilder sb, String label,
                                             List<AndroidComponentInfo> list) {
        if (list.isEmpty()) {
            return;
        }
        sb.append("\n**").append(label).append(":**\n\n");
        for (AndroidComponentInfo c : list) {
            sb.append("- `").append(c.getName()).append('`');
            if (c.isActivityAlias()) {
                sb.append(" *(alias → `").append(c.getTargetActivity()).append("`)*");
            }
            if (Boolean.TRUE.equals(c.getExported())) {
                sb.append(" *(exported)*");
            }
            if (c.isLauncher()) {
                sb.append(" *(launcher)*");
            }
            if (c.getForegroundServiceType() != null) {
                int api = ForegroundServiceTypeCatalog.minApiLevelFor(
                        c.getForegroundServiceType());
                sb.append(" *(foregroundServiceType: `")
                        .append(c.getForegroundServiceType()).append('`');
                if (api > 0) {
                    sb.append(", Android ").append(api).append("+");
                }
                sb.append(")*");
            }
            if (!c.getIntentFilters().isEmpty()) {
                sb.append(" — intent-filters: ").append(c.getIntentFilters().size());
            }
            sb.append('\n');
            for (AndroidIntentFilter f : c.getIntentFilters()) {
                if (!f.getActions().isEmpty()) {
                    sb.append("  - actions: ").append(String.join(", ", f.getActions()))
                            .append('\n');
                }
                if (!f.getCategories().isEmpty()) {
                    sb.append("  - categories: ")
                            .append(String.join(", ", f.getCategories())).append('\n');
                }
                if (Boolean.TRUE.equals(f.getAutoVerify())) {
                    sb.append("  - autoVerify: `true`\n");
                }
            }
        }
    }

    private static void appendPermissionsSection(StringBuilder sb,
                                                   AndroidProjectAnalysis analysis) {
        Set<String> perms = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidPermissionInfo p : m.getPermissions()) {
                perms.add(p.getName());
            }
        }
        if (perms.isEmpty()) {
            sb.append("(none)\n\n");
            return;
        }
        for (String p : perms) {
            sb.append("- ").append(p).append('\n');
        }
        sb.append('\n');
    }

    private static void appendFeaturesSection(StringBuilder sb,
                                                AndroidProjectAnalysis analysis) {
        Set<String> features = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            features.addAll(m.getFeatures());
        }
        if (features.isEmpty()) {
            sb.append("(none)\n");
            return;
        }
        for (String f : features) {
            sb.append("- ").append(f).append('\n');
        }
        sb.append('\n');
    }

    private static void appendIf(StringBuilder sb, String label, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append("- ").append(label).append(": `").append(value).append("`\n");
        }
    }

    private static String str(Integer v) {
        return v == null ? null : v.toString();
    }

    private TextSummaryReport() {
    }
}
