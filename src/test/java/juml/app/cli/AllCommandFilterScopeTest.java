// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.util.ErrorListener;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code -A} (--all) におけるクラス図フィルタの<b>適用範囲</b>を固定する。
 *
 * <p>フィルタ ({@code --exclude-package} など) はクラス図のためのオプションで、素の
 * {@code -c} も {@code classDiagram} のときだけ適用している。{@code -A} で走査結果そのものを
 * 絞ると、後段のシーケンス図が {@code infos} を<b>呼び出し解決の母集合</b>として使うため、
 * 呼び出し先が解決できなくなって呼び出し列が黙って途切れ、残った相手も
 * 「解析済みプロジェクトクラス」の色を失う。実測では {@code methods.txt} が 4 件から
 * 1 件へ縮んでいた。</p>
 *
 * <p>そのため本テストは 2 つを同時に見る: クラス図には効くこと、シーケンス図の候補一覧
 * ({@code methods.txt}) には効かないこと。片方だけだと、フィルタを丸ごと外す退行も、
 * 全体に効かせる退行も見逃す。</p>
 */
public class AllCommandFilterScopeTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private File project;

    @Before
    public void makeProject() throws Exception {
        project = tmp.newFolder("proj");
        write("src/main/java/com/example/ui/MainActivity.java",
                "package com.example.ui;\n"
                        + "import com.example.internal.Repo;\n"
                        + "public class MainActivity {\n"
                        + "    private Repo repo = new Repo();\n"
                        + "    protected void onCreate() { repo.load(); repo.save(); }\n"
                        + "}\n");
        write("src/main/java/com/example/internal/Repo.java",
                "package com.example.internal;\n"
                        + "public class Repo {\n"
                        + "    public void load() { helper(); }\n"
                        + "    public void save() { }\n"
                        + "    private void helper() { }\n"
                        + "}\n");
    }

    private void write(String relative, String content) throws Exception {
        Path p = project.toPath().resolve(relative);
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File f) throws Exception {
        return f.isFile()
                ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : "";
    }

    /** {@code -A} を実行して出力ディレクトリを返す。 */
    private File runAll(UmlOverrides overrides) throws Exception {
        File outDir = tmp.newFolder("out-" + System.nanoTime());
        CliContext ctx = new CliContext(project, outDir, ErrorListener.silent(),
                Boolean.FALSE, false, overrides, false);
        AndroidCommands.handleAll(ctx);
        return outDir;
    }

    private static UmlOverrides excludingInternal() {
        UmlOverrides o = new UmlOverrides();
        o.excludedPackages.add("com.example.internal");
        return o;
    }

    @Test
    public void excludePackageAppliesToTheClassDiagram() throws Exception {
        // 判定は「除外パッケージの箱が描かれているか」で行う。単に "Repo" を探すと、
        // MainActivity のフィールド型として出る文字列を拾ってしまい常に一致する。
        String before = read(new File(runAll(new UmlOverrides()), "class-diagram.svg"));
        assertTrue("前提: 除外しなければ internal パッケージがクラス図に出ること",
                before.contains("internal"));

        String after = read(new File(runAll(excludingInternal()), "class-diagram.svg"));
        assertFalse("--exclude-package したパッケージはクラス図から消えること",
                after.contains("internal"));
    }

    @Test
    public void excludePackageDoesNotShrinkTheSequenceCandidates() throws Exception {
        String before = read(new File(runAll(new UmlOverrides()), "methods.txt"));
        String after = read(new File(runAll(excludingInternal()), "methods.txt"));

        // クラス図フィルタはシーケンス図の母集合を削ってはいけない。
        assertTrue("前提: 除外なしで Repo.load が候補に載ること", before.contains("Repo.load"));
        assertTrue("除外しても呼び出し解決の母集合は保たれること: " + after,
                after.contains("Repo.load"));
        assertTrue("private ヘルパも母集合に残ること (呼び出し列の途切れ防止): " + after,
                after.contains("Repo.helper"));
    }
}
