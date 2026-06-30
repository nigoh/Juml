// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml.db;

import juml.core.formats.uml.db.dao.FilesDao;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DB の {@code files} テーブルと現在のファイルシステムを突き合わせ、
 * 「再パースが必要なファイル」「DB からは消すべきファイル」を仕分けする。
 *
 * <p>ハッシュは取らず、{@code mtime} と {@code size} の組だけで差分を判定する。
 * Gradle / IDE のビルド系ツールが書き戻すたびに mtime が変わるため、
 * これだけで実用的な精度になる。</p>
 */
public final class IncrementalScanner {

    /** 差分判定結果。 */
    public static final class DiffResult {

        private final List<File> added;
        private final List<File> modified;
        private final List<File> unchanged;
        private final List<String> deletedPaths;

        DiffResult(List<File> added, List<File> modified, List<File> unchanged,
                List<String> deletedPaths) {
            this.added = added;
            this.modified = modified;
            this.unchanged = unchanged;
            this.deletedPaths = deletedPaths;
        }

        public List<File> getAdded() {
            return Collections.unmodifiableList(added);
        }

        public List<File> getModified() {
            return Collections.unmodifiableList(modified);
        }

        public List<File> getUnchanged() {
            return Collections.unmodifiableList(unchanged);
        }

        /** DB には残っているが、もうファイルシステム上に存在しない path 一覧 (root 相対)。 */
        public List<String> getDeletedPaths() {
            return Collections.unmodifiableList(deletedPaths);
        }

        /** 再パースすべきファイル ({@code added + modified} の合算、登録順)。 */
        public List<File> getStale() {
            List<File> out = new ArrayList<>(added.size() + modified.size());
            out.addAll(added);
            out.addAll(modified);
            return out;
        }
    }

    private IncrementalScanner() {
    }

    /**
     * 指定 kind のファイルについて、DB と現在 FS を突き合わせて差分を返す。
     *
     * @param conn         接続中の DB
     * @param projectRoot  files.path は root 相対なので canonical root が必要
     * @param kind         {@link IndexWriter#KIND_JAVA} 等
     * @param currentFiles 現在 FS 上に存在するファイル一覧 (走査結果)
     */
    public static DiffResult diff(Connection conn, File projectRoot, String kind,
            List<File> currentFiles) throws SQLException {
        Map<String, FilesDao.FileRow> dbRows = FilesDao.listByKind(conn, kind);
        String rootCanonical = canonical(projectRoot);

        List<File> added = new ArrayList<>();
        List<File> modified = new ArrayList<>();
        List<File> unchanged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (File f : currentFiles) {
            String rel = relativize(rootCanonical, f);
            seen.add(rel);
            FilesDao.FileRow row = dbRows.get(rel);
            long mtime = f.lastModified();
            long size = f.length();
            if (row == null) {
                added.add(f);
            } else if (row.mtime != mtime || row.size != size) {
                modified.add(f);
            } else {
                unchanged.add(f);
            }
        }

        List<String> deleted = new ArrayList<>();
        for (String dbPath : dbRows.keySet()) {
            if (!seen.contains(dbPath)) {
                deleted.add(dbPath);
            }
        }
        return new DiffResult(added, modified, unchanged, deleted);
    }

    /** 同一 kind 用に root 相対パスへ変換する (FilesDao の path カラム形式に揃える)。 */
    public static String relativize(File projectRoot, File source) {
        return relativize(canonical(projectRoot), source);
    }

    private static String relativize(String rootCanonical, File source) {
        String srcPath;
        try {
            srcPath = source.getCanonicalPath();
        } catch (IOException ex) {
            srcPath = source.getAbsolutePath();
        }
        String sep = File.separator;
        if (rootCanonical != null && srcPath.startsWith(rootCanonical + sep)) {
            return srcPath.substring(rootCanonical.length() + sep.length());
        }
        if (rootCanonical != null && srcPath.equals(rootCanonical)) {
            return "";
        }
        return srcPath;
    }

    private static String canonical(File f) {
        if (f == null) {
            return null;
        }
        try {
            return f.getCanonicalPath();
        } catch (IOException ex) {
            return f.getAbsolutePath();
        }
    }

    /** 内部利用: 表示テスト用に DB 側の (path → row) を露出。 */
    static Map<String, FilesDao.FileRow> snapshotDbRows(Connection conn, String kind)
            throws SQLException {
        return new HashMap<>(FilesDao.listByKind(conn, kind));
    }
}
