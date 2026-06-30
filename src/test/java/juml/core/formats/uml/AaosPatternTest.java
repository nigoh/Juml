// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AaosPattern のユニットテスト。
 */
public class AaosPatternTest {

    private static JavaClassInfo make(String pkg, String name, JavaClassInfo.Kind kind) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(kind);
        return c;
    }

    @Test
    public void testCarManagerPattern() {
        JavaClassInfo c = make("android.car.audio", "CarAudioManager",
                JavaClassInfo.Kind.CLASS);
        assertEquals("CarManager", AaosPattern.categorize(c));
    }

    @Test
    public void testCarServicePattern() {
        JavaClassInfo c = make("com.android.car", "CarAudioService",
                JavaClassInfo.Kind.CLASS);
        assertEquals("CarService", AaosPattern.categorize(c));
    }

    @Test
    public void testAidlInterfacePattern() {
        JavaClassInfo c = make("android.car", "ICarAudio",
                JavaClassInfo.Kind.INTERFACE);
        assertEquals("ICarInterface", AaosPattern.categorize(c));
    }

    @Test
    public void testAidlKindAlwaysCategorized() {
        JavaClassInfo c = make("com.x", "Anything",
                JavaClassInfo.Kind.AIDL_INTERFACE);
        assertEquals("AIDL", AaosPattern.categorize(c));
    }

    @Test
    public void testNonAaosPackage() {
        JavaClassInfo c = make("com.example.foo", "CarThingManager",
                JavaClassInfo.Kind.CLASS);
        // パッケージが AAOS じゃないので CarManager 分類されない
        assertNull(AaosPattern.categorize(c));
    }

    @Test
    public void testAaosApiAnnotationDetection() {
        JavaClassInfo c = make("android.car", "Helper", JavaClassInfo.Kind.CLASS);
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("foo");
        m.getAnnotations().add("AddedIn(majorVersion=33)");
        c.getMethods().add(m);
        assertEquals("AaosApi", AaosPattern.categorize(c));
    }

    @Test
    public void testIsAaosApiAnnotation() {
        assertTrue(AaosPattern.isAaosApiAnnotation("AddedIn"));
        assertTrue(AaosPattern.isAaosApiAnnotation("AddedIn(majorVersion=33)"));
        assertTrue(AaosPattern.isAaosApiAnnotation("ApiRequirements"));
        assertTrue(AaosPattern.isAaosApiAnnotation("android.car.annotation.AddedIn"));
        assertFalse(AaosPattern.isAaosApiAnnotation("Override"));
        assertFalse(AaosPattern.isAaosApiAnnotation(""));
        assertFalse(AaosPattern.isAaosApiAnnotation(null));
    }

    @Test
    public void testIsInAaosPackage() {
        assertTrue(AaosPattern.isInAaosPackage("android.car"));
        assertTrue(AaosPattern.isInAaosPackage("android.car.audio"));
        assertTrue(AaosPattern.isInAaosPackage("com.android.car.internal"));
        assertFalse(AaosPattern.isInAaosPackage("com.example"));
        assertFalse(AaosPattern.isInAaosPackage(""));
        assertFalse(AaosPattern.isInAaosPackage(null));
    }

    @Test
    public void testNullClassInfo() {
        assertNull(AaosPattern.categorize(null));
    }

    // -------- API 可視性 (@SystemApi / @TestApi / @hide) --------

    @Test
    public void testSystemApiByAnnotation() {
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("SystemApi");
        assertEquals("SystemApi", AaosPattern.apiVisibilityStereotype(c));
    }

    @Test
    public void testSystemApiByFqnAnnotation() {
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("android.annotation.SystemApi");
        assertEquals("SystemApi", AaosPattern.apiVisibilityStereotype(c));
    }

    @Test
    public void testSystemApiWithArguments() {
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("SystemApi(client=PRIVILEGED_APPS)");
        assertEquals("SystemApi", AaosPattern.apiVisibilityStereotype(c));
    }

    @Test
    public void testTestApiByAnnotation() {
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("TestApi");
        assertEquals("TestApi", AaosPattern.apiVisibilityStereotype(c));
    }

    @Test
    public void testHiddenByJavadoc() {
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        c.setComment("/**\n * Internal helper.\n * @hide\n */");
        assertEquals("Hidden", AaosPattern.apiVisibilityStereotype(c));
    }

    @Test
    public void testHiddenTakesPrecedenceOverSystemApi() {
        // @SystemApi だが JavaDoc に @hide があるケース。Hidden を優先表示。
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("SystemApi");
        c.setComment("/** @hide */");
        assertEquals("Hidden", AaosPattern.apiVisibilityStereotype(c));
    }

    @Test
    public void testNoVisibilityStereotype() {
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        assertNull(AaosPattern.apiVisibilityStereotype(c));
        c.getAnnotations().add("Override");
        assertNull(AaosPattern.apiVisibilityStereotype(c));
        c.setComment("/** Some javadoc with @hidemention but not the tag. */");
        assertNull(AaosPattern.apiVisibilityStereotype(c));
    }

    @Test
    public void testNullForApiVisibility() {
        assertNull(AaosPattern.apiVisibilityStereotype(null));
    }

    // -------- AIDL binder impl 判定 --------

    @Test
    public void testAidlBinderImpl() {
        JavaClassInfo c = make("com.android.car", "CarAudioService",
                JavaClassInfo.Kind.CLASS);
        c.setSuperClass("ICarAudio.Stub");
        assertTrue(AaosPattern.isAidlBinderImpl(c));
    }

    @Test
    public void testAidlBinderImplWithGenerics() {
        JavaClassInfo c = make("com.android.car", "CarFooService",
                JavaClassInfo.Kind.CLASS);
        c.setSuperClass("ICarFoo.Stub<Bar>");
        assertTrue(AaosPattern.isAidlBinderImpl(c));
    }

    @Test
    public void testAidlBinderImplDeeplyNested() {
        JavaClassInfo c = make("com.android.car", "Inner",
                JavaClassInfo.Kind.CLASS);
        c.setSuperClass("com.android.aidl.Outer.Inner.Stub");
        assertTrue(AaosPattern.isAidlBinderImpl(c));
    }

    @Test
    public void testNotBinderImpl() {
        JavaClassInfo c = make("com.android.car", "CarFooService",
                JavaClassInfo.Kind.CLASS);
        c.setSuperClass("BaseService");
        assertFalse(AaosPattern.isAidlBinderImpl(c));
    }

    @Test
    public void testBareStubNotBinderImpl() {
        // 単独 "Stub" (前段なし) は AIDL 生成名のパターンに合わないので除外
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        c.setSuperClass("Stub");
        assertFalse(AaosPattern.isAidlBinderImpl(c));
    }

    @Test
    public void testNullForBinderImpl() {
        assertFalse(AaosPattern.isAidlBinderImpl(null));
        JavaClassInfo c = make("com.x", "Foo", JavaClassInfo.Kind.CLASS);
        assertFalse(AaosPattern.isAidlBinderImpl(c));
    }

    // -------- API レベルバッジ (@ApiRequirements / @AddedIn 等) --------

    @Test
    public void testApiLevelBadgeAddedInNamedArg() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("AddedIn(majorVersion=33)");
        assertEquals("API 33+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeAddedInPositional() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("AddedIn(34)");
        assertEquals("API 34+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeAddedInWithWhitespace() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("AddedIn(majorVersion = 33)");
        assertEquals("API 33+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeAddedInOrBefore() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("AddedInOrBefore(majorVersion=33)");
        assertEquals("API <=33", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeMinimumCarVersion() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("MinimumCarVersion(35)");
        assertEquals("Car 35+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeMinimumPlatformSdkVersion() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("MinimumPlatformSdkVersion(34)");
        assertEquals("Plat 34+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeApiRequirementsCombined() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add(
                "ApiRequirements(minPlatformVersion=Car.PLATFORM_VERSION_TIRAMISU_0, "
                        + "minCarVersion=Car.PLATFORM_VERSION_TIRAMISU_0)");
        assertEquals("Plat TIRAMISU+/Car TIRAMISU+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeApiRequirementsPlatOnly() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add(
                "ApiRequirements(minPlatformVersion=Car.PLATFORM_VERSION_UPSIDE_DOWN_CAKE_0)");
        assertEquals("Plat UPSIDE_DOWN_CAKE+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeApiRequirementsPreferredOverAddedIn() {
        // ApiRequirements が AddedIn より優先される
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("AddedIn(33)");
        c.getAnnotations().add(
                "ApiRequirements(minPlatformVersion=Car.PLATFORM_VERSION_TIRAMISU_0, "
                        + "minCarVersion=Car.PLATFORM_VERSION_TIRAMISU_0)");
        assertEquals("Plat TIRAMISU+/Car TIRAMISU+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeFqnAnnotation() {
        // FQN 形式の annotation でも判定できる
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("android.car.annotation.AddedIn(majorVersion=33)");
        assertEquals("API 33+", AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeNoneForUnrelatedAnnotation() {
        JavaClassInfo c = make("android.car", "Foo", JavaClassInfo.Kind.CLASS);
        c.getAnnotations().add("Override");
        c.getAnnotations().add("Deprecated");
        assertNull(AaosPattern.apiLevelBadge(c));
    }

    @Test
    public void testApiLevelBadgeNullSafe() {
        assertNull(AaosPattern.apiLevelBadge(null));
    }
}
