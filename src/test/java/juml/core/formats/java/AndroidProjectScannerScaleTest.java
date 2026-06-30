// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.util.CancelToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * AndroidProjectScanner の大規模/打ち切り/キャンセル動作のテスト。
 *
 * <p>10,000 ファイル規模の合成プロジェクトで:</p>
 * <ul>
 *   <li>maxFiles で取り込み上限を超えた時点で打ち切るか</li>
 *   <li>CancelToken で途中中断できるか</li>
 *   <li>useAospDefaults で AOSP 向け除外名が効くか</li>
 * </ul>
 * を検証する。
 */
public class AndroidProjectScannerScaleTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f),
                StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /** 合成プロジェクトを生成: 100 フォルダ × 100 ファイル = 10,000 java ファイル。 */
    private File createLargeProject(int folders, int filesPerFolder) throws IOException {
        File root = tmp.newFolder("BigProj");
        File srcRoot = new File(root, "src/main/java/com/example/big");
        assertTrue(srcRoot.mkdirs());
        for (int i = 0; i < folders; i++) {
            File pkg = new File(srcRoot, "p" + i);
            assertTrue(pkg.mkdirs());
            for (int j = 0; j < filesPerFolder; j++) {
                writeFile(new File(pkg, "C" + j + ".java"),
                        "package com.example.big.p" + i + "; public class C" + j + " {}");
            }
        }
        return root;
    }

    @Test
    public void testMaxFilesTruncatesScan() throws IOException {
        File root = createLargeProject(20, 50); // 1,000 files
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        o.maxFiles = 100;
        List<File> files = AndroidProjectScanner.scan(root, o);
        assertEquals("maxFiles で打ち切られているはず", 100, files.size());
    }

    @Test
    public void testCancelTokenInterruptsScan() throws IOException, InterruptedException {
        File root = createLargeProject(100, 100); // 10,000 files
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        CancelToken cancel = new CancelToken();
        o.cancelToken = cancel;
        // 走査開始前にキャンセル → 0 件で返る
        cancel.cancel();
        List<File> files = AndroidProjectScanner.scan(root, o);
        assertTrue("キャンセル後は 0 件のはず: " + files.size(), files.isEmpty());
    }

    @Test
    public void testAospExtraExcludedDirs() throws IOException {
        File root = tmp.newFolder("AospLike");
        File good = new File(root, "frameworks/base/src/main/java");
        assertTrue(good.mkdirs());
        writeFile(new File(good, "Good.java"), "public class Good {}");
        // prebuilts は AOSP_EXTRA で除外されるべき
        File pre = new File(root, "prebuilts/build-tools/Android.java");
        assertTrue(pre.getParentFile().mkdirs());
        writeFile(pre, "public class Android {}");

        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        // デフォルト除外だけだと prebuilts は対象に含まれる
        List<File> withoutAosp = AndroidProjectScanner.scan(root, o);
        boolean foundPrebuilts = false;
        for (File f : withoutAosp) {
            if (f.getPath().replace(File.separatorChar, '/').contains("/prebuilts/")) {
                foundPrebuilts = true;
            }
        }
        assertTrue("default では prebuilts も拾うはず", foundPrebuilts);

        AndroidProjectScanner.Options o2 = new AndroidProjectScanner.Options();
        o2.useAospDefaults = true;
        List<File> withAosp = AndroidProjectScanner.scan(root, o2);
        for (File f : withAosp) {
            String p = f.getPath().replace(File.separatorChar, '/');
            assertTrue("AOSP モードでは prebuilts は除外: " + p, !p.contains("/prebuilts/"));
        }
    }

    @Test
    public void testLargeScanCompletesQuickly() throws IOException {
        File root = createLargeProject(50, 50); // 2,500 files
        long start = System.currentTimeMillis();
        List<File> files = AndroidProjectScanner.scan(root);
        long elapsed = System.currentTimeMillis() - start;
        assertEquals(2500, files.size());
        assertTrue("2,500 ファイル走査が 10 秒以内に終わるはず (elapsed=" + elapsed + "ms)",
                elapsed < 10_000);
    }
}
