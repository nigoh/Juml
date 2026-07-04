// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.GitRepoService.CommitInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link GitGraphLayout} のレーン割り当て・エッジ計算を純ロジックとして検証する。
 * (Swing 非依存・headless で動く)
 */
public class GitGraphLayoutTest {

    /** テスト用のコミットを生成する (sha と親 sha だけ意味を持つ)。 */
    private static CommitInfo commit(String sha, String... parents) {
        return new CommitInfo(sha, sha.length() >= 7 ? sha.substring(0, 7) : sha,
                "msg " + sha, "msg " + sha, "Tester", "t@example.com",
                new Date(0), new ArrayList<>(Arrays.asList(parents)));
    }

    private static int outgoing(GitGraphLayout.Row row) {
        int n = 0;
        for (GitGraphLayout.Edge e : row.edges) {
            if (e.topColumn < 0) {
                n++;
            }
        }
        return n;
    }

    private static int incoming(GitGraphLayout.Row row) {
        int n = 0;
        for (GitGraphLayout.Edge e : row.edges) {
            if (e.bottomColumn < 0) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void linearHistoryStaysInOneLane() {
        // A(最新) -> B -> C。単純な一直線。
        List<CommitInfo> log = Arrays.asList(
                commit("A", "B"), commit("B", "C"), commit("C"));
        GitGraphLayout layout = GitGraphLayout.compute(log);

        assertEquals(3, layout.rows().size());
        assertEquals("直線履歴は 1 レーンに収まる", 1, layout.laneCount());
        for (GitGraphLayout.Row row : layout.rows()) {
            assertEquals(0, row.nodeColumn);
        }
        // 末尾 (初回コミット C) は親がないので継続エッジを出さない。
        assertEquals(0, outgoing(layout.rows().get(2)));
    }

    @Test
    public void mergeCreatesSecondLaneAndConverges() {
        // M = merge(A, B) / A,B は共に C を親に持つ。
        //   M(最新) -> 親 A, B
        //   A       -> 親 C
        //   B       -> 親 C
        //   C       (初回)
        List<CommitInfo> log = Arrays.asList(
                commit("M", "A", "B"),
                commit("A", "C"),
                commit("B", "C"),
                commit("C"));
        GitGraphLayout layout = GitGraphLayout.compute(log);

        assertEquals(4, layout.rows().size());
        assertEquals("マージで 2 レーンに広がる", 2, layout.laneCount());

        GitGraphLayout.Row merge = layout.rows().get(0);
        assertEquals("マージノードは先頭列", 0, merge.nodeColumn);
        assertEquals("2 つの親へ枝分かれする", 2, outgoing(merge));

        // 最終行 C には A・B の 2 レーンが合流する (bottomColumn == -1 が 2 本)。
        GitGraphLayout.Row last = layout.rows().get(3);
        assertEquals(2, incoming(last));
        assertEquals(0, outgoing(last));
    }

    @Test
    public void emptyHistoryYieldsNoRows() {
        GitGraphLayout layout = GitGraphLayout.compute(List.of());
        assertEquals(0, layout.rows().size());
        assertTrue("レーン数は最低 1", layout.laneCount() >= 1);
    }
}
