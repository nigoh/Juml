// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import org.junit.Test;
import juml.core.formats.uml.AidlParser;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AidlBindingResolver} の AIDL → 実装紐付け検証。
 */
public class AidlBindingResolverTest {

    private static List<JavaClassInfo> mixed(String aidl, String... javaSources) {
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(AidlParser.parse(aidl, ErrorListener.silent()));
        for (String s : javaSources) {
            all.addAll(JavaStructureExtractor.extract(s, ErrorListener.silent()));
        }
        return all;
    }

    @Test
    public void resolvesSimpleNameStubExtension() {
        String aidl = "package com.x;\n"
                + "interface ICarFoo {\n"
                + "  void doIt();\n"
                + "}\n";
        String impl = "package com.x;\n"
                + "public class CarFooService extends ICarFoo.Stub {\n"
                + "  public void doIt() {}\n"
                + "}\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl, impl));
        List<AidlBinding> impls = bindings.get("com.x.ICarFoo");
        assertNotNull(impls);
        assertEquals(1, impls.size());
        assertEquals("com.x.CarFooService", impls.get(0).getImplementationFqn());
    }

    @Test
    public void resolvesSameSimpleNameCollisionViaImports() {
        // 同名 AIDL (a.IFoo と b.IFoo) が両方ある場合、実装の import で正しい方へ紐付ける。
        // 衝突候補を潰さずに保持していることを担保する。
        String aidlA = "package a;\n"
                + "interface IFoo { void f(); }\n";
        String aidlB = "package b;\n"
                + "interface IFoo { void f(); }\n";
        String impl = "package svc;\n"
                + "import a.IFoo;\n"
                + "public class FooImpl extends IFoo.Stub {\n"
                + "  public void f() {}\n"
                + "}\n";
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(AidlParser.parse(aidlA, ErrorListener.silent()));
        // b.IFoo を後に追加: 旧実装 (last-wins) なら b.IFoo に誤紐付けされていた
        all.addAll(AidlParser.parse(aidlB, ErrorListener.silent()));
        all.addAll(JavaStructureExtractor.extract(impl, ErrorListener.silent()));
        Map<String, List<AidlBinding>> bindings = new AidlBindingResolver().resolve(all);
        assertEquals(1, bindings.get("a.IFoo").size());
        assertEquals("svc.FooImpl", bindings.get("a.IFoo").get(0).getImplementationFqn());
        // b.IFoo には紐付かない
        assertEquals(0, bindings.get("b.IFoo").size());
    }

    @Test
    public void unboundAidlReportedWithEmptyList() {
        String aidl = "package com.x;\n"
                + "interface ICarBar { void f(); }\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl));
        assertTrue(bindings.containsKey("com.x.ICarBar"));
        assertEquals(0, bindings.get("com.x.ICarBar").size());
    }

    @Test
    public void resolvesViaImportFqn() {
        String aidl = "package com.x.car;\n"
                + "interface ICarHvac { void f(); }\n";
        String impl = "package com.svc;\n"
                + "import com.x.car.ICarHvac;\n"
                + "public class HvacService extends ICarHvac.Stub {\n"
                + "  public void f() {}\n"
                + "}\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl, impl));
        List<AidlBinding> impls = bindings.get("com.x.car.ICarHvac");
        assertNotNull(impls);
        assertEquals(1, impls.size());
        assertEquals("com.svc.HvacService", impls.get(0).getImplementationFqn());
    }

    @Test
    public void markdownReportContainsBindingRow() {
        String aidl = "package com.x;\n"
                + "interface ICarFoo { void doIt(); }\n";
        String impl = "package com.x;\n"
                + "public class CarFooService extends ICarFoo.Stub {}\n";
        Map<String, List<AidlBinding>> bindings =
                new AidlBindingResolver().resolve(mixed(aidl, impl));
        String md = MarkdownAidlBindingReport.render(bindings);
        assertTrue(md.contains("ICarFoo"));
        assertTrue(md.contains("CarFooService"));
        assertTrue(md.contains("AIDL"));
    }
}
