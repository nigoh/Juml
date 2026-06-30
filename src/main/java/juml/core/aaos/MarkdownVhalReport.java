// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link VhalAccess} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (アクセス総数、Property 数、参照クラス数)</li>
 *   <li>Property 別アクセス一覧 (GET/SET/SUBSCRIBE 列)</li>
 *   <li>アクセス箇所一覧 (caller / kind / file:line)</li>
 * </ol>
 */
public final class MarkdownVhalReport {

    private MarkdownVhalReport() {
    }

    public static String render(List<VhalAccess> accesses,
                                  VehiclePropertyCatalog catalog) {
        if (accesses == null) {
            accesses = new ArrayList<>();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# VHAL Property Flow Report\n\n");

        // 集計
        Map<String, int[]> perProperty = new LinkedHashMap<>(); // GET, SET, SUB, UNSUB
        java.util.Set<String> callers = new java.util.LinkedHashSet<>();
        for (VhalAccess a : accesses) {
            String key = a.getPropertyShortName();
            int[] counts = perProperty.computeIfAbsent(key, k -> new int[4]);
            switch (a.getKind()) {
                case GET: counts[0]++; break;
                case SET: counts[1]++; break;
                case SUBSCRIBE: counts[2]++; break;
                case UNSUBSCRIBE: counts[3]++; break;
                default: break;
            }
            callers.add(a.getCallerFqn());
        }

        sb.append("- Total accesses: ").append(accesses.size()).append('\n');
        sb.append("- Distinct properties: ").append(perProperty.size()).append('\n');
        sb.append("- Caller classes: ").append(callers.size()).append('\n');
        if (catalog != null && catalog.size() > 0) {
            sb.append("- Catalog entries: ").append(catalog.size()).append('\n');
        }
        sb.append('\n');

        if (!perProperty.isEmpty()) {
            sb.append("## Property usage\n\n");
            sb.append("| Property | ID | GET | SET | SUBSCRIBE | UNSUBSCRIBE |\n");
            sb.append("|---|---|---|---|---|---|\n");
            List<Map.Entry<String, int[]>> sorted = new ArrayList<>(perProperty.entrySet());
            sorted.sort(Comparator.comparing(Map.Entry::getKey));
            for (Map.Entry<String, int[]> e : sorted) {
                int[] c = e.getValue();
                String id = "—";
                if (catalog != null) {
                    Optional<Long> v = catalog.idOf(e.getKey());
                    if (v.isPresent()) {
                        id = "0x" + Long.toHexString(v.get());
                    }
                }
                sb.append("| `").append(e.getKey()).append("` | ").append(id)
                        .append(" | ").append(c[0])
                        .append(" | ").append(c[1])
                        .append(" | ").append(c[2])
                        .append(" | ").append(c[3]).append(" |\n");
            }
            sb.append('\n');
        }

        sb.append("## Access sites\n\n");
        if (accesses.isEmpty()) {
            sb.append("(no VHAL accesses detected)\n");
            return sb.toString();
        }
        sb.append("| Caller | Method | Kind | Property | Area | Location |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (VhalAccess a : accesses) {
            String loc = a.getFile().isEmpty() ? ""
                    : "`" + a.getFile() + ":" + a.getLineHint() + "`";
            sb.append("| `").append(a.getCallerFqn()).append("` | ")
                    .append(a.getCallerMethod().isEmpty() ? "—" : "`" + a.getCallerMethod() + "`")
                    .append(" | ").append(a.getKind().name())
                    .append(" | `").append(a.getPropertyShortName()).append("`")
                    .append(" | ").append(a.getAreaToken().isEmpty() ? "—"
                            : "`" + a.getAreaToken() + "`")
                    .append(" | ").append(loc).append(" |\n");
        }
        return sb.toString();
    }
}
