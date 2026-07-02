// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.structdiff;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.core.structdiff.ClassStructureDiff.ChangeKind;
import juml.core.structdiff.ClassStructureDiff.ClassDiff;
import juml.core.structdiff.ClassStructureDiff.MemberDiff;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * {@link ClassStructureDiff} の構造差分計算を、実ソースの新旧 2 バージョンを
 * {@link JavaStructureExtractor} で解析した結果に対して検証する。
 */
public class ClassStructureDiffTest {

    private static List<ClassDiff> diff(String oldSrc, String newSrc) {
        return ClassStructureDiff.compare(
                oldSrc != null ? JavaStructureExtractor.extract(oldSrc) : List.of(),
                newSrc != null ? JavaStructureExtractor.extract(newSrc) : List.of());
    }

    private static ClassDiff byName(List<ClassDiff> diffs, String simpleName) {
        for (ClassDiff d : diffs) {
            if (d.anySide().getSimpleName().equals(simpleName)) {
                return d;
            }
        }
        fail("class not found in diff: " + simpleName);
        return null;
    }

    private static MemberDiff memberByLabelPart(List<MemberDiff> members, String part) {
        for (MemberDiff m : members) {
            if (m.label().contains(part)) {
                return m;
            }
        }
        fail("member not found: " + part);
        return null;
    }

    // -------------------------------------------------------------------------
    // クラス単位の追加・削除・不変
    // -------------------------------------------------------------------------

    @Test
    public void addedClass_isReportedWithAllMembers() {
        List<ClassDiff> diffs = diff(
                "class A { }",
                "class A { }\nclass B { int x; void run() {} }");
        assertEquals(ChangeKind.ADDED, byName(diffs, "B").kind);
        assertEquals(1, byName(diffs, "B").fields.size());
        assertEquals(1, byName(diffs, "B").methods.size());
        assertEquals(ChangeKind.ADDED, byName(diffs, "B").fields.get(0).kind);
        assertNull("追加メンバーに旧ラベルはない",
                byName(diffs, "B").fields.get(0).oldLabel);
    }

    @Test
    public void removedClass_isReportedAfterNewSideClasses() {
        List<ClassDiff> diffs = diff(
                "class A { }\nclass Gone { void bye() {} }",
                "class A { }");
        ClassDiff gone = byName(diffs, "Gone");
        assertEquals(ChangeKind.REMOVED, gone.kind);
        assertNull(gone.newClass);
        assertEquals(ChangeKind.REMOVED, gone.methods.get(0).kind);
        assertNull("削除メンバーに新ラベルはない", gone.methods.get(0).newLabel);
    }

    @Test
    public void identicalSource_isUnchanged() {
        String src = "class A { int x; void run(String s) {} }";
        List<ClassDiff> diffs = diff(src, src);
        assertEquals(1, diffs.size());
        assertEquals(ChangeKind.UNCHANGED, diffs.get(0).kind);
        assertTrue(diffs.get(0).headerChanges.isEmpty());
    }

    // -------------------------------------------------------------------------
    // メンバー差分
    // -------------------------------------------------------------------------

    @Test
    public void addedAndRemovedMethods_areDetected() {
        List<ClassDiff> diffs = diff(
                "class A { void oldOne() {} void keep() {} }",
                "class A { void keep() {} void newOne() {} }");
        ClassDiff a = byName(diffs, "A");
        assertEquals(ChangeKind.MODIFIED, a.kind);
        assertEquals(ChangeKind.ADDED,
                memberByLabelPart(a.methods, "newOne").kind);
        assertEquals(ChangeKind.REMOVED,
                memberByLabelPart(a.methods, "oldOne").kind);
        assertEquals(ChangeKind.UNCHANGED,
                memberByLabelPart(a.methods, "keep").kind);
    }

    @Test
    public void returnTypeChange_isModifiedWithBothLabels() {
        List<ClassDiff> diffs = diff(
                "class A { int count() { return 0; } }",
                "class A { long count() { return 0L; } }");
        MemberDiff m = memberByLabelPart(byName(diffs, "A").methods, "count");
        assertEquals(ChangeKind.MODIFIED, m.kind);
        assertTrue(m.oldLabel.contains("int"));
        assertTrue(m.newLabel.contains("long"));
    }

    @Test
    public void overloads_areMatchedByParameterTypes() {
        List<ClassDiff> diffs = diff(
                "class A { void f(int x) {} }",
                "class A { void f(int x) {} void f(String s) {} }");
        ClassDiff a = byName(diffs, "A");
        assertEquals(2, a.methods.size());
        assertEquals(ChangeKind.UNCHANGED,
                memberByLabelPart(a.methods, "(int)").kind);
        assertEquals(ChangeKind.ADDED,
                memberByLabelPart(a.methods, "(String)").kind);
    }

