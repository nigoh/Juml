// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.util.Messages;

import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JToggleButton;
import javax.swing.JTabbedPane;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 図種の切り替え・ツリー選択ハンドラ・ダイアログ操作など図制御ロジックを集約するコントローラ。
 *
 * <p>VS Code 風タブ中心モデル: すべての図は対等な「タブ (= エディタ)」として
 * {@link DiagramTabPane} が管理する。ツリー選択・ツールバー・メニュー操作は
 * 「アクティブタブ」を起点に動く。共有 previewPanel の「Home ビュー」は存在しない。</p>
 *
 * <p>UI の構築は行わず、状態遷移と UI 同期のみを担当する。</p>
 */
public final class DiagramController {

    // package-private: 補助クラス DiagramEntryDialogs が参照する。
    final DiagramState state;
    private final Supplier<ProjectAnalysisCache> cacheSupplier;
    final EnumMap<DiagramKind, JRadioButtonMenuItem> diagramItems;
    private final EnumMap<DiagramKind, JToggleButton> diagramToggles;
    private final ProjectTreePanel treePanel;
    private final JTabbedPane mainTabs;
    private final DiagramTabPane tabPane;
    final javax.swing.JLabel statusLabel;
    final java.awt.Frame parentFrame;
    final Runnable refreshDiagram;
    private final Consumer<DiagramKind> onKindChanged;
    private final DiagramEntryDialogs entryDialogs;

    /** package-private — UmlMainFrame がミラー同期するために読む (アクティブタブの図種)。 */
    DiagramKind currentKind = DiagramKind.CLASS;

    public DiagramController(DiagramControllerDeps deps) {
        this.state = deps.state;
        this.cacheSupplier = deps.cacheSupplier;
        this.diagramItems = deps.diagramItems;
        this.diagramToggles = deps.diagramToggles;
        this.treePanel = deps.treePanel;
        this.mainTabs = deps.mainTabs;
        this.tabPane = deps.tabPane;
        this.statusLabel = deps.statusLabel;
        this.parentFrame = deps.parentFrame;
        this.refreshDiagram = deps.refreshDiagram;
        this.onKindChanged = deps.onKindChanged;
        this.entryDialogs = new DiagramEntryDialogs(this);
    }

    ProjectAnalysisCache cache() {
        return cacheSupplier.get();
    }

    /** currentKind への書き込みはすべてこのメソッド経由で行い、呼び出し元へ通知する。 */
    void setCurrentKind(DiagramKind kind) {
        currentKind = kind;
        onKindChanged.accept(kind);
    }

    // -------------------------------------------------------------------------
    // ツリー選択ハンドラ — ノードを対応するダイアグラムタブとして開く/フォーカスする
    // -------------------------------------------------------------------------

    public void onTreePackageSelected(String pkg) {
        if (pkg == null || pkg.isEmpty() || "(default)".equals(pkg) || tabPane == null) {
            return;
        }
        tabPane.addOrFocusTab(TreeNodeOpenRequest.pkg(pkg));
    }

    public void onTreeClassSelected(JavaClassInfo cls) {
        if (cls == null || tabPane == null) {
            return;
        }
        String fqn = cls.getQualifiedName();
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        tabPane.addOrFocusTab(TreeNodeOpenRequest.classNode(cls));
    }

    public void onTreeModuleSelected(String module) {
        if (module == null || module.isEmpty() || "(other)".equals(module) || tabPane == null) {
            return;
        }
        tabPane.addOrFocusTab(TreeNodeOpenRequest.module(module));
    }

    /**
     * 左ペインのツリーでメソッドが選択されたら、そのメソッドのシーケンス図タブを開く。
     */
    public void onTreeMethodSelected(MethodSelection sel) {
        if (sel == null || tabPane == null) {
            return;
        }
        tabPane.addOrFocusTab(
                TreeNodeOpenRequest.method(sel.getOwner(), sel.getMethod(), DiagramKind.SEQUENCE));
    }

