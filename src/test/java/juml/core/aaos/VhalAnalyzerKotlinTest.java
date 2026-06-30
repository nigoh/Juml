// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link VhalAnalyzer} の Kotlin ソース対応テスト。
 *
 * <p>Java と Kotlin で構文が異なる点 (val/var フィールド宣言, fun メソッド宣言,
 * data class, object 宣言) でも CarPropertyManager API 呼び出しを検出できるかを確認する。</p>
 */
public class VhalAnalyzerKotlinTest {

    @Test
    public void detectsKotlinFieldBasedGetProperty() {
        String src = "package com.x\n"
                + "import android.car.hardware.property.CarPropertyManager\n"
                + "class HvacController {\n"
                + "    private val mCpm: CarPropertyManager = TODO()\n"
                + "    fun getFanSpeed(): Int {\n"
                + "        return mCpm.getIntProperty(HVAC_FAN_SPEED, 0)\n"
                + "    }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "HvacController.kt");
        assertEquals(1, hits.size());
        VhalAccess a = hits.get(0);
        assertEquals(VhalAccess.Kind.GET, a.getKind());
        assertEquals("HVAC_FAN_SPEED", a.getPropertyShortName());
        assertEquals("com.x.HvacController", a.getCallerFqn());
        assertEquals("getFanSpeed", a.getCallerMethod());
    }

    @Test
    public void detectsKotlinLateinitVar() {
        String src = "package com.x\n"
                + "class A {\n"
                + "    private lateinit var carPropertyManager: CarPropertyManager\n"
                + "    fun set(v: Int) { carPropertyManager.setIntProperty(PROP_A, 0, v) }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "A.kt");
        assertEquals(1, hits.size());
        assertEquals(VhalAccess.Kind.SET, hits.get(0).getKind());
        assertEquals("PROP_A", hits.get(0).getPropertyShortName());
        assertEquals("set", hits.get(0).getCallerMethod());
    }

    @Test
    public void detectsRegisterCallbackInKotlinObject() {
        String src = "package com.x\n"
                + "object Hvac {\n"
                + "    private val cpm: CarPropertyManager = TODO()\n"
                + "    fun subscribe() {\n"
                + "        cpm.registerCallback(cb, HVAC_TEMPERATURE_SET, 1.0f)\n"
                + "    }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "Hvac.kt");
        assertEquals(1, hits.size());
        VhalAccess a = hits.get(0);
        assertEquals(VhalAccess.Kind.SUBSCRIBE, a.getKind());
        assertEquals("HVAC_TEMPERATURE_SET", a.getPropertyShortName());
        assertEquals("com.x.Hvac", a.getCallerFqn());
    }

    @Test
    public void kotlinFunDeclarationGivesCallerMethodName() {
        String src = "package com.x\n"
                + "class A {\n"
                + "    private val cpm: CarPropertyManager = TODO()\n"
                + "    fun runFanSpeed() {\n"
                + "        cpm.getIntProperty(P1, 0)\n"
                + "    }\n"
                + "    fun runFan() {\n"
                + "        cpm.getIntProperty(P2, 0)\n"
                + "    }\n"
                + "}\n";
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "A.kt");
        assertEquals(2, hits.size());
        boolean found1 = false, found2 = false;
        for (VhalAccess h : hits) {
            if ("P1".equals(h.getPropertyShortName())
                    && "runFanSpeed".equals(h.getCallerMethod())) found1 = true;
            if ("P2".equals(h.getPropertyShortName())
                    && "runFan".equals(h.getCallerMethod())) found2 = true;
        }
        assertTrue("P1 in runFanSpeed", found1);
        assertTrue("P2 in runFan", found2);
    }

    @Test
    public void readsDataClassName() {
        String src = "package com.x\n"
                + "data class CarPropertyManagerHolder(\n"
                + "    val cpm: CarPropertyManager\n"
                + ") {\n"
                + "    fun get() = cpm.getProperty(P, 0)\n"
                + "}\n";
        // 注: data class の本体内の式 `cpm.getProperty(...)` は METHOD_DECL_PATTERN に
        // マッチしない (fun は単一式) ため caller method は空でも検出はされる
        List<VhalAccess> hits = new VhalAnalyzer().analyzeSource(src, "X.kt");
        assertTrue("should detect at least one VHAL access", hits.size() >= 1);
        assertEquals("P", hits.get(0).getPropertyShortName());
        assertEquals("com.x.CarPropertyManagerHolder", hits.get(0).getCallerFqn());
    }
}
