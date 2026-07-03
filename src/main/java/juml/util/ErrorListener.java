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
 * <p>各通知には {@link ErrorCode} を添え、利用者が ID から対処法を一意に
 * 引けるようにする。CLI の進捗通知 (「wrote …」等) のようなエラーでない
 * メッセージは {@link ErrorCode#NONE} を使う 3 引数オーバーロードで送る
 * (ID は付与されない)。</p>
 *
 * <p>用途別に {@link #stderr()} (標準エラー出力に出す) / {@link #collecting(List)}
 * (リストに溜める) / {@link #silent()} (無視) のファクトリが用意されている。</p>
 */
@FunctionalInterface
public interface ErrorListener {

    /**
     * エラー/警告を 1 件受け取る。
     *
     * @param code エラーカタログ ID。情報通知は {@link ErrorCode#NONE} (null 不可)
     * @param source 発生元ファイル名やソース識別子 (null 可)
     * @param line 1-based 行番号、不明な場合は -1
     * @param message エラーメッセージ本文 (null 不可)
     */
    void onError(ErrorCode code, String source, int line, String message);

    /** ID なし (情報通知) の便宜オーバーロード。{@link ErrorCode#NONE} で委譲する。 */
    default void onError(String source, int line, String message) {
        onError(ErrorCode.NONE, source, line, message);
    }

    /** 何もしないリスナー。 */
    static ErrorListener silent() {
        return (code, source, line, message) -> { };
    }

    /** 標準エラー出力 (System.err) に整形して出力するリスナー。 */
    static ErrorListener stderr() {
        return (code, source, line, message) ->
                System.err.println(format(code, source, line, message));
    }

    /**
     * 指定リストに「{@code [ID] source:line: message}」形式で蓄積するリスナー。
     * GUI などで後でまとめて表示する用途に使う。
     */
    static ErrorListener collecting(List<String> sink) {
        if (sink == null) {
            throw new IllegalArgumentException("sink is null");
        }
        return (code, source, line, message) ->
                sink.add(format(code, source, line, message));
    }

    /**
     * 1 行表現を組み立てる共通整形。ID があれば {@code [UML-R001] } を先頭に付け、
     * クローズド環境でも ID の目視転記だけで対処法を辿れるようにする。
     */
    static String format(ErrorCode code, String source, int line, String message) {
        StringBuilder sb = new StringBuilder();
        if (code != null && code.hasId()) {
            sb.append('[').append(code.getId()).append("] ");
        }
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
        return sb.toString();
    }
}
