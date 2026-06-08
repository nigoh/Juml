// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ローディング表示 ({@link SplashWindow} / {@link LoadingGlassPane}) で使う GIF を、
 * 複数候補からランダムに選ぶ小さなファクトリ。
 *
 * <p>候補のうち <b>クラスパス上に実在するものだけ</b>を抽選対象にする。新しい GIF を
 * {@code src/main/resources/images/} に追加すれば自動的に抽選へ加わり、まだ無い場合は
 * 既存の {@code loading.gif} だけが選ばれて従来どおりの見た目になる (リソース欠落で
 * アニメ無しになるのを防ぐ)。</p>
 */
final class LoadingGifs {

    /**
     * 候補となる GIF リソース (クラスパスルート基準)。実在するものだけが抽選対象。
     * 先頭は必ず同梱されている既定の GIF にしておく。
     */
    private static final List<String> CANDIDATES = List.of(
            "/images/loading.gif",
            "/images/loading2.gif");

    private LoadingGifs() {
    }

    /**
     * クラスパス上に実在する候補からランダムに 1 つ選んで返す。
     *
     * <p>1 つも見つからない場合は先頭候補のパスを返す (呼び出し側は URL が
     * null になっても許容する)。</p>
     */
    static String pickResource() {
        List<String> present = new ArrayList<>();
        for (String path : CANDIDATES) {
            if (LoadingGifs.class.getResource(path) != null) {
                present.add(path);
            }
        }
        if (present.isEmpty()) {
            return CANDIDATES.get(0);
        }
        return present.get(ThreadLocalRandom.current().nextInt(present.size()));
    }
}
