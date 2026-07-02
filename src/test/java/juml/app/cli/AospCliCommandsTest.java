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
 * {@link AospCommands} の新設 CLI ハンドラ ({@code --vintf} / {@code --android-mk} /
 * {@code --partitions}) の検証。一時ディレクトリに最小の入力
 * (manifest.xml / Android.mk / Android.bp) を置き、-o ディレクトリへ
 * 既定ファイル名の Markdown + PlantUML が書き出されることを固定する。
 */
public class AospCliCommandsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private CliContext ctx(File in, File out) {
        return new CliContext(in, out, ErrorListener.silent(), null, true, null);
    }

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            assertTrue(parent.mkdirs());
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void vintfWritesMarkdownAndPuml() throws IOException {
        File proj = tmp.newFolder("vintf-proj");
        write(new File(proj, "vintf/manifest.xml"),
                "<manifest version=\"1.0\" type=\"device\">"
                        + "  <hal format=\"aidl\">"
                        + "    <name>android.hardware.power</name>"
                        + "    <version>2</version>"
                        + "    <fqname>IPower/default</fqname>"
                        + "  </hal>"
                        + "</manifest>");
        write(new File(proj, "vintf/compatibility_matrix.xml"),
                "<compatibility-matrix version=\"1.0\" type=\"framework\" level=\"8\">"
                        + "  <hal format=\"aidl\" optional=\"false\">"
                        + "    <name>android.hardware.power</name>"
                        + "    <version>2</version>"
                        + "    <interface><name>IPower</name>"
                        + "<instance>default</instance></interface>"
                        + "  </hal>"
                        + "</compatibility-matrix>");
        // Android アプリの manifest は VINTF として扱わないこと
        write(new File(proj, "AndroidManifest.xml"),
                "<manifest package=\"com.example\"><application/></manifest>");

        File out = tmp.newFolder("vintf-out");
        AospCommands.handleVintf(ctx(proj, out));

        File md = new File(out, "vintf.md");
        File puml = new File(out, "vintf.puml");
        assertTrue(md.isFile());
        assertTrue(puml.isFile());
        String mdText = read(md);
        assertTrue(mdText.contains("# VINTF Manifest Report"));
        assertTrue(mdText.contains("android.hardware.power"));
        assertTrue(mdText.contains("device manifest"));
        assertTrue(mdText.contains("compatibility matrix"));
        // アプリ manifest を数えていないこと (device + matrix の 2 ファイルのみ)
        assertTrue(mdText.contains("- Total manifests: 2"));
        // 必須 HAL は device 側で宣言済みと判定されること
        assertTrue(mdText.contains("宣言されています"));
        String pumlText = read(puml);
        assertTrue(pumlText.contains("@startuml"));
        assertTrue(pumlText.contains("android.hardware.power"));
        assertTrue(pumlText.contains("IPower/default"));
        // matrix の要求 → device 宣言の対応矢印
        assertTrue(pumlText.contains("declared"));
    }

    @Test
    public void androidMkWritesModuleReportAndDependencyGraph() throws IOException {
        File proj = tmp.newFolder("mk-proj");
        write(new File(proj, "Android.mk"),
                "LOCAL_PATH := $(call my-dir)\n"
                        + "include $(CLEAR_VARS)\n"
                        + "LOCAL_MODULE := libmkfoo\n"
                        + "LOCAL_SRC_FILES := foo.c\n"
                        + "include $(BUILD_SHARED_LIBRARY)\n"
                        + "\n"
                        + "include $(CLEAR_VARS)\n"
                        + "LOCAL_MODULE := mkapp\n"
                        + "LOCAL_SHARED_LIBRARIES := libmkfoo\n"
                        + "include $(BUILD_EXECUTABLE)\n");

        File out = tmp.newFolder("mk-out");
        AospCommands.handleAndroidMk(ctx(proj, out));

        File md = new File(out, "android-mk.md");
        File puml = new File(out, "android-mk.puml");
        assertTrue(md.isFile());
        assertTrue(puml.isFile());
        String mdText = read(md);
        assertTrue(mdText.contains("# Android.mk (Make) Module Report"));
        assertTrue(mdText.contains("libmkfoo"));
        assertTrue(mdText.contains("mkapp"));
        assertTrue(mdText.contains("cc_library_shared"));
        String pumlText = read(puml);
        assertTrue(pumlText.contains("@startuml"));
        assertTrue(pumlText.contains("Android.mk Module Dependencies"));
        assertTrue(pumlText.contains("libmkfoo"));
        assertTrue(pumlText.contains("mkapp"));
    }

    @Test
    public void partitionsWritesBreakdownAndCrossPartitionEdges() throws IOException {
        File proj = tmp.newFolder("bp-proj");
        write(new File(proj, "Android.bp"),
                "cc_library {\n"
                        + "    name: \"libsys\",\n"
                        + "}\n"
                        + "cc_library {\n"
                        + "    name: \"libvnd\",\n"
                        + "    vendor: true,\n"
                        + "    shared_libs: [\"libsys\"],\n"
                        + "}\n");

        File out = tmp.newFolder("bp-out");
        AospCommands.handlePartitions(ctx(proj, out));

        File md = new File(out, "partitions.md");
        File puml = new File(out, "partitions.puml");
        assertTrue(md.isFile());
        assertTrue(puml.isFile());
        String mdText = read(md);
        assertTrue(mdText.contains("# Partition Placement Report"));
        assertTrue(mdText.contains("## system (1 modules)"));
        assertTrue(mdText.contains("## vendor (1 modules)"));
        // vendor → system の跨ぎ依存が表になること
        assertTrue(mdText.contains("Cross-partition dependencies"));
        assertTrue(mdText.contains("| `libvnd` | vendor | `libsys` | system |"));
        String pumlText = read(puml);
        assertTrue(pumlText.contains("@startuml"));
        assertTrue(pumlText.contains("package \"system\""));
        assertTrue(pumlText.contains("package \"vendor\""));
        assertTrue(pumlText.contains("shared_libs"));
    }
}
