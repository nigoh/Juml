// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 大きな図でも補完が一定の費用で返ることを固定する純ロジックテスト (headless)。
 *
 * <p>候補生成は 1 打鍵ごとに EDT で走るため、本文長に対して線形を超えると
 * 入力がそのまま引っかかる。実際、名前ごとに本文を引き直していた頃は
 * 2,000 行で 1 打鍵 0.5 秒近くかかっていた。閾値は環境差を吸収できるだけ緩く取り、
 * 「計算量が本文長に対して増えていないか」だけを見張る。</p>
 */
public class PumlCompletionScaleTest {

    /** 1 打鍵あたりの上限。実測は 1 桁 ms なので、遅い環境でも桁が変わらなければ通る。 */
    private static final long BUDGET_MS = 500;

    private static String sequenceDiagram(int lines) {
        StringBuilder sb = new StringBuilder("@startuml\n");
        for (int i = 0; i < lines; i++) {
            sb.append("participant Node").append(i).append('\n');
            sb.append("Node").append(i).append(" -> Node").append(i + 1)
                    .append(" : call").append(i).append("()\n");
        }
        return sb.toString();
    }

    @Test
    public void candidateGeneration_staysFlatAsTheDiagramGrows() {
        String small = sequenceDiagram(200) + "pa";
        String huge = sequenceDiagram(10_000) + "pa";
        // 計測前に両方を一度通して JIT を温める (初回のコンパイル時間を測らない)。
        PumlCompletion.items(small, small.length(), false);
        PumlCompletion.items(huge, huge.length(), false);

        long start = System.nanoTime();
        int rounds = 5;
        for (int i = 0; i < rounds; i++) {
            PumlCompletion.items(huge, huge.length(), false);
        }
        long perCallMs = (System.nanoTime() - start) / rounds / 1_000_000;
        assertTrue("1 万行で 1 打鍵 " + perCallMs + " ms かかっている"
                + " (本文長に対して線形を超えている疑い)", perCallMs < BUDGET_MS);
    }

    @Test
    public void hugeDiagram_stillReturnsUsefulCandidates() {
        // 速さのために候補が空になっていないこと。
        String huge = sequenceDiagram(10_000) + "pa";
        List<PumlCompletionItem> items = PumlCompletion.items(huge, huge.length(), false);
        assertFalse(items.isEmpty());
        assertTrue(items.size() <= PumlCompletion.MAX_CANDIDATES);
        assertTrue("辞書の候補は本文の大きさに関わらず出る",
                items.stream().anyMatch(i -> i.label().equals("participant")));
    }

    @Test
    public void namesNearTheCaret_surviveTheScanWindow() {
        // 窓を切っても「直前に書いた名前」は必ず拾えること (窓の目的そのもの)。
        String huge = sequenceDiagram(10_000) + "participant VeryRecentName\nVeryRec";
        List<PumlCompletionItem> items = PumlCompletion.items(huge, huge.length(), false);
        assertTrue("キャレット直前の宣言が候補に出る",
                items.stream().anyMatch(i -> i.label().equals("VeryRecentName")));
    }

    @Test
    public void caretAtTheStartOfAHugeDiagram_isAlsoFast() {
        String huge = sequenceDiagram(10_000);
        String text = "@startuml\npa" + huge.substring(9);
        long start = System.nanoTime();
        PumlCompletion.items(text, 12, false);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue("先頭でも " + ms + " ms は遅すぎる", ms < BUDGET_MS);
    }
}
