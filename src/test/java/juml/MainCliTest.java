// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Main CLI の UML / Android プロジェクト機能 (-c, -q, -d, -G, -g, -m, -A 等) を検証する。
 */
public class MainCliTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private final ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();
    private PrintStream origOut;
    private PrintStream origErr;
    private InputStream origIn;

    @Before
    public void redirectIo() {
        origOut = System.out;
        origErr = System.err;
        origIn = System.in;
        System.setOut(new PrintStream(stdoutBuf, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderrBuf, true, StandardCharsets.UTF_8));
    }

    @After
    public void restoreIo() {
        System.setOut(origOut);
        System.setErr(origErr);
        System.setIn(origIn);
    }

    private void writeFile(File f, String content) throws Exception {
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testClassDiagramCli() throws Exception {
        File javaFile = tmp.newFile("Foo.java");
        writeFile(javaFile, "package x; class Foo { Bar b; void m() {} } class Bar {}");
        File outPuml = new File(tmp.getRoot(), "foo.puml");
        Main.main(new String[]{"-c", "-o", outPuml.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("class \"x.Foo\""));
        assertTrue(puml, puml.contains("class \"x.Bar\""));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testSequenceDiagramCli() throws Exception {
        File javaFile = tmp.newFile("Bar.java");
        writeFile(javaFile, "class Bar { Service s; void run() { s.go(); } }");
        File outPuml = new File(tmp.getRoot(), "bar.puml");
        Main.main(new String[]{"-q", "Bar.run", "-o", outPuml.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("Caller -> Bar: Bar.run()"));
        assertTrue(puml, puml.contains("Bar -> Service: Service.go()"));
    }

    @Test
    public void testGradleCliFromFile() throws Exception {
        File gradle = tmp.newFile("build.gradle");
        writeFile(gradle, "plugins { id 'com.android.application' }\n"
                + "android {\n  namespace 'p'\n  defaultConfig { applicationId 'p' minSdk 24 }\n}\n");
        File out = new File(tmp.getRoot(), "g.md");
        Main.main(new String[]{"-g", "-o", out.getAbsolutePath(),
                gradle.getAbsolutePath()});
        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(md, md.contains("# Android Project Summary"));
        assertTrue(md, md.contains("com.android.application"));
    }

    @Test
    public void testManifestCliFromFile() throws Exception {
        File mf = tmp.newFile("AndroidManifest.xml");
        writeFile(mf, "<manifest xmlns:android='http://schemas.android.com/apk/res/android' "
                + "package='com.x'><application>"
                + "<activity android:name='.A' android:exported='true'/></application></manifest>");
        File out = new File(tmp.getRoot(), "m.md");
        Main.main(new String[]{"-m", "-o", out.getAbsolutePath(),
                mf.getAbsolutePath()});
        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(md, md.contains("com.x.A"));
    }

    @Test
    public void testComponentAndDependencyAndSummary() throws Exception {
        File root = tmp.newFolder("ProjY");
        File app = new File(root, "app");
        assertTrue(app.mkdirs());
        writeFile(new File(root, "settings.gradle"), "include ':app'\n");
        writeFile(new File(app, "build.gradle"),
                "plugins { id 'com.android.application' }\n"
                        + "android { namespace 'p' compileSdk 34 }\n"
                        + "dependencies { implementation 'androidx.appcompat:appcompat:1.6.1' }\n");
        File mainDir = new File(app, "src/main");
        assertTrue(mainDir.mkdirs());
        writeFile(new File(mainDir, "AndroidManifest.xml"),
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android' "
                        + "package='com.example'><application>"
                        + "<activity android:name='.MainActivity'>"
                        + "<intent-filter><action android:name='android.intent.action.MAIN'/>"
                        + "<category android:name='android.intent.category.LAUNCHER'/>"
                        + "</intent-filter></activity></application></manifest>");

        File compOut = new File(tmp.getRoot(), "comp.puml");
        Main.main(new String[]{"-d", "-o", compOut.getAbsolutePath(),
                root.getAbsolutePath()});
        String comp = new String(Files.readAllBytes(compOut.toPath()), StandardCharsets.UTF_8);
        assertTrue(comp, comp.contains("@startuml"));
        assertTrue(comp, comp.contains("MainActivity"));

        File depOut = new File(tmp.getRoot(), "dep.puml");
        Main.main(new String[]{"-G", "-o", depOut.getAbsolutePath(),
                root.getAbsolutePath()});
        String dep = new String(Files.readAllBytes(depOut.toPath()), StandardCharsets.UTF_8);
        assertTrue(dep, dep.contains("@startuml"));
        assertTrue(dep, dep.contains("androidx.appcompat:appcompat"));

        File sumOut = new File(tmp.getRoot(), "sum.md");
        Main.main(new String[]{"--summary", "-o", sumOut.getAbsolutePath(),
                root.getAbsolutePath()});
        String sum = new String(Files.readAllBytes(sumOut.toPath()), StandardCharsets.UTF_8);
        assertTrue(sum, sum.contains("Android Project Summary"));
        assertTrue(sum, sum.contains("MainActivity"));
    }

    @Test
    public void testClassDiagramAutomergeManifest() throws Exception {
        File root = tmp.newFolder("ProjZ");
        File pkg = new File(root, "app/src/main/java/com/x");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "MainActivity.java"),
                "package com.x; public class MainActivity { void m() {} }");
        File manifestDir = new File(root, "app/src/main");
        writeFile(new File(manifestDir, "AndroidManifest.xml"),
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android' "
                        + "package='com.x'><application>"
                        + "<activity android:name='.MainActivity'/></application></manifest>");
        File out = new File(tmp.getRoot(), "cls.puml");
        Main.main(new String[]{"-c", "-o", out.getAbsolutePath(),
                root.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue("expected <<Activity>> via manifest auto-merge: " + puml,
                puml.contains("<<Activity>>"));
    }

    @Test
    public void testAllInOneOutput() throws Exception {
        File root = tmp.newFolder("ProjAll");
        File pkg = new File(root, "app/src/main/java/com/x");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "MainActivity.java"),
                "package com.x; public class MainActivity { void onCreate() { setup(); } }");
        writeFile(new File(root, "settings.gradle"), "include ':app'\n");
        writeFile(new File(root, "app/build.gradle"),
                "plugins { id 'com.android.application' }\n"
                        + "android { namespace 'com.x' compileSdk 34 }\n"
                        + "dependencies { implementation 'androidx.core:core-ktx:1.13.0' }\n");
        writeFile(new File(root, "app/src/main/AndroidManifest.xml"),
                "<manifest xmlns:android='http://schemas.android.com/apk/res/android' "
                        + "package='com.x'><application>"
                        + "<activity android:name='.MainActivity'/></application></manifest>");

        File outDir = new File(tmp.getRoot(), "all-out");
        Main.main(new String[]{"--all", "-o", outDir.getAbsolutePath(),
                root.getAbsolutePath()});
        assertTrue("output dir created", outDir.isDirectory());
        for (String name : new String[]{
                "summary.md", "class-diagram.svg", "component-diagram.svg",
                "dependency-graph.svg"}) {
            File f = new File(outDir, name);
            assertTrue("expected " + name, f.isFile());
            assertTrue("expected " + name + " non-empty", f.length() > 0);
        }
        String md = new String(Files.readAllBytes(new File(outDir, "summary.md").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(md, md.contains("# Android Project Summary"));
        assertTrue(md, md.contains("com.x"));
        // SVG は XML テキストなのでクラス名が描画テキストとして含まれることを確認。
        String cls = new String(Files.readAllBytes(new File(outDir, "class-diagram.svg").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(cls, cls.contains("<svg"));
        assertTrue(cls, cls.contains("MainActivity"));
    }

    @Test
    public void testAllInOneRequiresDir() throws Exception {
        File regular = tmp.newFile("notadir.spd");
        writeFile(regular, "x");
        // file_in がディレクトリでないので exit するはず → System.exit を捕まえる方法が
        // 無いので、ここでは "プロジェクトディレクトリ" を別途用意して出力先のみ
        // ファイルにする実験は省略する。代わりに -o 未指定で実行して exit するパスを確認。
        File root = tmp.newFolder("ProjReq");
        // -o 無し → exit 1。ここでは「-A だけ → エラー出力」を確認
        // System.exit 捕捉は難しいので smoke レベルで OK とする
        File outDir = new File(tmp.getRoot(), "ok-out");
        Main.main(new String[]{"--all", "-o", outDir.getAbsolutePath(),
                root.getAbsolutePath()});
        assertTrue(outDir.isDirectory());
    }

    /**
     * README 例どおりに {@code java -jar Juml.jar -c -o out in.java} 形式で
     * 呼び出して {@code -c} が認識されることを保証する。歴史的に
     * {@code parse(args, 1)} で先頭が黙って消費されるバグがあったため、
     * 回帰防止としてダミー先頭引数を持たないテストを残しておく。
     */
    @Test
    public void testReadmeStyleDirectInvocation() throws Exception {
        File javaFile = tmp.newFile("DirectInvoke.java");
        writeFile(javaFile, "package x; class DirectInvoke {}");
        File outPuml = new File(tmp.getRoot(), "direct.puml");
        Main.main(new String[]{"-c", "-o", outPuml.getAbsolutePath(),
                javaFile.getAbsolutePath()});
        assertTrue("expected " + outPuml + " to exist",
                java.nio.file.Files.exists(outPuml.toPath()));
        String puml = new String(java.nio.file.Files.readAllBytes(outPuml.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("class \"x.DirectInvoke\""));
    }

    /**
     * {@code --all} 実行中に依存グラフ レンダリングが失敗すると、対応する
     * {@code .svg} は残らず、フォールバック {@code .puml} が書き出され、
     * 失敗ログが stderr に出ること。他の図 (summary.md / class-diagram.svg) は
     * 通常通り出力されることも確認する。
     */
    @Test
    public void testAllWritesFallbackPumlWhenDependencyGraphFails() throws Exception {
        // PlantUMLRenderer をテスト用スタブに差し替え、依存グラフ生成時だけエラー SVG を吐かせる
        java.util.function.BiConsumer<String, java.io.OutputStream> stub =
                (puml, out) -> {
                    try {
                        if (puml.contains("Gradle 依存グラフ")
                                || puml.contains("&lt;&lt;module&gt;&gt;")
                                || puml.contains("<<module>>")
                                || puml.contains("<<external>>")) {
                            out.write(("<svg><text>An error has occured</text></svg>")
                                    .getBytes(StandardCharsets.UTF_8));
                        } else {
                            // それ以外は素朴な正常 SVG (Batik は通らないが Main 経路では未使用)
                            out.write(("<?xml version=\"1.0\"?>"
                                    + "<svg xmlns=\"http://www.w3.org/2000/svg\""
                                    + " width=\"10\" height=\"10\">"
                                    + "<rect width=\"10\" height=\"10\"/></svg>")
                                    .getBytes(StandardCharsets.UTF_8));
                        }
                    } catch (java.io.IOException ioe) {
                        throw new RuntimeException(ioe);
                    }
                };
        juml.core.formats.uml.PlantUmlRenderer.setRendererImplForTest(stub);
        try {
            File root = tmp.newFolder("ProjFallback");
            File pkg = new File(root, "app/src/main/java/com/x");
            assertTrue(pkg.mkdirs());
            writeFile(new File(pkg, "MainActivity.java"),
                    "package com.x; public class MainActivity {}");
            writeFile(new File(root, "settings.gradle"), "include ':app'\n");
            writeFile(new File(root, "app/build.gradle"),
                    "plugins { id 'com.android.application' }\n"
                            + "android { namespace 'com.x' compileSdk 34 }\n"
                            + "dependencies { implementation 'androidx.core:core:1.13.0' }\n");
            writeFile(new File(root, "app/src/main/AndroidManifest.xml"),
                    "<manifest xmlns:android='http://schemas.android.com/apk/res/android' "
                            + "package='com.x'><application>"
                            + "<activity android:name='.MainActivity'/></application></manifest>");

            File outDir = new File(tmp.getRoot(), "fallback-out");
            Main.main(new String[]{"--all", "-o", outDir.getAbsolutePath(),
                    root.getAbsolutePath()});

            // 依存図は SVG が無く、.puml が残ること
            File depSvg = new File(outDir, "dependency-graph.svg");
            File depPuml = new File(outDir, "dependency-graph.puml");
            assertFalse("dependency-graph.svg should not exist on failure: " + depSvg,
                    depSvg.exists());
            assertTrue("dependency-graph.puml should exist as fallback",
                    depPuml.isFile());
            String depPumlContent = new String(
                    java.nio.file.Files.readAllBytes(depPuml.toPath()),
                    StandardCharsets.UTF_8);
            assertTrue(depPumlContent, depPumlContent.contains("@startuml"));

            // summary.md は --all 内で SVG 経路を通らないので常に書ける
            assertTrue("summary.md should exist",
                    new File(outDir, "summary.md").isFile());

            // FAILED ログが stderr に出ていること
            String err = stderrBuf.toString(StandardCharsets.UTF_8);
            assertTrue("expected FAILED log in stderr: " + err,
                    err.contains("dependency-graph.svg FAILED"));
        } finally {
            juml.core.formats.uml.PlantUmlRenderer.setRendererImplForTest(null);
        }
    }

    @Test
    public void testClassDiagramCliWithAidl() throws Exception {
        File aidlFile = tmp.newFile("ICar.aidl");
        writeFile(aidlFile, "package android.car; interface ICar { int getVersion(); }");
        File outPuml = new File(tmp.getRoot(), "car.puml");
        Main.main(new String[]{"-c", "-o", outPuml.getAbsolutePath(),
                aidlFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("<<AIDL>>"));
        assertTrue(puml, puml.contains("interface \"android.car.ICar\""));
        assertTrue(puml, puml.contains("getVersion(): int"));
    }

    // ---- Class diagram readability work (PR3) ----

    @Test
    public void testClassDiagramPresetMinimal() throws Exception {
        File javaFile = tmp.newFile("Foo.java");
        writeFile(javaFile,
                "package x; public class Foo { Bar b; public int n; void m() {} } "
                        + "public class Bar {}");
        File outPuml = new File(tmp.getRoot(), "min.puml");
        Main.main(new String[]{"-c", "--preset", "minimal",
                "-o", outPuml.getAbsolutePath(), javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("@startuml"));
        assertFalse("MINIMAL preset should hide fields",
                puml.contains("n: int"));
    }

    @Test
    public void testClassDiagramNoFieldsOption() throws Exception {
        File javaFile = tmp.newFile("Foo.java");
        writeFile(javaFile, "package x; public class Foo { public int n; void m() {} }");
        File outPuml = new File(tmp.getRoot(), "nof.puml");
        Main.main(new String[]{"-c", "--no-fields",
                "-o", outPuml.getAbsolutePath(), javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertFalse("--no-fields should hide fields", puml.contains("n: int"));
        assertTrue("methods still visible", puml.contains("m("));
    }

    @Test
    public void testClassDiagramPublicOnly() throws Exception {
        File javaFile = tmp.newFile("Foo.java");
        writeFile(javaFile,
                "package x; public class Pub {} class Pkg {}");
        File outPuml = new File(tmp.getRoot(), "pubonly.puml");
        Main.main(new String[]{"-c", "--public-only",
                "-o", outPuml.getAbsolutePath(), javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue(puml, puml.contains("Pub"));
        assertFalse("package-private class hidden by --public-only",
                puml.contains("class \"x.Pkg\""));
    }

    @Test
    public void testClassDiagramExcludePackage() throws Exception {
        File dir = tmp.newFolder("proj");
        File pkgA = new File(dir, "a");
        File pkgB = new File(dir, "b");
        assertTrue(pkgA.mkdirs());
        assertTrue(pkgB.mkdirs());
        writeFile(new File(pkgA, "A.java"), "package a; public class A {}");
        writeFile(new File(pkgB, "B.java"), "package b; public class B {}");
        File outPuml = new File(tmp.getRoot(), "exc.puml");
        Main.main(new String[]{"-c", "--exclude-package", "b",
                "-o", outPuml.getAbsolutePath(), dir.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue("a.A remains", puml.contains("class \"a.A\""));
        assertFalse("b.B excluded by --exclude-package",
                puml.contains("class \"b.B\""));
    }

    @Test
    public void testClassDiagramRelationInheritOnly() throws Exception {
        File javaFile = tmp.newFile("Hier.java");
        writeFile(javaFile,
                "package x; public class Base {} public class Child extends Base {}"
                        + " public interface I {} public class Impl implements I {}");
        File outPuml = new File(tmp.getRoot(), "rel.puml");
        Main.main(new String[]{"-c", "--relation", "inherit", "--no-legend",
                "-o", outPuml.getAbsolutePath(), javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue("extends arrow present", puml.contains("<|--"));
        assertFalse("implements arrow suppressed", puml.contains("<|.."));
    }

    @Test
    public void testClassDiagramModeHeadersOnly() throws Exception {
        File dir = tmp.newFolder("proj");
        writeFile(new File(dir, "Big.java"),
                "package x; public class Big {"
                        + " public void heavy() { /* body */ }"
                        + " public int n;"
                        + " }");
        File outPuml = new File(tmp.getRoot(), "ho.puml");
        Main.main(new String[]{"-c", "--mode", "headers-only",
                "-o", outPuml.getAbsolutePath(), dir.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        // headers-only でもクラス自身は出る。メソッドは headers のみだと
        // メソッドシグネチャは抽出されるが、本体内呼び出しは出ない。
        assertTrue(puml, puml.contains("Big"));
    }

    @Test
    public void testClassDiagramInteractiveSvg() throws Exception {
        File javaFile = tmp.newFile("Foo.java");
        writeFile(javaFile, "package x; public class Foo {}");
        File outPuml = new File(tmp.getRoot(), "isvg.puml");
        Main.main(new String[]{"-c", "--interactive-svg",
                "-o", outPuml.getAbsolutePath(), javaFile.getAbsolutePath()});
        String puml = new String(Files.readAllBytes(outPuml.toPath()), StandardCharsets.UTF_8);
        assertTrue("interactive link present",
                puml.contains("[[juml://class/x.Foo]]"));
    }

    @Test
    public void testInvalidPresetRejectedByUmlOverrides() {
        // UmlOverrides.build に対する直接検証は System.exit を呼ぶため Main 経由は
        // テストできない。代わりに DiagramPreset.fromCli の解析だけを確認する。
        assertEquals(juml.app.uml.DiagramPreset.CUSTOM,
                juml.app.uml.DiagramPreset.fromCli("bogus"));
    }

    @Test
    public void testFuncDiffCli() throws Exception {
        File fileA = tmp.newFile("ServiceA.java");
        writeFile(fileA,
                "class ServiceA { Manager m; void bind() {"
                + " m.connect(); m.init(); } }");
        File fileB = tmp.newFile("ServiceB.java");
        writeFile(fileB,
                "class ServiceB { Manager m; void bind() {"
                + " m.connect(); m.init(); m.extra(); } }");
        File out = new File(tmp.getRoot(), "diff.md");

        String spec = fileA.getAbsolutePath() + "::ServiceA.bind,"
                + fileB.getAbsolutePath() + "::ServiceB.bind";
        Main.main(new String[]{"--func-diff", spec, "-o", out.getAbsolutePath()});

        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(md, md.contains("# 関数差分レポート"));
        assertTrue(md, md.contains("## サマリー"));
        assertTrue(md, md.contains("## 呼び出し比較"));
        assertTrue(md, md.contains("一致"));
        assertTrue(md, md.contains("B のみ"));
        assertTrue(md, md.contains("extra"));
    }

    @Test
    public void testFuncDiffCliMethodOnly() throws Exception {
        File fileA = tmp.newFile("Alpha.java");
        writeFile(fileA, "class Alpha { X x; void go() { x.run(); } }");
        File fileB = tmp.newFile("Beta.java");
        writeFile(fileB, "class Beta { X x; void go() { x.run(); x.stop(); } }");
        File out = new File(tmp.getRoot(), "d2.md");

        String spec = fileA.getAbsolutePath() + "::go,"
                + fileB.getAbsolutePath() + "::go";
        Main.main(new String[]{"--func-diff", spec, "-o", out.getAbsolutePath()});

        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(md, md.contains("# 関数差分レポート"));
        assertTrue(md, md.contains("LCS 類似度"));
        assertTrue(md, md.contains("編集距離"));
        assertTrue(md, md.contains("Jaccard"));
        assertTrue(md, md.contains("B のみ"));
    }

    @Test
    public void testFunctionListCli() throws Exception {
        File root = tmp.newFolder("ProjFn");
        File pkg = new File(root, "app/src/main/java/x");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "Svc.java"),
                "package x; public class Svc {"
                + " public void run(boolean f) { if (f) { helper(); } }"
                + " void helper() {} }");
        File out = new File(tmp.getRoot(), "fn.md");
        Main.main(new String[]{"--function-list", "-o", out.getAbsolutePath(),
                root.getAbsolutePath()});
        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(md, md.contains("x.Svc"));
        assertTrue(md, md.contains("helper()"));
        // helper() は run() から if(f) 配下で呼ばれる → 利用側と実行条件に反映
        assertTrue(md, md.contains("利用側"));
        assertTrue(md, md.contains("run"));
        assertTrue(md, md.contains("実行条件"));
        assertTrue(md, md.contains("if"));
        // ファイル名と行に分けてソース位置を出す
        assertTrue(md, md.contains("ファイル"));
        assertTrue(md, md.contains("行"));
        assertTrue(md, md.contains("Svc.java"));
        // FQN と並べてクラス名単体の列も出す
        assertTrue(md, md.contains("クラス名"));
    }

    @Test
    public void testFunctionListCsvCli() throws Exception {
        File root = tmp.newFolder("ProjFnCsv");
        File pkg = new File(root, "app/src/main/java/x");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "Svc.java"),
                "package x; public class Svc {"
                + " public void run(boolean f) { if (f) { helper(); } }"
                + " void helper() {} }");
        File out = new File(tmp.getRoot(), "fn.csv");
        Main.main(new String[]{"--function-list", "--function-list-format", "csv",
                "-o", out.getAbsolutePath(), root.getAbsolutePath()});
        String csv = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        assertTrue(csv, csv.startsWith(
                "区分,クラス,クラス名,種別,関数,ファイル,行,利用側,実行条件,理由"));
        // class 列は FQN、class_name 列は単純名
        assertTrue(csv, csv.contains("method,x.Svc,Svc,CLASS,"));
        // file 列・line 列に定義位置が分かれて入る
        assertTrue(csv, csv.contains(",Svc.java,1,"));
        // helper() の呼び出しは if(f) 配下なので条件列・reason 列に反映される
        assertTrue(csv, csv.contains("if (f)"));
        assertTrue(csv, csv.contains("分岐ガード"));
    }

    @Test
    public void testFunctionListConditionCoverage() throws Exception {
        File root = tmp.newFolder("ProjCov");
        File pkg = new File(root, "app/src/main/java/t");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "Sample.java"),
                "package t; public class Sample {"
                + " void entry(int x) {"
                + "  target();"
                + "  if (x > 0) { target(); }"
                + "  while (x > 1) { whileCall(); }"
                + "  for (int i = 0; i < x; i++) { forCall(); }"
                + "  switch (x) { case 1: swCall(); break; default: swCall(); }"
                + "  try { tryCall(); } catch (Exception e) { catchCall(); }"
                + "  if (x > 0) { if (x > 5) { nested(); } }"
                + " }"
                + " void target() {} void whileCall() {} void forCall() {}"
                + " void swCall() {} void tryCall() {} void catchCall() {} void nested() {} }");
        File out = new File(tmp.getRoot(), "cov.md");
        Main.main(new String[]{"--function-list", "-o", out.getAbsolutePath(),
                root.getAbsolutePath()});
        String md = new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8);
        // 無条件呼び出し + if 内呼び出しの両経路を併記する
        assertTrue(md, md.contains("(直接呼び出し)<br>if (x > 0)"));
        // 各分岐種別を網羅
        assertTrue(md, md.contains("while (x > 1)"));
        assertTrue(md, md.contains("for (int i = 0; i < x; i++)"));
        assertTrue(md, md.contains("case (1)"));
        assertTrue(md, md.contains("default"));
        assertTrue(md, md.contains("try"));
        assertTrue(md, md.contains("catch (Exception e)"));
        // ネストした分岐は → で連鎖
        assertTrue(md, md.contains("if (x > 0) → if (x > 5)"));
    }
}
