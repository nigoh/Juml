// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * GradleScriptParser のユニットテスト。
 */
public class GradleScriptParserTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullScript() {
        GradleScriptParser.parse(null, "build.gradle");
    }

    @Test
    public void testEmpty() {
        GradleProjectInfo info = GradleScriptParser.parse("", "build.gradle");
        assertNotNull(info);
        assertEquals("groovy", info.getModuleType());
        assertTrue(info.getPlugins().isEmpty());
    }

    @Test
    public void testKtsType() {
        GradleProjectInfo info = GradleScriptParser.parse("", "build.gradle.kts");
        assertEquals("kotlin", info.getModuleType());
    }

    @Test
    public void testPluginsBlockGroovy() {
        String src = "plugins {\n  id 'com.android.application'\n  id 'kotlin-android'\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertTrue(info.getPlugins().contains("com.android.application"));
        assertTrue(info.getPlugins().contains("kotlin-android"));
        assertTrue(info.isAndroidApplication());
    }

    @Test
    public void testPluginsBlockKotlin() {
        String src = "plugins {\n  id(\"com.android.library\")\n  kotlin(\"android\")\n}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertTrue(info.getPlugins().contains("com.android.library"));
        assertTrue(info.isAndroidLibrary());
    }

    @Test
    public void testApplyPlugin() {
        String src = "apply plugin: 'com.android.application'\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertTrue(info.getPlugins().contains("com.android.application"));
    }

    @Test
    public void testAndroidBlockBasics() {
        String src =
                "android {\n"
                        + "  namespace 'com.example'\n"
                        + "  compileSdk 34\n"
                        + "  defaultConfig {\n"
                        + "    applicationId 'com.example.app'\n"
                        + "    minSdk 24\n"
                        + "    targetSdk 34\n"
                        + "    versionCode 5\n"
                        + "    versionName '1.2.3'\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals("com.example", info.getNamespace());
        assertEquals("com.example.app", info.getApplicationId());
        assertEquals(Integer.valueOf(34), info.getCompileSdk());
        assertEquals(Integer.valueOf(24), info.getMinSdk());
        assertEquals(Integer.valueOf(34), info.getTargetSdk());
        assertEquals(Integer.valueOf(5), info.getVersionCode());
        assertEquals("1.2.3", info.getVersionName());
    }

    @Test
    public void testKotlinDslAssignment() {
        // Kotlin DSL は `=` を使うことが多い
        String src =
                "android {\n"
                        + "  namespace = \"com.example\"\n"
                        + "  compileSdk = 34\n"
                        + "  defaultConfig {\n"
                        + "    applicationId = \"com.example.app\"\n"
                        + "    minSdk = 24\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle.kts");
        assertEquals("com.example", info.getNamespace());
        assertEquals("com.example.app", info.getApplicationId());
        assertEquals(Integer.valueOf(34), info.getCompileSdk());
        assertEquals(Integer.valueOf(24), info.getMinSdk());
    }

    @Test
    public void testBuildTypes() {
        String src =
                "android {\n"
                        + "  buildTypes {\n"
                        + "    release {\n"
                        + "      minifyEnabled true\n"
                        + "      applicationIdSuffix '.release'\n"
                        + "    }\n"
                        + "    debug {\n"
                        + "      debuggable true\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(2, info.getBuildTypes().size());
        GradleBuildType release = info.getBuildTypes().get("release");
        assertNotNull(release);
        assertEquals(Boolean.TRUE, release.getMinifyEnabled());
        assertEquals(".release", release.getApplicationIdSuffix());
        GradleBuildType debug = info.getBuildTypes().get("debug");
        assertEquals(Boolean.TRUE, debug.getDebuggable());
    }

    @Test
    public void testProductFlavors() {
        String src =
                "android {\n"
                        + "  flavorDimensions 'env'\n"
                        + "  productFlavors {\n"
                        + "    prod { dimension 'env' applicationIdSuffix '.prod' }\n"
                        + "    dev  { dimension 'env' versionNameSuffix '-dev' }\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertTrue(info.getFlavorDimensions().contains("env"));
        assertEquals(2, info.getProductFlavors().size());
        assertEquals("env", info.getProductFlavors().get("prod").getDimension());
        assertEquals(".prod", info.getProductFlavors().get("prod").getApplicationIdSuffix());
        assertEquals("-dev", info.getProductFlavors().get("dev").getVersionNameSuffix());
    }

    @Test
    public void testSigningConfigs() {
        String src =
                "android {\n"
                        + "  signingConfigs {\n"
                        + "    release {\n"
                        + "      keyAlias 'myalias'\n"
                        + "      storeFile file('keystore.jks')\n"
                        + "      keyPassword 'SECRET'\n"
                        + "      storePassword 'SECRET'\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(1, info.getSigningConfigs().size());
        GradleSigningConfig sc = info.getSigningConfigs().get("release");
        assertNotNull(sc);
        assertEquals("myalias", sc.getKeyAlias());
        // 機密値は保持しない (フィールド自体が無い)
    }

    @Test
    public void testDependencies() {
        String src =
                "dependencies {\n"
                        + "  implementation 'androidx.appcompat:appcompat:1.6.1'\n"
                        + "  api 'com.google.code.gson:gson:2.10.1'\n"
                        + "  testImplementation 'junit:junit:4.13.2'\n"
                        + "  androidTestImplementation 'androidx.test:core:1.5.0'\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(4, info.getDependencies().size());
        GradleDependency d0 = info.getDependencies().get(0);
        assertEquals("implementation", d0.getScope());
        assertEquals("androidx.appcompat", d0.getGroup());
        assertEquals("appcompat", d0.getName());
        assertEquals("1.6.1", d0.getVersion());
    }

    @Test
    public void testProjectDependency() {
        String src =
                "dependencies {\n"
                        + "  implementation project(':lib')\n"
                        + "  api project(':core')\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(2, info.getDependencies().size());
        assertTrue(info.getDependencies().get(0).isModuleReference());
        assertEquals("lib", info.getDependencies().get(0).getModuleRef());
        assertEquals("core", info.getDependencies().get(1).getModuleRef());
    }

    @Test
    public void testKspDependencies() {
        // ksp はカタログ参照だけでなく直書き表記/プロジェクト参照でも取れること
        String src =
                "dependencies {\n"
                        + "  ksp 'androidx.room:room-compiler:2.6.1'\n"
                        + "  ksp(project(\":processor\"))\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(2, info.getDependencies().size());
        // project() 参照が先に抽出される
        GradleDependency d0 = info.getDependencies().get(0);
        assertEquals("ksp", d0.getScope());
        assertTrue(d0.isModuleReference());
        assertEquals("processor", d0.getModuleRef());
        GradleDependency d1 = info.getDependencies().get(1);
        assertEquals("ksp", d1.getScope());
        assertEquals("androidx.room", d1.getGroup());
        assertEquals("room-compiler", d1.getName());
    }

    @Test
    public void testSettingsInclude() {
        String src = "include ':app', ':lib:core', ':lib:net'\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "settings.gradle");
        assertEquals(3, info.getSubprojects().size());
        assertTrue(info.getSubprojects().contains("app"));
        assertTrue(info.getSubprojects().contains("lib:core"));
    }

    @Test
    public void testCommentsIgnored() {
        String src =
                "// implementation 'commented:out:1.0'\n"
                        + "/* implementation 'block:commented:1.0' */\n"
                        + "dependencies {\n"
                        + "  implementation 'real:lib:1.0'  // trailing comment\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(1, info.getDependencies().size());
        assertEquals("real:lib:1.0", info.getDependencies().get(0).getNotation());
    }

    @Test
    public void testStringWithBraces() {
        // 文字列内の { } はブロック追跡に影響しない
        String src =
                "dependencies {\n"
                        + "  implementation 'group:name:${version}'\n"
                        + "}\n";
        GradleProjectInfo info = GradleScriptParser.parse(src, "build.gradle");
        assertEquals(1, info.getDependencies().size());
    }
}
