// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 実プロジェクトで遭遇しがちな Gradle DSL の難しめのケースを集めたストレステスト。
 */
public class GradleScriptParserStressTest {

    @Test
    public void testKotlinDslBuilderFunctions() {
        // Kotlin DSL の getByName / create / maybeCreate / register を builder 関数として
        // 認識し、内側の文字列引数を本来のブロック名として扱う
        String src =
                "android {\n"
                        + "  buildTypes {\n"
                        + "    getByName(\"release\") { isMinifyEnabled = true }\n"
                        + "    create(\"benchmark\") { initWith(getByName(\"release\")) }\n"
                        + "    maybeCreate(\"debug\")\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue("release", info.getBuildTypes().containsKey("release"));
        assertTrue("benchmark", info.getBuildTypes().containsKey("benchmark"));
        // getByName/create 自体は名前としては取り込まれない
        assertFalse(info.getBuildTypes().containsKey("getByName"));
        assertFalse(info.getBuildTypes().containsKey("create"));
    }

    @Test
    public void testNonQuotedSuffixIsSkipped() {
        // 識別子参照は文字列値として採用しない (NiaBuildType.DEBUG.applicationIdSuffix 等)
        String src =
                "android {\n"
                        + "  buildTypes {\n"
                        + "    release {\n"
                        + "      applicationIdSuffix BuildConstants.RELEASE_SUFFIX\n"
                        + "    }\n"
                        + "    debug {\n"
                        + "      applicationIdSuffix \".debug\"\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertNull(info.getBuildTypes().get("release").getApplicationIdSuffix());
        assertEquals(".debug", info.getBuildTypes().get("debug").getApplicationIdSuffix());
    }

    @Test
    public void testVersionCatalogIgnoredGracefully() {
        // libs.androidx.appcompat 形式 (version catalog) は notation 形式に合わないので
        // 無視するが、他の依存は正しく拾う
        String src =
                "dependencies {\n"
                        + "  implementation(libs.androidx.appcompat)\n"
                        + "  implementation('androidx.core:core-ktx:1.12.0')\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        // version catalog は文字列リテラルではないので 1 つだけ拾える
        assertEquals(1, info.getDependencies().size());
        assertEquals("androidx.core:core-ktx:1.12.0",
                info.getDependencies().get(0).getNotation());
    }

    @Test
    public void testNestedPluginsBlocks() {
        // subprojects { plugins { ... } } 形式: 内側の id は外側にも拾われる
        String src =
                "subprojects {\n"
                        + "  plugins {\n"
                        + "    id 'com.android.application'\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertTrue(info.getPlugins().contains("com.android.application"));
    }

    @Test
    public void testStringInterpolationInDependency() {
        // ${} 補完を含む文字列も notation として保持
        String src =
                "dependencies {\n"
                        + "  implementation \"group:name:${rootProject.ext.libVersion}\"\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(1, info.getDependencies().size());
        assertTrue(info.getDependencies().get(0).getNotation().contains("${"));
    }

    @Test
    public void testPlatformBomDependencyExtracted() {
        // implementation platform("...") / api(enforcedPlatform('...')) の BOM 依存を抽出する。
        String src =
                "dependencies {\n"
                        + "  implementation platform(\"androidx.compose:compose-bom:2024.02.00\")\n"
                        + "  api(enforcedPlatform('org.junit:junit-bom:5.10.0'))\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(2, info.getDependencies().size());
        assertTrue(info.getDependencies().stream().anyMatch(d ->
                d.getNotation().contains("androidx.compose:compose-bom:2024.02.00")));
        assertTrue(info.getDependencies().stream().anyMatch(d ->
                d.getNotation().contains("org.junit:junit-bom:5.10.0")));
    }

    @Test
    public void testMultiLineDependency() {
        // group + name + version をマップ形式で指定するケース。group:name:version 形式
        // ではないので best-effort で無視され、警告も出ない (静かにスキップ)
        String src =
                "dependencies {\n"
                        + "  implementation group: 'androidx.core', name: 'core', version: '1.0'\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        // notation 形式ではないので拾えない (現状の制限)
        assertEquals(0, info.getDependencies().size());
    }

    @Test
    public void testMultipleApplyPlugin() {
        String src =
                "apply plugin: 'com.android.application'\n"
                        + "apply plugin: 'kotlin-android'\n"
                        + "apply plugin: 'kotlin-kapt'\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(3, info.getPlugins().size());
        assertTrue(info.getPlugins().contains("com.android.application"));
        assertTrue(info.getPlugins().contains("kotlin-android"));
        assertTrue(info.getPlugins().contains("kotlin-kapt"));
    }

    @Test
    public void testMixedQuoteStyles() {
        // シングル/ダブルクォート混在
        String src =
                "android {\n"
                        + "  namespace 'com.a'\n"
                        + "  defaultConfig {\n"
                        + "    applicationId \"com.b\"\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals("com.a", info.getNamespace());
        assertEquals("com.b", info.getApplicationId());
    }

    @Test
    public void testKotlinDslListAssignment() {
        // Kotlin: flavorDimensions += "env" や = listOf("env") など。
        // 我々の現在のパターンは "flavorDimensions ..." 直後の文字列を拾うので拾える可能性あり
        String src =
                "android {\n"
                        + "  flavorDimensions(\"env\", \"tier\")\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getFlavorDimensions().contains("env"));
        assertTrue(info.getFlavorDimensions().contains("tier"));
    }

    @Test
    public void testEmptyAndroidBlock() {
        // 空の android {} ブロックがあってもクラッシュしない
        String src = "android {}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertNull(info.getApplicationId());
        assertTrue(info.getBuildTypes().isEmpty());
    }

    @Test
    public void testCommentBeforeBlock() {
        // ブロック前にコメントがあってもパースできる
        String src =
                "// comment\n"
                        + "/* multi\n"
                        + "   line */\n"
                        + "android {\n"
                        + "  namespace 'com.x'\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals("com.x", info.getNamespace());
    }
}
