// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link MarkdownPartitionReport} / {@link PlantUmlPartitionDiagram} のユニットテスト。
 * partition 別集計 (件数・種類内訳・主要モジュール) と跨ぎ依存の可視化を検証する。
 */
public class MarkdownPartitionReportTest {

    private static List<AndroidBpModule> sampleModules() {
        AndroidBpModule libsys = new AndroidBpModule(
                "cc_library", "libsys", "a/Android.bp", 1);
        AndroidBpModule libvnd = new AndroidBpModule(
                "cc_library", "libvnd", "b/Android.bp", 1);
        libvnd.putScalar("vendor", "true");
        libvnd.addDep("shared_libs", "libsys");
        AndroidBpModule prodApp = new AndroidBpModule(
                "android_app", "ProdApp", "c/Android.bp", 1);
        prodApp.putScalar("product_specific", "true");
        return Arrays.asList(libsys, libvnd, prodApp);
    }

    @Test
    public void emptyModulesRenderPlaceholder() {
        String md = MarkdownPartitionReport.render(Collections.emptyList());
        assertTrue(md.contains("(no Android.bp modules found)"));
        String puml = PlantUmlPartitionDiagram.render(Collections.emptyList());
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.contains("(no Android.bp modules found)"));
    }

    @Test
    public void reportGroupsByPartitionAndListsCrossDeps() {
        String md = MarkdownPartitionReport.render(sampleModules());
        assertTrue(md.contains("## system (1 modules)"));
        assertTrue(md.contains("## vendor (1 modules)"));
        assertTrue(md.contains("## product (1 modules)"));
        // 種類内訳と主要モジュール (被依存 1 の libsys)
        assertTrue(md.contains("| `cc_library` | 1 |"));
        assertTrue(md.contains("| `libsys` | `cc_library` | 1 |"));
        // vendor → system の跨ぎ依存
        assertTrue(md.contains("| `libvnd` | vendor | `libsys` | system |"));
    }

    @Test
    public void reportWithoutCrossDepsSaysSo() {
        AndroidBpModule solo = new AndroidBpModule(
                "cc_library", "libsolo", "Android.bp", 1);
        String md = MarkdownPartitionReport.render(Collections.singletonList(solo));
        assertTrue(md.contains("パーティションを跨ぐ依存はありません"));
    }

    @Test
    public void diagramDrawsPartitionPackagesAndEdges() {
        String puml = PlantUmlPartitionDiagram.render(sampleModules());
        assertTrue(puml.contains("package \"system\""));
        assertTrue(puml.contains("package \"vendor\""));
        assertTrue(puml.contains("package \"product\""));
        // 跨ぎ依存に参加するモジュールはコンポーネント化される
        assertTrue(puml.contains("libvnd"));
        assertTrue(puml.contains("libsys"));
        assertTrue(puml.contains("shared_libs"));
        // 参加しない ProdApp はプレースホルダに畳まれる
        assertFalse(puml.contains("ProdApp"));
        assertTrue(puml.contains("(+1 modules)"));
    }
}
