// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 旧 TSV キャッシュディレクトリの自動退避。
 *
 * <p>{@code ~/.juml/cache/} 配下に PR3 以前の {@code PersistentAnalysisCache}
 * が書いていた {@code <shortHash>/manifest.txt} + {@code classes.tsv} の組が
 * 残っているとき、それらを {@code .legacy-<timestamp>/} 配下に rename して退避する。
 * 自動削除はしない (ユーザーが手で消せるように)。</p>
 *
 * <p>SQLite ベースの新しい {@code <shortHash>/index.db} を作ろうとしている
 * ディレクトリにも旧 TSV ファイルが居座っていることがあるため、
 * {@link #archiveLegacyDirs(File)} は base ディレクトリ全体を走査して
 * 該当パターンを一括退避する。</p>
 */
public final class LegacyCacheArchiver {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private LegacyCacheArchiver() {
    }

    /**
     * {@code baseDir} 直下のサブディレクトリのうち、旧 TSV キャッシュ
     * (manifest.txt + classes.tsv を持つ) を {@code .legacy-<ts>/} に rename して退避する。
     *
     * @return 退避先のディレクトリ。退避対象が無ければ {@code null}。
     */
    public static File archiveLegacyDirs(File baseDir) throws IOException {
        if (baseDir == null || !baseDir.isDirectory()) {
            return null;
        }
        File[] children = baseDir.listFiles();
        if (children == null) {
            return null;
        }
        File archiveDir = null;
        for (File sub : children) {
            if (!sub.isDirectory()) {
                continue;
            }
            if (!isLegacyTsvDir(sub)) {
                continue;
            }
            if (archiveDir == null) {
                String ts = LocalDateTime.now().format(TS_FMT);
                archiveDir = new File(baseDir, ".legacy-" + ts);
                if (!archiveDir.mkdirs() && !archiveDir.isDirectory()) {
                    throw new IOException("Failed to create archive dir: " + archiveDir);
                }
            }
            File target = new File(archiveDir, sub.getName());
            Files.move(sub.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return archiveDir;
    }

    /** 旧 TSV キャッシュかどうか (manifest.txt + classes.tsv が両方ある)。 */
    public static boolean isLegacyTsvDir(File dir) {
        return new File(dir, "manifest.txt").isFile()
                && new File(dir, "classes.tsv").isFile();
    }
}
