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

import static org.junit.Assert.assertTrue;

/**
 * {@code --nav-graph} CLI 導線のテスト。エンジン (parser / diagram) 自体は
 * それぞれの単体テストで担保済みなので、ここでは CLI ハンドラが入力 (単一 XML /
 * ディレクトリ) を解析して PlantUML を標準出力へ流すことを検証する。
 */
public class NavGraphCommandTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final File SAMPLES =
            new File("src/test/resources/samples/navigation");

    /** stdout を捕捉して handleNavGraph を実行し、出力 PlantUML を返す。 */
    private String runNavGraph(File in) throws Exception {
        CliContext ctx = new CliContext(in, null, ErrorListener.silent(),
                null, false, null);
        PrintStream orig = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, "UTF-8"));
        try {
            AndroidCommands.handleNavGraph(ctx);
        } finally {
            System.setOut(orig);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    public void singleXmlEmitsNavigationDiagram() throws Exception {
        String puml = runNavGraph(new File(SAMPLES, "full_nav.xml"));
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        // destination とアクション (画面遷移) が出ること
        assertTrue(puml, puml.contains("<<fragment>>"));
        assertTrue(puml, puml.contains("action_home_to_detail"));
    }

    @Test
    public void directoryWithoutNavigationGraphsEmitsPlaceholderInsteadOfExiting() throws Exception {
        // 他の Android 系オプションと同じく「該当データなし」は失敗ではない: 以前は System.exit(1)
        // していたため、CI でオプションを並べて回すとこのステップだけ落ちていた。
        File empty = tmp.newFolder("no-nav");
        PrintStream origErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, "UTF-8"));
        String puml;
        try {
            puml = runNavGraph(empty);
        } finally {
            System.setErr(origErr);
        }
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("no navigation graphs found"));
        String stderr = new String(err.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(stderr, stderr.contains("No Jetpack Navigation graphs"));
    }

    @Test
    public void directoryScansNavigationResources() throws Exception {
        // ディレクトリ入力で res/navigation 配下の *.xml を走査して図化する
        String puml = runNavGraph(SAMPLES);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("<<fragment>>"));
    }
}
