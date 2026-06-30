// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.insights;

import juml.core.formats.uml.AndroidSuperclassDetector;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.DependencyJarIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceIndexBuilder;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;
import juml.util.ErrorListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * コードリーディングの足がかりとなる「アーキテクチャ俯瞰」を解析する。
 *
 * <p>{@link ReferenceIndex} (逆参照) と {@link ClassIndex} を入力に、
 * 以下を 1 回の走査ベースで集計して {@link InsightsModel} にまとめる:</p>
 * <ul>
 *   <li>エントリポイント: {@code public static main} / Android コンポーネント</li>
 *   <li>ホットスポット: fan-in (参照元クラス数) / fan-out (参照先クラス数) 上位</li>
 *   <li>パッケージ循環依存: Tarjan SCC によるサイズ 2 以上の強連結成分</li>
 *   <li>デッドコード候補: 参照ゼロの public シンボル (ヒューリスティック除外つき)</li>
 *   <li>推定レイヤ: パッケージ名セグメント辞書による best-effort 分類</li>
 * </ul>
 *
 * <p>CLI に依存しない (GUI からも利用可能)。</p>
 */
public final class InsightsAnalyzer {

    /** ホットスポットの上位件数。 */
    public static final int MAX_HOTSPOTS = 20;
    /** ホットスポット 1 件あたりに併記する主な参照元の数。 */
    private static final int MAX_TOP_REFERRERS = 3;

    /** パッケージ末尾セグメント → 推定レイヤ名 の辞書。 */
    private static final Map<String, String> LAYER_DICTIONARY;

    static {
        Map<String, String> m = new HashMap<>();
        for (String s : new String[] {"ui", "view", "views", "widget", "widgets",
                "fragment", "fragments", "activity", "activities", "compose",
                "screen", "screens", "layout"}) {
            m.put(s, "Presentation");
        }
        for (String s : new String[] {"viewmodel", "viewmodels", "presenter",
                "presenters", "controller", "controllers"}) {
            m.put(s, "Presentation Logic");
        }
        for (String s : new String[] {"usecase", "usecases", "domain", "model",
                "models", "service", "services", "logic"}) {
            m.put(s, "Domain");
        }
        for (String s : new String[] {"repository", "repositories", "data", "db",
                "dao", "database", "net", "network", "api", "remote", "local",
                "datasource", "storage"}) {
            m.put(s, "Data");
        }
        for (String s : new String[] {"util", "utils", "common", "core", "base",
                "shared", "helper", "helpers", "internal"}) {
            m.put(s, "Shared");
        }
        LAYER_DICTIONARY = m;
    }

    private InsightsAnalyzer() {
    }

    /**
     * {@link ReferenceIndex} を内部で構築してから解析する便宜メソッド。
     * 構築済みインデックスを持たない呼び出し元 (GUI の図種ディスパッチ等) 向け。
     */
    public static InsightsModel analyzeBuildingIndex(List<JavaClassInfo> classes,
                                                     ClassIndex index,
                                                     DependencyJarIndex depIndex) {
        ReferenceIndex refs = new ReferenceIndex();
        new ReferenceIndexBuilder(refs, index, depIndex, ErrorListener.silent())
                .addAll(classes);
        return analyze(classes, index, refs);
    }

