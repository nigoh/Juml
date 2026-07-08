// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import brut.androlib.ApkDecoder;
import brut.androlib.Config;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 同梱の Apktool ({@code apktool-lib}) を JVM 内で直接呼び出し、APK ファイルを
 * {@link ApktoolDecodedAnalyzer} が読めるディレクトリへ逆コンパイルする薄いラッパー。
 *
 * <p>ユーザが事前に {@code apktool d app.apk} を実行しなくても、Juml に {@code .apk} を
 * 渡すだけで内部展開できるようにする。{@code brut.androlib.ApkDecoder} を呼ぶだけで
 * 外部プロセス (aapt 等) の起動やネットワークアクセスは行わない (decode は純 Java 実装)。
 * Juml の「ローカルファイルを読むだけ」という安全方針を保ったまま、APK を入力に加える。</p>
 *
 * <p>抽出範囲は smali (クラス図用) と AndroidManifest.xml (テキスト化) に絞り、res/assets の
 * フル復号はスキップして高速化する。{@code apktool.yml} は常に書き出される。</p>
 */
public final class ApktoolDecoder {

    /**
     * {@code brut.androlib.Config} に渡すバージョン文字列。{@code apktool.yml} の
     * {@code apktoolVersion} メタとして書き出され、{@link ApkSummaryReport} が
     * 「apktool version」として表示するだけの情報項目 (デコード挙動には影響しない)。
     * 同梱している {@code org.apktool:apktool-lib} の version に合わせる。
     */
    private static final String APKTOOL_VERSION = "3.0.2";

    private ApktoolDecoder() {
    }

    /** 入力が {@code .apk} ファイルらしいか (拡張子判定)。 */
    public static boolean looksLikeApk(File f) {
        return f != null && f.isFile()
                && f.getName().toLowerCase().endsWith(".apk");
    }

    /**
     * APK を一時ディレクトリへ逆コンパイルし、その出力ディレクトリを返す。
     * 呼び出し側はこのディレクトリを {@link ApktoolDecodedAnalyzer} に渡せる。
     */
    public static File decodeToTempDir(File apk, ErrorListener listener) throws IOException {
        File outDir = Files.createTempDirectory("juml-apk-").toFile();
        // 実行中は解析に使うため即削除できない。JVM 終了時に再帰削除して /tmp に
        // juml-apk-* を溜めない (deleteOnExit は中身の詰まったディレクトリを消せない)。
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(outDir)));
        decode(apk, outDir, listener);
        return outDir;
    }

    /** ディレクトリを再帰削除する (ベストエフォート)。 */
    private static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(dir.toPath())) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // ベストエフォート
                }
            });
        } catch (IOException ignored) {
            // ベストエフォート (既に削除済み等)
        }
    }

    /**
     * APK を指定ディレクトリへ逆コンパイルする。
     *
     * @param apk      入力 APK ファイル
     * @param outDir   出力先ディレクトリ (既存でも上書きする)
     * @param listener 進捗・注意点の通知先 (null なら silent)
     * @throws IOException 入力が無い場合や Apktool が失敗した場合 (Apktool 由来の例外も正規化)
     */
    public static void decode(File apk, File outDir, ErrorListener listener) throws IOException {
        if (apk == null || !apk.isFile()) {
            throw new IOException("APK file not found: " + apk);
        }
        ErrorListener log = listener != null ? listener : ErrorListener.silent();
        // Apktool は java.util.logging で INFO ログを出すので、既定では抑制する。
        Logger.getLogger("brut").setLevel(Level.SEVERE);
        try {
            // Apktool 3.0.x で Config API が刷新された (定数 int → enum、
            // getDefaultConfig() 廃止、ApkDecoder の引数順が (File, Config) に)。
            Config config = new Config(APKTOOL_VERSION);
            config.setForced(true); // 既存の outDir があっても上書きする (旧 forceDelete)
            // dex を全て smali へ復号する (クラス図の入力)。旧 DECODE_SOURCES_SMALI 相当。
            config.setDecodeSources(Config.DecodeSources.FULL);
            // res/ のフル復号はスキップしつつ AndroidManifest.xml だけは可読 XML に復号する。
            // 旧 (DECODE_RESOURCES_NONE + FORCE_DECODE_MANIFEST_FULL) の意図を ONLY_MANIFEST が担う
            // (NONE だとマニフェストがバイナリのまま raw コピーされ可読化されない)。
            config.setDecodeResources(Config.DecodeResources.ONLY_MANIFEST);
            config.setDecodeAssets(Config.DecodeAssets.NONE);
            ApkDecoder decoder = new ApkDecoder(apk, config);
            decoder.decode(outDir);
            log.onError(apk.getName(), -1, "decoded APK into " + outDir.getPath());
        } catch (Exception ex) {
            // AndrolibException / DirectoryException など Apktool 由来の検査例外を正規化する。
            // (Apktool 3.0.x の ApkDecoder.decode は AndrolibException のみを送出する)
            throw new IOException("apktool failed to decode " + apk.getName()
                    + ": " + ex.getMessage(), ex);
        }
    }
}
