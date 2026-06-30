// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * AndroidProjectAnalysis から PlantUML コンポーネント図を生成する。
 *
 * <p>Activity / Service / BroadcastReceiver / ContentProvider をそれぞれ
 * {@code component} ノードとして配置し、intent-filter のアクションを矢印として描く。
 * アプリケーションが要求するパーミッションは別パッケージにまとめる。</p>
 */
public final class PlantUmlComponentDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public boolean groupByModule = true;
        public boolean showIntentFilters = true;
        public boolean showPermissions = true;
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
        out.append("top to bottom direction\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        }
        Map<String, String> aliasByFqn = new LinkedHashMap<>();
        // モジュール順と各モジュールの最初のエイリアスを追跡（縦積み用）
        List<String> moduleOrder = new java.util.ArrayList<>();
        Map<String, String> firstAliasByModule = new LinkedHashMap<>();
        int seq = 0;
        boolean any = false;
        for (Map.Entry<String, List<AndroidManifestInfo>> e
                : analysis.getManifestsByModule().entrySet()) {
            String moduleName = e.getKey();
            List<AndroidManifestInfo> manifests = e.getValue();
            if (o.groupByModule) {
                out.append("package \"").append(moduleName).append("\" {\n");
            }
            // 同一モジュール内に複数 manifest (main + debug + flavor) があると同じ
            // コンポーネントが複数回宣言されることがあるため FQN で重複排除する。
            // 別 sourceSet からの差分は <<overlay>> ステレオタイプで付加する。
            java.util.Set<String> emittedFqn = new java.util.HashSet<>();
            for (AndroidManifestInfo m : manifests) {
                for (AndroidComponentInfo c : m.allComponents()) {
                    if (c.getName() == null || c.getName().isEmpty()) {
                        continue;
                    }
                    String fqn = c.getName();
                    if (!emittedFqn.add(fqn)) {
                        continue;
                    }
                    String alias = "K" + (seq++);
                    aliasByFqn.put(fqn, alias);
                    firstAliasByModule.putIfAbsent(moduleName, alias);
                    String stereo = "<<" + c.getKind().label() + ">>";
                    String indent = o.groupByModule ? "  " : "";
                    out.append(indent).append("component \"").append(escapeLabel(fqn))
                            .append("\" as ").append(alias).append(' ')
                            .append(stereo);
                    if (!"main".equals(m.getSourceSet())) {
                        out.append(" <<src:").append(m.getSourceSet()).append(">>");
                    }
                    if (Boolean.TRUE.equals(c.getExported())) {
                        out.append(" #LightYellow");
                    }
                    if (c.isLauncher()) {
                        out.append(" <<launcher>>");
                    }
                    out.append('\n');
                    any = true;
                }
            }
            if (o.groupByModule) {
                out.append("}\n");
            }
            moduleOrder.add(moduleName);
        }
        // 無接続モジュールが横に広がるのを防ぐ: 連続モジュール間に隠しリンクを追加
        if (o.groupByModule && moduleOrder.size() > 1) {
            for (int i = 1; i < moduleOrder.size(); i++) {
                String prevAlias = firstAliasByModule.get(moduleOrder.get(i - 1));
                String currAlias = firstAliasByModule.get(moduleOrder.get(i));
                if (prevAlias != null && currAlias != null) {
                    out.append(prevAlias).append(" -[hidden]-> ").append(currAlias).append('\n');
                }
            }
        }
        if (!any) {
            out.append("note as N1\n  (no manifest components found)\nend note\n");
        }
        if (o.showIntentFilters) {
            emitIntentFilters(out, analysis, aliasByFqn);
        }
        if (o.showPermissions) {
            emitPermissions(out, analysis);
        }
        if (o.includeLegend) {
            emitLegend(out, analysis, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static void emitIntentFilters(StringBuilder out,
                                           AndroidProjectAnalysis analysis,
                                           Map<String, String> aliasByFqn) {
        // intent-filter の action を集約し、各 action を 1 つのノードにして矢印を引く
        Map<String, String> actionAlias = new HashMap<>();
        // 短縮ラベル (末尾セグメント) の衝突検出用。別 FQN の action が同じ短縮名に
        // なると区別不能な同名ノードが並ぶため、衝突時は FQN 全体をラベルにする。
        Set<String> usedLabels = new TreeSet<>();
        int seq = 0;
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidComponentInfo c : m.allComponents()) {
                String compAlias = aliasByFqn.get(c.getName());
                if (compAlias == null) {
                    continue;
                }
                for (AndroidIntentFilter f : c.getIntentFilters()) {
                    for (String action : f.getActions()) {
                        String aa = actionAlias.get(action);
                        if (aa == null) {
                            aa = "A" + (seq++);
                            actionAlias.put(action, aa);
                            String label = shortAction(action);
                            if (!usedLabels.add(label)) {
                                // 短縮名が既出 → FQN 全体で区別する
                                label = action;
                                usedLabels.add(label);
                            }
                            out.append("usecase \"").append(escapeLabel(label))
                                    .append("\" as ").append(aa)
                                    .append(" <<action>>\n");
                        }
                        out.append(aa).append(" --> ").append(compAlias).append('\n');
                    }
                }
            }
        }
    }

    private static void emitPermissions(StringBuilder out, AndroidProjectAnalysis analysis) {
        Set<String> perms = new TreeSet<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidPermissionInfo p : m.getPermissions()) {
                perms.add(p.getShortName());
            }
        }
        if (perms.isEmpty()) {
            return;
        }
        out.append("package \"permissions\" {\n");
        for (String p : perms) {
            // [name] 構文は名前に ']' が含まれると途中で閉じて構文エラーになるため、
            // 引用符付き component 形式 (']'/'"' を含んでも安全) で出力する。
            out.append("  component \"").append(escapeLabel(p))
                    .append("\" <<permission>>\n");
        }
        out.append("}\n");
    }

    private static String shortAction(String a) {
        int dot = a.lastIndexOf('.');
        return dot >= 0 ? a.substring(dot + 1) : a;
    }

    private static void emitLegend(StringBuilder out, AndroidProjectAnalysis a, Options o) {
        out.append("legend top left\n");
        out.append("== Android コンポーネント図 ==\n");
        out.append("Activity         : ユーザーが操作する画面\n");
        out.append("Service          : 画面を持たず裏で動く処理 (再生・通信など)\n");
        out.append("BroadcastReceiver: システムや他アプリからの通知を受け取る部品\n");
        out.append("ContentProvider  : 他アプリとデータを共有する窓口\n");
        out.append("<<launcher>>     : アプリ起動時に最初に開く画面\n");
        out.append("#LightYellow     : exported=true (他アプリから呼び出せる=外部公開)\n");
        if (o.showIntentFilters) {
            out.append("<<action>>       : このアプリが反応する操作の種類 (intent-filter)\n");
        }
        if (o.showPermissions) {
            out.append("<<permission>>   : アプリが必要とする許可 (uses-permission)\n");
        }
        out.append("endlegend\n");
    }

    /**
     * 引用符付きラベル ({@code "..."}) に安全に埋め込めるよう無害化する。
     * ダブルクォートはアポストロフィに、改行は空白に置換する
     * (PlantUML のラベル文字列が途中で閉じて構文エラーになるのを防ぐ)。
     */
    private static String escapeLabel(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('"', '\'').replace('\r', ' ').replace('\n', ' ');
    }

    private PlantUmlComponentDiagram() {
    }
}
