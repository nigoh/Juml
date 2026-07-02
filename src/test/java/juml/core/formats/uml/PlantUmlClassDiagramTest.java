// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PlantUmlClassDiagram のユニットテスト。
 */
public class PlantUmlClassDiagramTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullClasses() {
        PlantUmlClassDiagram.generate(null);
    }

    @Test
    public void testEmptyClasses() {
        String puml = PlantUmlClassDiagram.generate(java.util.Collections.emptyList());
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("@enduml"));
    }

    @Test
    public void testSimpleClassEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { int a; void m() {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("@startuml"));
        assertTrue(puml, puml.contains("package \"x\""));
        assertTrue(puml, puml.contains("class \"x.Foo\""));
        assertTrue(puml, puml.contains("a: int"));
        assertTrue(puml, puml.contains("m(): void"));
    }

    @Test
    public void testOutputIsDeterministicRegardlessOfInputOrder() {
        // 入力順 (並列ファイル処理由来でぶれる) に依らず、エイリアス ID や出力が
        // 再現可能であることを保証する。FQN 昇順で安定ソートしているため、
        // 入力を逆順にしても生成結果は完全一致する。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Alpha {} class Beta {} class Gamma {} class Delta {}");
        assertTrue("expected multiple classes", infos.size() >= 4);
        java.util.List<JavaClassInfo> reversed = new java.util.ArrayList<>(infos);
        java.util.Collections.reverse(reversed);
        String a = PlantUmlClassDiagram.generate(infos);
        String b = PlantUmlClassDiagram.generate(reversed);
        assertEquals("class diagram must be order-independent", a, b);
    }

    @Test
    public void testDuplicateInterfaceEdgeEmittedOnce() {
        // interfaces リストに重複があっても実装エッジは 1 本だけにする。
        // 実在の interface を解析した上で重複を注入する (パース復旧等で起こり得る状態を再現)。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; interface I {} class A implements I {}");
        for (JavaClassInfo ci : infos) {
            if ("A".equals(ci.getSimpleName())) {
                ci.getInterfaces().add("I");
            }
        }
        String puml = PlantUmlClassDiagram.generate(infos);
        // 凡例 (legend) にも "A <|.. B" の例が含まれるため、エイリアス間の実エッジ行だけを数える
        long edges = puml.lines().filter(l -> l.matches("C\\d+ <\\|\\.\\. C\\d+")).count();
        assertEquals("implements edge should be deduplicated:\n" + puml, 1, edges);
    }

    @Test
    public void testInitializerPseudoMethodNameHtmlEscaped() {
        // <clinit> 擬似メソッド名は class body でタグ開始をチルダエスケープして描画する
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Dao { static { connect(); } static void connect() {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("~<clinit>("));
        assertFalse("unescaped <clinit> must not leak:\n" + puml, puml.contains(" <clinit>("));
    }

    @Test
    public void testNoteModeMergesOverloadedMethodNotes() {
        // NOTE モードで同名オーバーロードの note が 1 つにまとまり、両方の doc を含むこと
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Foo {\n"
                        + "  /** doc for string */ void process(String s) {}\n"
                        + "  /** doc for int */ void process(int n) {}\n"
                        + "}");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        long notes = puml.lines().filter(l -> l.contains("::process")).count();
        assertEquals("overloaded method notes must merge into one:\n" + puml, 1, notes);
        assertTrue(puml, puml.contains("doc for string"));
        assertTrue(puml, puml.contains("doc for int"));
    }

    @Test
    public void testGenericMemberTypesHtmlEscaped() {
        // フィールド/メソッドのジェネリクス型 < > は PlantUML のタグ誤認を避けるため
        // HTML エンティティ化される。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; import java.util.*; class Foo {"
                        + " Map<String, List<Integer>> data;"
                        + " List<String> names(Set<Long> ids) { return null; } }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("data: Map~<String, List~<Integer>>"));
        assertTrue(puml, puml.contains("ids: Set~<Long>"));
        assertTrue(puml, puml.contains("): List~<String>"));
        // エスケープされていない < が member 行に残らないこと
        assertFalse(puml, puml.contains("Map<String"));
    }

    @Test
    public void testInteractiveLinksDisabledByDefault() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { void m() {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertFalse("[[juml://...]] should not appear by default: " + puml,
                puml.contains("[[juml://"));
    }

    @Test
    public void testInteractiveLinksEmitsUrlPerClass() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { void m() {} } class Bar { void n() {} }");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.interactiveLinks = true;
        String puml = PlantUmlClassDiagram.generate(infos, opts);
        assertTrue(puml, puml.contains("[[juml://class/x.Foo]]"));
        assertTrue(puml, puml.contains("[[juml://class/x.Bar]]"));
    }

    @Test
    public void testInterfaceEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface I { void m(); }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("interface \"I\""));
        assertTrue(puml, puml.contains("{abstract}"));
    }

    @Test
    public void testEnumEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "enum E { A; int x() { return 1; } }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("enum \"E\""));
    }

    @Test
    public void testAnnotationEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "@interface A { String value(); }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("annotation \"A\""));
    }

    @Test
    public void testAbstractClassEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "abstract class A { abstract void m(); }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("abstract class \"A\""));
    }

    @Test
    public void testVisibilityMarks() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class C { public int a; private int b; protected int c; int d; }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("+a: int"));
        assertTrue(puml, puml.contains("-b: int"));
        assertTrue(puml, puml.contains("#c: int"));
        assertTrue(puml, puml.contains("~d: int"));
    }

    @Test
    public void testInheritanceEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A extends B implements I1, I2 {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        // 継承の表記
        assertTrue(puml, puml.contains("<|--"));
        // 実装の表記
        assertTrue(puml, puml.contains("<|.."));
    }

    @Test
    public void testKotlinStyleAnnotationNotDoubled() {
        // Kotlin (KotlinLightScanner) はアノテーションを "@Foo" 形式で格納するため、
        // クラス図側で再度 '@' を付けて "@@Foo" にならないこと
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("x");
        c.setSimpleName("Entity");
        JavaFieldInfo f = new JavaFieldInfo();
        f.setName("id");
        f.setType("Long");
        f.setVisibility(Visibility.PUBLIC);
        f.getAnnotations().add("@PrimaryKey");
        c.getFields().add(f);
        String puml = PlantUmlClassDiagram.generate(java.util.Collections.singletonList(c));
        assertTrue(puml, puml.contains("@PrimaryKey"));
        assertFalse("annotation '@' must not be doubled:\n" + puml, puml.contains("@@"));
    }

    @Test
    public void testInterfaceExtendsEmitsInheritanceArrow() {
        // interface Child extends Base は「インタフェース継承」なので実線 <|--。
        // 実装線 <|.. (class implements) と混同しないこと。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface Base {} interface Child extends Base {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.lines().anyMatch(l -> l.matches("C\\d+ <\\|-- C\\d+")));
        assertFalse("interface extends must not be a realization (<|..):\n" + puml,
                puml.lines().anyMatch(l -> l.matches("C\\d+ <\\|\\.\\. C\\d+")));
    }


    @Test
    public void testUsageRelationsEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A { B b; }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("expected --> usage", puml.contains("-->"));
    }

    @Test
    public void testUsageRelationFromMethodParamAndReturn() {
        // フィールドが無くても、メソッドの引数型・戻り値型から利用関係 --> を出す
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class C {} class A { B process(C input) { return null; } }");
        String puml = PlantUmlClassDiagram.generate(infos);
        long edges = puml.lines().filter(l -> l.matches("C\\d+ --> C\\d+")).count();
        // A --> B (戻り値) と A --> C (引数) の 2 本
        assertEquals("expected usage edges from return + param:\n" + puml, 2, edges);
    }

    @Test
    public void testColorCodeRelationsOffKeepsPlainArrows() {
        // 既定 (colorCodeRelations=false) では従来の素の矢印を保つ (CLI/既存図の後方互換)。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface I {} class B {} class A extends B implements I { B b; }");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false; // 凡例の例示行と本物のエッジを混同しないため
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue("plain inheritance expected:\n" + puml, puml.contains("<|--"));
        assertTrue("plain realization expected:\n" + puml, puml.contains("<|.."));
        assertTrue("plain usage expected:\n" + puml, puml.contains("-->"));
        assertFalse("must not color when off:\n" + puml, puml.contains("-[#"));
    }

    @Test
    public void testColorCodeRelationsOnColorsAndDashesByKind() {
        // colorCodeRelations=true で 継承=緑 / 実装=青 / 利用=灰の破線 に色分けされる。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface I {} class B {} class A extends B implements I { B b; }");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        o.colorCodeRelations = true;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        // 継承 (extends): 緑の中空三角
        assertTrue("colored inheritance expected:\n" + puml,
                puml.contains("-[#2E7D32]<|-- "));
        // 実装 (implements): 青の中空三角 (破線)
        assertTrue("colored realization expected:\n" + puml,
                puml.contains("-[#1565C0]<|.. "));
        // 利用 (依存): 灰の破線矢印
        assertTrue("colored dashed usage expected:\n" + puml,
                puml.contains("-[#9E9E9E,dashed]-> "));
        // 素の矢印 (無着色) はエッジ行に残らない
        assertFalse("no plain usage arrow when colored:\n" + puml,
                puml.lines().anyMatch(l -> l.matches("C\\d+ --> C\\d+")));
    }

    @Test
    public void testColorCodeRelationsInterfaceExtendsUsesInheritColor() {
        // interface 同士の extends は実装ではなく継承色 (緑) になる。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface Base {} interface Child extends Base {}");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        o.colorCodeRelations = true;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue("interface extends should use inherit color:\n" + puml,
                puml.contains("-[#2E7D32]<|-- "));
        assertFalse("interface extends must not be realization color:\n" + puml,
                puml.contains("-[#1565C0]<|.. "));
    }

    @Test
    public void testFocusModeAccentsFocusAndDimsNonNeighbors() {
        // Focus は Near を使う / Far は Near を使う (Focus には無関係)。
        // 焦点=Focus のとき、Focus=アクセント色、Far=淡色、Far→Near エッジ=淡色破線。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Near {} class Focus { Near n; } class Far { Near n; }");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        o.focusClass = "x.Focus";
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue("focus node should get accent bg:\n" + puml,
                puml.contains(PlantUmlClassFocus.FOCUS_BG));
        assertTrue("non-neighbor (Far) should be dimmed:\n" + puml,
                puml.contains(PlantUmlClassFocus.DIM_BG));
        assertTrue("non-focus edge (Far->Near) should be dimmed:\n" + puml,
                puml.contains("-[" + PlantUmlClassFocus.DIM_EDGE + ",dashed]-> "));
        // 焦点に接するエッジ (Focus->Near) は淡色化せず通常の素矢印のまま残る
        assertTrue("focus edge stays plain:\n" + puml,
                puml.lines().anyMatch(l -> l.matches("C\\d+ --> C\\d+")));
    }

    @Test
    public void testFocusModeAcceptsSimpleName() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Near {} class Focus { Near n; }");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        o.focusClass = "Focus"; // 単純名でも解決される
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue("simple-name focus should resolve and accent:\n" + puml,
                puml.contains(PlantUmlClassFocus.FOCUS_BG));
    }

    @Test
    public void testFocusModeUnresolvedDisablesDimming() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class A {} class B { A a; }");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        o.focusClass = "x.DoesNotExist";
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse("unresolved focus must not dim anything:\n" + puml,
                puml.contains(PlantUmlClassFocus.DIM_BG));
        assertFalse("unresolved focus must not accent anything:\n" + puml,
                puml.contains(PlantUmlClassFocus.FOCUS_BG));
    }

    @Test
    public void testColorCodeRelationsLegendShowsColorKey() {
        // 色分け時、凡例に実際の線色サンプル (色 ↔ 意味) が出る。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "interface I {} class B {} class A extends B implements I { B b; }");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.colorCodeRelations = true;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue("legend should carry inherit color key:\n" + puml,
                puml.contains("<color:" + PlantUmlClassRelations.INHERIT_COLOR + ">"));
        assertTrue("legend should carry usage color key:\n" + puml,
                puml.contains("<color:" + PlantUmlClassRelations.USAGE_COLOR + ">"));
    }

    @Test
    public void testUsageRelationsIgnorePrimitives() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class A { int a; String s; long l; }");
        String puml = PlantUmlClassDiagram.generate(infos);
        // ユーザ定義型が無いので --> が無いことを確認
        assertFalse(puml, puml.contains("-->"));
    }

    @Test
    public void testAaosCategoryStereotype() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("android.car.audio");
        c.setSimpleName("CarAudioManager");
        c.setKind(JavaClassInfo.Kind.CLASS);
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<CarManager>>"));
    }

    @Test
    public void testOptionDisableVisibility() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showVisibility = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class C { public int a; }");
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse(puml, puml.contains("+a"));
        assertTrue(puml, puml.contains("a: int"));
    }

    @Test
    public void testOptionDisableInheritance() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showInheritance = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A extends B {}");
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse(puml, puml.contains("<|--"));
    }

    @Test
    public void testOptionDisableUsage() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showUsageRelations = false;
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class B {} class A { B b; }");
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse(puml, puml.contains("-->"));
    }

    @Test
    public void testOptionTitle() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.title = "MyProject";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class C {}"), o);
        assertTrue(puml, puml.contains("title MyProject"));
    }

    @Test
    public void testStaticMember() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class C { public static final int N = 1; public static void m() {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("{static}"));
    }

    @Test
    public void testConstructorEmit() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Foo { public Foo(int x) {} }");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("Foo(x: int)"));
        // コンストラクタは戻り型を表示しない
        assertFalse(puml, puml.contains("Foo(x: int): "));
    }

    @Test
    public void testGroupByPackage() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class A {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("package \"x\""));
        assertTrue(puml, puml.contains("class \"x.A\""));
    }

    @Test
    public void testAidlInterfaceMarked() {
        String aidl = "package c; interface ICar { int v(); }";
        List<JavaClassInfo> infos = AidlParser.parse(aidl);
        for (JavaClassInfo c : infos) {
            c.setAaosCategory(AaosPattern.categorize(c));
        }
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue(puml, puml.contains("<<AIDL>>"));
        assertTrue(puml, puml.contains("interface \"c.ICar\""));
    }

    // --- 凡例 ---

    @Test
    public void testLegendIncludedByDefault() {
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A {}"));
        assertTrue(puml, puml.contains("legend top left"));
        assertTrue(puml, puml.contains("endlegend"));
    }

    @Test
    public void testLegendDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.includeLegend = false;
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A {}"), o);
        assertFalse(puml, puml.contains("legend top left"));
    }

    @Test
    public void testLegendShowsVisibilitySection() {
        // 既定 (visibilityIcons=true) ではアイコン凡例 (色付き glyph + 説明) を出す
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A { public int x; }"));
        assertTrue(puml, puml.contains("== 可視性 =="));
        assertTrue(puml, puml.contains("public (公開)"));
        // 記号テキストの凡例ではない
        assertFalse(puml, puml.contains("+ public\n"));
    }

    @Test
    public void testVisibilityIconsEnabledByDefault() {
        // 既定でカラーアイコン (classAttributeIconSize > 0) を有効化する
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A { public int x; }"));
        assertTrue(puml, puml.contains("skinparam classAttributeIconSize 12"));
        assertFalse(puml, puml.contains("skinparam classAttributeIconSize 0"));
        // メンバー行の可視性マークはソース上は従来どおり残る (アイコン化はレンダリング側)
        assertTrue(puml, puml.contains("+x: int"));
    }

    @Test
    public void testVisibilityIconsDisabledFallsBackToSymbols() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.visibilityIcons = false;
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A { public int x; }"), o);
        assertTrue(puml, puml.contains("skinparam classAttributeIconSize 0"));
        // 記号テキストの凡例に戻る
        assertTrue(puml, puml.contains("+ public"));
    }

    @Test
    public void testLegendShowsStereotypeOnlyWhenUsed() {
        // AAOS パターンを使わないクラスでは <<CarManager>> が凡例に出ない
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class Foo { void m() {} }"));
        assertFalse(puml, puml.contains("<<CarManager>>"));
        // AAOS Manager パターンのクラスを含む場合は出る
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("android.car.audio");
        c.setSimpleName("CarAudioManager");
        c.setKind(JavaClassInfo.Kind.CLASS);
        String puml2 = PlantUmlClassDiagram.generate(java.util.Arrays.asList(c));
        assertTrue(puml2, puml2.contains("<<CarManager>>"));
    }

    @Test
    public void testLegendOmitsUsageSectionWhenNoRelations() {
        // すべて primitive 型 → --> セクションは凡例にも出ない
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A { int a; String s; }"));
        assertFalse(puml, puml.contains("A --> B"));
    }

    @Test
    public void testLegendIncludesInheritanceWhenPresent() {
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class B {} class A extends B {}"));
        assertTrue(puml, puml.contains("A <|-- B"));
    }

    @Test
    public void testAndroidComponentStereotype() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.x");
        c.setSimpleName("MainActivity");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setAndroidComponentType("Activity");
        String puml = PlantUmlClassDiagram.generate(java.util.Arrays.asList(c));
        assertTrue(puml, puml.contains("<<Activity>>"));
        // 凡例セクションに「Android コンポーネント」も出る
        assertTrue(puml, puml.contains("== Android コンポーネント =="));
    }

    @Test
    public void testNoAndroidSectionWhenAbsent() {
        // androidComponentType が一切無いクラスでは Android セクションは凡例にも出ない
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class A {}"));
        assertFalse(puml, puml.contains("== Android コンポーネント =="));
    }

    // --- コメント表示 ---

    @Test
    public void testJavadocOnClassEmitsInlineByDefault() {
        String src = "/** ユーザを表すクラス */\nclass User { int id; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // INLINE モードがデフォルト。デフォルト色 (#008800) でラップされる
        assertTrue(puml, puml.contains(".. <color:#008800>ユーザを表すクラス</color> .."));
    }

    @Test
    public void testJavadocOnFieldAndMethodEmitsInline() {
        String src = "class C {\n"
                + "  /** ユーザID */\n"
                + "  int id;\n"
                + "  /** 表示名を返す */\n"
                + "  String name() { return null; }\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains(".. <color:#008800>ユーザID</color> .."));
        assertTrue(puml, puml.contains(".. <color:#008800>表示名を返す</color> .."));
    }

    @Test
    public void testLineCommentMergedAndAttached() {
        String src = "class C {\n"
                + "  // 1 行目\n"
                + "  // 2 行目\n"
                + "  int x;\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // INLINE では先頭行のみ出す
        assertTrue(puml, puml.contains(".. <color:#008800>1 行目</color> .."));
    }

    @Test
    public void testCommentDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showComments = false;
        String src = "/** doc */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertFalse(puml, puml.contains("doc"));
    }

    @Test
    public void testCommentNoteStyle() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        String src = "/** クラスの説明 */\nclass C {\n"
                + "  /** field の説明 */ int x;\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        // INLINE 形式 (..) は出ない
        assertFalse(puml, puml.contains(".. クラスの説明 .."));
        // クラスレベル note
        assertTrue(puml, puml.contains("note top of"));
        assertTrue(puml, puml.contains("クラスの説明"));
        // メンバーレベル note
        assertTrue(puml, puml.contains("note right of"));
        assertTrue(puml, puml.contains("::x"));
        assertTrue(puml, puml.contains("field の説明"));
        assertTrue(puml, puml.contains("end note"));
    }

    @Test
    public void testCommentJavadocStripsLeadingAsterisksAndTags() {
        String src = "/**\n"
                + " * 概要 1 行目。\n"
                + " * 詳細 2 行目。\n"
                + " * @param x ignored\n"
                + " */\n"
                + "class C { void m() {} }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // INLINE モードでは先頭 1 行のみ
        assertTrue(puml, puml.contains(".. <color:#008800>概要 1 行目。</color> .."));
        // @param 行は表示されない
        assertFalse(puml, puml.contains("@param"));
    }

    @Test
    public void testCommentInlineLengthLimited() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentMaxLength = 10;
        String src = "/** 非常に非常に非常に長いコメントです */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue("expected truncated marker '…'", puml.contains("…"));
    }

    @Test
    public void testCommentNotAttachedAcrossDecls() {
        // /** doc */ は foo の直前にあり、bar の直前にはない
        String src = "class C {\n"
                + "  /** doc */\n"
                + "  int foo;\n"
                + "  int bar;\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // foo にだけ付く想定。bar に "doc" が出ないこと
        int count = puml.split("\\.\\. <color:#008800>doc</color> \\.\\.", -1).length - 1;
        assertEquals("doc コメントが 1 箇所のみ出ること", 1, count);
    }

    @Test
    public void testInlineCommentWrappedInColorTag() {
        // デフォルト色 (#008800) がインラインコメントを <color:...> で囲む
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("<color:#008800>クラス説明</color>"));
    }

    @Test
    public void testInlineCommentColorCustomizable() {
        // commentColor を任意の値に差し替えられる
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentColor = "#FF00AA";
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue(puml, puml.contains(".. <color:#FF00AA>クラス説明</color> .."));
    }

    @Test
    public void testInlineCommentColorDisabledWhenEmpty() {
        // commentColor が空なら従来通り色タグなしで出力する
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentColor = "";
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue(puml, puml.contains(".. クラス説明 .."));
        // コメント本体が色タグで囲まれていないことを確認する
        // (凡例の可視性アイコン glyph は別途 <color:...> を使うため、コメント文字列を直接見る)。
        assertFalse(puml, puml.contains(">クラス説明"));
    }

    @Test
    public void testNoteStyleEmitsSkinparamForCommentColor() {
        // NOTE モード時はコメント色を skinparam noteBorderColor / noteFontColor に流す
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        String src = "/** クラス説明 */ class C {}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertTrue(puml, puml.contains("skinparam noteBorderColor #008800"));
        assertTrue(puml, puml.contains("skinparam noteFontColor #008800"));
    }

    // --- アノテーション表示 ---

    @Test
    public void testAnnotationsOnMembersEmittedByDefault() {
        String src = "class C {\n"
                + "  @Nullable\n"
                + "  String name;\n"
                + "  @Deprecated\n"
                + "  void m() {}\n"
                + "}";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("@Nullable"));
        assertTrue(puml, puml.contains("@Deprecated"));
    }

    @Test
    public void testAnnotationsHiddenByDefaultForOverride() {
        String src = "class C { @Override public String toString() { return null; } }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // @Override は既定で非表示
        assertFalse(puml, puml.contains("@Override"));
    }

    @Test
    public void testAnnotationsDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showAnnotations = false;
        String src = "class C { @Nullable String name; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertFalse(puml, puml.contains("@Nullable"));
    }

    // --- enum 定数 ---

    @Test
    public void testEnumConstantsEmittedByDefault() {
        String src = "enum Color { RED, GREEN, BLUE }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("RED"));
        assertTrue(puml, puml.contains("GREEN"));
        assertTrue(puml, puml.contains("BLUE"));
    }

    @Test
    public void testEnumConstantsWithMembersSeparated() {
        String src = "enum E { A, B; int x() { return 1; } }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("A"));
        assertTrue(puml, puml.contains("B"));
        // PlantUML の区切り '--' が定数とメンバーの間に入る
        assertTrue(puml, puml.contains("--"));
        assertTrue(puml, puml.contains("x(): int"));
    }

    @Test
    public void testEnumConstantArgsEmitted() {
        String src = "enum Color { RED(255, 0, 0), BLACK; Color(int... rgb) {} }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // 引数つき定数は RED(255, 0, 0) と併記される
        assertTrue(puml, puml.contains("RED(255, 0, 0)"));
        // 引数なし定数は名前のみ
        assertTrue(puml, puml.contains("\n  BLACK\n"));
    }

    @Test
    public void testThrowsRenderedWhenEnabled() {
        String src = "class A { void f() throws java.io.IOException, IllegalStateException {} }";
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        // 既定 (showThrows=false) では throws を出さない
        String off = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src));
        assertFalse(off, off.contains("throws"));
        // 有効化すると throws 例外がシグネチャに併記される
        o.showThrows = true;
        String on = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src), o);
        assertTrue(on, on.contains("throws java.io.IOException, IllegalStateException"));
    }

    @Test
    public void testMethodTypeParametersInSignature() {
        String puml = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(
                "class U { <T> T id(T x) { return x; } }"));
        // メソッドの <T> 宣言が名前の前にチルダエスケープされて併記される
        assertTrue(puml, puml.contains("~<T> id(x: T): T"));
    }

    @Test
    public void testGenericTypeParametersInHeader() {
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("package p; class Box<T extends Number> {}"));
        // 型パラメータがチルダエスケープされてクラス名に併記される
        assertTrue(puml, puml.contains("p.Box~<T extends Number>"));
    }

    @Test
    public void testAnnotationDefaultValueEmitted() {
        String src = "@interface Cfg { int timeout() default 30; String name(); }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // default 値が " = 30" として併記される
        assertTrue(puml, puml.contains("timeout(): int = 30"));
        // default のない属性に " = " は付かない
        assertFalse(puml, puml.contains("name(): String ="));
    }

    @Test
    public void testEnumConstantsDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showEnumConstants = false;
        String src = "enum Color { RED, GREEN }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        // クラス本体内に RED が出ないこと (class エイリアスとは別に)
        assertFalse(puml, puml.contains("\n  RED\n"));
        assertFalse(puml, puml.contains("\n  GREEN\n"));
    }

    // --- ネスト含有 ---

    @Test
    public void testNestedContainmentEdge() throws java.io.IOException {
        String src = "package p; class Outer { static class Inner {} }";
        // 既定 (false) では含有エッジを出さない
        String off = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src));
        assertFalse(off, off.contains("+--"));
        // 有効化すると Outer +-- Inner の含有エッジを出す
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showNestedContainment = true;
        String on = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src), o);
        assertTrue(on, on.contains("+--"));
        // PlantUML が構文エラー画像を返さないこと (+-- が妥当な記法であること) を確認
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        PlantUmlRenderer.setRendererImplForTest(null);
        PlantUmlRenderer.renderSvg(on, bos);
        String svg = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(svg, svg.contains("Syntax Error"));
    }

    // --- 定数値 ---

    @Test
    public void testConstantValueEmittedByDefault() {
        String src = "class C { public static final int MAX = 100; private int n = 5; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        // static final 定数は値を併記
        assertTrue(puml, puml.contains("MAX: int = 100"));
        // 非 static/非 final の通常フィールドには値を付けない
        assertFalse(puml, puml.contains("n: int = 5"));
    }

    @Test
    public void testConstantValueDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showConstantValues = false;
        String src = "class C { public static final int MAX = 100; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertFalse(puml, puml.contains("= 100"));
    }

    // --- final マーカー ---

    @Test
    public void testFinalMarkerEmittedByDefault() {
        String src = "class C { public final int N = 1; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src));
        assertTrue(puml, puml.contains("{final}"));
    }

    @Test
    public void testFinalMarkerDisabled() {
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showFinal = false;
        String src = "class C { public final int N = 1; }";
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract(src), o);
        assertFalse(puml, puml.contains("{final}"));
    }

    @Test
    public void testJetpackStereotypeOffByDefault() {
        // ソース直接抽出経路 (JavaStructureExtractor.extract) では Jetpack 分類が走らないので、
        // 明示的にステレオタイプを設定して --jetpack の Options ゲートだけを検証する。
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.x");
        c.setSimpleName("Home");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.getJetpackStereotypes().add("Fragment");
        String puml = PlantUmlClassDiagram.generate(java.util.Collections.singletonList(c));
        assertFalse(puml, puml.contains("<<Fragment>>"));
        assertFalse(puml, puml.contains("Jetpack ステレオタイプ"));
    }

    @Test
    public void testJetpackStereotypeWhenEnabled() {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("com.x");
        c.setSimpleName("Home");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.getJetpackStereotypes().add("Fragment");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.jetpack.enabled = true;
        String puml = PlantUmlClassDiagram.generate(
                java.util.Collections.singletonList(c), o);
        assertTrue(puml, puml.contains("<<Fragment>>"));
        assertTrue(puml, puml.contains("Jetpack ステレオタイプ"));
    }

    // ---- Class diagram readability work (PR2) ----

    @Test
    public void testExcludeExternalLibrariesFiltersByOrigin() {
        JavaClassInfo internal = new JavaClassInfo();
        internal.setPackageName("com.example");
        internal.setSimpleName("Foo");
        internal.setKind(JavaClassInfo.Kind.CLASS);
        internal.getModifiers().add("public");

        JavaClassInfo external = new JavaClassInfo();
        external.setPackageName("com.example.dep");
        external.setSimpleName("DepClass");
        external.setKind(JavaClassInfo.Kind.CLASS);
        external.setOrigin(JavaClassInfo.Origin.EXTERNAL_JAR);

        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.excludeExternalLibraries = true;
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(internal, external), o);
        assertTrue(puml, puml.contains("Foo"));
        assertFalse("external JAR class should be filtered out",
                puml.contains("DepClass"));
    }

    @Test
    public void testExcludeExternalLibrariesFiltersByPackagePrefix() {
        JavaClassInfo project = new JavaClassInfo();
        project.setPackageName("com.example");
        project.setSimpleName("App");
        project.setKind(JavaClassInfo.Kind.CLASS);

        JavaClassInfo androidView = new JavaClassInfo();
        androidView.setPackageName("android.view");
        androidView.setSimpleName("View");
        androidView.setKind(JavaClassInfo.Kind.CLASS);

        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.excludeExternalLibraries = true;
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(project, androidView), o);
        assertTrue(puml, puml.contains("App"));
        assertFalse("android.view.View should be filtered out by prefix",
                puml.contains("android.view"));
    }

    @Test
    public void testExcludeExternalDisabledByDefault() {
        JavaClassInfo external = new JavaClassInfo();
        external.setPackageName("java.util");
        external.setSimpleName("HashMap");
        external.setKind(JavaClassInfo.Kind.CLASS);
        external.setOrigin(JavaClassInfo.Origin.EXTERNAL_JAR);

        // default Options: excludeExternalLibraries=false → still emitted
        String puml = PlantUmlClassDiagram.generate(
                java.util.Collections.singletonList(external));
        assertTrue("external class should be shown when filter is off",
                puml.contains("HashMap"));
    }

    @Test
    public void testShowImplementationsToggleSeparatesExtendsAndImplements() {
        JavaClassInfo parent = new JavaClassInfo();
        parent.setPackageName("com.x");
        parent.setSimpleName("Base");
        parent.setKind(JavaClassInfo.Kind.CLASS);

        JavaClassInfo iface = new JavaClassInfo();
        iface.setPackageName("com.x");
        iface.setSimpleName("Runnable");
        iface.setKind(JavaClassInfo.Kind.INTERFACE);

        JavaClassInfo child = new JavaClassInfo();
        child.setPackageName("com.x");
        child.setSimpleName("Child");
        child.setKind(JavaClassInfo.Kind.CLASS);
        child.setSuperClass("com.x.Base");
        child.getInterfaces().add("com.x.Runnable");

        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showInheritance = true;
        o.showImplementations = false;
        o.includeLegend = false; // legend に <|.. 説明があるので無効化
        String puml = PlantUmlClassDiagram.generate(
                Arrays.asList(parent, iface, child), o);
        assertTrue("extends should still be emitted", puml.contains("<|--"));
        assertFalse("implements should be suppressed", puml.contains("<|.."));
    }

    @Test
    public void testShowInheritanceOffStillEmitsImplements() {
        JavaClassInfo parent = new JavaClassInfo();
        parent.setPackageName("com.x");
        parent.setSimpleName("Base");
        parent.setKind(JavaClassInfo.Kind.CLASS);

        JavaClassInfo iface = new JavaClassInfo();
        iface.setPackageName("com.x");
        iface.setSimpleName("Runnable");
        iface.setKind(JavaClassInfo.Kind.INTERFACE);

        JavaClassInfo child = new JavaClassInfo();
        child.setPackageName("com.x");
        child.setSimpleName("Child");
        child.setKind(JavaClassInfo.Kind.CLASS);
        child.setSuperClass("com.x.Base");
        child.getInterfaces().add("com.x.Runnable");

        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showInheritance = false;
        o.showImplementations = true;
        o.includeLegend = false; // legend に <|-- 説明があるので無効化
        String puml = PlantUmlClassDiagram.generate(
                Arrays.asList(parent, iface, child), o);
        assertFalse("extends should be suppressed", puml.contains("<|--"));
        assertTrue("implements should still be emitted", puml.contains("<|.."));
    }

    @Test
    public void testPublicOnlyFiltersNonPublicClasses() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; public class Pub {} class Pkg {} ");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.publicOnly = true;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue(puml, puml.contains("Pub"));
        assertFalse("package-private class should be hidden", puml.contains("Pkg"));
    }

    @Test
    public void testPublicOnlySuppressesPrivateMembers() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; public class Holder {"
                        + " public int a; private int b; protected int c; int d;"
                        + " public void m() {} private void n() {}"
                        + " }");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.publicOnly = true;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertTrue(puml, puml.contains("a: int"));
        assertFalse("private field b should be hidden",
                puml.contains("b: int"));
        assertFalse("protected field c should be hidden",
                puml.contains("c: int"));
        assertFalse("package-private field d should be hidden",
                puml.contains("d: int"));
        assertTrue(puml.contains("m("));
        assertFalse("private method n should be hidden", puml.contains("n("));
    }

    @Test
    public void testPresetMinimalSuppressesFieldsAndUsage() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; public class Foo { Bar b; public void m() {} }"
                        + "public class Bar {}");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        juml.app.uml.DiagramPreset.MINIMAL.applyTo(o);
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse("fields should not appear in MINIMAL", puml.contains("b: Bar"));
        assertFalse("usage relations should not appear in MINIMAL",
                puml.contains("-->"));
    }

    @Test
    public void testInlineFunctionFromFieldInitializerEmitted() {
        // フィールド初期化子で関数を変数として設定しているケースが
        // クラス図にインライン関数として描画されること
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Foo {\n"
                        + "  private OnClickListener listener = new OnClickListener() {\n"
                        + "    public void onClick(View v) { doX(); }\n"
                        + "  };\n"
                        + "}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("listener separator should appear: " + puml,
                puml.contains(".. listener: OnClickListener .."));
        assertTrue("onClick should appear under listener: " + puml,
                puml.contains("onClick("));
    }

    @Test
    public void testInlineFunctionFromConstructorAssignmentEmitted() {
        // コンストラクタ内で this.field = ... と代入したラムダが
        // クラス図に描画されること
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Foo {\n"
                        + "  private Runnable r;\n"
                        + "  Foo() { this.r = () -> doX(); }\n"
                        + "}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("r separator should appear: " + puml,
                puml.contains(".. r: Runnable .."));
        assertTrue("run should appear under r: " + puml,
                puml.contains("run("));
    }

    @Test
    public void testInlineFunctionsCanBeSuppressed() {
        // showInlineFunctions=false で抑制できること
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "class Foo {\n"
                        + "  private Runnable r = () -> doX();\n"
                        + "}");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.showInlineFunctions = false;
        String puml = PlantUmlClassDiagram.generate(infos, o);
        assertFalse("separator should not appear when suppressed: " + puml,
                puml.contains(".. r: Runnable .."));
    }

    // ---- record / sealed support ----

    @Test
    public void testRecordGetsRecordStereotype() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.example; public record Point(int x, int y) {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("record should have <<record>> stereotype: " + puml,
                puml.contains("<<record>>"));
        assertTrue("record should still use 'class' keyword: " + puml,
                puml.contains("class \"com.example.Point\""));
    }

    @Test
    public void testRecordLegendEntryAppearsWhenPresent() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.example; public record Point(int x, int y) {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("legend should describe record: " + puml,
                puml.contains("record 宣言"));
    }

    @Test
    public void testSealedClassGetsSealedStereotype() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.example; public sealed class Shape permits Circle {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("sealed class should have <<sealed>> stereotype: " + puml,
                puml.contains("<<sealed>>"));
    }

    @Test
    public void testSealedPermitsDoNotEmitInvertedArrow() {
        // sealed の permits は逆向きの実装矢印 (Circle <|.. Shape) を出さない。
        // 正しい継承矢印は子クラスの extends から出る (Shape <|-- Circle)。
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.example;"
                        + " sealed class Shape permits Circle {}"
                        + " final class Circle extends Shape {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        // 実装矢印 (<|..) は出ない
        assertFalse("sealed permits must not produce an implements arrow:\n" + puml,
                puml.contains("<|.. "));
        // 子の extends による継承矢印は出る
        assertTrue("subclass extends should still produce inheritance:\n" + puml,
                puml.contains("<|-- "));
    }

    @Test
    public void testSealedLegendEntryAppearsWhenPresent() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.example; public sealed class Shape permits Circle {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertTrue("legend should describe sealed: " + puml,
                puml.contains("permits で継承先を限定"));
    }

    @Test
    public void testNonRecordClassHasNoRecordStereotype() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package com.example; public class Foo {}");
        String puml = PlantUmlClassDiagram.generate(infos);
        assertFalse("ordinary class should not have <<record>>: " + puml,
                puml.contains("<<record>>"));
    }

    @Test
    public void testInteractiveLinksEmitsMethodLinks() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { void doSomething() {} void helper() {} }");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.interactiveLinks = true;
        opts.showMethods = true;
        String puml = PlantUmlClassDiagram.generate(infos, opts);
        assertTrue("method link for doSomething: " + puml,
                puml.contains("[[juml://method/x.Foo#doSomething ▶]]"));
        assertTrue("method link for helper: " + puml,
                puml.contains("[[juml://method/x.Foo#helper ▶]]"));
    }

    @Test
    public void testInteractiveLinksNoMethodLinksWhenMethodsHidden() {
        List<JavaClassInfo> infos = JavaStructureExtractor.extract(
                "package x; class Foo { void doSomething() {} }");
        PlantUmlClassDiagram.Options opts = new PlantUmlClassDiagram.Options();
        opts.interactiveLinks = true;
        opts.showMethods = false;
        String puml = PlantUmlClassDiagram.generate(infos, opts);
        assertFalse("no method links when showMethods=false: " + puml,
                puml.contains("juml://method/"));
    }

    // ---- [High] title エスケープ修正 テスト ----

    @Test
    public void testTitleWithSpecialCharsEscaped() {
        // title に < が含まれる場合、PlantUML がタグと誤認しないよう
        // ~< にチルダエスケープされること (> & は生のままで安全)。
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.title = "A<B>&C";
        o.includeLegend = false;
        String puml = PlantUmlClassDiagram.generate(
                JavaStructureExtractor.extract("class C {}"), o);
        assertTrue("escaped title expected: " + puml,
                puml.contains("title A~<B>&C"));
        assertFalse("unescaped < must not appear in title: " + puml,
                puml.contains("title A<B>"));
    }

    // ---- [Medium] NOTE モード での <init> 等の擬似名エスケープ テスト ----

    @Test
    public void testNoteStyleFieldNameWithAngleBracketEscaped() {
        // NOTE モードで、フィールド名に < > が含まれる場合に HTML エスケープされること。
        // 実際の Java ソースにこのような名前は存在しないが、解析器が
        // <clinit>/<init> 等を擬似フィールドとして登録するケースへの防衛。
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        o.showFields = true;
        o.showComments = true;
        o.includeLegend = false;

        // JavaClassInfo を手動構築してフィールド名に < > を注入
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("pkg");
        c.setSimpleName("Cls");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setDetailed(true);
        JavaFieldInfo f = new JavaFieldInfo();
        f.setName("<clinit>");
        f.setType("void");
        f.setComment("クラス初期化子");
        c.getFields().add(f);

        String puml = PlantUmlClassDiagram.generate(java.util.Collections.singletonList(c), o);
        // 生の < は PlantUML HTML タグとして扱われるため出力されていてはならない
        assertFalse("raw < in field note must not appear: " + puml,
                puml.contains("::<clinit>"));
        // HTML エスケープ済み形式で出力されること
        assertTrue("escaped field name must appear: " + puml,
                puml.contains("::" + PlantUmlCommentFormatter.escapeText("<clinit>")));
    }

    @Test
    public void testNoteStyleMethodNameWithAngleBracketEscaped() {
        // NOTE モードで、メソッド名に < > が含まれる場合に HTML エスケープされること。
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
        o.showMethods = true;
        o.showComments = true;
        o.includeLegend = false;

        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName("pkg");
        c.setSimpleName("Cls");
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setDetailed(true);
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("<init>");
        m.setReturnType("void");
        m.setComment("コンストラクタ");
        c.getMethods().add(m);

        String puml = PlantUmlClassDiagram.generate(java.util.Collections.singletonList(c), o);
        assertFalse("raw < in method note must not appear: " + puml,
                puml.contains("::<init>"));
        assertTrue("escaped method name must appear: " + puml,
                puml.contains("::" + PlantUmlCommentFormatter.escapeText("<init>")));
    }

    // ---- 定数グルーピング (groupConstants) ----

    @Test
    public void testGroupConstantsEnabledSeparatesWithDotDot() {
        // groupConstants=true (既定) のとき static final 定数が先に出て、
        // 通常フィールドとの間に ".." セパレータが入ることを確認する。
        String src = "class C { "
                + "public static final int MAX = 100; "
                + "private int x; "
                + "}";
        String puml = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src));
        // 両グループが揃っていれば ".." セパレータが出る
        assertTrue("separator (..) should appear between constant and plain field: " + puml,
                puml.contains(".."));
        // MAX が x より先に現れること
        int maxIdx = puml.indexOf("MAX");
        int xIdx = puml.indexOf("x:");
        assertTrue("MAX constant should appear before separator: " + puml, maxIdx >= 0);
        assertTrue("x field should appear after separator: " + puml, xIdx >= 0);
        assertTrue("MAX should precede x in output: " + puml, maxIdx < xIdx);
    }

    @Test
    public void testGroupConstantsDisabledPreservesDeclarationOrder() {
        // groupConstants=false のとき、フィールドが宣言順で出て ".." セパレータが出ないことを確認する。
        // x を先に宣言し MAX を後に宣言して、フィールド順が保たれることを検証する。
        String src = "class C { private int x; public static final int MAX = 100; }";
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.groupConstants = false;
        String puml = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src), o);
        // groupConstants=false ではセパレータが出ない
        // ("  .." パターンはクラス本体の区切り線のみ; 両グループ揃っていれば出るはずが出ない)
        assertFalse("separator should NOT appear when groupConstants=false: " + puml,
                puml.contains("  .."));
        // 宣言順: x が MAX より先に出ること
        int xIdx = puml.indexOf("x:");
        int maxIdx = puml.indexOf("MAX:");
        assertTrue("x should appear before MAX when groupConstants=false: " + puml,
                xIdx >= 0 && maxIdx >= 0 && xIdx < maxIdx);
    }

    @Test
    public void testGroupConstantsSeparatorAbsentWhenNoPlainFields() {
        // static final 定数のみでプレーンフィールドが無い場合、セパレータを出さないことを確認する。
        String src = "class C { public static final int A = 1; public static final int B = 2; }";
        String puml = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src));
        // "  .." はフィールドグループ間の区切り。両グループが揃っていないので出ない。
        assertFalse("separator should NOT appear when there are only constants: " + puml,
                puml.contains("  .."));
    }

    // ---- 定数値の長文切り詰め廃止・空白正規化 ----

    @Test
    public void testConstantValueLongStringNotTruncated() {
        // 40 文字超の static final String 定数値が "..." なしで全文出力されることを確認する。
        // 旧実装では 40 文字で切り詰めていたが、新実装では全文表示する。
        String longValue = "A".repeat(60);
        String src = "class C { public static final String MSG = \""
                + longValue + "\"; }";
        String puml = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src));
        // 60 個の 'A' が全文含まれること
        assertTrue("long constant value should appear in full: " + puml,
                puml.contains(longValue));
        // 旧実装の切り詰め記号 "..." が含まれないこと
        assertFalse("long constant value should NOT be truncated with ...: " + puml,
                puml.contains("..."));
    }

    @Test
    public void testConstantValueMultiWhitespaceCollapsed() {
        // 初期化値の連続空白が単一スペースに正規化されることを確認する。
        // "hello   world" (3 スペース) → "hello world" (1 スペース)
        String src = "class C { public static final String S = \"hello   world\"; }";
        String puml = PlantUmlClassDiagram.generate(JavaStructureExtractor.extract(src));
        // 正規化後の 1 スペース版が含まれること
        assertTrue("whitespace should be collapsed to single space: " + puml,
                puml.contains("hello world"));
        // 3 スペースのままでは含まれないこと
        assertFalse("multi-space should NOT remain after normalization: " + puml,
                puml.contains("hello   world"));
    }
}
