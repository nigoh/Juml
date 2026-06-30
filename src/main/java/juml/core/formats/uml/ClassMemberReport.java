// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.List;

/**
 * 全クラスの「メンバー一覧」を CSV で出力する。
 *
 * <p>列定義・各セルの計算 (素のメンバー属性 + 構造把握 + 処理解析) は {@link MemberAnalysis} に集約し、
 * このクラスはそれを CSV 文字列へ直列化するだけの薄い責務を持つ。Excel 出力 ({@link MemberWorkbookExporter})
 * も同じ {@link MemberAnalysis} を共有する。クラスは単純名で出力し、FQN/URI は使わない (パッケージは別カラム)。
 * 表計算ソフトへの取込を想定。</p>
 *
 * <p>列の意味は {@link MemberAnalysis.Col} を参照。{@code line}/{@code calls}/処理解析系のメトリクスは
 * メソッド本体を解析する FULL パースでのみ実値になる (GUI の Members タブは FULL)。</p>
 *
 * <p>データ行の空セルは {@code -} (ハイフン) で埋める。値が無い箇所を視認しやすくするため。ヘッダ行は対象外。</p>
 */
public final class ClassMemberReport {

    /** 空セルを表すマーカー。表計算で「値なし」として読みやすいハイフンを使う。 */
    private static final String EMPTY = "-";

    private ClassMemberReport() {
    }

    /** 全クラスのメンバー一覧 (1 メンバー=1 行) を CSV で返す。 */
    public static String render(List<JavaClassInfo> classes) {
        StringBuilder out = new StringBuilder();
        out.append(String.join(",", MemberAnalysis.headers())).append('\n');
        for (String[] row : MemberAnalysis.rows(classes)) {
            line(out, row);
        }
        return out.toString();
    }

    /** 1 行分のセルを CSV エスケープしてカンマ連結する。空セルは {@link #EMPTY} に置換する。 */
    private static void line(StringBuilder out, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            String v = nz(cells[i]);
            out.append(v.isEmpty() ? EMPTY : csv(v));
        }
        out.append('\n');
    }

    private static String csv(String s) {
        String v = nz(s);
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
