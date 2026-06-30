// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.refs;

import org.junit.Test;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.util.ErrorListener;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ReferenceIndexBuilder} が JavaClassInfo ツリーから逆参照を構築できることを確認。
 */
public class ReferenceIndexBuilderTest {

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

    @Test
    public void callViaFieldIsRegistered() {
        String a = "package com.foo;\n"
                + "public class Bar {\n"
                + "  public void doIt() {}\n"
                + "}\n";
        String b = "package com.foo;\n"
                + "public class UseSite {\n"
                + "  private Bar bar;\n"
                + "  void run() {\n"
                + "    bar.doIt();\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> classes = parse(a, b);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> sites = refIdx.sites(
                ReferenceKey.ofMethod("com.foo.Bar", "doIt"));
        assertEquals(1, sites.size());
        assertEquals("com.foo.UseSite", sites.get(0).getCallerFqn());
        assertEquals("run", sites.get(0).getCallerMethod());
        assertEquals(ReferenceSite.Kind.CALL, sites.get(0).getKind());
    }

    @Test
    public void callViaParameterIsRegistered() {
        String a = "package com.foo;\n"
                + "public class Bar {\n"
                + "  public void doIt() {}\n"
                + "}\n";
        String b = "package com.foo;\n"
                + "public class UseSite {\n"
                + "  void run(Bar bar) {\n"
                + "    bar.doIt();\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> classes = parse(a, b);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> sites = refIdx.sites(
                ReferenceKey.ofMethod("com.foo.Bar", "doIt"));
        assertEquals(1, sites.size());
        assertEquals("com.foo.UseSite", sites.get(0).getCallerFqn());
    }

    @Test
    public void staticCallIsRegistered() {
        String a = "package com.foo;\n"
                + "public class Bar {\n"
                + "  public static void doIt() {}\n"
                + "}\n";
        String b = "package com.foo;\n"
                + "public class UseSite {\n"
                + "  void run() {\n"
                + "    Bar.doIt();\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> classes = parse(a, b);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> sites = refIdx.sites(
                ReferenceKey.ofMethod("com.foo.Bar", "doIt"));
        assertEquals(1, sites.size());
        assertEquals("com.foo.UseSite", sites.get(0).getCallerFqn());
    }

    @Test
    public void extendsAndImplementsAreRegistered() {
        String parent = "package com.foo;\n"
                + "public class Base {}\n";
        String iface = "package com.foo;\n"
                + "public interface Listener {}\n";
        String child = "package com.foo;\n"
                + "public class Sub extends Base implements Listener {}\n";
        List<JavaClassInfo> classes = parse(parent, iface, child);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> baseSites = refIdx.sites(
                ReferenceKey.ofClass("com.foo.Base"));
        boolean foundExtends = false;
        for (ReferenceSite s : baseSites) {
            if (s.getKind() == ReferenceSite.Kind.EXTENDS
                    && "com.foo.Sub".equals(s.getCallerFqn())) {
                foundExtends = true;
            }
        }
        assertTrue("EXTENDS site missing", foundExtends);

        List<ReferenceSite> ifaceSites = refIdx.sites(
                ReferenceKey.ofClass("com.foo.Listener"));
        boolean foundImpl = false;
        for (ReferenceSite s : ifaceSites) {
            if (s.getKind() == ReferenceSite.Kind.IMPLEMENTS
                    && "com.foo.Sub".equals(s.getCallerFqn())) {
                foundImpl = true;
            }
        }
        assertTrue("IMPLEMENTS site missing", foundImpl);
    }

    @Test
    public void fieldTypeReferenceIsRegistered() {
        String a = "package com.foo;\n"
                + "public class Bar {}\n";
        String b = "package com.foo;\n"
                + "public class Holder {\n"
                + "  private Bar bar;\n"
                + "}\n";
        List<JavaClassInfo> classes = parse(a, b);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> sites = refIdx.sites(
                ReferenceKey.ofClass("com.foo.Bar"));
        boolean foundTypeRef = false;
        for (ReferenceSite s : sites) {
            if (s.getKind() == ReferenceSite.Kind.TYPE_REFERENCE
                    && "com.foo.Holder".equals(s.getCallerFqn())) {
                foundTypeRef = true;
            }
        }
        assertTrue("TYPE_REFERENCE site missing", foundTypeRef);
    }

    @Test
    public void importIsRegisteredAsReference() {
        String a = "package com.foo;\n"
                + "public class Bar {}\n";
        String b = "package com.baz;\n"
                + "import com.foo.Bar;\n"
                + "public class User {\n"
                + "  void f(Bar b) {}\n"
                + "}\n";
        List<JavaClassInfo> classes = parse(a, b);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> sites = refIdx.sites(
                ReferenceKey.ofClass("com.foo.Bar"));
        boolean foundImport = false;
        for (ReferenceSite s : sites) {
            if (s.getKind() == ReferenceSite.Kind.IMPORT
                    && "com.baz.User".equals(s.getCallerFqn())) {
                foundImport = true;
            }
        }
        assertTrue("IMPORT site missing", foundImport);
    }

    @Test
    public void annotationArgumentClassLiteralIsRegistered() {
        // @TypeConverters({Converters.class}) の引数内クラスリテラルも参照として数える
        // (Room/Dagger で多用。無視すると Converters が dead-code 誤検出される)
        String conv = "package com.foo;\n"
                + "public class Converters { public String x() { return \"\"; } }\n";
        String db = "package com.foo;\n"
                + "@TypeConverters({Converters.class})\n"
                + "public abstract class AppDb {}\n";
        List<JavaClassInfo> classes = parse(conv, db);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> sites = refIdx.sites(ReferenceKey.ofClass("com.foo.Converters"));
        assertTrue("Converters referenced via annotation class literal", !sites.isEmpty());
        assertEquals(ReferenceSite.Kind.ANNOTATION, sites.get(0).getKind());
        assertEquals("com.foo.AppDb", sites.get(0).getCallerFqn());
    }

    @Test
    public void memberImportReferencesContainingClass() {
        // import static a.b.Constants.MAX (Java) / import a.b.Migrations.X (Kotlin) は
        // 含有クラス (Constants / Migrations) への参照として数える
        String constants = "package com.foo;\n"
                + "public class Constants { public static final int MAX = 1; }\n";
        String use = "package com.bar;\n"
                + "import static com.foo.Constants.MAX;\n"
                + "public class Use {}\n";
        List<JavaClassInfo> classes = parse(constants, use);
        ClassIndex idx = indexOf(classes);
        ReferenceIndex refIdx = new ReferenceIndex();
        new ReferenceIndexBuilder(refIdx, idx, null, ErrorListener.silent())
                .addAll(classes);

        List<ReferenceSite> sites = refIdx.sites(ReferenceKey.ofClass("com.foo.Constants"));
        assertTrue("member import should reference containing class", !sites.isEmpty());
        assertEquals("com.bar.Use", sites.get(0).getCallerFqn());
    }
}
