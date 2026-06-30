// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.impact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Impact Analysis の結果グラフ。
 *
 * <p>ノードは「影響を受ける可能性のあるシンボル (クラス FQN もしくは FQN.method)」、
 * エッジは「参照関係 (caller → callee)」を表す。</p>
 */
public final class ImpactGraph {

    /** ノード (影響を受ける可能性があるシンボル)。 */
    public static final class Node {
        private final String id;
        private final int layer;
        private final double score;
        private final String reason;

        public Node(String id, int layer, double score, String reason) {
            this.id = id;
            this.layer = layer;
            this.score = score;
            this.reason = reason == null ? "" : reason;
        }

        public String getId() { return id; }
        public int getLayer() { return layer; }
        public double getScore() { return score; }
        public String getReason() { return reason; }

        /** 影響度スコアから簡易リスクラベル。
         * しきい値は種別重み付けスコア (CALL=1.0 / TYPE_REFERENCE=0.7 / IMPORT=0.4,
         * 層ごとに半減) に合わせ、直接呼び出し層のみが HIGH になるよう設定。 */
        public String getBreakageRisk() {
            if (layer == 0) return "TARGET";
            if (score >= 0.8) return "HIGH";
            if (score >= 0.45) return "MEDIUM";
            return "LOW";
        }
    }

    /** エッジ (caller → callee の参照)。 */
    public static final class Edge {
        private final String from;
        private final String to;
        private final String kind;
        private final String callerMethod;
        private final String file;
        private final int lineHint;

        public Edge(String from, String to, String kind,
                    String callerMethod, String file, int lineHint) {
            this.from = from;
            this.to = to;
            this.kind = kind == null ? "" : kind;
            this.callerMethod = callerMethod == null ? "" : callerMethod;
            this.file = file == null ? "" : file;
            this.lineHint = lineHint;
        }

        public String getFrom() { return from; }
        public String getTo() { return to; }
        public String getKind() { return kind; }
        public String getCallerMethod() { return callerMethod; }
        public String getFile() { return file; }
        public int getLineHint() { return lineHint; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Edge)) return false;
            Edge e = (Edge) o;
            return lineHint == e.lineHint
                    && Objects.equals(from, e.from)
                    && Objects.equals(to, e.to)
                    && Objects.equals(kind, e.kind)
                    && Objects.equals(callerMethod, e.callerMethod)
                    && Objects.equals(file, e.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, kind, callerMethod, file, lineHint);
        }
    }

    private final String target;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    public ImpactGraph(String target) {
        this.target = target == null ? "" : target;
    }

    public String getTarget() {
        return target;
    }

    public void addNode(String id, int layer, double score, String reason) {
        if (id == null || id.isEmpty()) {
            return;
        }
        nodes.putIfAbsent(id, new Node(id, layer, score, reason));
    }

    public void addEdge(String from, String to, String kind,
                         String callerMethod, String file, int lineHint) {
        if (from == null || to == null || from.isEmpty() || to.isEmpty()) {
            return;
        }
        edges.add(new Edge(from, to, kind, callerMethod, file, lineHint));
    }

    /** ノード一覧 (追加順)。 */
    public List<Node> nodes() {
        return new ArrayList<>(nodes.values());
    }

    /** エッジ一覧 (追加順)。 */
    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    /** 直接参照元の数 (layer == 1 のノード数)。 */
    public int directCallerCount() {
        int n = 0;
        for (Node node : nodes.values()) {
            if (node.getLayer() == 1) {
                n++;
            }
        }
        return n;
    }

    /** 推移閉包の合計 (target を除く)。 */
    public int transitiveCallerCount() {
        return Math.max(0, nodes.size() - 1);
    }
}
