// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiagramTabSupport} の純粋な分岐ロジックを網羅する (package-private ヘルパ)。
 *
 * <p>Swing コンポーネントを生成しない純関数のみを扱うため headless でも安全に実行できる。
 * 検証対象: {@code tooltipFor} / {@code iconFor} / {@code toDiagramRequest} の
 * METHOD (ACTIVITY/CALLGRAPH/SEQUENCE) / CLASS / PACKAGE / MODULE / SOONG 分岐、
 * および {@code treeSync == null} のフォールバック。</p>
 */
public class DiagramTabSupportTest {

    private static JavaClassInfo classInfo(String fqn) {
        JavaClassInfo ci = new JavaClassInfo();
        int dot = fqn.lastIndexOf('.');
        if (dot >= 0) {
            ci.setPackageName(fqn.substring(0, dot));
            ci.setSimpleName(fqn.substring(dot + 1));
        } else {
            ci.setSimpleName(fqn);
        }
        return ci;
    }

    private static JavaMethodInfo methodInfo(String name) {
        JavaMethodInfo mi = new JavaMethodInfo();
        mi.setName(name);
        return mi;
    }

    // -------------------------------------------------------------------------
    // tooltipFor
    // -------------------------------------------------------------------------

    @Test
    public void tooltipFor_method_includesClassAndMethodName() {
        JavaClassInfo ci = classInfo("com.example.Svc");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                ci, methodInfo("handle"), DiagramKind.SEQUENCE);
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        String tip = DiagramTabSupport.tooltipFor(spec, req);

        assertTrue("メソッドタブのツールチップは FQN を含むはず: " + tip,
                tip.contains("com.example.Svc"));
        assertTrue("メソッドタブのツールチップは #メソッド名 を含むはず: " + tip,
                tip.contains("#handle"));
    }

    @Test
    public void tooltipFor_classNode_includesQualifiedName() {
        JavaClassInfo ci = classInfo("com.example.Foo");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        String tip = DiagramTabSupport.tooltipFor(spec, req);

        assertTrue("クラスタブのツールチップは FQN を含むはず: " + tip,
                tip.contains("com.example.Foo"));
    }

    @Test
    public void tooltipFor_packageNode_mentionsPackage() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.pkg("com.example.pkg");
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        String tip = DiagramTabSupport.tooltipFor(spec, req);

        assertEquals("Class — package com.example.pkg", tip);
    }

    @Test
    public void tooltipFor_moduleNode_mentionsModule() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.module("app");
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        String tip = DiagramTabSupport.tooltipFor(spec, req);

        assertEquals("Class — module app", tip);
    }

    @Test
    public void tooltipFor_nullTreeSync_fallsBackToWholeProject() {
        DiagramRequest spec = new DiagramRequest(DiagramKind.CLASS);

        String tip = DiagramTabSupport.tooltipFor(spec, null);

        assertTrue("treeSync が null のときは whole project フォールバックになるはず: " + tip,
                tip.endsWith(" diagram (whole project)"));
    }

    // -------------------------------------------------------------------------
    // iconFor
    // -------------------------------------------------------------------------

    @Test
    public void iconFor_methodActivity_returnsActivityIcon() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                classInfo("com.example.Svc"), methodInfo("run"), DiagramKind.ACTIVITY);
        assertSame(TreeNodeIcon.ACTIVITY, DiagramTabSupport.iconFor(req));
    }

    @Test
    public void iconFor_methodCallGraph_returnsSequenceIcon() {
        // ACTIVITY 以外のメソッド系図種 (CALLGRAPH/SEQUENCE) はシーケンスアイコンで代表する。
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                classInfo("com.example.Svc"), methodInfo("run"), DiagramKind.CALLGRAPH);
        assertSame(TreeNodeIcon.SEQUENCE, DiagramTabSupport.iconFor(req));
    }

    @Test
    public void iconFor_methodSequence_returnsSequenceIcon() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                classInfo("com.example.Svc"), methodInfo("run"), DiagramKind.SEQUENCE);
        assertSame(TreeNodeIcon.SEQUENCE, DiagramTabSupport.iconFor(req));
    }

    @Test
    public void iconFor_classNode_returnsClassIcon() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(classInfo("com.example.Foo"));
        assertSame(TreeNodeIcon.CLASS, DiagramTabSupport.iconFor(req));
    }

    @Test
    public void iconFor_packageNode_returnsPackageIcon() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.pkg("com.example");
        assertSame(TreeNodeIcon.PACKAGE, DiagramTabSupport.iconFor(req));
    }

    @Test
    public void iconFor_moduleNode_returnsModuleIcon() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.module("app");
        assertSame(TreeNodeIcon.MODULE, DiagramTabSupport.iconFor(req));
    }

    @Test
    public void iconFor_soongNode_fallsBackToModuleIcon() {
        // iconFor は METHOD/CLASS/PACKAGE 以外をすべて MODULE アイコンにフォールバックする。
        TreeNodeOpenRequest req = TreeNodeOpenRequest.soong("libfoo");
        assertSame(TreeNodeIcon.MODULE, DiagramTabSupport.iconFor(req));
    }

    // -------------------------------------------------------------------------
    // toDiagramRequest
    // -------------------------------------------------------------------------

    @Test
    public void toDiagramRequest_methodActivity_buildsActivityRequest() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                classInfo("com.example.Svc"), methodInfo("run"), DiagramKind.ACTIVITY);
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        assertEquals(DiagramKind.ACTIVITY, spec.getKind());
        assertEquals("Svc", spec.getActivityEntryClass());
        assertEquals("run", spec.getActivityEntryMethod());
    }

    @Test
    public void toDiagramRequest_methodCallGraph_buildsCallGraphRequest() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                classInfo("com.example.Svc"), methodInfo("run"), DiagramKind.CALLGRAPH);
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        assertEquals(DiagramKind.CALLGRAPH, spec.getKind());
    }

    @Test
    public void toDiagramRequest_methodSequence_buildsSequenceRequest() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.method(
                classInfo("com.example.Svc"), methodInfo("run"), DiagramKind.SEQUENCE);
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        assertEquals(DiagramKind.SEQUENCE, spec.getKind());
        assertEquals("Svc", spec.getSequenceEntryClass());
        assertEquals("run", spec.getSequenceEntryMethod());
    }

    @Test
    public void toDiagramRequest_classNode_focusesClickedClass() {
        JavaClassInfo ci = classInfo("com.example.Foo");
        TreeNodeOpenRequest req = TreeNodeOpenRequest.classNode(ci);
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        assertEquals(DiagramKind.CLASS, spec.getKind());
        assertTrue("クラスタブは interactiveLinks を有効にするはず", spec.isInteractiveLinks());
        assertEquals("com.example.Foo", spec.getScope().getFocusClass());
    }

    @Test
    public void toDiagramRequest_packageNode_scopesToPackage() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.pkg("com.example.pkg");
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        assertEquals(DiagramKind.CLASS, spec.getKind());
        assertTrue(spec.getScope().getIncludedPackages().contains("com.example.pkg"));
    }

    @Test
    public void toDiagramRequest_moduleNode_scopesToModule() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.module("app");
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        assertEquals(DiagramKind.CLASS, spec.getKind());
        assertTrue(spec.getScope().getIncludedModules().contains("app"));
    }

    @Test
    public void toDiagramRequest_soongNode_buildsSoongRequest() {
        TreeNodeOpenRequest req = TreeNodeOpenRequest.soong("libfoo");
        DiagramRequest spec = DiagramTabSupport.toDiagramRequest(req);

        assertEquals(DiagramKind.SOONG, spec.getKind());
    }
}
