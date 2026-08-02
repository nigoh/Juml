// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        Path targetPath = target.toPath();
        Path dir = targetPath.getParent();
        // 親ディレクトリは<b>作らない</b>。存在しなければ一時ファイルの作成が
        // IOException になり、呼び出し側は従来 (対象を直接開いていた頃) と同じく
        // 「保存先が無い」として失敗を報告する。勝手に mkdir すると、打ち間違えた
        // パスへ黙って書き出してしまう (BulkTabExporter の契約もこれに依存)。
        // 同一ディレクトリに作る (別ボリュームだと原子的な置換ができないため)。
        Path tmp = Files.createTempFile(dir, ".juml-", ".tmp");
        boolean moved = false;
        try {
            body.writeTo(tmp.toFile());
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
