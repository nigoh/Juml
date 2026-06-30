// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link AndroidBpModule} のリストを PlantUML 依存図に整形する。
 *
 * <p>各モジュールをコンポーネントとして描画し、依存先 (libs/static_libs/etc.) を矢印で接続。
 * 同じプロジェクト内に宣言されたモジュールは category ({@code cc/java/android/aidl/hidl/build/other})
 * 単位で package グループ化される。プロジェクト外のモジュール (依存名のみ判明) は
 * {@code external} グループにまとめる。</p>
 *
 * <p>矢印は依存の種別 (static / shared / header / defaults / required 等) で色・線種を
 * 描き分け、循環依存に属するモジュールは赤枠で強調する。凡例で意味を示す。</p>
 */
public final class PlantUmlSoongDependencyDiagram {

    private PlantUmlSoongDependencyDiagram() {
    }

    public static String render(List<AndroidBpModule> modules) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Soong (Android.bp) Module Dependencies\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");

        SoongGraphAnalysis graph = SoongGraphAnalysis.of(modules);

        // name → module 索引
        Map<String, AndroidBpModule> byName = new LinkedHashMap<>();
        for (AndroidBpModule m : modules) {
            if (!m.getName().isEmpty()) {
                byName.put(m.getName(), m);
            }
        }

        // category 別 package グループ
        Map<String, List<AndroidBpModule>> byCategory = new LinkedHashMap<>();
        for (AndroidBpModule m : modules) {
            byCategory.computeIfAbsent(m.getCategory(), k -> new java.util.ArrayList<>())
                    .add(m);
        }
        for (Map.Entry<String, List<AndroidBpModule>> e : byCategory.entrySet()) {
            sb.append("package \"").append(e.getKey()).append("\" {\n");
            for (AndroidBpModule m : e.getValue()) {
                sb.append("  component \"").append(escape(m.getName())).append("\\n<<")
                        .append(m.getType()).append(">>\" as ")
                        .append(alias(m.getName())).append(' ')
                        .append(styleFor(m, graph.isInCycle(m.getName()))).append('\n');
            }
            sb.append("}\n");
        }

        // 外部参照 (依存名でしか出てこないモジュール) を集める。
        // AIDL 生成ライブラリ名 (foo-V3-java 等) はローカル aidl_interface へ畳み込む。
        Set<String> external = new LinkedHashSet<>();
        for (AndroidBpModule m : modules) {
            for (String dep : m.getDeps()) {
                String canon = graph.canonical(dep);
                if (!byName.containsKey(canon)) external.add(canon);
            }
        }
        if (!external.isEmpty()) {
            sb.append("package \"external\" #DDDDDD {\n");
            for (String name : external) {
                sb.append("  component \"").append(escape(name))
                        .append("\" as ").append(alias(name)).append(" #EEEEEE\n");
            }
            sb.append("}\n");
        }

        emitEdges(sb, modules, graph);
        appendLegend(sb);
        sb.append("@enduml\n");
        return sb.toString();
    }

    /** 依存エッジを種別ごとのスタイルで出力する (同一 from→dep は重複排除)。 */
    private static void emitEdges(StringBuilder sb, List<AndroidBpModule> modules,
                                  SoongGraphAnalysis graph) {
        Set<String> emitted = new LinkedHashSet<>();
        // 重複本数のカウント (kind を問わず from→dep 単位、AIDL 生成名は正規化後)
        Map<String, Integer> edgeCount = new HashMap<>();
        for (AndroidBpModule m : modules) {
            for (String dep : m.getDeps()) {
                edgeCount.merge(m.getName() + "->" + graph.canonical(dep), 1, Integer::sum);
            }
        }
        for (AndroidBpModule m : modules) {
            for (Map.Entry<String, List<String>> kd : m.getDepsByKind().entrySet()) {
                String kind = kd.getKey();
                for (String depRaw : kd.getValue()) {
                    String dep = graph.canonical(depRaw);
                    String key = m.getName() + "->" + dep;
                    if (!emitted.add(key)) continue;
                    int n = edgeCount.getOrDefault(key, 1);
                    sb.append(alias(m.getName())).append(' ')
                            .append(arrowFor(kind)).append(' ').append(alias(dep));
                    if (n > 1) sb.append(" : x").append(n);
                    sb.append('\n');
                }
            }
        }
    }

    /** 凡例: 矢印の色・線種とカテゴリ色、循環強調の意味を示す。 */
    private static void appendLegend(StringBuilder sb) {
        sb.append("legend right\n");
        sb.append("依存の種類 (矢印):\n");
        sb.append("  青 実線 = shared_libs (動的リンク)\n");
        sb.append("  緑 実線 = static_libs / whole_static_libs (静的リンク=埋め込み)\n");
        sb.append("  紫 破線 = header_libs (ヘッダのみ)\n");
        sb.append("  灰 点線 = defaults (プロパティ継承)\n");
        sb.append("  橙 点線 = required (実行時に必要)\n");
        sb.append("  黒 実線 = libs ほか\n");
        sb.append("赤背景 = 循環依存 (cycle) のメンバ\n");
        sb.append("カテゴリ色: cc=水色 / java=橙 / android=緑 / aidl=桃 / hidl=赤系\n");
        sb.append("endlegend\n");
    }

    /** 依存キー種別 → PlantUML の矢印表記。 */
    private static String arrowFor(String kind) {
        switch (kind) {
            case "shared_libs":
            case "system_shared_libs":
                return "-[#1f6feb]->";
            case "static_libs":
            case "whole_static_libs":
                return "-[#2da44e]->";
            case "header_libs":
            case "export_static_lib_headers":
            case "export_shared_lib_headers":
                return "-[#8250df,dashed]->";
            case "defaults":
                return "-[#999999,dotted]->";
            case "required":
                return "-[#bf8700,dotted]->";
            case "runtime_libs":
                return "-[#cf222e,dashed]->";
            default:
                return "-->";
        }
    }

    /** 循環依存メンバを強調する背景色 (赤系)。通常はカテゴリ色。 */
    static final String CYCLE_FILL = "#F4A6A6";

    /**
     * コンポーネント宣言の塗り色。循環メンバは赤系背景で強調し (component 短縮形は
     * 枠線スタイル指定を受け付けないため塗りで表現)、それ以外はカテゴリ色を使う。
     */
    private static String styleFor(AndroidBpModule m, boolean inCycle) {
        return inCycle ? CYCLE_FILL : colorFor(m.getCategory());
    }

    private static String colorFor(String category) {
        switch (category) {
            case "cc": return "#D7F0FF";
            case "java": return "#FFE8C8";
            case "android": return "#D5F5D0";
            case "aidl": return "#FFD5E8";
            case "hidl": return "#FFE0E0";
            case "build": return "#EEEEEE";
            default: return "#FFFFFF";
        }
    }

    private static String alias(String name) {
        StringBuilder sb = new StringBuilder("m_");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
