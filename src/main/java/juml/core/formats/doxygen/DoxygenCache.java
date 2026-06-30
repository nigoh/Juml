// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * doxygen の XML 出力をプロジェクト単位で永続キャッシュするためのユーティリティ。
 *
 * <p>doxygen の実行は重いので、対象プロジェクトの {@code *.java} に変更が無ければ前回生成した
 * XML を再利用し、{@link DoxygenXmlParser} で再パースするだけにする (パースは軽い)。
 * 配置は既存のキャッシュ規約に合わせて {@code ~/.juml/cache/doxygen/<projectHash>/} とする
 * ({@link juml.app.uml.DiskAnalysisCache} と同じ {@code ~/.juml/cache} ルート配下)。</p>
 *
 * <p>鮮度判定は「{@code *.java} のファイル数 + 最終更新時刻の最大値 + doxygen バイナリパス」を
 * 連結した署名で行う。署名が一致すれば再実行をスキップする。</p>
 */
public final class DoxygenCache {

    /** 署名計算でスキップするディレクトリ名 (doxygen の EXCLUDE_PATTERNS と整合)。 */
    private static final java.util.Set<String> SKIP_DIRS =
            java.util.Set.of("build", ".gradle", "test", "generated", ".git", "node_modules");

    private DoxygenCache() {
    }

    /** {@code ~/.juml/cache/doxygen/<projectHash>} を返す。 */
    public static File cacheDirFor(File projectRoot) {
        File base = new File(new File(System.getProperty("user.home", "."), ".juml/cache"), "doxygen");
        return new File(base, shortHash(projectRoot));
    }

    /** プロジェクト内 {@code *.java} の (件数, 最終更新最大値) + doxygen パスから署名を作る。 */
    public static String signature(File projectRoot, String doxygenPath) {
        long count = 0;
        long maxMtime = 0;
        Deque<File> stack = new ArrayDeque<>();
        stack.push(projectRoot);
        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] children = dir.listFiles();
            if (children == null) {
                continue;
            }
            for (File f : children) {
                if (f.isDirectory()) {
                    String n = f.getName();
                    if (!n.startsWith(".") && !SKIP_DIRS.contains(n)) {
                        stack.push(f);
                    }
                } else if (f.getName().endsWith(".java")) {
                    count++;
                    maxMtime = Math.max(maxMtime, f.lastModified());
                }
            }
        }
        return count + ":" + maxMtime + ":" + (doxygenPath != null ? doxygenPath : "");
    }

    /** キャッシュが指定署名に対して有効か (stamp 一致かつ {@code xml/index.xml} 実在)。 */
    public static boolean isFresh(File cacheDir, String signature) {
        File stamp = new File(cacheDir, "stamp.txt");
        File index = new File(new File(cacheDir, "xml"), "index.xml");
        if (!stamp.isFile() || !index.isFile()) {
            return false;
        }
        try {
            return signature.equals(Files.readString(stamp.toPath(), StandardCharsets.UTF_8).trim());
        } catch (IOException ex) {
            return false;
        }
    }

    /** 署名を stamp.txt に書き込む (XML 生成後に呼ぶ)。 */
    public static void writeStamp(File cacheDir, String signature) throws IOException {
        Files.createDirectories(cacheDir.toPath());
        Files.writeString(new File(cacheDir, "stamp.txt").toPath(), signature, StandardCharsets.UTF_8);
    }

    /** プロジェクトルートの正規化パスから安定した短いハッシュ (SHA-1 先頭 16 桁) を作る。 */
    static String shortHash(File projectRoot) {
        String key;
        try {
            key = projectRoot.getCanonicalPath();
        } catch (IOException ex) {
            key = projectRoot.getAbsolutePath();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(key.hashCode());
        }
    }
}
