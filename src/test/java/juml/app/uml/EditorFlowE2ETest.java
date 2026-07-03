// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.ErrorListener;

import javax.swing.JPanel;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * UML エディタの主要フロー (プロジェクト読込 → PlantUML 生成 → SVG/PNG レンダリング →
 * 付箋の永続化・キー移行・エクスポート注入) を Swing フレーム抜きで通す end-to-end テスト。
 *
 * <p>個々のユニットテストが各部品を守るのに対し、本テストは「フローとして壊れていないか」
 * (生成した図が実際に描画でき、付箋がエクスポート SVG に整形式で入り、Save As 相当の
 * キー移行後も付箋が失われないか) を横断的に確認して回帰を早期検出する。付箋・エクスポート系は
 * パッケージ内部 API を使うため {@code juml.app.uml} パッケージに置く。</p>
 */
public class EditorFlowE2ETest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File f, String content) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private File buildProject() throws IOException {
        File root = tmp.newFolder("EditorSample");
        write(new File(root, "src/main/java/com/demo/Service.java"),
                "package com.demo;\n"
                        + "/** Service layer. */\n"
                        + "public class Service {\n"
                        + "  private final Repo repo = new Repo();\n"
                        + "  public void handle() { repo.save(); }\n"
                        + "}\n");
        write(new File(root, "src/main/java/com/demo/Repo.java"),
                "package com.demo;\n"
                        + "public class Repo { public void save() {} }\n");
        return root;
    }

    /**
     * クラス図を生成 → SVG レンダリング → 付箋を注入し、結果が整形式 XML であること。
     * ELEMENT アンカー付箋は解決済み絶対座標で注入されること。
     */
    @Test
    public void classDiagramRendersAndNotesInjectAsWellFormedSvg() throws Exception {
        File project = buildProject();
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(project, ErrorListener.silent());
        assertTrue(cache.isLoaded());

        String puml = DiagramService.generatePuml(new DiagramRequest(DiagramKind.CLASS), cache);
        assertNotNull(puml);
        assertTrue(puml.contains("@start"));

        // 素の SVG
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PlantUmlRenderer.renderSvg(puml, buf);
        String bareSvg = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        assertWellFormedXml(bareSvg);

        // ELEMENT アンカー付箋を解決してエクスポート注入する。
        JPanel owner = new JPanel();
        DiagramNotesLayer layer = new DiagramNotesLayer(owner);
        layer.setElementResolver(fqn -> new double[] {500, 250, 140, 70});
        layer.addElementNote("com.demo.Service", new double[] {500, 250, 140, 70});
        List<DiagramNote> resolved = layer.notesForExportResolved();
        assertEquals(1, resolved.size());
        // 解決後の絶対座標は要素の隣 (原点付近ではない)。
        assertTrue("付箋 X は要素の右側 (>500)", resolved.get(0).getX() > 500);

        String withNotes = NoteExport.injectIntoSvg(bareSvg, resolved);
        assertTrue("付箋グループが注入される", withNotes.contains("juml-notes"));
        assertWellFormedXml(withNotes);
    }

    /** 生成したクラス図が PNG ラスタライズ経路でも例外なく画像化できること。 */
    @Test
    public void classDiagramRastersToPng() throws Exception {
        File project = buildProject();
        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(project, ErrorListener.silent());

        String puml = DiagramService.generatePuml(new DiagramRequest(DiagramKind.CLASS), cache);
        BufferedImage img = PlantUmlImageRenderer.toBufferedImage(puml);
        assertNotNull("PNG 画像が得られる", img);
        assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
    }

    /**
     * 付箋の永続化フロー: 保存 → 別インスタンスで読込 → Save As 相当のキー移行 →
     * 旧キーから消え新キーで読める。1 図分でも失われないこと。
     */
    @Test
    public void notesPersistAcrossKeyMigration() throws Exception {
        File project = buildProject();
        DiagramNotesStore store = new DiagramNotesStore(project);
        DiagramNote note = new DiagramNote(30, 40, 200, 120, "# TODO\nreview this");
        note.setColor("#FFE08A");
        store.save("PUML:untitled-1", Arrays.asList(note));

        // 別インスタンス = 再起動相当でロードできる
        DiagramNotesStore reopened = new DiagramNotesStore(project);
        assertEquals(1, reopened.load("PUML:untitled-1").size());

        // Save As 相当のキー移行
        String newKey = "PUML:" + new File(project, "diagram.puml").getAbsolutePath();
        assertTrue(reopened.rename("PUML:untitled-1", newKey));

        DiagramNotesStore afterRename = new DiagramNotesStore(project);
        assertTrue("旧キーは空", afterRename.load("PUML:untitled-1").isEmpty());
        assertEquals("新キーで付箋が読める", 1, afterRename.load(newKey).size());
    }

    /**
     * プロジェクト切替フロー: A を読み込み → B を読み込むと、キャッシュは B の内容だけを
     * 見せ、A のクラスは残らない (スナップショット差し替えの一貫性)。
     */
    @Test
    public void projectSwitchFullyReplacesAnalysis() throws Exception {
        File projectA = buildProject();
        File projectB = tmp.newFolder("OtherSample");
        write(new File(projectB, "src/main/java/org/other/Widget.java"),
                "package org.other;\npublic class Widget { public void draw() {} }\n");

        ProjectAnalysisCache cache = new ProjectAnalysisCache();
        cache.load(projectA, ErrorListener.silent());
        assertTrue(cache.getClasses().stream()
                .anyMatch(c -> "Service".equals(c.getSimpleName())));

        cache.load(projectB, ErrorListener.silent());
        assertTrue("B のクラスが見える", cache.getClasses().stream()
                .anyMatch(c -> "Widget".equals(c.getSimpleName())));
        assertFalse("A のクラスは残らない", cache.getClasses().stream()
                .anyMatch(c -> "Service".equals(c.getSimpleName())));
    }

    private static void assertWellFormedXml(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        // 外部 DTD/エンティティは読みに行かない (SVG の DOCTYPE で失敗しないため)。
        f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        f.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
