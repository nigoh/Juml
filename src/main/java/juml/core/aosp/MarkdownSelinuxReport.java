// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link SelinuxRule} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (種別ごとの件数)</li>
 *   <li>ドメイン宣言一覧 (type)</li>
 *   <li>ドメインごとに許可された target/class/perms</li>
 *   <li>neverallow / dontaudit のサマリー</li>
 * </ol>
 */
public final class MarkdownSelinuxReport {

    private MarkdownSelinuxReport() {
    }

    public static String render(List<SelinuxRule> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("# SELinux Policy Report\n\n");
        if (rules == null || rules.isEmpty()) {
            sb.append("(no SELinux policy files found)\n");
            return sb.toString();
        }

        // カウンタ
        Map<SelinuxRule.Kind, Integer> counts = new LinkedHashMap<>();
        for (SelinuxRule r : rules) {
            counts.merge(r.getKind(), 1, Integer::sum);
        }
        for (Map.Entry<SelinuxRule.Kind, Integer> e : counts.entrySet()) {
            sb.append("- ").append(e.getKey().name())
                    .append(": ").append(e.getValue()).append('\n');
        }
        sb.append('\n');

        // type declarations
        List<SelinuxRule> typeDecls = filter(rules, SelinuxRule.Kind.TYPE_DECL);
        if (!typeDecls.isEmpty()) {
            sb.append("## Domain / Type Declarations\n\n");
            sb.append("| Type | Attributes | File |\n");
            sb.append("|---|---|---|\n");
            typeDecls.sort(Comparator.comparing(SelinuxRule::getSubject));
            for (SelinuxRule r : typeDecls) {
                String attrs = r.getAttributes().isEmpty() ? "—"
                        : "`" + String.join(", ", r.getAttributes()) + "`";
                String loc = r.getFile().isEmpty() ? ""
                        : "`" + r.getFile() + ":" + r.getLine() + "`";
                sb.append("| `").append(r.getSubject()).append("` | ")
                        .append(attrs).append(" | ").append(loc).append(" |\n");
            }
            sb.append('\n');
        }

        // allow rules grouped by subject
        List<SelinuxRule> allows = filter(rules, SelinuxRule.Kind.ALLOW);
        if (!allows.isEmpty()) {
            sb.append("## Allow Rules — by Subject\n\n");
            Map<String, List<SelinuxRule>> bySubject = groupBy(allows,
                    SelinuxRule::getSubject);
            for (Map.Entry<String, List<SelinuxRule>> e : bySubject.entrySet()) {
                sb.append("### `").append(e.getKey()).append('`').append("\n\n");
                sb.append("| Target | Class | Permissions |\n");
                sb.append("|---|---|---|\n");
                for (SelinuxRule r : e.getValue()) {
                    sb.append("| `").append(r.getTarget()).append("` | `")
                            .append(r.getObjectClass()).append("` | `")
                            .append(String.join(" ", r.getPermissions()))
                            .append("` |\n");
                }
                sb.append('\n');
            }
        }

        // neverallow
        List<SelinuxRule> nevers = filter(rules, SelinuxRule.Kind.NEVERALLOW);
        if (!nevers.isEmpty()) {
            sb.append("## Neverallow Rules\n\n");
            sb.append("| Subject | Target | Class | Forbidden permissions | File |\n");
            sb.append("|---|---|---|---|---|\n");
            for (SelinuxRule r : nevers) {
                String loc = r.getFile().isEmpty() ? ""
                        : "`" + r.getFile() + ":" + r.getLine() + "`";
                sb.append("| `").append(r.getSubject()).append("` | `")
                        .append(r.getTarget()).append("` | `")
                        .append(r.getObjectClass()).append("` | `")
                        .append(String.join(" ", r.getPermissions()))
                        .append("` | ").append(loc).append(" |\n");
            }
        }
        return sb.toString();
    }

    private static List<SelinuxRule> filter(List<SelinuxRule> in, SelinuxRule.Kind kind) {
        List<SelinuxRule> out = new ArrayList<>();
        for (SelinuxRule r : in) {
            if (r.getKind() == kind) out.add(r);
        }
        return out;
    }

    private static <T> Map<String, List<T>> groupBy(List<T> in,
                                                      java.util.function.Function<T, String> key) {
        Map<String, List<T>> out = new LinkedHashMap<>();
        Set<String> keys = new LinkedHashSet<>();
        for (T t : in) keys.add(key.apply(t));
        for (String k : keys) {
            List<T> bucket = new ArrayList<>();
            for (T t : in) {
                if (k.equals(key.apply(t))) bucket.add(t);
            }
            out.put(k, bucket);
        }
        return out;
    }
}
