// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * Phase 2.1 で導入した Android API 可視性・AIDL binder impl ステレオタイプの
 * クラス図への反映を検証する。{@code PlantUmlClassDiagramTest} はファイル
 * サイズ上限の都合で分割しており、本テストは {@code markAaosCategories}
 * 経由で {@code AaosPattern.apiVisibilityStereotype} /
 * {@code isAidlBinderImpl} が UML 出力に出ることのみを保証する。
 */
public class PlantUmlClassDiagramAaosStereotypeTest {

    private static JavaClassInfo make(String pkg, String name) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        return c;
    }

    @Test
    public void testSystemApiStereotypeAppears() {
        JavaClassInfo c = make("com.x", "Foo");
        c.getAnnotations().add("SystemApi");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<SystemApi>>"));
    }

    @Test
    public void testHiddenStereotypeFromJavadoc() {
        JavaClassInfo c = make("com.x", "Foo");
        c.setComment("/** @hide */");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<Hidden>>"));
    }

    @Test
    public void testBinderStereotypeAppearsForStubSuperclass() {
        JavaClassInfo c = make("com.android.car", "CarFooService");
        c.setSuperClass("ICarFoo.Stub");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<binder>>"));
    }

    @Test
    public void testStereotypesStackWithCarServiceCategory() {
        // android.car パッケージ + 命名 "CarFooService" + Stub 継承 + @SystemApi
        // を全て満たすクラスは <<CarService>><<SystemApi>><<binder>> をまとめて持つ
        JavaClassInfo c = make("com.android.car", "CarFooService");
        c.setSuperClass("ICarFoo.Stub");
        c.getAnnotations().add("SystemApi");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<CarService>>"));
        assertTrue(puml, puml.contains("<<SystemApi>>"));
        assertTrue(puml, puml.contains("<<binder>>"));
    }

    @Test
    public void testMarkAaosCategoriesOffSuppressesAllAaosStereotypes() {
        JavaClassInfo c = make("com.android.car", "CarFooService");
        c.setSuperClass("ICarFoo.Stub");
        c.getAnnotations().add("SystemApi");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.markAaosCategories = false;
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c), o);
        assertFalse(puml, puml.contains("<<CarService>>"));
        assertFalse(puml, puml.contains("<<SystemApi>>"));
        assertFalse(puml, puml.contains("<<binder>>"));
    }

    @Test
    public void testHidingPrecedenceOverSystemApi() {
        JavaClassInfo c = make("com.x", "Foo");
        c.getAnnotations().add("SystemApi");
        c.setComment("/** @hide */");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        // Hidden が優先表示され、SystemApi は出ない
        assertTrue(puml, puml.contains("<<Hidden>>"));
        assertFalse(puml, puml.contains("<<SystemApi>>"));
    }

    @Test
    public void testRegularClassHasNoNewStereotypes() {
        List<JavaClassInfo> infos = Arrays.asList(make("com.x", "PlainFoo"));
        String puml = PlantUmlClassDiagram.generate(infos);
        assertFalse(puml, puml.contains("<<Hidden>>"));
        assertFalse(puml, puml.contains("<<SystemApi>>"));
        assertFalse(puml, puml.contains("<<TestApi>>"));
        assertFalse(puml, puml.contains("<<binder>>"));
    }

    @Test
    public void testApiLevelBadgeRenderedAsStereotype() {
        JavaClassInfo c = make("android.car", "Foo");
        c.getAnnotations().add("AddedIn(majorVersion=33)");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<API 33+>>"));
    }

    @Test
    public void testApiRequirementsBadgeRenderedAsStereotype() {
        JavaClassInfo c = make("android.car", "Foo");
        c.getAnnotations().add(
                "ApiRequirements(minPlatformVersion=Car.PLATFORM_VERSION_TIRAMISU_0, "
                        + "minCarVersion=Car.PLATFORM_VERSION_TIRAMISU_0)");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<Plat TIRAMISU+/Car TIRAMISU+>>"));
    }

    @Test
    public void testApiBadgeSuppressedByMarkAaosCategoriesOff() {
        JavaClassInfo c = make("android.car", "Foo");
        c.getAnnotations().add("AddedIn(33)");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.markAaosCategories = false;
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c), o);
        assertFalse(puml, puml.contains("API 33+"));
    }

    @Test
    public void testCarAppServiceStereotypeAppears() {
        JavaClassInfo c = make("com.example.app", "MyCarService");
        c.setSuperClass("CarAppService");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<CarAppService>>"));
    }

    @Test
    public void testCarAppScreenStereotypeFromFqnSuperclass() {
        JavaClassInfo c = make("com.example.app", "HomeScreen");
        c.setSuperClass("androidx.car.app.Screen");
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c));
        assertTrue(puml, puml.contains("<<CarAppScreen>>"));
    }

    @Test
    public void testCarAppSessionStereotypeSuppressedByMarkAaosCategoriesOff() {
        JavaClassInfo c = make("androidx.car.app.demo", "DemoSession");
        c.setSuperClass("Session");
        PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
        o.markAaosCategories = false;
        String puml = PlantUmlClassDiagram.generate(Arrays.asList(c), o);
        assertFalse(puml, puml.contains("<<CarAppSession>>"));
    }
}
