// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code .intermediates} ツリー走査の集計結果。モジュール ({@link IntermediateModule}) の
 * 一覧と全体サマリー (総ファイル数 / 総バイト数 / 走査打ち切りフラグ) を保持する。
 */
public final class IntermediatesInventory {

    /** "modulePath/name" → モジュール。重複キーで集約するため map で保持。 */
    private final Map<String, IntermediateModule> modules = new LinkedHashMap<>();
    private long totalFiles;
    private long totalBytes;
    private boolean truncated;

    public List<IntermediateModule> getModules() {
        return new ArrayList<>(modules.values());
    }

    public long getTotalFiles() { return totalFiles; }
    public long getTotalBytes() { return totalBytes; }
    public boolean isTruncated() { return truncated; }

    IntermediateModule moduleFor(String modulePath, String name) {
        String key = modulePath + "/" + name;
        return modules.computeIfAbsent(key, k -> new IntermediateModule(name, modulePath));
    }

    void addFile(long bytes) {
        totalFiles++;
        totalBytes += Math.max(0, bytes);
    }

    void markTruncated() { this.truncated = true; }
}
