// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@code build.gradle} のブロック取り出しと BOM 座標の回帰テスト。
 *
 * <p>1 つ目: {@code extractBlock} は最初に見つかった綴りを無条件に採っていた。Groovy DSL の
 * {@code build.gradle} でファイル中<b>最初の</b> {@code dependencies {}} は
 * {@code buildscript {}} の内側 (classpath 宣言) なので、モジュール本来の依存ブロックが
 * 一度も読まれず<b>依存が 1 件も出ない</b>。{@code classpath} はどのスコープ語にも
 * 一致しないので黙って 0 件になり、そのモジュールは依存グラフから孤立ノードとして落ちる。
 * 同じメソッドの兄弟規則 ({@code include} の全件走査、{@code apply plugin:} の全文検索) は
 * この限界を持たないので、<b>モジュール名とプラグインだけ生き残って依存だけ消える</b>という
 * 気付きにくい壊れ方だった。</p>
 *
 * <p>2 つ目: {@code platform()} / {@code enforcedPlatform()} の BOM 依存だけが
 * ラッパ付きの文字列を素の座標として渡していたので、group が
 * {@code platform('androidx.compose}、version が {@code 2024.02.00')} になっていた。
 * 兄弟の {@code files()} / {@code fileTree()} は専用ファクトリでこれを避けている。</p>
 */
public class GradleBlockAndBomCoordinatesTest {

    private static final String SCRIPT =
            "buildscript {\n"
            + "  repositories { google() }\n"
            + "  dependencies {\n"
            + "    classpath 'com.android.tools.build:gradle:8.2.0'\n"
            + "  }\n"
            + "}\n"
            + "apply plugin: 'com.android.application'\n"
            + "android {\n  namespace 'com.x'\n  compileSdk 34\n}\n"
            + "dependencies {\n"
            + "  implementation 'androidx.core:core-ktx:1.12.0'\n"
            + "  implementation project(':lib')\n"
            + "  implementation platform('androidx.compose:compose-bom:2024.02.00')\n"
            + "}\n";

    private static GradleDependency byNotationContaining(
            List<GradleDependency> deps, String needle) {
        for (GradleDependency d : deps) {
            if (d.getNotation() != null && d.getNotation().contains(needle)) {
                return d;
            }
        }
        return null;
    }

    /** {@code buildscript} の中の {@code dependencies} に隠されないこと。 */
    @Test
    public void theBuildscriptBlockDoesNotShadowTheRealDependencies() {
        GradleProjectInfo info = GradleScriptParser.parse(SCRIPT, "build.gradle");
        assertEquals("本物の dependencies ブロックが読まれること: "
                + info.getDependencies(), 3, info.getDependencies().size());
        assertNotNull("外部ライブラリ依存",
                byNotationContaining(info.getDependencies(), "androidx.core:core-ktx"));
        assertNotNull("プロジェクト依存",
                byNotationContaining(info.getDependencies(), "project(':lib')"));
    }

    /** 非退行: 同じスクリプトの {@code android} ブロックとプラグインは従来どおり。 */
    @Test
    public void theAndroidBlockAndPluginsStillParse() {
        GradleProjectInfo info = GradleScriptParser.parse(SCRIPT, "build.gradle");
        assertEquals("com.x", info.getNamespace());
        assertEquals("34", String.valueOf(info.getCompileSdk()));
        assertTrue("プラグインが拾えること: " + info.getPlugins(),
                info.getPlugins().stream().anyMatch(p -> p.contains("com.android.application")));
    }

    /** BOM の座標はラッパを剥がして分解すること。 */
    @Test
    public void platformBomCoordinatesDropTheDslWrapper() {
        GradleProjectInfo info = GradleScriptParser.parse(SCRIPT, "build.gradle");
        GradleDependency bom = byNotationContaining(info.getDependencies(), "compose-bom");
        assertNotNull("BOM 依存が読めること", bom);
        assertEquals("group にラッパが混ざらないこと", "androidx.compose", bom.getGroup());
        assertEquals("compose-bom", bom.getName());
        assertEquals("version に閉じ括弧が混ざらないこと", "2024.02.00", bom.getVersion());
    }

    /** 非退行: ローカルファイル依存の扱いは変えていないこと。 */
    @Test
    public void localFileDependenciesKeepTheirShape() {
        GradleProjectInfo info = GradleScriptParser.parse(
                "dependencies {\n  implementation files('libs/a.jar')\n}\n", "build.gradle");
        GradleDependency f = byNotationContaining(info.getDependencies(), "libs/a.jar");
        assertNotNull(f);
        assertEquals("座標を持たないこと", null, f.getGroup());
        assertEquals("libs/a.jar", f.getName());
    }
}
