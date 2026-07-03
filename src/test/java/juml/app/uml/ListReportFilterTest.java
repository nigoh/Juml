// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ListReportFilter} の絞り込み規則テスト（Swing 非依存の純粋関数）。
 */
public class ListReportFilterTest {

    private static final String TABLE =
            "# 関数使用マップ\n"
            + "\n"
            + "| クラス | 関数 | 利用側 |\n"
            + "|---|---|---|\n"
            + "| `Foo` | bar() | `CliDispatcher` |\n"
            + "| `Baz` | qux() | (呼び出し元なし) |\n"
            + "\n"
            + "## 算出ロジック\n"
            + "説明テキスト\n";

    private static final String CSV =
            "class,member,type\n"
            + "Foo,bar,void\n"
            + "Baz,qux,int\n";

    @Test
    public void tableKeepsHeaderSeparatorAndMatchingRows() {
        String out = ListReportFilter.filterTable(TABLE, "Foo");
        // 見出し行・区切り行は常に残る
        assertTrue(out.contains("| クラス | 関数 | 利用側 |"));
        assertTrue(out.contains("|---|---|---|"));
        // マッチするデータ行だけ残り、非マッチ行は消える
        assertTrue(out.contains("| `Foo` | bar()"));
        assertFalse(out.contains("| `Baz` | qux()"));
    }

    @Test
    public void tableKeepsProseContextLines() {
        String out = ListReportFilter.filterTable(TABLE, "Foo");
        // パイプで始まらない散文・節見出しは文脈として常に残す
        assertTrue(out.contains("# 関数使用マップ"));
        assertTrue(out.contains("## 算出ロジック"));
        assertTrue(out.contains("説明テキスト"));
    }

    @Test
    public void tableMatchesAnyColumnCaseInsensitively() {
        // 利用側 (caller) 列の一致でも行は残る。大文字小文字は無視。
        String out = ListReportFilter.filterTable(TABLE, "clidispatcher");
        assertTrue(out.contains("| `Foo` | bar()"));
        assertFalse(out.contains("| `Baz` | qux()"));
    }

    @Test
    public void csvKeepsHeaderAndMatchingRows() {
        String out = ListReportFilter.filterCsv(CSV, "Foo");
        assertTrue(out.startsWith("class,member,type\n")); // ヘッダは常に残る
        assertTrue(out.contains("Foo,bar,void"));
        assertFalse(out.contains("Baz,qux,int"));
    }

    @Test
    public void emptyInputReturnsEmpty() {
        assertEquals("", ListReportFilter.filterTable("", "x"));
        assertEquals("", ListReportFilter.filterCsv(null, "x"));
    }

    /**
     * 複数テーブルを含むレポートでは、2 個目以降のテーブルの見出し行・区切り行も
     * 残す。以前は pipeSeen を文書全体で数えていたため、2 番目のテーブルのヘッダが
     * データ行扱いになり絞り込みで消えていた。
     */
    @Test
    public void multipleTables_eachKeepsItsOwnHeader() {
        String twoTables =
                "## Foo クラス\n"
                + "| 関数 | 戻り値 |\n"
                + "|---|---|\n"
                + "| bar | void |\n"
                + "| skip | int |\n"
                + "\n"
                + "## Zed クラス\n"
                + "| 関数 | 戻り値 |\n"
                + "|---|---|\n"
                + "| bar | long |\n"
                + "| gone | int |\n";
        String out = ListReportFilter.filterTable(twoTables, "bar");
        // 2 番目のテーブルの見出し・区切りも残る (テーブル境界で pipeSeen リセット)。
        int headerCount = out.split("\\| 関数 \\| 戻り値 \\|", -1).length - 1;
        assertEquals("両テーブルのヘッダが残るべき", 2, headerCount);
        int sepCount = out.split("\\|---\\|---\\|", -1).length - 1;
        assertEquals("両テーブルの区切り行が残るべき", 2, sepCount);
        // 各テーブルのマッチ行は残り、非マッチ行は消える。
        assertTrue(out.contains("| bar | void |"));
        assertTrue(out.contains("| bar | long |"));
        assertFalse(out.contains("| skip | int |"));
        assertFalse(out.contains("| gone | int |"));
    }
}
