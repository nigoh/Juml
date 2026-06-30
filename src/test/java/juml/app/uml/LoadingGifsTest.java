// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link LoadingGifs} のテスト。GUI を起動しないヘッドレス安全な検証のみ行う。
 */
public class LoadingGifsTest {

    /** 抽選で返るパスは必ずクラスパス上に実在する (= GIF アニメが必ず再生できる)。 */
    @Test
    public void pickResource_returnsAnExistingClasspathResource() {
        for (int i = 0; i < 50; i++) {
            String path = LoadingGifs.pickResource();
            assertNotNull("pickResource は null を返さない", path);
            assertNotNull(
                    "返したリソース " + path + " はクラスパス上に存在するべき",
                    LoadingGifs.class.getResource(path));
        }
    }

    /** 既定の loading.gif は同梱されているので、必ず抽選候補に含まれる。 */
    @Test
    public void pickResource_includesDefaultLoadingGif() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(LoadingGifs.pickResource());
        }
        // 同梱が保証される既定 GIF は (単独候補でも複数候補でも) 必ず選ばれうる。
        assertTrue(
                "既定の /images/loading.gif が抽選結果に現れるべき: " + seen,
                seen.contains("/images/loading.gif"));
    }

    /** loading2.gif が追加されている環境では、両方が抽選されうる (ランダム切替が働く)。 */
    @Test
    public void pickResource_whenSecondGifPresent_eventuallyPicksBoth() {
        List<String> candidates = List.of("/images/loading.gif", "/images/loading2.gif");
        long present = candidates.stream()
                .filter(p -> LoadingGifs.class.getResource(p) != null)
                .count();
        if (present < 2) {
            // loading2.gif 未追加の環境ではスキップ (従来どおり loading.gif のみ)。
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(LoadingGifs.pickResource());
        }
        assertTrue("両方の GIF が抽選されるべき: " + seen, seen.containsAll(candidates));
    }
}
