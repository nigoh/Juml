// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SQLite カラムに「カンマ連結」で多値を格納するためのエンコード/デコード。
 *
 * <p>{@code modifiers} / {@code annotations} / {@code param_types} など、
 * 順序を保ちながら少量の文字列列を保存したいときに使う。
 * カンマ自体は {@code \,}、バックスラッシュは {@code \\} にエスケープする。</p>
 *
 * <p>多対多の本格的なクエリ対象 (例: {@code class_interfaces} の iface_qn) は
 * 別テーブル化する。本コーデックは「並びを再現するだけで個別検索しない」
 * カラム向け。</p>
 */
public final class CsvCodec {

    private CsvCodec() {
    }

    /** リストを「\, でカンマ自身をエスケープしつつカンマ連結」した文字列に変換。 */
    public static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values.get(i)));
        }
        return sb.toString();
    }

    /** {@link #join(List)} の逆変換。null/空文字なら空リスト。 */
    public static List<String> split(String csv) {
        if (csv == null || csv.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < csv.length(); i++) {
            char ch = csv.charAt(i);
            if (esc) {
                cur.append(ch);
                esc = false;
            } else if (ch == '\\') {
                esc = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String escape(String v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\\' || ch == ',') {
                sb.append('\\');
            }
            sb.append(ch);
        }
        return sb.toString();
    }
}
