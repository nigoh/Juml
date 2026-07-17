// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * DiagramScope の各フィルタ単位テスト (applyScope は DiagramService 経由)。
 */
public class DiagramScopeTest {

    private static JavaClassInfo cls(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        return c;
    }

    @Test
    public void testEmptyScopeReturnsAll() {
        DiagramScope s = DiagramScope.builder().build();
        assertTrue(s.isEmpty());
        List<JavaClassInfo> input = Arrays.asList(cls("a", "A"), cls("b", "B"));
        List<JavaClassInfo> out = DiagramService.applyScope(input, s, null);
        assertEquals(2, out.size());
    }

    @Test
    public void testIncludedPackagePrefix() {
        DiagramScope s = DiagramScope.builder()
                .includePackage("com.car").build();
        List<JavaClassInfo> input = Arrays.asList(
                cls("com.car", "A"),
                cls("com.car.sub", "B"),
                cls("com.other", "C"));
        List<JavaClassInfo> out = DiagramService.applyScope(input, s, null);
        assertEquals(2, out.size());
        assertEquals("A", out.get(0).getSimpleName());
        assertEquals("B", out.get(1).getSimpleName());
    }

    @Test
    public void testRegexFilter() {
        DiagramScope s = DiagramScope.builder().classNameRegex(".*Service$").build();
        List<JavaClassInfo> input = Arrays.asList(
                cls("p", "FooService"),
                cls("p", "BarController"),
                cls("p", "BazService"));
        List<JavaClassInfo> out = DiagramService.applyScope(input, s, null);
        assertEquals(2, out.size());
    }

    @Test
    public void testModuleFilter() {
        DiagramScope s = DiagramScope.builder()
                .includeModule(":app").build();
        List<JavaClassInfo> input = Arrays.asList(
                cls("p", "X"),
                cls("p", "Y"));
        Map<String, String> qnToModule = new HashMap<>();
        qnToModule.put("p.X", ":app");
        qnToModule.put("p.Y", ":lib");
        List<JavaClassInfo> out = DiagramService.applyScope(input, s, qnToModule);
        assertEquals(1, out.size());
        assertEquals("X", out.get(0).getSimpleName());
    }

    @Test
    public void testSeedNeighborHops() {
        JavaClassInfo a = cls("p", "A");
        a.setSuperClass("B");
        JavaClassInfo b = cls("p", "B");
        b.getInterfaces().add("C");
        JavaClassInfo c = cls("p", "C");
        JavaClassInfo d = cls("p", "D"); // 無関係
        DiagramScope s = DiagramScope.builder()
                .seed("p.A").neighborHops(1).build();
        List<JavaClassInfo> out = DiagramService.applyScope(
                Arrays.asList(a, b, c, d), s, null);
        // A + B (hop=1) のみ。C は hop=2、D は到達不可
        assertEquals(2, out.size());
    }

    @Test
    public void testSeedHopsZero() {
        JavaClassInfo a = cls("p", "A");
        a.setSuperClass("B");
        JavaClassInfo b = cls("p", "B");
        DiagramScope s = DiagramScope.builder()
                .seed("p.A").neighborHops(0).build();
        List<JavaClassInfo> out = DiagramService.applyScope(
                Arrays.asList(a, b), s, null);
        assertEquals(1, out.size());
        assertEquals("A", out.get(0).getSimpleName());
    }

    @Test
    public void testCombinedFilters() {
        DiagramScope s = DiagramScope.builder()
                .includePackage("com.car")
                .classNameRegex(".*Manager$")
                .build();
        List<JavaClassInfo> input = Arrays.asList(
                cls("com.car", "CarManager"),
                cls("com.car", "Helper"),
                cls("com.other", "OtherManager"));
        List<JavaClassInfo> out = DiagramService.applyScope(input, s, null);
        assertEquals(1, out.size());
        assertEquals("CarManager", out.get(0).getSimpleName());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(DiagramScope.builder().build().isEmpty());
        assertFalse(DiagramScope.builder().includePackage("p").build().isEmpty());
        assertFalse(DiagramScope.builder().maxClasses(10).build().isEmpty());
        // 個別クラス除外があれば isEmpty=false (applyScope の早期リターンを通さないため)
        assertFalse(DiagramScope.builder().excludeClass("p.A").build().isEmpty());
    }

    @Test
    public void testExcludeClassFilters() {
        DiagramScope s = DiagramScope.builder()
                .excludeClass("p.B").build();
        List<JavaClassInfo> input = Arrays.asList(
                cls("p", "A"), cls("p", "B"), cls("p", "C"));
        List<JavaClassInfo> out = DiagramService.applyScope(input, s, null);
        assertEquals(2, out.size());
        assertEquals("A", out.get(0).getSimpleName());
        assertEquals("C", out.get(1).getSimpleName());
    }

    @Test
    public void testToBuilderPreservesExcludedClassesAndClearResets() {
        DiagramScope s = DiagramScope.builder()
                .excludeClass("p.A")
                .excludeClass("p.B")
                .focusClass("p.C")
                .build();
        // toBuilder は個別除外と focus を引き継ぐ
        DiagramScope copy = s.toBuilder().build();
        assertTrue(copy.getExcludedQualifiedNames().contains("p.A"));
        assertTrue(copy.getExcludedQualifiedNames().contains("p.B"));
        assertEquals("p.C", copy.getFocusClass());
        // clearExcludedClasses + focusClass("") で整形だけ解除できる
        DiagramScope reset = s.toBuilder().clearExcludedClasses().focusClass("").build();
        assertTrue(reset.getExcludedQualifiedNames().isEmpty());
        assertTrue(reset.getFocusClass().isEmpty());
    }
}
