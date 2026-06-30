// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link CarAppLibraryPattern} のユニットテスト。
 */
public class CarAppLibraryPatternTest {

    private static JavaClassInfo make(String pkg, String name, String superClass) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setSuperClass(superClass);
        return c;
    }

    @Test
    public void testCarAppServiceBySimpleName() {
        // CarAppService は車載特有の名前なので superClass マッチだけで採用
        JavaClassInfo c = make("com.example.app", "MyCarService", "CarAppService");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertEquals(1, ss.size());
        assertEquals("CarAppService", ss.get(0));
    }

    @Test
    public void testCarAppServiceByFqn() {
        JavaClassInfo c = make("com.example.app", "MyCarService",
                "androidx.car.app.CarAppService");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertEquals(1, ss.size());
        assertEquals("CarAppService", ss.get(0));
    }

    @Test
    public void testSessionFromFqnSuperclass() {
        JavaClassInfo c = make("com.example.app", "MySession",
                "androidx.car.app.Session");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertEquals(1, ss.size());
        assertEquals("CarAppSession", ss.get(0));
    }

    @Test
    public void testSessionFromPackageHintEvenWithSimpleSuperclass() {
        // クラス自体が androidx.car.app.* 配下なら、Session でも採用
        JavaClassInfo c = make("androidx.car.app.demo", "DemoSession", "Session");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertEquals(1, ss.size());
        assertEquals("CarAppSession", ss.get(0));
    }

    @Test
    public void testSessionWithSimpleSuperclassNoHintRejected() {
        // パッケージも FQN superClass も無いと、汎用名 "Session" は採用しない
        JavaClassInfo c = make("com.example.foo", "BackgroundSession", "Session");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertTrue(ss.isEmpty());
    }

    @Test
    public void testScreenFromFqnSuperclass() {
        JavaClassInfo c = make("com.example.app", "HomeScreen",
                "androidx.car.app.Screen");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertEquals(1, ss.size());
        assertEquals("CarAppScreen", ss.get(0));
    }

    @Test
    public void testScreenWithGenericsFqnSuperclass() {
        JavaClassInfo c = make("com.example.app", "HomeScreen",
                "androidx.car.app.Screen<MyTemplate>");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertEquals(1, ss.size());
        assertEquals("CarAppScreen", ss.get(0));
    }

    @Test
    public void testScreenSimpleSuperclassNoHintRejected() {
        // 別パッケージで Screen 単純名は誤検出を避けて拒否
        JavaClassInfo c = make("com.example.legacy", "LegacyScreen", "Screen");
        List<String> ss = CarAppLibraryPattern.classify(c);
        assertTrue(ss.isEmpty());
    }

    @Test
    public void testUnrelatedSuperclassReturnsEmpty() {
        JavaClassInfo c = make("com.example.app", "Foo", "java.lang.Object");
        assertTrue(CarAppLibraryPattern.classify(c).isEmpty());
    }

    @Test
    public void testNoSuperclassReturnsEmpty() {
        JavaClassInfo c = make("com.example.app", "Foo", null);
        assertTrue(CarAppLibraryPattern.classify(c).isEmpty());

        c.setSuperClass("");
        assertTrue(CarAppLibraryPattern.classify(c).isEmpty());
    }

    @Test
    public void testNullClassInfo() {
        assertTrue(CarAppLibraryPattern.classify(null).isEmpty());
    }

    @Test
    public void testFqnFromCarAppHelper() {
        assertTrue(CarAppLibraryPattern.isFqnFromCarApp("androidx.car.app.Screen"));
        assertTrue(CarAppLibraryPattern.isFqnFromCarApp(
                "androidx.car.app.model.PaneTemplate"));
        assertFalse(CarAppLibraryPattern.isFqnFromCarApp("Screen"));
        assertFalse(CarAppLibraryPattern.isFqnFromCarApp("com.example.Screen"));
        assertFalse(CarAppLibraryPattern.isFqnFromCarApp(null));
        assertFalse(CarAppLibraryPattern.isFqnFromCarApp(""));
    }

    @Test
    public void testInCarAppPackageHelper() {
        assertTrue(CarAppLibraryPattern.isInCarAppPackage("androidx.car.app"));
        assertTrue(CarAppLibraryPattern.isInCarAppPackage("androidx.car.app.demo"));
        assertTrue(CarAppLibraryPattern.isInCarAppPackage("androidx.car.app.model"));
        assertFalse(CarAppLibraryPattern.isInCarAppPackage("androidx.car.appfoo"));
        assertFalse(CarAppLibraryPattern.isInCarAppPackage("com.example"));
        assertFalse(CarAppLibraryPattern.isInCarAppPackage(""));
        assertFalse(CarAppLibraryPattern.isInCarAppPackage(null));
    }
}
