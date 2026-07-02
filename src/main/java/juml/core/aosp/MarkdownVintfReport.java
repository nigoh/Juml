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
import java.util.TreeSet;

/**
 * {@link VintfProjectScanner.Entry} のリストを Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (ファイル数の種別内訳 / HAL 宣言数)</li>
 *   <li>manifest ファイル一覧 (種別 / version / level / kernel / sepolicy)</li>
 *   <li>HAL 宣言一覧 (format / transport / versions / interface / instance)</li>
 *   <li>compatibility-matrix の要求と device manifest の宣言の突き合わせ</li>
 * </ol>
 */
public final class MarkdownVintfReport {

    private MarkdownVintfReport() {
    }

    public static String render(List<VintfProjectScanner.Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# VINTF Manifest Report\n\n");
        if (entries == null || entries.isEmpty()) {
            sb.append("(no VINTF manifests found)\n");
            return sb.toString();
        }
        appendSummary(sb, entries);
        appendManifestTable(sb, entries);
        appendHalTable(sb, entries);
        appendMatrixCheck(sb, entries);
        return sb.toString();
    }

    /** 種別ごとのファイル数と HAL 宣言総数のサマリー。 */
    private static void appendSummary(StringBuilder sb,
                                      List<VintfProjectScanner.Entry> entries) {
        Map<VintfManifest.Kind, Integer> counts = new LinkedHashMap<>();
        int halCount = 0;
        for (VintfProjectScanner.Entry e : entries) {
            counts.merge(e.getManifest().getKind(), 1, Integer::sum);
            halCount += e.getManifest().getHals().size();
        }
        sb.append("- Total manifests: ").append(entries.size()).append('\n');
        for (Map.Entry<VintfManifest.Kind, Integer> e : counts.entrySet()) {
            sb.append("- ").append(kindLabel(e.getKey())).append(": ")
                    .append(e.getValue()).append('\n');
        }
        sb.append("- HAL declarations (HAL 宣言数): ").append(halCount).append('\n');
        sb.append('\n');
    }

    /** manifest ファイル一覧 (種別・version・level・kernel・sepolicy・HAL 数)。 */
    private static void appendManifestTable(StringBuilder sb,
                                            List<VintfProjectScanner.Entry> entries) {
        sb.append("## Manifests (ファイル一覧)\n\n");
        sb.append("| File | Kind | Version | Level | Kernel | Sepolicy | HALs |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (VintfProjectScanner.Entry e : entries) {
            VintfManifest m = e.getManifest();
            sb.append("| `").append(e.getFile()).append("` | ")
                    .append(kindLabel(m.getKind())).append(" | ")
                    .append(dash(m.getVersion())).append(" | ")
                    .append(m.getLevel() == null ? "—" : m.getLevel().toString())
                    .append(" | ")
                    .append(dash(m.getKernelVersion())).append(" | ")
                    .append(dash(m.getSepolicyVersion())).append(" | ")
                    .append(m.getHals().size()).append(" |\n");
        }
        sb.append('\n');
    }

    /** 全 HAL 宣言の一覧 (HAL 名でソート)。 */
    private static void appendHalTable(StringBuilder sb,
                                       List<VintfProjectScanner.Entry> entries) {
        sb.append("## HAL declarations (HAL 宣言)\n\n");
        sb.append("_device manifest は「この HAL を実装・提供する」宣言、"
                + "compatibility matrix は「framework がこの HAL を要求する」宣言。_\n\n");
        sb.append("| HAL | Format | Transport | Versions | Interfaces (instances) |"
                + " Optional | Declared in |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        List<String[]> rows = new ArrayList<>();
        for (VintfProjectScanner.Entry e : entries) {
            for (VintfHal hal : e.getManifest().getHals()) {
                rows.add(new String[] {
                        hal.getName(),
                        dash(emptyToNull(hal.getFormat())),
                        dash(hal.getTransport()),
                        hal.getVersions().isEmpty() ? "—"
                                : String.join(", ", hal.getVersions()),
                        interfacesCell(hal),
                        hal.isOptional() == null ? "—"
                                : (hal.isOptional() ? "optional" : "required"),
                        kindLabel(e.getManifest().getKind()),
                });
            }
        }
        rows.sort(Comparator.comparing(r -> r[0]));
        for (String[] r : rows) {
            sb.append("| `").append(r[0]).append("` | ").append(r[1]).append(" | ")
                    .append(r[2]).append(" | ").append(r[3]).append(" | ")
                    .append(r[4]).append(" | ").append(r[5]).append(" | ")
                    .append(r[6]).append(" |\n");
        }
        sb.append('\n');
    }

    /**
     * compatibility-matrix が要求する HAL のうち、device manifest で宣言されて
     * いないものを列挙する (device manifest と matrix の両方があるときのみ)。
     */
    private static void appendMatrixCheck(StringBuilder sb,
                                          List<VintfProjectScanner.Entry> entries) {
        Set<String> provided = new LinkedHashSet<>();
        Set<String> required = new TreeSet<>();
        boolean hasDevice = false;
        boolean hasMatrix = false;
        for (VintfProjectScanner.Entry e : entries) {
            VintfManifest m = e.getManifest();
            if (m.getKind() == VintfManifest.Kind.DEVICE_MANIFEST) {
                hasDevice = true;
                for (VintfHal hal : m.getHals()) {
                    provided.add(hal.getName());
                }
            } else if (m.getKind() == VintfManifest.Kind.COMPATIBILITY_MATRIX) {
                hasMatrix = true;
                for (VintfHal hal : m.getHals()) {
                    // optional=true の HAL は欠けていても互換性違反ではない
                    if (!Boolean.TRUE.equals(hal.isOptional())) {
                        required.add(hal.getName());
                    }
                }
            }
        }
        if (!hasDevice || !hasMatrix) {
            return;
        }
        sb.append("## Matrix ⇄ Manifest check (要求と宣言の突き合わせ)\n\n");
        List<String> missing = new ArrayList<>();
        for (String name : required) {
            if (!provided.contains(name)) {
                missing.add(name);
            }
        }
        if (missing.isEmpty()) {
            sb.append("_compatibility matrix の必須 HAL はすべて device manifest で"
                    + "宣言されています。_\n");
            return;
        }
        sb.append("_matrix で必須 (optional=false) なのに device manifest に宣言が"
                + "無い HAL:_\n\n");
        for (String name : missing) {
            sb.append("- `").append(name).append("`\n");
        }
    }

    /** interface 名と instance 一覧をセル文字列に整形する。 */
    private static String interfacesCell(VintfHal hal) {
        if (hal.getInterfaces().isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (VintfInterface vi : hal.getInterfaces()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append('`').append(vi.getName()).append('`');
            if (!vi.getInstances().isEmpty()) {
                sb.append(" (").append(String.join(", ", vi.getInstances())).append(')');
            }
        }
        return sb.toString();
    }

    /** 種別の表示ラベル。 */
    static String kindLabel(VintfManifest.Kind kind) {
        switch (kind) {
            case DEVICE_MANIFEST:
                return "device manifest";
            case FRAMEWORK_MANIFEST:
                return "framework manifest";
            case COMPATIBILITY_MATRIX:
                return "compatibility matrix";
            default:
                return "unknown";
        }
    }

    private static String dash(String v) {
        return v == null || v.isEmpty() ? "—" : v;
    }

    private static String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }
}
