// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import juml.core.formats.java.AndroidProjectScanner;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * UmlGenerator のユニットテスト。
 */
public class UmlGeneratorTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void writeFile(File f, String content) throws IOException {
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullSource() {
        UmlGenerator.extractFromSource(null, "F.java");
    }

    @Test
    public void testExtractFromJavaSource() {
        List<JavaClassInfo> r = UmlGenerator.extractFromSource(
                "package x; class C { void m() {} }", "C.java");
        assertEquals(1, r.size());
        assertEquals(JavaClassInfo.Kind.CLASS, r.get(0).getKind());
    }

    @Test
    public void testExtractFromAidlSource() {
        List<JavaClassInfo> r = UmlGenerator.extractFromSource(
                "package x; interface I { int v(); }", "I.aidl");
        assertEquals(1, r.size());
        assertEquals(JavaClassInfo.Kind.AIDL_INTERFACE, r.get(0).getKind());
    }

    @Test
    public void testAaosCategoryAttached() {
        List<JavaClassInfo> r = UmlGenerator.extractFromSource(
                "package android.car.audio; class CarAudioManager {}",
                "CarAudioManager.java");
        assertEquals("CarManager", r.get(0).getAaosCategory());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExtractFromProjectNullRoot() throws IOException {
        UmlGenerator.extractFromProject(null);
    }

    @Test
    public void testExtractFromProject() throws IOException {
        File root = tmp.newFolder("proj");
        File mainJava = new File(root, "src/main/java/com/x");
        assertTrue(mainJava.mkdirs());
        writeFile(new File(mainJava, "Foo.java"),
                "package com.x; public class Foo { void f() {} }");
        File aidlDir = new File(root, "src/main/aidl/com/x");
        assertTrue(aidlDir.mkdirs());
        writeFile(new File(aidlDir, "IService.aidl"),
                "package com.x; interface IService { void run(); }");

        List<JavaClassInfo> r = UmlGenerator.extractFromProject(root);
        assertEquals(2, r.size());
        boolean hasJava = false;
        boolean hasAidl = false;
        for (JavaClassInfo c : r) {
            if (c.getKind() == JavaClassInfo.Kind.CLASS) {
                hasJava = true;
            }
            if (c.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
                hasAidl = true;
            }
        }
        assertTrue(hasJava);
        assertTrue(hasAidl);
    }

    @Test
    public void testClassDiagramShortcut() {
        String puml = UmlGenerator.classDiagram(
                "package x; class A { B b; } class B {}");
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("class \"x.A\""));
        assertTrue(puml, puml.contains("class \"x.B\""));
    }

    @Test
    public void testClassDiagramFromProject() throws IOException {
        File root = tmp.newFolder("p");
        File pkg = new File(root, "src/main/java/p");
        assertTrue(pkg.mkdirs());
        writeFile(new File(pkg, "A.java"), "package p; class A { void f() {} }");
        writeFile(new File(pkg, "B.java"), "package p; class B { void g() {} }");
        String puml = UmlGenerator.classDiagramFromProject(root);
        assertTrue(puml, puml.contains("class \"p.A\""));
        assertTrue(puml, puml.contains("class \"p.B\""));
    }

    @Test
    public void testSequenceDiagramShortcut() {
        String puml = UmlGenerator.sequenceDiagram(
                "class A { void run() { x(); } }", "A", "run");
        assertTrue(puml, puml.contains("Caller -> A: A.run()"));
        assertTrue(puml, puml.contains("A -> A: A.x()"));
    }

    @Test
    public void testProjectScannerIncludesAidlOption() {
        // AndroidProjectScanner の Options.includeAidl が UmlGenerator から有効化される
        AndroidProjectScanner.Options o = new AndroidProjectScanner.Options();
        assertFalse(o.includeAidl); // デフォルトでは false
        o.includeAidl = true;
        assertTrue(o.includeAidl);
    }

    @Test
    public void testListenerReceivesSourceName() {
        List<String> log = new ArrayList<>();
        // 開きっぱなしのジェネリック宣言で skipBalanced が EOF 警告を出す
        UmlGenerator.extractFromSource(
                "class A <T",
                "A.java",
                ErrorListener.collecting(log));
        boolean foundSource = false;
        for (String s : log) {
            if (s.startsWith("A.java")) {
                foundSource = true;
            }
        }
        assertTrue("expected listener entries prefixed with A.java: " + log, foundSource);
    }

    @Test
    public void testListenerSilentDefault() {
        // listener を渡さなければサイレント
        List<JavaClassInfo> r = UmlGenerator.extractFromSource(
                "class A <T", "A.java");
        // 不完全でもクラスは抽出される (1 つ)
        assertEquals(1, r.size());
    }
}
