// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AndroidSuperclassDetectorTest {

    private static JavaClassInfo cls(String pkg, String name, String superClass) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(pkg);
        c.setSimpleName(name);
        c.setKind(JavaClassInfo.Kind.CLASS);
        c.setSuperClass(superClass);
        return c;
    }

    @Test
    public void testDetectsDirectActivitySubclass() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "MainActivity", "android.app.Activity"), null, null);
        assertEquals(AndroidSuperclassDetector.ComponentKind.ACTIVITY,
                AndroidSuperclassDetector.kindOf("app.MainActivity", idx));
    }

    @Test
    public void testDetectsAppCompatActivity() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "MainActivity", "androidx.appcompat.app.AppCompatActivity"), null, null);
        assertEquals(AndroidSuperclassDetector.ComponentKind.ACTIVITY,
                AndroidSuperclassDetector.kindOf("app.MainActivity", idx));
    }

    @Test
    public void testFragmentDetectedViaAndroidxFqn() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "HomeFragment", "androidx.fragment.app.Fragment"), null, null);
        assertEquals(AndroidSuperclassDetector.ComponentKind.FRAGMENT,
                AndroidSuperclassDetector.kindOf("app.HomeFragment", idx));
    }

    @Test
    public void testFragmentDetectedViaSimpleNameFallback() {
        // 外部 JAR にも居ない Fragment 基底クラスでも、単純名 "Fragment" で拾える
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "HomeFragment", "Fragment"), null, null);
        assertEquals(AndroidSuperclassDetector.ComponentKind.FRAGMENT,
                AndroidSuperclassDetector.kindOf("app.HomeFragment", idx));
    }

    @Test
    public void testWalksUpChainOfTwoCustomClasses() {
        ClassIndex idx = new ClassIndex();
        // GrandChild → ChildActivity → AppCompatActivity (=Activity)
        idx.put(cls("app", "BaseActivity", "androidx.appcompat.app.AppCompatActivity"), null, null);
        idx.put(cls("app", "GrandChild", "app.BaseActivity"), null, null);
        assertEquals(AndroidSuperclassDetector.ComponentKind.ACTIVITY,
                AndroidSuperclassDetector.kindOf("app.GrandChild", idx));
    }

    @Test
    public void testReceiverProviderService() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "BootReceiver", "android.content.BroadcastReceiver"), null, null);
        idx.put(cls("app", "FooProvider", "android.content.ContentProvider"), null, null);
        idx.put(cls("app", "WorkService", "android.app.Service"), null, null);
        assertEquals(AndroidSuperclassDetector.ComponentKind.RECEIVER,
                AndroidSuperclassDetector.kindOf("app.BootReceiver", idx));
        assertEquals(AndroidSuperclassDetector.ComponentKind.PROVIDER,
                AndroidSuperclassDetector.kindOf("app.FooProvider", idx));
        assertEquals(AndroidSuperclassDetector.ComponentKind.SERVICE,
                AndroidSuperclassDetector.kindOf("app.WorkService", idx));
    }

    @Test
    public void testWellKnownVendorServiceBasesDetected() {
        // 外部 jar (GMS / support) の Service 基底でも chain 解決不要で SERVICE 判定できる。
        // 回帰: WearListenerService extends WearableListenerService が dead-code 誤検出される問題。
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "WearListenerService",
                "com.google.android.gms.wearable.WearableListenerService"), null, null);
        idx.put(cls("app", "SyncJob", "androidx.core.app.JobIntentService"), null, null);
        idx.put(cls("app", "MyTile", "TileService"), null, null);
        assertEquals(AndroidSuperclassDetector.ComponentKind.SERVICE,
                AndroidSuperclassDetector.kindOf("app.WearListenerService", idx));
        assertEquals(AndroidSuperclassDetector.ComponentKind.SERVICE,
                AndroidSuperclassDetector.kindOf("app.SyncJob", idx));
        assertEquals(AndroidSuperclassDetector.ComponentKind.SERVICE,
                AndroidSuperclassDetector.kindOf("app.MyTile", idx));
    }

    @Test
    public void testPlainClassReturnsNull() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "Util", "java.lang.Object"), null, null);
        assertNull(AndroidSuperclassDetector.kindOf("app.Util", idx));
    }

    @Test
    public void testCycleInSuperChainTerminates() {
        // 異常データ: A extends B / B extends A の循環参照でも無限ループしない
        ClassIndex idx = new ClassIndex();
        idx.put(cls("p", "A", "p.B"), null, null);
        idx.put(cls("p", "B", "p.A"), null, null);
        assertNull(AndroidSuperclassDetector.kindOf("p.A", idx));
    }

    @Test
    public void testDetectAllReturnsOnlyComponentClasses() {
        ClassIndex idx = new ClassIndex();
        idx.put(cls("app", "MainActivity", "android.app.Activity"), null, null);
        idx.put(cls("app", "HomeFragment", "androidx.fragment.app.Fragment"), null, null);
        idx.put(cls("app", "Util", "java.lang.Object"), null, null);

        Map<String, AndroidSuperclassDetector.ComponentKind> all =
                AndroidSuperclassDetector.detect(idx);
        assertEquals(2, all.size());
        assertEquals(AndroidSuperclassDetector.ComponentKind.ACTIVITY,
                all.get("app.MainActivity"));
        assertEquals(AndroidSuperclassDetector.ComponentKind.FRAGMENT,
                all.get("app.HomeFragment"));
        assertTrue(!all.containsKey("app.Util"));
    }
}
