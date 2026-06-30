// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Version Catalog の plugin alias 認識テスト。
 *
 * <p>{@code alias(libs.plugins.android.application)} 形式は modern Android プロジェクトの
 * 標準的な書き方であり、これを {@code com.android.application} に正規化できることを検証する。</p>
 */
public class GradleVersionCatalogTest {

    @Test
    public void testAliasMapsToAndroidApplication() {
        String src = "plugins {\n  alias(libs.plugins.android.application)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("com.android.application"));
        assertTrue(info.isAndroidApplication());
    }

    @Test
    public void testAliasMapsToAndroidLibrary() {
        String src = "plugins {\n  alias(libs.plugins.android.library)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("com.android.library"));
        assertTrue(info.isAndroidLibrary());
    }

    @Test
    public void testAliasMapsToKotlinAndroid() {
        String src = "plugins {\n  alias(libs.plugins.kotlin.android)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("org.jetbrains.kotlin.android"));
    }

    @Test
    public void testAliasMapsToKsp() {
        String src = "plugins {\n  alias(libs.plugins.ksp)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("com.google.devtools.ksp"));
    }

    @Test
    public void testAliasMapsToHilt() {
        String src = "plugins {\n  alias(libs.plugins.hilt)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("dagger.hilt.android.plugin"));
    }

    @Test
    public void testUnknownAliasFallsBackToPath() {
        String src = "plugins {\n  alias(libs.plugins.custom.special)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("custom.special"));
    }

    @Test
    public void testCamelCaseAlias() {
        // libs.plugins.androidApplication (camelCase) もサポート
        String src = "plugins {\n  alias(libs.plugins.androidApplication)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("com.android.application"));
    }

    @Test
    public void testAliasAndIdMixed() {
        // alias と id の混在
        String src =
                "plugins {\n"
                        + "  alias(libs.plugins.android.application)\n"
                        + "  id(\"kotlin-android\")\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertEquals(2, info.getPlugins().size());
        assertTrue(info.getPlugins().contains("com.android.application"));
        assertTrue(info.getPlugins().contains("kotlin-android"));
    }

    @Test
    public void testDepsAliasIsAccepted() {
        // alias(deps.plugins.x) や alias(catalog.plugins.x) も認識
        String src = "plugins {\n  alias(deps.plugins.android.application)\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("com.android.application"));
    }
}
