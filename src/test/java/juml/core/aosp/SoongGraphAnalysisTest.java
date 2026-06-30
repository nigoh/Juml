// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link SoongGraphAnalysis} の逆依存・循環検出テスト。
 */
public class SoongGraphAnalysisTest {

    private static List<AndroidBpModule> parse(String src) {
        return new AndroidBpParser().parseSource(src, "Android.bp");
    }

    @Test
    public void computesReverseDependencies() {
        String src = "cc_library { name: \"libcore\" }\n"
                + "cc_library { name: \"libfoo\", shared_libs: [\"libcore\"] }\n"
                + "cc_library { name: \"libbar\", static_libs: [\"libcore\"] }\n";
        SoongGraphAnalysis g = SoongGraphAnalysis.of(parse(src));
        assertTrue(g.reverseEdges().get("libcore").contains("libfoo"));
        assertTrue(g.reverseEdges().get("libcore").contains("libbar"));
        List<Map.Entry<String, Integer>> rank = g.mostDependedUpon(10);
        assertEquals("libcore", rank.get(0).getKey());
        assertEquals(Integer.valueOf(2), rank.get(0).getValue());
    }

    @Test
    public void ignoresExternalDependenciesInGraph() {
        String src = "cc_library { name: \"libfoo\", shared_libs: [\"libexternal\"] }\n";
        SoongGraphAnalysis g = SoongGraphAnalysis.of(parse(src));
        // 外部依存はローカルエッジに含めない
        assertTrue(g.forwardEdges().get("libfoo").isEmpty());
        assertFalse(g.localNames().contains("libexternal"));
    }

    @Test
    public void detectsTwoNodeCycle() {
        String src = "cc_library { name: \"libA\", shared_libs: [\"libB\"] }\n"
                + "cc_library { name: \"libB\", shared_libs: [\"libA\"] }\n";
        SoongGraphAnalysis g = SoongGraphAnalysis.of(parse(src));
        assertEquals(1, g.cycles().size());
        assertTrue(g.cycles().get(0).contains("libA"));
        assertTrue(g.cycles().get(0).contains("libB"));
        assertTrue(g.isInCycle("libA"));
    }

    @Test
    public void detectsThreeNodeCycle() {
        String src = "cc_library { name: \"a\", shared_libs: [\"b\"] }\n"
                + "cc_library { name: \"b\", shared_libs: [\"c\"] }\n"
                + "cc_library { name: \"c\", shared_libs: [\"a\"] }\n";
        SoongGraphAnalysis g = SoongGraphAnalysis.of(parse(src));
        assertEquals(1, g.cycles().size());
        assertEquals(3, g.cycles().get(0).size());
    }

    @Test
    public void acyclicGraphHasNoCycles() {
        String src = "cc_library { name: \"a\", shared_libs: [\"b\"] }\n"
                + "cc_library { name: \"b\", shared_libs: [\"c\"] }\n"
                + "cc_library { name: \"c\" }\n";
        SoongGraphAnalysis g = SoongGraphAnalysis.of(parse(src));
        assertTrue(g.cycles().isEmpty());
        assertFalse(g.isInCycle("a"));
    }

    @Test
    public void resolvesGeneratedAidlLibToInterface() {
        String src = "aidl_interface { name: \"android.hardware.foo\","
                + " versions: [\"1\", \"2\"] }\n"
                + "android_app { name: \"App\","
                + " static_libs: [\"android.hardware.foo-V2-java\"] }\n";
        SoongGraphAnalysis g = SoongGraphAnalysis.of(parse(src));
        // 生成ライブラリ名がローカル aidl_interface に解決され、ローカルエッジになる
        assertTrue(g.forwardEdges().get("App").contains("android.hardware.foo"));
        assertTrue(g.reverseEdges().get("android.hardware.foo").contains("App"));
        assertEquals("android.hardware.foo",
                g.canonical("android.hardware.foo-V2-java"));
        // 非 AIDL の通常ライブラリは畳み込まない
        assertEquals("libbar-V2-java", g.canonical("libbar-V2-java"));
    }

    @Test
    public void generatedAidlLibFoldedOutOfExternalRanking() {
        String src = "aidl_interface { name: \"android.hardware.foo\", versions: [\"1\"] }\n"
                + "android_app { name: \"App\","
                + " static_libs: [\"android.hardware.foo-V1-ndk\", \"libreallyexternal\"] }\n";
        String md = MarkdownSoongReport.render(parse(src));
        // 生成名は external に出ず、本当の外部依存だけ残る
        assertTrue(md.contains("libreallyexternal"));
        assertFalse(md.contains("android.hardware.foo-V1-ndk"));
    }

    @Test
    public void diagramStylesEdgesByKindAndHighlightsCycles() {
        String src = "cc_library { name: \"libfoo\","
                + " shared_libs: [\"libshared\"], static_libs: [\"libstatic\"],"
                + " header_libs: [\"libhdr\"] }\n"
                + "cc_library { name: \"libshared\" }\n"
                + "cc_library { name: \"libstatic\" }\n"
                + "cc_library { name: \"libhdr\" }\n"
                + "cc_library { name: \"libA\", shared_libs: [\"libB\"] }\n"
                + "cc_library { name: \"libB\", shared_libs: [\"libA\"] }\n";
        String puml = PlantUmlSoongDependencyDiagram.render(parse(src));
        // 種別ごとの矢印スタイル
        assertTrue(puml.contains("m_libfoo -[#1f6feb]-> m_libshared")); // shared
        assertTrue(puml.contains("m_libfoo -[#2da44e]-> m_libstatic")); // static
        assertTrue(puml.contains("m_libfoo -[#8250df,dashed]-> m_libhdr")); // header
        // 循環メンバは赤系背景で強調
        assertTrue(puml.contains("#F4A6A6"));
        // 凡例
        assertTrue(puml.contains("legend"));
        assertTrue(puml.contains("循環依存"));
    }

    @Test
    public void reportRendersCyclesAndReverseDeps() {
        String src = "cc_library { name: \"libcore\" }\n"
                + "cc_library { name: \"libfoo\", shared_libs: [\"libcore\"] }\n"
                + "cc_library { name: \"libA\", shared_libs: [\"libB\"] }\n"
                + "cc_library { name: \"libB\", shared_libs: [\"libA\"] }\n";
        String md = MarkdownSoongReport.render(parse(src));
        assertTrue(md.contains("被依存ランキング"));
        assertTrue(md.contains("循環依存"));
        assertTrue(md.contains("libcore"));
        assertTrue(md.contains("↔"));
    }
}
