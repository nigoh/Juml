// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.CommitInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * コミット履歴 (新しい順) を GitKraken 風のレーングラフに変換する純ロジック。
 *
 * <p>親 SHA の DAG から、各行について「ノードが乗るレーン列」「行内を通過・分岐・合流
 * するエッジ」を計算する。副作用や Swing 依存を持たないため単体テストできる。描画は
 * {@link GitCommitsPane} が本結果を読み取って行う。</p>
 *
 * <h2>座標系</h2>
 * <p>行 index はリスト順 (0 = 最新) で下に増える。列 (lane) は 0 が左。各行は高さ 1 の
 * 帯で、ノードは帯の中央 (y=0.5) に置く。エッジは帯の上端 (y=0, 前行の下端に一致) から
 * 下端 (y=1, 次行の上端に一致) を結ぶため、行をまたいで線が途切れない。</p>
 */
final class GitGraphLayout {

    /** 行内のエッジ 1 本。列 {@code -1} はノード中央 (y=0.5) を表す。 */
    static final class Edge {
        /** 帯上端での列 (-1 ならノード中央から出る)。 */
        final int topColumn;
        /** 帯下端での列 (-1 ならノード中央へ入る)。 */
        final int bottomColumn;
        /** 配色インデックス (パレットを剰余で参照)。 */
        final int colorIndex;

        Edge(int topColumn, int bottomColumn, int colorIndex) {
            this.topColumn = topColumn;
            this.bottomColumn = bottomColumn;
            this.colorIndex = colorIndex;
        }
    }

    /** 1 コミット行分のレイアウト結果 (不変)。 */
    static final class Row {
        /** ノード (コミットの丸) が乗る列。 */
        final int nodeColumn;
        /** ノードの配色インデックス。 */
        final int colorIndex;
        /** この帯に描くエッジ群。 */
        final List<Edge> edges;

        Row(int nodeColumn, int colorIndex, List<Edge> edges) {
            this.nodeColumn = nodeColumn;
            this.colorIndex = colorIndex;
            this.edges = edges;
        }
    }

    /** レーン 1 本の状態: 次に来るはずの親 SHA と、その配色。 */
    private static final class Lane {
        String expectedSha;
        int colorIndex;

        Lane(String expectedSha, int colorIndex) {
            this.expectedSha = expectedSha;
            this.colorIndex = colorIndex;
        }
    }

    private final List<Row> rows;
    private final int laneCount;

    private GitGraphLayout(List<Row> rows, int laneCount) {
        this.rows = rows;
        this.laneCount = laneCount;
    }

    /** 行ごとのレイアウト (入力と同じ順序・件数)。 */
    List<Row> rows() {
        return rows;
    }

    /** 使用された最大レーン数 (= 描画に必要な列幅)。最低 1。 */
    int laneCount() {
        return laneCount;
    }

    /**
     * 履歴からレイアウトを計算する。
     *
     * @param commits 新しい順のコミット (各 {@code parents} に親の完全 SHA を持つ)
     */
    static GitGraphLayout compute(List<CommitInfo> commits) {
        List<Row> out = new ArrayList<>(commits.size());
        List<Lane> lanes = new ArrayList<>();
        int maxCol = 0;
        int nextColor = 0;

        for (CommitInfo commit : commits) {
            String sha = commit.sha;
            List<String> parents = commit.parents;
            List<Edge> edges = new ArrayList<>();

            // このコミットを待っていたレーンを探す (合流もあるので複数あり得る)。
            int nodeCol = -1;
            for (int j = 0; j < lanes.size(); j++) {
                Lane lane = lanes.get(j);
                if (lane != null && sha.equals(lane.expectedSha)) {
                    nodeCol = j;
                    break;
                }
            }
            int colorIndex;
            if (nodeCol < 0) {
                // どのレーンも待っていない = 履歴窓内での枝先。新レーンを起こす。
                nodeCol = allocate(lanes);
                colorIndex = nextColor++;
            } else {
                colorIndex = lanes.get(nodeCol).colorIndex;
            }

            // 通過レーン (このコミットと無関係) はまっすぐ下へ。
            for (int j = 0; j < lanes.size(); j++) {
                Lane lane = lanes.get(j);
                if (lane != null && !sha.equals(lane.expectedSha)) {
                    edges.add(new Edge(j, j, lane.colorIndex));
                }
            }
            // このコミットを待っていたレーンはノードへ合流。ノード列以外は解放。
            for (int j = 0; j < lanes.size(); j++) {
                Lane lane = lanes.get(j);
                if (lane != null && sha.equals(lane.expectedSha)) {
                    edges.add(new Edge(j, -1, lane.colorIndex));
                    if (j != nodeCol) {
                        lanes.set(j, null);
                    }
                }
            }

            if (parents.isEmpty()) {
                // 初回コミット: 継続なし。ノード列を解放する。
                lanes.set(nodeCol, null);
            } else {
                // 第 1 親はノード列をそのまま継承 (色も維持)。
                lanes.set(nodeCol, new Lane(parents.get(0), colorIndex));
                edges.add(new Edge(-1, nodeCol, colorIndex));
                // 追加の親 (マージ) は既存レーンへ合流するか、新レーンへ分岐。
                for (int p = 1; p < parents.size(); p++) {
                    String parent = parents.get(p);
                    int existing = indexOfExpected(lanes, parent);
                    if (existing >= 0) {
                        edges.add(new Edge(-1, existing, lanes.get(existing).colorIndex));
                    } else {
                        int col = allocate(lanes);
                        int c = nextColor++;
                        lanes.set(col, new Lane(parent, c));
                        edges.add(new Edge(-1, col, c));
                    }
                }
            }

            maxCol = Math.max(maxCol, nodeCol);
            for (Edge e : edges) {
                maxCol = Math.max(maxCol, Math.max(e.topColumn, e.bottomColumn));
            }
            out.add(new Row(nodeColumnClamped(nodeCol), colorIndex, edges));
        }
        return new GitGraphLayout(out, Math.max(1, maxCol + 1));
    }

    private static int nodeColumnClamped(int col) {
        return Math.max(0, col);
    }

    /** 空きレーン (null スロット) を再利用し、無ければ末尾に追加してその列を返す。 */
    private static int allocate(List<Lane> lanes) {
        for (int j = 0; j < lanes.size(); j++) {
            if (lanes.get(j) == null) {
                return j;
            }
        }
        lanes.add(null);
        return lanes.size() - 1;
    }

    private static int indexOfExpected(List<Lane> lanes, String sha) {
        for (int j = 0; j < lanes.size(); j++) {
            Lane lane = lanes.get(j);
            if (lane != null && sha.equals(lane.expectedSha)) {
                return j;
            }
        }
        return -1;
    }
}
