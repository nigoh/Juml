// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.ErrorListener;
import juml.util.ProgressListener;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link BulkTabExporter} のコア (Swing 非依存) を headless で検証する。ファイル名計画
 * ({@link BulkTabExporter#planFileNames}) と一括書き出し ({@link BulkTabExporter#exportAll}) の
 * 出力・スキップ・部分失敗を、実際に生成されたファイルで確認する。
 */
public class BulkTabExporterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String OK_PUML = "@startuml\nAlice -> Bob : hi\n@enduml";
    // 実機 renderSvg が error42L で throw する既知入力 (枝ファミリ不整合)。部分失敗の再現に使う。
    private static final String BAD_PUML = "@startmindmap\n* Root\n-- C\n+++ D\n@endmindmap";

    /** ソースと描画結果が同じ (生成図タブ相当) のスナップショット。 */
    private static BulkTabExporter.Snapshot tab(String label, String puml) {
        return new BulkTabExporter.Snapshot(label, "key:" + label, puml, puml);
    }

    /** ソースと描画結果がずれた (編集中のエディタタブ相当) のスナップショット。 */
    private static BulkTabExporter.Snapshot editing(String label, String buffer,
                                                    String lastRendered) {
        return new BulkTabExporter.Snapshot(label, "key:" + label, buffer, lastRendered);
    }

    private static BulkTabExporter.Result run(List<BulkTabExporter.Snapshot> tabs, File dir,
                                              UmlExporter.Format fmt) {
        return BulkTabExporter.exportAll(tabs, dir, fmt,
                ProgressListener.silent(), ErrorListener.silent());
    }

    // --- planFileNames -------------------------------------------------------

    @Test
    public void planFileNames_sanitizesAndAppendsExtension() {
        List<String> names = BulkTabExporter.planFileNames(List.of("Foo Bar", "a/b:c"), "svg");
        assertEquals(List.of("Foo_Bar.svg", "a_b_c.svg"), names);
    }

    @Test
    public void planFileNames_dedupesCollisionsCaseInsensitively() {
        List<String> names = BulkTabExporter.planFileNames(List.of("Foo", "Foo", "foo"), "png");
        assertEquals("Foo.png", names.get(0));
        assertEquals("Foo_2.png", names.get(1));
        assertEquals("foo_3.png", names.get(2));
    }

    @Test
    public void planFileNames_blankLabelFallsBackToDiagram() {
        List<String> names = BulkTabExporter.planFileNames(
                Arrays.asList(null, "   ", "***"), "puml");
        assertEquals("diagram.puml", names.get(0));
        assertEquals("空白ラベルも diagram にフォールバックし連番で衝突回避",
                "diagram_2.puml", names.get(1));
        assertEquals("記号のみは _ 置換で残る", "___.puml", names.get(2));
    }

    // --- exportAll -----------------------------------------------------------

    @Test
    public void exportAll_puml_writesEachTab_skipsUnrendered() {
        File dir = tmp.getRoot();
        List<BulkTabExporter.Snapshot> tabs = List.of(
                tab("Alpha", OK_PUML),
                tab("Beta", null),          // 未描画 → スキップ
                tab("Gamma", "   "),        // 空白のみ → スキップ
                tab("Delta", OK_PUML));
        BulkTabExporter.Result r = run(tabs, dir, UmlExporter.Format.PUML);
        assertEquals(2, r.exported);
        assertEquals(2, r.skipped);
        assertTrue(r.failures.isEmpty());
        assertTrue(new File(dir, "Alpha.puml").isFile());
        assertTrue(new File(dir, "Delta.puml").isFile());
        assertFalse("未描画タブは書き出さない", new File(dir, "Beta.puml").exists());
    }

    @Test
    public void exportAll_svg_producesValidSvg() throws Exception {
        File dir = tmp.getRoot();
        BulkTabExporter.Result r = run(List.of(tab("Seq", OK_PUML)), dir, UmlExporter.Format.SVG);
        assertEquals(1, r.exported);
        File svg = new File(dir, "Seq.svg");
        assertTrue(svg.isFile());
        String content = Files.readString(svg.toPath());
        assertTrue(content.contains("<svg"));
        assertFalse(content.contains("Syntax Error"));
    }

    @Test
    public void exportAll_png_producesNonEmptyPng() {
        File dir = tmp.getRoot();
        BulkTabExporter.Result r = run(List.of(tab("Pic", OK_PUML)), dir, UmlExporter.Format.PNG);
        assertEquals(1, r.exported);
        File png = new File(dir, "Pic.png");
        assertTrue(png.isFile());
        assertTrue(png.length() > 0);
    }

    @Test
    public void exportAll_collidingLabels_writeSeparateFiles() {
        File dir = tmp.getRoot();
        BulkTabExporter.Result r = run(List.of(tab("Same", OK_PUML), tab("Same", OK_PUML)),
                dir, UmlExporter.Format.PUML);
        assertEquals(2, r.exported);
        assertTrue("同名ラベルでも両方残る", new File(dir, "Same.puml").isFile());
        assertTrue(new File(dir, "Same_2.puml").isFile());
    }

    @Test
    public void exportAll_ioFailure_isCollectedNotThrown() {
        // 実在しない出力ディレクトリへ書くと各タブが IOException。exportAll は例外を外へ漏らさず
        // 全件 failures に集計する (部分成功の核はレンダリング失敗だけでなく実 IO 失敗でも成立)。
        // ※ root 実行では setWritable(false) が効かないため、非存在ディレクトリで確実に失敗させる。
        File missing = new File(tmp.getRoot(), "no-such-dir");
        BulkTabExporter.Result r = run(List.of(tab("A", OK_PUML), tab("B", OK_PUML)),
                missing, UmlExporter.Format.PUML);
        assertEquals(0, r.exported);
        assertEquals("IO 失敗も全件 failures へ集計 (throw しない)", 2, r.failures.size());
        assertFalse("ディレクトリは勝手に作られない", missing.exists());
    }

    @Test
    public void exportAll_svgFailure_keepsThePreviousFile() throws Exception {
        // 回帰: SVG 分岐だけが renderSvg(File) を直接呼んでおり、対象を切り詰めてから
        // 描画し、失敗時はファイルごと削除していた = 前回の正しい SVG が消える。
        // 同じ操作なのに形式 (PNG/PUML) によって上書きの安全性が違っていた。
        File dir = tmp.getRoot();
        File target = new File(dir, "Bad.svg");
        byte[] previous = "<svg>previous good export</svg>".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.write(target.toPath(), previous);

        BulkTabExporter.Result r = run(List.of(tab("Bad", BAD_PUML)), dir,
                UmlExporter.Format.SVG);

        assertEquals(0, r.exported);
        assertEquals(1, r.failures.size());
        assertTrue("失敗しても前回の SVG が残ること", target.isFile());
        assertArrayEquals("前回の内容がそのまま残ること",
                previous, java.nio.file.Files.readAllBytes(target.toPath()));
    }

    @Test
    public void exportAll_oneFailure_othersStillExported() {
        File dir = tmp.getRoot();
        BulkTabExporter.Result r = run(List.of(tab("Bad", BAD_PUML), tab("Good", OK_PUML)),
                dir, UmlExporter.Format.SVG);
        assertTrue("失敗があっても他タブは出力される (部分成功)",
                new File(dir, "Good.svg").isFile());
        assertEquals(1, r.exported);
        assertEquals(1, r.failures.size());
        assertTrue("失敗一覧にラベルが含まれる", r.failures.get(0).startsWith("Bad:"));
    }

    @Test
    public void exportAll_missingOutputDirectory_failsWithTheChosenPathNotATempName() {
        // 回帰: DIRECTORIES_ONLY のチューザは「まだ無いフォルダ名を打ち込んで保存」を
        // 承認する。作らずに進むと全タブが失敗し、完了ダイアログにはタブ数ぶんの
        // 隠し一時ファイル名が並ぶだけで、本当の原因 (フォルダが無い) が出なかった。
        // 呼び出し側 (choose) がフォルダを作るようにしたが、万一届かなかった場合でも
        // 失敗メッセージは利用者が選んだ名前を指すこと。
        File missing = new File(tmp.getRoot(), "not-created-yet");
        BulkTabExporter.Result r = run(List.of(tab("Alpha", OK_PUML)),
                missing, UmlExporter.Format.SVG);

        assertEquals(0, r.exported);
        assertEquals(1, r.failures.size());
        String failure = r.failures.get(0);
        assertTrue("利用者が選んだ名前を指すこと: " + failure, failure.contains("Alpha.svg"));
        assertTrue("一時ファイル名を出さないこと: " + failure, !failure.contains(".juml-"));
    }

    /**
     * 回帰: 形式はスナップショットを採った後に選ばれるので、両方のテキストを持たせること。
     *
     * <p>一時期スナップショットが {@code .puml} 用のバッファだけを持っており、SVG/PNG も
     * それを受け取っていた。編集途中で構文エラーのタブがあると、画面には直前の正常な図が
     * 出ているのに一括画像出力だけ<b>そのタブが失敗</b>し、ワークスペース全体の書き出しが
     * 部分成功 + 失敗ダイアログになっていた。</p>
     */
    @Test
    public void imageFormatsUseTheRenderedTextAndPumlUsesTheBuffer() throws Exception {
        BulkTabExporter.Snapshot mid = editing("Mid", BAD_PUML, OK_PUML);

        File svgDir = tmp.newFolder("svg");
        BulkTabExporter.Result svg = run(List.of(mid), svgDir, UmlExporter.Format.SVG);
        assertEquals("画像は最後に描けた図を出すので成功すること: " + svg.failures,
                1, svg.exported);

        File pumlDir = tmp.newFolder("puml");
        BulkTabExporter.Result puml = run(List.of(mid), pumlDir, UmlExporter.Format.PUML);
        assertEquals(1, puml.exported);
        String written = new String(java.nio.file.Files.readAllBytes(
                pumlDir.listFiles()[0].toPath()), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("ソースは編集中のバッファであること", BAD_PUML, written);
    }

    /** 未描画のエディタタブでも .puml はバッファがあるので書き出せること。 */
    @Test
    public void unrenderedEditorTabStillExportsItsSource() throws Exception {
        BulkTabExporter.Snapshot fresh = editing("Fresh", OK_PUML, null);

        File pumlDir = tmp.newFolder("puml-fresh");
        assertEquals(1, run(List.of(fresh), pumlDir, UmlExporter.Format.PUML).exported);

        File svgDir = tmp.newFolder("svg-fresh");
        assertEquals("画像はまだ描けていないので skip されること",
                1, run(List.of(fresh), svgDir, UmlExporter.Format.SVG).skipped);
    }
}
