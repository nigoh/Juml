// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.init;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;

import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * AppInitAnnotationDetector のユニットテスト。
 */
public class AppInitAnnotationDetectorTest {

    private final AppInitAnnotationDetector detector = new AppInitAnnotationDetector();

    @Test
    public void detectsHiltFromAnnotationList() {
        JavaClassInfo cls = new JavaClassInfo();
        cls.getAnnotations().add("HiltAndroidApp");
        List<AppInitAnnotationDetector.DiFramework> found = detector.detect(cls, "");
        assertTrue(found.contains(AppInitAnnotationDetector.DiFramework.HILT));
    }

    @Test
    public void detectsHiltFromSource() {
        JavaClassInfo cls = new JavaClassInfo();
        String src = "@HiltAndroidApp\npublic class MyApp extends Application {}";
        List<AppInitAnnotationDetector.DiFramework> found = detector.detect(cls, src);
        assertTrue(found.contains(AppInitAnnotationDetector.DiFramework.HILT));
    }

    @Test
    public void detectsKoinFromSource() {
        JavaClassInfo cls = new JavaClassInfo();
        String src = "startKoin {\n  modules(appModule)\n}";
        List<AppInitAnnotationDetector.DiFramework> found = detector.detect(cls, src);
        assertTrue(found.contains(AppInitAnnotationDetector.DiFramework.KOIN));
    }

    @Test
    public void detectsDaggerFromSource() {
        JavaClassInfo cls = new JavaClassInfo();
        String src = "DaggerAppComponent.create().inject(this);";
        List<AppInitAnnotationDetector.DiFramework> found = detector.detect(cls, src);
        assertTrue(found.contains(AppInitAnnotationDetector.DiFramework.DAGGER));
    }

    @Test
    public void noDiFrameworkIfNoneDetected() {
        JavaClassInfo cls = new JavaClassInfo();
        String src = "public class MyApp extends Application { void onCreate() {} }";
        List<AppInitAnnotationDetector.DiFramework> found = detector.detect(cls, src);
        assertTrue(found.isEmpty());
    }

    @Test
    public void nullClassReturnsEmptyList() {
        List<AppInitAnnotationDetector.DiFramework> found = detector.detect(null, "");
        assertTrue(found.isEmpty());
    }
}
