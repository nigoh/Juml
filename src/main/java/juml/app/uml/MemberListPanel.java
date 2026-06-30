// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * クラスの「純粋なメンバー一覧」（フィールド・メソッド・enum 定数）を CSV でリードオンリー表示するパネル。
 *
 * <p>{@link juml.core.formats.uml.ClassMemberReport#render} の CSV をそのまま貼り付ける。
 * 等幅フォント / 行ラップなしで表示し、選択コピーして表計算ソフトに取り込める。
 * クラスは単純名カラム、パッケージは別カラムに分離する。
 * 表示範囲セレクタとインクリメンタル絞り込みは {@link FilteredListPanel} が提供する。</p>
 */
public class MemberListPanel extends FilteredListPanel {

    public MemberListPanel() {
        super();
    }

    /**
     * CSV 向けの行絞り込み。1 行目のヘッダは常に残し、データ行はクエリを含むものだけを残す。
     */
    @Override
    protected String filter(String full, String query) {
        return ListReportFilter.filterCsv(full, query);
    }
}
