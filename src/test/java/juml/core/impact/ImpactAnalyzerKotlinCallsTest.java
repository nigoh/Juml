// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.impact;

import org.junit.Test;
import juml.core.formats.kotlin.KotlinLightScanner;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.refs.ReferenceIndex;
import juml.core.refs.ReferenceIndexBuilder;
import juml.core.refs.ReferenceKey;
import juml.core.refs.ReferenceSite;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin メソッド本体の呼び出し解析が ReferenceIndex 経由で Impact に流れるかを確認する。
 */
public class ImpactAnalyzerKotlinCallsTest {

    private static List<JavaClassInfo> parseKotlin(String... sources) {
        List<JavaClassInfo> all = new ArrayList<>();
        for (String s : sources) {
            all.addAll(KotlinLightScanner.scan(s, ErrorListener.silent()));
        }
        return all;
    }

    private static ReferenceIndex buildIndex(List<JavaClassInfo> classes) {
        ClassIndex idx = new ClassIndex();
        for (JavaClassInfo c : classes) {
            idx.put(c, null, null);
        }
        ReferenceIndex ref = new ReferenceIndex();
        new ReferenceIndexBuilder(ref, idx, null, ErrorListener.silent()).addAll(classes);
        return ref;
    }

    @Test
    public void callViaFieldEmitsCallEdge() {
        String target = "package com.x\n"
                + "class Target {\n"
                + "  fun hit() {}\n"
                + "}\n";
        String caller = "package com.x\n"
                + "class Caller {\n"
                + "  private val t: Target = Target()\n"
                + "  fun run() { t.hit() }\n"
                + "}\n";
        ReferenceIndex idx = buildIndex(parseKotlin(target, caller));
        List<ReferenceSite> sites = idx.sites(
                ReferenceKey.ofMethod("com.x.Target", "hit"));
        assertNotNull(sites);
        // フィールド型解決経由で Caller.run -> Target.hit の CALL エッジが出る
        boolean foundCall = false;
        for (ReferenceSite s : sites) {
            if (s.getKind() == ReferenceSite.Kind.CALL
                    && "com.x.Caller".equals(s.getCallerFqn())
                    && "run".equals(s.getCallerMethod())) {
                foundCall = true;
            }
        }
        assertTrue("Kotlin t.hit() must produce a CALL edge", foundCall);
    }

    @Test
    public void safeCallReceiverIsResolved() {
        String target = "package com.x\n"
                + "class Listener { fun onChange() {} }\n";
        String caller = "package com.x\n"
                + "class A {\n"
                + "  private val listener: Listener? = null\n"
                + "  fun fire() { listener?.onChange() }\n"
                + "}\n";
        ReferenceIndex idx = buildIndex(parseKotlin(target, caller));
        List<ReferenceSite> sites = idx.sites(
                ReferenceKey.ofMethod("com.x.Listener", "onChange"));
        boolean foundCall = false;
        for (ReferenceSite s : sites) {
            if (s.getKind() == ReferenceSite.Kind.CALL
                    && "com.x.A".equals(s.getCallerFqn())
                    && "fire".equals(s.getCallerMethod())) {
                foundCall = true;
            }
        }
        assertTrue("Kotlin safe-call ?. must produce a CALL edge", foundCall);
    }

    @Test
    public void impactAnalyzerProducesDirectCallerForKotlin() {
        String target = "package com.x\n"
                + "class Target { fun hit() {} }\n";
        String caller = "package com.x\n"
                + "class Caller {\n"
                + "  private val t: Target = Target()\n"
                + "  fun run() { t.hit() }\n"
                + "}\n";
        ReferenceIndex idx = buildIndex(parseKotlin(target, caller));
        ImpactGraph g = new ImpactAnalyzer(idx).analyzeMethod("com.x.Target", "hit", 3);
        ImpactGraph.Node callerNode = null;
        for (ImpactGraph.Node n : g.nodes()) {
            if ("com.x.Caller".equals(n.getId())) callerNode = n;
        }
        assertNotNull(callerNode);
        assertEquals(1, callerNode.getLayer());
    }
}
