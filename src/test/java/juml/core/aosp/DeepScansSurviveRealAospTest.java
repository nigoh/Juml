// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * bug-hunt R30: 実 AOSP ツリーの定型入力で VINTF / build.ninja / RRO /
 * .intermediates 解析が取りこぼし・誤報告・擬似ノードを出さないことの回帰テスト。
 *
 * <p>いずれも「規則が 1 つの経路にだけ適用され、兄弟経路に届いていない」型の
 * 確定バグの固定化。修正前の HEAD では各テストが FAILED になることを確認済み。</p>
 */
public class DeepScansSurviveRealAospTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    // --- VINTF parser ---

    /** HIDL の fqname {@code @1.0::IFoo/default} は version と interface に分解される。 */
    @Test
    public void hidlFqnameCarriesVersionIntoVersions() {
        VintfManifest m = VintfManifestParser.parse(
                "<manifest version=\"1.0\" type=\"device\"><hal format=\"hidl\">"
                + "<name>android.hardware.foo</name><transport>hwbinder</transport>"
                + "<fqname>@1.0::IFoo/default</fqname></hal></manifest>");
        VintfHal h = m.getHals().get(0);
        assertEquals(List.of("1.0"), h.getVersions());
        assertEquals("IFoo", h.getInterfaces().get(0).getName());
        assertEquals(List.of("default"), h.getInterfaces().get(0).getInstances());
    }

    /** compat-matrix の {@code <regex-instance>} は instance として取り込まれる。 */
    @Test
    public void regexInstanceIsNotDropped() {
        VintfManifest m = VintfManifestParser.parse(
                "<compatibility-matrix version=\"1.0\" type=\"framework\">"
                + "<hal format=\"aidl\"><name>x.y</name><interface><name>IA</name>"
                + "<regex-instance>legacy/[0-9]+</regex-instance></interface></hal>"
                + "</compatibility-matrix>");
        assertEquals(List.of("legacy/[0-9]+"),
                m.getHals().get(0).getInterfaces().get(0).getInstances());
    }

    /** matrix 形式の複数 {@code <kernel>} と {@code <sepolicy-version>} が読める。 */
    @Test
    public void matrixKernelsAndSepolicyVersionAreParsed() {
        VintfManifest m = VintfManifestParser.parse(
                "<compatibility-matrix version=\"1.0\" type=\"framework\" level=\"5\">"
                + "<kernel version=\"4.9.0\"/><kernel version=\"4.14.0\"/>"
                + "<sepolicy><kernel-sepolicy-version>30</kernel-sepolicy-version>"
                + "<sepolicy-version>25.0</sepolicy-version></sepolicy>"
                + "</compatibility-matrix>");
        assertEquals(List.of("4.9.0", "4.14.0"), m.getKernelVersions());
        assertEquals("25.0", m.getSepolicyVersion());
        assertEquals("framework", m.getMatrixType());
    }

    // --- VINTF diagram / report ---

    /** 同名 HAL の後続宣言は捨てずに 1 ノードへマージされる。 */
    @Test
    public void diagramMergesSameNameHalDeclarations() {
        VintfManifest fw = VintfManifestParser.parse(
                "<manifest version=\"1.0\" type=\"framework\">"
                + "<hal format=\"aidl\"><name>android.hardware.vibrator</name>"
                + "<fqname>IVibrator/default</fqname></hal>"
                + "<hal format=\"aidl\"><name>android.hardware.vibrator</name>"
                + "<version>2</version><fqname>IVibratorManager/default</fqname></hal>"
                + "</manifest>");
        List<VintfProjectScanner.Entry> entries = new ArrayList<>();
        entries.add(new VintfProjectScanner.Entry("fw.xml", fw));
        String puml = PlantUmlVintfDiagram.render(entries);
        assertTrue("2 件目の interface が残ること", puml.contains("IVibratorManager"));
        assertTrue("2 件目の version が残ること", puml.contains("2"));
        // "  component " 宣言は 1 つだけ (凡例の "component =" は数えない)
        assertEquals(1, puml.split("  component ", -1).length - 1);
    }

    /** device matrix の要求は framework manifest と突き合わせる (false missing を出さない)。 */
    @Test
    public void deviceMatrixIsCheckedAgainstFrameworkManifest() {
        VintfManifest devMatrix = VintfManifestParser.parse(
                "<compatibility-matrix version=\"1.0\" type=\"device\">"
                + "<hal format=\"hidl\" optional=\"false\">"
                + "<name>android.frameworks.schedulerservice</name></hal>"
                + "</compatibility-matrix>");
        VintfManifest fw = VintfManifestParser.parse(
                "<manifest version=\"1.0\" type=\"framework\"><hal format=\"hidl\">"
                + "<name>android.frameworks.schedulerservice</name></hal></manifest>");
        VintfManifest dev = VintfManifestParser.parse(
                "<manifest version=\"1.0\" type=\"device\"><hal format=\"aidl\">"
                + "<name>android.hardware.audio</name></hal></manifest>");
        List<VintfProjectScanner.Entry> entries = new ArrayList<>();
        entries.add(new VintfProjectScanner.Entry("dev.xml", dev));
        entries.add(new VintfProjectScanner.Entry("matrix.xml", devMatrix));
        entries.add(new VintfProjectScanner.Entry("fw.xml", fw));
        String md = MarkdownVintfReport.render(entries);
        assertFalse("framework が宣言済みの HAL を missing と誤報告しないこと",
                md.contains("schedulerservice` (device manifest に無い)"));
        assertTrue(md.contains("宣言されています"));
        String puml = PlantUmlVintfDiagram.render(entries);
        assertFalse("赤 (missing) 強調が出ないこと",
                puml.contains(PlantUmlVintfDiagram.MISSING_FILL));
        assertTrue("満たしている緑破線が framework 側へ出ること",
                puml.contains("declared"));
    }

    /** セルに {@code |} を含む値 (パス等) が表の列を割らない。 */
    @Test
    public void reportsEscapePipesInTableCells() {
        VintfManifest m = VintfManifestParser.parse(
                "<manifest version=\"1.0\" type=\"device\"><hal format=\"aidl\">"
                + "<name>a.b</name></hal></manifest>");
        List<VintfProjectScanner.Entry> entries = new ArrayList<>();
        entries.add(new VintfProjectScanner.Entry("res/a|b/manifest.xml", m));
        assertTrue(MarkdownVintfReport.render(entries).contains("a\\|b"));

        RroOverlay o = new RroOverlayDetector().parseManifest(
                "<manifest package=\"com.x\"><overlay android:targetPackage=\"t\"/></manifest>",
                "res/a|b/AndroidManifest.xml");
        assertTrue(MarkdownRroReport.render(List.of(o)).contains("a\\|b"));
    }

    // --- build.ninja ---

    /** ninja 1.11+ の validations 区切り {@code |@} から擬似ノードを作らない。 */
    @Test
    public void ninjaValidationsSeparatorMakesNoPseudoNode() throws IOException {
        File f = tmp.newFile("a.ninja");
        Files.writeString(f.toPath(), "build out/soong/.intermediates/a/b/x.o: cc src/x.c"
                + " |@ out/soong/.intermediates/lint/y/v.txt\n");
        BuildNinjaGraph g = new BuildNinjaGraph();
        new BuildNinjaParser().parseFile(f, g);
        for (String edge : g.getGroupEdges().keySet()) {
            assertFalse("擬似ノード '@' が生成されないこと: " + edge, edge.contains("@"));
        }
        assertTrue(g.getGroupEdges().containsKey("a/b -> lint/y"));
    }

    /** 末尾が {@code $} のコメント行は継続せず、次の build 文を飲み込まない。 */
    @Test
    public void ninjaCommentEndingWithDollarDoesNotSwallowNextBuild() throws IOException {
        File f = tmp.newFile("b.ninja");
        Files.writeString(f.toPath(), "# comment ends with dollar $\n"
                + "build out/soong/.intermediates/c/d/z.o: cc src/z.c\n");
        BuildNinjaGraph g = new BuildNinjaGraph();
        new BuildNinjaParser().parseFile(f, g);
        assertEquals(1, g.getBuildStatements());
        assertTrue(g.getGroupEdges().containsKey("c/d -> src"));
    }

    /** 読めない *.ninja は 1 件スキップで続行し、全体を中断しない。 */
    @Test
    public void ninjaUnreadableFileIsSkippedNotFatal() throws IOException {
        File proj = tmp.newFolder("proj");
        Files.writeString(new File(proj, "good.ninja").toPath(),
                "build out/x.o: cc src/x.c\n");
        File bad = new File(proj, "bad.ninja");
        Files.writeString(bad.toPath(), "x");
        assertTrue(bad.setReadable(false));
        // root は mode に関わらず読めてしまい、スキップ経路に入らないため skip
        Assume.assumeFalse("root では読み取り拒否を再現できない", bad.canRead());
        try {
            BuildNinjaGraph g = new BuildNinjaParser().analyzeProject(proj);
            assertEquals("良品側は解析されること", 1, g.getBuildStatements());
        } finally {
            bad.setReadable(true);
        }
    }

    /** ブロック note はリテラル {@code \n} でなく実改行で行を分ける。 */
    @Test
    public void summaryNotesUseRealNewlines() throws IOException {
        File f = tmp.newFile("c.ninja");
        Files.writeString(f.toPath(), "build out/x.o: cc src/x.c\n");
        BuildNinjaGraph g = new BuildNinjaGraph();
        new BuildNinjaParser().parseFile(f, g);
        String ninjaPuml = PlantUmlBuildNinjaDiagram.render(g);
        assertFalse("note 内にリテラル \\n を出さないこと", ninjaPuml.contains("\\n"));

        IntermediatesInventory inv = new IntermediatesInventory();
        inv.moduleFor("a", "b").addArtifact("jar", 1);
        String imPuml = PlantUmlIntermediatesDiagram.render(inv);
        int noteStart = imPuml.indexOf("note as Summary");
        assertFalse("note 内にリテラル \\n を出さないこと",
                imPuml.substring(noteStart).contains("\\n"));
    }

    // --- .intermediates / RRO ---

    /** {@code libcrypto_static} のようなモジュール名を variant と誤認しない。 */
    @Test
    public void variantHeuristicDoesNotEatModuleNames() throws IOException {
        File root = tmp.newFolder("tree");
        String base = "out/soong/.intermediates/external/boringssl/";
        for (String mod : new String[] {"libcrypto_static", "libssl_static"}) {
            File v = new File(root, base + mod + "/android_arm64_armv8-a_static");
            assertTrue(v.mkdirs());
            Files.writeString(new File(v, mod + ".a").toPath(), "x");
        }
        IntermediatesInventory inv = new IntermediatesAnalyzer().analyzeProject(root);
        assertEquals("別モジュールが合成されないこと", 2, inv.getModules().size());
        for (IntermediateModule m : inv.getModules()) {
            assertEquals("external/boringssl", m.getModulePath());
            assertEquals(List.of("android_arm64_armv8-a_static"),
                    new ArrayList<>(m.getVariants()));
        }
    }

    /** '_' 構成だけが違うモジュール同士で component alias が衝突しない。 */
    @Test
    public void intermediatesAliasesStayDistinct() {
        IntermediatesInventory inv = new IntermediatesInventory();
        inv.moduleFor("hardware_x", "foo").addArtifact("a", 1);
        inv.moduleFor("hardware", "x_foo").addArtifact("a", 1);
        String puml = PlantUmlIntermediatesDiagram.render(inv);
        java.util.Set<String> aliases = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("as (im_\\S+)").matcher(puml);
        while (m.find()) {
            aliases.add(m.group(1));
        }
        assertEquals("alias が一意で片方が消えないこと", 2, aliases.size());
    }

    /** RRO 走査も兄弟走査と同じ共有除外 (prebuilts 等) を使う。 */
    @Test
    public void rroScanUsesSharedExcludes() throws IOException {
        File root = tmp.newFolder("rro");
        File good = new File(root, "device/x/overlay");
        assertTrue(good.mkdirs());
        Files.writeString(new File(good, "AndroidManifest.xml").toPath(),
                "<manifest package=\"com.x.rro\">"
                + "<overlay android:targetPackage=\"com.t\"/></manifest>");
        File pre = new File(root, "prebuilts/y");
        assertTrue(pre.mkdirs());
        Files.writeString(new File(pre, "AndroidManifest.xml").toPath(),
                "<manifest package=\"com.pre.rro\">"
                + "<overlay android:targetPackage=\"com.t\"/></manifest>");
        List<RroOverlay> out = new RroOverlayDetector().analyzeProject(root);
        assertEquals(1, out.size());
        assertEquals("com.x.rro", out.get(0).getOverlayPackage());
    }
}
