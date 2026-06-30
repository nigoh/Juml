// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.Collections;
import java.util.List;

/**
 * 開いている図タブ数を上限内に保つための LRU 退避ポリシー (純粋ロジック)。
 *
 * <p>各図タブは描画済み SVG (Batik のベクタ木) を保持するため、タブを多数開くと
 * メモリが累積する。上限を超えたら「アクティブタブ以外で最も長く使われていない」
 * タブを 1 つ閉じてメモリを解放する。閉じたタブはツリーから再度開ける。</p>
 *
 * <p>Swing から切り離してテストできるよう、退避対象キーの選定だけをここに置く。</p>
 */
final class LruTabPolicy {

    private LruTabPolicy() {
    }

    /**
     * 上限を超えているとき、閉じるべきタブのキーを返す。
     *
     * @param mruOldestFirst 開いている図タブのキー (使用順: 先頭が最も古い)
     * @param activeKey      現在アクティブなタブのキー (退避対象から除外)。null 可
     * @param openCount      現在開いている図タブ数
     * @param max            図タブ数の上限 (0 以下で上限なし=退避しない)
     * @return 閉じるべきキー。退避不要 / 対象なしのときは {@code null}
     */
    static String victim(List<String> mruOldestFirst, String activeKey,
                         int openCount, int max) {
        if (max <= 0 || openCount <= max || mruOldestFirst == null) {
            return null;
        }
        for (String k : mruOldestFirst) {
            if (k != null && !k.equals(activeKey)) {
                return k;
            }
        }
        return null;
    }

    /**
     * 描画済み SVG を保持し続けるタブ ({@code keepRendered} 件の最新) を超えた、
     * 古いタブのキーを返す (これらは描画を解放し、再フォーカス時に再描画する)。
     *
     * @param mruOldestFirst 開いている図タブのキー (使用順: 先頭が最も古い)
     * @param keepRendered   描画を保持する最新タブ数 (0 以下で解放しない)
     * @return 描画を解放すべきキー (古い順)。無ければ空リスト
     */
    static List<String> keysToRelease(List<String> mruOldestFirst, int keepRendered) {
        if (mruOldestFirst == null || keepRendered <= 0
                || mruOldestFirst.size() <= keepRendered) {
            return Collections.emptyList();
        }
        return mruOldestFirst.subList(0, mruOldestFirst.size() - keepRendered);
    }
}
