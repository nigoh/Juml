// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.util.List;

/**
 * Java/AIDL ソース変換中の警告・エラーを受け取るリスナー。
 *
 * <p>パーサ/コンバータは「致命的では無いが利用者に知らせる価値があるエラー」を
 * 例外で投げずに本リスナー経由で通知する。プロジェクト走査時の個別ファイル
 * 読込失敗、内部パース時の不正トークン、AIDL の文法逸脱などが典型例。</p>
 *
 * <p>用途別に {@link #stderr()} (標準エラー出力に出す) / {@link #collecting(List)}
 * (リストに溜める) / {@link #silent()} (無視) のファクトリが用意されている。</p>
 */
@FunctionalInterface
public interface ErrorListener {

    /**
     * エラー/警告を 1 件受け取る。
     *
     * @param source 発生元ファイル名やソース識別子 (null 可)
     * @param line 1-based 行番号、不明な場合は -1
     * @param message エラーメッセージ本文 (null 不可)
     */
    void onError(String source, int line, String message);

    /** 何もしないリスナー。 */
    static ErrorListener silent() {
        return (source, line, message) -> { };
    }

    /** 標準エラー出力 (System.err) に整形して出力するリスナー。 */
    static ErrorListener stderr() {
        return (source, line, message) -> {
            StringBuilder sb = new StringBuilder();
            if (source != null && !source.isEmpty()) {
                sb.append(source);
                if (line >= 0) {
                    sb.append(':').append(line);
                }
                sb.append(": ");
            } else if (line >= 0) {
                sb.append("line ").append(line).append(": ");
            }
            sb.append(message);
            System.err.println(sb.toString());
        };
    }

    /**
     * 指定リストに「{@code source:line: message}」形式で蓄積するリスナー。
     * GUI などで後でまとめて表示する用途に使う。
     */
    static ErrorListener collecting(List<String> sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink is null");
        }
        return (source, line, message) -> {
            StringBuilder sb = new StringBuilder();
            if (source != null && !source.isEmpty()) {
                sb.append(source);
                if (line >= 0) {
                    sb.append(':').append(line);
                }
                sb.append(": ");
            } else if (line >= 0) {
                sb.append("line ").append(line).append(": ");
            }
            sb.append(message);
            sink.add(sb.toString());
        };
    }
}
