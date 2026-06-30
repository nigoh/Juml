// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link RroOverlay} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (オーバレイ数 / static 数 / 上書き対象パッケージ数)</li>
 *   <li>オーバレイ一覧 (overlay package / target / priority / static? / file)</li>
 *   <li>上書きされているターゲットパッケージ別グループ</li>
 * </ol>
 */
public final class MarkdownRroReport {

    private MarkdownRroReport() {
    }

    public static String render(List<RroOverlay> overlays) {
        StringBuilder sb = new StringBuilder();
        sb.append("# RRO Overlay Report\n\n");
        if (overlays == null || overlays.isEmpty()) {
            sb.append("(no RRO overlays detected)\n");
            return sb.toString();
        }

        int staticCount = 0;
        Map<String, java.util.List<RroOverlay>> byTarget = new LinkedHashMap<>();
        for (RroOverlay o : overlays) {
            if (o.isStatic()) staticCount++;
            byTarget.computeIfAbsent(o.getTargetPackage(),
                    k -> new java.util.ArrayList<>()).add(o);
        }
        sb.append("- Overlay count: ").append(overlays.size()).append('\n');
        sb.append("- Static overlays: ").append(staticCount).append('\n');
        sb.append("- Distinct target packages: ").append(byTarget.size()).append('\n');
        sb.append('\n');

        sb.append("## Overlays\n\n");
        sb.append("| Overlay package | Target | Priority | Static | File |\n");
        sb.append("|---|---|---|---|---|\n");
        java.util.List<RroOverlay> sorted = new java.util.ArrayList<>(overlays);
        sorted.sort(Comparator.comparing(RroOverlay::getOverlayPackage));
        for (RroOverlay o : sorted) {
            sb.append("| `").append(o.getOverlayPackage()).append("` | `")
                    .append(o.getTargetPackage()).append("` | ")
                    .append(o.getPriority() >= 0 ? String.valueOf(o.getPriority()) : "—")
                    .append(" | ").append(o.isStatic() ? "✔" : "")
                    .append(" | ").append(o.getFile().isEmpty() ? ""
                            : "`" + o.getFile() + "`").append(" |\n");
        }
        sb.append('\n');

        sb.append("## Target packages\n\n");
        for (Map.Entry<String, List<RroOverlay>> e : byTarget.entrySet()) {
            sb.append("### `").append(e.getKey()).append("`\n\n");
            sb.append("- Overlays: ").append(e.getValue().size()).append('\n');
            for (RroOverlay o : e.getValue()) {
                sb.append("  - `").append(o.getOverlayPackage()).append('`');
                if (o.getPriority() >= 0) {
                    sb.append(" (priority ").append(o.getPriority()).append(")");
                }
                if (o.isStatic()) {
                    sb.append(" *(static)*");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
