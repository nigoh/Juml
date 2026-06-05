// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ResourceLinkAnalyzer のユニットテスト (analyzeSource 中心)。
 */
public class ResourceLinkAnalyzerTest {

    private ResourceReference find(ResourceLinkAnalysis r,
                                   ResourceReference.Kind kind, String name) {
        for (ResourceReference ref : r.getReferences()) {
            if (ref.getKind() == kind && name.equals(ref.getResourceName())) {
                return ref;
            }
        }
        return null;
    }

    @Test
    public void testSetContentViewIsContentBinding() {
        String src = "package com.x;\n"
                + "public class MainActivity extends Activity {\n"
                + "  protected void onCreate(Bundle b) {\n"
                + "    setContentView(R.layout.activity_main);\n"
                + "    String s = getString(R.string.app_name);\n"
                + "    View v = findViewById(R.id.ok_button);\n"
                + "  }\n"
                + "}\n";
        ResourceLinkAnalysis r = new ResourceLinkAnalysis();
        new ResourceLinkAnalyzer().analyzeSource(src, "MainActivity.java", r);

        ResourceReference layout = find(r, ResourceReference.Kind.LAYOUT, "activity_main");
        assertNotNull(layout);
        assertEquals("MainActivity", layout.getOwnerClass());
        assertTrue("setContentView は content binding", layout.isContentBinding());

        ResourceReference str = find(r, ResourceReference.Kind.STRING, "app_name");
        assertNotNull(str);
        assertEquals("MainActivity", str.getOwnerClass());

        assertNotNull(find(r, ResourceReference.Kind.ID, "ok_button"));
    }

    @Test
    public void testPlainRLayoutIsNotContentBinding() {
        String src = "class Helper { int id = R.layout.row_item; }\n";
        ResourceLinkAnalysis r = new ResourceLinkAnalysis();
        new ResourceLinkAnalyzer().analyzeSource(src, "Helper.java", r);
        ResourceReference layout = find(r, ResourceReference.Kind.LAYOUT, "row_item");
        assertNotNull(layout);
        assertFalse(layout.isContentBinding());
    }

    @Test
    public void testViewBindingDerivesLayoutName() {
        String src = "class DetailFragment {\n"
                + "  void f(android.view.LayoutInflater i) {\n"
                + "    ActivityDetailBinding b = ActivityDetailBinding.inflate(i);\n"
                + "  }\n"
                + "}\n";
        ResourceLinkAnalysis r = new ResourceLinkAnalysis();
        new ResourceLinkAnalyzer().analyzeSource(src, "DetailFragment.java", r);
        ResourceReference layout = find(r, ResourceReference.Kind.LAYOUT, "activity_detail");
        assertNotNull("Binding からレイアウト名を逆算", layout);
        assertTrue(layout.isContentBinding());
        assertEquals("DetailFragment", layout.getOwnerClass());
    }

    @Test
    public void testRStyleExtracted() {
        String src = "class ThemedActivity {\n"
                + "  void onCreate() {\n"
                + "    setTheme(R.style.AppTheme_Dark);\n"
                + "    int s = R.style.Button_Primary;\n"
                + "  }\n"
                + "}\n";
        ResourceLinkAnalysis r = new ResourceLinkAnalysis();
        new ResourceLinkAnalyzer().analyzeSource(src, "ThemedActivity.java", r);
        assertNotNull(find(r, ResourceReference.Kind.STYLE, "AppTheme_Dark"));
        ResourceReference btn = find(r, ResourceReference.Kind.STYLE, "Button_Primary");
        assertNotNull(btn);
        assertEquals("ThemedActivity", btn.getOwnerClass());
        // R.style は content binding ではない
        assertFalse(btn.isContentBinding());
    }

    @Test
    public void testBindingClassToLayout() {
        assertEquals("activity_main",
                ResourceLinkAnalyzer.bindingClassToLayout("ActivityMainBinding"));
        assertEquals("fragment_list_item",
                ResourceLinkAnalyzer.bindingClassToLayout("FragmentListItemBinding"));
    }

    @Test
    public void testDetectOwnerKotlinObject() {
        assertEquals("Foo", ResourceLinkAnalyzer.detectOwner("object Foo { }"));
        assertEquals("Bar",
                ResourceLinkAnalyzer.detectOwner("internal class Bar : Activity() {}"));
        assertNull(ResourceLinkAnalyzer.detectOwner("// no type here"));
    }

    @Test
    public void testDedupMergesContentFlag() {
        // R.layout.activity_main が複数回 (片方は setContentView) でも 1 件に統合され content=true
        String src = "class A {\n"
                + "  int x = R.layout.activity_main;\n"
                + "  void c(){ setContentView(R.layout.activity_main); }\n"
                + "}\n";
        ResourceLinkAnalysis r = new ResourceLinkAnalysis();
        new ResourceLinkAnalyzer().analyzeSource(src, "A.java", r);
        int count = 0;
        for (ResourceReference ref : r.getReferences()) {
            if (ref.getKind() == ResourceReference.Kind.LAYOUT
                    && "activity_main".equals(ref.getResourceName())) {
                count++;
                assertTrue(ref.isContentBinding());
            }
        }
        assertEquals("重複は 1 件に統合", 1, count);
    }
}
