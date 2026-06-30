// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ScreenTransition} のリストを PlantUML 状態遷移図に整形する。
 *
 * <p>各 Activity を状態ノードとして描画し、遷移種別に応じた矢印で接続する:</p>
 * <ul>
 *   <li>{@link ScreenTransition.Kind#START_ACTIVITY}: 通常矢印</li>
 *   <li>{@link ScreenTransition.Kind#START_FOR_RESULT}: 双方向矢印 (結果受け取り)</li>
 *   <li>{@link ScreenTransition.Kind#SET_CLASS}: 破線</li>
 * </ul>
 */
public final class PlantUmlScreenFlowDiagram {

    private PlantUmlScreenFlowDiagram() {
    }

    public static String render(List<ScreenTransition> transitions) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title 画面遷移図 (Screen Flow)\n");
        sb.append("skinparam shadowing false\n");
        sb.append("skinparam state {\n");
        sb.append("  BackgroundColor #F0F8FF\n");
        sb.append("  BorderColor #3070A0\n");
        sb.append("}\n");

        // ノード集合: from + target すべて
        Set<String> nodes = new LinkedHashSet<>();
        for (ScreenTransition t : transitions) {
            if (!t.getFromSimpleName().isEmpty()) nodes.add(t.getFromSimpleName());
            if (!t.getTargetSimpleName().isEmpty()) nodes.add(t.getTargetSimpleName());
        }
        if (nodes.isEmpty()) {
            // 遷移が 1 件も無いと図が真っ白になり素人が戸惑うため、理由を明示する
            sb.append("note as N1\n");
            sb.append("  画面遷移が見つかりませんでした。\n");
            sb.append("  startActivity / Fragment 切替 / Navigation などの\n");
            sb.append("  画面遷移コードが対象に含まれているか確認してください。\n");
            sb.append("end note\n");
            sb.append("@enduml\n");
            return sb.toString();
        }
        for (String n : nodes) {
            sb.append("state \"").append(escape(n)).append("\" as ").append(alias(n))
                    .append('\n');
        }

        // 重複エッジを集約
        Map<String, EdgeAgg> edges = new LinkedHashMap<>();
        for (ScreenTransition t : transitions) {
            String fromS = t.getFromSimpleName();
            String toS = t.getTargetSimpleName();
            if (fromS.isEmpty() || toS.isEmpty()) continue;
            String key = fromS + "->" + toS + "/" + t.getKind();
            EdgeAgg agg = edges.computeIfAbsent(key,
                    k -> new EdgeAgg(fromS, toS, t.getKind()));
            agg.count++;
            if (!t.getFromMethod().isEmpty()) {
                agg.methods.add(t.getFromMethod());
            }
        }
        // 凡例に出すため、実際に使われている遷移種別を出現順で集める
        Set<ScreenTransition.Kind> usedKinds = new LinkedHashSet<>();
        for (EdgeAgg e : edges.values()) {
            usedKinds.add(e.kind);
            String arrow = arrowFor(e.kind);
            String label = e.kind.jpLabel();
            if (e.count > 1) label += " x" + e.count;
            if (!e.methods.isEmpty()) {
                String head = e.methods.iterator().next();
                label = head + "()\\n" + label;
            }
            sb.append(alias(e.from)).append(' ').append(arrow).append(' ')
                    .append(alias(e.to)).append(" : ").append(label).append('\n');
        }

        emitLegend(sb, usedKinds);
        sb.append("@enduml\n");
        return sb.toString();
    }

    /**
     * 図中に現れた遷移種別だけを「日本語ラベル: 説明」の形で凡例に並べる。
     * 素人が矢印の色やラベルの意味をその場で確認できるようにするため。
     */
    private static void emitLegend(StringBuilder sb, Set<ScreenTransition.Kind> usedKinds) {
        if (usedKinds.isEmpty()) {
            return;
        }
        sb.append("legend top left\n");
        sb.append("== 画面遷移の種類 ==\n");
        for (ScreenTransition.Kind k : usedKinds) {
            sb.append(k.jpLabel()).append(": ").append(k.jpDescription()).append('\n');
        }
        sb.append("endlegend\n");
    }

    // State 図で有効な矢印のみ使う（双方向 <--> や ..> は state 図では構文エラーになる）。
    private static String arrowFor(ScreenTransition.Kind kind) {
        switch (kind) {
            case START_FOR_RESULT: return "-[#1f6fb0]->";
            case SET_CLASS: return "-[#888888,dashed]->";
            case SCREEN_PUSH: return "-[#2e8b57]->";
            case FRAGMENT_TXN: return "-[#b8860b]->";
            case NAV_ACTION: return "-[#8a2be2]->";
            case COMPOSE_NAVIGATE: return "-[#d2691e]->";
            default: return "-->";
        }
    }

    private static String alias(String id) {
        StringBuilder sb = new StringBuilder("s_");
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    private static final class EdgeAgg {
        final String from;
        final String to;
        final ScreenTransition.Kind kind;
        int count = 0;
        final Set<String> methods = new LinkedHashSet<>();

        EdgeAgg(String from, String to, ScreenTransition.Kind kind) {
            this.from = from;
            this.to = to;
            this.kind = kind;
        }
    }
}
