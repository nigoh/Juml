// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.uml.PlantUmlCommentFormatter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AndroidProjectAnalysis を「アプリ視点」で 1 枚に描画する PlantUML 図を生成する。
 *
 * <p>{@link PlantUmlComponentDiagram} が intent-filter 中心の連携図であるのに対し、
 * Manifest Diagram は AndroidManifest.xml に含まれる
 * {@code <application>} の属性 (パッケージ名 / Application クラス / theme /
 * debuggable / allowBackup) を中央のノードに据え、その配下に Activity / Service /
 * Receiver / Provider をグループ化して所属関係を可視化する。
 * 周辺に {@code <uses-permission>} と {@code <uses-feature>} を別ボックスで配置する。</p>
 *
 * <p>同一モジュールに複数 manifest (main + debug + flavor など) がある場合は
 * sourceSet ごとに別 Application ノードを描画する。各コンポーネントは {@code main}
 * 以外なら {@code <<src:debug>>} 等のステレオタイプが付き、launcher と exported は
 * 視覚的に強調する。</p>
 */
public final class PlantUmlManifestDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public boolean groupByModule = true;
        public boolean showPermissions = true;
        public boolean showFeatures = true;
        public boolean showMetaData = true;
        public String title;
    }

    /** デフォルト Options で生成。 */
    public static String generate(AndroidProjectAnalysis analysis) {
        return generate(analysis, null);
    }

    /** オプション付き生成。 */
    public static String generate(AndroidProjectAnalysis analysis, Options opts) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        Options o = opts != null ? opts : new Options();
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }
        // 全体の見た目を整える skinparam。文字数が多い Application ノードを
        // 読みやすく保つために幅指定と縦並べを優先する。
        out.append("skinparam componentStyle uml2\n");
        out.append("skinparam rectangle {\n");
        out.append("  BackgroundColor<<application>> #E0EEFF\n");
        out.append("  BorderColor<<application>> #4A6FB7\n");
        out.append("}\n");
        // FOREGROUND_SERVICE_* permission を強調するための専用色 (Android 14+ で必須化)
        out.append("skinparam component {\n");
        out.append("  BackgroundColor<<fgs>> #FDEBD0\n");
        out.append("  BorderColor<<fgs>>     #B9770E\n");
        out.append("}\n");

        boolean any = false;
        int seq = 0;
        for (Map.Entry<String, List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            String moduleName = e.getKey();
            List<AndroidManifestInfo> manifests = e.getValue();
            if (manifests.isEmpty()) {
                continue;
            }
            if (o.groupByModule) {
                out.append("package \"module: ").append(moduleName).append("\" {\n");
            }
            for (AndroidManifestInfo m : manifests) {
                seq = emitManifest(out, m, seq, o, o.groupByModule);
                any = true;
            }
            if (o.groupByModule) {
                out.append("}\n");
            }
        }
        if (!any) {
            out.append("note as N1\n  (no AndroidManifest.xml found)\nend note\n");
        }
        if (o.showPermissions) {
            emitPermissions(out, analysis);
        }
        if (o.showFeatures) {
            emitFeatures(out, analysis);
        }
        if (o.includeLegend) {
            emitLegend(out, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static int emitManifest(StringBuilder out, AndroidManifestInfo m, int startSeq,
                                     Options o, boolean indented) {
        int seq = startSeq;
        String indent = indented ? "  " : "";
        String appAlias = "APP" + (seq++);

        // Application ノード本体。属性を 1 行ずつ \n で並べる。
        StringBuilder header = new StringBuilder();
        header.append("Application");
        if (!"main".equals(m.getSourceSet())) {
            header.append(" [").append(m.getSourceSet()).append("]");
        }
        header.append("\\n----");
        if (m.getPackageName() != null && !m.getPackageName().isEmpty()) {
            header.append("\\npackage: ").append(escape(m.getPackageName()));
        }
        if (m.getApplicationClass() != null && !m.getApplicationClass().isEmpty()) {
            header.append("\\nclass: ").append(escape(m.getApplicationClass()));
        }
        if (m.getApplicationLabel() != null && !m.getApplicationLabel().isEmpty()) {
            header.append("\\nlabel: ").append(escape(m.getApplicationLabel()));
        }
        if (m.getApplicationTheme() != null && !m.getApplicationTheme().isEmpty()) {
            header.append("\\ntheme: ").append(escape(m.getApplicationTheme()));
        }
        if (m.getApplicationDebuggable() != null) {
            header.append("\\ndebuggable: ").append(m.getApplicationDebuggable());
        }
        if (m.getApplicationAllowBackup() != null) {
            header.append("\\nallowBackup: ").append(m.getApplicationAllowBackup());
        }
        if (m.getMinSdkVersion() != null) {
            header.append("\\nminSdk: ").append(m.getMinSdkVersion());
        }
        if (m.getTargetSdkVersion() != null) {
            header.append("\\ntargetSdk: ").append(m.getTargetSdkVersion());
        }
        // Android 12+/13+/14+ で重要なセキュリティ・UX 属性は宣言があるものだけ追記。
        if (m.getApplicationUsesCleartextTraffic() != null) {
            header.append("\\nusesCleartextTraffic: ")
                    .append(m.getApplicationUsesCleartextTraffic());
        }
        if (m.getApplicationNetworkSecurityConfig() != null) {
            header.append("\\nnetworkSecurityConfig: ")
                    .append(escape(m.getApplicationNetworkSecurityConfig()));
        }
        if (m.getApplicationEnableOnBackInvokedCallback() != null) {
            header.append("\\nenableOnBackInvokedCallback: ")
                    .append(m.getApplicationEnableOnBackInvokedCallback());
        }
        if (m.getApplicationLocaleConfig() != null) {
            header.append("\\nlocaleConfig: ")
                    .append(escape(m.getApplicationLocaleConfig()));
        }
        if (m.getApplicationDataExtractionRules() != null) {
            header.append("\\ndataExtractionRules: ")
                    .append(escape(m.getApplicationDataExtractionRules()));
        }
        if (m.getApplicationHardwareAccelerated() != null) {
            header.append("\\nhardwareAccelerated: ")
                    .append(m.getApplicationHardwareAccelerated());
        }
        if (m.getApplicationLargeHeap() != null) {
            header.append("\\nlargeHeap: ").append(m.getApplicationLargeHeap());
        }
        if (m.getApplicationAppCategory() != null) {
            header.append("\\nappCategory: ").append(escape(m.getApplicationAppCategory()));
        }
        if (o.showMetaData && !m.getApplicationProperties().isEmpty()) {
            // <property> は Android 12+ の正式 API メタデータ。meta-data と分けて表示する。
            header.append("\\n--properties--");
            for (AndroidPropertyInfo p : m.getApplicationProperties()) {
                String v = p.effectiveValue();
                header.append("\\n").append(escape(p.getName()));
                if (v != null) {
                    header.append(" = ").append(escape(v));
                }
            }
        }
        if (o.showMetaData && !m.getApplicationMetaData().isEmpty()) {
            header.append("\\n--meta-data--");
            for (Map.Entry<String, String> me : m.getApplicationMetaData().entrySet()) {
                header.append("\\n").append(escape(me.getKey()))
                        .append(" = ").append(escape(me.getValue()));
            }
        }
        out.append(indent).append("rectangle \"").append(header).append("\" as ")
                .append(appAlias).append(" <<application>>");
        if (!"main".equals(m.getSourceSet())) {
            out.append(" <<src:").append(m.getSourceSet()).append(">>");
        }
        out.append('\n');

        // 種別ごとに 1 グループずつ。空グループは省略する。
        seq = emitComponentGroup(out, "Activities", m.getActivities(), appAlias, seq,
                indent, m.getSourceSet());
        seq = emitComponentGroup(out, "Services", m.getServices(), appAlias, seq,
                indent, m.getSourceSet());
        seq = emitComponentGroup(out, "Receivers", m.getReceivers(), appAlias, seq,
                indent, m.getSourceSet());
        seq = emitComponentGroup(out, "Providers", m.getProviders(), appAlias, seq,
                indent, m.getSourceSet());
        return seq;
    }

    private static int emitComponentGroup(StringBuilder out, String label,
                                           List<AndroidComponentInfo> list,
                                           String appAlias, int startSeq,
                                           String parentIndent, String sourceSet) {
        if (list == null || list.isEmpty()) {
            return startSeq;
        }
        int seq = startSeq;
        String groupAlias = "G" + (seq++);
        String inner = parentIndent + "  ";
        out.append(parentIndent).append("package \"").append(label)
                .append(" (").append(list.size()).append(")\" as ")
                .append(groupAlias).append(" {\n");
        for (AndroidComponentInfo c : list) {
            String alias = "C" + (seq++);
            String name = c.getName() == null || c.getName().isEmpty()
                    ? "(unnamed)" : c.getName();
            String stereo = "<<" + c.getKind().label() + ">>";
            // alias / foregroundServiceType を補助情報として 2 行目に表示する。
            StringBuilder labelBuf = new StringBuilder(escape(shortName(name)));
            if (c.isActivityAlias()) {
                labelBuf.append("\\n→ ").append(escape(shortName(c.getTargetActivity())));
            }
            if (c.getForegroundServiceType() != null) {
                int api = ForegroundServiceTypeCatalog.minApiLevelFor(
                        c.getForegroundServiceType());
                labelBuf.append("\\nfgType: ").append(escape(c.getForegroundServiceType()));
                if (api > 0) {
                    labelBuf.append(" (API ").append(api).append("+)");
                }
            }
            out.append(inner).append("component \"").append(labelBuf)
                    .append("\" as ").append(alias).append(' ').append(stereo);
            if (Boolean.TRUE.equals(c.getExported())) {
                out.append(" #LightYellow");
            }
            if (c.isLauncher()) {
                out.append(" <<launcher>>");
            }
            if (c.isActivityAlias()) {
                out.append(" <<alias>>");
            }
            if (sourceSet != null && !"main".equals(sourceSet)) {
                out.append(" <<src:").append(sourceSet).append(">>");
            }
            out.append('\n');
        }
        out.append(parentIndent).append("}\n");
        out.append(parentIndent).append(appAlias).append(" *-- ").append(groupAlias)
                .append('\n');
        return seq;
    }

    private static void emitPermissions(StringBuilder out, AndroidProjectAnalysis analysis) {
        Set<String> perms = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidPermissionInfo p : m.getPermissions()) {
                if (p.getName() != null && !p.getName().isEmpty()) {
                    perms.add(p.getName());
                }
            }
        }
        if (perms.isEmpty()) {
            return;
        }
        out.append("package \"uses-permission\" {\n");
        for (String p : perms) {
            int dot = p.lastIndexOf('.');
            String shortP = dot >= 0 ? p.substring(dot + 1) : p;
            // FOREGROUND_SERVICE_* は Android 14+ で service の fgType と対になる
            // 必須宣言なので、別ステレオタイプで識別できるようにする。
            String stereo = ForegroundServiceTypeCatalog.isForegroundServicePermission(p)
                    ? " <<permission>> <<fgs>>" : " <<permission>>";
            out.append("  [").append(shortP).append("]").append(stereo).append('\n');
        }
        out.append("}\n");
    }

    private static void emitFeatures(StringBuilder out, AndroidProjectAnalysis analysis) {
        Set<String> features = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            features.addAll(m.getFeatures());
        }
        if (features.isEmpty()) {
            return;
        }
        out.append("package \"uses-feature\" {\n");
        Map<String, String> aliases = new LinkedHashMap<>();
        int seq = 0;
        for (String f : features) {
            String alias = "F" + (seq++);
            aliases.put(f, alias);
            int dot = f.lastIndexOf('.');
            String shortF = dot >= 0 ? f.substring(dot + 1) : f;
            out.append("  usecase \"").append(escape(shortF))
                    .append("\" as ").append(alias).append(" <<feature>>\n");
        }
        out.append("}\n");
    }

    /** {@code com.x.MainActivity} → {@code MainActivity} (簡易表示)。 */
    private static String shortName(String fqn) {
        if (fqn == null) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        // 引用符付きラベル ("...") へ埋め込むため、まずダブルクォートをアポストロフィに、
        // 改行・タブを空白に畳んでラベル文字列が途中で閉じないようにする。そのうえで
        // 共通エスケーパ (制御文字除去・"<" のチルダエスケープ・"[[" の無害化・図境界
        // ディレクティブの分断) に通し、applicationLabel 等に "<" や改行が入っても
        // creole 解釈やハードエラーにならないようにする。
        String t = s.replace('"', '\'').replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return PlantUmlCommentFormatter.escapeText(t);
    }

    private static void emitLegend(StringBuilder out, Options o) {
        out.append("legend top left\n");
        out.append("== AndroidManifest 図 (アプリの構成一覧) ==\n");
        out.append("<<application>>  : アプリ全体の設定\n");
        out.append("Activity         : ユーザーが操作する画面\n");
        out.append("Service          : 画面を持たず裏で動く処理\n");
        out.append("BroadcastReceiver: 通知 (ブロードキャスト) を受け取る部品\n");
        out.append("ContentProvider  : 他アプリとデータを共有する窓口\n");
        out.append("<<launcher>>     : アプリ起動時に最初に開く画面\n");
        out.append("#LightYellow     : exported=true (他アプリから呼び出せる=外部公開)\n");
        out.append("<<src:flavor>>   : main 以外のビルド種別だけにある定義 (debug 等)\n");
        if (o.showPermissions) {
            out.append("<<permission>>   : アプリが必要とする許可 (uses-permission)\n");
            out.append("<<fgs>>          : 裏で動き続ける処理に必要な許可 "
                    + "(FOREGROUND_SERVICE_*, Android 14+)\n");
        }
        if (o.showFeatures) {
            out.append("<<feature>>      : アプリが必要とする端末機能 (uses-feature)\n");
        }
        out.append("endlegend\n");
    }

    private PlantUmlManifestDiagram() {
    }
}
