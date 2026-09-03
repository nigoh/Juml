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
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class DiagramControllerTest {

    private DiagramState state;
    private ProjectAnalysisCache cache;
    private EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    private DiagramKindChooser diagramKindChooser;
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
        refreshCount = new AtomicInteger(0);
        lastKind = new AtomicReference<>(DiagramKind.CLASS);
        // Swing コンポーネントの生成・配線は EDT 上で行う (EDT 規律)
        GuiActionRunner.execute(() -> {
            for (DiagramKind k : DiagramKind.values()) {
                diagramItems.put(k, new JRadioButtonMenuItem(k.name()));
            }
            // 図種ドロップダウンは production と同じ構成 (項目・並び) を使う。
            ToolBarBuilder.Callbacks tcb = new ToolBarBuilder.Callbacks();
            tcb.chooseProject = () -> { };
            tcb.chooseAndExport = () -> { };
            tcb.refreshDiagram = () -> { };
            tcb.openEntitySearch = () -> { };
            tcb.selectDiagramKind = k -> { };
            diagramKindChooser =
                    new ToolBarBuilder(DiagramKind.CLASS, tcb).build().diagramKindChooser;
            treePanel = new ProjectTreePanel();
            DiagramControllerDeps deps = new DiagramControllerDeps();
            deps.state = state;
            deps.cacheSupplier = () -> cache;
            deps.diagramItems = diagramItems;
            deps.diagramKindChooser = diagramKindChooser;
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

    /** コンポーネント選択で、種別と単純名がステータスバーに表示される (#37)。 */
    @Test
    public void onTreeComponentSelected_showsNameInStatusBar() {
        juml.core.formats.android.AndroidComponentInfo c =
                new juml.core.formats.android.AndroidComponentInfo(
                        juml.core.formats.android.AndroidComponentInfo.Kind.SERVICE,
                        "com.x.PushService");
        controller.onTreeComponentSelected(c);
        String text = controller.statusLabel.getText();
        assertTrue(text, text.contains("PushService"));
        assertTrue("種別 Service を含むはず: " + text, text.contains("Service"));
    }

    /** Manifest 選択で、パッケージ名がステータスバーに表示される (#37)。 */
    @Test
    public void onTreeManifestSelected_showsPackageInStatusBar() {
        juml.core.formats.android.AndroidManifestInfo m =
                new juml.core.formats.android.AndroidManifestInfo();
        m.setPackageName("com.example.app");
        controller.onTreeManifestSelected(m);
        assertTrue(controller.statusLabel.getText(),
                controller.statusLabel.getText().contains("com.example.app"));
    }

    /** null 選択でも例外なく no-op (ステータス更新なし)。 */
    @Test
    public void onTreeComponentSelected_null_isSafeNoOp() {
        controller.statusLabel.setText("unchanged");
        controller.onTreeComponentSelected(null);
        assertEquals("unchanged", controller.statusLabel.getText());
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

    /** 図種ボタンのラベルが指定図種を示しているか。 */
    private void assertButtonShows(String message, DiagramKind kind) {
        String label = diagramKindChooser.component().getText();
        assertTrue(message + " (ラベル: " + label + ")",
                label.contains(ToolBarBuilder.toolbarLabel(kind)));
    }

    @Test
    public void syncDiagramToggle_updatesButtonLabel() {
        controller.syncDiagramToggle(DiagramKind.SEQUENCE);
        assertButtonShows("図種ボタンのラベルが SEQUENCE になっていない", DiagramKind.SEQUENCE);
    }

    /**
     * ドロップダウンの一覧に項目を持たない図種 (メソッド系・レイアウトの画面/実寸) でも、
     * 現在の図種としてボタンのラベルには出ること。一覧から選べないだけで、
     * アクティブな図種を見失ってはいけない。
     */
    @Test
    public void syncDiagramToggle_hiddenKind_stillUpdatesLabel() {
        assertNull("前提: LAYOUT_RENDER は一覧に項目を持たない",
                diagramKindChooser.itemFor(DiagramKind.LAYOUT_RENDER));
        controller.syncDiagramToggle(DiagramKind.LAYOUT_RENDER);
        assertButtonShows("一覧に無い図種でもラベルは更新されるべき", DiagramKind.LAYOUT_RENDER);
    }

    /** 図種を持たないタブ (自由編集 PlantUML エディタ) では null が渡る。落ちないこと。 */
    @Test
    public void syncDiagramToggle_nullKind_showsPlaceholder() {
        controller.syncDiagramToggle(DiagramKind.PACKAGE);
        controller.syncDiagramToggle(null);
        String label = diagramKindChooser.component().getText();
        assertNotNull("null 図種でもラベルは非 null のはず", label);
        assertFalse("null 図種では直前の図種を示したままにしない (ラベル: " + label + ")",
                label.contains(ToolBarBuilder.toolbarLabel(DiagramKind.PACKAGE)));
    }

    @Test
    public void updateAvailableDiagrams_disablesNonAllowedKinds() {
        controller.updateAvailableDiagrams(EnumSet.of(DiagramKind.CLASS, DiagramKind.PACKAGE));
        // 非表示 (setVisible) ではなく無効化 (setEnabled) する方針。
        assertTrue("許可された CLASS の項目が無効化されている",
                diagramKindChooser.itemFor(DiagramKind.CLASS).isEnabled());
        assertTrue("許可された PACKAGE の項目が無効化されている",
                diagramKindChooser.itemFor(DiagramKind.PACKAGE).isEnabled());
        assertFalse("許可されていない MANIFEST の項目が無効化されていない",
                diagramKindChooser.itemFor(DiagramKind.MANIFEST).isEnabled());
        assertTrue("選べる図種があるのに図種ボタンが無効化されている",
                diagramKindChooser.component().isEnabled());
    }

    @Test
    public void updateAvailableDiagrams_methodKinds_disableStructuralItems() {
        controller.updateAvailableDiagrams(ToolBarBuilder.DIAGRAMS_METHOD);
        assertFalse("メソッド図種セットでは CLASS の項目が無効化されていない",
                diagramKindChooser.itemFor(DiagramKind.CLASS).isEnabled());
    }

    /** プロジェクト未ロード (空集合) では図種ボタンごと押せなくすること。 */
    @Test
    public void updateAvailableDiagrams_emptySet_disablesChooserButton() {
        controller.updateAvailableDiagrams(EnumSet.noneOf(DiagramKind.class));
        assertFalse("未ロード時は図種ボタンが無効化されるべき",
                diagramKindChooser.component().isEnabled());
        controller.updateAvailableDiagrams(EnumSet.of(DiagramKind.CLASS));
        assertTrue("ロード後は図種ボタンが有効に戻るべき",
                diagramKindChooser.component().isEnabled());
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
    public void reflectKindInToolbar_selectsMenuAndUpdatesButton() {
        controller.reflectKindInToolbar(DiagramKind.PACKAGE);
        assertTrue("reflectKindInToolbar 後 PACKAGE メニュー項目が選択されていない",
                diagramItems.get(DiagramKind.PACKAGE).isSelected());
        assertButtonShows("reflectKindInToolbar 後 図種ボタンが PACKAGE になっていない",
                DiagramKind.PACKAGE);
    }

    @Test
    public void selectDiagramKind_reflectsKindInToolbar() {
        // tabPane 未配線: 図種選択はツールバー/メニュー反映までを行う (タブ生成は production のみ)。
        controller.selectDiagramKind(DiagramKind.PACKAGE);
        assertEquals(DiagramKind.PACKAGE, controller.currentKind);
        assertTrue("selectDiagramKind 後 PACKAGE メニュー項目が選択されていない",
                diagramItems.get(DiagramKind.PACKAGE).isSelected());
        assertButtonShows("selectDiagramKind 後 図種ボタンが PACKAGE になっていない",
                DiagramKind.PACKAGE);
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

    /**
     * treePanel にデモクラスを populate して内部 JTree を返す。
     *
     * <p>populate() は Swing コンポーネントを変更するため EDT 上で実行する。
     * tree フィールドの読み取りは populate() 完了後に行えば EDT 外でも安全
     * (参照の読み取りのみ)。</p>
     */
    private JTree populatedTree() throws Exception {
        List<JavaClassInfo> classes = demoClasses();
        // populate() は EDT 上で実行しなければならない (Swing のツリーモデル更新)
        GuiActionRunner.execute(() -> {
            treePanel.populate(new AndroidProjectAnalysis(), classes, "Demo", null);
            return null;
        });
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
        // syncToFocusedTab() は内部でツリー選択状態を変更するため EDT 上で実行する
        GuiActionRunner.execute(() -> controller.syncToFocusedTab(TreeNodeOpenRequest.classNode(foo)));
        // getSelectionPath() は Swing コンポーネントの読み取りのため EDT 上で実行する
        TreePath sel = GuiActionRunner.execute(() -> tree.getSelectionPath());
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
        GuiActionRunner.execute(() -> controller.syncToFocusedTab(
                TreeNodeOpenRequest.method(foo, bar, DiagramKind.SEQUENCE)));
        TreePath sel = GuiActionRunner.execute(() -> tree.getSelectionPath());
        assertNotNull("method tab should highlight a tree node", sel);
        assertTrue("expected bar method node, got " + sel.getLastPathComponent(),
                String.valueOf(sel.getLastPathComponent()).contains("bar"));
    }

    @Test
    public void syncToFocusedTab_package_highlightsPackageNode() throws Exception {
        JTree tree = populatedTree();
        GuiActionRunner.execute(() -> controller.syncToFocusedTab(
                TreeNodeOpenRequest.pkg("com.demo")));
        TreePath sel = GuiActionRunner.execute(() -> tree.getSelectionPath());
        assertNotNull("package tab should highlight a tree node", sel);
        assertTrue("expected com.demo package node, got " + sel.getLastPathComponent(),
                String.valueOf(sel.getLastPathComponent()).contains("com.demo"));
    }

    @Test
    public void syncToFocusedTab_null_isNoOp() throws Exception {
        JTree tree = populatedTree();
        GuiActionRunner.execute(() -> controller.syncToFocusedTab(null));
        TreePath sel = GuiActionRunner.execute(() -> tree.getSelectionPath());
        assertNull(sel);
    }

    @Test
    public void syncToFocusedTab_doesNotTriggerHomeRefresh() throws Exception {
        populatedTree();
        int before = refreshCount.get();
        JavaClassInfo foo = find(demoClasses(), "Foo");
        GuiActionRunner.execute(() -> controller.syncToFocusedTab(
                TreeNodeOpenRequest.classNode(foo)));
        // ツリーハイライトは suppressNotify なので Home の再描画を誘発しない
        assertEquals(before, refreshCount.get());
    }


    /** bug-hunt R1 で発見: ユーティリティタブへ移っても図種ドロップダウンが直前の図種のままだった。 */
    @Test
    public void onTabFocused_null_showsPlaceholderInChooser() {
        DiagramKind before = controller.currentKind;
        GuiActionRunner.execute(() -> {
            controller.reflectKindInToolbar(DiagramKind.PACKAGE);
            controller.onTabFocused(null);
        });
        String label = diagramKindChooser.component().getText();
        assertFalse("ユーティリティタブ選択時は直前の図種を残さない (ラベル: " + label + ")",
                label.contains(ToolBarBuilder.toolbarLabel(DiagramKind.PACKAGE)));
        assertEquals("currentKind (最後の図種) は保持する", before, controller.currentKind);
    }

    /** bug-hunt R1 で発見: 図種を持たない自由編集エディタタブでも直前の図種表示が残っていた。 */
    @Test
    public void onTabFocused_editorTabWithoutKind_showsPlaceholderInChooser() {
        GuiActionRunner.execute(() -> {
            controller.reflectKindInToolbar(DiagramKind.PACKAGE);
            controller.onTabFocused(new DiagramTabPane.FocusedTab(null, null));
        });
        String label = diagramKindChooser.component().getText();
        assertFalse("エディタタブでは直前の図種を残さない (ラベル: " + label + ")",
                label.contains(ToolBarBuilder.toolbarLabel(DiagramKind.PACKAGE)));
    }

    /** bug-hunt R1 で発見: コマンドパレット経路が無効化済みの図種をすり抜けて空図タブを開いていた。 */
    @Test
    public void selectDiagramKind_notAllowed_isRejectedWithStatusMessage() {
        controller.updateAvailableDiagrams(EnumSet.of(DiagramKind.CLASS));
        DiagramKind before = controller.currentKind;
        GuiActionRunner.execute(() -> controller.selectDiagramKind(DiagramKind.SOONG));
        assertEquals("無効化された図種では currentKind を変えない", before, controller.currentKind);
        String label = diagramKindChooser.component().getText();
        assertFalse("無効化された図種をドロップダウンに表示しない (ラベル: " + label + ")",
                label.contains(ToolBarBuilder.toolbarLabel(DiagramKind.SOONG)));
        String status = controller.statusLabel.getText();
        assertTrue("案内メッセージに図種名を含む: " + status,
                status.contains(DiagramKind.SOONG.getDisplayName()));
    }

    @Test
    public void selectDiagramKind_allowed_stillSwitchesKind() {
        controller.updateAvailableDiagrams(EnumSet.of(DiagramKind.CLASS, DiagramKind.PACKAGE));
        GuiActionRunner.execute(() -> controller.selectDiagramKind(DiagramKind.PACKAGE));
        assertEquals(DiagramKind.PACKAGE, controller.currentKind);
        String label = diagramKindChooser.component().getText();
        assertTrue(label, label.contains(ToolBarBuilder.toolbarLabel(DiagramKind.PACKAGE)));
    }
}
