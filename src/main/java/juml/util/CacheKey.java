// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * プロジェクトルート + 対象ファイル群の {@code (path, mtime, size)} を SHA-256 化して
 * ディスクキャッシュのキーとして使う文字列を生成する。
 *
 * <p>ファイルが 1 件でも追加・更新・サイズ変更されたらキーが変わるので、
 * 永続キャッシュの自動無効化に利用できる。先頭 16 hex 文字 (64bit 相当) を
 * 短縮 ID として返す API も提供する。</p>
 */
public final class CacheKey {

    private CacheKey() {
    }

    /**
     * プロジェクトルートと対象ファイル列からキャッシュキー (64 hex chars) を計算する。
     *
     * <p>files は走査済みの安定した順序 (ソート済み) で渡すこと。順序が変わると
     * キーも変わるので、呼び出し側で必ずソートしてから渡す。</p>
     */
    public static String compute(File projectRoot, List<File> files) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String rootCanonical;
            try {
                rootCanonical = projectRoot.getCanonicalPath();
            } catch (IOException ex) {
                rootCanonical = projectRoot.getAbsolutePath();
            }
            md.update(rootCanonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            md.update((byte) 0);
            for (File f : files) {
                String rel;
                try {
                    String fp = f.getCanonicalPath();
                    rel = fp.startsWith(rootCanonical)
                            ? fp.substring(rootCanonical.length()) : fp;
                } catch (IOException ex) {
                    rel = f.getAbsolutePath();
                }
                long mtime = 0L;
                long size = 0L;
                try {
                    Path p = f.toPath();
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    mtime = attrs.lastModifiedTime().toMillis();
                    size = attrs.size();
                } catch (IOException ignore) {
                    // 読めなければ 0 のままにしておく
                }
                md.update(rel.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                md.update((byte) 0);
                md.update(longBytes(mtime));
                md.update(longBytes(size));
            }
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /**
     * プロジェクトルートの canonical path だけからキーを計算する。
     *
     * <p>files を含めないため、走査対象が増減・更新されても同じディレクトリが
     * 使い回される。SQLite ベースの永続インデックスのように、ファイル差分は
     * DB 内 (files.mtime/size) で検知する場合に使う。</p>
     */
    public static String computeRootOnly(File projectRoot) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String rootCanonical;
            try {
                rootCanonical = projectRoot.getCanonicalPath();
            } catch (IOException ex) {
                rootCanonical = projectRoot.getAbsolutePath();
            }
            md.update(rootCanonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    /** 先頭 16 hex char (64bit 相当) の短縮 ID。ディレクトリ名向き。 */
    public static String shortId(String fullKey) {
        if (fullKey == null || fullKey.length() < 16) {
            return fullKey;
        }
        return fullKey.substring(0, 16);
    }

    private static byte[] longBytes(long v) {
        byte[] b = new byte[8];
        for (int i = 0; i < 8; i++) {
            b[i] = (byte) (v >>> (56 - 8 * i));
        }
        return b;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
