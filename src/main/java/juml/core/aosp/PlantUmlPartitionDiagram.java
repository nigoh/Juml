// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link AndroidBpModule} のリストを partition (配置) を package 枠にした
 * PlantUML 図に整形する。
 *
 * <p>フルツリーでは全モジュールを描くと読めなくなるため、partition を跨ぐ依存に
 * 参加しているモジュールだけをコンポーネントとして描き、残りは partition ごとの
 * 件数プレースホルダにまとめる。矢印は依存キー種別 (shared/static/header 等) で
 * 色分けし、Treble 境界 (system↔vendor) の直接リンクを目視できるようにする。</p>
 */
public final class PlantUmlPartitionDiagram {

    private PlantUmlPartitionDiagram() {
    }

    public static String render(List<AndroidBpModule> modules) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Partition Placement (Android.bp)\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");
        if (modules == null || modules.isEmpty()) {
            sb.append("note as N1\n(no Android.bp modules found)\nend note\n");
            sb.append("@enduml\n");
            return sb.toString();
        }
        SoongGraphAnalysis graph = SoongGraphAnalysis.of(modules);
        Map<String, AndroidBpModule> byName = new LinkedHashMap<>();
        for (AndroidBpModule m : modules) {
            if (!m.getName().isEmpty()) {
                byName.putIfAbsent(m.getName(), m);
            }
        }
        // 跨ぎ依存エッジ {from, to, kind} と、参加モジュール集合を集める
        List<String[]> edges = new ArrayList<>();
        Set<String> involved = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AndroidBpModule m : modules) {
            if (m.getName().isEmpty()) {
                continue;
            }
            for (String depRaw : m.getDeps()) {
                String dep = graph.canonical(depRaw);
                AndroidBpModule target = byName.get(dep);
                if (target == null || target.getPartition().equals(m.getPartition())) {
                    continue;
                }
                if (!seen.add(m.getName() + "->" + dep)) {
                    continue;
                }
                edges.add(new String[] {m.getName(), dep, m.kindOf(depRaw)});
                involved.add(m.getName());
                involved.add(dep);
            }
        }

        Map<String, List<AndroidBpModule>> byPartition =
                MarkdownPartitionReport.groupByPartition(modules);
        for (Map.Entry<String, List<AndroidBpModule>> e : byPartition.entrySet()) {
            String partition = e.getKey();
            sb.append("package \"").append(partition).append("\" ")
                    .append(partitionColor(partition)).append(" {\n");
            int rest = 0;
            for (AndroidBpModule m : e.getValue()) {
                if (!m.getName().isEmpty() && involved.contains(m.getName())) {
                    sb.append("  component \"").append(escape(m.getName()))
                            .append("\\n<<").append(m.getType()).append(">>\" as ")
                            .append(alias(m.getName())).append('\n');
                } else {
                    rest++;
                }
            }
            if (rest > 0) {
                sb.append("  component \"(+").append(rest)
                        .append(" modules)\" as ").append(alias("rest@" + partition))
                        .append(" #FFFFFF\n");
            }
            sb.append("}\n");
        }

        for (String[] e : edges) {
            sb.append(alias(e[0])).append(' ').append(arrowFor(e[2])).append(' ')
                    .append(alias(e[1]));
            if (!e[2].isEmpty()) {
                sb.append(" : ").append(e[2]);
            }
            sb.append('\n');
        }
        appendLegend(sb);
        sb.append("@enduml\n");
        return sb.toString();
    }

    /** 凡例: package の意味と矢印の種別。 */
    private static void appendLegend(StringBuilder sb) {
        sb.append("legend right\n");
        sb.append("package = partition (system / vendor / product / system_ext / odm ...)\n");
        sb.append("矢印 = partition を跨ぐ依存 (Treble 境界の確認対象)\n");
        sb.append("  青 実線 = shared_libs / 緑 実線 = static_libs\n");
        sb.append("  紫 破線 = header_libs / 橙 点線 = required / 黒 実線 = その他\n");
        sb.append("(+N modules) = 跨ぎ依存に参加しないモジュールの件数\n");
        sb.append("endlegend\n");
    }

    /** 依存キー種別 → 矢印表記 (Soong 依存図と揃える)。 */
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

    /** partition ごとの package 背景色。 */
    private static String partitionColor(String partition) {
        switch (partition) {
            case "system":
                return "#EAF2FB";
            case "system_ext":
                return "#E2ECF9";
            case "product":
                return "#E8F5E9";
            case "vendor":
                return "#FFF1F0";
            case "odm":
                return "#FFE9E0";
            case "recovery":
                return "#F5F5F5";
            case "ramdisk":
                return "#F0F0EA";
            default:
                return "#FFFFFF";
        }
    }

    /** モジュール名から一意な PlantUML alias を作る (Soong 図と同方式)。 */
    private static String alias(String name) {
        StringBuilder sb = new StringBuilder("p_");
        boolean replaced = false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append('_');
                replaced |= c != '_';
            }
        }
        if (replaced) {
            sb.append('_').append(Integer.toHexString(name.hashCode() & 0xfffff));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