    @Test
    public void fieldTypeAndVisibilityChanges_areModified() {
        List<ClassDiff> diffs = diff(
                "class A { private String name; int size; }",
                "class A { public String name; long size; }");
        ClassDiff a = byName(diffs, "A");
        MemberDiff name = memberByLabelPart(a.fields, "name");
        assertEquals("可視性変更も MODIFIED になるはず", ChangeKind.MODIFIED, name.kind);
        assertEquals(ChangeKind.MODIFIED,
                memberByLabelPart(a.fields, "size").kind);
    }

    @Test
    public void staticFinalMarkers_participateInComparison() {
        List<ClassDiff> diffs = diff(
                "class A { int x; }",
                "class A { static final int x = 1; }");
        MemberDiff x = memberByLabelPart(byName(diffs, "A").fields, "x");
        assertEquals(ChangeKind.MODIFIED, x.kind);
        assertTrue(x.newLabel.contains("{static}"));
        assertTrue(x.newLabel.contains("{final}"));
    }

    @Test
    public void enumConstants_areDiffed() {
        List<ClassDiff> diffs = diff(
                "enum E { RED, BLUE }",
                "enum E { RED, GREEN }");
        ClassDiff e = byName(diffs, "E");
        assertEquals(ChangeKind.MODIFIED, e.kind);
        assertEquals(ChangeKind.ADDED, memberByLabelPart(e.fields, "GREEN").kind);
        assertEquals(ChangeKind.REMOVED, memberByLabelPart(e.fields, "BLUE").kind);
        assertEquals(ChangeKind.UNCHANGED, memberByLabelPart(e.fields, "RED").kind);
    }

    // -------------------------------------------------------------------------
    // クラス宣言ヘッダ差分
    // -------------------------------------------------------------------------

    @Test
    public void superClassChange_isReportedAsHeaderChange() {
        List<ClassDiff> diffs = diff(
                "class A extends Base { }",
                "class A extends NewBase { }");
        ClassDiff a = byName(diffs, "A");
        assertEquals(ChangeKind.MODIFIED, a.kind);
        assertEquals(1, a.headerChanges.size());
        assertTrue(a.headerChanges.get(0).contains("Base"));
        assertTrue(a.headerChanges.get(0).contains("NewBase"));
    }

    @Test
    public void interfaceAdditions_areReportedAsSetChange() {
        List<ClassDiff> diffs = diff(
                "class A implements Runnable { public void run() {} }",
                "class A implements Runnable, Cloneable { public void run() {} }");
        ClassDiff a = byName(diffs, "A");
        assertEquals(ChangeKind.MODIFIED, a.kind);
        assertTrue(a.headerChanges.get(0).contains("+Cloneable"));
    }

    @Test
    public void emptyOldSide_marksEverythingAdded() {
        List<ClassDiff> diffs = diff(null, "class A { }\nclass B { }");
        assertEquals(2, diffs.size());
        for (ClassDiff d : diffs) {
            assertEquals(ChangeKind.ADDED, d.kind);
        }
    }

    @Test
    public void nestedClasses_areComparedByQualifiedName() {
        List<ClassDiff> diffs = diff(
                "class Outer { static class Inner { int a; } }",
                "class Outer { static class Inner { int a; int b; } }");
        ClassDiff inner = byName(diffs, "Inner");
        assertEquals(ChangeKind.MODIFIED, inner.kind);
        assertEquals("Outer.Inner", inner.displayName());
        assertEquals(ChangeKind.UNCHANGED, byName(diffs, "Outer").kind);
    }

    @Test
    public void constructorLabel_hasNoReturnType() {
        List<ClassDiff> diffs = diff(
                "class A { }",
                "class A { A(int x) { } }");
        MemberDiff ctor = memberByLabelPart(byName(diffs, "A").methods, "A(int)");
        assertEquals(ChangeKind.ADDED, ctor.kind);
        assertFalse("コンストラクタに戻り値表記は付かない",
                ctor.newLabel.contains(" : "));
    }

    @Test
    public void genericFieldTypes_appearInLabels() {
        List<ClassDiff> diffs = diff(
                "import java.util.*;\nclass A { List<String> items; }",
                "import java.util.*;\nclass A { Map<String, Integer> items; }");
        MemberDiff items = memberByLabelPart(byName(diffs, "A").fields, "items");
        assertEquals(ChangeKind.MODIFIED, items.kind);
        assertTrue(items.oldLabel.contains("List<String>"));
        assertTrue(items.newLabel.contains("Map<String, Integer>"));
    }

    @Test
    public void classInfoSides_arePreserved() {
        List<ClassDiff> diffs = diff(
                "class A { int x; }",
                "class A { long x; }");
        ClassDiff a = byName(diffs, "A");
        assertNotNull(a.oldClass);
        assertNotNull(a.newClass);
        JavaClassInfo oldC = a.oldClass;
        assertEquals("int", oldC.getFields().get(0).getType());
        assertEquals("long", a.newClass.getFields().get(0).getType());
    }
}
