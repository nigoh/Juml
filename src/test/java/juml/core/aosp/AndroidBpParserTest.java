// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Android.bp (Soong) パーサのテスト。
 */
public class AndroidBpParserTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void parsesSingleCcLibrary() {
        String src = "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    srcs: [\"foo.cpp\", \"bar.cpp\"],\n"
                + "    shared_libs: [\"libbase\"],\n"
                + "    static_libs: [\"libutils\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(1, mods.size());
        AndroidBpModule m = mods.get(0);
        assertEquals("cc_library", m.getType());
        assertEquals("libfoo", m.getName());
        assertEquals(2, m.getSrcs().size());
        assertTrue(m.getSrcs().contains("foo.cpp"));
        assertTrue(m.getDeps().contains("libbase"));
        assertTrue(m.getDeps().contains("libutils"));
        assertEquals("cc", m.getCategory());
    }

    @Test
    public void parsesMultipleModules() {
        String src = "java_library {\n"
                + "    name: \"MyLib\",\n"
                + "    srcs: [\"src/main/java/**/*.java\"],\n"
                + "    libs: [\"androidx\"],\n"
                + "}\n"
                + "\n"
                + "android_app {\n"
                + "    name: \"MyApp\",\n"
                + "    static_libs: [\"MyLib\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(2, mods.size());
        assertEquals("java", mods.get(0).getCategory());
        assertEquals("android", mods.get(1).getCategory());
        assertTrue(mods.get(1).getDeps().contains("MyLib"));
    }

    @Test
    public void ignoresComments() {
        String src = "// top-level comment\n"
                + "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    /* block comment\n"
                + "       multi line */\n"
                + "    srcs: [\"foo.cpp\"], // trailing\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(1, mods.size());
        assertEquals("libfoo", mods.get(0).getName());
        assertEquals(1, mods.get(0).getSrcs().size());
    }

    @Test
    public void handlesNestedBlocks() {
        String src = "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    target: {\n"
                + "        android: {\n"
                + "            srcs: [\"android_only.cpp\"],\n"
                + "        },\n"
                + "    },\n"
                + "    srcs: [\"common.cpp\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(1, mods.size());
        // 全 srcs プロパティを集めるので両方拾える
        assertTrue(mods.get(0).getSrcs().contains("common.cpp"));
        assertTrue(mods.get(0).getSrcs().contains("android_only.cpp"));
    }

    @Test
    public void nameNotShadowedByNestedBlockName() {
        // ネストブロック (target/arch 等) 内の name: が先に現れても、
        // モジュール名はトップレベルの name: を採用すること。
        String src = "cc_library {\n"
                + "    target: {\n"
                + "        android: {\n"
                + "            name: \"wrong_nested_name\",\n"
                + "        },\n"
                + "    },\n"
                + "    name: \"libfoo\",\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals(1, mods.size());
        assertEquals("libfoo", mods.get(0).getName());
    }

    @Test
    public void nestedSrcsStillAggregatedIntoModule() {
        // 設計上の境界を固定: srcs はトップレベルとネスト (arch/target) の両方を
        // 集約する従来挙動を維持する (handlesNestedBlocks と同方針)。name の
        // トップレベル限定化が srcs 集約に波及していないことを担保する。
        String src = "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    arch: { arm: { srcs: [\"arm_only.S\"] } },\n"
                + "    srcs: [\"common.cpp\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals("libfoo", mods.get(0).getName());
        assertTrue(mods.get(0).getSrcs().contains("common.cpp"));
        assertTrue(mods.get(0).getSrcs().contains("arm_only.S"));
    }

    @Test
    public void inheritsDefaultsDeps() {
        // cc_defaults 経由のプロパティ継承を解決し、参照先の srcs/依存を取り込む
        String src = "cc_defaults {\n"
                + "    name: \"x_defaults\",\n"
                + "    shared_libs: [\"liblog\"],\n"
                + "    srcs: [\"shared.c\"],\n"
                + "}\n"
                + "cc_library {\n"
                + "    name: \"libx\",\n"
                + "    defaults: [\"x_defaults\"],\n"
                + "    srcs: [\"x.c\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        AndroidBpParser.resolveDefaults(mods);
        AndroidBpModule libx = mods.stream()
                .filter(m -> m.getName().equals("libx")).findFirst().orElse(null);
        assertNotNull(libx);
        assertTrue("inherited real dep", libx.getDeps().contains("liblog"));
        assertTrue("inherited srcs", libx.getSrcs().contains("shared.c"));
        assertTrue("own srcs kept", libx.getSrcs().contains("x.c"));
        assertTrue("defaults reference preserved", libx.getDeps().contains("x_defaults"));
    }

    @Test
    public void doesNotTransitivelyExpandNonDefaultsLibs() {
        // 通常の cc_library 依存は推移展開しない (継承は *_defaults 参照のみ)
        String src = "cc_library { name: \"libfoo\", shared_libs: [\"libbar\"] }\n"
                + "cc_library { name: \"libapp\", shared_libs: [\"libfoo\"] }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        AndroidBpParser.resolveDefaults(mods);
        AndroidBpModule libapp = mods.stream()
                .filter(m -> m.getName().equals("libapp")).findFirst().orElse(null);
        assertNotNull(libapp);
        assertTrue(libapp.getDeps().contains("libfoo"));
        // libfoo の依存 libbar を libapp に引き込まないこと
        assertTrue(!libapp.getDeps().contains("libbar"));
    }

    @Test
    public void resolvesChainedDefaultsWithoutInfiniteLoop() {
        // defaults の連鎖と循環参照でも無限ループせず、全 *_defaults の依存を集約する
        String src = "cc_defaults { name: \"a_def\", defaults: [\"b_def\"], shared_libs: [\"liba\"] }\n"
                + "cc_defaults { name: \"b_def\", defaults: [\"a_def\"], shared_libs: [\"libb\"] }\n"
                + "cc_library { name: \"libc\", defaults: [\"a_def\"] }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        AndroidBpParser.resolveDefaults(mods);
        AndroidBpModule libc = mods.stream()
                .filter(m -> m.getName().equals("libc")).findFirst().orElse(null);
        assertNotNull(libc);
        assertTrue(libc.getDeps().contains("liba"));
        assertTrue(libc.getDeps().contains("libb"));
    }

    @Test
    public void diagramOutputContainsModuleAndEdge() {
        String src = "cc_library { name: \"libfoo\", shared_libs: [\"libbar\"] }\n"
                + "cc_library { name: \"libbar\" }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        String puml = PlantUmlSoongDependencyDiagram.render(mods);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("libfoo"));
        assertTrue(puml.contains("libbar"));
        // edge: libfoo -[shared_libs style]-> libbar (種別ごとに矢印スタイルが変わる)
        assertTrue(puml.contains("m_libfoo -[#1f6feb]-> m_libbar"));
    }

    @Test
    public void externalDepGoesIntoExternalGroup() {
        String src = "cc_library { name: \"libfoo\", shared_libs: [\"libsystemexternal\"] }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        String puml = PlantUmlSoongDependencyDiagram.render(mods);
        assertTrue(puml.contains("package \"external\""));
        assertTrue(puml.contains("libsystemexternal"));
    }

    @Test
    public void markdownReportSummarizesByCategory() {
        String src = "cc_library { name: \"libfoo\" }\n"
                + "java_library { name: \"BarLib\", libs: [\"libfoo\"] }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        String md = MarkdownSoongReport.render(mods);
        assertTrue(md.contains("Soong"));
        assertTrue(md.contains("cc"));
        assertTrue(md.contains("java"));
        assertTrue(md.contains("libfoo"));
        assertTrue(md.contains("BarLib"));
    }

    @Test
    public void reportShowsTypeHistogramAndPartition() {
        String src = "cc_library { name: \"libfoo\" }\n"
                + "cc_library { name: \"libbar\", vendor: true }\n"
                + "android_app { name: \"App\", product_specific: true }\n"
                + "cc_test { name: \"libfoo_test\" }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        String md = MarkdownSoongReport.render(mods);
        assertTrue(md.contains("種別ヒストグラム"));
        assertTrue(md.contains("`cc_library`"));
        assertTrue(md.contains("配置"));
        assertTrue(md.contains("vendor"));
        assertTrue(md.contains("product"));
        // テストモジュールの件数表示
        assertTrue(md.contains("test modules"));
        // パーティション列の表ヘッダ
        assertTrue(md.contains("| Name | Type | Partition | SDK | Deps | Srcs | Location |"));
    }

    @Test
    public void reportOmitsPartitionSectionWhenAllSystem() {
        String src = "cc_library { name: \"libfoo\" }\n";
        String md = MarkdownSoongReport.render(
                new AndroidBpParser().parseSource(src, "Android.bp"));
        // 全部 system のときは Partition placement セクションを出さない
        assertTrue(!md.contains("Partition placement"));
    }

    @Test
    public void parsesAidlInterfaceMetadata() {
        String src = "aidl_interface {\n"
                + "    name: \"android.hardware.vehicle\",\n"
                + "    stability: \"vintf\",\n"
                + "    versions: [\"1\", \"2\", \"3\"],\n"
                + "    backend: {\n"
                + "        java: { enabled: false },\n"
                + "        cpp: { enabled: true },\n"
                + "        ndk: { enabled: true },\n"
                + "    },\n"
                + "}\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertEquals("aidl", m.getCategory());
        assertEquals("vintf", m.scalar("stability"));
        assertEquals("3", m.scalar("versions_count"));
        assertEquals("3", m.scalar("latest_version"));
        // java は enabled:false なので除外、cpp/ndk が残る
        assertEquals("cpp,ndk", m.scalar("backends"));
    }

    @Test
    public void versionsWithInfoCountsOnlyVersionScalarsNotNestedImports() {
        // versions_with_info の要素マップの version だけを数える。imports の要素まで
        // 拾うと versions_count / latest_version が誤る (以前は 3 / "…bar-V1" になっていた)。
        String src = "aidl_interface {\n"
                + "    name: \"android.hardware.foo\",\n"
                + "    versions_with_info: [\n"
                + "        { version: \"1\", imports: [] },\n"
                + "        { version: \"2\", imports: [\"android.hardware.bar-V1\"] },\n"
                + "    ],\n"
                + "}\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertEquals("2", m.scalar("versions_count"));
        assertEquals("2", m.scalar("latest_version"));
    }

    @Test
    public void aidlBackendDefaultsWhenNoBackendBlock() {
        String src = "aidl_interface { name: \"x\", unstable: true }\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertEquals("java,cpp,ndk", m.scalar("backends"));
        assertTrue(m.boolProp("unstable"));
    }

    @Test
    public void reportListsAidlInterfaces() {
        String src = "aidl_interface {\n"
                + "    name: \"android.hardware.foo\",\n"
                + "    stability: \"vintf\",\n"
                + "    versions: [\"1\"],\n"
                + "}\n";
        String md = MarkdownSoongReport.render(
                new AndroidBpParser().parseSource(src, "Android.bp"));
        assertTrue(md.contains("AIDL インタフェース"));
        assertTrue(md.contains("android.hardware.foo"));
        assertTrue(md.contains("vintf"));
    }

    @Test
    public void emptyProjectRendersPlaceholder() {
        String md = MarkdownSoongReport.render(new java.util.ArrayList<>());
        assertNotNull(md);
        assertTrue(md.contains("no Android.bp"));
    }

    @Test
    public void tracksDependencyKindSeparately() {
        // 依存を種別 (static_libs / shared_libs / header_libs) ごとに保持すること。
        String src = "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    shared_libs: [\"libbase\"],\n"
                + "    static_libs: [\"libutils\"],\n"
                + "    header_libs: [\"libfoo_headers\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        AndroidBpModule m = mods.get(0);
        assertEquals("shared_libs", m.kindOf("libbase"));
        assertEquals("static_libs", m.kindOf("libutils"));
        assertEquals("header_libs", m.kindOf("libfoo_headers"));
        assertTrue(m.getDepsByKind().containsKey("shared_libs"));
        assertEquals("", m.kindOf("not_a_dep"));
        // 結合ビューも従来どおり全件含む
        assertTrue(m.getDeps().contains("libbase"));
        assertTrue(m.getDeps().contains("libutils"));
        assertTrue(m.getDeps().contains("libfoo_headers"));
    }

    @Test
    public void inheritedDefaultsKeepDependencyKind() {
        // defaults 継承で取り込む依存も、参照先で宣言された種別を保つこと。
        String src = "cc_defaults {\n"
                + "    name: \"x_defaults\",\n"
                + "    static_libs: [\"libutils\"],\n"
                + "}\n"
                + "cc_library {\n"
                + "    name: \"libx\",\n"
                + "    defaults: [\"x_defaults\"],\n"
                + "    shared_libs: [\"libbase\"],\n"
                + "}\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        AndroidBpParser.resolveDefaults(mods);
        AndroidBpModule libx = mods.stream()
                .filter(m -> m.getName().equals("libx")).findFirst().orElse(null);
        assertNotNull(libx);
        assertEquals("static_libs", libx.kindOf("libutils"));
        assertEquals("shared_libs", libx.kindOf("libbase"));
    }

    @Test
    public void capturesScalarPropertiesAndPartition() {
        // vendor:true → vendor パーティション、文字列/数値スカラも保持
        String src = "cc_library {\n"
                + "    name: \"libveh\",\n"
                + "    vendor: true,\n"
                + "    min_sdk_version: \"29\",\n"
                + "    stem: \"libvehicle\",\n"
                + "}\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertTrue(m.boolProp("vendor"));
        assertEquals("vendor", m.getPartition());
        assertEquals("29", m.scalar("min_sdk_version"));
        assertEquals("libvehicle", m.scalar("stem"));
    }

    @Test
    public void partitionDefaultsToSystem() {
        String src = "java_library { name: \"BarLib\" }\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertEquals("system", m.getPartition());
        assertEquals("", m.scalar("min_sdk_version"));
    }

    @Test
    public void productAndSystemExtPartitions() {
        String src = "android_app { name: \"P\", product_specific: true }\n"
                + "android_app { name: \"S\", system_ext_specific: true }\n";
        List<AndroidBpModule> mods = new AndroidBpParser().parseSource(src, "Android.bp");
        assertEquals("product", mods.get(0).getPartition());
        assertEquals("system_ext", mods.get(1).getPartition());
    }

    @Test
    public void scalarNotTakenFromNestedBlock() {
        // ネストブロック内の vendor:true はモジュール配置に影響させない
        String src = "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    target: { vendor: { cflags: [\"-DX\"] } },\n"
                + "}\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertEquals("system", m.getPartition());
    }

    @Test
    public void resolvesVariableReferenceInSrcs() {
        String src = "common_srcs = [\"a.cpp\", \"b.cpp\"]\n"
                + "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    srcs: common_srcs,\n"
                + "}\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertTrue(m.getSrcs().contains("a.cpp"));
        assertTrue(m.getSrcs().contains("b.cpp"));
    }

    @Test
    public void resolvesConcatenationOfVariableAndLiteral() {
        String src = "base = [\"a.cpp\"]\n"
                + "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    srcs: base + [\"b.cpp\", \"c.cpp\"],\n"
                + "    shared_libs: common_libs + [\"liblog\"],\n"
                + "}\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertTrue(m.getSrcs().contains("a.cpp"));
        assertTrue(m.getSrcs().contains("b.cpp"));
        assertTrue(m.getSrcs().contains("c.cpp"));
        // 未定義変数 common_libs は無視され、リテラル分だけ残る
        assertTrue(m.getDeps().contains("liblog"));
        assertEquals("shared_libs", m.kindOf("liblog"));
    }

    @Test
    public void resolvesPlusEqualsVariableAppend() {
        String src = "srcs_list = [\"a.cpp\"]\n"
                + "srcs_list += [\"b.cpp\"]\n"
                + "cc_library {\n"
                + "    name: \"libfoo\",\n"
                + "    srcs: srcs_list,\n"
                + "}\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertTrue(m.getSrcs().contains("a.cpp"));
        assertTrue(m.getSrcs().contains("b.cpp"));
    }

    @Test
    public void variableDefinedFromAnotherVariable() {
        String src = "a = [\"x.cpp\"]\n"
                + "b = a + [\"y.cpp\"]\n"
                + "cc_library { name: \"libfoo\", srcs: b }\n";
        AndroidBpModule m = new AndroidBpParser().parseSource(src, "Android.bp").get(0);
        assertTrue(m.getSrcs().contains("x.cpp"));
        assertTrue(m.getSrcs().contains("y.cpp"));
    }

    @Test
    public void stripCommentsPreservesLength() {
        String src = "abc /* xxx */ def // tail\nghi";
        String stripped = AndroidBpParser.stripComments(src);
        assertEquals(src.length(), stripped.length());
        // 元のオフセット位置で行情報が壊れていないこと
        assertEquals('\n', stripped.charAt(src.indexOf('\n')));
    }

    @Test
    public void analyzeProjectSkipsPrebuiltsRepoAndOutSoong() throws java.io.IOException {
        java.io.File root = tmp.newFolder("tree");
        writeBp(new java.io.File(root, "Android.bp"), "libmain");
        // AOSP フルツリー指定時のノイズ源はスキップされること
        // (AndroidProjectScanner の既定除外との整合)
        writeBp(new java.io.File(root, "prebuilts/module/Android.bp"), "libprebuilt");
        writeBp(new java.io.File(root, ".repo/Android.bp"), "librepo");
        writeBp(new java.io.File(root, "out-soong/Android.bp"), "liboutsoong");
        writeBp(new java.io.File(root, "out/Android.bp"), "libout");

        List<AndroidBpModule> mods = new AndroidBpParser().analyzeProject(root);
        assertEquals(1, mods.size());
        assertEquals("libmain", mods.get(0).getName());
    }

    private static void writeBp(java.io.File f, String moduleName)
            throws java.io.IOException {
        java.io.File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            assertTrue(parent.mkdirs());
        }
        java.nio.file.Files.write(f.toPath(),
                ("cc_library { name: \"" + moduleName + "\" }\n")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
