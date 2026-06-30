// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * GradleScriptParser に VersionCatalog を渡したときの解決動作。
 */
public class GradleScriptCatalogResolutionTest {

    private VersionCatalog catalog;

    @Before
    public void setup() {
        String toml =
                "[versions]\n"
                        + "agp = \"8.1.0\"\n"
                        + "compileSdk = \"34\"\n"
                        + "minSdk = \"24\"\n"
                        + "appcompat = \"1.6.1\"\n"
                        + "[libraries]\n"
                        + "androidx-appcompat = { module = \"androidx.appcompat:appcompat\","
                        + " version.ref = \"appcompat\" }\n"
                        + "androidx-core-ktx = { group = \"androidx.core\","
                        + " name = \"core-ktx\", version = \"1.13.0\" }\n"
                        + "[plugins]\n"
                        + "android-application = { id = \"com.android.application\","
                        + " version.ref = \"agp\" }\n"
                        + "kotlin-android = { id = \"org.jetbrains.kotlin.android\","
                        + " version.ref = \"agp\" }\n";
        catalog = VersionCatalogParser.parse(toml);
    }

    @Test
    public void testPluginAliasResolved() {
        String src = "plugins {\n"
                + "  alias(libs.plugins.android.application)\n"
                + "  alias(libs.plugins.kotlin.android)\n"
                + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts", null, catalog);
        assertTrue(info.getPlugins().contains("com.android.application"));
        assertTrue(info.getPlugins().contains("org.jetbrains.kotlin.android"));
        assertTrue(info.isAndroidApplication());
    }

    @Test
    public void testPluginAliasFallbackWhenNotInCatalog() {
        String src = "plugins { alias(libs.plugins.unknown.foo) }\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts", null, catalog);
        // catalog にないので fall back: aliasToPluginId で best-effort
        assertEquals(1, info.getPlugins().size());
    }

    @Test
    public void testSdkVersionResolution() {
        String src = "android {\n"
                + "  compileSdk = libs.versions.compileSdk.get().toInt()\n"
                + "  defaultConfig {\n"
                + "    minSdk = libs.versions.minSdk.get().toInt()\n"
                + "  }\n"
                + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts", null, catalog);
        assertEquals(Integer.valueOf(34), info.getCompileSdk());
        assertEquals(Integer.valueOf(24), info.getMinSdk());
    }

    @Test
    public void testSdkVersionGroovyForm() {
        // Groovy DSL の `libs.versions.compileSdk.get().toInteger()` 形式
        String src = "android {\n"
                + "  compileSdkVersion libs.versions.compileSdk.get().toInteger()\n"
                + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle", null, catalog);
        assertEquals(Integer.valueOf(34), info.getCompileSdk());
    }

    @Test
    public void testLibraryDepResolution() {
        String src = "dependencies {\n"
                + "  implementation(libs.androidx.appcompat)\n"
                + "  implementation(libs.androidx.core.ktx)\n"
                + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts", null, catalog);
        assertEquals(2, info.getDependencies().size());
        boolean a = false;
        boolean b = false;
        for (GradleDependency d : info.getDependencies()) {
            if ("androidx.appcompat:appcompat:1.6.1".equals(d.getNotation())) {
                a = true;
            }
            if ("androidx.core:core-ktx:1.13.0".equals(d.getNotation())) {
                b = true;
            }
        }
        assertTrue("expected appcompat resolved", a);
        assertTrue("expected core-ktx resolved", b);
    }

    @Test
    public void testLibraryDepGroovyForm() {
        // Groovy DSL の implementation libs.androidx.appcompat 形式 (括弧無し)
        String src = "dependencies { implementation libs.androidx.appcompat }\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle", null, catalog);
        assertEquals(1, info.getDependencies().size());
        assertEquals("androidx.appcompat:appcompat:1.6.1",
                info.getDependencies().get(0).getNotation());
    }

    @Test
    public void testMixedCatalogAndLiteral() {
        // catalog 参照とリテラル文字列が混在
        String src = "dependencies {\n"
                + "  implementation(libs.androidx.appcompat)\n"
                + "  implementation \"junit:junit:4.13.2\"\n"
                + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts", null, catalog);
        assertEquals(2, info.getDependencies().size());
    }

    @Test
    public void testCatalogNullStillWorksForLiterals() {
        // catalog 無しでも従来動作は維持
        String src = "dependencies { implementation 'junit:junit:4.13.2' }\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle", null, null);
        assertEquals(1, info.getDependencies().size());
    }

    @Test
    public void testUnknownLibAliasIgnored() {
        // catalog にない libs.X を参照しても落ちず、依存リストに加わらない
        String src = "dependencies { implementation(libs.nothing.here) }\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts", null, catalog);
        assertTrue(info.getDependencies().isEmpty());
    }
}
