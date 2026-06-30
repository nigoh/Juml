// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * {@code out/soong/.intermediates/<module-path>/<module-name>/<variant>/...} から
 * 復元した 1 モジュール分の中間生成物サマリー。
 *
 * <p>variant (例 {@code android_arm64_armv8-a_shared} / {@code android_common}) は
 * 複数あり得るため集合で保持し、成果物は拡張子由来の「種別」({@code .o/.so/.jar/.apk} 等)
 * ごとに件数を数える。</p>
 */
public final class IntermediateModule {

    private final String name;
    private final String modulePath;
    private final TreeSet<String> variants = new TreeSet<>();
    /** 成果物種別 (拡張子) → 件数。 */
    private final Map<String, Integer> kindCounts = new LinkedHashMap<>();
    private long totalFiles;
    private long totalBytes;

    public IntermediateModule(String name, String modulePath) {
        this.name = name == null ? "" : name;
        this.modulePath = modulePath == null ? "" : modulePath;
    }

    public String getName() { return name; }
    public String getModulePath() { return modulePath; }
    public TreeSet<String> getVariants() { return variants; }
    public Map<String, Integer> getKindCounts() { return kindCounts; }
    public long getTotalFiles() { return totalFiles; }
    public long getTotalBytes() { return totalBytes; }

    void addVariant(String variant) {
        if (variant != null && !variant.isEmpty()) {
            variants.add(variant);
        }
    }

    void addArtifact(String kind, long bytes) {
        kindCounts.merge(kind, 1, Integer::sum);
        totalFiles++;
        totalBytes += Math.max(0, bytes);
    }

    /** 成果物種別の優勢から大分類する: apk / java / native / aidl / other。 */
    public String getCategory() {
        if (kindCounts.containsKey("apk")) return "apk";
        if (kindCounts.containsKey("jar") || kindCounts.containsKey("dex")
                || kindCounts.containsKey("class")) {
            return "java";
        }
        if (kindCounts.containsKey("so") || kindCounts.containsKey("o")
                || kindCounts.containsKey("a")) {
            return "native";
        }
        if (kindCounts.containsKey("aidl") || kindCounts.containsKey("hidl")) {
            return "aidl";
        }
        return "other";
    }

    @Override
    public String toString() {
        return name + " [" + variants.size() + " variant(s), "
                + totalFiles + " file(s)]";
    }
}
