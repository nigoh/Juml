// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt R4 で発見: 解析本体は {@code .kt} も読んで DB に載せるのに、陳腐化チェック用の
 * 走査は既定の {@code includeKotlin=false} で行われていた。その結果 DB の {@code .kt} 行が
 * 毎回「削除された」と判定され、Kotlin を含むプロジェクトではディスクキャッシュが
 * 恒久的にヒットせず、起動のたびにフル解析が走っていた。
 */
public class DiskCacheKotlinHitTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String content) throws IOException {
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static ProjectAnalysisCache.LoadOptions lazyWithDiskCache() {
        ProjectAnalysisCache.LoadOptions o = new ProjectAnalysisCache.LoadOptions();
        o.lazyDetails = true;
        o.useDiskCache = true;
        return o;
    }

    @Test
    public void secondLoadOfAKotlinProjectHitsTheDiskCache() throws Exception {
        File project = tmp.newFolder("kt-project");
        write(new File(project, "src/main/java/x/A.java"), "package x; public class A {}");
        write(new File(project, "src/main/java/x/B.kt"), "package x\nclass B\n");
        DiskAnalysisCache disk = new DiskAnalysisCache(tmp.newFolder("cache-base"));

        ProjectAnalysisCache first = new ProjectAnalysisCache(disk);
        first.load(project, ErrorListener.silent(), null, null, lazyWithDiskCache());
        assertTrue("1 回目でクラスが取れること", first.isLoaded());

        // 2 回目: ファイルは何も変えていないのでキャッシュから復元されるはず。
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ProjectAnalysisCache second = new ProjectAnalysisCache(disk);
        second.load(project, (code, source, line, message) -> warnings.add(String.valueOf(message)),
                null, null, lazyWithDiskCache());
        assertTrue("2 回目もロードできること", second.isLoaded());
        assertTrue("キャッシュから復元されていること (再解析されていない)",
                second.isFromDiskCacheForTest());
        assertFalse("キャッシュ読み込み失敗の警告が出ていないこと: " + warnings,
                warnings.stream().anyMatch(m -> m.contains("cache")));
    }
}
