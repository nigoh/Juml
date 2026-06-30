// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.insights;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * アーキテクチャ俯瞰 ({@code --insights}) の解析結果。
 *
 * <p>{@link InsightsAnalyzer} が生成し、{@link MarkdownInsightsReport} /
 * {@link PlantUmlPackageCycleDiagram} が消費する。
 * {@link juml.core.impact.ImpactGraph} と同様に CLI / GUI のどちらからでも
 * 使える純粋なデータホルダとして設計する。</p>
 */
public final class InsightsModel {

    /** エントリポイント種別。 */
    public enum EntryPointKind {
        MAIN,
        APPLICATION,
        ACTIVITY,
        SERVICE,
        RECEIVER,
        PROVIDER,
        FRAGMENT
    }

    /** プログラムの入口となるシンボル (main / Android コンポーネント)。 */
    public static final class EntryPoint {
        private final EntryPointKind kind;
        private final String fqn;
        private final String detail;

        public EntryPoint(EntryPointKind kind, String fqn, String detail) {
            this.kind = kind;
            this.fqn = fqn;
            this.detail = detail == null ? "" : detail;
        }

        public EntryPointKind getKind() { return kind; }
        public String getFqn() { return fqn; }
        public String getDetail() { return detail; }
    }

    /** 参照集中度の高いクラス (fan-in / fan-out)。 */
    public static final class Hotspot {
        private final String fqn;
        private final int fanIn;
        private final int fanOut;
        private final List<String> topReferrers;

        public Hotspot(String fqn, int fanIn, int fanOut, List<String> topReferrers) {
            this.fqn = fqn;
            this.fanIn = fanIn;
            this.fanOut = fanOut;
            this.topReferrers = topReferrers == null
                    ? Collections.emptyList() : new ArrayList<>(topReferrers);
        }

        public String getFqn() { return fqn; }
        public int getFanIn() { return fanIn; }
        public int getFanOut() { return fanOut; }
        public List<String> getTopReferrers() {
            return Collections.unmodifiableList(topReferrers);
        }
    }

    /** パッケージ間依存のエッジ (from → to)。 */
    public static final class PackageEdge {
        private final String from;
        private final String to;

        public PackageEdge(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
    }

    /** 強連結成分 (SCC) として検出されたパッケージ循環。 */
    public static final class PackageCycle {
        private final Set<String> packages;
        private final List<PackageEdge> edges;

        public PackageCycle(Set<String> packages, List<PackageEdge> edges) {
            this.packages = packages == null
                    ? Collections.emptySet() : new LinkedHashSet<>(packages);
            this.edges = edges == null
                    ? Collections.emptyList() : new ArrayList<>(edges);
        }

        public Set<String> getPackages() {
            return Collections.unmodifiableSet(packages);
        }

        /** SCC 内部のエッジ (循環を構成する依存)。 */
        public List<PackageEdge> getEdges() {
            return Collections.unmodifiableList(edges);
        }
    }

    /** 静的解析で参照が見つからなかったシンボル (デッドコード候補)。 */
    public static final class DeadCodeCandidate {
        /** シンボル種別。 */
        public enum Kind { CLASS, METHOD }

        private final Kind kind;
        private final String symbol;
        private final String reason;
        private final String confidence;

        public DeadCodeCandidate(Kind kind, String symbol,
                                 String reason, String confidence) {
            this.kind = kind;
            this.symbol = symbol;
            this.reason = reason == null ? "" : reason;
            this.confidence = confidence == null ? "" : confidence;
        }

        public Kind getKind() { return kind; }
        public String getSymbol() { return symbol; }
        public String getReason() { return reason; }
        public String getConfidence() { return confidence; }
    }

    private final List<EntryPoint> entryPoints = new ArrayList<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<PackageCycle> packageCycles = new ArrayList<>();
    private final List<DeadCodeCandidate> deadCodeCandidates = new ArrayList<>();
    /** パッケージ → 推定レイヤ名 (辞書ヒューリスティックによる best-effort)。 */
    private final Map<String, String> layerByPackage = new LinkedHashMap<>();
    /** プロジェクト内のパッケージ間依存エッジ (重複なし、登録順)。 */
    private final List<PackageEdge> packageEdges = new ArrayList<>();
    /** パッケージ → 所属クラス数。 */
    private final Map<String, Integer> classCountByPackage = new LinkedHashMap<>();
    private int classCount;
    private int referenceCount;

    public void addEntryPoint(EntryPoint e) {
        if (e != null) {
            entryPoints.add(e);
        }
    }

    public void addHotspot(Hotspot h) {
        if (h != null) {
            hotspots.add(h);
        }
    }

    public void addPackageCycle(PackageCycle c) {
        if (c != null) {
            packageCycles.add(c);
        }
    }

    public void addDeadCodeCandidate(DeadCodeCandidate d) {
        if (d != null) {
            deadCodeCandidates.add(d);
        }
    }

    public void addPackageEdge(PackageEdge e) {
        if (e != null) {
            packageEdges.add(e);
        }
    }

    public void putLayer(String pkg, String layer) {
        if (pkg != null && layer != null) {
            layerByPackage.put(pkg, layer);
        }
    }

    public void putClassCount(String pkg, int count) {
        if (pkg != null) {
            classCountByPackage.put(pkg, count);
        }
    }

    public void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public void setReferenceCount(int referenceCount) {
        this.referenceCount = referenceCount;
    }

    public List<EntryPoint> getEntryPoints() {
        return Collections.unmodifiableList(entryPoints);
    }

    public List<Hotspot> getHotspots() {
        return Collections.unmodifiableList(hotspots);
    }

    public List<PackageCycle> getPackageCycles() {
        return Collections.unmodifiableList(packageCycles);
    }

    public List<DeadCodeCandidate> getDeadCodeCandidates() {
        return Collections.unmodifiableList(deadCodeCandidates);
    }

    public Map<String, String> getLayerByPackage() {
        return Collections.unmodifiableMap(layerByPackage);
    }

    public List<PackageEdge> getPackageEdges() {
        return Collections.unmodifiableList(packageEdges);
    }

    public Map<String, Integer> getClassCountByPackage() {
        return Collections.unmodifiableMap(classCountByPackage);
    }

    public int getClassCount() {
        return classCount;
    }

    public int getReferenceCount() {
        return referenceCount;
    }
}
