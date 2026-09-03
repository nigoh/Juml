// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.util.Messages;

import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
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
    /** 現在選択可能な図種 ({@link #updateAvailableDiagrams} で更新。null = 未設定でゲート無し)。 */
    private EnumSet<DiagramKind> allowedKinds;
    private final DiagramKindChooser diagramKindChooser;
    private final ProjectTreePanel treePanel;
    private final JTabbedPane mainTabs;
    private final DiagramTabPane tabPane;
    final javax.swing.JLabel statusLabel;
    final java.awt.Frame parentFrame;
    final Runnable refreshDiagram;
    private final Consumer<DiagramKind> onKindChanged;
    private final DiagramEntryDialogs entryDialogs;
    private final List<javax.swing.JMenuItem> sequenceOnlyMenuItems;
    private final List<javax.swing.JMenuItem> activityOnlyMenuItems;
    private final List<javax.swing.JMenuItem> layoutOnlyMenuItems;
    private final List<javax.swing.JMenuItem> navigationOnlyMenuItems;

    /** package-private — UmlMainFrame がミラー同期するために読む (アクティブタブの図種)。 */
    DiagramKind currentKind = DiagramKind.CLASS;

    public DiagramController(DiagramControllerDeps deps) {
        this.state = deps.state;
        this.cacheSupplier = deps.cacheSupplier;
        this.diagramItems = deps.diagramItems;
        this.diagramKindChooser = deps.diagramKindChooser;
        this.treePanel = deps.treePanel;
        this.mainTabs = deps.mainTabs;
        this.tabPane = deps.tabPane;
        this.statusLabel = deps.statusLabel;
        this.parentFrame = deps.parentFrame;
        this.refreshDiagram = deps.refreshDiagram;
        this.onKindChanged = deps.onKindChanged;
        this.entryDialogs = new DiagramEntryDialogs(this);
        this.sequenceOnlyMenuItems = deps.sequenceOnlyMenuItems;
        this.activityOnlyMenuItems = deps.activityOnlyMenuItems;
        this.layoutOnlyMenuItems = deps.layoutOnlyMenuItems;
        this.navigationOnlyMenuItems = deps.navigationOnlyMenuItems;
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

    // ツリー選択で汎用 Manifest 図を開きつつ、選択名をステータスバーへ出す (#37, 整形は
    // TreeSelectionStatus)。
    public void onTreeManifestSelected(juml.core.formats.android.AndroidManifestInfo m) {
        openManifestDiagram();
        TreeSelectionStatus.show(statusLabel, TreeSelectionStatus.forManifest(m));
    }

    public void onTreeComponentSelected(juml.core.formats.android.AndroidComponentInfo c) {
        openManifestDiagram();
        TreeSelectionStatus.show(statusLabel, TreeSelectionStatus.forComponent(c));
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

    public void onTreePreviewInTab(TreeNodeOpenRequest req) {
        if (req == null || tabPane == null) {
            return;
        }
        tabPane.addOrFocusPreviewTab(req);
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

    /**
     * スコープ内容から決定的なタブキー接尾辞を作る (null や全体図は空文字)。
     *
     * <p>署名は {@link DiagramScope#signature()} に委ねる。以前はここで一部の項目だけを
     * 連結しており、除外クラス正規表現・include/exclude アノテーション・seed・プリセット・
     * フォーカスクラスだけが違うスコープが同じキーになっていた。同じキーは既存タブへの
     * フォーカスに解決されるため、<b>スコープを変えたのに図が変わらない</b>状態になる。</p>
     */
    static String scopeKey(DiagramScope scope) {
        if (scope == null) {
            return "";
        }
        return ":" + Integer.toHexString(scope.signature().hashCode());
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
        return entryDialogs.promptForScope();
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

    /** アクティブタブの現在の描画リクエスト (無ければ null)。タブ固有状態の起点に使う (#40)。 */
    DiagramRequest activeTabSpec() {
        return tabPane != null ? tabPane.activeTabSpec() : null;
    }

    /**
     * アクティブタブの spec を {@code spec} へ差し替えて再描画する。null / タブ無しなら共有状態
     * からの再構築へフォールバック。タブ固有変更を「アクティブタブ spec の更新」へ統一する (#40)。
     */
    void applySpecToActiveTab(DiagramRequest spec) {
        if (spec != null && tabPane != null) {
            tabPane.setActiveTabSpecAndRender(spec);
        } else {
            applyStateToActiveTab();
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
                        : keepActiveLocale(DiagramRequest.forLayout(state.currentLayoutKey, true));
            case LAYOUT_SCREEN:
                return isBlank(state.currentLayoutKey) ? null
                        : keepActiveLocale(
                                DiagramRequest.forLayoutScreen(state.currentLayoutKey, true));
            case LAYOUT_RENDER:
                return isBlank(state.currentLayoutKey) ? null
                        : keepActiveLocale(
                                DiagramRequest.forLayoutRender(state.currentLayoutKey, true));
            case NAVIGATION:
                return isBlank(state.currentNavigationKey) ? null
                        : DiagramRequest.forNavigationGraph(state.currentNavigationKey, true);
            default:
                boolean links = k == DiagramKind.CLASS || k == DiagramKind.INHERITANCE;
                return new DiagramRequest(k, null, null, true, state.currentScope, links);
        }
    }

    /**
     * アクティブタブが選んでいる文言 locale をリクエストへ引き継ぐ。
     *
     * <p>レイアウト図のロケール切替は<b>タブ固有</b>の設定で、共有 {@link DiagramState} は
     * これを持たない。引き継がないと、スコープ変更などで spec を再構築するたびに
     * 選んだ言語が捨てられ、図が既定言語の文言へ勝手に戻ってしまう。</p>
     */
    private DiagramRequest keepActiveLocale(DiagramRequest spec) {
        return keepLocaleFrom(activeTabSpec(), spec);
    }

    /** {@link #keepActiveLocale} の純ロジック部 (アクティブタブ取得と分離してテスト可能にする)。 */
    static DiagramRequest keepLocaleFrom(DiagramRequest current, DiagramRequest spec) {
        String locale = current != null ? current.getStringLocale() : null;
        return locale != null && !locale.isEmpty() ? spec.withStringLocale(locale) : spec;
    }

    public void openScopeDialog() {
        entryDialogs.openScopeDialog();
    }

    // -------------------------------------------------------------------------
    // 図種切替・UI 同期
    // -------------------------------------------------------------------------

    /**
     * 利用可能な図種に応じて図種ドロップダウンの項目とメニューラジオを有効/無効化する
     * (非表示でなく無効化で「今は到達できない」ことを示す。未ロード時は空集合で全無効化)。
     */
    public void updateAvailableDiagrams(EnumSet<DiagramKind> allowed) {
        allowedKinds = EnumSet.copyOf(allowed);
        if (diagramKindChooser != null) {
            diagramKindChooser.setAvailableKinds(allowed);
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
            // ユーティリティタブ (Functions/Members 等) が選ばれ図タブが非アクティブ:
            // 図種ドロップダウンはプレースホルダ、メニューラジオ解除、文脈項目は無効化。
            reflectKindInToolbar(null);
            return;
        }
        syncToFocusedTab(info.treeSync);
        if (info.kind != null) {
            setCurrentKind(info.kind);
        }
        // 自由編集エディタタブは kind == null → プレースホルダ表示 (直前の図種を残さない)。
        reflectKindInToolbar(info.kind);
    }

    /**
     * 動的ダイアグラムタブの由来ノードを左ツリーでハイライトして連動させる。
     * {@code select*Node} は suppressNotify なので選択コールバックは発火しない。
     */
    public void syncToFocusedTab(TreeNodeOpenRequest req) {
        if (req != null) {
            treePanel.selectNodeFor(req);
        }
    }

    /** メニューラジオと図種ドロップダウンの表示を {@code kind} に合わせる (見た目のみ。null 可)。 */
    void reflectKindInToolbar(DiagramKind kind) {
        JRadioButtonMenuItem item = diagramItems.get(kind);
        if (item != null) {
            item.setSelected(true);
        } else {
            // メソッド系図種はメニューラジオを持たない。直前の構造図種の選択表示が残るので解除。
            clearButtonGroupOf(diagramItems.values());
        }
        syncDiagramToggle(kind);
        updateContextualMenuItems(kind);
    }

    /** ボタン群が属する {@link javax.swing.ButtonGroup} の選択を解除する。 */
    private static void clearButtonGroupOf(
            java.util.Collection<? extends javax.swing.AbstractButton> buttons) {
        for (javax.swing.AbstractButton b : buttons) {
            if (b.getModel() instanceof javax.swing.DefaultButtonModel dm
                    && dm.getGroup() != null) {
                dm.getGroup().clearSelection();
                return;
            }
        }
    }

    /**
     * 図種に依存する Diagram メニュー項目 (起点選択・参加者フィルタ・レイアウト/
     * ナビゲーショングラフ選択) の有効/無効を、アクティブな図種に合わせて切り替える。
     * これらは図種が合っていないと空振りするため、無効化して押せないことを示す。
     */
    private void updateContextualMenuItems(DiagramKind kind) {
        setEnabledAll(sequenceOnlyMenuItems, kind == DiagramKind.SEQUENCE);
        setEnabledAll(activityOnlyMenuItems, kind == DiagramKind.ACTIVITY);
        setEnabledAll(layoutOnlyMenuItems, kind == DiagramKind.LAYOUT
                || kind == DiagramKind.LAYOUT_SCREEN || kind == DiagramKind.LAYOUT_RENDER);
        setEnabledAll(navigationOnlyMenuItems, kind == DiagramKind.NAVIGATION);
    }

    private static void setEnabledAll(List<javax.swing.JMenuItem> items, boolean enabled) {
        if (items == null) {
            return;
        }
        for (javax.swing.JMenuItem item : items) {
            item.setEnabled(enabled);
        }
    }

    /**
     * ツールバー/メニューの図種ボタンが押されたときのハンドラ。
     * VS Code 風: アクティブタブを起点に、選んだ図種をタブとして開く / フォーカスする。
     */
    public void selectDiagramKind(DiagramKind kind) {
        if (allowedKinds != null && !allowedKinds.contains(kind)) {
            // メニュー/ドロップダウンでは無効化済みの図種 (コマンドパレット等の別経路)。
            // 空図タブを開いたり表示だけ切り替わったりしないよう、案内して終了する。
            if (statusLabel != null) {
                statusLabel.setText(java.text.MessageFormat.format(
                        Messages.get("diagram.kindUnavailable"), kind.getDisplayName()));
            }
            return;
        }
        // VS Code 風: 図種切替は「いまフォーカスしているタブの題材」に作用する (メソッドタブなら
        // 同じ Class.method の別図種、スコープ付きタブなら同じ題材のクラス図)。単体クラスを
        // 見ている最中に Class を選んで全体図の大規模ガードへ飛ばされる誤警告を防ぐ。
        if (tabPane != null && tabPane.dynamicTabFocused()) {
            TreeNodeOpenRequest reopen = reopenRequestFor(tabPane.focusedTabRequest(), kind);
            if (reopen != null) {
                tabPane.addOrFocusTab(reopen);
                // addOrFocusTab が何も発火しない場合 (未ロード/同一タブ) も表示と currentKind を揃える。
                syncKindToActiveTab();
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
        syncKindToActiveTab();
    }

    /**
     * 表示と {@link #currentKind} をアクティブタブの図種へ揃える (ダイアログキャンセル等で
     * 選んだ図種のタブが開かなかったときの食い違い防止)。図タブが無ければプレースホルダ表示。
     */
    private void syncKindToActiveTab() {
        if (tabPane == null) {
            return;
        }
        if (!tabPane.hasActiveTab()) {
            // 図タブが無い (ユーティリティタブ選択中 / 未ロード): 開かなかった図種を
            // 表示に残さずプレースホルダへ戻す。
            reflectKindInToolbar(null);
            return;
        }
        DiagramKind active = tabPane.activeTabKind();
        if (active != null && active != currentKind) {
            setCurrentKind(active);
        }
        reflectKindInToolbar(active);
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

    /** 図種ドロップダウンに現在の図種を反映する (一覧に無い図種や null でもラベルは更新する)。 */
    public void syncDiagramToggle(DiagramKind kind) {
        if (diagramKindChooser != null) {
            diagramKindChooser.setCurrentKind(kind);
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
