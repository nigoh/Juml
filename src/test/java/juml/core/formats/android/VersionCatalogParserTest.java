// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * VersionCatalogParser のユニットテスト。
 */
public class VersionCatalogParserTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        VersionCatalogParser.parse(null);
    }

    @Test
    public void testEmptyToml() {
        VersionCatalog cat = VersionCatalogParser.parse("");
        assertTrue(cat.getVersions().isEmpty());
        assertTrue(cat.getLibraries().isEmpty());
        assertTrue(cat.getPlugins().isEmpty());
    }

    @Test
    public void testVersionsSection() {
        String toml = "[versions]\n"
                + "compileSdk = \"34\"\n"
                + "minSdk = \"24\"\n"
                + "agp = \"8.1.0\"\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        assertEquals("34", cat.findVersion("compileSdk"));
        assertEquals("24", cat.findVersion("minSdk"));
        assertEquals("8.1.0", cat.findVersion("agp"));
    }

    @Test
    public void testLibrariesShortForm() {
        String toml = "[libraries]\n"
                + "junit = \"junit:junit:4.13.2\"\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        VersionCatalog.Library lib = cat.findLibrary("junit");
        assertNotNull(lib);
        assertEquals("junit", lib.group);
        assertEquals("junit", lib.name);
        assertEquals("4.13.2", lib.version);
        assertEquals("junit:junit:4.13.2", lib.toNotation());
    }

    @Test
    public void testLibrariesInlineTable() {
        String toml = "[versions]\n"
                + "appcompat = \"1.6.1\"\n"
                + "[libraries]\n"
                + "androidx-appcompat = { module = \"androidx.appcompat:appcompat\","
                + " version.ref = \"appcompat\" }\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        VersionCatalog.Library lib = cat.findLibrary("androidx.appcompat");
        assertNotNull(lib);
        assertEquals("androidx.appcompat", lib.group);
        assertEquals("appcompat", lib.name);
        assertEquals("1.6.1", lib.version);
    }

    @Test
    public void testInlineTableSingleQuotedValues() {
        // Gradle はインラインテーブルでシングルクオート値も許容する。
        // KV_PATTERN がシングルクオートを取りこぼさないこと。
        String toml = "[libraries]\n"
                + "material = { module = 'com.google.android.material:material',"
                + " version = '1.10.0' }\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        VersionCatalog.Library lib = cat.findLibrary("material");
        assertNotNull(lib);
        assertEquals("com.google.android.material", lib.group);
        assertEquals("material", lib.name);
        assertEquals("1.10.0", lib.version);
    }

    @Test
    public void testLibrariesGroupNameSeparate() {
        String toml = "[versions]\n"
                + "core = \"1.13.0\"\n"
                + "[libraries]\n"
                + "androidx-core-ktx = { group = \"androidx.core\","
                + " name = \"core-ktx\", version.ref = \"core\" }\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        VersionCatalog.Library lib = cat.findLibrary("androidx.core.ktx");
        assertNotNull(lib);
        assertEquals("androidx.core", lib.group);
        assertEquals("core-ktx", lib.name);
        assertEquals("1.13.0", lib.version);
    }

    @Test
    public void testLibrariesInlineVersionLiteral() {
        // version = "1.10.0" 直書き
        String toml = "[libraries]\n"
                + "material = { module = \"com.google.android.material:material\","
                + " version = \"1.10.0\" }\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        VersionCatalog.Library lib = cat.findLibrary("material");
        assertEquals("1.10.0", lib.version);
    }

    @Test
    public void testPluginsInlineTable() {
        String toml = "[versions]\n"
                + "agp = \"8.1.0\"\n"
                + "[plugins]\n"
                + "android-application = { id = \"com.android.application\","
                + " version.ref = \"agp\" }\n"
                + "kotlin-android = { id = \"org.jetbrains.kotlin.android\","
                + " version.ref = \"agp\" }\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        VersionCatalog.Plugin p = cat.findPlugin("android.application");
        assertNotNull(p);
        assertEquals("com.android.application", p.id);
        assertEquals("8.1.0", p.version);
    }

    @Test
    public void testPluginsShortForm() {
        String toml = "[plugins]\n"
                + "ksp = \"com.google.devtools.ksp:1.9.22-1.0.17\"\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        VersionCatalog.Plugin p = cat.findPlugin("ksp");
        assertNotNull(p);
        assertEquals("com.google.devtools.ksp", p.id);
        assertEquals("1.9.22-1.0.17", p.version);
    }

    @Test
    public void testCommentsIgnored() {
        String toml = "# header\n"
                + "[versions]\n"
                + "agp = \"8.1.0\" # inline\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        assertEquals("8.1.0", cat.findVersion("agp"));
    }

    @Test
    public void testHashInsideStringIsNotComment() {
        // 文字列内の # はコメントとして扱わない
        String toml = "[versions]\n"
                + "compose = \"1.2.3 # not comment\"\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        assertEquals("1.2.3 # not comment", cat.findVersion("compose"));
    }

    @Test
    public void testNormalizationDashUnderscoreDot() {
        // androidx-core-ktx, androidx_core_ktx は libs.androidx.core.ktx と同じ
        String toml = "[libraries]\n"
                + "androidx-core_ktx = \"androidx.core:core-ktx:1.13.0\"\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        assertNotNull(cat.findLibrary("androidx.core.ktx"));
        assertNotNull(cat.findLibrary("androidx-core-ktx"));
        assertNotNull(cat.findLibrary("ANDROIDX.CORE.KTX"));
    }

    @Test
    public void testBundlesSectionIgnored() {
        // [bundles] は未サポート、スキップして他のセクションは正常パース
        String toml = "[versions]\nagp = \"8.0\"\n"
                + "[bundles]\n"
                + "androidx = [\"a\", \"b\"]\n"
                + "[plugins]\n"
                + "x = \"y:1.0\"\n";
        VersionCatalog cat = VersionCatalogParser.parse(toml);
        assertEquals("8.0", cat.findVersion("agp"));
        assertNotNull(cat.findPlugin("x"));
    }
}
