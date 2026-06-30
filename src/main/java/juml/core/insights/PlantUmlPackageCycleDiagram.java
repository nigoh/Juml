// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.insights;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link InsightsModel} のパッケージ循環依存を PlantUML で可視化する。
 *
 * <p>循環 (SCC) に参加するパッケージと、その隣接パッケージのみを描画し、
 * 循環を構成するエッジを赤の太線でハイライトする。孤立ノードを出力しないのは
 * 同梱 PlantUML の Smetana レイアウトが孤立ノードで qsort 例外を起こす
 * 既知の問題を避けるため ({@code PlantUmlGradleDependencyGraph} と同じ方針)。</p>
 */
public final class PlantUmlPackageCycleDiagram {

    private PlantUmlPackageCycleDiagram() {
    }

    public static String render(InsightsModel model) {
        if (model == null) {
            throw new IllegalArgumentException("model is null");
        }
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        out.append("title Package Cycles\n");
        if (model.getPackageCycles().isEmpty()) {
            out.append("note as N1\n");
            out.append("循環依存は検出されませんでした\n");
            out.append("endnote\n");
            out.append("@enduml\n");
            return out.toString();
        }

        // 循環参加パッケージと SCC 内エッジ集合
        Set<String> cyclePackages = new LinkedHashSet<>();
        Set<String> cycleEdgeKeys = new LinkedHashSet<>();
        for (InsightsModel.PackageCycle c : model.getPackageCycles()) {
            cyclePackages.addAll(c.getPackages());
            for (InsightsModel.PackageEdge e : c.getEdges()) {
                cycleEdgeKeys.add(edgeKey(e));
            }
        }
        // 隣接パッケージ (循環パッケージとエッジで繋がるもの) も文脈として描画
        Set<String> visible = new LinkedHashSet<>(cyclePackages);
        for (InsightsModel.PackageEdge e : model.getPackageEdges()) {
            if (cyclePackages.contains(e.getFrom())) {
                visible.add(e.getTo());
            }
            if (cyclePackages.contains(e.getTo())) {
                visible.add(e.getFrom());
            }
        }

        // ノード (PlantUmlPackageDiagram のパッケージノード表現を踏襲)
        Map<String, String> aliasByPkg = new LinkedHashMap<>();
        int seq = 0;
        for (String pkg : visible) {
            String alias = "P" + (seq++);
            aliasByPkg.put(pkg, alias);
            Integer count = model.getClassCountByPackage().get(pkg);
            out.append("package \"").append(escape(displayPackage(pkg)));
            if (count != null) {
                out.append("\\n").append(count)
                        .append(count == 1 ? " class" : " classes");
            }
            out.append("\" as ").append(alias);
            if (cyclePackages.contains(pkg)) {
                out.append(" #FFCCCC");
            }
            out.append(" {\n}\n");
        }

        // エッジ (可視ノード間のみ。循環エッジは赤太線)
        Set<String> emitted = new LinkedHashSet<>();
        for (InsightsModel.PackageEdge e : model.getPackageEdges()) {
            String from = aliasByPkg.get(e.getFrom());
            String to = aliasByPkg.get(e.getTo());
            if (from == null || to == null || !emitted.add(from + "->" + to)) {
                continue;
            }
            String arrow = cycleEdgeKeys.contains(edgeKey(e))
                    ? " -[#Red,bold]-> " : " -[#Gray]-> ";
            out.append(from).append(arrow).append(to).append('\n');
        }

        emitLegend(out);
        out.append("@enduml\n");
        return out.toString();
    }

    private static String edgeKey(InsightsModel.PackageEdge e) {
        return e.getFrom() + " -> " + e.getTo();
    }

    private static String displayPackage(String pkg) {
        return pkg == null || pkg.isEmpty() ? "(default)" : pkg;
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private static void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== パッケージ循環依存 ==\n");
        out.append("  <color:#Red>赤の太線</color> は循環を構成する依存\n");
        out.append("  <color:#Gray>灰線</color> は循環外の周辺依存\n");
        out.append("  赤背景のパッケージが循環に参加\n");
        out.append("endlegend\n");
    }
}
