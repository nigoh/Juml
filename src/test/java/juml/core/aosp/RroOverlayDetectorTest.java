// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

/**
 * RRO Overlay 検出のテスト。
 */
public class RroOverlayDetectorTest {

    @Test
    public void detectsBasicOverlay() {
        String src = "<?xml version=\"1.0\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"com.example.overlay\">\n"
                + "  <overlay android:targetPackage=\"com.android.systemui\" />\n"
                + "  <application />\n"
                + "</manifest>\n";
        RroOverlay o = new RroOverlayDetector().parseManifest(src, "AndroidManifest.xml");
        assertNotNull(o);
        assertEquals("com.example.overlay", o.getOverlayPackage());
        assertEquals("com.android.systemui", o.getTargetPackage());
        assertFalse(o.isStatic());
        assertEquals(-1, o.getPriority());
    }

    @Test
    public void detectsStaticOverlayWithPriority() {
        String src = "<?xml version=\"1.0\"?>\n"
                + "<manifest package=\"com.android.car.overlay\">\n"
                + "  <overlay\n"
                + "      android:targetPackage=\"com.android.car.ui\"\n"
                + "      android:isStatic=\"true\"\n"
                + "      android:priority=\"100\"\n"
                + "      android:targetName=\"BaseLayout\" />\n"
                + "</manifest>\n";
        RroOverlay o = new RroOverlayDetector().parseManifest(src, "AndroidManifest.xml");
        assertNotNull(o);
        assertEquals("com.android.car.overlay", o.getOverlayPackage());
        assertEquals("com.android.car.ui", o.getTargetPackage());
        assertTrue(o.isStatic());
        assertEquals(100, o.getPriority());
        assertEquals("BaseLayout", o.getTargetName());
    }

    @Test
    public void returnsNullForNonOverlayManifest() {
        String src = "<?xml version=\"1.0\"?>\n"
                + "<manifest package=\"com.example.app\">\n"
                + "  <application android:label=\"Foo\" />\n"
                + "</manifest>\n";
        RroOverlay o = new RroOverlayDetector().parseManifest(src, "AndroidManifest.xml");
        assertNull(o);
    }

    @Test
    public void markdownReportSummarizes() {
        java.util.List<RroOverlay> list = new java.util.ArrayList<>();
        list.add(new RroOverlay("com.x.a", "com.android.systemui", "", true, 50, "a.xml"));
        list.add(new RroOverlay("com.x.b", "com.android.systemui", "", false, -1, "b.xml"));
        list.add(new RroOverlay("com.x.c", "com.android.car.ui", "", true, 100, "c.xml"));
        String md = MarkdownRroReport.render(list);
        assertTrue(md.contains("RRO Overlay Report"));
        assertTrue(md.contains("Overlay count: 3"));
        assertTrue(md.contains("Static overlays: 2"));
        assertTrue(md.contains("Distinct target packages: 2"));
        assertTrue(md.contains("com.android.systemui"));
        assertTrue(md.contains("com.android.car.ui"));
    }

    @Test
    public void emptyListRendersPlaceholder() {
        String md = MarkdownRroReport.render(new java.util.ArrayList<>());
        assertNotNull(md);
        assertTrue(md.contains("no RRO"));
    }

    @Test
    public void overlayWithoutAndroidPrefixAlsoWorks() {
        // 古い形式: android: 名前空間プレフィックスなし
        String src = "<?xml version=\"1.0\"?>\n"
                + "<manifest package=\"com.example.overlay\">\n"
                + "  <overlay targetPackage=\"com.android.systemui\" priority=\"1\" />\n"
                + "</manifest>\n";
        RroOverlay o = new RroOverlayDetector().parseManifest(src, "AndroidManifest.xml");
        assertNotNull(o);
        assertEquals("com.android.systemui", o.getTargetPackage());
        assertEquals(1, o.getPriority());
    }
}
