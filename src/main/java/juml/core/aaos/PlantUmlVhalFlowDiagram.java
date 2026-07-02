// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link VhalAccess} のリストを PlantUML フロー図に整形する。
 *
 * <p>クラス → Property → kind の 3 階層構造を「コンポーネント図」として描画:</p>
 * <ul>
 *   <li>左: 呼び出し元クラス (Caller)</li>
 *   <li>中央: VHAL Property (1 ノード = 1 プロパティ名)</li>
 *   <li>矢印: GET (緑) / SET (橙) / SUBSCRIBE (青) / UNSUBSCRIBE (灰)</li>
 * </ul>
 */
public final class PlantUmlVhalFlowDiagram {

    private PlantUmlVhalFlowDiagram() {
    }

    public static String render(List<VhalAccess> accesses) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title VHAL Property Flow\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");

        Set<String> callerIds = new LinkedHashSet<>();
        Set<String> propIds = new LinkedHashSet<>();
        // edges: caller → property → kinds count
        Map<String, Map<String, Map<VhalAccess.Kind, Integer>>> edges = new LinkedHashMap<>();
        for (VhalAccess a : accesses) {
            String caller = a.getCallerFqn();
            if (caller.isEmpty()) continue;
            String prop = a.getPropertyShortName();
            if (prop.isEmpty()) continue;
            callerIds.add(caller);
            propIds.add(prop);
            edges.computeIfAbsent(caller, k -> new LinkedHashMap<>())
                    .computeIfAbsent(prop, k -> new LinkedHashMap<>())
                    .merge(a.getKind(), 1, Integer::sum);
        }

        sb.append("package \"Callers\" {\n");
        for (String c : callerIds) {
            sb.append("  component \"").append(escape(c)).append("\" as ")
                    .append(alias("c", c)).append('\n');
        }
        sb.append("}\n");

        sb.append("package \"VHAL Properties\" {\n");
        for (String p : propIds) {
            sb.append("  component \"").append(escape(p)).append("\" as ")
                    .append(alias("p", p)).append(" #FFF2A8\n");
        }
        sb.append("}\n");

        for (Map.Entry<String, Map<String, Map<VhalAccess.Kind, Integer>>> e
                : edges.entrySet()) {
            String caller = e.getKey();
            for (Map.Entry<String, Map<VhalAccess.Kind, Integer>> pe
                    : e.getValue().entrySet()) {
                String prop = pe.getKey();
                for (Map.Entry<VhalAccess.Kind, Integer> ke
                        : pe.getValue().entrySet()) {
                    String label = ke.getKey().name();
                    int n = ke.getValue();
                    if (n > 1) {
                        label = label + " x" + n;
                    }
                    sb.append(alias("c", caller))
                            .append(" ").append(arrowFor(ke.getKey())).append(" ")
                            .append(alias("p", prop))
                            .append(" : ").append(label).append('\n');
                }
            }
        }

        sb.append("@enduml\n");
        return sb.toString();
    }

    private static String arrowFor(VhalAccess.Kind kind) {
        switch (kind) {
            case GET: return "-[#green]->";
            case SET: return "-[#orange]->";
            case SUBSCRIBE: return "-[#blue]->";
            case UNSUBSCRIBE: return "-[#gray]->";
            default: return "-->";
        }
    }

    private static String alias(String prefix, String id) {
        StringBuilder sb = new StringBuilder(prefix).append('_');
        boolean replaced = false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            } else {
                sb.append('_');
                replaced |= c != '_';
            }
        }
        // "a.b" と "a-b" が同じ別名に潰れて別ノードが合成されないよう、
        // 置換が起きた id には元 id のハッシュを付けて一意化する。
        if (replaced) {
            sb.append('_').append(Integer.toHexString(id.hashCode() & 0xfffff));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
