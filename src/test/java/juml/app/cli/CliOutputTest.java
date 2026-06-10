// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

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
    public void siblingPumlForDerivesBaseName() {
        File svg = new File(tmp.getRoot(), "foo.svg");
        assertEquals("foo.puml", CliOutput.siblingPumlFor(svg).getName());
    }
}
