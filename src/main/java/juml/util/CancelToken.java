// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 長時間処理のキャンセル要求を伝搬するための軽量トークン。
 *
 * <p>大規模プロジェクト解析時に走査・パース・キャッシュ書き込みのいずれかで
 * ユーザーが「中断」を選んだ場合、この {@link CancelToken#cancel()} を呼ぶ。
 * パイプライン各層は {@link #isCancelled()} をループ内で定期確認し、
 * 早期離脱する責務を持つ。</p>
 *
 * <p>状態はスレッドセーフ ({@link AtomicBoolean} ベース)。一度
 * cancel 状態になったトークンを未キャンセルに戻すことはできない (使い捨て)。</p>
 */
public final class CancelToken {

    /** 既にキャンセル不要な場面で使い回す共通の「キャンセルされないトークン」。 */
    public static final CancelToken NONE = new CancelToken();

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** 新しい未キャンセル状態のトークンを作成する。 */
    public CancelToken() {
    }

    /** キャンセル要求を立てる。複数回呼んでも安全。 */
    public void cancel() {
        cancelled.set(true);
    }

    /** キャンセル要求が立っていれば true。 */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /** キャンセル済みなら {@link InterruptedException} を投げる。ループの境界で使う。 */
    public void throwIfCancelled() throws InterruptedException {
        if (cancelled.get()) {
            throw new InterruptedException("cancelled");
        }
    }
}
