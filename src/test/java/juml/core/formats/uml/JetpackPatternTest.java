// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link JetpackPattern} のステレオタイプ判定テスト。
 */
public class JetpackPatternTest {

    private static JavaClassInfo cls(String pkg, String name, String superClass) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setSuperClass(superClass);
        return c;
    }

    @Test
    public void testFragmentFromSimpleName() {
        JavaClassInfo c = cls("com.example.ui", "HomeFragment", "Fragment");
        assertEquals(List.of("Fragment"), JetpackPattern.classify(c));
    }

    @Test
    public void testFragmentFromFqn() {
        JavaClassInfo c = cls("com.example.ui", "HomeFragment",
                "androidx.fragment.app.Fragment");
        assertEquals(List.of("Fragment"), JetpackPattern.classify(c));
    }

    @Test
    public void testDialogFragmentTakesPriorityOverFragment() {
        // DialogFragment 自体が Fragment を継承しているが、ソース上の extends は 1 つ
        JavaClassInfo c = cls("com.example.ui", "AboutDialog", "DialogFragment");
        // DialogFragment のみが入り、Fragment は重複しない
        assertEquals(List.of("DialogFragment"), JetpackPattern.classify(c));
    }

    @Test
    public void testBottomSheetDialogFragment() {
        JavaClassInfo c = cls("com.example.ui", "SheetFrag",
                "com.google.android.material.bottomsheet.BottomSheetDialogFragment");
        assertEquals(List.of("BottomSheetDialogFragment"), JetpackPattern.classify(c));
    }

    @Test
    public void testNavHostFragment() {
        JavaClassInfo c = cls("com.example.ui", "MyNavHost", "NavHostFragment");
        assertEquals(List.of("NavHostFragment"), JetpackPattern.classify(c));
    }

    @Test
    public void testViewModel() {
        JavaClassInfo c = cls("com.example.vm", "HomeViewModel", "ViewModel");
        assertEquals(List.of("ViewModel"), JetpackPattern.classify(c));
    }

    @Test
    public void testAndroidViewModelOverridesViewModel() {
        JavaClassInfo c = cls("com.example.vm", "HomeViewModel", "AndroidViewModel");
        assertEquals(List.of("AndroidViewModel"), JetpackPattern.classify(c));
    }

    @Test
    public void testAndroidEntryPointAnnotation() {
        JavaClassInfo c = cls("com.example.ui", "MainActivity", "AppCompatActivity");
        c.getAnnotations().add("AndroidEntryPoint");
        // Activity 系 ステレオタイプは AaosPattern/Manifest 経由なので、Jetpack 側は AndroidEntryPoint のみ。
        assertEquals(List.of("AndroidEntryPoint"), JetpackPattern.classify(c));
    }

    @Test
    public void testHiltViewModelAlongsideViewModel() {
        JavaClassInfo c = cls("com.example.vm", "HomeViewModel", "ViewModel");
        c.getAnnotations().add("HiltViewModel");
        // ViewModel ステレオタイプも併記される (順序: super 由来 → annotation 由来)
        assertEquals(List.of("ViewModel", "HiltViewModel"), JetpackPattern.classify(c));
    }

    @Test
    public void testHiltAndroidApp() {
        JavaClassInfo c = cls("com.example", "App", "Application");
        c.getAnnotations().add("dagger.hilt.android.HiltAndroidApp");
        assertEquals(List.of("HiltAndroidApp"), JetpackPattern.classify(c));
    }

    @Test
    public void testHiltModuleVsDaggerModule() {
        JavaClassInfo hilt = cls("com.example.di", "NetworkModule", null);
        hilt.getAnnotations().add("Module");
        hilt.getAnnotations().add("InstallIn(SingletonComponent.class)");
        assertEquals(List.of("HiltModule"), JetpackPattern.classify(hilt));

        JavaClassInfo dagger = cls("com.example.di", "NetworkModule", null);
        dagger.getAnnotations().add("Module");
        assertEquals(List.of("DaggerModule"), JetpackPattern.classify(dagger));
    }

    @Test
    public void testInjectableConstructor() {
        JavaClassInfo c = cls("com.example.data", "Repo", null);
        JavaMethodInfo ctor = new JavaMethodInfo();
        ctor.setName("Repo");
        ctor.setConstructor(true);
        ctor.getAnnotations().add("Inject");
        c.getMethods().add(ctor);
        assertEquals(List.of("Injectable"), JetpackPattern.classify(c));
    }

    @Test
    public void testInjectOnNonConstructorIgnored() {
        JavaClassInfo c = cls("com.example.data", "Repo", null);
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName("init");
        m.setConstructor(false);
        m.getAnnotations().add("Inject");
        c.getMethods().add(m);
        assertTrue(JetpackPattern.classify(c).isEmpty());
    }

    @Test
    public void testPlainClassEmpty() {
        JavaClassInfo c = cls("com.example", "Plain", null);
        assertTrue(JetpackPattern.classify(c).isEmpty());
    }

    @Test
    public void testNullInput() {
        assertTrue(JetpackPattern.classify(null).isEmpty());
    }

    @Test
    public void testAnnotationShortNameStripsFqnAndArgs() {
        assertEquals("InstallIn",
                JetpackPattern.annotationShortName("dagger.hilt.InstallIn(SingletonComponent.class)"));
        assertEquals("Module", JetpackPattern.annotationShortName("@Module"));
        assertEquals("", JetpackPattern.annotationShortName(""));
        assertEquals("", JetpackPattern.annotationShortName(null));
    }

    @Test
    public void testSimpleNameStripsGenericsAndPackage() {
        assertEquals("Fragment", JetpackPattern.simpleName("androidx.fragment.app.Fragment"));
        assertEquals("MutableLiveData",
                JetpackPattern.simpleName("MutableLiveData<UiState>"));
        assertEquals("", JetpackPattern.simpleName(null));
        assertEquals("", JetpackPattern.simpleName(""));
    }

    @Test
    public void testUnrelatedSuperClass() {
        JavaClassInfo c = cls("com.example", "Foo", "Object");
        assertFalse(JetpackPattern.classify(c).contains("Fragment"));
        assertFalse(JetpackPattern.classify(c).contains("ViewModel"));
    }
}