    /**
     * 後方互換: アクティビティ図リーフ選択ハンドラ。当該メソッドのアクティビティ図タブを開く。
     */
    public void onTreeActivityMethodSelected(MethodSelection sel) {
        if (sel == null || tabPane == null) {
            return;
        }
        tabPane.addOrFocusTab(
                TreeNodeOpenRequest.method(sel.getOwner(), sel.getMethod(), DiagramKind.ACTIVITY));
    }

    public void onTreeManifestSelected(juml.core.formats.android.AndroidManifestInfo m) {
        openManifestDiagram();
    }

    public void onTreeComponentSelected(
            juml.core.formats.android.AndroidComponentInfo c) {
        openManifestDiagram();
    }

    /** 3 エントリを同じメソッドに揃える (DiagramState のヘルパへ委譲)。 */
    public void setAllMethodEntries(String entry) {
        state.setAllMethodEntries(entry);
    }

    /**
     * 左ペインで中クリック / ダブルクリックされたノードをタブとして開くハンドラ。
     */
    public void onTreeOpenInNewTab(TreeNodeOpenRequest req) {
        if (req == null || tabPane == null) {
            return;
        }
        tabPane.addOrFocusTab(req);
    }

    /**
     * 左ペインで「Open source」されたノードのタブを開き、実ソース表示を前面に出すハンドラ。
     */
    public void onTreeOpenSource(TreeNodeOpenRequest req) {
        if (req == null || tabPane == null) {
            return;
        }
        tabPane.openSourceForRequest(req);
    }

    // -------------------------------------------------------------------------
    // タブを開く補助 (図種・スコープごと)
    // -------------------------------------------------------------------------

