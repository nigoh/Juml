// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link CliOutput} の {@code -o} 解釈の検証。
 * 特に「既存ディレクトリを渡されたときの規約」(既定ファイル名の補完 / 明確なエラー) を固定する。
 */
public class CliOutputTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void writeTextToFileWritesContent() throws IOException {
        File out = new File(tmp.getRoot(), "report.md");
        CliOutput.writeText(out, "# hello\n");
        assertTrue(out.isFile());
    }

    @Test
    public void writeTextCreatesMissingParentDirectories() throws IOException {
        // -o out/sub/report.md のように未作成の親ディレクトリを指定しても、
        // 生の FileNotFoundException ではなく素直に書き出せること。
        File out = new File(tmp.getRoot(), "missing/deep/report.md");
        assertFalse(out.getParentFile().exists());
        CliOutput.writeText(out, "# hi\n");
        assertTrue("parent dirs should be created", out.isFile());
    }

    @Test
    public void writeTextWithDefaultNameWritesIntoDirectory() throws IOException {
        File dir = tmp.newFolder("outdir");
        CliOutput.writeText(dir, "# hello\n", "report.md");
        File expected = new File(dir, "report.md");
        assertTrue("default file must be created inside the directory",
                expected.isFile());
    }

    @Test
    public void writeTextToDirectoryWithoutDefaultThrowsClearError() throws IOException {
        File dir = tmp.newFolder("outdir2");
        try {
            CliOutput.writeText(dir, "# hello\n");
            fail("expected IOException for directory output without default name");
        } catch (IOException ex) {
            assertTrue("message should explain the problem: " + ex.getMessage(),
                    ex.getMessage().contains("directory"));
        }
    }

    @Test
    public void writeImpactOutputToDirectoryWritesBothArtifacts() throws IOException {
        File dir = tmp.newFolder("impact-out");
        CliOutput.writeImpactOutput(dir, "# md\n", "@startuml\n@enduml\n", "impact");
        assertTrue(new File(dir, "impact.md").isFile());
        assertTrue(new File(dir, "impact.puml").isFile());
    }

    @Test
    public void writeImpactOutputExtensionlessWritesSiblings() throws IOException {
        File base = new File(tmp.getRoot(), "result");
        CliOutput.writeImpactOutput(base, "# md\n", "@startuml\n@enduml\n", "impact");
        assertTrue(new File(tmp.getRoot(), "result.md").isFile());
        assertTrue(new File(tmp.getRoot(), "result.puml").isFile());
        assertFalse(base.exists());
    }

    @Test
    public void writeImpactOutputMarkdownOnly() throws IOException {
        File out = new File(tmp.getRoot(), "only.md");
        CliOutput.writeImpactOutput(out, "# md\n", "@startuml\n@enduml\n", "impact");
        assertTrue(out.isFile());
        assertFalse(new File(tmp.getRoot(), "only.puml").exists());
    }

    @Test
    public void writeUmlOutputToDirectoryUsesDefaultBaseName() throws IOException {
        File dir = tmp.newFolder("uml-out");
        // 最小の正しい PlantUML。ディレクトリ + 既定ベース名で .svg が書かれる
        CliOutput.writeUmlOutput(dir, "@startuml\nclass A\n@enduml\n", "class-diagram");
        File svg = new File(dir, "class-diagram.svg");
        assertTrue("class-diagram.svg must be rendered into the directory",
                svg.isFile());
    }

    @Test
    public void writeUmlOutputPumlPassThrough() throws IOException {
        File out = new File(tmp.getRoot(), "diagram.puml");
        CliOutput.writeUmlOutput(out, "@startuml\nclass A\n@enduml\n", "class-diagram");
        assertTrue(out.isFile());
    }

    @Test
    public void writeUmlOutputRendersRealPngFromExtension() throws IOException {
        // -o diagram.png は以前 PlantUML テキストを .png に書いていた (拡張子無視の不具合)。
        // 現在は同梱 PlantUML で本物の PNG をラスタライズすることを、PNG シグネチャで固定する。
        File out = new File(tmp.getRoot(), "diagram.png");
        CliOutput.writeUmlOutput(out, "@startuml\nclass A\n@enduml\n", "class-diagram");
        assertTrue("PNG file must be created", out.isFile());
        byte[] bytes = java.nio.file.Files.readAllBytes(out.toPath());
        assertTrue("file must not be empty", bytes.length > 8);
        // PNG マジックナンバー 89 50 4E 47 (テキスト '@startuml' でないこと)
        assertEquals((byte) 0x89, bytes[0]);
        assertEquals((byte) 0x50, bytes[1]);
        assertEquals((byte) 0x4E, bytes[2]);
        assertEquals((byte) 0x47, bytes[3]);
    }

    @Test
    public void siblingPumlForDerivesBaseName() {
        File svg = new File(tmp.getRoot(), "foo.svg");
        assertEquals("foo.puml", CliOutput.siblingPumlFor(svg).getName());
    }

    @Test
    public void isSvgTargetDetectsSvgFileAndDirectory() throws IOException {
        assertTrue(CliOutput.isSvgTarget(new File(tmp.getRoot(), "out.svg")));
        assertTrue(CliOutput.isSvgTarget(new File(tmp.getRoot(), "OUT.SVG")));
        assertTrue(CliOutput.isSvgTarget(tmp.newFolder("dir")));
        assertFalse(CliOutput.isSvgTarget(new File(tmp.getRoot(), "out.puml")));
        assertFalse(CliOutput.isSvgTarget(null));
    }

    @Test
    public void perDiagramSvgTargetForDirectoryUsesLabel() throws IOException {
        File dir = tmp.newFolder("navs");
        File t = CliOutput.perDiagramSvgTarget(dir, "main_graph", 0, "nav-graph");
        assertEquals(dir, t.getParentFile());
        assertEquals("main_graph.svg", t.getName());
    }

    @Test
    public void perDiagramSvgTargetForFileAppendsLabel() {
        File out = new File(tmp.getRoot(), "graphs.svg");
        File t = CliOutput.perDiagramSvgTarget(out, "detail", 1, "nav-graph");
        assertEquals("graphs-detail.svg", t.getName());
    }

    @Test
    public void perDiagramSvgTargetSanitizesAndFallsBackOnEmptyLabel() {
        File out = new File(tmp.getRoot(), "g.svg");
        assertEquals("g-a_b.svg",
                CliOutput.perDiagramSvgTarget(out, "a/b", 0, "nav-graph").getName());
        assertEquals("g-nav-graph-2.svg",
                CliOutput.perDiagramSvgTarget(out, "", 2, "nav-graph").getName());
    }

    // --- ラウンド2: 画像出力の原子性 / .png の扱い / 名前の重複解決 -----------------

    @Test
    public void impactOutputRendersPngLikeTheHelpSays() throws Exception {
        // 回帰: .png が「拡張子なし」扱いに落ち、画像を期待した利用者に md+puml が返っていた。
        File target = new File(tmp.newFolder("impact"), "report.png");
        CliOutput.writeImpactOutput(target, "# md", "@startuml\nclass A\n@enduml\n");
        assertTrue("report.png が作られること", target.isFile());
        assertTrue("PNG として書かれること", target.length() > 0);
        assertFalse("md へフォールバックしないこと",
                new File(target.getParentFile(), "report.md").exists());
    }

    @Test
    public void svgRenderFailureKeepsThePreviousFile() throws Exception {
        // 回帰: CLI の .svg 経路だけ対象を直接開いており、失敗時に前回の SVG が消えていた。
        File target = new File(tmp.newFolder("svgout"), "d.svg");
        byte[] previous = "<svg>previous</svg>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(target.toPath(), previous);
        boolean ok = CliOutput.writeImageOrFallback(target,
                "@startuml\nclass A {\n@enduml\nbroken )(\n", "svg");
        assertFalse("壊れた図はレンダリング失敗すること", ok);
        assertArrayEquals("失敗しても前回の SVG が残ること",
                previous, java.nio.file.Files.readAllBytes(target.toPath()));
        assertTrue("サイドカー .puml が残ること",
                new File(target.getParentFile(), "d.puml").isFile());
    }

    @Test
    public void planDiagramNamesDedupesAfterSanitising() {
        // サニタイズで同じ名前へ落ちる組み合わせも重複解決すること。
        java.util.List<String> names = CliOutput.planDiagramNames(
                java.util.Arrays.asList("nav+graph", "nav graph", "nav_graph", "nav_graph"),
                "diagram");
        assertEquals(4, names.size());
        assertEquals("すべて異なること", 4, new java.util.HashSet<>(names).size());
        for (String n : names) {
            assertFalse("使えない文字が残らないこと: " + n, n.contains("+") || n.contains(" "));
        }
    }

    @Test
    public void planDiagramNamesFallsBackForBlankLabels() {
        java.util.List<String> names = CliOutput.planDiagramNames(
                java.util.Arrays.asList("", null, "  "), "nav-graph");
        assertEquals(3, names.size());
        assertEquals("空ラベルでも衝突しないこと", 3, new java.util.HashSet<>(names).size());
    }

    @Test
    public void isSingleDiagramImageTargetCoversPngAndSvg() throws IOException {
        assertTrue(CliOutput.isSingleDiagramImageTarget(new File(tmp.getRoot(), "a.svg")));
        assertTrue(CliOutput.isSingleDiagramImageTarget(new File(tmp.getRoot(), "a.PNG")));
        assertTrue(CliOutput.isSingleDiagramImageTarget(tmp.newFolder("d2")));
        assertFalse(CliOutput.isSingleDiagramImageTarget(new File(tmp.getRoot(), "a.puml")));
        assertFalse(CliOutput.isSingleDiagramImageTarget(null));
    }
}
