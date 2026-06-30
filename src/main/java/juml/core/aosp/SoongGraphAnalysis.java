// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * {@link AndroidBpModule} のリストからモジュール依存グラフを構築し、
 * 逆依存 (rdeps) と依存循環 (強連結成分) を解析する。
 *
 * <p>「プロジェクト内で宣言されたモジュール」(local) 同士のエッジだけを対象にする。
 * 外部参照 (依存名のみ判明) は {@link MarkdownSoongReport} 側の external ランキングが扱う。</p>
 *
 * <p>循環検出は Tarjan の強連結成分 (SCC) アルゴリズムで行う。サイズ 2 以上の SCC、
 * もしくは自己ループを「循環」として報告する。全要素循環の列挙は行わない (計算量回避)。</p>
 */
public final class SoongGraphAnalysis {

    /** {@code <iface>-V<n>-<backend>} 形式の AIDL 自動生成ライブラリ名を分解する。 */
    private static final java.util.regex.Pattern AIDL_GENERATED =
            java.util.regex.Pattern.compile("^(.+?)-V\\d+(?:-(?:java|cpp|ndk|rust))?$");

    private final Set<String> localNames;
    /** {@code aidl_interface} として宣言されたモジュール名 (生成ライブラリ名の解決元)。 */
    private final Set<String> aidlBases;
    /** module 名 → ローカル依存先 (重複排除・自己ループ除去・宣言順)。 */
    private final Map<String, List<String>> forward;
    /** module 名 → そのモジュールに依存しているローカルモジュール (宣言順)。 */
    private final Map<String, List<String>> reverse;
    /** 検出した循環 (各要素はメンバ名の昇順リスト)。 */
    private final List<List<String>> cycles;

    private SoongGraphAnalysis(Set<String> localNames, Set<String> aidlBases,
                               Map<String, List<String>> forward,
                               Map<String, List<String>> reverse,
                               List<List<String>> cycles) {
        this.localNames = localNames;
        this.aidlBases = aidlBases;
        this.forward = forward;
        this.reverse = reverse;
        this.cycles = cycles;
    }

    public static SoongGraphAnalysis of(List<AndroidBpModule> modules) {
        Set<String> local = new LinkedHashSet<>();
        Set<String> aidlBases = new LinkedHashSet<>();
        if (modules != null) {
            for (AndroidBpModule m : modules) {
                if (!m.getName().isEmpty()) {
                    local.add(m.getName());
                    if (m.getType().contains("aidl_interface")) {
                        aidlBases.add(m.getName());
                    }
                }
            }
        }
        Map<String, List<String>> forward = new LinkedHashMap<>();
        Map<String, List<String>> reverse = new LinkedHashMap<>();
        for (String name : local) {
            forward.put(name, new ArrayList<>());
            reverse.put(name, new ArrayList<>());
        }
        if (modules != null) {
            for (AndroidBpModule m : modules) {
                String from = m.getName();
                if (from.isEmpty()) {
                    continue;
                }
                List<String> outs = forward.get(from);
                Set<String> seen = new LinkedHashSet<>(outs);
                for (String depRaw : m.getDeps()) {
                    String dep = canonicalize(depRaw, local, aidlBases);
                    if (dep.equals(from) || !local.contains(dep)) {
                        continue;
                    }
                    if (seen.add(dep)) {
                        outs.add(dep);
                        reverse.get(dep).add(from);
                    }
                }
            }
        }
        List<List<String>> cycles = findCycles(local, forward);
        return new SoongGraphAnalysis(local, aidlBases, forward, reverse, cycles);
    }

    /**
     * 依存名を正規化する。{@code <iface>-V<n>-<backend>} のような AIDL 自動生成ライブラリ名で、
     * 基底 {@code <iface>} がローカルの {@code aidl_interface} なら基底名へ畳み込む。
     * それ以外は元の名前を返す。
     */
    public String canonical(String dep) {
        return canonicalize(dep, localNames, aidlBases);
    }

    private static String canonicalize(String dep, Set<String> local, Set<String> aidlBases) {
        if (local.contains(dep)) {
            return dep;
        }
        java.util.regex.Matcher m = AIDL_GENERATED.matcher(dep);
        if (m.matches() && aidlBases.contains(m.group(1))) {
            return m.group(1);
        }
        return dep;
    }