    /** プロジェクト全体を対象とする図種 (Class/Common/Inheritance/Package/Module 等) をタブで開く。 */
    void openProjectWide(DiagramKind kind) {
        if (tabPane == null) {
            return;
        }
        // 大規模プロジェクトで全体 Class/Inheritance 図は巨大化・描画失敗しやすい。
        // 受動的な警告で終わらせず、開く前に「スコープを絞る」一手を提示して
        // ユーザーが行き止まらないようにする (描画失敗→対処探し、を未然に防ぐ)。
        DiagramScope scope = null;
        if (cache().isLoaded()
                && isWholeProjectDiagramLarge(kind, cache().getClasses().size())) {
            int n = cache().getClasses().size();
            String[] options = {
                    Messages.get("dlg.largeDiagram.chooseScope"),
                    Messages.get("dlg.largeDiagram.renderAnyway"),
                    Messages.get("dlg.cancel")};
            int choice = JOptionPane.showOptionDialog(parentFrame,
                    java.text.MessageFormat.format(
                            Messages.get("dlg.largeDiagram.message"),
                            n, ToolBarBuilder.toolbarLabel(kind)),
                    Messages.get("dlg.largeDiagram.title"),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options[0]);
            if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                statusLabel.setText(Messages.get("dlg.largeDiagram.cancelledPickScope"));
                return;
            }
            if (choice == 0) {
                scope = promptForScope();
                if (scope == null) {
                    // スコープ未選択 (ダイアログをキャンセル) なら全体図は開かず中断。
                    statusLabel.setText(Messages.get("dlg.largeDiagram.cancelledNoScope"));
                    return;
                }
            }
            // choice == 1 (Render anyway): scope は null のまま全体図を描画。
        }
        boolean links = kind == DiagramKind.CLASS || kind == DiagramKind.INHERITANCE;
        DiagramRequest spec = new DiagramRequest(kind, null, null, true, scope, links);
        // スコープ内容を署名化してキーに含める。固定の ":scoped" だと別スコープでも
        // タブが衝突し、スコープ変更が反映されない (同一スコープは引き続き 1 タブに集約)。
        String key = "KIND:" + kind.name() + scopeKey(scope);
        tabPane.openDiagram(key,
                ToolBarBuilder.toolbarLabel(kind), iconForKind(kind), spec, null);
    }

    /** スコープ内容から決定的なタブキー接尾辞を作る (null や全体図は空文字)。 */
    private static String scopeKey(DiagramScope scope) {
        if (scope == null) {
            return "";
        }
        String sig = String.valueOf(scope.getIncludedPackages())
                + '|' + scope.getExcludedPackages()
                + '|' + scope.getIncludedModules()
                + '|' + (scope.getClassNameRegex() != null
                        ? scope.getClassNameRegex().pattern() : "")
                + '|' + scope.getMaxClasses()
                + '|' + scope.getNeighborHops()
                + '|' + scope.getRelationKinds()
                + '|' + scope.getVisibilityFilter()
                + '|' + scope.isExcludeExternalLibraries()
                + '|' + scope.getParseMode();
        return ":" + Integer.toHexString(sig.hashCode());
    }

    /** これを超えるクラス数の全体図 (Class/Inheritance) は描画コストが高く事前ガードする。 */
    private static final int LARGE_PROJECT_CLASSES = 40;

    /**
     * 全体図を開く前に大規模ガードを出すべきか判定する (純粋関数; UI なし)。
     * Class / Inheritance の全体図のみが対象で、クラス数が閾値を超えたら true。
     */
    static boolean isWholeProjectDiagramLarge(DiagramKind kind, int classCount) {
        return (kind == DiagramKind.CLASS || kind == DiagramKind.INHERITANCE)
                && classCount > LARGE_PROJECT_CLASSES;
    }

    /**
     * Scope ダイアログを表示し、ユーザーが選んだスコープを返す (アクティブタブには適用しない)。
     * キャンセル / 空スコープ選択時は null。
     */
    private DiagramScope promptForScope() {
        if (!cache().isLoaded()) {
            return null;
        }
        java.util.Set<String> packages = new java.util.TreeSet<>();
        java.util.Set<String> modules =
                new java.util.TreeSet<>(cache().getClassToModule().values());
        for (JavaClassInfo c : cache().getClasses()) {
            String p = c.getPackageName();
            if (p != null && !p.isEmpty()) {
                packages.add(p);
            }
        }
        DiagramScopeDialog dlg = new DiagramScopeDialog(parentFrame,
                List.copyOf(packages), List.copyOf(modules), state.currentScope);
        dlg.setVisible(true);
        DiagramScope picked = dlg.getResult();
        return (picked == null || picked.isEmpty()) ? null : picked;
    }

    /** Manifest 図をタブで開く。 */
    void openManifestDiagram() {
        if (tabPane == null) {
            return;
        }
        tabPane.openDiagram("KIND:MANIFEST", "Manifest", TreeNodeIcon.MANIFEST,
                new DiagramRequest(DiagramKind.MANIFEST), null);
    }

    /** Soong (Android.bp) 依存図をタブで開く / 既存タブにフォーカスする。 */
    void openSoongDiagram() {
        if (tabPane == null) {
            return;
        }
        tabPane.openDiagram("KIND:SOONG", "Soong", TreeNodeIcon.MODULE,
                new DiagramRequest(DiagramKind.SOONG), null);
    }

    /** {@code Class.method} 起点の Sequence/Activity/CallGraph 図をタブで開く。 */
    void openEntryDiagram(String entry, DiagramKind kind) {
        if (tabPane == null || entry == null) {
            return;
        }
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            return;
        }
        String simple = entry.substring(0, dot);
        String method = entry.substring(dot + 1);
        JavaClassInfo ci = findClassBySimpleName(simple);
        if (ci == null) {
            ci = new JavaClassInfo();
            ci.setSimpleName(simple);
        }
        JavaMethodInfo mi = new JavaMethodInfo();
        mi.setName(method);
        tabPane.addOrFocusTab(TreeNodeOpenRequest.method(ci, mi, kind));
    }

    void openLayoutDiagram(String layoutKey) {
        if (tabPane == null || layoutKey == null) {
            return;
        }
        tabPane.openDiagram("LAYOUT:" + layoutKey, shortKeyLabel(layoutKey),
                TreeNodeIcon.COMPONENT_GROUP, DiagramRequest.forLayout(layoutKey, true), null);
    }

    void openLayoutScreenDiagram(String layoutKey) {
        if (tabPane == null || layoutKey == null) {
            return;
        }
        tabPane.openDiagram("LAYOUT_SCREEN:" + layoutKey,
                shortKeyLabel(layoutKey) + " (screen)",
                TreeNodeIcon.COMPONENT_GROUP,
                DiagramRequest.forLayoutScreen(layoutKey, true), null);
    }

    void openLayoutRenderDiagram(String layoutKey) {
        if (tabPane == null || layoutKey == null) {
            return;
        }
        tabPane.openDiagram("LAYOUT_RENDER:" + layoutKey,
                shortKeyLabel(layoutKey) + " (render)",
                TreeNodeIcon.COMPONENT_GROUP,
                DiagramRequest.forLayoutRender(layoutKey, true), null);
    }

    void openNavigationDiagram(String navKey) {
        if (tabPane == null || navKey == null) {
            return;
        }
        tabPane.openDiagram("NAV:" + navKey, shortKeyLabel(navKey),
                TreeNodeIcon.COMPONENT_GROUP,
                DiagramRequest.forNavigationGraph(navKey, true), null);
    }

    /**
     * プロジェクトロード後に開く既定タブ。Common 図は参照関係が薄いプロジェクトで
     * 空になりがちなため、構造が一目で分かる Package 概要図を既定とする。
     */
    public void openDefaultDiagram() {
        openProjectWide(DiagramKind.PACKAGE);
    }

    private JavaClassInfo findClassBySimpleName(String simple) {
        if (simple == null || !cache().isLoaded()) {
            return null;
        }
        for (JavaClassInfo c : cache().getClasses()) {
            if (simple.equals(c.getSimpleName())) {
                return c;
            }
        }
        return null;
    }

    private static TreeNodeIcon iconForKind(DiagramKind kind) {
        switch (kind) {
            case PACKAGE:   return TreeNodeIcon.PACKAGE;
            case CYCLES:    return TreeNodeIcon.PACKAGE;
            case MODULE:    return TreeNodeIcon.MODULE;
            case MANIFEST:  return TreeNodeIcon.MANIFEST;
            case SEQUENCE:  return TreeNodeIcon.SEQUENCE;
            case ACTIVITY:  return TreeNodeIcon.ACTIVITY;
            case CALLGRAPH: return TreeNodeIcon.METHOD;
            case COMPONENT: return TreeNodeIcon.COMPONENT_GROUP;
            case SOONG:     return TreeNodeIcon.MODULE;
            case BUILD_NINJA:   return TreeNodeIcon.MODULE;
            case INTERMEDIATES: return TreeNodeIcon.MODULE;
            default:        return TreeNodeIcon.CLASS;
        }
    }

    private static String shortKeyLabel(String key) {
        if (key == null) {
            return "";
        }
        int sep = key.lastIndexOf("::");
        return sep >= 0 ? key.substring(sep + 2) : key;
    }

    // -------------------------------------------------------------------------
    // アクティブタブへの操作 (スコープ・プリセット・participant フィルタ・再描画)
    // -------------------------------------------------------------------------

    /**
     * 共有 {@link DiagramState} の現在値からアクティブタブの {@link DiagramRequest} を
     * 再構築し、再描画する。スコープ/プリセット/participant フィルタ変更後に呼ぶ。
     */
    public void applyStateToActiveTab() {
        if (tabPane == null || !tabPane.hasActiveTab()) {
            return;
        }
        DiagramKind k = tabPane.activeTabKind();
        DiagramRequest spec = buildSpecForKind(k);
        if (spec != null) {
            tabPane.setActiveTabSpecAndRender(spec);
        }
    }

    /** アクティブタブの図種と共有状態から {@link DiagramRequest} を組み立てる。 */
    private DiagramRequest buildSpecForKind(DiagramKind k) {
        if (k == null) {
            return null;
        }
        switch (k) {
            case SEQUENCE:
                return isBlank(state.sequenceEntry) ? null : buildSequenceRequest(state.sequenceEntry);
            case ACTIVITY:
                return isBlank(state.activityEntry) ? null : buildActivityRequest(state.activityEntry);
            case CALLGRAPH:
                return isBlank(state.callGraphEntry) ? null : buildCallGraphRequest(state.callGraphEntry);
            case LAYOUT:
                return isBlank(state.currentLayoutKey) ? null
                        : DiagramRequest.forLayout(state.currentLayoutKey, true);
            case LAYOUT_SCREEN:
                return isBlank(state.currentLayoutKey) ? null
                        : DiagramRequest.forLayoutScreen(state.currentLayoutKey, true);
            case LAYOUT_RENDER:
                return isBlank(state.currentLayoutKey) ? null
                        : DiagramRequest.forLayoutRender(state.currentLayoutKey, true);
            case NAVIGATION:
                return isBlank(state.currentNavigationKey) ? null
                        : DiagramRequest.forNavigationGraph(state.currentNavigationKey, true);
            default:
                boolean links = k == DiagramKind.CLASS || k == DiagramKind.INHERITANCE;
                return new DiagramRequest(k, null, null, true, state.currentScope, links);
        }
    }

    public void openScopeDialog() {
        if (!cache().isLoaded()) {
            JOptionPane.showMessageDialog(parentFrame,
                    Messages.get("dlg.noProject.message"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        java.util.Set<String> packages = new java.util.TreeSet<>();
        java.util.Set<String> modules = new java.util.TreeSet<>(cache().getClassToModule().values());
        for (JavaClassInfo c : cache().getClasses()) {
            String p = c.getPackageName();
            if (p != null && !p.isEmpty()) {
                packages.add(p);
            }
        }
        DiagramScopeDialog dlg = new DiagramScopeDialog(parentFrame,
                List.copyOf(packages), List.copyOf(modules), state.currentScope);
        dlg.setVisible(true);
        DiagramScope picked = dlg.getResult();
        if (picked != null) {
            state.currentScope = picked.isEmpty() ? null : picked;
            applyStateToActiveTab();
        }
    }

    // -------------------------------------------------------------------------
    // 図種切替・UI 同期
    // -------------------------------------------------------------------------

    /**
     * 利用可能な図種に応じてツールバーのトグルと Diagram メニューのラジオを有効/無効化する。
     * 非表示 (setVisible) ではなく無効化 (setEnabled) にすることで、ボタンの存在は伝えつつ
     * 「今は到達できない」ことを示す。プロジェクト未ロード時は空集合を渡して全無効化する。
     */
    public void updateAvailableDiagrams(EnumSet<DiagramKind> allowed) {
        for (java.util.Map.Entry<DiagramKind, JToggleButton> e : diagramToggles.entrySet()) {
            e.getValue().setEnabled(allowed.contains(e.getKey()));
        }
        for (java.util.Map.Entry<DiagramKind, JRadioButtonMenuItem> e : diagramItems.entrySet()) {
            e.getValue().setEnabled(allowed.contains(e.getKey()));
        }
    }

    /**
     * 動的タブにフォーカスが移ったときの一括同期: ツリーハイライト + 図種ミラー +
     * ツールバー/メニュー反映。
     */
    public void onTabFocused(DiagramTabPane.FocusedTab info) {
        if (info == null) {
            return;
        }
        syncToFocusedTab(info.treeSync);
        if (info.kind != null) {
            setCurrentKind(info.kind);
            reflectKindInToolbar(info.kind);
        }
    }

    /**
     * 動的ダイアグラムタブの由来ノードを左ツリーでハイライトして連動させる。
     * {@code select*Node} は suppressNotify なので選択コールバックは発火しない。
     */
    public void syncToFocusedTab(TreeNodeOpenRequest req) {
        if (req == null) {
            return;
        }
        switch (req.target) {
            case METHOD:
                if (req.classInfo != null && req.methodInfo != null) {
                    treePanel.selectMethodNode(
                            req.classInfo.getQualifiedName(), req.methodInfo.getName());
                }
                break;
            case CLASS:
                if (req.classInfo != null) {
                    treePanel.selectClassNode(req.classInfo.getQualifiedName());
                }
                break;
            case PACKAGE:
                treePanel.selectPackageNode(req.name);
                break;
            case MODULE:
                treePanel.selectModuleNode(req.name);
                break;
            default:
                break;
        }
    }

    /** メニューラジオ/ツールバートグルの選択を {@code kind} に合わせる (見た目のみ)。 */
    void reflectKindInToolbar(DiagramKind kind) {
        JRadioButtonMenuItem item = diagramItems.get(kind);
        if (item != null) {
            item.setSelected(true);
        }
        syncDiagramToggle(kind);
    }

    /**
     * ツールバー/メニューの図種ボタンが押されたときのハンドラ。
     * VS Code 風: アクティブタブを起点に、選んだ図種をタブとして開く / フォーカスする。
     */
    public void selectDiagramKind(DiagramKind kind) {
        // VS Code 風: 図種切替は「いまフォーカスしているタブの題材」に作用する。
        // メソッドタブなら同じ Class.method の別図種、クラス/パッケージ/モジュールの
        // スコープ付きタブなら同じ題材のクラス図を開き直す。これにより単体クラスを
        // 見ている最中に Class を選んでもプロジェクト全体図 (大規模ガード) へ
        // 飛ばされ「図が大きい」と誤警告される不具合を防ぐ。
        if (tabPane != null && tabPane.dynamicTabFocused()) {
            TreeNodeOpenRequest reopen = reopenRequestFor(tabPane.focusedTabRequest(), kind);
            if (reopen != null) {
                tabPane.addOrFocusTab(reopen);
                return;
            }
        }
        // 図種選択をツールバー/メニューへ反映 (テスト・未ロード時はここまで)。
        setCurrentKind(kind);
        reflectKindInToolbar(kind);
        if (tabPane == null) {
            return;
        }
        openKindAsTab(kind);
        // タブを開かなかった場合 (ダイアログキャンセル等) はアクティブタブの図種へ戻す。
        if (tabPane.hasActiveTab()) {
            reflectKindInToolbar(tabPane.activeTabKind());
        }
    }

    /**
     * フォーカス中の動的タブの題材 (メソッド/クラス/パッケージ/モジュール) を保ったまま
     * {@code kind} の図を開き直すための {@link TreeNodeOpenRequest} を返す。
     * 題材と図種が噛み合わず「題材を引き継いで開き直す」べきでない場合は {@code null}。
     *
     * <p>純粋関数 (UI 副作用なし)。題材スコープを持つタブから構造系の図種を選んだとき、
     * プロジェクト全体図へフォールバックして大規模ガード (「図が大きい」警告) を
     * 出してしまうのを防ぐ。{@code null} のときは呼び出し側が従来どおり
     * {@link #openKindAsTab} へ委譲し、全体図 / ダイアログ誘導を行う。</p>
     */
    static TreeNodeOpenRequest reopenRequestFor(TreeNodeOpenRequest focused, DiagramKind kind) {
        if (focused == null) {
            return null;
        }
        switch (focused.target) {
            case METHOD:
                return (focused.classInfo != null && focused.methodInfo != null
                        && ToolBarBuilder.DIAGRAMS_METHOD.contains(kind))
                        ? TreeNodeOpenRequest.method(focused.classInfo, focused.methodInfo, kind)
                        : null;
            case CLASS:
                return (kind == DiagramKind.CLASS && focused.classInfo != null)
                        ? TreeNodeOpenRequest.classNode(focused.classInfo) : null;
            case PACKAGE:
                return (kind == DiagramKind.CLASS && focused.name != null)
                        ? TreeNodeOpenRequest.pkg(focused.name) : null;
            case MODULE:
                return (kind == DiagramKind.CLASS && focused.name != null)
                        ? TreeNodeOpenRequest.module(focused.name) : null;
            default:
                return null;
        }
    }

    /** 図種に応じてタブを開く / 追加入力ダイアログを誘導する。 */
    private void openKindAsTab(DiagramKind kind) {
        switch (kind) {
            case SEQUENCE:
            case ACTIVITY:
            case CALLGRAPH:
                openMethodKind(kind);
                break;
            case LAYOUT:
                pickLayoutFile();
                break;
            case LAYOUT_SCREEN:
                pickLayoutScreenFile();
                break;
            case LAYOUT_RENDER:
                pickLayoutRenderFile();
                break;
            case NAVIGATION:
                pickNavigationGraph();
                break;
            case MANIFEST:
                openManifestDiagram();
                break;
            default:
                openProjectWide(kind);
                break;
        }
    }

    /** Sequence/Activity/CallGraph: アクティブタブのメソッドを流用、無ければ入力ダイアログ。 */
    private void openMethodKind(DiagramKind kind) {
        TreeNodeOpenRequest focused = tabPane.focusedTabRequest();
        if (focused != null && focused.target == TreeNodeOpenRequest.Target.METHOD) {
            tabPane.addOrFocusTab(TreeNodeOpenRequest.method(
                    focused.classInfo, focused.methodInfo, kind));
            return;
        }
        if (!cache().isLoaded()) {
            return;
        }
        switch (kind) {
            case SEQUENCE:  pickSequenceEntry(); break;
            case ACTIVITY:  pickActivityEntry(); break;
            case CALLGRAPH: pickCallGraphEntry(); break;
            default: break;
        }
    }

    /** kind が起点/対象キーを必要とし、かつそれが未指定かどうか (補助 API)。 */
    boolean entryMissingFor(DiagramKind kind) {
        switch (kind) {
            case SEQUENCE:   return isBlank(state.sequenceEntry);
            case ACTIVITY:   return isBlank(state.activityEntry);
            case CALLGRAPH:  return isBlank(state.callGraphEntry);
            case LAYOUT:        return isBlank(state.currentLayoutKey);
            case LAYOUT_SCREEN: return isBlank(state.currentLayoutKey);
            case LAYOUT_RENDER: return isBlank(state.currentLayoutKey);
            case NAVIGATION:    return isBlank(state.currentNavigationKey);
            default:         return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * ツールバーのトグルボタン側で現在の図種を反映する (メニューラジオと双方向同期)。
     */
    public void syncDiagramToggle(DiagramKind kind) {
        JToggleButton b = diagramToggles.get(kind);
        if (b != null && !b.isSelected()) {
            b.setSelected(true);
        }
    }

    // -------------------------------------------------------------------------
    // エンティティ検索・ドリルダウン
    // -------------------------------------------------------------------------

    public void openEntitySearch() {
        if (!cache().isLoaded()) {
            JOptionPane.showMessageDialog(parentFrame,
                    Messages.get("dlg.noProject.message"),
                    Messages.get("dlg.noProject.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LazyDetail.withDetailedClasses(cache(), parentFrame, classes -> {
            EntitySearchDialog dlg = new EntitySearchDialog(parentFrame, classes);
            if (dlg.getCandidateCount() == 0) {
                JOptionPane.showMessageDialog(parentFrame,
                        Messages.get("dlg.search.noEntities"),
                        Messages.get("dlg.search.title"), JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            dlg.setVisible(true);
            EntitySearchDialog.Entry result = dlg.getResult();
            if (result == null) {
                return;
            }
            if (dlg.isDrillDownRequested()) {
                drillDownToClass(result.ownerQn);
                return;
            }
            switch (result.kind) {
                case CLASS:
                    scopeToClass(result.ownerQn);
                    break;
                case METHOD: {
                    String simple = extractSimpleClass(result.ownerQn);
                    String methodEntry = simple + "." + result.simpleName;
                    // 画面中央のポップアップ (Esc 不可) ではなく、親中央のモーダル選択に。
                    String[] options = {
                            Messages.get("dlg.chooseDiagram.sequence"),
                            Messages.get("dlg.chooseDiagram.activity"),
                            Messages.get("dlg.chooseDiagram.callGraph")};
                    int choice = JOptionPane.showOptionDialog(parentFrame,
                            java.text.MessageFormat.format(
                                    Messages.get("dlg.chooseDiagram.message"), methodEntry),
                            Messages.get("dlg.chooseDiagram.title"), JOptionPane.DEFAULT_OPTION,
                            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                    if (choice == 0) {
                        openEntryDiagram(methodEntry, DiagramKind.SEQUENCE);
                    } else if (choice == 1) {
                        openEntryDiagram(methodEntry, DiagramKind.ACTIVITY);
                    } else if (choice == 2) {
                        openEntryDiagram(methodEntry, DiagramKind.CALLGRAPH);
                    }
                    break;
                }
                case FIELD:
                    scopeToClass(result.ownerQn);
                    break;
                default:
                    break;
            }
        });
    }

    /** クラス図タブを開く (FQN を seed として 1 ホップ近傍)。 */
    public void scopeToClass(String fqn) {
        if (fqn == null || fqn.isEmpty() || tabPane == null) {
            return;
        }
        JavaClassInfo ci = cache().getIndex().header(fqn).orElse(null);
        if (ci == null) {
            return;
        }
        tabPane.addOrFocusTab(TreeNodeOpenRequest.classNode(ci));
    }

    /** 指定された FQN を seed として DETAILED プリセットのクラス図タブを開く。 */
    private void drillDownToClass(String fqn) {
        if (fqn == null || fqn.isEmpty() || tabPane == null) {
            return;
        }
        JavaClassInfo ci = cache().getIndex().header(fqn).orElse(null);
        DiagramScope.Builder b = DiagramScope.builder().seed(fqn).neighborHops(1);
        DiagramPreset.DETAILED.applyTo(b);
        DiagramRequest spec = new DiagramRequest(DiagramKind.CLASS, null, null, true, b.build(), true);
        String label = ci != null ? ci.getSimpleName() : extractSimpleClass(fqn);
        tabPane.openDiagram("CLASS:" + fqn, label, TreeNodeIcon.CLASS, spec,
                ci != null ? TreeNodeOpenRequest.classNode(ci) : null);
        treePanel.selectClassNode(fqn);
    }

    public static String extractSimpleClass(String qn) {
        if (qn == null || qn.isEmpty()) {
            return "";
        }
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
    }

    // -------------------------------------------------------------------------
    // エントリ選択ダイアログ (DiagramEntryDialogs へ委譲)
    // -------------------------------------------------------------------------

    public void pickSequenceEntry() {
        entryDialogs.pickSequenceEntry();
    }

    public void openParticipantFilterDialog() {
        entryDialogs.openParticipantFilterDialog();
    }

    public void pickActivityEntry() {
        entryDialogs.pickActivityEntry();
    }

    public void pickCallGraphEntry() {
        entryDialogs.pickCallGraphEntry();
    }

    public void pickLayoutFile() {
        entryDialogs.pickLayoutFile();
    }

    public void pickLayoutScreenFile() {
        entryDialogs.pickLayoutScreenFile();
    }

    public void pickLayoutRenderFile() {
        entryDialogs.pickLayoutRenderFile();
    }

    public void pickNavigationGraph() {
        entryDialogs.pickNavigationGraph();
    }

    // -------------------------------------------------------------------------
    // DiagramRequest ビルダ
    // -------------------------------------------------------------------------

    public DiagramRequest buildSequenceRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Sequence entry must be in 'Class.method' format: " + entry);
        }
        java.util.Set<String> hidden = state.sequenceHiddenParticipants.isEmpty()
                ? null : new java.util.LinkedHashSet<>(state.sequenceHiddenParticipants);
        return new DiagramRequest(DiagramKind.SEQUENCE,
                entry.substring(0, dot), entry.substring(dot + 1), true,
                null, false, null, hidden);
    }

    public DiagramRequest buildActivityRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Activity entry must be in 'Class.method' format: " + entry);
        }
        return DiagramRequest.forActivity(
                entry.substring(0, dot), entry.substring(dot + 1), true);
    }

    public DiagramRequest buildCallGraphRequest(String entry) {
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            throw new IllegalArgumentException(
                    "Call graph entry must be in 'Class.method' format: " + entry);
        }
        return DiagramRequest.forCallGraph(
                entry.substring(0, dot), entry.substring(dot + 1), true);
    }
}
