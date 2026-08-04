// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * スニペット本文のテンプレート展開 (プレースホルダ → 実テキスト + タブストップ)。
 *
 * <p>「なるべく入力を少なく」するための要は、雛形を丸ごと入れたうえで
 * <em>埋めるべき場所だけ</em> を順に巡れることにある。本クラスはテンプレート中の
 * プレースホルダ記法を解釈し、挿入すべきプレーン本文と、{@code Tab} で巡回する
 * タブストップ座標 (本文先頭からの相対オフセット) に分解する。</p>
 *
 * <p>記法:</p>
 * <ul>
 *   <li>{@code ${1:cond}} — 1 番目のタブストップ。既定値 {@code cond} が選択状態で入り、
 *       そのまま打てば置き換わる。</li>
 *   <li>{@code ${2}} — 既定値なしのタブストップ (空のキャレット位置)。</li>
 *   <li>{@code ${0}} — 最後に訪れるタブストップ (巡回の終端)。番号順の後に置かれる。</li>
 *   <li>{@code ${caret}} — 既存スニペット互換の終端キャレット ({@code ${0}} と同義)。</li>
 * </ul>
 *
 * <p>数字でない {@code ${...}} はマーカーとみなさずそのまま本文へ出す
 * (PlantUML 本文に {@code ${}} 風の文字列が出ても壊さないため)。</p>
 */
final class PumlSnippetTemplate {

    /** 展開結果: 挿入するプレーン本文と、Tab で巡回するタブストップ。 */
    static final class Expansion {
        private final String text;
        private final List<int[]> stops;

        private Expansion(String text, List<int[]> stops) {
            this.text = text;
            this.stops = List.copyOf(stops);
        }

        /** 実際に挿入する本文 (マーカー除去済み)。 */
        String text() {
            return text;
        }

        /**
         * 巡回順のタブストップ。各要素は本文先頭からの相対 {@code {開始, 終了}}。
         * 空 (要素なし) ならキャレットは本文末尾へ置く。
         */
        List<int[]> stops() {
            return stops;
        }
    }

    private PumlSnippetTemplate() {
    }

    /** インデントなしで展開する。 */
    static Expansion expand(String template) {
        return expand(template, "");
    }

    /**
     * {@code template} を展開する。{@code indent} が非空なら 2 行目以降の
     * 各行 (空行を除く) の先頭へ付け、入れ子のブロックが現在行の字下げに揃うようにする。
     */
    static Expansion expand(String template, String indent) {
        String src = applyIndent(template == null ? "" : template, indent);
        StringBuilder out = new StringBuilder();
        List<int[]> ordered = new ArrayList<>();
        List<Integer> orders = new ArrayList<>();
        int i = 0;
        while (i < src.length()) {
            int open = src.indexOf("${", i);
            if (open < 0) {
                out.append(src, i, src.length());
                break;
            }
            int close = src.indexOf('}', open + 2);
            if (close < 0) {
                out.append(src, i, src.length());
                break;
            }
            out.append(src, i, open);
            String body = src.substring(open + 2, close);
            int order = orderOf(body);
            if (order < 0) {
                // マーカーではない ${...}。本文としてそのまま通す。
                out.append(src, open, close + 1);
                i = close + 1;
                continue;
            }
            int colon = body.indexOf(':');
            String value = colon >= 0 ? body.substring(colon + 1) : "";
            int start = out.length();
            out.append(value);
            ordered.add(new int[] {start, out.length()});
            orders.add(order);
            i = close + 1;
        }
        return new Expansion(out.toString(), sortByOrder(ordered, orders));
    }

    /**
     * プレースホルダ本文の巡回番号。{@code caret} は {@code 0} (終端) として扱う。
     * 数字で始まらない (= マーカーでない) なら {@code -1}。
     */
    private static int orderOf(String body) {
        if ("caret".equals(body)) {
            return 0;
        }
        int colon = body.indexOf(':');
        String num = colon >= 0 ? body.substring(0, colon) : body;
        if (num.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < num.length(); i++) {
            if (!Character.isDigit(num.charAt(i))) {
                return -1;
            }
        }
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * 番号昇順 (0 は終端) に並べ替える。同番号は出現順を保つ (安定ソート)。
     */
    private static List<int[]> sortByOrder(List<int[]> stops, List<Integer> orders) {
        List<int[]> idx = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            // {巡回キー, 出現順, 開始, 終了} にして安定ソートする。
            int order = orders.get(i);
            idx.add(new int[] {order == 0 ? Integer.MAX_VALUE : order, i,
                    stops.get(i)[0], stops.get(i)[1]});
        }
        idx.sort(Comparator.<int[]>comparingInt(a -> a[0]).thenComparingInt(a -> a[1]));
        List<int[]> out = new ArrayList<>(idx.size());
        for (int[] e : idx) {
            out.add(new int[] {e[2], e[3]});
        }
        return out;
    }

    /**
     * 2 行目以降の各行へ {@code indent} を付ける。空行には付けない
     * (行末に無意味な空白を残さないため)。
     */
    private static String applyIndent(String template, String indent) {
        if (indent == null || indent.isEmpty() || template.indexOf('\n') < 0) {
            return template;
        }
        String[] lines = template.split("\n", -1);
        StringBuilder sb = new StringBuilder(lines[0]);
        for (int i = 1; i < lines.length; i++) {
            sb.append('\n');
            if (!lines[i].isEmpty()) {
                sb.append(indent);
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
