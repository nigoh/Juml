// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 「書き切ってから原子的に置換」の契約を固定する。
 *
 * <p>対象ファイルへ直接書く実装だと、生成途中で失敗したときに<b>以前の正しい出力が
 * 0 バイトや途中までの状態で失われる</b>。上書き確認に「はい」と答えただけで、新しい
 * 内容も古い内容も残らないのは保存操作として受け入れられない。</p>
 */
public class AtomicFileWriteTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void successReplacesTheTarget() throws IOException {
        File target = tmp.newFile("out.txt");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));

        AtomicFileWrite.write(target, os -> os.write("new".getBytes(StandardCharsets.UTF_8)));

        assertEquals("new", Files.readString(target.toPath()));
    }

    @Test
    public void failureLeavesTheExistingFileIntact() throws IOException {
        File target = tmp.newFile("out.txt");
        byte[] original = "important previous export".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), original);

        try {
            AtomicFileWrite.write(target, os -> {
                os.write("partial".getBytes(StandardCharsets.UTF_8));
                throw new IOException("disk full");
            });
            fail("例外が伝播するべき");
        } catch (IOException expected) {
            assertEquals("disk full", expected.getMessage());
        }

        assertArrayEquals("失敗しても前回の内容が残ること",
                original, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void failureLeavesNoTempFileBehind() throws IOException {
        File dir = tmp.newFolder("exports");
        File target = new File(dir, "out.txt");
        try {
            AtomicFileWrite.writeFile(target, f -> {
                throw new IOException("encoder missing");
            });
            fail("例外が伝播するべき");
        } catch (IOException expected) {
            assertEquals("encoder missing", expected.getMessage());
        }
        assertEquals("一時ファイルを残さないこと: " + java.util.Arrays.toString(dir.list()),
                0, dir.list().length);
    }

    @Test
    public void createsANewFileWhenTargetDoesNotExist() throws IOException {
        File target = new File(tmp.newFolder("fresh"), "out.txt");
        AtomicFileWrite.write(target, os -> os.write("hello".getBytes(StandardCharsets.UTF_8)));
        assertTrue(target.isFile());
        assertEquals("hello", Files.readString(target.toPath()));
    }

    @Test
    public void bareRelativeFileNameWritesIntoTheCurrentDirectory() throws IOException {
        // 回帰 (critical): 親要素を持たない相対パス ("out.png" など。CLI の -o で普通に来る) で
        // getParent() が null になり、Files.createTempFile(null, ...) が NPE を投げていた。
        // CLI はスタックトレースだけ吐いて 1 バイトも出力しなかった。
        String name = "juml-atomic-write-probe.txt";
        File target = new File(name);
        assertNull("この検証の前提: 親要素を持たない相対パス", target.toPath().getParent());
        try {
            AtomicFileWrite.write(target, os -> os.write("ok".getBytes(StandardCharsets.UTF_8)));
            assertTrue("カレントディレクトリへ書けること", target.isFile());
            assertEquals("ok", Files.readString(target.toPath()));
        } finally {
            Files.deleteIfExists(target.toPath());
        }
    }

    @Test
    public void existingFilePermissionsAreKept() throws IOException {
        // 回帰: createTempFile は POSIX で必ず 0600 を作り、ATOMIC_MOVE は inode ごと
        // 置換するため権限が引き継がれない。共有ディレクトリや Web ルートへ出した図が
        // 再エクスポート後に所有者しか読めなくなっていた。
        File target = tmp.newFile("shared.svg");
        java.util.Set<PosixFilePermission> perms;
        try {
            perms = PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(target.toPath(), perms);
        } catch (UnsupportedOperationException notPosix) {
            return; // POSIX でないファイルシステムでは検証対象外
        }
        AtomicFileWrite.write(target, os -> os.write("x".getBytes(StandardCharsets.UTF_8)));
        assertEquals("既存ファイルの権限を保つこと",
                perms, Files.getPosixFilePermissions(target.toPath()));
    }

    @Test
    public void restrictivePermissionsAreAlsoKept() throws IOException {
        // 逆向きの保険: 意図的に絞った権限を勝手に緩めないこと。
        File target = tmp.newFile("private.svg");
        java.util.Set<PosixFilePermission> perms;
        try {
            perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(target.toPath(), perms);
        } catch (UnsupportedOperationException notPosix) {
            return;
        }
        AtomicFileWrite.write(target, os -> os.write("x".getBytes(StandardCharsets.UTF_8)));
        assertEquals(perms, Files.getPosixFilePermissions(target.toPath()));
    }

    @Test
    public void newFileIsReadableByDefault() throws IOException {
        // 新規作成は umask 準拠 (従来の FileOutputStream と同じ) であること。
        File target = new File(tmp.newFolder("fresh"), "out.svg");
        AtomicFileWrite.write(target, os -> os.write("x".getBytes(StandardCharsets.UTF_8)));
        try {
            assertTrue("所有者以外にも読めるのが既定 (0600 に固定しない)",
                    Files.getPosixFilePermissions(target.toPath())
                            .contains(PosixFilePermission.GROUP_READ)
                    || Files.getPosixFilePermissions(target.toPath())
                            .contains(PosixFilePermission.OTHERS_READ));
        } catch (UnsupportedOperationException notPosix) {
            assertTrue(target.isFile());
        }
    }

    @Test
    public void veryLongTargetNamesStillWrite() throws IOException {
        // 回帰: 一時名は元の名前に約 20 バイトを足すため、素直に連結すると 255 バイト
        // 上限を超え、長い名前の対象だけ "File name too long" で失敗していた
        // (対象を直接開いていた頃は 255 バイトまで書けた)。図の題材名から生成する
        // エクスポート名は普通に 200 バイトを超える。
        File dir = tmp.newFolder("long");
        for (int len : new int[] {200, 240, 250}) {
            File target = new File(dir, "x".repeat(len - 4) + ".svg");
            AtomicFileWrite.write(target, os -> os.write("ok".getBytes(StandardCharsets.UTF_8)));
            assertTrue(len + " バイトの名前でも書けること", target.isFile());
            assertEquals("ok", Files.readString(target.toPath()));
        }
    }

    @Test
    public void symlinkTargetsAreWrittenThrough() throws IOException {
        // 回帰: ATOMIC_MOVE はリンク自体を差し替えるため、シンボリックリンクへ
        // 書き出すとリンクが普通のファイルに化け、リンク先の実体は古い内容のまま
        // 取り残されていた (公開先へのリンクに出力する運用が静かに壊れる)。
        File dir = tmp.newFolder("sym");
        File real = new File(dir, "real.svg");
        Files.write(real.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        File link = new File(dir, "link.svg");
        try {
            Files.createSymbolicLink(link.toPath(), real.toPath());
        } catch (UnsupportedOperationException | IOException noSymlink) {
            return; // シンボリックリンク非対応の環境では検証対象外
        }

        AtomicFileWrite.write(link, os -> os.write("new".getBytes(StandardCharsets.UTF_8)));

        assertTrue("リンクのままであること", Files.isSymbolicLink(link.toPath()));
        assertEquals("リンク先の実体が更新されること", "new", Files.readString(real.toPath()));
    }

    @Test
    public void symlinkWhoseDestinationDoesNotExistYetIsFollowed() throws IOException {
        // 回帰: toRealPath() は実体が無いリンクで例外になり、リンク自体が置換先に
        // なっていた。ln -s してから初めて書き出す (最も自然な手順) たびに
        // リンクが普通のファイルへ化け、意図した場所には 1 バイトも書かれなかった。
        File dir = tmp.newFolder("dangling");
        File dest = new File(dir, "www/out.svg");
        assertTrue(dest.getParentFile().mkdirs());
        File link = new File(dir, "latest.svg");
        try {
            Files.createSymbolicLink(link.toPath(), dest.toPath());
        } catch (UnsupportedOperationException | IOException noSymlink) {
            return;
        }

        AtomicFileWrite.write(link, os -> os.write("new".getBytes(StandardCharsets.UTF_8)));

        assertTrue("リンクのままであること", Files.isSymbolicLink(link.toPath()));
        assertTrue("リンク先の実体が作られること", dest.isFile());
        assertEquals("new", Files.readString(dest.toPath()));
    }

    @Test
    public void writeProtectedTargetIsNotReplaced() throws IOException {
        // 回帰: 最後の一手が rename(2) なので、カーネルは親ディレクトリの権限しか
        // 見ない。番人が無いと、利用者が chmod 444 で保護したファイルや共有
        // ディレクトリの他人所有のファイルを黙って置き換えてしまっていた
        // (対象を直接開いていた頃は AccessDeniedException で失敗した)。
        File target = tmp.newFile("protected.txt");
        byte[] original = "do not touch".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), original);
        try {
            Files.setPosixFilePermissions(target.toPath(),
                    PosixFilePermissions.fromString("r--r--r--"));
        } catch (UnsupportedOperationException notPosix) {
            return;
        }
        // root は権限検査を素通りするため、この保証は検証できない。
        org.junit.Assume.assumeFalse("root では権限が効かない",
                Files.isWritable(target.toPath()));

        try {
            AtomicFileWrite.write(target, os -> os.write("x".getBytes(StandardCharsets.UTF_8)));
            fail("書き込み不可のファイルは置換しないこと");
        } catch (IOException expected) {
            assertTrue(expected instanceof java.nio.file.AccessDeniedException);
        }
        assertArrayEquals("保護されたファイルが残ること",
                original, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void missingParentDirectoryFailsWithoutCreatingIt() throws IOException {
        // 保存先ディレクトリを勝手に作らない (打ち間違えたパスへ黙って書かない)。
        File dir = new File(tmp.getRoot(), "no-such-dir");
        File target = new File(dir, "out.txt");
        try {
            AtomicFileWrite.write(target, os -> os.write("x".getBytes(StandardCharsets.UTF_8)));
            fail("保存先が無ければ失敗するべき");
        } catch (IOException expected) {
            assertTrue(expected != null);
        }
        assertTrue("ディレクトリは作られないこと", !dir.exists());
    }
}
