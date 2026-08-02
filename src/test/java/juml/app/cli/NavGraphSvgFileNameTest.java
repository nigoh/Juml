// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code --nav-graph} を SVG へ書き出すとき、同名のグラフが互いを上書きしないことの回帰テスト。
 *
 * <p>ナビゲーショングラフのファイル名はモジュール間で重複する (どのモジュールにも
 * {@code res/navigation/nav_graph.xml} がある構成は普通)。ラベルをそのまま出力名に
 * していたため、後のグラフが前のグラフを黙って上書きし、<b>「N 個書き出した」と
 * 表示しながら実際には数個しか残らない</b>状態だった。</p>
 */
public class NavGraphSvgFileNameTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final String NAV_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
            + "    android:id=\"@+id/%s\" app:startDestination=\"@id/home\">\n"
            + "  <fragment android:id=\"@+id/home\" android:name=\"com.x.HomeFragment\"\n"
            + "      android:label=\"Home\" />\n"
            + "</navigation>\n";

    /** 同名 nav_graph.xml を 2 モジュールに持つプロジェクトを作る。 */
    private File projectWithDuplicateGraphNames() throws Exception {
        File root = tmp.newFolder("proj");
        for (String module : new String[] {"app", "feature"}) {
            File navDir = new File(root, module + "/src/main/res/navigation");
            assertTrue(navDir.mkdirs());
            Files.write(new File(navDir, "nav_graph.xml").toPath(),
                    String.format(NAV_XML, module + "_graph").getBytes(StandardCharsets.UTF_8));
        }
        return root;
    }

    private void runNavGraphToSvgDir(File in, File outDir) throws Exception {
        CliContext ctx = new CliContext(in, outDir, ErrorListener.silent(), null, false, null);
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        System.setOut(new PrintStream(java.io.OutputStream.nullOutputStream(), true, "UTF-8"));
        System.setErr(new PrintStream(java.io.OutputStream.nullOutputStream(), true, "UTF-8"));
        try {
            AndroidCommands.handleNavGraph(ctx);
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
    }

    @Test
    public void sameNamedGraphsGetDistinctSvgFiles() throws Exception {
        File outDir = tmp.newFolder("out");
        runNavGraphToSvgDir(projectWithDuplicateGraphNames(), outDir);

        List<String> names = Arrays.asList(outDir.list());
        assertEquals("同名グラフでも 2 ファイル残ること: " + names, 2, names.size());
        assertTrue("素の名前のファイルがあること: " + names, names.contains("nav_graph.svg"));
        assertTrue("2 つ目は連番付きになること: " + names, names.contains("nav_graph_2.svg"));
        for (String n : names) {
            assertTrue(n + " が空でないこと", new File(outDir, n).length() > 0);
        }
    }
}
