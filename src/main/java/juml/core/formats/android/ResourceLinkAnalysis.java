// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ResourceLinkAnalyzer} の解析結果。コードからリソースへの参照
 * ({@link ResourceReference}) と、レイアウト XML が参照する文字列リソース、
 * そして文言解決用の {@link AndroidProjectAnalysis} を束ねる。
 *
 * <p>{@link PlantUmlResourceLinkDiagram} がこの情報から
 * 「クラス ── レイアウト ── 文字列リソース」の紐づけ図を構築する。</p>
 */
public final class ResourceLinkAnalysis {

    private final List<ResourceReference> references = new ArrayList<>();
    /** layout 名 → その XML 内で参照される {@code @string/} 名の集合。 */
    private final Map<String, Set<String>> layoutStringRefs = new LinkedHashMap<>();
    private AndroidProjectAnalysis analysis;

    public List<ResourceReference> getReferences() {
        return references;
    }

    public Map<String, Set<String>> getLayoutStringRefs() {
        return layoutStringRefs;
    }

    public void addLayoutStringRef(String layoutName, String stringName) {
        if (layoutName == null || stringName == null) {
            return;
        }
        layoutStringRefs.computeIfAbsent(layoutName, k -> new LinkedHashSet<>()).add(stringName);
    }

    public AndroidProjectAnalysis getAnalysis() {
        return analysis;
    }

    public void setAnalysis(AndroidProjectAnalysis analysis) {
        this.analysis = analysis;
    }

    /** 解析対象が皆無 (コード参照もレイアウト参照も無し) か。 */
    public boolean isEmpty() {
        return references.isEmpty() && layoutStringRefs.isEmpty();
    }

    /** 文字列名 → 実文言を解決する (見つからなければ null)。 */
    public String resolveString(String name) {
        return analysis != null ? analysis.resolveString(name) : null;
    }
}
