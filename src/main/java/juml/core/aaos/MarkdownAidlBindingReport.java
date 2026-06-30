// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AIDL ↔ 実装クラスの紐付けを Markdown 表に整形する。
 */
public final class MarkdownAidlBindingReport {

    private MarkdownAidlBindingReport() {
    }

    public static String render(Map<String, List<AidlBinding>> bindings) {
        StringBuilder sb = new StringBuilder();
        sb.append("# AIDL ↔ Implementation Bindings\n\n");
        if (bindings == null || bindings.isEmpty()) {
            sb.append("(no AIDL interfaces detected)\n");
            return sb.toString();
        }
        int unbound = 0;
        int boundIfaces = 0;
        int totalImpls = 0;
        for (List<AidlBinding> v : bindings.values()) {
            if (v.isEmpty()) unbound++;
            else {
                boundIfaces++;
                totalImpls += v.size();
            }
        }
        sb.append("- AIDL interfaces: ").append(bindings.size()).append('\n');
        sb.append("- With implementations: ").append(boundIfaces).append('\n');
        sb.append("- Without implementations: ").append(unbound).append('\n');
        sb.append("- Total implementation classes: ").append(totalImpls).append('\n');
        sb.append('\n');

        sb.append("## Bindings\n\n");
        sb.append("| AIDL interface | Implementation(s) |\n");
        sb.append("|---|---|\n");
        List<Map.Entry<String, List<AidlBinding>>> sorted =
                new ArrayList<>(bindings.entrySet());
        sorted.sort(Comparator.comparing(Map.Entry::getKey));
        for (Map.Entry<String, List<AidlBinding>> e : sorted) {
            String aidl = e.getKey();
            List<AidlBinding> impls = e.getValue();
            String implCol;
            if (impls.isEmpty()) {
                implCol = "_(none)_";
            } else {
                StringBuilder cells = new StringBuilder();
                for (int i = 0; i < impls.size(); i++) {
                    if (i > 0) cells.append("<br>");
                    cells.append("`").append(impls.get(i).getImplementationFqn()).append("`");
                }
                implCol = cells.toString();
            }
            sb.append("| `").append(aidl).append("` | ").append(implCol).append(" |\n");
        }
        return sb.toString();
    }
}
