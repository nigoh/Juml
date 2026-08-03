// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * エディタの保存 (Ctrl+S) が利用者の {@code .puml} を壊さないことの回帰テスト。
 *
 * <p>以前は {@code Files.write} で対象を直接開いていた。開いた瞬間に切り詰めるため、
 * 途中で失敗すると<b>保存操作をしただけでソースが 0 バイトになり、編集中のバッファ以外に
 * どこにも残らない</b>。書き切ってから置換する経路 ({@code AtomicFileWrite}) へ通し、
 * 失敗時に元ファイルが残ることは {@code AtomicFileWriteTest} が保証する。ここでは
 * エディタ側がその経路を通っていること (一時ファイルを残さない・往復する) を固定する。</p>
 */
public class PumlEditorSaveSafetyTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void saveRoundTripsAndLeavesNoTempFile() throws Exception {
        File dir = tmp.newFolder("puml");
        File target = new File(dir, "diagram.puml");
        Files.write(target.toPath(), "@startuml\nclass Old\n@enduml\n"
                .getBytes(StandardCharsets.UTF_8));

        String text = "@startuml\nclass New\n@enduml\n";
        PumlEditorSupport.write(target, text);

        assertEquals(text, PumlEditorSupport.read(target));
        assertEquals("一時ファイルを残さないこと: " + java.util.Arrays.toString(dir.list()),
                1, dir.list().length);
    }

    @Test
    public void saveKeepsSymlinkedSourceAsALink() throws Exception {
        // .puml を公開先へシンボリックリンクしている構成で、保存のたびにリンクが
        // 普通のファイルへ化けないこと。
        File dir = tmp.newFolder("link");
        File real = new File(dir, "real.puml");
        Files.write(real.toPath(), "@startuml\nclass A\n@enduml\n"
                .getBytes(StandardCharsets.UTF_8));
        File link = new File(dir, "edited.puml");
        try {
            Files.createSymbolicLink(link.toPath(), real.toPath());
        } catch (UnsupportedOperationException | java.io.IOException noSymlink) {
            return;
        }

        PumlEditorSupport.write(link, "@startuml\nclass B\n@enduml\n");

        assertTrue("リンクのままであること", Files.isSymbolicLink(link.toPath()));
        assertEquals("実体が更新されること",
                "@startuml\nclass B\n@enduml\n", Files.readString(real.toPath()));
    }

    @Test
    public void saveCreatesMissingParentDirectories() throws Exception {
        // 既存契約: エディタの「名前を付けて保存」は親ディレクトリを作る
        // (エクスポートと違い、利用者がダイアログで明示的に場所を決めている)。
        File target = new File(tmp.getRoot(), "nested/deeper/new.puml");
        PumlEditorSupport.write(target, "@startuml\n@enduml\n");
        assertTrue(target.isFile());
    }
}
