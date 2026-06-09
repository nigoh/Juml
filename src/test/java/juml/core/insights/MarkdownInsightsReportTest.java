// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.insights;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * {@link MarkdownInsightsReport} のユニットテスト。
 */
public class MarkdownInsightsReportTest {

    private static InsightsModel sampleModel() {
        InsightsModel m = new InsightsModel();
        m.setClassCount(3);
        m.setReferenceCount(5);
        m.addEntryPoint(new InsightsModel.EntryPoint(
                InsightsModel.EntryPointKind.MAIN, "com.foo.Main", "public static main"));
        m.addHotspot(new InsightsModel.Hotspot(
                "com.foo.Core", 4, 2, List.of("com.foo.U1", "com.foo.U2")));
        Set<String> cyclePkgs = new LinkedHashSet<>(List.of("pkg.a", "pkg.b"));
        InsightsModel.PackageEdge e1 = new InsightsModel.PackageEdge("pkg.a", "pkg.b");
        InsightsModel.PackageEdge e2 = new InsightsModel.PackageEdge("pkg.b", "pkg.a");
        m.addPackageEdge(e1);
        m.addPackageEdge(e2);
        m.addPackageCycle(new InsightsModel.PackageCycle(cyclePkgs, List.of(e1, e2)));
        m.addDeadCodeCandidate(new InsightsModel.DeadCodeCandidate(
                InsightsModel.DeadCodeCandidate.Kind.METHOD,
                "com.foo.Core.unused", "no call sites found", "MEDIUM"));
        m.putLayer("pkg.a", "Presentation");
        m.putLayer("pkg.b", "Unclassified");
        m.putClassCount("pkg.a", 2);
        m.putClassCount("pkg.b", 1);
        return m;
    }

    @Test
    public void testNullModel() {
        String md = MarkdownInsightsReport.render(null);
        assertTrue(md, md.contains("# Architecture Insights"));
        assertTrue(md, md.contains("(no data)"));
    }

    @Test
    public void testSectionHeadings() {
        String md = MarkdownInsightsReport.render(sampleModel());
        assertTrue(md, md.contains("# Architecture Insights"));
        assertTrue(md, md.contains("## Summary"));
        assertTrue(md, md.contains("## Entry Points"));
        assertTrue(md, md.contains("## Hotspots"));
        assertTrue(md, md.contains("## Package Cycles"));
        assertTrue(md, md.contains("## Dead Code Candidates"));
        assertTrue(md, md.contains("## Estimated Layers"));
    }

    @Test
    public void testTableContents() {
        String md = MarkdownInsightsReport.render(sampleModel());
        assertTrue(md, md.contains("| Kind | Class | Detail |"));
        assertTrue(md, md.contains("`com.foo.Main`"));
        assertTrue(md, md.contains("| Class | Fan-in | Fan-out | Top referrers |"));
        assertTrue(md, md.contains("`com.foo.Core`"));
        assertTrue(md, md.contains("| Symbol | Kind | Reason | Confidence |"));
        assertTrue(md, md.contains("`com.foo.Core.unused`"));
        assertTrue(md, md.contains("MEDIUM"));
    }

    @Test
    public void testCycleSection() {
        String md = MarkdownInsightsReport.render(sampleModel());
        assertTrue(md, md.contains("### Cycle 1"));
        assertTrue(md, md.contains("`pkg.a` → `pkg.b`"));
        assertTrue(md, md.contains("`pkg.b` → `pkg.a`"));
    }

    @Test
    public void testDisclaimerPresent() {
        String md = MarkdownInsightsReport.render(sampleModel());
        assertTrue(md, md.contains("静的解析による推定です"));
    }

    @Test
    public void testEmptyModelFallbacks() {
        String md = MarkdownInsightsReport.render(new InsightsModel());
        assertTrue(md, md.contains("(no entry points detected)"));
        assertTrue(md, md.contains("(no references indexed)"));
        assertTrue(md, md.contains("循環依存は検出されませんでした"));
        assertTrue(md, md.contains("デッドコード候補は検出されませんでした"));
        assertTrue(md, md.contains("(no packages)"));
    }

    @Test
    public void testLayerGrouping() {
        String md = MarkdownInsightsReport.render(sampleModel());
        assertTrue(md, md.contains("### Presentation"));
        assertTrue(md, md.contains("### Unclassified"));
        // Unclassified は最後に来る
        assertTrue(md, md.indexOf("### Presentation") < md.indexOf("### Unclassified"));
        assertTrue(md, md.contains("`pkg.a` (2 classes)"));
        assertTrue(md, md.contains("`pkg.b` (1 class)"));
    }
}