    /**
     * 解析を実行して {@link InsightsModel} を返す。
     *
     * @param classes Stage B 化されたプロジェクト内クラス一覧
     * @param index   クラスインデックス (Android コンポーネント判定に使用)
     * @param refs    逆参照インデックス (fan-in / 循環 / デッドコード判定に使用)
     */
    public static InsightsModel analyze(List<JavaClassInfo> classes,
                                        ClassIndex index, ReferenceIndex refs) {
        if (classes == null || refs == null) {
            throw new IllegalArgumentException("classes/refs is null");
        }
        InsightsModel model = new InsightsModel();

        // プロジェクト内クラス (ソース由来のみ。module-info は除外)
        Map<String, JavaClassInfo> project = new LinkedHashMap<>();
        for (JavaClassInfo c : classes) {
            if (c.getOrigin() != JavaClassInfo.Origin.SOURCE
                    || c.getKind() == JavaClassInfo.Kind.MODULE) {
                continue;
            }
            project.put(c.getQualifiedName(), c);
        }
        model.setClassCount(project.size());
        model.setReferenceCount(refs.totalSites());

        Map<String, Integer> pkgCount = new TreeMap<>();
        for (JavaClassInfo c : project.values()) {
            pkgCount.merge(packageOf(c), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : pkgCount.entrySet()) {
            model.putClassCount(e.getKey(), e.getValue());
        }

        Map<String, InsightsModel.EntryPoint> entryPoints =
                collectEntryPoints(project, index);
        for (InsightsModel.EntryPoint e : entryPoints.values()) {
            model.addEntryPoint(e);
        }

        // ReferenceIndex の 1 パス走査で fan-in・被参照集合・パッケージ依存エッジを集計
        RefAggregation agg = aggregateReferences(refs, project);
        for (InsightsModel.PackageEdge e : agg.packageEdges) {
            model.addPackageEdge(e);
        }

        collectHotspots(model, project, agg);
        detectPackageCycles(model, agg.packageEdges);
        collectDeadCodeCandidates(model, project, refs, agg, entryPoints.keySet());
        estimateLayers(model, pkgCount.keySet());
        return model;
    }

    // ----------------------------------------------------------------
    // エントリポイント
    // ----------------------------------------------------------------

    private static Map<String, InsightsModel.EntryPoint> collectEntryPoints(
            Map<String, JavaClassInfo> project, ClassIndex index) {
        Map<String, InsightsModel.EntryPoint> out = new LinkedHashMap<>();
        // 1. Manifest 由来 (androidComponentType) を優先
        for (JavaClassInfo c : project.values()) {
            InsightsModel.EntryPointKind kind =
                    manifestKind(c.getAndroidComponentType());
            if (kind != null) {
                out.put(c.getQualifiedName(), new InsightsModel.EntryPoint(
                        kind, c.getQualifiedName(), "declared in AndroidManifest.xml"));
            }
        }
        // 2. 継承チェーン由来 (Fragment など Manifest に出ないものを補完)
        if (index != null) {
            for (Map.Entry<String, AndroidSuperclassDetector.ComponentKind> e
                    : AndroidSuperclassDetector.detect(index).entrySet()) {
                if (!project.containsKey(e.getKey()) || out.containsKey(e.getKey())) {
                    continue;
                }
                out.put(e.getKey(), new InsightsModel.EntryPoint(
                        InsightsModel.EntryPointKind.valueOf(e.getValue().name()),
                        e.getKey(), "detected by superclass chain"));
            }
        }
        // 3. public static void main
        for (JavaClassInfo c : project.values()) {
            if (out.containsKey(c.getQualifiedName())) {
                continue;
            }
            for (JavaMethodInfo m : c.getMethods()) {
                if (isMainMethod(m)) {
                    out.put(c.getQualifiedName(), new InsightsModel.EntryPoint(
                            InsightsModel.EntryPointKind.MAIN,
                            c.getQualifiedName(), "public static main"));
                    break;
                }
            }
        }
        return out;
    }

    private static InsightsModel.EntryPointKind manifestKind(String componentType) {
        if (componentType == null || componentType.isEmpty()) {
            return null;
        }
        switch (componentType) {
            case "Activity":
                return InsightsModel.EntryPointKind.ACTIVITY;
            case "Service":
                return InsightsModel.EntryPointKind.SERVICE;
            case "BroadcastReceiver":
                return InsightsModel.EntryPointKind.RECEIVER;
            case "ContentProvider":
                return InsightsModel.EntryPointKind.PROVIDER;
            case "Application":
                return InsightsModel.EntryPointKind.APPLICATION;
            default:
                return null;
        }
    }

    private static boolean isMainMethod(JavaMethodInfo m) {
        return "main".equals(m.getName())
                && m.isStatic()
                && m.getVisibility() == Visibility.PUBLIC;
    }

    // ----------------------------------------------------------------
    // ReferenceIndex の 1 パス集計
    // ----------------------------------------------------------------

    /** {@link ReferenceIndex} 走査の集計結果。 */
    private static final class RefAggregation {
        /** owner FQN → 参照してくる distinct クラス集合 (fan-in)。 */
        final Map<String, Set<String>> fanInBy = new HashMap<>();
        /** owner FQN → (参照元 FQN → 参照回数)。主な参照元の表示用。 */
        final Map<String, Map<String, Integer>> referrerCount = new HashMap<>();
        /** 自分以外から 1 回でも参照されたクラス集合 (デッドクラス判定用)。 */
        final Set<String> referencedClasses = new HashSet<>();
        /** プロジェクト内パッケージ依存エッジ (重複なし、登録順)。 */
        final List<InsightsModel.PackageEdge> packageEdges = new ArrayList<>();
    }

    private static RefAggregation aggregateReferences(
            ReferenceIndex refs, Map<String, JavaClassInfo> project) {
        RefAggregation agg = new RefAggregation();
        Set<String> emittedEdges = new LinkedHashSet<>();
        for (ReferenceKey key : refs.keys()) {
            String owner = key.getOwnerFqn();
            JavaClassInfo ownerClass = project.get(owner);
            if (ownerClass == null) {
                continue;
            }
            String ownerPkg = packageOf(ownerClass);
            for (ReferenceSite site : refs.sites(key)) {
                String caller = site.getCallerFqn();
                if (caller.isEmpty() || caller.equals(owner)) {
                    continue;
                }
                agg.referencedClasses.add(owner);
                JavaClassInfo callerClass = project.get(caller);
                if (callerClass == null) {
                    continue;
                }
                agg.fanInBy.computeIfAbsent(owner, k -> new HashSet<>()).add(caller);
                agg.referrerCount.computeIfAbsent(owner, k -> new HashMap<>())
                        .merge(caller, 1, Integer::sum);
                String callerPkg = packageOf(callerClass);
                if (!callerPkg.equals(ownerPkg)
                        && emittedEdges.add(callerPkg + " -> " + ownerPkg)) {
                    agg.packageEdges.add(
                            new InsightsModel.PackageEdge(callerPkg, ownerPkg));
                }
            }
        }
        return agg;
    }

    // ----------------------------------------------------------------
    // ホットスポット
    // ----------------------------------------------------------------

    private static void collectHotspots(InsightsModel model,
                                        Map<String, JavaClassInfo> project,
                                        RefAggregation agg) {
        List<InsightsModel.Hotspot> all = new ArrayList<>();
        for (JavaClassInfo c : project.values()) {
            String qn = c.getQualifiedName();
            int fanIn = agg.fanInBy.getOrDefault(qn, java.util.Collections.emptySet())
                    .size();
            int fanOut = fanOutOf(c, project);
            if (fanIn == 0 && fanOut == 0) {
                continue;
            }
            all.add(new InsightsModel.Hotspot(qn, fanIn, fanOut,
                    topReferrers(agg.referrerCount.get(qn))));
        }
        all.sort(Comparator
                .comparingInt(InsightsModel.Hotspot::getFanIn).reversed()
                .thenComparing(Comparator
                        .comparingInt(InsightsModel.Hotspot::getFanOut).reversed())
                .thenComparing(InsightsModel.Hotspot::getFqn));
        for (int i = 0; i < all.size() && i < MAX_HOTSPOTS; i++) {
            model.addHotspot(all.get(i));
        }
    }

    /** クラスが参照しているプロジェクト内の distinct クラス数 (解決済み呼び出しベース)。 */
    private static int fanOutOf(JavaClassInfo c, Map<String, JavaClassInfo> project) {
        Set<String> out = new HashSet<>();
        for (JavaMethodInfo m : c.getMethods()) {
            for (JavaMethodInfo.Call call : m.getCalls()) {
                String fqn = call.getResolvedOwnerFqn();
                if (fqn != null && !fqn.isEmpty()
                        && !fqn.equals(c.getQualifiedName())
                        && project.containsKey(fqn)) {
                    out.add(fqn);
                }
            }
        }
        return out.size();
    }

    private static List<String> topReferrers(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < entries.size() && i < MAX_TOP_REFERRERS; i++) {
            out.add(entries.get(i).getKey());
        }
        return out;
    }

