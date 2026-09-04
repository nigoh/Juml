// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * ラウンド4 の CLI スイープで発見: Java ソースの無いプロジェクトに {@code --list-methods} を
 * 使うと 0 バイトのファイルだけが残り、解析失敗と区別できなかった。理由を stderr に出す。
 */
public class ListMethodsEmptyNoticeTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void emptyCandidateListExplainsItselfOnStderr() throws Exception {
        File project = tmp.newFolder("res-only");
        File res = new File(project, "res/layout");
        assertTrue(res.mkdirs());
        Files.write(new File(res, "main.xml").toPath(),
                "<LinearLayout/>".getBytes(StandardCharsets.UTF_8));
        File out = new File(tmp.getRoot(), "methods.txt");

        PrintStream orig = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            UmlCommands.handleListMethods(new CliContext(project, out,
                    ErrorListener.silent(), null, false, null, false));
        } finally {
            System.setErr(orig);
        }

        assertEquals("候補が無ければ一覧は空のまま", 0L, out.length());
        String err = buf.toString(StandardCharsets.UTF_8);
        assertTrue(err, err.contains("No sequence-diagram entry candidates"));
    }
}
