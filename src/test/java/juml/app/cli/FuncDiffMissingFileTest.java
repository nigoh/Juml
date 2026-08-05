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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code --func-diff} に読めないファイルを渡したときの案内の回帰テスト。
 *
 * <p>以前は {@code AndroidProjectScanner.readFile} の {@code FileNotFoundException} が
 * {@code handleFuncDiff} を素通りして {@code main} まで抜け、利用者には
 * {@code Exception in thread "main"} と内部フレームの羅列が出ていた。同じコマンドの
 * 「メソッドが見つからない」は既に 1 行で案内しているので扱いを揃える。</p>
 *
 * <p>本番経路は案内のあと {@code System.exit(1)} するためテストから直接は叩けない。
 * そこで終了を伴わない {@link AnalysisCommands#readSide} を検証する
 * (呼び出し側は null を受けて exit するだけなので、案内と戻り値がここでの本質)。</p>
 */
public class FuncDiffMissingFileTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    /** stderr を捕捉して readSide を実行する。 */
    private static String[] runCapturingErr(String path, String rawSpec) throws Exception {
        PrintStream orig = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buf, true, "UTF-8"));
        List<juml.core.formats.uml.JavaClassInfo> result;
        try {
            result = AnalysisCommands.readSide(path, rawSpec, ErrorListener.silent());
        } finally {
            System.setErr(orig);
        }
        return new String[]{
                new String(buf.toByteArray(), StandardCharsets.UTF_8),
                result == null ? null : String.valueOf(result.size()),
        };
    }

    @Test
    public void missingSourceIsReportedAsOneLineNotAStackTrace() throws Exception {
        File missing = new File(tmp.getRoot(), "NoSuch.java");
        String spec = missing.getPath() + "::A.b";

        String[] out = runCapturingErr(missing.getPath(), spec);
        String err = out[0];

        assertNull("読めなければ null を返して呼び出し側に終了を委ねること", out[1]);
        assertTrue("読めなかったパスを名指しすること: " + err,
                err.contains("--func-diff: cannot read"));
        assertTrue("どの指定に由来するかを示すこと: " + err, err.contains(spec));
        assertFalse("スタックトレースを出さないこと: " + err,
                err.contains("\tat java.base/") || err.contains("Exception in thread"));
    }

    @Test
    public void readableSourceStillParses() throws Exception {
        // 非退行: 読めるファイルは従来どおり解析されること。
        File src = new File(tmp.getRoot(), "Alpha.java");
        Files.write(src.toPath(),
                "package com.example;\npublic class Alpha { void go() { } }\n"
                        .getBytes(StandardCharsets.UTF_8));

        List<juml.core.formats.uml.JavaClassInfo> classes =
                AnalysisCommands.readSide(src.getPath(), src.getPath() + "::Alpha.go",
                        ErrorListener.silent());

        assertNotNull(classes);
        assertEquals(1, classes.size());
        assertEquals("Alpha", classes.get(0).getSimpleName());
    }
}
