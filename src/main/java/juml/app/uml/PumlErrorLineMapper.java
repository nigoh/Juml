// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * PlantUML が報告する「生成ソースの行番号」を、エディタ ({@link PumlSourcePanel}) 上の
 * 行番号へ写像する純ロジック。
 *
 * <p>{@link juml.core.formats.uml.PlantUmlRenderer#injectLayout} が {@code @startuml} 直後へ
 * prelude を挿入する (かつ向き指定行を除去する) ため、生成側の行番号はエディタ側と
 * ずれる。ここでは行内容を突き合わせて正確に対応付け、一致が無い場合は行数差による
 * 数値補正へフォールバックする。</p>
 */
final class PumlErrorLineMapper {

    private PumlErrorLineMapper() {
    }

    /**
     * スタイル prelude 挿入分 ({@code injectedLines}) を差し引いてエディタ上の行番号へ写像する。
     * 挿入は {@code @startuml} 直後に入るため、行 1 (= @startuml) はそのまま。
     */
    static int editorLineForError(int errorLine, int injectedLines) {
        if (errorLine <= 1 || injectedLines <= 0) {
            return errorLine;
        }
        return Math.max(1, errorLine - injectedLines);
    }

    /**
     * 生成テキストとエディタテキストを突き合わせて行番号を写像する。
     *
     * <p>prelude の挿入と向き指定行の除去で生じるずれを、行内容の一致 (前後コンテキスト優先) で
     * 吸収する。一致が無い prelude 由来の行などは行数差による数値補正へフォールバックする。</p>
     */
    static int editorLineForError(String editorPuml, String generatedPuml, int errorLine) {
        if (editorPuml == null || generatedPuml == null || errorLine <= 0) {
            return Math.max(1, errorLine);
        }
        String[] gen = generatedPuml.split("\n", -1);
        String[] edit = editorPuml.split("\n", -1);
        if (errorLine > gen.length) {
            return Math.min(Math.max(1, errorLine), edit.length);
        }
        // prelude 挿入分を差し引いた「エディタ上の期待位置」。距離の基準を生成行番号の
        // ままにすると、同一内容の行 ("}" や重複する関連行など PlantUML で頻出) がある
        // 場合に、常に正解より下の複製行が選ばれる下方バイアスが出る。
        int injected = Math.max(0, gen.length - edit.length);
        int expected = Math.max(1, errorLine - injected);
        String target = gen[errorLine - 1].trim();
        if (!target.isEmpty()) {
            // 同一内容の行が複数あるとき、数値距離 (expected) だけだと direction 行の除去などで
            // expected がずれて隣接する誤った複製を選びうる。まず前後の行内容 (near context) が
            // 生成側の errorLine 周辺と一致する候補を優先し、同点は expected への距離で決める。
            String prevCtx = nonEmptyNeighbor(gen, errorLine - 1, -1);
            String nextCtx = nonEmptyNeighbor(gen, errorLine - 1, +1);
            int best = -1;
            int bestScore = Integer.MIN_VALUE;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < edit.length; i++) {
                if (!edit[i].trim().equals(target)) {
                    continue;
                }
                int ctx = 0;
                if (prevCtx != null && prevCtx.equals(nonEmptyNeighbor(edit, i, -1))) {
                    ctx++;
                }
                if (nextCtx != null && nextCtx.equals(nonEmptyNeighbor(edit, i, +1))) {
                    ctx++;
                }
                int dist = Math.abs((i + 1) - expected);
                if (ctx > bestScore || (ctx == bestScore && dist < bestDist)) {
                    bestScore = ctx;
                    bestDist = dist;
                    best = i + 1;
                }
            }
            if (best > 0) {
                return best;
            }
        }
        // 内容一致なし (prelude 由来の行や空行): 挿入行数を行数差で推定して数値補正する。
        return editorLineForError(errorLine, injected);
    }

    /**
     * {@code lines[from]} から {@code dir} 方向 (+1/-1) に進み、最初の非空行の trim 内容を返す。
     * 端に達したら null。重複行のタイブレークで前後コンテキストを比較するために使う。
     */
    private static String nonEmptyNeighbor(String[] lines, int from, int dir) {
        for (int i = from + dir; i >= 0 && i < lines.length; i += dir) {
            String t = lines[i].trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }
}
