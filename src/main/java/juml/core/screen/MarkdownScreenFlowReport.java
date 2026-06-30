// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ScreenTransition} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (画面数、遷移エッジ数)</li>
 *   <li>画面ごとの入出力 (どこから来てどこへ行くか)</li>
 *   <li>遷移エッジ詳細 (caller / target / kind / file:line)</li>
 * </ol>
 *
 * <p>遷移先は Activity だけでなく Fragment / Dialog / Car App Library の Screen も
 * 含むため、ノードは Activity に限定せず中立に「Screen (画面)」と表記する。</p>
 */
public final class MarkdownScreenFlowReport {

    private MarkdownScreenFlowReport() {
    }

    public static String render(List<ScreenTransition> transitions) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 画面遷移レポート — Screen Flow Report\n\n");
        if (transitions == null || transitions.isEmpty()) {
            sb.append("画面遷移が見つかりませんでした "
                    + "(no screen transitions detected)。\n\n");
            sb.append("startActivity / Fragment 切替 / Navigation などの画面遷移コードが"
                    + "対象に含まれているか確認してください。\n");
            return sb.toString();
        }

        // ノード集合 + 入出力
        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Set<String>> incoming = new LinkedHashMap<>();
        Set<String> screens = new LinkedHashSet<>();
        for (ScreenTransition t : transitions) {
            String from = t.getFromSimpleName();
            String to = t.getTargetSimpleName();
            if (!from.isEmpty()) screens.add(from);
            if (!to.isEmpty()) screens.add(to);
            if (!from.isEmpty() && !to.isEmpty()) {
                outgoing.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
                incoming.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
            }
        }

        sb.append("- Screens involved: ").append(screens.size()).append('\n');
        sb.append("- Transitions: ").append(transitions.size()).append('\n');
        sb.append('\n');

        sb.append("## Screens\n\n");
        sb.append("| Screen | Outgoing | Incoming |\n");
        sb.append("|---|---|---|\n");
        List<String> sorted = new ArrayList<>(screens);
        sorted.sort(Comparator.naturalOrder());
        for (String a : sorted) {
            String out = joinOrDash(outgoing.get(a));
            String in = joinOrDash(incoming.get(a));
            sb.append("| `").append(a).append("` | ").append(out).append(" | ")
                    .append(in).append(" |\n");
        }
        sb.append('\n');

        sb.append("## Transitions\n\n");
        sb.append("| From | Method | 遷移の種類 | To | Location |\n");
        sb.append("|---|---|---|---|---|\n");
        Set<ScreenTransition.Kind> usedKinds = new LinkedHashSet<>();
        for (ScreenTransition t : transitions) {
            usedKinds.add(t.getKind());
            String loc = t.getFile().isEmpty() ? ""
                    : "`" + t.getFile() + ":" + t.getLineHint() + "`";
            sb.append("| `").append(t.getFromSimpleName()).append("` | ")
                    .append(t.getFromMethod().isEmpty() ? "—"
                            : "`" + t.getFromMethod() + "`")
                    .append(" | ").append(t.getKind().jpLabel())
                    .append(" | `").append(t.getTargetSimpleName()).append("`")
                    .append(" | ").append(loc).append(" |\n");
        }
        appendKindLegend(sb, usedKinds);
        appendRoutes(sb, transitions);
        return sb.toString();
    }

    /**
     * 「遷移の種類」列に出てくる日本語ラベルの意味を表で説明する。
     * 素人が START_FOR_RESULT 等の専門用語を知らなくても読めるようにするため。
     */
    private static void appendKindLegend(StringBuilder sb, Set<ScreenTransition.Kind> usedKinds) {
        if (usedKinds.isEmpty()) {
            return;
        }
        sb.append("\n### 遷移の種類について\n\n");
        sb.append("| 種類 | 意味 |\n");
        sb.append("|---|---|\n");
        for (ScreenTransition.Kind k : usedKinds) {
            sb.append("| ").append(k.jpLabel()).append(" | ")
                    .append(k.jpDescription()).append(" |\n");
        }
    }

    /** 起点からの多段遷移ルートを列挙して追記する。 */
    private static void appendRoutes(StringBuilder sb, List<ScreenTransition> transitions) {
        List<List<String>> routes = ScreenRouteBuilder.routes(transitions);
        sb.append("\n## Routes (entry → ...)\n\n");
        if (routes.isEmpty()) {
            sb.append("(no multi-step routes; transitions form isolated edges or cycles)\n");
            return;
        }
        for (List<String> route : routes) {
            sb.append("- ").append(String.join(" → ", route)).append('\n');
        }
    }

    private static String joinOrDash(Set<String> set) {
        if (set == null || set.isEmpty()) return "—";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (String s : set) {
            if (i > 0) sb.append(", ");
            sb.append('`').append(s).append('`');
            i++;
        }
        return sb.toString();
    }
}
