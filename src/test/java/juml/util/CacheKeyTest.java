// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * CacheKey の決定性と変更検出のテスト。
 */
public class CacheKeyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File touch(File parent, String name, String content) throws IOException {
        File f = new File(parent, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes());
        }
        return f;
    }

    @Test
    public void testDeterministicForSameInputs() throws IOException {
        File root = tmp.newFolder("p");
        File a = touch(root, "a.txt", "AAA");
        File b = touch(root, "b.txt", "BBB");
        List<File> files = Arrays.asList(a, b);
        String k1 = CacheKey.compute(root, files);
        String k2 = CacheKey.compute(root, files);
        assertEquals(k1, k2);
        assertTrue(k1.length() >= 32);
    }

    @Test
    public void testDifferentOrderProducesDifferentKey() throws IOException {
        File root = tmp.newFolder("p");
        File a = touch(root, "a.txt", "AAA");
        File b = touch(root, "b.txt", "BBB");
        String k1 = CacheKey.compute(root, Arrays.asList(a, b));
        String k2 = CacheKey.compute(root, Arrays.asList(b, a));
        assertNotEquals("順序違いは別キー", k1, k2);
    }

    @Test
    public void testFileSizeChangeChangesKey() throws IOException {
        File root = tmp.newFolder("p");
        File a = touch(root, "a.txt", "AAA");
        String k1 = CacheKey.compute(root, Collections.singletonList(a));
        touch(root, "a.txt", "AAAA"); // サイズ変化
        String k2 = CacheKey.compute(root, Collections.singletonList(a));
        assertNotEquals(k1, k2);
    }

    @Test
    public void testShortIdIsPrefix() {
        String key = "abcdef0123456789FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        assertEquals("abcdef0123456789", CacheKey.shortId(key));
        assertEquals("short", CacheKey.shortId("short"));
    }
}
