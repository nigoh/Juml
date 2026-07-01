// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Before;
import org.junit.Test;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.JavaStructureExtractor;

import javax.swing.JLabel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DiagramControllerTest {

    private DiagramState state;
    private ProjectAnalysisCache cache;
    private EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    private EnumMap<DiagramKind, JToggleButton> diagramToggles;
    private AtomicInteger refreshCount;
    private AtomicReference<DiagramKind> lastKind;
    private ProjectTreePanel treePanel;
    private DiagramController controller;

    @Before
    public void setUp() {
        // 非 Swing オブジェクトはテストスレッドで生成して OK
        state = new DiagramState();
        cache = new ProjectAnalysisCache();
        diagramItems = new EnumMap<>(DiagramKind.class);
        diagramToggles = new EnumMap<>(DiagramKind.class);
        refreshCount = new AtomicInteger(0);
        lastKind = new AtomicReference<>(DiagramKind.CLASS);
        // Swing コンポーネントの生成・配線は EDT 上で行う (EDT 規律)
        GuiActionRunner.execute(() -> {
            for (DiagramKind k : DiagramKind.values()) {
                diagramItems.put(k, new JRadioButtonMenuItem(k.name()));
                diagramToggles.put(k, new JToggleButton(k.name()));
            }
            treePanel = new ProjectTreePanel();
            DiagramControllerDeps deps = new DiagramControllerDeps();
            deps.state = state;
            deps.cacheSupplier = () -> cache;
            deps.diagramItems = diagramItems;
            deps.diagramToggles = diagramToggles;
            deps.treePanel = treePanel;
            deps.mainTabs = new JTabbedPane();
            deps.tabPane = null;
            deps.statusLabel = new JLabel();
            deps.parentFrame = null;
            deps.refreshDiagram = () -> refreshCount.incrementAndGet();
            deps.onKindChanged = kind -> lastKind.set(kind);
            controller = new DiagramController(deps);
            return null;
        });
    }

    @Test
    public void onTreeMethodSelected_withoutTabPane_isSafeNoOp() {
        // タブ中心モデル: ツリー選択はタブを開く操作 (tabPane へ委譲) であり、
        // グローバル状態 (sequenceEntry / currentKind) を変更しない。tabPane 未配線では no-op。
        JavaClassInfo cls = new JavaClassInfo();
        cls.setSimpleName("Foo");
        JavaMethodInfo method = new JavaMethodInfo();
        method.setName("bar");
        controller.onTreeMethodSelected(new MethodSelection(cls, method));
        assertNull(state.sequenceEntry);
        assertEquals(DiagramKind.CLASS, controller.currentKind);
    }

    @Test
    public void setAllMethodEntries_setsAllThree() {
        controller.setAllMethodEntries("Bar.baz");
        assertEquals("Bar.baz", state.sequenceEntry);
        assertEquals("Bar.baz", state.activityEntry);
        assertEquals("Bar.baz", state.callGraphEntry);
    }


    @Test
    public void onTreeMethodSelected_null_isNoOp() {
        DiagramKind before = controller.currentKind;
        controller.onTreeMethodSelected(null);
        assertEquals(before, controller.currentKind);
        assertEquals(0, refreshCount.get());
    }

    @Test
    public void buildSequenceRequest_requiresDotSeparatedEntry() {
        DiagramRequest req = controller.buildSequenceRequest("Foo.bar");
        assertNotNull(req);
        assertEquals(DiagramKind.SEQUENCE, req.getKind());
    }

    @Test(expected = IllegalArgumentException.class)
    public void buildSequenceRequest_missingDot_throws() {
        controller.buildSequenceRequest("Foobar");
    }

    @Test
    public void buildActivityRequest_requiresDotSeparatedEntry() {
        DiagramRequest req = controller.buildActivityRequest("Foo.bar");
        assertNotNull(req);
        assertEquals(DiagramKind.ACTIVITY, req.getKind());
    }

    @Test
    public void buildCallGraphRequest_requiresDotSeparatedEntry() {
        DiagramRequest req = controller.buildCallGraphRequest("Foo.bar");
        assertNotNull(req);
        assertEquals(DiagramKind.CALLGRAPH, req.getKind());
    }

    @Test
    public void syncDiagramToggle_selectsToggleButton() {
        diagramToggles.get(DiagramKind.SEQUENCE).setSelected(false);
        controller.syncDiagramToggle(DiagramKind.SEQUENCE);
        assertTrue("SEQUENCE ダイアグラムトグルが選択状態になっていない",
                diagramToggles.get(DiagramKind.SEQUENCE).isSelected());
    }

    @Test
    public void updateAvailableDiagrams_disablesNonAllowedKinds() {
        controller.updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        // 非表示 (setVisible) ではなく無効化 (setEnabled) する方針へ変更。
        assertFalse("メソッド図種セットでは CLASS トグルが無効化されていない",
                diagramToggles.get(DiagramKind.CLASS).isEnabled());
        assertTrue("メソッド図種セットでは SEQUENCE トグルが有効になっていない",
                diagramToggles.get(DiagramKind.SEQUENCE).isEnabled());
        assertTrue("メソッド図種セットでは ACTIVITY トグルが有効になっていない",
                diagramToggles.get(DiagramKind.ACTIVITY).isEnabled());
    }

    @Test
    public void entryMissingFor_trueWhenUnsetFalseWhenSet() {
        assertTrue("起点未設定なら SEQUENCE は entryMissing=true のはず",
                controller.entryMissingFor(DiagramKind.SEQUENCE));
        assertTrue("起点未設定なら ACTIVITY は entryMissing=true のはず",
                controller.entryMissingFor(DiagramKind.ACTIVITY));
        assertTrue("起点未設定なら CALLGRAPH は entryMissing=true のはず",
                controller.entryMissingFor(DiagramKind.CALLGRAPH));
        assertTrue("起点未設定なら LAYOUT は entryMissing=true のはず",
                controller.entryMissingFor(DiagramKind.LAYOUT));
        assertTrue("起点未設定なら NAVIGATION は entryMissing=true のはず",
                controller.entryMissingFor(DiagramKind.NAVIGATION));
        // 起点不要の図種は常に false
        assertFalse("CLASS は起点不要なので entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.CLASS));
        assertFalse("PACKAGE は起点不要なので entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.PACKAGE));
        assertFalse("MANIFEST は起点不要なので entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.MANIFEST));

        state.sequenceEntry = "Foo.bar";
        state.activityEntry = "Foo.bar";
        state.callGraphEntry = "Foo.bar";
        state.currentLayoutKey = "m::main::::a.xml";
        state.currentNavigationKey = "m::main::nav.xml";
        assertFalse("sequenceEntry 設定後は SEQUENCE の entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.SEQUENCE));
        assertFalse("activityEntry 設定後は ACTIVITY の entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.ACTIVITY));
        assertFalse("callGraphEntry 設定後は CALLGRAPH の entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.CALLGRAPH));
        assertFalse("currentLayoutKey 設定後は LAYOUT の entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.LAYOUT));
        assertFalse("currentNavigationKey 設定後は NAVIGATION の entryMissing=false のはず",
                controller.entryMissingFor(DiagramKind.NAVIGATION));
    }

    @Test
    public void reflectKindInToolbar_selectsMenuAndToggle() {
        controller.reflectKindInToolbar(DiagramKind.PACKAGE);
        assertTrue("reflectKindInToolbar 後 PACKAGE メニュー項目が選択されていない",
                diagramItems.get(DiagramKind.PACKAGE).isSelected());
        assertTrue("reflectKindInToolbar 後 PACKAGE トグルボタンが選択されていない",
                diagramToggles.get(DiagramKind.PACKAGE).isSelected());
    }

    @Test
    public void selectDiagramKind_reflectsKindInToolbar() {
        // tabPane 未配線: 図種選択はツールバー/メニュー反映までを行う (タブ生成は production のみ)。
        controller.selectDiagramKind(DiagramKind.PACKAGE);
        assertEquals(DiagramKind.PACKAGE, controller.currentKind);
        assertTrue("selectDiagramKind 後 PACKAGE メニュー項目が選択されていない",
                diagramItems.get(DiagramKind.PACKAGE).isSelected());
        assertTrue("selectDiagramKind 後 PACKAGE トグルボタンが選択されていない",
                diagramToggles.get(DiagramKind.PACKAGE).isSelected());
    }

    @Test
    public void selectDiagramKind_entryKind_reflectsKindInToolbar() {
        controller.selectDiagramKind(DiagramKind.SEQUENCE);
        assertEquals(DiagramKind.SEQUENCE, controller.currentKind);
        assertTrue("selectDiagramKind(SEQUENCE) 後 SEQUENCE メニュー項目が選択されていない",
                diagramItems.get(DiagramKind.SEQUENCE).isSelected());
    }

    // --- 動的タブ ↔ ツリー連動 (syncToFocusedTab) ---

    private static List<JavaClassInfo> demoClasses() {
        List<JavaClassInfo> classes = new ArrayList<>();
        classes.addAll(JavaStructureExtractor.extract(
                "package com.demo; public class Foo { public void bar() {} }"));
        classes.addAll(JavaStructureExtractor.extract(
                "package com.demo; public class Baz {}"));
        return classes;
    }

    private JTree populatedTree() throws Exception {
        List<JavaClassInfo> classes = demoClasses();
        treePanel.populate(new AndroidProjectAnalysis(), classes, "Demo", null);
        Field f = ProjectTreePanel.class.getDeclaredField("tree");
        f.setAccessible(true);
        return (JTree) f.get(treePanel);
    }

    private static JavaClassInfo find(List<JavaClassInfo> cs, String simple) {
        for (JavaClassInfo c : cs) {
            if (simple.equals(c.getSimpleName())) {
                return c;
            }
        }
        throw new IllegalStateException("no class " + simple);
    }

    @Test
    public void syncToFocusedTab_class_highlightsClassNode() throws Exception {
        JTree tree = populatedTree();
        JavaClassInfo foo = find(demoClasses(), "Foo");
        controller.syncToFocusedTab(TreeNodeOpenRequest.classNode(foo));
        TreePath sel = tree.getSelectionPath();
        assertNotNull("class tab should highlight a tree node", sel);
        assertTrue("expected Foo class node, got " + sel.getLastPathComponent(),
                String.valueOf(sel.getLastPathComponent()).contains("Foo"));
    }

    @Test
    public void syncToFocusedTab_method_highlightsMethodNode() throws Exception {
        JTree tree = populatedTree();
        List<JavaClassInfo> cs = demoClasses();
        JavaClassInfo foo = find(cs, "Foo");
        JavaMethodInfo bar = foo.getMethods().get(0);
        controller.syncToFocusedTab(
                TreeNodeOpenRequest.method(foo, bar, DiagramKind.SEQUENCE));
        TreePath sel = tree.getSelectionPath();
        assertNotNull("method tab should highlight a tree node", sel);
        assertTrue("expected bar method node, got " + sel.getLastPathComponent(),
                String.valueOf(sel.getLastPathComponent()).contains("bar"));
    }

    @Test
    public void syncToFocusedTab_package_highlightsPackageNode() throws Exception {
        JTree tree = populatedTree();
        controller.syncToFocusedTab(TreeNodeOpenRequest.pkg("com.demo"));
        TreePath sel = tree.getSelectionPath();
        assertNotNull("package tab should highlight a tree node", sel);
        assertTrue("expected com.demo package node, got " + sel.getLastPathComponent(),
                String.valueOf(sel.getLastPathComponent()).contains("com.demo"));
    }

    @Test
    public void syncToFocusedTab_null_isNoOp() throws Exception {
        JTree tree = populatedTree();
        controller.syncToFocusedTab(null);
        assertNull(tree.getSelectionPath());
    }

    @Test
    public void syncToFocusedTab_doesNotTriggerHomeRefresh() throws Exception {
        populatedTree();
        int before = refreshCount.get();
        JavaClassInfo foo = find(demoClasses(), "Foo");
        controller.syncToFocusedTab(TreeNodeOpenRequest.classNode(foo));
        // ツリーハイライトは suppressNotify なので Home の再描画を誘発しない
        assertEquals(before, refreshCount.get());
    }
}
