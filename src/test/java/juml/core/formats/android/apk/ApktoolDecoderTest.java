// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.util.ErrorListener;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link ApktoolDecoder} の検証。
 *
 * <p>有効な APK の生成にはサンドボックスに無い Android ビルドツール (aapt2) が要るため、
 * 完全な end-to-end (実 APK の逆コンパイル) は {@code sample.apk} を置いたときだけ走る
 * gated テストにしている。それ以外は入力検証・エラー正規化・Apktool 配線を検証する。</p>
 */
public class ApktoolDecoderTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void looksLikeApkChecksExtension() throws IOException {
        File apk = tmp.newFile("x.apk");
        File txt = tmp.newFile("x.txt");
        assertTrue(ApktoolDecoder.looksLikeApk(apk));
        assertFalse(ApktoolDecoder.looksLikeApk(txt));
        assertFalse(ApktoolDecoder.looksLikeApk(null));
        assertFalse(ApktoolDecoder.looksLikeApk(tmp.getRoot())); // ディレクトリ
    }

    @Test
    public void missingFileThrows() {
        try {
            ApktoolDecoder.decode(new File(tmp.getRoot(), "nope.apk"),
                    tmp.getRoot(), ErrorListener.silent());
            fail("expected IOException for missing APK");
        } catch (IOException ex) {
            assertTrue(ex.getMessage().contains("not found"));
        }
    }

    /**
     * 非 ZIP の {@code .apk} を渡すと Apktool が実際に呼ばれて失敗し、その検査例外が
     * {@link IOException} に正規化されることを確認する (= apktool-lib が classpath にあり
     * decode 経路が結線されている証拠)。
     */
    @Test
    public void invalidApkIsNormalizedToIoException() throws IOException {
        File bogus = tmp.newFile("broken.apk");
        Files.write(bogus.toPath(), "not a real apk".getBytes(StandardCharsets.UTF_8));
        File out = tmp.newFolder("out");
        try {
            ApktoolDecoder.decode(bogus, out, ErrorListener.silent());
            fail("expected IOException for an invalid APK");
        } catch (IOException ex) {
            assertTrue("message should reference apktool decode failure: " + ex.getMessage(),
                    ex.getMessage().contains("apktool failed to decode"));
        }
    }

    /**
     * 実 APK での end-to-end。{@code src/test/resources/samples/apk/sample.apk} が
     * 存在するときだけ走る (CI/開発者が任意の APK を置けば自動で有効化)。
     */
    @Test
    public void realApkDecodesAndAnalyzes() throws IOException {
        File apk = new File("src/test/resources/samples/apk/sample.apk");
        Assume.assumeTrue("place a sample.apk to exercise real decode", apk.isFile());
        File decoded = ApktoolDecoder.decodeToTempDir(apk, ErrorListener.silent());
        assertTrue(new File(decoded, "apktool.yml").isFile());
        assertTrue(ApktoolDecodedAnalyzer.isApktoolDecodedDir(decoded));
        ApkAnalysis a = ApktoolDecodedAnalyzer.analyze(decoded, ErrorListener.silent());
        assertTrue("decoded APK should yield smali classes", a.classCount() > 0);
    }
}
