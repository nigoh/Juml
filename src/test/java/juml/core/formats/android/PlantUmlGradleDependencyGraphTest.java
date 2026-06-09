// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * PlantUmlGradleDependencyGraph のユニットテスト。
 */
public class PlantUmlGradleDependencyGraphTest {

    private static AndroidProjectAnalysis build() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        GradleProjectInfo app = new GradleProjectInfo();
        app.setModuleName("app");
        app.getPlugins().add("com.android.application");
        app.getDependencies().add(new GradleDependency("implementation", "project(':lib')"));
        app.getDependencies().add(new GradleDependency("implementation",
                "androidx.appcompat:appcompat:1.6.1"));
        app.getDependencies().add(new GradleDependency("testImplementation",
                "junit:junit:4.13.2"));
        a.getGradleByModule().put("app", app);

        GradleProjectInfo lib = new GradleProjectInfo();
        lib.setModuleName("lib");
        lib.getPlugins().add("com.android.library");
        a.getGradleByModule().put("lib", lib);
        return a;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNull() {
        PlantUmlGradleDependencyGraph.generate(null);
    }

    @Test
    public void testBasicGeneration() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("component \"app\""));
        assertTrue(puml, puml.contains("component \"lib\""));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testApplicationAndLibraryStereotype() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("<<application>>"));
        assertTrue(puml, puml.contains("<<library>>"));
    }

    @Test
    public void testModuleEdge() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        // 矢印 (具体的なエイリアスは可変なので "-->" の存在を確認)
        assertTrue(puml, puml.contains("-->"));
        assertTrue(puml, puml.contains("implementation"));
    }

    /** モジュール→モジュールのエッジ ({@code M0 --> M1}) が puml に存在するか。 */
    private static boolean hasModuleToModuleEdge(String puml) {
        Matcher m = Pattern.compile("(?m)^M\\d+ --> M\\d+ : ").matcher(puml);
        return m.find();
    }

    /**
     * Gradle のサブプロジェクトのディレクトリ単体をルートとして開いた場合の回帰テスト。
     *
     * <p>モジュール名はルート相対の {@code "lib"} になる一方、依存宣言は絶対 Gradle パス
     * {@code project(':group:lib')} を保持する。このとき完全一致だけでは
     * モジュール間の {@code project()} 依存エッジが解決できず脱落していた
     * (依存グラフがモジュール同士の接続を一切描かない)。末尾セグメント一致の
     * フォールバックでエッジを復元する。</p>
     */
    @Test
    public void testModuleEdgeResolvedByLeafSegmentWhenRootRelative() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        GradleProjectInfo app = new GradleProjectInfo();
        app.setModuleName("app");
        app.getPlugins().add("com.android.application");
        // 絶対 Gradle パスでの参照 (ルートに settings.gradle がある本来の構成由来)
        app.getDependencies().add(new GradleDependency("implementation",
                "project(':group:lib')"));
        a.getGradleByModule().put("app", app);

        GradleProjectInfo lib = new GradleProjectInfo();
        lib.setModuleName("lib"); // ルート相対で短縮されたモジュール名
        lib.getPlugins().add("com.android.library");
        a.getGradleByModule().put("lib", lib);

        String puml = PlantUmlGradleDependencyGraph.generate(a);
        assertTrue("module-to-module edge must be present: " + puml,
                hasModuleToModuleEdge(puml));
        assertTrue(puml, puml.contains("component \"app\""));
        assertTrue(puml, puml.contains("component \"lib\""));
    }

    @Test
    public void testResolveModuleKeyExactMatchPreferred() {
        Set<String> keys = Set.of("showcase:common", "places:common", "app");
        assertEquals("showcase:common",
                PlantUmlGradleDependencyGraph.resolveModuleKey("showcase:common", keys));
    }

    @Test
    public void testResolveModuleKeyLeafFallback() {
        Set<String> keys = Set.of("common", "mobile", "automotive");
        assertEquals("common",
                PlantUmlGradleDependencyGraph.resolveModuleKey("showcase:common", keys));
    }

    @Test
    public void testResolveModuleKeyAmbiguousLeafUnresolved() {
        // 末尾セグメントが複数モジュールで重複する場合は誤接続を避けて解決しない
        Set<String> keys = Set.of("showcase:common", "places:common");
        assertNull(PlantUmlGradleDependencyGraph.resolveModuleKey(":other:common", keys));
    }

    @Test
    public void testExternalLib() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("androidx.appcompat:appcompat"));
        assertTrue(puml, puml.contains("<<external>>"));
    }

    @Test
    public void testTestScopeExcludedByDefault() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertFalse(puml, puml.contains("junit"));
    }

    @Test
    public void testTestScopeIncluded() {
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        o.includeTestScopes = true;
        String puml = PlantUmlGradleDependencyGraph.generate(build(), o);
        assertTrue(puml, puml.contains("junit"));
        assertTrue(puml, puml.contains("..>"));
    }

    @Test
    public void testApiScopeUsesBoldArrow() {
        AndroidProjectAnalysis a = build();
        a.getGradleByModule().get("app").getDependencies()
                .add(new GradleDependency("api", "project(':lib')"));
        String puml = PlantUmlGradleDependencyGraph.generate(a);
        // api は太線、implementation は通常線のまま
        assertTrue(puml, puml.contains("-[bold]-> M1 : api"));
        assertTrue(puml, puml.contains("--> M1 : implementation"));
    }

    @Test
    public void testCompileOnlyAndRuntimeOnlyUseDashedArrow() {
        AndroidProjectAnalysis a = build();
        a.getGradleByModule().get("app").getDependencies()
                .add(new GradleDependency("compileOnly", "project(':lib')"));
        a.getGradleByModule().get("app").getDependencies()
                .add(new GradleDependency("runtimeOnly", "project(':lib')"));
        String puml = PlantUmlGradleDependencyGraph.generate(a);
        assertTrue(puml, puml.contains("..> M1 : compileOnly"));
        assertTrue(puml, puml.contains("..> M1 : runtimeOnly"));
    }

    @Test
    public void testLegendDescribesScopeArrows() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("A --> B"));
        assertTrue(puml, puml.contains("A -[bold]-> B"));
        assertTrue(puml, puml.contains("compileOnly/runtimeOnly"));
    }

    @Test
    public void testNoExternalLibsOption() {
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        o.includeExternalLibs = false;
        String puml = PlantUmlGradleDependencyGraph.generate(build(), o);
        assertFalse(puml, puml.contains("androidx.appcompat"));
    }

    @Test
    public void testLegendOnByDefault() {
        String puml = PlantUmlGradleDependencyGraph.generate(build());
        assertTrue(puml, puml.contains("legend top left"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        o.includeLegend = false;
        String puml = PlantUmlGradleDependencyGraph.generate(build(), o);
        assertFalse(puml, puml.contains("legend top left"));
    }

    /**
     * 集約用ルートプロジェクト (`:root`) のようにエッジを 1 本も持たない
     * 孤立モジュールは {@code component} 宣言から除外される。
     *
     * <p>同梱 PlantUML の Smetana レイアウトが孤立ノードを含むグラフで
     * {@code IllegalStateException} (qsort 内部) を起こすため。</p>
     */
    @Test
    public void testIsolatedRootModuleOmitted() {
        AndroidProjectAnalysis a = build();
        GradleProjectInfo root = new GradleProjectInfo();
        root.setModuleName(":root");
        a.getGradleByModule().put(":root", root);

        String puml = PlantUmlGradleDependencyGraph.generate(a);
        assertFalse("isolated :root module must not appear: " + puml,
                puml.contains("component \":root\""));
        assertTrue(puml, puml.contains("component \"app\""));
        assertTrue(puml, puml.contains("component \"lib\""));
    }

    /**
     * 外部ライブラリ非表示オプション下では、外部依存しか持たないモジュールも
     * 孤立扱いになり {@code component} 宣言から除外される。
     */
    @Test
    public void testExternalOnlyModuleOmittedWhenExternalsDisabled() {
        AndroidProjectAnalysis a = new AndroidProjectAnalysis();
        GradleProjectInfo solo = new GradleProjectInfo();
        solo.setModuleName("solo");
        solo.getPlugins().add("com.android.library");
        solo.getDependencies().add(new GradleDependency("implementation",
                "androidx.appcompat:appcompat:1.6.1"));
        a.getGradleByModule().put("solo", solo);

        PlantUmlGradleDependencyGraph.Options o = new PlantUmlGradleDependencyGraph.Options();
        o.includeExternalLibs = false;
        String puml = PlantUmlGradleDependencyGraph.generate(a, o);
        assertFalse("module with only filtered-out deps must not appear: " + puml,
                puml.contains("component \"solo\""));
    }
}
