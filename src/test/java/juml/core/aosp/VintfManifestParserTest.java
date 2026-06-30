// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link VintfManifestParser} のユニットテスト。
 */
public class VintfManifestParserTest {

    @Test(expected = IllegalArgumentException.class)
    public void testNullInput() {
        VintfManifestParser.parse(null);
    }

    @Test
    public void testEmptyInputReturnsUnknown() {
        VintfManifest m = VintfManifestParser.parse("");
        assertEquals(VintfManifest.Kind.UNKNOWN, m.getKind());
        assertTrue(m.getHals().isEmpty());
    }

    @Test
    public void testInvalidXmlReturnsUnknown() {
        VintfManifest m = VintfManifestParser.parse("<not closed");
        assertEquals(VintfManifest.Kind.UNKNOWN, m.getKind());
    }

    @Test
    public void testAndroidManifestNotMisclassifiedAsVintf() {
        // AndroidManifest.xml は <manifest package="..."> 形式 (type 属性無し)
        // なので VINTF として扱わない
        String xml = "<manifest package=\"com.example\">"
                + "  <application/>"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(VintfManifest.Kind.UNKNOWN, m.getKind());
    }

    @Test
    public void testDeviceManifest() {
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(VintfManifest.Kind.DEVICE_MANIFEST, m.getKind());
        assertEquals("1.0", m.getVersion());
    }

    @Test
    public void testFrameworkManifest() {
        String xml = "<manifest version=\"1.0\" type=\"framework\">"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(VintfManifest.Kind.FRAMEWORK_MANIFEST, m.getKind());
    }

    @Test
    public void testCompatibilityMatrixWithLevel() {
        String xml = "<compatibility-matrix version=\"1.0\" type=\"framework\" level=\"6\">"
                + "</compatibility-matrix>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(VintfManifest.Kind.COMPATIBILITY_MATRIX, m.getKind());
        assertNotNull(m.getLevel());
        assertEquals(6, m.getLevel().intValue());
    }

    @Test
    public void testAidlHalFqnameForm() {
        // AIDL HAL は <interface> ではなく <fqname>IFoo/instance</fqname> 形式。
        // interface 名と instance を分解して VintfInterface に取り込むこと。
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"aidl\">"
                + "    <name>android.hardware.foo</name>"
                + "    <version>2</version>"
                + "    <fqname>IFoo/default</fqname>"
                + "    <fqname>IFoo/secondary</fqname>"
                + "  </hal>"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(1, m.getHals().size());
        VintfHal hal = m.getHals().get(0);
        assertEquals("aidl", hal.getFormat());
        assertEquals("android.hardware.foo", hal.getName());
        // 同名 interface IFoo に 2 instance がマージされる
        assertEquals(1, hal.getInterfaces().size());
        VintfInterface vi = hal.getInterfaces().get(0);
        assertEquals("IFoo", vi.getName());
        assertEquals(2, vi.getInstances().size());
        assertTrue(vi.getInstances().contains("default"));
        assertTrue(vi.getInstances().contains("secondary"));
    }

    @Test
    public void testHidlHalFullStructure() {
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"hidl\">"
                + "    <name>android.hardware.audio</name>"
                + "    <transport>hwbinder</transport>"
                + "    <version>6.0</version>"
                + "    <interface>"
                + "      <name>IDevicesFactory</name>"
                + "      <instance>default</instance>"
                + "    </interface>"
                + "  </hal>"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(1, m.getHals().size());
        VintfHal hal = m.getHals().get(0);
        assertEquals("hidl", hal.getFormat());
        assertEquals("android.hardware.audio", hal.getName());
        assertEquals("hwbinder", hal.getTransport());
        assertEquals(1, hal.getVersions().size());
        assertEquals("6.0", hal.getVersions().get(0));
        assertEquals(1, hal.getInterfaces().size());
        VintfInterface vi = hal.getInterfaces().get(0);
        assertEquals("IDevicesFactory", vi.getName());
        assertEquals(1, vi.getInstances().size());
        assertEquals("default", vi.getInstances().get(0));
    }

    @Test
    public void testAidlHal() {
        // AIDL HAL は transport を省略する (binder 固定)
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"aidl\">"
                + "    <name>android.hardware.power</name>"
                + "    <version>2</version>"
                + "    <interface>"
                + "      <name>IPower</name>"
                + "      <instance>default</instance>"
                + "    </interface>"
                + "  </hal>"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        VintfHal hal = m.getHals().get(0);
        assertEquals("aidl", hal.getFormat());
        assertNull(hal.getTransport());
        assertEquals("2", hal.getVersions().get(0));
    }

