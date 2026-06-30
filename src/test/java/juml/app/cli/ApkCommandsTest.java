// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.util.ErrorListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

/**
 * {@link ApkCommands} の CLI ハンドラ検証。フィクスチャ
 * {@code src/test/resources/samples/apk/decoded} を入力に成果物が書き出されることを固定する。
 */
public class ApkCommandsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final File DECODED =
            new File("src/test/resources/samples/apk/decoded");

    private CliContext ctx(File out) {
        return new CliContext(DECODED, out, ErrorListener.silent(), null, true, null);
    }

    @Test
    public void summaryWritesMarkdown() throws IOException {
        File out = new File(tmp.getRoot(), "apk-summary.md");
        ApkCommands.handleApkSummary(ctx(out), null);
        assertTrue(out.isFile());
        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(md.contains("example-release.apk"));
        assertTrue(md.contains("MainActivity"));
    }

    @Test
    public void classDiagramWritesPuml() throws IOException {
        File out = new File(tmp.getRoot(), "apk-class.puml");
        ApkCommands.handleApkClassDiagram(ctx(out), null);
        assertTrue(out.isFile());
        String puml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.contains("MainActivity"));
    }

    @Test
    public void classDiagramRespectsPackageFilter() throws IOException {
        File out = new File(tmp.getRoot(), "apk-util.puml");
        ApkCommands.handleApkClassDiagram(ctx(out), "com.example.app.util");
        String puml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml.contains("Repository"));
        assertTrue("MainActivity should be filtered out", !puml.contains("MainActivity"));
    }

    @Test
    public void smaliReportWritesMarkdown() throws IOException {
        File out = new File(tmp.getRoot(), "apk-smali.md");
        ApkCommands.handleApkSmali(ctx(out), null);
        assertTrue(out.isFile());
        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(md.contains("# smali クラス構造"));
        assertTrue(md.contains("MainActivity"));
    }

    @Test
    public void sequenceDiagramWritesPuml() throws IOException {
        File out = new File(tmp.getRoot(), "apk-seq.puml");
        ApkCommands.handleApkSequence(ctx(out),
                "com.example.app.MainActivity.onClick", null);
        assertTrue(out.isFile());
        String puml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.contains("load()"));
        assertTrue(puml.contains("fetch()"));
    }

    @Test
    public void apkAllWritesAllArtifacts() throws IOException {
        File dir = tmp.newFolder("apk-out");
        ApkCommands.handleApkAll(ctx(dir), null);
        assertTrue(new File(dir, "apk-summary.md").isFile());
        assertTrue(new File(dir, "apk-class-diagram.svg").isFile());
        assertTrue(new File(dir, "manifest-diagram.svg").isFile());
    }

    /**
     * {@code --apk-decode} の end-to-end。実 APK が必要なので
     * {@code src/test/resources/samples/apk/sample.apk} があるときだけ走る。
     */
    @Test
    public void apkDecodeExtractsSmali() throws IOException {
        File apk = new File("src/test/resources/samples/apk/sample.apk");
        org.junit.Assume.assumeTrue("place a sample.apk to exercise decode", apk.isFile());
        File dir = tmp.newFolder("decode-out");
        CliContext c = new CliContext(apk, dir, juml.util.ErrorListener.silent(),
                null, true, null);
        ApkCommands.handleApkDecode(c);
        assertTrue(new File(dir, "apktool.yml").isFile());
        assertTrue(new File(dir, "smali").isDirectory());
    }
}
