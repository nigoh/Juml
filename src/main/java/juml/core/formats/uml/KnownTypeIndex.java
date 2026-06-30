// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 型参照解決の索引。完全修飾名 (FQN) の集合に加え、各 FQN の
 * 「ドット区切り接尾辞 → FQN」マップを事前計算しておき、
 * {@code k.endsWith("." + name)} の判定を集合全走査 (O(n)) ではなく
 * O(1) で行えるようにする。
 *
 * <p>大きなクラス図では型参照ごとに既知クラス集合を線形走査していたため
 * O(参照数 × クラス数) になっていた。本索引は生成時に 1 度だけ構築し、
 * 以降の照合を定数時間にする。</p>
 *
 * <p>同じ接尾辞を持つ FQN が複数ある場合は <b>辞書順最小</b>を採用する。
 * 旧実装は {@code HashSet} の反復順 (実質不定) で最初の一致を返していたが、
 * 本索引では結果が決定的になり、図出力の再現性が高まる
 * (曖昧な単純名でのみ選ばれる FQN が変わりうるが、一意なケースは不変)。</p>
 */
final class KnownTypeIndex {

    private final Set<String> exact;
    private final Map<String, String> bySuffix;

    KnownTypeIndex(Set<String> known) {
        this.exact = known;
        this.bySuffix = new HashMap<>();
        for (String k : known) {
            // k 内の各ドット位置の「直後から末尾まで」が、k.endsWith("."+tail) を満たす tail。
            for (int i = 0; i < k.length(); i++) {
                if (k.charAt(i) == '.') {
                    String tail = k.substring(i + 1);
                    String prev = bySuffix.get(tail);
                    if (prev == null || k.compareTo(prev) < 0) {
                        bySuffix.put(tail, k);
                    }
                }
            }
        }
    }

    /** 完全修飾名としてそのまま既知か。 */
    boolean containsExact(String name) {
        return exact.contains(name);
    }

    /**
     * {@code k.endsWith("." + name)} を満たす既知 FQN を返す (決定的: 辞書順最小)。
     * 該当が無ければ null。
     */
    String suffixMatch(String name) {
        return bySuffix.get(name);
    }
}
