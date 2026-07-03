// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * 図タブのメモリ使用を抑えるための状態と判定をまとめた協調オブジェクト。
 *
 * <p>各図タブは描画済み SVG (Batik のベクタ木) を保持するため、タブを多数開くと
 * メモリが累積する。本クラスはタブの使用順 (MRU) を保持し、フォーカス時に:</p>
 * <ul>
 *   <li>上限 ({@code maxTabs}) を超えたら最古の未使用タブを<b>閉じる</b> ({@link Actions#closeTab})</li>
 *   <li>最新 {@code keepRendered} 件を超えた古いタブの<b>描画を解放</b> ({@link Actions#releaseRender})。
 *       再フォーカス時に {@link Actions#ensureRendered} で再描画される</li>
 * </ul>
 *
 * <p>Swing 操作 (タブのクローズ・描画解放・再描画) は {@link Actions} 経由で呼び出し元に
 * 委譲し、判定ロジック自体はここに閉じてユニットテストしやすくする。</p>
 */
final class TabMemoryManager {

    /** タブに対する実操作 (Swing 側が実装)。 */
    interface Actions {
        /**
         * 指定キーのタブを閉じる。
         *
         * @return 閉じた (またはキーに対応するタブが既に存在しない) 場合 true。
         *         未保存編集の保護などで閉じられなかった場合 false。
         */
        boolean closeTab(String key);
        /** 指定キーのタブの描画済み SVG を解放する (メモリ節約)。 */
        void releaseRender(String key);
        /** 指定キーのタブが解放済みなら再描画する。 */
        void ensureRendered(String key);
    }

    /** 図タブのキーを使用順で保持 (先頭が最も古い)。 */
    private final LinkedHashSet<String> mru = new LinkedHashSet<>();
    private int maxTabs;
    private int keepRendered;

    TabMemoryManager() {
        this(resolveInt("juml.maxDiagramTabs", 20), resolveInt("juml.renderedTabs", 4));
    }

    /** テスト用: 上限・描画保持数を指定 (いずれも 0 以下で無効)。 */
    TabMemoryManager(int maxTabs, int keepRendered) {
        this.maxTabs = maxTabs;
        this.keepRendered = keepRendered;
    }

    /**
     * Preferences 変更をアプリ再起動なしで反映するための、上限・描画保持数の再設定。
     * 既存の MRU 状態はそのまま保持する (次回の {@link #onActivate} から新しい上限が働く)。
     */
    void configure(int maxTabs, int keepRendered) {
        this.maxTabs = maxTabs;
        this.keepRendered = keepRendered;
    }

    /** タブが閉じられたとき (ユーザー操作含む) に使用順から外す。 */
    void onClose(String key) {
        mru.remove(key);
    }

    /**
     * タブのキーが変わったとき (Save As / メソッド図のその場図種切替) に追従する。
     * 追従しないと旧キーが幽霊として MRU に残り、退避・描画解放の枠を浪費して
     * 実タブのメモリ管理が働かなくなる。
     */
    void rename(String oldKey, String newKey) {
        if (oldKey == null || newKey == null || oldKey.equals(newKey)) {
            return;
        }
        if (mru.remove(oldKey)) {
            mru.remove(newKey);
            mru.add(newKey);
        }
    }

    /**
     * タブを開いた / フォーカスしたときに呼ぶ。MRU を更新し、上限超過タブのクローズと
     * 古いタブの描画解放を {@code actions} 経由で適用する。
     *
     * @param activeKey 現在アクティブなタブのキー
     * @param openCount 現在開いている図タブ数
     */
    void onActivate(String activeKey, int openCount, Actions actions) {
        if (activeKey == null) {
            return;
        }
        mru.remove(activeKey);
        mru.add(activeKey);
        actions.ensureRendered(activeKey);

        int count = openCount;
        // 閉じられなかったタブ (未保存編集の保護等) は候補から外して次の犠牲を探す。
        // カウントは実際に閉じたときだけ減らす。拒否をクローズ扱いにすると帳簿が
        // 実態とずれ、後続のアクティブ化で新しいタブが身代わりに退避されてしまう。
        java.util.Set<String> refused = new java.util.HashSet<>();
        String victim;
        while ((victim = victimExcluding(refused, activeKey, count)) != null) {
            if (actions.closeTab(victim)) {
                mru.remove(victim);
                count--;
            } else {
                refused.add(victim);
            }
        }
        for (String k : LruTabPolicy.keysToRelease(new ArrayList<>(mru), keepRendered)) {
            if (!k.equals(activeKey)) {
                actions.releaseRender(k);
            }
        }
    }

    /** {@code refused} を除いた MRU 候補から次の犠牲キーを選ぶ。 */
    private String victimExcluding(java.util.Set<String> refused, String activeKey, int count) {
        ArrayList<String> candidates = new ArrayList<>(mru);
        candidates.removeAll(refused);
        return LruTabPolicy.victim(candidates, activeKey, count, maxTabs);
    }

    private static int resolveInt(String prop, int def) {
        String v = System.getProperty(prop);
        if (v != null) {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException ignore) {
                // 不正値は既定にフォールバック
            }
        }
        return def;
    }
}
