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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
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
