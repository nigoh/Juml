// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.Locale;

/**
 * Functions / Members 一覧の「インクリメンタル絞り込み」ロジック（表示形式ごと）。
 *
 * <p>Swing から切り離した純粋関数として置くことで、ヘッダ・データ行の保持ルールを
 * 表示コンポーネントなしで検証できるようにする（{@link FilteredListPanel} の責務分離）。
 * いずれもクエリは大文字小文字を無視して部分一致で判定する。</p>
 */
final class ListReportFilter {

    private ListReportFilter() {
    }

    /**
     * Markdown テーブル向け。テーブルの見出し行・区切り行（先頭 2 本のパイプ行）と、
     * 散文・節見出し（パイプで始まらない行）は文脈として常に残す。データ行（3 本目以降の
     * パイプ行）はクエリを含むものだけを残す。
     */
    static String filterTable(String full, String query) {
        if (full == null || full.isEmpty()) {
            return "";
        }
        String ql = query.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int pipeSeen = 0;
        for (String line : full.split("\n", -1)) {
            if (!line.startsWith("|")) {
                sb.append(line).append('\n'); // 散文・節見出し・空行は文脈として保持
                continue;
            }
            pipeSeen++;
            if (pipeSeen <= 2 || line.toLowerCase(Locale.ROOT).contains(ql)) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * CSV 向け。1 行目のヘッダは常に残し、データ行はクエリを含むものだけを残す。
     */
    static String filterCsv(String full, String query) {
        if (full == null || full.isEmpty()) {
            return "";
        }
        String ql = query.toLowerCase(Locale.ROOT);
        String[] lines = full.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == 0 || lines[i].toLowerCase(Locale.ROOT).contains(ql)) {
                sb.append(lines[i]).append('\n');
            }
        }
        return sb.toString();
    }
}
