// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.integration;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import juml.app.uml.PlantUmlSvgRenderer;
import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import juml.app.uml.git.GitRepoService;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.structdiff.ClassStructureDiff;
import juml.core.structdiff.ClassStructureDiff.ChangeKind;
import juml.core.structdiff.ClassStructureDiff.ClassDiff;
import juml.core.structdiff.PlantUmlStructureDiffDiagram;
import juml.core.structdiff.PlantUmlStructureDiffDiagram.Options;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 「git UML diff」機能の描画パイプライン ({@link GitRepoService#fileContentAt} →
 * {@link JavaStructureExtractor#extract} → {@link ClassStructureDiff#compare} →
 * {@link PlantUmlStructureDiffDiagram#generate} → {@link PlantUmlSvgRenderer#render})
 * を、テンポラリの実 git リポジトリに対してヘッドレスで通す end-to-end 統合テスト。
 *
 * <p>{@link juml.app.uml.git.GitUmlDiffDialog} の {@code SwingWorker#doInBackground()} が
 * 行うのとまったく同じ連鎖を Swing/Robot を使わず再現し、コミット間の構造変化が SVG まで
 * 正しく行き渡ることを確認する。個々の変換ロジックの単体テストは
 * {@code juml.core.structdiff.ClassStructureDiffTest} /
 * {@code juml.core.structdiff.PlantUmlStructureDiffDiagramTest} /
 * {@code juml.app.uml.git.GitRepoServiceTest} 側にあるため、ここでは「繋ぎ込み」の
 * 回帰検出に絞る。</p>
 */
public class GitUmlDiffPipelineE2ETest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final String REL_PATH = "src/main/java/com/demo/ClassA.java";

    private File root;
    private Git git;
    private GitRepoService service;

    private RevCommit v1;
    private RevCommit v2;
    private RevCommit v3;

    /** v1: name フィールドと greet() メソッドのみを持つ ClassA。 */
    private static final String SRC_V1 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  private String name;\n\n"
                    + "  public String greet() {\n"
                    + "    return \"hi\";\n"
                    + "  }\n"
                    + "}\n";

    /** v2: greet() の引数追加 (シグネチャ変更) + count フィールド追加 + Extra クラス追加。 */
    private static final String SRC_V2 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  private String name;\n"
                    + "  private int count;\n\n"
                    + "  public String greet(String who) {\n"
                    + "    return \"hi \" + who;\n"
                    + "  }\n"
                    + "}\n\n"
                    + "class Extra {\n"
                    + "  void run() {}\n"
                    + "}\n";

    /** v3: count フィールド削除 + Extra に stop() メソッド追加。 */
    private static final String SRC_V3 =
            "package com.demo;\n\n"
                    + "public class ClassA {\n"
                    + "  private String name;\n\n"
                    + "  public String greet(String who) {\n"
                    + "    return \"hi \" + who;\n"
                    + "  }\n"
                    + "}\n\n"
                    + "class Extra {\n"
                    + "  void run() {}\n"
                    + "  void stop() {}\n"
                    + "}\n";

    @Before
    public void setUp() throws Exception {
        root = tmp.newFolder("repo");
        git = Git.init().setDirectory(root).call();
        // CI/開発環境のグローバル設定に gpg.format=ssh があると JGit 6.x の
        // CommitCommand が解釈できず失敗するため、リポジトリ設定で無効化する。
        StoredConfig cfg = git.getRepository().getConfig();
        cfg.setString("gpg", null, "format", "openpgp");
        cfg.setBoolean("commit", null, "gpgsign", false);
        cfg.save();

        writeFile(SRC_V1);
        git.add().addFilepattern(".").call();
        v1 = commit("v1: add ClassA");

        writeFile(SRC_V2);
        git.add().addFilepattern(".").call();
        v2 = commit("v2: change greet signature, add count, add Extra");

        writeFile(SRC_V3);
        git.add().addFilepattern(".").call();
        v3 = commit("v3: remove count, add Extra.stop");

        service = GitRepoService.open(root);
        assertNotNull("テスト用リポジトリを開けるはず", service);
    }

    private void writeFile(String content) throws IOException {
        File f = new File(root, REL_PATH);
        File parent = f.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("mkdirs failed: " + parent);
        }
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private RevCommit commit(String message) throws Exception {
        return git.commit().setMessage(message)
                .setAuthor("Alice", "alice@example.com")
                .setCommitter("Alice", "alice@example.com").call();
    }

    @After
    public void tearDown() {
        if (service != null) {
            service.close();
        }
        if (git != null) {
            git.close();
        }
    }

    /**
     * {@code GitUmlDiffDialog} が {@code oldRev == null} で行う「親コミットとの比較」を
     * 再現する。{@code GitUmlDiffDialog#parseQuietly} と同じフォールバック規則で
     * null/空ソースを空リスト扱いする。
     */
    private static List<JavaClassInfo> parseQuietly(String source) {
        return (source == null || source.isEmpty())
                ? List.of() : JavaStructureExtractor.extract(source);
    }

    /** {@code GitUmlDiffDialog} の doInBackground() と同じ連鎖を通し、生成した PlantUML を返す。 */
    private List<ClassDiff> diffAgainst(String baseRev, String newRev, String path)
            throws IOException {
        String oldSrc = baseRev != null ? service.fileContentAt(baseRev, path) : null;
        String newSrc = service.fileContentAt(newRev, path);
        return ClassStructureDiff.compare(parseQuietly(oldSrc), parseQuietly(newSrc));
    }

    private static RenderedSvg render(List<ClassDiff> diffs, String title) throws IOException {
        Options opt = new Options();
        opt.title = title;
        String puml = PlantUmlStructureDiffDiagram.generate(diffs, opt);
        assertTrue("生成 PlantUML は @startuml で始まるはず", puml.startsWith("@startuml"));
        RenderedSvg svg = PlantUmlSvgRenderer.render(puml);
        assertNotNull("PlantUML が空出力でない限り RenderedSvg は非 null のはず", svg);
        assertNotNull("GraphicsNode が構築されているはず", svg.getRoot());
        assertTrue("width should be > 0", svg.getWidth() > 0);
        assertTrue("height should be > 0", svg.getHeight() > 0);
        assertTrue("SVG 要素が出力されるはず: " + svg.getSvgXml(), svg.getSvgXml().contains("<svg"));
        return svg;
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: 1 コミット選択 (= 親コミットとの比較)
    // -------------------------------------------------------------------------

    @Test
    public void parentComparison_detectsAddedClassAndModifiedMember() throws Exception {
        // GitUmlDiffDialog(oldRev=null) と同じく parentOf() で基準コミットを求める
        String base = service.parentOf(v2.getName());
        assertEquals("v2 の親は v1 のはず", v1.getName(), base);

        List<ClassDiff> diffs = diffAgainst(base, v2.getName(), REL_PATH);
        RenderedSvg svg = render(diffs, REL_PATH + " v1 -> v2");

        // ClassA: greet() のシグネチャ変更 + count フィールド追加で MODIFIED
        assertEquals(ChangeKind.MODIFIED, byName(diffs, "ClassA").kind);
        // Extra: v1 に存在しないので丸ごと ADDED
        assertEquals(ChangeKind.ADDED, byName(diffs, "Extra").kind);

        assertTrue("追加クラス Extra のクラス名が SVG に現れるはず",
                svg.getSvgXml().contains("Extra"));
        assertTrue("既存クラス ClassA のクラス名が SVG に現れるはず",
                svg.getSvgXml().contains("ClassA"));
    }

    // -------------------------------------------------------------------------
    // ハッピーパス: 2 コミット選択 (親コミットを飛び越えた比較)
    // -------------------------------------------------------------------------

    @Test
    public void explicitTwoCommitComparison_accumulatesChangesAcrossSkippedCommit() throws Exception {
        // v1 → v3 を直接比較 (v2 は履歴上飛ばす)。GitFileHistoryPane が 2 件選択時に行う経路。
        List<ClassDiff> diffs = diffAgainst(v1.getName(), v3.getName(), REL_PATH);
        render(diffs, REL_PATH + " v1 -> v3");

        ClassDiff classA = byName(diffs, "ClassA");
        assertEquals(ChangeKind.MODIFIED, classA.kind);
        // v1 -> v3 では greet() のシグネチャ変更のみが残り、count は v2 で追加後 v3 で削除済みなので
        // 最終的な差分には現れない (中間コミットを経由しない直接比較の特性)。
        boolean countMentioned = classA.fields.stream()
                .anyMatch(m -> m.label().contains("count"));
        assertFalse("v1/v3 直接比較では中間コミットのみに存在した count は現れないはず",
                countMentioned);

        ClassDiff extra = byName(diffs, "Extra");
        assertEquals("Extra は v1 に無いのでクラスごと ADDED のはず", ChangeKind.ADDED, extra.kind);
        assertEquals("ADDED クラスの全メソッドは ADDED 扱い", 2, extra.methods.size());
    }

    // -------------------------------------------------------------------------
    // 異常系: 初回コミット (親なし) は空ソースとの比較になり全 ADDED
    // -------------------------------------------------------------------------

    @Test
    public void initialCommit_hasNoParent_andComparesAgainstEmptySource() throws Exception {
        assertNull("初回コミットの親は null のはず", service.parentOf(v1.getName()));

        // GitUmlDiffDialog は base==null のとき oldSrc も null にする
        List<ClassDiff> diffs = diffAgainst(null, v1.getName(), REL_PATH);
        RenderedSvg svg = render(diffs, REL_PATH + " (empty) -> v1");

        assertFalse("初回コミットは全クラスが ADDED のはず", diffs.isEmpty());
        for (ClassDiff d : diffs) {
            assertEquals("初回コミットは全クラスが ADDED のはず: " + d.displayName(),
                    ChangeKind.ADDED, d.kind);
        }
        assertTrue(svg.getSvgXml().contains("ClassA"));
    }

    // -------------------------------------------------------------------------
    // 異常系: 存在しないパスは fileContentAt が null を返し、差分なし表示になる
    // -------------------------------------------------------------------------

    @Test
    public void missingPath_fileContentAtReturnsNull_andDiagramShowsNoChanges() throws Exception {
        String missing = "src/main/java/com/demo/NoSuchFile.java";
        assertNull(service.fileContentAt(v1.getName(), missing));
        assertNull(service.fileContentAt(v2.getName(), missing));

        List<ClassDiff> diffs = diffAgainst(v1.getName(), v2.getName(), missing);
        assertTrue("存在しないファイルなので差分は空のはず", diffs.isEmpty());

        RenderedSvg svg = render(diffs, missing);
        assertTrue("差分なしメッセージが図中に出るはず (既定の英語メッセージ)",
                svg.getSvgXml().contains("No structural changes"));
    }

    private static ClassDiff byName(List<ClassDiff> diffs, String simpleName) {
        for (ClassDiff d : diffs) {
            if (d.anySide().getSimpleName().equals(simpleName)) {
                return d;
            }
        }
        throw new AssertionError("class not found in diff: " + simpleName);
    }
}
