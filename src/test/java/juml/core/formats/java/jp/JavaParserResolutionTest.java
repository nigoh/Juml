// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SymbolSolver による呼び出し先解決 (P3a) のテスト。
 * チェーン呼び出し・継承・オーバーロードで {@code Call.resolvedOwnerFqn} が埋まることを検証する。
 */
public class JavaParserResolutionTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File srcRoot;

    private void write(String pkgPath, String content) throws IOException {
        File dir = new File(srcRoot, pkgPath);
        assertTrue(dir.mkdirs() || dir.isDirectory());
        String name = content.replaceAll("(?s).*\\b(class|interface)\\s+(\\w+).*", "$2");
        try (java.io.Writer w = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(new File(dir, name + ".java")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    private JavaMethodInfo.Call findCall(List<JavaClassInfo> cs, String cls,
                                         String method, String calleeName) {
        for (JavaClassInfo c : cs) {
            if (!cls.equals(c.getSimpleName())) {
                continue;
            }
            for (JavaMethodInfo m : c.getMethods()) {
                if (method.equals(m.getName())) {
                    for (JavaMethodInfo.Call call : m.getCalls()) {
                        if (calleeName.equals(call.getMethodName())) {
                            return call;
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<JavaClassInfo> parseWithSolver(String pkgPath, String fileContent)
            throws IOException {
        srcRoot = tmp.newFolder("src", "main", "java");
        // 依存型を配置
        write("p", "package p; public class C { public void hello() {} }");
        write("p", "package p; public class B { public C getC() { return new C(); } }");
        write("p", "package p; public class Base { public void common() {} }");
        write("p", "package p; public class Sub extends Base {}");
        write(pkgPath, fileContent);
        JpSolver solver = JpSolver.fromSourceRoots(
                java.util.Collections.singletonList(srcRoot));
        return JavaParserFrontend.parse(fileContent, false, null, solver);
    }

    @Test
    public void resolvesChainCallReceiverType() throws IOException {
        List<JavaClassInfo> cs = parseWithSolver("p",
                "package p; public class A {"
                + " private B b = new B();"
                + " void run() { b.getC().hello(); } }");
        JavaMethodInfo.Call hello = findCall(cs, "A", "run", "hello");
        assertNotNull("hello() call should be present", hello);
        assertEquals("p.C", hello.getResolvedOwnerFqn());
    }

    @Test
    public void resolvesInheritedMemberToDeclaringType() throws IOException {
        List<JavaClassInfo> cs = parseWithSolver("p",
                "package p; public class A {"
                + " private Sub s = new Sub();"
                + " void run() { s.common(); } }");
        JavaMethodInfo.Call common = findCall(cs, "A", "run", "common");
        assertNotNull(common);
        // 継承元の Base に解決される
        assertEquals("p.Base", common.getResolvedOwnerFqn());
    }

    @Test
    public void resolvedSignatureIsPopulated() throws IOException {
        List<JavaClassInfo> cs = parseWithSolver("p",
                "package p; public class A {"
                + " private B b = new B();"
                + " void run() { b.getC(); } }");
        JavaMethodInfo.Call getC = findCall(cs, "A", "run", "getC");
        assertNotNull(getC);
        assertEquals("p.B", getC.getResolvedOwnerFqn());
        assertNotNull(getC.getResolvedSignature());
        assertTrue(getC.getResolvedSignature(), getC.getResolvedSignature().contains("getC"));
    }
}
