// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link VintfProjectScanner.Entry} のリストを PlantUML の HAL 宣言図に整形する。
 *
 * <p>manifest 種別 (framework manifest / compatibility matrix / device manifest) を
 * package 枠にし、HAL 1 宣言を 1 コンポーネントとして描く。ラベルには format /
 * transport / versions と interface/instance を含める。compatibility matrix の
 * 要求 HAL と同名の HAL が device manifest に宣言されていれば「満たしている」
 * 緑破線矢印で接続し、必須なのに宣言が無い HAL は赤背景で強調する。</p>
 */
public final class PlantUmlVintfDiagram {

    /** 必須なのに device manifest に宣言が無い HAL の強調色 (赤系)。 */
    static final String MISSING_FILL = "#F4A6A6";

    private PlantUmlVintfDiagram() {
    }

    public static String render(List<VintfProjectScanner.Entry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title VINTF HAL Declarations\n");
        sb.append("skinparam componentStyle rectangle\n");
        sb.append("skinparam shadowing false\n");
        if (entries == null || entries.isEmpty()) {
            sb.append("note as N1\n(no VINTF manifests found)\nend note\n");
            sb.append("@enduml\n");
            return sb.toString();
        }

        // 種別 → (HAL 名 → alias)。同名 HAL の重複宣言は 1 ノードへ集約する。
        Map<VintfManifest.Kind, Map<String, String>> aliases = new LinkedHashMap<>();
        Set<String> provided = providedHalNames(entries);
        boolean hasDevice = !provided.isEmpty() || hasKind(entries,
                VintfManifest.Kind.DEVICE_MANIFEST);

        VintfManifest.Kind[] order = {
                VintfManifest.Kind.FRAMEWORK_MANIFEST,
                VintfManifest.Kind.COMPATIBILITY_MATRIX,
                VintfManifest.Kind.DEVICE_MANIFEST,
        };
        for (VintfManifest.Kind kind : order) {
            if (!hasKind(entries, kind)) {
                continue;
            }
            sb.append("package \"").append(MarkdownVintfReport.kindLabel(kind))
                    .append("\" {\n");
            Map<String, String> byName =
                    aliases.computeIfAbsent(kind, k -> new LinkedHashMap<>());
            for (VintfProjectScanner.Entry e : entries) {
                if (e.getManifest().getKind() != kind) {
                    continue;
                }
                for (VintfHal hal : e.getManifest().getHals()) {
                    if (byName.containsKey(hal.getName())) {
                        continue;
                    }
                    String alias = alias(kind, hal.getName());
                    byName.put(hal.getName(), alias);
                    boolean missing = kind == VintfManifest.Kind.COMPATIBILITY_MATRIX
                            && hasDevice
                            && !Boolean.TRUE.equals(hal.isOptional())
                            && !provided.contains(hal.getName());
                    sb.append("  component \"").append(label(hal)).append("\" as ")
                            .append(alias).append(' ')
                            .append(missing ? MISSING_FILL : colorFor(hal.getFormat()))
                            .append('\n');
                }
            }
            sb.append("}\n");
        }

        emitSatisfiedEdges(sb, aliases);
        appendLegend(sb, hasDevice);
        sb.append("@enduml\n");
        return sb.toString();
    }

    /** matrix の要求 HAL と同名の device manifest 宣言を緑破線で結ぶ。 */
    private static void emitSatisfiedEdges(StringBuilder sb,
            Map<VintfManifest.Kind, Map<String, String>> aliases) {
        Map<String, String> matrix = aliases.getOrDefault(
                VintfManifest.Kind.COMPATIBILITY_MATRIX, Map.of());
        Map<String, String> device = aliases.getOrDefault(
                VintfManifest.Kind.DEVICE_MANIFEST, Map.of());
        for (Map.Entry<String, String> e : matrix.entrySet()) {
            String deviceAlias = device.get(e.getKey());
            if (deviceAlias != null) {
                sb.append(e.getValue()).append(" .[#2da44e].> ")
                        .append(deviceAlias).append(" : declared\n");
            }
        }
    }

    /** device manifest で宣言されている HAL 名の集合。 */
    private static Set<String> providedHalNames(List<VintfProjectScanner.Entry> entries) {
        Set<String> provided = new LinkedHashSet<>();
        for (VintfProjectScanner.Entry e : entries) {
            if (e.getManifest().getKind() == VintfManifest.Kind.DEVICE_MANIFEST) {
                for (VintfHal hal : e.getManifest().getHals()) {
                    provided.add(hal.getName());
                }
            }
        }
        return provided;
    }

    private static boolean hasKind(List<VintfProjectScanner.Entry> entries,
                                   VintfManifest.Kind kind) {
        for (VintfProjectScanner.Entry e : entries) {
            if (e.getManifest().getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    /** HAL 1 宣言分のコンポーネントラベル (name / format / versions / interfaces)。 */
    private static String label(VintfHal hal) {
        StringBuilder sb = new StringBuilder(escape(hal.getName()));
        StringBuilder stereo = new StringBuilder();
        if (!hal.getFormat().isEmpty()) {
            stereo.append(hal.getFormat());
        }
        if (hal.getTransport() != null && !hal.getTransport().isEmpty()) {
            if (stereo.length() > 0) {
                stereo.append(' ');
            }
            stereo.append(hal.getTransport());
        }
        if (!hal.getVersions().isEmpty()) {
            if (stereo.length() > 0) {
                stereo.append(' ');
            }
            stereo.append(String.join(",", hal.getVersions()));
        }
        if (stereo.length() > 0) {
            sb.append("\\n<<").append(escape(stereo.toString())).append(">>");
        }
        for (VintfInterface vi : hal.getInterfaces()) {
            sb.append("\\n").append(escape(vi.getName()));
            if (!vi.getInstances().isEmpty()) {
                sb.append('/').append(escape(String.join(",", vi.getInstances())));
            }
        }
        return sb.toString();
    }

    /** 凡例: package / 色 / 矢印の意味。 */
    private static void appendLegend(StringBuilder sb, boolean hasDevice) {
        sb.append("legend right\n");
        sb.append("package = manifest 種別 (framework / compatibility matrix / device)\n");
        sb.append("component = HAL 宣言 (<<format transport versions>> と interface/instance)\n");
        sb.append("色: aidl=桃 / hidl=赤系 / native ほか=灰\n");
        sb.append("緑 破線矢印 = matrix の要求 HAL を device manifest が宣言 (満たしている)\n");
        if (hasDevice) {
            sb.append("赤背景 = matrix で必須 (optional=false) なのに宣言が無い HAL\n");
        }
        sb.append("endlegend\n");
    }

    /** HAL format 別のコンポーネント色 (Soong 図のカテゴリ色と揃える)。 */
    private static String colorFor(String format) {
        switch (format) {
            case "aidl":
                return "#FFD5E8";
            case "hidl":
                return "#FFE0E0";
            default:
                return "#EEEEEE";
        }
    }

    /** 種別 + HAL 名から一意な PlantUML alias を作る。 */
    private static String alias(VintfManifest.Kind kind, String name) {
        String prefix;
        switch (kind) {
            case DEVICE_MANIFEST:
                prefix = "v_dev_";
                break;
            case FRAMEWORK_MANIFEST:
                prefix = "v_fwk_";
                break;
            case COMPATIBILITY_MATRIX:
                prefix = "v_mtx_";
                break;
            default:
                prefix = "v_unk_";
                break;
        }
        StringBuilder sb = new StringBuilder(prefix);
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
