// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.insights;

import org.junit.Test;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceIndexBuilder;
import juml.util.ErrorListener;

import java.util.List;

import static org.junit.Assert.*;

/**
 * {@link InsightsAnalyzer} のユニットテスト。
 * フィクスチャは {@code ReferenceIndexBuilderTest} と同じ
 * 「ソース文字列 → JavaStructureExtractor → ClassIndex → ReferenceIndexBuilder」を踏襲。
 */
public class InsightsAnalyzerTest {

    private static List<JavaClassInfo> parse(String... sources) {
        java.util.List<JavaClassInfo> all = new java.util.ArrayList<>();
        for (String src : sources) {
            all.addAll(JavaStructureExtractor.extract(src, ErrorListener.silent()));
        }
        return all;
    }

    private static ClassIndex indexOf(List<JavaClassInfo> classes) {
        ClassIndex idx = new ClassIndex();
        for (JavaClassInfo c : classes) {
            idx.put(c, null, null);
        }
        return idx;
    }

    private static InsightsModel analyze(List<JavaClassInfo> classes) {
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);
        return InsightsAnalyzer.analyze(classes, idx, refIdx);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        InsightsAnalyzer.analyze(null, new ClassIndex(), new ReferenceIndex());
    }

    @Test
    public void testMainEntryPointDetected() {
        String src = "package com.foo;\n"
                + "public class Main {\n"
                + "  public static void main(String[] args) {}\n"
                + "}\n";
        InsightsModel model = analyze(parse(src));
        assertEquals(1, model.getEntryPoints().size());
        InsightsModel.EntryPoint e = model.getEntryPoints().get(0);
        assertEquals(InsightsModel.EntryPointKind.MAIN, e.getKind());
        assertEquals("com.foo.Main", e.getFqn());
    }

    @Test
    public void testActivityEntryPointDetectedBySuperclass() {
        String src = "package com.foo;\n"
                + "public class MainActivity extends Activity {\n"
                + "  public void onCreate() {}\n"
                + "}\n";
        InsightsModel model = analyze(parse(src));
        boolean found = model.getEntryPoints().stream().anyMatch(
                e -> e.getKind() == InsightsModel.EntryPointKind.ACTIVITY
                        && "com.foo.MainActivity".equals(e.getFqn()));
        assertTrue("ACTIVITY entry point missing", found);
    }

    @Test
    public void testHotspotRankedByFanIn() {
        String core = "package com.foo;\n"
                + "public class Core {\n"
                + "  public static void doIt() {}\n"
                + "}\n";
        String other = "package com.foo;\n"
                + "public class Other {\n"
                + "  public static void run() {}\n"
                + "}\n";
        String u1 = "package com.foo;\n"
                + "public class U1 {\n"
                + "  void f() { Core.doIt(); Other.run(); }\n"
                + "}\n";
        String u2 = "package com.foo;\n"
                + "public class U2 {\n"
                + "  void f() { Core.doIt(); }\n"
                + "}\n";
        InsightsModel model = analyze(parse(core, other, u1, u2));
        assertFalse(model.getHotspots().isEmpty());
        InsightsModel.Hotspot top = model.getHotspots().get(0);
        assertEquals("com.foo.Core", top.getFqn());
        assertEquals(2, top.getFanIn());
        assertFalse(top.getTopReferrers().isEmpty());
    }

    @Test
    public void testPackageCycleDetected() {
        String a = "package pkg.a;\n"
                + "import pkg.b.B;\n"
                + "public class A {\n"
                + "  private B b;\n"
                + "}\n";
        String b = "package pkg.b;\n"
                + "import pkg.a.A;\n"
                + "public class B {\n"
                + "  private A a;\n"
                + "}\n";
        InsightsModel model = analyze(parse(a, b));
        assertEquals(1, model.getPackageCycles().size());
        InsightsModel.PackageCycle cycle = model.getPackageCycles().get(0);
        assertTrue(cycle.getPackages().contains("pkg.a"));
        assertTrue(cycle.getPackages().contains("pkg.b"));
        assertFalse(cycle.getEdges().isEmpty());
    }

    @Test
    public void testNoCycleForOneWayDependency() {
        String a = "package pkg.a;\n"
                + "import pkg.b.B;\n"
                + "public class A {\n"
                + "  private B b;\n"
                + "}\n";
        String b = "package pkg.b;\n"
                + "public class B {}\n";
        InsightsModel model = analyze(parse(a, b));
        assertTrue(model.getPackageCycles().isEmpty());
        // 依存エッジ自体は記録される
        assertEquals(1, model.getPackageEdges().size());
        assertEquals("pkg.a", model.getPackageEdges().get(0).getFrom());
        assertEquals("pkg.b", model.getPackageEdges().get(0).getTo());
    }

    @Test
    public void testDeadMethodCandidateDetected() {
        String bar = "package com.foo;\n"
                + "public class Bar {\n"
                + "  public void used() {}\n"
                + "  public void unusedHelper() {}\n"
                + "  @Override\n"
                + "  public String toString() { return \"\"; }\n"
                + "  public void onCreate() {}\n"
                + "}\n";
        String use = "package com.foo;\n"
                + "public class UseSite {\n"
                + "  private Bar bar;\n"
                + "  void run() { bar.used(); }\n"
                + "}\n";
        InsightsModel model = analyze(parse(bar, use));
        List<InsightsModel.DeadCodeCandidate> dead = model.getDeadCodeCandidates();
        boolean unusedFound = dead.stream().anyMatch(
                d -> d.getKind() == InsightsModel.DeadCodeCandidate.Kind.METHOD
                        && "com.foo.Bar.unusedHelper".equals(d.getSymbol()));
        assertTrue("unusedHelper must be a candidate", unusedFound);
        // 除外ヒューリスティック: used / @Override / on* は候補にならない
        assertFalse(dead.stream().anyMatch(
                d -> d.getSymbol().endsWith("Bar.used")));
        assertFalse(dead.stream().anyMatch(
                d -> d.getSymbol().endsWith("Bar.toString")));
        assertFalse(dead.stream().anyMatch(
                d -> d.getSymbol().endsWith("Bar.onCreate")));
    }

    @Test
    public void testUnreferencedClassIsCandidateButMainClassIsNot() {
        String main = "package com.foo;\n"
                + "public class Main {\n"
                + "  public static void main(String[] args) {}\n"
                + "}\n";
        String orphan = "package com.foo;\n"
                + "public class Orphan {\n"
                + "  public void lonely() {}\n"
                + "}\n";
        InsightsModel model = analyze(parse(main, orphan));
        List<InsightsModel.DeadCodeCandidate> dead = model.getDeadCodeCandidates();
        boolean orphanFound = dead.stream().anyMatch(
                d -> d.getKind() == InsightsModel.DeadCodeCandidate.Kind.CLASS
                        && "com.foo.Orphan".equals(d.getSymbol()));
        assertTrue("Orphan class must be a candidate", orphanFound);
        // Main はエントリポイントなので候補にならない
        assertFalse(dead.stream().anyMatch(
                d -> "com.foo.Main".equals(d.getSymbol())));
    }

    @Test
    public void testLayerEstimation() {
        assertEquals("Presentation", InsightsAnalyzer.layerOf("com.app.ui"));
        assertEquals("Presentation Logic", InsightsAnalyzer.layerOf("com.app.viewmodel"));
        assertEquals("Domain", InsightsAnalyzer.layerOf("com.app.domain.model"));
        assertEquals("Data", InsightsAnalyzer.layerOf("com.app.data.repository"));
        assertEquals("Shared", InsightsAnalyzer.layerOf("com.app.util"));
        assertEquals("Unclassified", InsightsAnalyzer.layerOf("com.app.misc"));
    }

    @Test
    public void testSummaryCounts() {
        String a = "package com.foo;\n"
                + "public class A {}\n";
        String b = "package com.foo;\n"
                + "public class B {}\n";
        InsightsModel model = analyze(parse(a, b));
        assertEquals(2, model.getClassCount());
        assertEquals(Integer.valueOf(2),
                model.getClassCountByPackage().get("com.foo"));
        assertEquals("Unclassified", model.getLayerByPackage().get("com.foo"));
    }
}
