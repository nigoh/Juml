// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * エクスポートが失敗しても、前回書き出したファイルを壊さないことの回帰テスト。
 *
 * <p>以前は対象ファイルを直接開いて書いていたため、描画失敗・エンコーダ不在・
 * ディスク満杯のときに<b>上書き確認に「はい」と答えただけで前回の正しい出力が失われ、
 * 新しい内容も残らない</b>状態になっていた。</p>
 */
public class UmlExporterOverwriteSafetyTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private static final String BROKEN_PUML = "@startuml\nclass A {\n@enduml\nthis is not uml )(\n";

    @Test
    public void failedSvgExportKeepsThePreviousFile() throws Exception {
        File target = tmp.newFile("diagram.svg");
        byte[] previous = "<svg>previous good export</svg>".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), previous);

        try {
            UmlExporter.export(UmlExporter.Format.SVG, target, BROKEN_PUML, null);
            // 描画が通ってしまう入力なら、この検証自体が無意味なので気付けるようにする。
            fail("壊れた PlantUML は描画に失敗するはず");
        } catch (Exception expected) {
            assertTrue("描画失敗が伝播すること", expected != null);
        }

        assertArrayEquals("失敗しても前回のエクスポートが残ること",
                previous, Files.readAllBytes(target.toPath()));
    }

    @Test
    public void successfulPumlExportReplacesTheFile() throws Exception {
        File target = tmp.newFile("diagram.puml");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));

        UmlExporter.export(UmlExporter.Format.PUML, target,
                "@startuml\nclass A\n@enduml\n", null);

        assertEquals("@startuml\nclass A\n@enduml\n", Files.readString(target.toPath()));
    }

    @Test
    public void successfulSvgExportReplacesTheFile() throws Exception {
        File target = tmp.newFile("diagram.svg");
        Files.write(target.toPath(), "old".getBytes(StandardCharsets.UTF_8));

        UmlExporter.export(UmlExporter.Format.SVG, target,
                "@startuml\nclass A\nclass B\nA --> B\n@enduml\n", null);

        assertTrue("SVG が書き出されること",
                Files.readString(target.toPath()).contains("<svg"));
    }

    @Test
    public void emptyDiagramPngExportKeepsThePreviousFile() throws Exception {
        File target = tmp.newFile("diagram.png");
        byte[] previous = "previous png bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(target.toPath(), previous);

        try {
            UmlExporter.export(UmlExporter.Format.PNG, target, null, null);
            fail("画像が無ければ失敗するはず");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage() != null);
        }

        assertArrayEquals("空図のエクスポート失敗で前回の PNG を壊さないこと",
                previous, Files.readAllBytes(target.toPath()));
    }
}
