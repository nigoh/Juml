// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.aosp.AndroidBpModule;

import org.junit.Test;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 左ツリーへの Android.bp (Soong) 可視化テスト。
 *
 * <p>Swing ウィジェットの構築自体は headless でも可能なため (Robot を使わない)、
 * モデル構造とノード整形・open リクエストの組み立てを検証する。</p>
 */
public class SoongTreeTest {

    private static AndroidBpModule mod(String type, String name) {
        return new AndroidBpModule(type, name, "/proj/sub/Android.bp", 1);
    }

    @Test
    public void moduleNodeLabelShowsTypeNameAndPartition() {
        AndroidBpModule vendor = mod("cc_library", "libfoo");
        vendor.putScalar("vendor", "true");
        BpModuleEntry e = new BpModuleEntry(vendor);
        assertTrue(e.toString().contains("[cc_library] libfoo"));
        assertTrue(e.toString().contains("(vendor)"));

        AndroidBpModule sys = mod("java_library", "BarLib");
        // system 配置はバッジを付けない
        assertEquals("[java_library] BarLib", new BpModuleEntry(sys).toString());
    }

    @Test
    public void testModuleNodeShowsMarker() {
        BpModuleEntry e = new BpModuleEntry(mod("cc_test", "libfoo_test"));
        assertTrue(e.toString().contains("⚙"));
    }

    @Test
    public void fileAndGroupNodeLabels() {
        assertTrue(new BpFileEntry("packages/Car/Android.bp", 3).toString()
                .contains("packages/Car/Android.bp (3)"));
        assertTrue(new SoongGroupEntry(2, 5).toString().contains("5 modules / 2 files"));
    }

    @Test
    public void addSoongModulesBuildsGroupFileModuleHierarchy() {
        ProjectTreePanel panel = new ProjectTreePanel();
        panel.populate(null, new ArrayList<>(), "proj", null, null);
        List<AndroidBpModule> mods = new ArrayList<>();
        AndroidBpModule a = new AndroidBpModule("cc_library", "libA", "/proj/a/Android.bp", 1);
        AndroidBpModule b = new AndroidBpModule("cc_library", "libB", "/proj/a/Android.bp", 8);
        AndroidBpModule c = new AndroidBpModule("java_library", "C", "/proj/b/Android.bp", 1);
        mods.add(a);
        mods.add(b);
        mods.add(c);
        panel.addSoongModules(mods, "/proj");

        DefaultMutableTreeNode root = panel.rootForTest();
        // 最後の子が Soong グループ
        DefaultMutableTreeNode group =
                (DefaultMutableTreeNode) root.getChildAt(root.getChildCount() - 1);
        assertTrue(group.getUserObject() instanceof SoongGroupEntry);
        // 2 ファイル
        assertEquals(2, group.getChildCount());
        DefaultMutableTreeNode file0 = (DefaultMutableTreeNode) group.getChildAt(0);
        BpFileEntry fe = (BpFileEntry) file0.getUserObject();
        assertEquals("a/Android.bp", fe.relPath);
        // a/Android.bp は 2 モジュール
        assertEquals(2, file0.getChildCount());
        assertTrue(((DefaultMutableTreeNode) file0.getChildAt(0)).getUserObject()
                instanceof BpModuleEntry);
    }

    @Test
    public void emptyModulesAddsNothing() {
        ProjectTreePanel panel = new ProjectTreePanel();
        panel.populate(null, new ArrayList<>(), "proj", null, null);
        int before = panel.rootForTest().getChildCount();
        panel.addSoongModules(new ArrayList<>(), "/proj");
        assertEquals(before, panel.rootForTest().getChildCount());
    }

    @Test
    public void openRequestForBpNodesTargetsSoongDiagram() {
        ProjectTreePanel panel = new ProjectTreePanel();
        TreeNodeOpenRequest req = panel.buildOpenRequestForTest(
                new BpModuleEntry(mod("cc_library", "libfoo")));
        assertNotNull(req);
        assertEquals(TreeNodeOpenRequest.Target.SOONG, req.target);
        assertEquals(DiagramKind.SOONG, req.kind);
        assertEquals("KIND:SOONG", req.tabKey());
        assertEquals("Soong", req.displayLabel());

        // bp ファイル / グループからも同じ Soong 図リクエスト
        assertEquals(TreeNodeOpenRequest.Target.SOONG,
                panel.buildOpenRequestForTest(new BpFileEntry("x/Android.bp", 1)).target);
        assertEquals(TreeNodeOpenRequest.Target.SOONG,
                panel.buildOpenRequestForTest(new SoongGroupEntry(1, 1)).target);
        // 非 Soong ノードは null のまま (既存挙動)
        assertNull(panel.buildOpenRequestForTest("plain string node"));
    }
}