    // ----------------------------------------------------------------
    // パッケージ循環 (Tarjan SCC)
    // ----------------------------------------------------------------

    private static void detectPackageCycles(InsightsModel model,
                                            List<InsightsModel.PackageEdge> edges) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (InsightsModel.PackageEdge e : edges) {
            graph.computeIfAbsent(e.getFrom(), k -> new ArrayList<>()).add(e.getTo());
            graph.computeIfAbsent(e.getTo(), k -> new ArrayList<>());
        }
        for (Set<String> scc : new Tarjan(graph).run()) {
            if (scc.size() < 2) {
                continue;
            }
            List<InsightsModel.PackageEdge> inner = new ArrayList<>();
            for (InsightsModel.PackageEdge e : edges) {
                if (scc.contains(e.getFrom()) && scc.contains(e.getTo())) {
                    inner.add(e);
                }
            }
            model.addPackageCycle(new InsightsModel.PackageCycle(scc, inner));
        }
    }

    /** Tarjan の強連結成分アルゴリズム (再帰なしの明示スタック版)。 */
    private static final class Tarjan {
        private final Map<String, List<String>> graph;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Set<String> onStack = new HashSet<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final List<Set<String>> sccs = new ArrayList<>();
        private int counter;

        Tarjan(Map<String, List<String>> graph) {
            this.graph = graph;
        }

        List<Set<String>> run() {
            for (String v : graph.keySet()) {
                if (!index.containsKey(v)) {
                    strongConnect(v);
                }
            }
            return sccs;
        }

        /** 明示スタックで DFS する (パッケージ数が多くても StackOverflow しない)。 */
        private void strongConnect(String root) {
            Deque<String[]> work = new ArrayDeque<>();
            work.push(new String[] {root, "0"});
            while (!work.isEmpty()) {
                String[] frame = work.peek();
                String v = frame[0];
                int childIndex = Integer.parseInt(frame[1]);
                if (childIndex == 0) {
                    index.put(v, counter);
                    lowLink.put(v, counter);
                    counter++;
                    stack.push(v);
                    onStack.add(v);
                }
                List<String> children = graph.getOrDefault(v,
                        java.util.Collections.emptyList());
                boolean recursed = false;
                for (int i = childIndex; i < children.size(); i++) {
                    String w = children.get(i);
                    if (!index.containsKey(w)) {
                        frame[1] = String.valueOf(i + 1);
                        work.push(new String[] {w, "0"});
                        recursed = true;
                        break;
                    }
                    if (onStack.contains(w)) {
                        lowLink.merge(v, index.get(w), Math::min);
                    }
                }
                if (recursed) {
                    continue;
                }
                work.pop();
                if (!work.isEmpty()) {
                    lowLink.merge(work.peek()[0], lowLink.get(v), Math::min);
                }
                if (lowLink.get(v).equals(index.get(v))) {
                    Set<String> scc = new LinkedHashSet<>();
                    String w;
                    do {
                        w = stack.pop();
                        onStack.remove(w);
                        scc.add(w);
                    } while (!w.equals(v));
                    sccs.add(scc);
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // デッドコード候補
    // ----------------------------------------------------------------

    private static void collectDeadCodeCandidates(InsightsModel model,
                                                  Map<String, JavaClassInfo> project,
                                                  ReferenceIndex refs,
                                                  RefAggregation agg,
                                                  Set<String> entryPointFqns) {
        List<InsightsModel.DeadCodeCandidate> out = new ArrayList<>();
        for (JavaClassInfo c : project.values()) {
            String qn = c.getQualifiedName();
            if (entryPointFqns.contains(qn) || isTestClass(c)) {
                continue;
            }
            boolean hasMain = c.getMethods().stream()
                    .anyMatch(InsightsAnalyzer::isMainMethod);
            if (!agg.referencedClasses.contains(qn) && !hasMain) {
                out.add(new InsightsModel.DeadCodeCandidate(
                        InsightsModel.DeadCodeCandidate.Kind.CLASS, qn,
                        "no inbound references",
                        c.getAnnotations().isEmpty() ? "HIGH" : "LOW"));
                continue;
            }
            if (c.getKind() != JavaClassInfo.Kind.CLASS
                    && c.getKind() != JavaClassInfo.Kind.RECORD
                    && c.getKind() != JavaClassInfo.Kind.ENUM) {
                // interface / annotation のメソッドは実装側で使われるため対象外
                continue;
            }
            // 参照判定・シンボルとも名前単位のため、同名オーバーロードは 1 件に集約する
            Set<String> seenMethodNames = new HashSet<>();
            for (JavaMethodInfo m : c.getMethods()) {
                if (!isDeadMethodCandidate(m) || !seenMethodNames.add(m.getName())) {
                    continue;
                }
                if (refs.sitesByMember(ReferenceKey.Kind.METHOD,
                        qn, m.getName()).isEmpty()) {
                    String confidence = c.getAnnotations().isEmpty()
                            && m.getAnnotations().isEmpty() ? "MEDIUM" : "LOW";
                    out.add(new InsightsModel.DeadCodeCandidate(
                            InsightsModel.DeadCodeCandidate.Kind.METHOD,
                            qn + "." + m.getName(),
                            "no call sites found (name-based, overloads merged)",
                            confidence));
                }
            }
        }
        out.sort(Comparator
                .comparing(InsightsModel.DeadCodeCandidate::getKind)
                .thenComparing(InsightsModel.DeadCodeCandidate::getSymbol));
        for (InsightsModel.DeadCodeCandidate d : out) {
            model.addDeadCodeCandidate(d);
        }
    }

    private static boolean isTestClass(JavaClassInfo c) {
        return c.getSimpleName().endsWith("Test")
                || c.getSimpleName().endsWith("IT");
    }

    /**
     * デッドコード判定の対象とする public メソッドか。
     * Android のライフサイクルコールバック ({@code on...}) や {@code @Override}
     * 付きメソッドはフレームワーク / 親型経由で呼ばれるため除外する。
     */
    private static boolean isDeadMethodCandidate(JavaMethodInfo m) {
        if (m.getVisibility() != Visibility.PUBLIC
                || m.isConstructor() || m.isAbstract()) {
            return false;
        }
        String name = m.getName();
        if ("main".equals(name)) {
            return false;
        }
        if (name.length() > 2 && name.startsWith("on")
                && Character.isUpperCase(name.charAt(2))) {
            return false;
        }
        for (String a : m.getAnnotations()) {
            if (a.startsWith("Override")) {
                return false;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------
    // レイヤ推定
    // ----------------------------------------------------------------

    private static void estimateLayers(InsightsModel model, Set<String> packages) {
        for (String pkg : packages) {
            model.putLayer(pkg, layerOf(pkg));
        }
    }

    /** パッケージ名のセグメントを末尾から辞書照合してレイヤを推定する。 */
    static String layerOf(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return "Unclassified";
        }
        String[] segments = pkg.split("\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            String layer = LAYER_DICTIONARY.get(segments[i].toLowerCase(java.util.Locale.ROOT));
            if (layer != null) {
                return layer;
            }
        }
        return "Unclassified";
    }

    private static String packageOf(JavaClassInfo c) {
        return c.getPackageName() == null ? "" : c.getPackageName();
    }
}
