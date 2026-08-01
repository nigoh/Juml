// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import juml.util.CacheKey;

import java.io.File;

/**
 * SQLite ベースの解析インデックス DB の配置先を決定するヘルパー。
 *
 * <p>配置: {@code ~/.juml/cache/<shortHash>/index.db}</p>
 *
 * <p>{@code shortHash} は <b>プロジェクトルートの canonical path のみ</b>から
 * 計算される ({@link CacheKey#computeRootOnly(File)})。
 * 既存の {@code CacheKey.compute(root, files)} はファイル差分でハッシュが変わる
 * 仕様だったが、永続インデックスでは「1 ファイル変えるたびに別ディレクトリ」に
 * なってしまい増分更新が成り立たないため、ここではルートだけからキーを作る。
 * ファイル差分は DB 内の {@code files.mtime/size} で検知する。</p>
 */
public final class DbBootstrap {

    public static final String DB_FILENAME = "index.db";

    private DbBootstrap() {
    }

    /** {@code ~/.juml/cache} 等、OS に応じたキャッシュベースディレクトリ。 */
    public static File defaultBaseDir() {
        // 設定・ログ・下書きと同じユーザー単位の親ディレクトリ配下に置く (単一の解決元)。
        return new File(juml.util.PathUtil.getUserDataDir(), "cache");
    }

    /** 指定 root に対するキャッシュサブディレクトリ ({@code <base>/<shortHash>}) を返す。 */
    public static File resolveCacheDir(File baseDir, File projectRoot) {
        String key = CacheKey.computeRootOnly(projectRoot);
        return new File(baseDir, CacheKey.shortId(key));
    }

    /** {@code ~/.juml/cache/<shortHash>} を返す (デフォルト base 配下)。 */
    public static File resolveCacheDir(File projectRoot) {
        return resolveCacheDir(defaultBaseDir(), projectRoot);
    }

    /** {@code <cacheDir>/index.db} のフルパスを返す。 */
    public static File resolveDbFile(File baseDir, File projectRoot) {
        return new File(resolveCacheDir(baseDir, projectRoot), DB_FILENAME);
    }

    /** デフォルト base での {@code <cacheDir>/index.db} を返す。 */
    public static File resolveDbFile(File projectRoot) {
        return new File(resolveCacheDir(projectRoot), DB_FILENAME);
    }
}
