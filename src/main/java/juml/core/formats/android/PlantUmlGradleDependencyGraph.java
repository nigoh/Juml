// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.uml.PlantUmlCommentFormatter;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Gradle のモジュール依存と外部ライブラリ依存を PlantUML コンポーネント図として生成する。
 */
public final class PlantUmlGradleDependencyGraph {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public boolean includeTestScopes = false;
        public boolean includeExternalLibs = true;
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
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }
        // 同梱 PlantUML の Smetana レイアウトは孤立ノード (in/out エッジを 1 本も
        // 持たないコンポーネント) を含むグラフで qsort 内 IllegalStateException を
        // 起こすことがある。集約用ルートプロジェクト (`:root` 等) や依存を持たない
        // モジュールは情報量も小さいため、出力前に除外する。
        Set<String> connectedModules = collectConnectedModules(analysis, o);
        Map<String, String> moduleAlias = new LinkedHashMap<>();
        int seq = 0;
        // モジュールノード
        for (String module : analysis.getGradleByModule().keySet()) {
            if (!connectedModules.contains(module)) {
                continue;
            }
            String alias = "M" + (seq++);
            moduleAlias.put(module, alias);
            GradleProjectInfo info = analysis.getGradleByModule().get(module);
            String stereo;
            if (info.isAndroidApplication()) {
                stereo = " <<application>>";
            } else if (info.isAndroidLibrary()) {
                stereo = " <<library>>";
            } else {
                stereo = " <<module>>";
            }
            out.append("component \"").append(escapeLabel(module)).append("\" as ")
                    .append(alias).append(stereo).append('\n');
        }
        // 外部ライブラリノード
        Map<String, String> libAlias = new LinkedHashMap<>();
        if (o.includeExternalLibs) {
            Set<String> libs = new TreeSet<>();
            for (GradleProjectInfo info : analysis.getGradleByModule().values()) {
                for (GradleDependency d : info.getDependencies()) {
                    if (d.isModuleReference()) {
                        continue;
                    }
                    if (!o.includeTestScopes && isTestScope(d.getScope())) {
                        continue;
                    }
                    if (d.getGroup() != null && d.getName() != null) {
                        libs.add(d.getGroup() + ":" + d.getName());
                    } else if (d.getName() != null) {
                        libs.add(d.getName());
                    }
                }
            }
            for (String lib : libs) {
                String alias = "L" + (seq++);
                libAlias.put(lib, alias);
                out.append("component \"").append(escapeLabel(lib)).append("\" as ").append(alias)
                        .append(" <<external>>\n");
            }
        }
        // エッジ
        for (Map.Entry<String, GradleProjectInfo> e
                : analysis.getGradleByModule().entrySet()) {
            String from = moduleAlias.get(e.getKey());
            if (from == null) {
                continue;
            }
            for (GradleDependency d : e.getValue().getDependencies()) {
                if (!o.includeTestScopes && isTestScope(d.getScope())) {
                    continue;
                }
                String arrow = arrowForScope(d.getScope());
                if (d.isModuleReference()) {
                    String toModule = resolveModuleKey(d.getModuleRef(), moduleAlias.keySet());
                    String to = toModule != null ? moduleAlias.get(toModule) : null;
                    if (to != null) {
                        out.append(from).append(' ').append(arrow).append(' ')
                                .append(to).append(" : ").append(d.getScope()).append('\n');
                    }
                } else if (o.includeExternalLibs) {
                    String key = (d.getGroup() != null ? d.getGroup() + ":" : "")
                            + (d.getName() != null ? d.getName() : "");
                    String to = libAlias.get(key);
                    if (to != null) {
                        out.append(from).append(' ').append(arrow).append(' ')
                                .append(to).append(" : ").append(d.getScope()).append('\n');
                    }
                }
            }
        }
        if (o.includeLegend) {
            emitLegend(out, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * 後段の出力ロジックと同じフィルタ条件で「少なくとも 1 本のエッジに参加する」
     * モジュール集合を求める。孤立ノードを diagram から除外して Smetana の qsort
     * バグを回避するために使う。
     */
    private static Set<String> collectConnectedModules(
            AndroidProjectAnalysis analysis, Options o) {
        Set<String> connected = new HashSet<>();
        for (Map.Entry<String, GradleProjectInfo> e
                : analysis.getGradleByModule().entrySet()) {
            String from = e.getKey();
            for (GradleDependency d : e.getValue().getDependencies()) {
                if (!o.includeTestScopes && isTestScope(d.getScope())) {
                    continue;
                }
                if (d.isModuleReference()) {
                    String to = resolveModuleKey(d.getModuleRef(),
                            analysis.getGradleByModule().keySet());
                    if (to != null) {
                        connected.add(from);
                        connected.add(to);
                    }
                } else if (o.includeExternalLibs) {
                    // 外部ライブラリへの依存もモジュール側の「接続あり」と見なす
                    connected.add(from);
                }
            }
        }
        return connected;
    }

    /**
     * {@code project(...)} のモジュール参照を実際のモジュールキーへ解決する。
     *
     * <p>まず完全一致を試し、失敗したらコロン区切りの末尾セグメント一致でフォールバックする。
     * これは Gradle のサブプロジェクトのディレクトリ単体をルートとして開いたとき
     * (モジュール名がルート相対の {@code "common"} になる一方、依存宣言は絶対 Gradle パス
     * {@code ":showcase:common"} を保持するケース) でも、モジュール間の {@code project()}
     * 依存エッジを失わないようにするため。末尾セグメントが複数モジュールで重複する場合は
     * 誤接続を避けるため解決しない (null)。</p>
     */
    static String resolveModuleKey(String ref, Set<String> moduleKeys) {
        if (ref == null || ref.isEmpty() || moduleKeys == null) {
            return null;
        }
        if (moduleKeys.contains(ref)) {
            return ref;
        }
        String refLeaf = lastSegment(ref);
        String match = null;
        for (String key : moduleKeys) {
            if (lastSegment(key).equals(refLeaf)) {
                if (match != null) {
                    return null;
                }
                match = key;
            }
        }
        return match;
    }

    private static String lastSegment(String path) {
        int i = path.lastIndexOf(':');
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static boolean isTestScope(String scope) {
        if (scope == null) {
            return false;
        }
        return scope.startsWith("test") || scope.startsWith("androidTest");
    }

    private static String arrowForScope(String scope) {
        if (scope == null) {
            return "-->";
        }
        if (scope.startsWith("test") || scope.startsWith("androidTest")) {
            return "..>";
        }
        // api は推移的に公開される依存なので太線で強調する
        if (scope.startsWith("api")) {
            return "-[bold]->";
        }
        // compileOnly / runtimeOnly は実行時 or コンパイル時のみの弱い依存
        if ("compileOnly".equals(scope) || "runtimeOnly".equals(scope)) {
            return "..>";
        }
        return "-->";
    }

    private static void emitLegend(StringBuilder out, Options o) {
        out.append("legend top left\n");
        out.append("== Gradle 依存グラフ ==\n");
        out.append("component <<application>>  com.android.application\n");
        out.append("component <<library>>      com.android.library\n");
        out.append("component <<module>>       上記以外のモジュール\n");
        if (o.includeExternalLibs) {
            out.append("component <<external>>     外部 Maven 依存\n");
        }
        out.append("A --> B                    implementation 依存\n");
        out.append("A -[bold]-> B              api 依存 (推移的に公開)\n");
        out.append("A ..> B                    compileOnly/runtimeOnly 依存\n");
        if (o.includeTestScopes) {
            out.append("A ..> B                    test/androidTest 依存\n");
        }
        out.append("endlegend\n");
    }

    /** 引用符付きラベルに安全に埋め込めるよう無害化する (" → '、改行 → 空白)。 */
    private static String escapeLabel(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('"', '\'').replace('\r', ' ').replace('\n', ' ');
    }

    private PlantUmlGradleDependencyGraph() {
    }
}
