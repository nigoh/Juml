// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;

/**
 * 既存ファイルを壊さずに書き換えるための「一時ファイル → 原子的置換」ヘルパ。
 *
 * <p>{@code new FileOutputStream(target)} や {@code ImageIO.write(img, fmt, target)} は
 * 開いた瞬間に対象を切り詰める。途中で失敗する (ディスク満杯・権限変更・エンコーダ不在・
 * 生成側の例外) と、<b>利用者が以前エクスポートした正しいファイルが 0 バイトや途中までの
 * 状態で失われる</b>。上書き確認で「はい」を押しただけで前のファイルが消えるのは、
 * 保存操作として受け入れられない。</p>
 *
 * <p>そこで同じディレクトリの一時ファイルへ書き切ってから置換する。失敗時は一時ファイルを
 * 消して例外を投げるだけで、対象ファイルには一切触れない。</p>
 */
public final class AtomicFileWrite {

    private AtomicFileWrite() {
    }

    /** 一時ファイルへの書き出し本体 (成功したら原子的に置換される)。 */
    @FunctionalInterface
    public interface Writer {
        /** {@code out} へ内容を書き切る。例外を投げれば対象ファイルは元のまま残る。 */
        void writeTo(OutputStream out) throws IOException;
    }

    /** 一時ファイル自体を受け取って書き出す形 ({@code ImageIO.write} など File API 用)。 */
    @FunctionalInterface
    public interface FileWriter {
        /** {@code tempFile} へ内容を書き切る。例外を投げれば対象ファイルは元のまま残る。 */
        void writeTo(File tempFile) throws IOException;
    }

    /** {@code target} を、書き切ってから原子的に置き換える ({@link OutputStream} 版)。 */
    public static void write(File target, Writer body) throws IOException {
        writeFile(target, tmp -> {
            try (OutputStream os = Files.newOutputStream(tmp.toPath())) {
                body.writeTo(os);
            }
        });
    }

    /** {@code target} を、書き切ってから原子的に置き換える ({@link File} 版)。 */
    public static void writeFile(File target, FileWriter body) throws IOException {
        if (target == null) {
            throw new IllegalArgumentException("target is null");
        }
        Path targetPath = resolveLink(target.toPath());
        // 親要素を持たない相対パス ("out.png" など。CLI の -o で普通に来る) では
        // getParent() が null になる。null を Files.createTempFile へ渡すと
        // NullPointerException になり、CLI がスタックトレースだけ吐いて 1 バイトも
        // 出力しない。カレントディレクトリとして扱う。
        Path dir = targetPath.getParent() != null
                ? targetPath.getParent() : Paths.get(".");
        // 親ディレクトリは<b>作らない</b>。存在しなければ一時ファイルの作成が
        // IOException になり、呼び出し側は従来 (対象を直接開いていた頃) と同じく
        // 「保存先が無い」として失敗を報告する。勝手に mkdir すると、打ち間違えた
        // パスへ黙って書き出してしまう (BulkTabExporter の契約もこれに依存)。
        // 同一ディレクトリに作る (別ボリュームだと原子的な置換ができないため)。
        Path tmp = createTempIn(dir, targetPath.getFileName().toString());
        boolean moved = false;
        try {
            body.writeTo(tmp.toFile());
            adoptPermissions(tmp, targetPath);
            replace(tmp, targetPath);
            moved = true;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 一時ファイルの後始末失敗は本来の失敗を隠さない。
                }
            }
        }
    }

    /**
     * 対象がシンボリックリンクなら、その実体パスへ解決する。
     *
     * <p>{@code ATOMIC_MOVE} はリンク自体を差し替えてしまうため、これをしないと
     * <b>リンクが普通のファイルに化け、リンク先の実体は古い内容のまま取り残される</b>。
     * 従来の {@code FileOutputStream} はリンクを辿って実体へ書いていたので、
     * 「公開先へのシンボリックリンクに書き出す」という運用が静かに壊れる。
     * 壊れたリンク (実体が無い) は解決できないので、そのまま扱う。</p>
     */
    private static Path resolveLink(Path target) {
        try {
            return Files.isSymbolicLink(target) ? target.toRealPath() : target;
        } catch (IOException dangling) {
            return target;
        }
    }

    /** 一時ファイル名の連番 (同一プロセス内の衝突を避ける)。 */
    private static final java.util.concurrent.atomic.AtomicLong SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    /** 多くのファイルシステム (ext4/XFS/APFS) の 1 要素あたり上限。 */
    private static final int MAX_NAME_BYTES = 255;

    /**
     * 一時ファイル名が上限に収まるよう、元の名前部分を末尾から削る。
     *
     * <p>一時名は元の名前に約 20 バイトを足すので、素直に連結すると
     * <b>長い名前の対象がここでだけ「File name too long」で失敗する</b>
     * (対象を直接開いていた頃は 255 バイトまで書けた)。一時名の一意性は
     * pid + 連番が担保するので、可読性のための元名は削って構わない。</p>
     */
    private static String trimToFit(String baseName, int maxBytes) {
        String s = baseName;
        while (s.length() > 0
                && s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes) {
            // サロゲートペアを割らないよう 1 コードポイントずつ削る。
            s = s.substring(0, s.offsetByCodePoints(s.length(), -1));
        }
        return s;
    }

    /**
     * {@code dir} に一時ファイルを作る。
     *
     * <p>{@link Files#createTempFile} を使わないのは、POSIX では必ず {@code rw-------}
     * (0600) で作られるため。{@code ATOMIC_MOVE} は inode ごと置換するので、その権限が
     * そのまま成果物の権限になり、<b>共有ディレクトリや Web ルートへ書き出した図が
     * 所有者以外から読めなくなる</b> (従来の {@code FileOutputStream} は umask 準拠の
     * 0644 だった)。{@link Files#createFile} なら umask が効く。</p>
     */
    private static Path createTempIn(Path dir, String baseName) throws IOException {
        for (int attempt = 0; attempt < 100; attempt++) {
            String suffix = ".juml-" + ProcessHandle.current().pid()
                    + "-" + SEQ.incrementAndGet() + ".tmp";
            Path candidate = dir.resolve(
                    "." + trimToFit(baseName, MAX_NAME_BYTES - 1 - suffix.length()) + suffix);
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException retry) {
                // 別プロセス/スレッドと衝突。次の名前で作り直す。
                continue;
            }
        }
        throw new IOException("could not create a temporary file in " + dir);
    }

    /**
     * 置換先が既にあるなら、その権限を一時ファイルへ写す。
     *
     * <p>{@code ATOMIC_MOVE} は権限を引き継がないため、これをしないと再エクスポートの
     * たびに元ファイルの権限 (共有用の 0644、あるいは意図的に絞った 0600) が
     * 作成時の既定へ書き換わってしまう。POSIX でないファイルシステムでは何もしない。</p>
     */
    private static void adoptPermissions(Path tmp, Path target) {
        try {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            java.util.Set<PosixFilePermission> perms =
                    Files.getPosixFilePermissions(target);
            Files.setPosixFilePermissions(tmp, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // POSIX 非対応 / 権限取得不可なら既定のまま置換する (書き出し自体は続行)。
        }
    }

    private static void replace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            // ファイルシステムが原子的 move を持たない場合の縮退 (それでも「書き切ってから
            // 置換」なので、生成に失敗した内容で上書きされることはない)。
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
