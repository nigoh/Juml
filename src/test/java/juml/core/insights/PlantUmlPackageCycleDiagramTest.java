// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.insights;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * {@link PlantUmlPackageCycleDiagram} のユニットテスト。
 */
public class PlantUmlPackageCycleDiagramTest {

    private static InsightsModel cyclicModel() {
        InsightsModel m = new InsightsModel();
        InsightsModel.PackageEdge e1 = new InsightsModel.PackageEdge("pkg.a", "pkg.b");
        InsightsModel.PackageEdge e2 = new InsightsModel.PackageEdge("pkg.b", "pkg.a");
        // 循環外の隣接エッジ (pkg.c → pkg.a)
        InsightsModel.PackageEdge e3 = new InsightsModel.PackageEdge("pkg.c", "pkg.a");
        m.addPackageEdge(e1);
        m.addPackageEdge(e2);
        m.addPackageEdge(e3);
        Set<String> cyclePkgs = new LinkedHashSet<>(List.of("pkg.a", "pkg.b"));
        m.addPackageCycle(new InsightsModel.PackageCycle(cyclePkgs, List.of(e1, e2)));
        m.putClassCount("pkg.a", 2);
        m.putClassCount("pkg.b", 1);
        m.putClassCount("pkg.c", 3);
        return m;
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullModel() {
        PlantUmlPackageCycleDiagram.render(null);
    }

    @Test
    public void testNoCycleEmitsNote() {
        String puml = PlantUmlPackageCycleDiagram.render(new InsightsModel());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        assertTrue(puml, puml.contains("循環依存は検出されませんでした"));
        assertFalse(puml, puml.contains("-[#Red,bold]->"));
    }

    @Test
    public void testCycleEdgesHighlightedRed() {
        String puml = PlantUmlPackageCycleDiagram.render(cyclicModel());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
        // 循環を構成するエッジは赤太線、循環外エッジは灰線
        assertTrue(puml, puml.contains("-[#Red,bold]->"));
        assertTrue(puml, puml.contains("-[#Gray]->"));
    }

    @Test
    public void testCyclePackagesHighlightedAndCounted() {
        String puml = PlantUmlPackageCycleDiagram.render(cyclicModel());
        // 循環参加パッケージは赤背景 + クラス数表記 (PlantUmlPackageDiagram 踏襲)
        assertTrue(puml, puml.contains("package \"pkg.a\\n2 classes\""));
        assertTrue(puml, puml.contains("#FFCCCC"));
        // 隣接パッケージ (pkg.c) も文脈として描画される
        assertTrue(puml, puml.contains("package \"pkg.c\\n3 classes\""));
    }

    @Test
    public void testLegendPresent() {
        String puml = PlantUmlPackageCycleDiagram.render(cyclicModel());
        assertTrue(puml, puml.contains("legend top left"));
        assertTrue(puml, puml.contains("endlegend"));
    }
}
