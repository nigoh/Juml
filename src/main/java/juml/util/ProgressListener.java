// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

/**
 * 長時間処理の進捗を受け取るリスナー。
 *
 * <p>{@link ErrorListener} と同じ要領で関数型インタフェースとして定義し、
 * Scanner / Parser / Cache が「N 件中 M 件処理した」を報告する。
 * GUI 側はステータスバーやプログレスバーに反映する。</p>
 *
 * <p>{@code total} が {@code -1} の場合は総数未確定の進捗 (走査中など)。
 * {@code done == total} で完了を示すが、完了通知の有無は呼び出し側の自由。</p>
 *
 * <p>呼び出しスレッドは任意 (ワーカープール内など)。GUI 反映には
 * {@code SwingUtilities.invokeLater} 等で EDT に飛ばすファクトリ
 * ({@link #throttled}) を利用すること。</p>
 */
@FunctionalInterface
public interface ProgressListener {

    /**
     * 進捗を 1 件報告する。
     *
     * @param done    完了済み件数 (0 以上)
     * @param total   全体件数。未確定なら -1
     * @param message 補足メッセージ (現在処理中のファイル名など、null 可)
     */
    void onProgress(int done, int total, String message);

    /** 何もしないリスナー。 */
    static ProgressListener silent() {
        return (done, total, message) -> { };
    }

    /** 標準出力に整形して出力するリスナー。CLI / デバッグ用。 */
    static ProgressListener console() {
        return (done, total, message) -> {
            StringBuilder sb = new StringBuilder();
            if (total >= 0) {
                sb.append('[').append(done).append('/').append(total).append(']');
            } else {
                sb.append('[').append(done).append(']');
            }
            if (message != null && !message.isEmpty()) {
                sb.append(' ').append(message);
            }
            System.out.println(sb.toString());
        };
    }

    /**
     * 一定間隔 ({@code minIntervalMs} ミリ秒) 以上経過したときだけ {@code delegate}
     * へ流す絞り込みリスナーを返す。完了通知 ({@code done == total}, または
     * {@code total < 0} で {@code done} が 0 のとき) は常に流す。
     *
     * <p>GUI で毎ファイル更新すると EDT が詰まるので、典型的には 100〜250ms 程度に絞る。</p>
     */
    static ProgressListener throttled(ProgressListener delegate, long minIntervalMs) {
        if (delegate == null) {
            return silent();
        }
        final long interval = Math.max(0L, minIntervalMs);
        return new ProgressListener() {
            private long last;

            @Override
            public synchronized void onProgress(int done, int total, String message) {
                long now = System.currentTimeMillis();
                boolean isCompletion = (total >= 0 && done >= total);
                if (isCompletion || now - last >= interval) {
                    last = now;
                    delegate.onProgress(done, total, message);
                }
            }
        };
    }
}