    @Test
    public void testMultipleInstances() {
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"hidl\">"
                + "    <name>android.hardware.foo</name>"
                + "    <version>1.0</version>"
                + "    <interface>"
                + "      <name>IFoo</name>"
                + "      <instance>default</instance>"
                + "      <instance>secondary</instance>"
                + "    </interface>"
                + "  </hal>"
                + "</manifest>";
        VintfInterface vi = m_get(xml).getInterfaces().get(0);
        assertEquals(2, vi.getInstances().size());
        assertEquals("default", vi.getInstances().get(0));
        assertEquals("secondary", vi.getInstances().get(1));
    }

    @Test
    public void testMultipleVersionsAndRanges() {
        String xml = "<compatibility-matrix version=\"1.0\" type=\"framework\" level=\"6\">"
                + "  <hal format=\"hidl\">"
                + "    <name>android.hardware.audio</name>"
                + "    <version>6.0-7</version>"
                + "    <version>5.0</version>"
                + "    <interface>"
                + "      <name>IDevicesFactory</name>"
                + "      <instance>default</instance>"
                + "    </interface>"
                + "  </hal>"
                + "</compatibility-matrix>";
        VintfHal hal = m_get(xml);
        assertEquals(2, hal.getVersions().size());
        assertEquals("6.0-7", hal.getVersions().get(0));
        assertEquals("5.0", hal.getVersions().get(1));
    }

    @Test
    public void testOptionalAttributeOnMatrix() {
        String xml = "<compatibility-matrix version=\"1.0\" type=\"framework\" level=\"6\">"
                + "  <hal format=\"hidl\" optional=\"false\">"
                + "    <name>android.hardware.foo</name>"
                + "    <version>1.0</version>"
                + "    <interface><name>IFoo</name><instance>default</instance></interface>"
                + "  </hal>"
                + "  <hal format=\"hidl\" optional=\"true\">"
                + "    <name>android.hardware.bar</name>"
                + "    <version>1.0</version>"
                + "    <interface><name>IBar</name><instance>default</instance></interface>"
                + "  </hal>"
                + "</compatibility-matrix>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(Boolean.FALSE, m.getHals().get(0).isOptional());
        assertEquals(Boolean.TRUE, m.getHals().get(1).isOptional());
    }

    @Test
    public void testOptionalNullWhenAbsent() {
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"hidl\">"
                + "    <name>android.hardware.foo</name>"
                + "    <version>1.0</version>"
                + "    <interface><name>IFoo</name><instance>default</instance></interface>"
                + "  </hal>"
                + "</manifest>";
        assertNull(m_get(xml).isOptional());
    }

    @Test
    public void testKernelAndSepolicyVersion() {
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <kernel version=\"5.10.0\"/>"
                + "  <sepolicy>"
                + "    <version>30.0</version>"
                + "  </sepolicy>"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals("5.10.0", m.getKernelVersion());
        assertEquals("30.0", m.getSepolicyVersion());
    }

    @Test
    public void testMultipleHalsAreCollected() {
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"hidl\">"
                + "    <name>android.hardware.foo</name>"
                + "    <version>1.0</version>"
                + "    <interface><name>IFoo</name><instance>default</instance></interface>"
                + "  </hal>"
                + "  <hal format=\"aidl\">"
                + "    <name>android.hardware.bar</name>"
                + "    <version>2</version>"
                + "    <interface><name>IBar</name><instance>default</instance></interface>"
                + "  </hal>"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(2, m.getHals().size());
        assertEquals("android.hardware.foo", m.getHals().get(0).getName());
        assertEquals("android.hardware.bar", m.getHals().get(1).getName());
    }

    @Test
    public void testHalWithoutInterfacesStillAccepted() {
        // 形式は欠落していても name さえあれば取り込む
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"hidl\">"
                + "    <name>android.hardware.foo</name>"
                + "    <version>1.0</version>"
                + "  </hal>"
                + "</manifest>";
        VintfHal hal = m_get(xml);
        assertEquals("android.hardware.foo", hal.getName());
        assertTrue(hal.getInterfaces().isEmpty());
    }

    @Test
    public void testHalWithoutNameSkipped() {
        // <name> が無い <hal> は出力に含めない
        String xml = "<manifest version=\"1.0\" type=\"device\">"
                + "  <hal format=\"hidl\">"
                + "    <version>1.0</version>"
                + "  </hal>"
                + "</manifest>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertTrue(m.getHals().isEmpty());
    }

    @Test
    public void testDoctypeRejectedSafely() {
        // 外部 DTD 解決を試みる入力 — secure builder で拒否され UNKNOWN を返す
        String xml = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE manifest SYSTEM \"https://evil/x.dtd\">"
                + "<manifest type=\"device\"/>";
        VintfManifest m = VintfManifestParser.parse(xml);
        assertEquals(VintfManifest.Kind.UNKNOWN, m.getKind());
    }

    // 単一 HAL を取り出すヘルパ
    private static VintfHal m_get(String xml) {
        VintfManifest m = VintfManifestParser.parse(xml);
        assertFalse(m.getHals().isEmpty());
        return m.getHals().get(0);
    }
}
