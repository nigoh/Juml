// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DiagramService に DiagramScope を適用したときに生成 PlantUML がスコープを尊重するテスト。
 */
public class DiagramServiceScopeTest {

    private static JavaClassInfo cls(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        return c;
    }

    private List<JavaClassInfo> sample() {
        List<JavaClassInfo> l = new ArrayList<>();
        l.add(cls("com.car", "CarManager"));
        l.add(cls("com.car", "CarHelper"));
        l.add(cls("com.other", "OtherClass"));
        return l;
    }

    @Test
    public void testScopeFiltersClassDiagram() {
        DiagramScope scope = DiagramScope.builder()
                .includePackage("com.car").build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue("@startuml で始まる", puml.startsWith("@startuml"));
        assertTrue("CarManager は含まれる", puml.contains("CarManager"));
        assertTrue("CarHelper は含まれる", puml.contains("CarHelper"));
        assertFalse("OtherClass はスコープ外", puml.contains("OtherClass"));
    }

    @Test
    public void testScopeRegexLimits() {
        DiagramScope scope = DiagramScope.builder()
                .classNameRegex(".*Manager$").build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue(puml.contains("CarManager"));
        assertFalse(puml.contains("CarHelper"));
        assertFalse(puml.contains("OtherClass"));
    }

    @Test
    public void testFooterWarningWhenScopeShrinks() {
        DiagramScope scope = DiagramScope.builder()
                .includePackage("com.car").build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue("スコープで件数が減ったら footer に表示", puml.contains("footer "));
        assertTrue(puml.contains("scope filter"));
    }

    @Test
    public void testMaxClassesAddsTruncationFooter() {
        DiagramScope scope = DiagramScope.builder().maxClasses(2).build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        // maxClasses 2 < 3 件なので切り詰め + warning
        assertTrue(puml.contains("showing 2 of 3"));
    }

    @Test
    public void testNoScopeAllowsAll() {
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue(puml.contains("CarManager"));
        assertTrue(puml.contains("CarHelper"));
        assertTrue(puml.contains("OtherClass"));
        assertFalse("scope 警告なし", puml.contains("scope filter"));
    }

    @Test
    public void testScopeExcludesPackage() {
        DiagramScope scope = DiagramScope.builder()
                .excludePackage("com.other").build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, sample(), null);
        assertTrue("CarManager は残る", puml.contains("CarManager"));
        assertFalse("除外パッケージのクラスは消える", puml.contains("OtherClass"));
    }

    @Test
    public void testScopeExcludesExternalLibraries() {
        List<JavaClassInfo> mixed = new ArrayList<>();
        mixed.add(cls("com.app", "MyClass"));
        JavaClassInfo ext = cls("com.app.dep", "DepClass");
        ext.setOrigin(JavaClassInfo.Origin.EXTERNAL_JAR);
        mixed.add(ext);
        mixed.add(cls("android.view", "View"));

        DiagramScope scope = DiagramScope.builder()
                .excludeExternalLibraries(true).build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, mixed, null);
        assertTrue("project class remains", puml.contains("MyClass"));
        assertFalse("EXTERNAL_JAR class removed",
                puml.contains("DepClass"));
        assertFalse("android.* class removed by prefix",
                puml.contains("android.view"));
    }

    @Test
    public void testScopePropagatesPublicOnlyToOptions() {
        // public でないクラスがフィルタされることを確認
        JavaClassInfo pub = cls("com.app", "PublicCls");
        pub.getModifiers().add("public");
        JavaClassInfo pkg = cls("com.app", "PkgCls"); // 修飾子なし = package-private
        List<JavaClassInfo> infos = new ArrayList<>();
        infos.add(pub);
        infos.add(pkg);

        DiagramScope scope = DiagramScope.builder()
                .visibilityFilter(VisibilityFilter.PUBLIC_ONLY).build();
        DiagramRequest req = new DiagramRequest(DiagramKind.CLASS, null, null, false, scope);
        String puml = DiagramService.generatePuml(req, null, infos, null);
        assertTrue("public class remains", puml.contains("PublicCls"));
        assertFalse("non-public class hidden", puml.contains("PkgCls"));
    }
}
