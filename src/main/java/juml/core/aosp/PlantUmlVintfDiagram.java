// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.LinkedHashMap;
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

        // 種別 → (HAL 名 → マージ済み HAL)。同名 HAL の複数宣言 (fragment 分割や
        // IVibrator / IVibratorManager の並記) は捨てずに 1 ノードへ統合する —
        // 先勝ちで捨てると 2 件目以降の interface / version が図から消える。
        Map<VintfManifest.Kind, Map<String, VintfHal>> merged = mergeByName(entries);
        // matrix の要求 HAL 名 → matrix type ("device" なら framework への要求)
        Map<String, String> matrixTypeOf = matrixTypeByName(entries);
        Set<String> providedByDevice = merged.getOrDefault(
                VintfManifest.Kind.DEVICE_MANIFEST, Map.of()).keySet();
        Set<String> providedByFramework = merged.getOrDefault(
                VintfManifest.Kind.FRAMEWORK_MANIFEST, Map.of()).keySet();
        boolean hasDevice = hasKind(entries, VintfManifest.Kind.DEVICE_MANIFEST);
        boolean hasFramework = hasKind(entries, VintfManifest.Kind.FRAMEWORK_MANIFEST);

        Map<VintfManifest.Kind, Map<String, String>> aliases = new LinkedHashMap<>();
        VintfManifest.Kind[] order = {
                VintfManifest.Kind.FRAMEWORK_MANIFEST,
                VintfManifest.Kind.COMPATIBILITY_MATRIX,
                VintfManifest.Kind.DEVICE_MANIFEST,
        };
        for (VintfManifest.Kind kind : order) {
            Map<String, VintfHal> byName = merged.get(kind);
            if (byName == null || !hasKind(entries, kind)) {
                continue;
            }
            sb.append("package \"").append(MarkdownVintfReport.kindLabel(kind))
                    .append("\" {\n");
            Map<String, String> aliasMap =
                    aliases.computeIfAbsent(kind, k -> new LinkedHashMap<>());
            for (Map.Entry<String, VintfHal> me : byName.entrySet()) {
                VintfHal hal = me.getValue();
                String alias = alias(kind, hal.getName());
                aliasMap.put(hal.getName(), alias);
                boolean missing = false;
                if (kind == VintfManifest.Kind.COMPATIBILITY_MATRIX
                        && !Boolean.TRUE.equals(hal.isOptional())) {
                    // device matrix は framework manifest への要求、それ以外
                    // (framework matrix / type 無し) は device manifest への要求。
                    boolean deviceMatrix =
                            "device".equals(matrixTypeOf.get(hal.getName()));
                    missing = deviceMatrix
                            ? hasFramework && !providedByFramework.contains(hal.getName())
                            : hasDevice && !providedByDevice.contains(hal.getName());
                }
                sb.append("  component \"").append(label(hal)).append("\" as ")
                        .append(alias).append(' ')
                        .append(missing ? MISSING_FILL : colorFor(hal.getFormat()))
                        .append('\n');
            }
            sb.append("}\n");
        }

        emitSatisfiedEdges(sb, aliases, matrixTypeOf);
        appendLegend(sb, hasDevice || hasFramework);
        sb.append("@enduml\n");
        return sb.toString();
    }

    /** matrix の要求 HAL と同名の manifest 宣言 (要求の向きに応じた側) を緑破線で結ぶ。 */
    private static void emitSatisfiedEdges(StringBuilder sb,
            Map<VintfManifest.Kind, Map<String, String>> aliases,
            Map<String, String> matrixTypeOf) {
        Map<String, String> matrix = aliases.getOrDefault(
                VintfManifest.Kind.COMPATIBILITY_MATRIX, Map.of());
        Map<String, String> device = aliases.getOrDefault(
                VintfManifest.Kind.DEVICE_MANIFEST, Map.of());
        Map<String, String> framework = aliases.getOrDefault(
                VintfManifest.Kind.FRAMEWORK_MANIFEST, Map.of());
        for (Map.Entry<String, String> e : matrix.entrySet()) {
            boolean deviceMatrix = "device".equals(matrixTypeOf.get(e.getKey()));
            String providerAlias = deviceMatrix
                    ? framework.get(e.getKey()) : device.get(e.getKey());
            if (providerAlias != null) {
                sb.append(e.getValue()).append(" .[#2da44e].> ")
                        .append(providerAlias).append(" : declared\n");
            }
        }
    }

    /** 種別ごとに同名 HAL をマージした (HAL 名 → 統合 HAL) を作る。 */
    private static Map<VintfManifest.Kind, Map<String, VintfHal>> mergeByName(
            List<VintfProjectScanner.Entry> entries) {
        Map<VintfManifest.Kind, Map<String, VintfHal>> merged = new LinkedHashMap<>();
        for (VintfProjectScanner.Entry e : entries) {
            VintfManifest.Kind kind = e.getManifest().getKind();
            Map<String, VintfHal> byName =
                    merged.computeIfAbsent(kind, k -> new LinkedHashMap<>());
            for (VintfHal hal : e.getManifest().getHals()) {
                VintfHal into = byName.get(hal.getName());
                if (into == null) {
                    byName.put(hal.getName(), copyOf(hal));
                } else {
                    mergeInto(into, hal);
                }
            }
        }
        return merged;
    }

    private static VintfHal copyOf(VintfHal hal) {
        VintfHal copy = new VintfHal(hal.getFormat(), hal.getName());
        copy.setTransport(hal.getTransport());
        copy.setOptional(hal.isOptional());
        mergeInto(copy, hal);
        return copy;
    }

    /** interface / instance / version を集合的に統合する。optional は required 優先。 */
    private static void mergeInto(VintfHal into, VintfHal from) {
        for (String v : from.getVersions()) {
            if (!into.getVersions().contains(v)) {
                into.getVersions().add(v);
            }
        }
        for (VintfInterface vi : from.getInterfaces()) {
            VintfInterface target = null;
            for (VintfInterface existing : into.getInterfaces()) {
                if (existing.getName().equals(vi.getName())) {
                    target = existing;
                    break;
                }
            }
            if (target == null) {
                target = new VintfInterface(vi.getName());
                into.getInterfaces().add(target);
            }
            for (String inst : vi.getInstances()) {
                if (!target.getInstances().contains(inst)) {
                    target.getInstances().add(inst);
                }
            }
        }
        if (into.getTransport() == null) {
            into.setTransport(from.getTransport());
        }
        // 1 件でも required (optional=false) があれば required として扱う。
        if (Boolean.FALSE.equals(from.isOptional())) {
            into.setOptional(Boolean.FALSE);
        } else if (into.isOptional() == null) {
            into.setOptional(from.isOptional());
        }
    }

    /** matrix 内の HAL 名 → その matrix の type ("device" / "framework" / "")。 */
    private static Map<String, String> matrixTypeByName(
            List<VintfProjectScanner.Entry> entries) {
        Map<String, String> types = new LinkedHashMap<>();
        for (VintfProjectScanner.Entry e : entries) {
            VintfManifest m = e.getManifest();
            if (m.getKind() != VintfManifest.Kind.COMPATIBILITY_MATRIX) {
                continue;
            }
            String type = m.getMatrixType() == null ? "" : m.getMatrixType();
            for (VintfHal hal : m.getHals()) {
                types.putIfAbsent(hal.getName(), type);
            }
        }
        return types;
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
        // クォート内ラベルの規則は escapeQuotedLabel に 1 本化されている
        // (\" は PlantUML 1.2026.x ではエスケープにならず、< は creole 開始になる)。
        return juml.core.formats.uml.PlantUmlCommentFormatter.escapeQuotedLabel(s);
    }
}