    /** ローカルモジュール名の集合。 */
    public Set<String> localNames() {
        return Collections.unmodifiableSet(localNames);
    }

    /** ローカル依存エッジ (module → 依存先)。 */
    public Map<String, List<String>> forwardEdges() {
        return Collections.unmodifiableMap(forward);
    }

    /** 逆依存 (module → 依存元)。 */
    public Map<String, List<String>> reverseEdges() {
        return Collections.unmodifiableMap(reverse);
    }

    /** 検出した循環 (SCC) のリスト。 */
    public List<List<String>> cycles() {
        return Collections.unmodifiableList(cycles);
    }

    /** あるモジュールが循環に属するか。 */
    public boolean isInCycle(String name) {
        for (List<String> c : cycles) {
            if (c.contains(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 被依存数 (ローカル逆依存の件数) で降順に並べた {@code [module, count]} のランキング。
     * count 同名は名前昇順。0 件のモジュールは除外する。
     */
    public List<Map.Entry<String, Integer>> mostDependedUpon(int limit) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Map.Entry<String, List<String>> e : reverse.entrySet()) {
            if (!e.getValue().isEmpty()) {
                counts.put(e.getKey(), e.getValue().size());
            }
        }
        List<Map.Entry<String, Integer>> rank = new ArrayList<>(counts.entrySet());
        rank.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .reversed().thenComparing(Map.Entry::getKey));
        if (limit > 0 && rank.size() > limit) {
            return rank.subList(0, limit);
        }
        return rank;
    }

    // ---- Tarjan SCC ----

    private static List<List<String>> findCycles(Set<String> nodes,
                                                 Map<String, List<String>> forward) {
        Tarjan t = new Tarjan(nodes, forward);
        return t.run();
    }

    /** Tarjan の強連結成分検出 (再帰スタックを明示スタックで実装し、深いグラフでも安全)。 */
    private static final class Tarjan {
        private final Set<String> nodes;
        private final Map<String, List<String>> forward;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> low = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new LinkedHashSet<>();
        private final List<List<String>> result = new ArrayList<>();
        private int counter = 0;

        Tarjan(Set<String> nodes, Map<String, List<String>> forward) {
            this.nodes = nodes;
            this.forward = forward;
        }

        List<List<String>> run() {
            for (String v : nodes) {
                if (!index.containsKey(v)) {
                    strongConnect(v);
                }
            }
            return result;
        }

        private void strongConnect(String start) {
            // 明示スタックで DFS をシミュレートする。
            Deque<String> work = new ArrayDeque<>();
            Deque<Integer> childPos = new ArrayDeque<>();
            work.push(start);
            childPos.push(0);
            index.put(start, counter);
            low.put(start, counter);
            counter++;
            stack.push(start);
            onStack.add(start);
            while (!work.isEmpty()) {
                String v = work.peek();
                int pos = childPos.pop();
                List<String> succ = forward.getOrDefault(v, Collections.emptyList());
                if (pos < succ.size()) {
                    childPos.push(pos + 1);
                    String w = succ.get(pos);
                    if (!index.containsKey(w)) {
                        index.put(w, counter);
                        low.put(w, counter);
                        counter++;
                        stack.push(w);
                        onStack.add(w);
                        work.push(w);
                        childPos.push(0);
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                } else {
                    // v の後続を処理し終えた
                    if (low.get(v).equals(index.get(v))) {
                        List<String> comp = new ArrayList<>();
                        String w;
                        do {
                            w = stack.pop();
                            onStack.remove(w);
                            comp.add(w);
                        } while (!w.equals(v));
                        if (comp.size() > 1 || hasSelfLoop(v)) {
                            Collections.sort(comp);
                            result.add(comp);
                        }
                    }
                    work.pop();
                    if (!work.isEmpty()) {
                        String parent = work.peek();
                        low.put(parent, Math.min(low.get(parent), low.get(v)));
                    }
                }
            }
        }

        private boolean hasSelfLoop(String v) {
            return forward.getOrDefault(v, Collections.emptyList()).contains(v);
        }
    }
}
