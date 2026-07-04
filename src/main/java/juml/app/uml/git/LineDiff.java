// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import java.util.ArrayList;
import java.util.List;

/**
 * 2 つのテキスト (旧 / 新) を行単位で突き合わせ、左右 (side-by-side) 表示用に
 * <b>整列した行ペア</b>へ変換する純ロジック。
 *
 * <p>LCS (最長共通部分列) で一致行を求め、一致しない区間の削除行と追加行を
 * 上から順にペアにして「変更 (MODIFIED)」として整列させる (GitKraken 風)。
 * あぶれた行は片側のみの追加/削除として残す。Swing 非依存で単体テストできる。</p>
 *
 * <p>巨大ファイルで O(n·m) の DP がメモリを食い過ぎないよう、セル数が上限を超える
 * 場合は「全削除 → 全追加」という素朴な整列にフォールバックする。</p>
 */
final class LineDiff {

    /** DP を諦めてフォールバックするセル数のしきい値。 */
    private static final long MAX_CELLS = 8_000_000L;

    /** 行ペアの種別。 */
    enum Type { EQUAL, ADDED, REMOVED, MODIFIED }

    /** 左右に整列した 1 行分 (不変)。片側が無い場合は行番号 -1・テキスト null。 */
    static final class Row {
        final Type type;
        final int oldLine;
        final String oldText;
        final int newLine;
        final String newText;

        Row(Type type, int oldLine, String oldText, int newLine, String newText) {
            this.type = type;
            this.oldLine = oldLine;
            this.oldText = oldText;
            this.newLine = newLine;
            this.newText = newText;
        }
    }

    private LineDiff() {
    }

    /** テキストを行リストへ分割する (改行は保持しない。null は空)。 */
    static List<String> toLines(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        // 末尾が改行なら余分な空要素を作らないよう -1 は使わない。
        String[] parts = text.split("\n", -1);
        List<String> out = new ArrayList<>(parts.length);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (!p.isEmpty() && p.charAt(p.length() - 1) == '\r') {
                p = p.substring(0, p.length() - 1);
            }
            // 末尾改行由来の最後の空要素は落とす。
            if (i == parts.length - 1 && p.isEmpty()) {
                break;
            }
            out.add(p);
        }
        return out;
    }

    /** 旧 / 新テキストから整列済みの行ペアを計算する。 */
    static List<Row> compute(String oldText, String newText) {
        return compute(toLines(oldText), toLines(newText));
    }

    /** 旧 / 新の行リストから整列済みの行ペアを計算する。 */
    static List<Row> compute(List<String> oldLines, List<String> newLines) {
        int n = oldLines.size();
        int m = newLines.size();
        if ((long) n * (long) m > MAX_CELLS) {
            return fallback(oldLines, newLines);
        }

        // dp[i][j] = old[i..], new[j..] の LCS 長。
        int[][] dp = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (oldLines.get(i).equals(newLines.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<Row> rows = new ArrayList<>();
        List<String> pendDel = new ArrayList<>();
        List<Integer> pendDelNo = new ArrayList<>();
        List<String> pendAdd = new ArrayList<>();
        List<Integer> pendAddNo = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                flush(rows, pendDel, pendDelNo, pendAdd, pendAddNo);
                rows.add(new Row(Type.EQUAL, i + 1, oldLines.get(i),
                        j + 1, newLines.get(j)));
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                pendDel.add(oldLines.get(i));
                pendDelNo.add(i + 1);
                i++;
            } else {
                pendAdd.add(newLines.get(j));
                pendAddNo.add(j + 1);
                j++;
            }
        }
        while (i < n) {
            pendDel.add(oldLines.get(i));
            pendDelNo.add(i + 1);
            i++;
        }
        while (j < m) {
            pendAdd.add(newLines.get(j));
            pendAddNo.add(j + 1);
            j++;
        }
        flush(rows, pendDel, pendDelNo, pendAdd, pendAddNo);
        return rows;
    }

    /** 溜まった削除行・追加行を、上から順にペア (MODIFIED) にして整列出力する。 */
    private static void flush(List<Row> rows, List<String> del, List<Integer> delNo,
                              List<String> add, List<Integer> addNo) {
        int paired = Math.min(del.size(), add.size());
        for (int k = 0; k < paired; k++) {
            rows.add(new Row(Type.MODIFIED, delNo.get(k), del.get(k),
                    addNo.get(k), add.get(k)));
        }
        for (int k = paired; k < del.size(); k++) {
            rows.add(new Row(Type.REMOVED, delNo.get(k), del.get(k), -1, null));
        }
        for (int k = paired; k < add.size(); k++) {
            rows.add(new Row(Type.ADDED, -1, null, addNo.get(k), add.get(k)));
        }
        del.clear();
        delNo.clear();
        add.clear();
        addNo.clear();
    }

    /** DP を使わない素朴な整列 (旧を全削除 → 新を全追加)。巨大ファイル用。 */
    private static List<Row> fallback(List<String> oldLines, List<String> newLines) {
        List<Row> rows = new ArrayList<>(oldLines.size() + newLines.size());
        for (int i = 0; i < oldLines.size(); i++) {
            rows.add(new Row(Type.REMOVED, i + 1, oldLines.get(i), -1, null));
        }
        for (int j = 0; j < newLines.size(); j++) {
            rows.add(new Row(Type.ADDED, -1, null, j + 1, newLines.get(j)));
        }
        return rows;
    }
}
