// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * クラスの関数使用マップ（署名・利用側・実行条件・リスナー）をリードオンリー表示するパネル。
 *
 * <p>{@link juml.core.formats.uml.MethodUsageReport#render} の Markdown 結果をそのまま貼り付ける。
 * 等幅フォント / 行ラップなしで表示し、選択コピー可能（CLI の {@code --function-list} の GUI 版）。
 * 表示範囲セレクタとインクリメンタル絞り込みは {@link FilteredListPanel} が提供する。</p>
 */
public class MethodListPanel extends FilteredListPanel {

    public MethodListPanel() {
        super();
    }

    /**
     * Markdown テーブル向けの行絞り込み。テーブルの見出し行・区切り行（先頭 2 本のパイプ行）と、
     * 散文・節見出し（パイプ以外の行）は文脈として常に残し、データ行（3 本目以降のパイプ行）は
     * クエリを含むものだけを残す。
     */
    @Override
    protected String filter(String full, String query) {
        return ListReportFilter.filterTable(full, query);
    }
}
