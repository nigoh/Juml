// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link VhalAnalyzer} の正規表現ベース呼び出し検出のテスト。
 */
public class VhalAnalyzerTest {

    @Test
    public void detectsFieldBasedGetProperty() {
        String src = "package com.x;\n"
                + "import android.car.hardware.property.CarPropertyManager;\n"
                + "public class HvacController {\n"
                + "  private CarPropertyManager mCpm;\n"
                + "  public int getFanSpeed() {\n"
                + "    return mCpm.getIntProperty(HVAC_FAN_SPEED, AREA_ID_GLOBAL);\n"
                + "  }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "HvacController.java");
        assertEquals(1, hits.size());
        VhalAccess a = hits.get(0);
        assertEquals(VhalAccess.Kind.GET, a.getKind());
        assertEquals("HVAC_FAN_SPEED", a.getPropertyShortName());
        assertEquals("com.x.HvacController", a.getCallerFqn());
        assertEquals("getFanSpeed", a.getCallerMethod());
    }

    @Test
    public void detectsSetAndSubscribe() {
        String src = "package com.x;\n"
                + "public class HvacCtl {\n"
                + "  private CarPropertyManager carPropertyManager;\n"
                + "  void setFan(int v) { carPropertyManager.setIntProperty(HVAC_FAN_SPEED, 0, v); }\n"
                + "  void subscribe() { carPropertyManager.registerCallback(cb, HVAC_TEMPERATURE_SET, 0); }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "HvacCtl.java");
        assertEquals(2, hits.size());
        boolean foundSet = false, foundSub = false;
        for (VhalAccess a : hits) {
            if (a.getKind() == VhalAccess.Kind.SET
                    && "HVAC_FAN_SPEED".equals(a.getPropertyShortName())) foundSet = true;
            if (a.getKind() == VhalAccess.Kind.SUBSCRIBE) foundSub = true;
        }
        assertTrue(foundSet);
        assertTrue(foundSub);
    }

    @Test
    public void skipsNonCarPropertyManagerReceivers() {
        String src = "package com.x;\n"
                + "public class Other {\n"
                + "  private Bar bar;\n"
                + "  void run() { bar.getProperty(KEY, 0); }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "Other.java");
        assertEquals(0, hits.size());
    }

    @Test
    public void handlesNestedParenthesesInArguments() {
        // 第1引数自体がメソッド呼び出し (getPropId(...)) のネスト括弧でも、
        // 引数列が途中で切れず、Property/Area トークンを正しく取り出すこと。
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  private CarPropertyManager mCpm;\n"
                + "  int v() {\n"
                + "    return mCpm.getProperty(getPropId(VENDOR_X), GLOBAL_AREA);\n"
                + "  }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "A.java");
        assertEquals(1, hits.size());
        VhalAccess h = hits.get(0);
        assertEquals(VhalAccess.Kind.GET, h.getKind());
        // Property トークンはネスト括弧を含めて完結している
        assertEquals("getPropId(VENDOR_X)", h.getPropertyToken());
        // Area トークン (第2引数) が切れずに拾えている
        assertEquals("GLOBAL_AREA", h.getAreaToken());
    }

    @Test
    public void detectsQualifiedReceiverName() {
        String src = "package com.x;\n"
                + "public class A {\n"
                + "  private CarPropertyManager mCarPropertyManager;\n"
                + "  int v() {\n"
                + "    return mCarPropertyManager.getProperty(PROP_FOO, 0);\n"
                + "  }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "A.java");
        assertEquals(1, hits.size());
        assertEquals(VhalAccess.Kind.GET, hits.get(0).getKind());
        assertEquals("PROP_FOO", hits.get(0).getPropertyShortName());
    }

    @Test
    public void catalogResolvesPropertyId() {
        VehiclePropertyCatalog cat = new VehiclePropertyCatalog();
        cat.loadFromSource(
                "public class VehiclePropertyIds {\n"
                + "  public static final int HVAC_FAN_SPEED = 0x12345678;\n"
                + "  public static final int PERF_VEHICLE_SPEED = 291504647;\n"
                + "}\n");
        assertEquals(Long.valueOf(0x12345678L), cat.idOf("HVAC_FAN_SPEED").orElse(-1L));
        assertEquals(Long.valueOf(291504647L), cat.idOf("PERF_VEHICLE_SPEED").orElse(-1L));
        assertEquals(Long.valueOf(0x12345678L),
                cat.idOf("VehiclePropertyIds.HVAC_FAN_SPEED").orElse(-1L));
    }

    @Test
    public void markdownReportShowsHeaderAndAccessCounts() {
        String src = "package com.x;\n"
                + "public class Ctl {\n"
                + "  private CarPropertyManager cpm;\n"
                + "  void a() { cpm.getProperty(P1, 0); }\n"
                + "  void b() { cpm.setProperty(P1, 0, 1); }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "Ctl.java");
        String md = MarkdownVhalReport.render(hits, null);
        assertTrue(md.contains("VHAL Property Flow Report"));
        assertTrue(md.contains("`P1`"));
        assertTrue(md.contains("GET"));
        assertTrue(md.contains("SET"));
    }

    @Test
    public void plantUmlOutputHasEnduml() {
        String src = "package com.x;\n"
                + "public class Ctl {\n"
                + "  private CarPropertyManager cpm;\n"
                + "  void a() { cpm.getProperty(P1, 0); }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "Ctl.java");
        String puml = PlantUmlVhalFlowDiagram.render(hits);
        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("P1"));
        assertTrue(puml.contains("com.x.Ctl"));
    }

    @Test
    public void staticCallIsNotDoubleCounted() {
        // CarPropertyManager.getProperty(...) は CALL_PATTERN (receiver 判定を通過) と
        // STATIC_CALL_PATTERN の両方にマッチするため、以前は 1 呼び出しが 2 件計上されていた。
        String src = "package com.x;\n"
                + "public class Ctl {\n"
                + "  int read() { return CarPropertyManager.getProperty(PROP_FOO, 0); }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "Ctl.java");
        assertEquals(1, hits.size());
        assertEquals(VhalAccess.Kind.GET, hits.get(0).getKind());
        assertEquals("PROP_FOO", hits.get(0).getPropertyShortName());
    }
}
