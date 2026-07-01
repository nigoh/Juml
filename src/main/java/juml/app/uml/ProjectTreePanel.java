// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.aosp.AndroidBpModule;
import juml.core.formats.android.AndroidComponentInfo;
import juml.core.formats.android.AndroidManifestInfo;
import juml.core.formats.android.AndroidPermissionInfo;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * プロジェクトのモジュール/パッケージ/クラス/メソッド階層を表示する {@link JTree}。
 *
 * <p>{@link ProjectAnalysisCache} の解析結果から
 * <code>(プロジェクトルート)</code> → <code>module</code> →
 * <code>package</code> → <code>class</code> → <code>method</code>
 * のノードツリーを構築する。モジュール情報は
 * {@link AndroidProjectAnalysis#getGradleByModule()} と
 * {@link ClassIndex#moduleMap()} を組み合わせて紐付け、見つからなかったクラスは
 * <em>(other)</em> モジュール下に集約する。</p>
 *
 * <p>AOSP 級でも初期表示が固まらないよう、子ノードは遅延構築する:
 * <ul>
 *   <li>初期: モジュール → パッケージ ノードまで構築</li>
 *   <li>パッケージ展開時: 該当パッケージのクラスノードを追加</li>
 *   <li>クラス展開時: メソッドノードを追加</li>
 * </ul>
 * 抽象メソッドはシーケンス図起点にならないので除外する。</p>
 *
 * <p>選択イベントは {@link #setOnClassSelected(java.util.function.Consumer)} ・
 * {@link #setOnMethodSelected(java.util.function.Consumer)} ・
 * {@link #setOnPackageSelected(java.util.function.Consumer)} で受け取れる。
 * パッケージ右クリックメニューからクラス図ドリルダウンを発火する。</p>
 */
public class ProjectTreePanel extends JPanel {

    private final JTree tree;
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode root;
    /** Stage A クラスを展開時に Stage B 昇格するためのインデックス (lazy ロード時のみ非 null)。 */
    private ClassIndex classIndex;
    private boolean suppressNotify;
    private java.util.function.Consumer<JavaClassInfo> onClassSelected;
    private java.util.function.Consumer<MethodSelection> onMethodSelected;
    private java.util.function.Consumer<MethodSelection> onActivityMethodSelected;
    private java.util.function.Consumer<String> onPackageSelected;
    private java.util.function.Consumer<String> onModuleSelected;
    private java.util.function.Consumer<AndroidManifestInfo> onManifestSelected;
    private java.util.function.Consumer<AndroidComponentInfo> onComponentSelected;
    private java.util.function.Consumer<TreeNodeOpenRequest> onOpenInNewTab;
    private java.util.function.Consumer<TreeNodeOpenRequest> onOpenSource;
    private java.util.function.Consumer<TreeNodeOpenRequest> onPreviewInTab;
    private Runnable onSoongSelected;

    public ProjectTreePanel() {
        super(new BorderLayout());
        root = new DefaultMutableTreeNode("(no project)");
        model = new DefaultTreeModel(root);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> notifySelection());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                Object last = event.getPath().getLastPathComponent();
                if (last instanceof DefaultMutableTreeNode) {
                    expandLazy((DefaultMutableTreeNode) last);
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // no-op: 一度構築した子ノードは破棄せず保持
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
                // 中クリックは mousePressed で捕捉する (プラットフォーム互換性のため)
                if (javax.swing.SwingUtilities.isMiddleMouseButton(e)) {
                    maybeOpenInNewTab(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                // ダブルクリックで新しいタブに開く (左ボタンのみ)
                if (javax.swing.SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    maybeOpenInNewTab(e);
                }
            }
        });
        tree.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    maybeOpenSelectedInNewTab();
                }
            }
        });
        tree.setCellRenderer(new ProjectTreeCellRenderer());
        // セルレンダラが返す FQN tooltip を表示できるようツリーを登録する。
        javax.swing.ToolTipManager.sharedInstance().registerComponent(tree);
        // 行間に余白を持たせて、メソッドが多いツリーでも視認しやすくする。
        tree.setRowHeight(22);
        add(new JScrollPane(tree), BorderLayout.CENTER);
        add(new TreeIconLegendPanel(), BorderLayout.SOUTH);
    }

    public void setOnClassSelected(java.util.function.Consumer<JavaClassInfo> listener) {
        this.onClassSelected = listener;
    }

    public void setOnMethodSelected(java.util.function.Consumer<MethodSelection> listener) {
        this.onMethodSelected = listener;
    }

    /**
     * メソッド配下のアクティビティ図リーフが選択されたときのハンドラ。
     * 受け取り側はアクティビティ図モードへ切り替える。
     */
    public void setOnActivityMethodSelected(java.util.function.Consumer<MethodSelection> listener) {
        this.onActivityMethodSelected = listener;
    }

    /**
     * パッケージノードのクリック (左クリック選択 / 右クリックメニュー両方) で発火する
     * ドリルダウン用ハンドラ。 受け取り側は当該パッケージにスコープしたクラス図への
     * 切り替えなどを行う。
     */
    public void setOnPackageSelected(java.util.function.Consumer<String> listener) {
        this.onPackageSelected = listener;
    }

    /** モジュールノード選択時のハンドラ。受け取り側は当該モジュールにスコープした図への切替などを行う。 */
    public void setOnModuleSelected(java.util.function.Consumer<String> listener) {
        this.onModuleSelected = listener;
    }

    /** Manifest ノード選択時のコールバック。Manifest 図への切り替え等に使う。 */
    public void setOnManifestSelected(java.util.function.Consumer<AndroidManifestInfo> listener) {
        this.onManifestSelected = listener;
    }

    /** Manifest 配下の Activity / Service / Receiver / Provider ノード選択時のコールバック。 */
    public void setOnComponentSelected(java.util.function.Consumer<AndroidComponentInfo> listener) {
        this.onComponentSelected = listener;
    }

    /**
     * マウス中クリックで「新しいタブとして開く」操作を受けるハンドラ。
     * 受け取り側は {@link DiagramTabPane#addOrFocusTab} などを呼び出す。
     */
    public void setOnOpenInNewTab(java.util.function.Consumer<TreeNodeOpenRequest> listener) {
        this.onOpenInNewTab = listener;
    }

    /**
     * クラス / メソッドノードの「Open source」操作を受けるハンドラ。
     * 受け取り側はタブを開いて実ソース表示を前面に出す。
     */
    public void setOnOpenSource(java.util.function.Consumer<TreeNodeOpenRequest> listener) {
        this.onOpenSource = listener;
    }

    /**
     * ツリーの単一クリック (選択変更) でプレビュータブとして開くハンドラ。
     * VS Code 風の斜体一時タブを実現する。
     */
    public void setOnPreviewInTab(java.util.function.Consumer<TreeNodeOpenRequest> listener) {
        this.onPreviewInTab = listener;
    }

    /**
     * 「Soong (Android.bp)」グループ / bp ファイル / bp モジュールノード選択時のハンドラ。
     * 受け取り側は Soong 依存図タブを開く / フォーカスする。
     */
    public void setOnSoongSelected(Runnable listener) {
        this.onSoongSelected = listener;
    }

    /** モジュール紐付けなしの簡易 populate (既存呼び出しの互換維持)。 */
    public void populate(AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
                          String projectName) {
        populate(analysis, classes, projectName, null);
    }

    /**
     * 解析結果からツリーを再構築する。
     *
     * <p>classToModule が与えられた場合、各クラスはそのモジュール下に配置される。
     * null/空の場合は全クラスを <code>(other)</code> に集約する。</p>
     */
    public void populate(AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
                          String projectName, Map<String, String> classToModule) {
        populate(analysis, classes, projectName, classToModule, null);
    }

    /**
     * インデックス付き populate。{@code index} が与えられ、かつクラスがヘッダのみ
     * (Stage A) の場合、クラスノード展開時に {@link ClassIndex#detail} で
     * Stage B 昇格してメソッドノードを構築する (lazyDetails ロード対応)。
     */
    public void populate(AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
                          String projectName, Map<String, String> classToModule,
                          ClassIndex index) {
        this.classIndex = index;
        root.setUserObject(projectName != null ? projectName : "(project)");
        root.removeAllChildren();
        // モジュール → パッケージ → クラスを集計
        Map<String, Map<String, List<JavaClassInfo>>> byModule
                = groupByModule(analysis, classes, classToModule);
        // どのモジュールに manifest があるかは analysis から取り出す
        Map<String, List<AndroidManifestInfo>> manifestsByModule = analysis != null
                ? analysis.getManifestsByModule() : java.util.Collections.emptyMap();
        // クラスが何もないモジュールでも manifest があれば表示する
        for (String mod : manifestsByModule.keySet()) {
            byModule.computeIfAbsent(mod, k -> new TreeMap<>());
        }
        for (Map.Entry<String, Map<String, List<JavaClassInfo>>> me : byModule.entrySet()) {
            DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(
                    new ModuleEntry(me.getKey()));
            // Manifest を先頭に出す (アプリ視点で目立ちやすいように)
            List<AndroidManifestInfo> manifests = manifestsByModule.get(me.getKey());
            if (manifests != null) {
                for (AndroidManifestInfo m : manifests) {
                    moduleNode.add(buildManifestNode(m));
                }
            }
            for (Map.Entry<String, List<JavaClassInfo>> pe : me.getValue().entrySet()) {
                PackageEntry pkgEntry = new PackageEntry(pe.getKey(), pe.getValue().size(),
                        pe.getValue());
                DefaultMutableTreeNode pkgNode = new DefaultMutableTreeNode(pkgEntry);
                // クラス子ノードは遅延構築 (TreeWillExpandListener で生成)
                // ただし「展開可能」と見せるためのダミー子を 1 件入れる
                if (!pe.getValue().isEmpty()) {
                    pkgNode.add(new DefaultMutableTreeNode(LazyPlaceholder.INSTANCE));
                }
                moduleNode.add(pkgNode);
            }
            root.add(moduleNode);
        }
        model.reload(root);
        // モジュールノードは開いた状態で見せておく (パッケージ一覧がすぐ見えるように)。
        // row index ではなく TreePath で展開しないとルートと取り違える。
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode mod = (DefaultMutableTreeNode) root.getChildAt(i);
            tree.expandPath(new TreePath(mod.getPath()));
        }
    }

    /**
     * 解析済みの {@code Android.bp} (Soong Blueprint) モジュールを、ツリー最上位の
     * 「Soong (Android.bp)」グループとして追加する。
     *
     * <p>{@code (Soong) → bp ファイル → モジュール} の階層で並べ、{@link #populate} 後に
     * 呼び出す前提 (既存のモジュール/パッケージ展開状態を壊さないよう {@code insertNodeInto}
     * で追記する)。{@code rootPath} は bp ファイルパスのルート相対化に使う。</p>
     */
    public void addSoongModules(List<AndroidBpModule> modules, String rootPath) {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        // ファイル単位でグルーピング (宣言順を保つ)
        Map<String, List<AndroidBpModule>> byFile = new LinkedHashMap<>();
        for (AndroidBpModule m : modules) {
            byFile.computeIfAbsent(m.getFile(), k -> new java.util.ArrayList<>()).add(m);
        }
        DefaultMutableTreeNode group = new DefaultMutableTreeNode(
                new SoongGroupEntry(byFile.size(), modules.size()));
        for (Map.Entry<String, List<AndroidBpModule>> e : byFile.entrySet()) {
            DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(
                    new BpFileEntry(relativize(e.getKey(), rootPath), e.getValue().size()));
            for (AndroidBpModule m : e.getValue()) {
                fileNode.add(new DefaultMutableTreeNode(new BpModuleEntry(m)));
            }
            group.add(fileNode);
        }
        model.insertNodeInto(group, root, root.getChildCount());
        // グループは開いて bp ファイル一覧をすぐ見せる (各ファイル配下は折りたたみのまま)。
        tree.expandPath(new TreePath(group.getPath()));
    }

    /** 絶対パスをルート相対に畳む (ルート外なら元のパス)。 */
    private static String relativize(String filePath, String rootPath) {
        if (filePath == null) {
            return "";
        }
        if (rootPath != null && !rootPath.isEmpty() && filePath.startsWith(rootPath)) {
            String rel = filePath.substring(rootPath.length());
            while (rel.startsWith("/") || rel.startsWith("\\")) {
                rel = rel.substring(1);
            }
            return rel.isEmpty() ? filePath : rel;
        }
        return filePath;
    }

    /**
     * Manifest 1 件分のサブツリーを構築。
     *
     * <p>{@code [manifest] sourceSet} → Activities / Services / Receivers / Providers /
     * Permissions / Features の順で展開可能なノードを並べる。</p>
     */
    private static DefaultMutableTreeNode buildManifestNode(AndroidManifestInfo m) {
        DefaultMutableTreeNode mnode = new DefaultMutableTreeNode(new ManifestEntry(m));
        addComponentGroup(mnode, "Activities", m.getActivities());
        addComponentGroup(mnode, "Services", m.getServices());
        addComponentGroup(mnode, "Receivers", m.getReceivers());
        addComponentGroup(mnode, "Providers", m.getProviders());
        if (!m.getPermissions().isEmpty()) {
            DefaultMutableTreeNode g = new DefaultMutableTreeNode(
                    new ComponentGroupEntry("Permissions", m.getPermissions().size()));
            for (AndroidPermissionInfo p : m.getPermissions()) {
                g.add(new DefaultMutableTreeNode(new PermissionEntry(p)));
            }
            mnode.add(g);
        }
        if (!m.getFeatures().isEmpty()) {
            DefaultMutableTreeNode g = new DefaultMutableTreeNode(
                    new ComponentGroupEntry("Features", m.getFeatures().size()));
            for (String f : m.getFeatures()) {
                g.add(new DefaultMutableTreeNode(new FeatureEntry(f)));
            }
            mnode.add(g);
        }
        return mnode;
    }

    private static void addComponentGroup(DefaultMutableTreeNode mnode, String label,
                                           List<AndroidComponentInfo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        DefaultMutableTreeNode g = new DefaultMutableTreeNode(
                new ComponentGroupEntry(label, list.size()));
        for (AndroidComponentInfo c : list) {
            g.add(new DefaultMutableTreeNode(new ComponentEntry(c)));
        }
        mnode.add(g);
    }

    /**
     * 指定 FQN のクラスノードをツリーで選択・スクロールする。
     * パッケージ/クラスノードは必要に応じて遅延展開する。
     * 選択変更コールバック ({@link #notifySelection}) は発火しない。
     */
    public void selectClassNode(String fqn) {
        if (fqn == null || fqn.isEmpty()) {
            return;
        }
        int lastDot = fqn.lastIndexOf('.');
        String pkg = lastDot < 0 ? "(default)" : fqn.substring(0, lastDot);
        for (int m = 0; m < root.getChildCount(); m++) {
            DefaultMutableTreeNode moduleNode = (DefaultMutableTreeNode) root.getChildAt(m);
            DefaultMutableTreeNode pkgNode = findPackageNode(moduleNode, pkg);
            if (pkgNode == null) {
                continue;
            }
            tree.expandPath(new TreePath(pkgNode.getPath()));
            DefaultMutableTreeNode classNode = findClassNode(pkgNode, fqn);
            if (classNode != null) {
                TreePath path = new TreePath(classNode.getPath());
                suppressNotify = true;
                tree.setSelectionPath(path);
                suppressNotify = false;
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    /**
     * 指定 FQN クラス + メソッド名のメソッドノードをツリーで選択・スクロールする。
     * クラス/パッケージノードは必要に応じて遅延展開する。
     * 選択変更コールバック ({@link #notifySelection}) は発火しない。
     */
    public void selectMethodNode(String classFqn, String methodName) {
        if (classFqn == null || classFqn.isEmpty() || methodName == null || methodName.isEmpty()) {
            return;
        }
        int lastDot = classFqn.lastIndexOf('.');
        String pkg = lastDot < 0 ? "(default)" : classFqn.substring(0, lastDot);
        for (int m = 0; m < root.getChildCount(); m++) {
            DefaultMutableTreeNode moduleNode = (DefaultMutableTreeNode) root.getChildAt(m);
            DefaultMutableTreeNode pkgNode = findPackageNode(moduleNode, pkg);
            if (pkgNode == null) {
                continue;
            }
            tree.expandPath(new TreePath(pkgNode.getPath()));
            DefaultMutableTreeNode classNode = findClassNode(pkgNode, classFqn);
            if (classNode == null) {
                continue;
            }
            tree.expandPath(new TreePath(classNode.getPath()));
            DefaultMutableTreeNode methodNode = findMethodNode(classNode, methodName);
            if (methodNode != null) {
                TreePath path = new TreePath(methodNode.getPath());
                suppressNotify = true;
                tree.setSelectionPath(path);
                suppressNotify = false;
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    /**
     * 指定パッケージのノードをツリーで選択・スクロールする。
     * 選択変更コールバック ({@link #notifySelection}) は発火しない (タブ↔ツリー同期用)。
     */
    public void selectPackageNode(String pkg) {
        if (pkg == null || pkg.isEmpty()) {
            return;
        }
        for (int m = 0; m < root.getChildCount(); m++) {
            DefaultMutableTreeNode moduleNode = (DefaultMutableTreeNode) root.getChildAt(m);
            DefaultMutableTreeNode pkgNode = findPackageNode(moduleNode, pkg);
            if (pkgNode != null) {
                TreePath path = new TreePath(pkgNode.getPath());
                suppressNotify = true;
                tree.setSelectionPath(path);
                suppressNotify = false;
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    /**
     * 指定モジュールのノードをツリーで選択・スクロールする。
     * 選択変更コールバック ({@link #notifySelection}) は発火しない (タブ↔ツリー同期用)。
     */
    public void selectModuleNode(String module) {
        if (module == null || module.isEmpty()) {
            return;
        }
        for (int m = 0; m < root.getChildCount(); m++) {
            DefaultMutableTreeNode moduleNode = (DefaultMutableTreeNode) root.getChildAt(m);
            Object u = moduleNode.getUserObject();
            if (u instanceof ModuleEntry && module.equals(((ModuleEntry) u).name)) {
                TreePath path = new TreePath(moduleNode.getPath());
                suppressNotify = true;
                tree.setSelectionPath(path);
                suppressNotify = false;
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    private static DefaultMutableTreeNode findPackageNode(DefaultMutableTreeNode moduleNode, String pkg) {
        for (int i = 0; i < moduleNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) moduleNode.getChildAt(i);
            Object u = child.getUserObject();
            if (u instanceof PackageEntry && pkg.equals(((PackageEntry) u).name)) {
                return child;
            }
        }
        return null;
    }

    private static DefaultMutableTreeNode findClassNode(DefaultMutableTreeNode pkgNode, String fqn) {
        for (int i = 0; i < pkgNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) pkgNode.getChildAt(i);
            Object u = child.getUserObject();
            if (u instanceof ClassEntry && fqn.equals(((ClassEntry) u).info.getQualifiedName())) {
                return child;
            }
        }
        return null;
    }

    private static DefaultMutableTreeNode findMethodNode(DefaultMutableTreeNode classNode, String methodName) {
        for (int i = 0; i < classNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) classNode.getChildAt(i);
            Object u = child.getUserObject();
            if (u instanceof MethodEntry && methodName.equals(((MethodEntry) u).method.getName())) {
                return child;
            }
        }
        return null;
    }

    /** テスト用: ルートノードを返す (ツリー構造の検証専用)。 */
    DefaultMutableTreeNode rootForTest() {
        return root;
    }

    /** テスト用: ユーザーオブジェクトから「新しいタブで開く」リクエストを作る。 */
    TreeNodeOpenRequest buildOpenRequestForTest(Object userObject) {
        return buildOpenRequest(userObject);
    }

    /** テスト用: 指定ノードの遅延展開 (TreeWillExpand と同等) を起こす。 */
    void expandNodeForTest(DefaultMutableTreeNode node) {
        expandLazy(node);
    }

    /** ツリーをクリアして空状態に戻す。 */
    public void clear() {
        root.setUserObject("(no project)");
        root.removeAllChildren();
        model.reload(root);
    }

    private void notifySelection() {
        if (suppressNotify) {
            return;
        }
        Object last = tree.getLastSelectedPathComponent();
        if (last instanceof DefaultMutableTreeNode) {
            Object u = ((DefaultMutableTreeNode) last).getUserObject();
            firePreview(u);
            if (u instanceof MethodDiagramEntry) {
                MethodDiagramEntry mde = (MethodDiagramEntry) u;
                MethodSelection sel = new MethodSelection(mde.owner, mde.method);
                if (mde.kind == DiagramKind.ACTIVITY) {
                    if (onActivityMethodSelected != null) {
                        onActivityMethodSelected.accept(sel);
                    }
                } else {
                    if (onMethodSelected != null) {
                        onMethodSelected.accept(sel);
                    }
                }
                return;
            }
            if (u instanceof MethodEntry) {
                MethodEntry me = (MethodEntry) u;
                if (onMethodSelected != null) {
                    onMethodSelected.accept(new MethodSelection(me.owner, me.method));
                }
                return;
            }
            if (u instanceof ClassEntry) {
                if (onClassSelected != null) {
                    onClassSelected.accept(((ClassEntry) u).info);
                }
                return;
            }
            if (u instanceof PackageEntry) {
                if (onPackageSelected != null) {
                    onPackageSelected.accept(((PackageEntry) u).name);
                }
                return;
            }
            if (u instanceof ModuleEntry) {
                if (onModuleSelected != null) {
                    onModuleSelected.accept(((ModuleEntry) u).name);
                }
                return;
            }
            if (u instanceof ManifestEntry) {
                if (onManifestSelected != null) {
                    onManifestSelected.accept(((ManifestEntry) u).info);
                }
                return;
            }
            if (u instanceof ComponentEntry) {
                if (onComponentSelected != null) {
                    onComponentSelected.accept(((ComponentEntry) u).info);
                }
                return;
            }
            if (u instanceof ComponentGroupEntry || u instanceof PermissionEntry
                    || u instanceof FeatureEntry) {
                if (onManifestSelected != null) {
                    onManifestSelected.accept(null);
                }
                return;
            }
            if (u instanceof SoongGroupEntry || u instanceof BpFileEntry
                    || u instanceof BpModuleEntry) {
                if (onSoongSelected != null) {
                    onSoongSelected.run();
                }
                return;
            }
        }
        if (onClassSelected != null) {
            onClassSelected.accept(null);
        }
    }

    private void firePreview(Object userObject) {
        if (onPreviewInTab == null) {
            return;
        }
        TreeNodeOpenRequest req = buildOpenRequest(userObject);
        if (req != null) {
            onPreviewInTab.accept(req);
        }
    }

    /**
     * マウス中クリック (ホイール押し込み) で当該ノードを「新しいタブで開く」リクエストを発火する。
     * 受け手 (機能 2 で配線) は {@link #setOnOpenInNewTab(java.util.function.Consumer)} で設定する。
     * 中クリックはツリーの選択を変えない (Web ブラウザの挙動に合わせる)。
     */
    private void maybeOpenInNewTab(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        openInNewTabForPath(path);
    }

    /** キーボードのみの操作 (Enter キー) で選択中ノードを新しいタブで開く。 */
    private void maybeOpenSelectedInNewTab() {
        openInNewTabForPath(tree.getSelectionPath());
    }

    private void openInNewTabForPath(TreePath path) {
        if (onOpenInNewTab == null || path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) {
            return;
        }
        TreeNodeOpenRequest req = buildOpenRequest(((DefaultMutableTreeNode) last).getUserObject());
        if (req != null) {
            onOpenInNewTab.accept(req);
        }
    }

    /** ツリーのユーザーオブジェクトから「新しいタブで開く」リクエストを作る。 */
    private TreeNodeOpenRequest buildOpenRequest(Object u) {
        if (u instanceof MethodDiagramEntry) {
            MethodDiagramEntry mde = (MethodDiagramEntry) u;
            return TreeNodeOpenRequest.method(mde.owner, mde.method, mde.kind);
        }
        if (u instanceof MethodEntry) {
            MethodEntry me = (MethodEntry) u;
            return TreeNodeOpenRequest.method(me.owner, me.method, DiagramKind.SEQUENCE);
        }
        if (u instanceof ClassEntry) {
            return TreeNodeOpenRequest.classNode(((ClassEntry) u).info);
        }
        if (u instanceof PackageEntry) {
            return TreeNodeOpenRequest.pkg(((PackageEntry) u).name);
        }
        if (u instanceof ModuleEntry) {
            return TreeNodeOpenRequest.module(((ModuleEntry) u).name);
        }
        if (u instanceof BpModuleEntry) {
            return TreeNodeOpenRequest.soong(((BpModuleEntry) u).module.getName());
        }
        if (u instanceof BpFileEntry) {
            return TreeNodeOpenRequest.soong(((BpFileEntry) u).relPath);
        }
        if (u instanceof SoongGroupEntry) {
            return TreeNodeOpenRequest.soong(null);
        }
        return null;
    }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) {
            return;
        }
        Object u = ((DefaultMutableTreeNode) last).getUserObject();
        JPopupMenu menu = new JPopupMenu();
        if (u instanceof PackageEntry && onPackageSelected != null) {
            final String pkgName = ((PackageEntry) u).name;
            JMenuItem drill = new JMenuItem(
                    juml.util.Messages.get("tree.menu.packageClassDiagram"));
            drill.setIcon(ToolBarBuilder.kindIcon(DiagramKind.CLASS, 14));
            drill.addActionListener(ev -> onPackageSelected.accept(pkgName));
            menu.add(drill);
        }
        // クラス/メソッド/モジュール等、タブで開けるノードに「新しいタブで開く」を出す。
        final TreeNodeOpenRequest openReq = buildOpenRequest(u);
        if (openReq != null && onOpenInNewTab != null) {
            JMenuItem openTab = new JMenuItem(juml.util.Messages.get("tree.menu.openInNewTab"));
            openTab.addActionListener(ev -> onOpenInNewTab.accept(openReq));
            menu.add(openTab);
        }
        // クラス / メソッドは実ソースを直接開ける ("Open source")。
        if (openReq != null && onOpenSource != null
                && (openReq.target == TreeNodeOpenRequest.Target.CLASS
                    || openReq.target == TreeNodeOpenRequest.Target.METHOD)) {
            JMenuItem openSource = new JMenuItem(juml.util.Messages.get("source.openSource"));
            openSource.addActionListener(ev -> onOpenSource.accept(openReq));
            menu.add(openSource);
        }
        if (menu.getComponentCount() > 0) {
            tree.setSelectionPath(path);
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /** 遅延展開: パッケージ → クラスノード、クラス → メソッドノードを生成する。 */
    private void expandLazy(DefaultMutableTreeNode node) {
        Object u = node.getUserObject();
        if (u instanceof PackageEntry) {
            PackageEntry pe = (PackageEntry) u;
            if (pe.expanded) {
                return;
            }
            pe.expanded = true;
            node.removeAllChildren();
            for (JavaClassInfo c : pe.classes) {
                DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(new ClassEntry(c));
                // クラス展開時にメソッドを構築するためダミー子を 1 件入れる。
                // Stage A (未昇格) のクラスはメソッド有無が未知なので、展開可能にして
                // 展開時に昇格 → 実メソッドを構築する (lazyDetails 対応)。
                boolean mayHaveMethods = c.isDetailed() ? hasConcreteMethods(c)
                        : (classIndex != null && couldHaveMethods(c));
                if (mayHaveMethods) {
                    classNode.add(new DefaultMutableTreeNode(LazyPlaceholder.INSTANCE));
                }
                node.add(classNode);
            }
            model.nodeStructureChanged(node);
        } else if (u instanceof ClassEntry) {
            ClassEntry ce = (ClassEntry) u;
            if (ce.expanded) {
                return;
            }
            ce.expanded = true;
            // lazy ロード時はここで初めて Stage B 昇格し、実メソッドを得る。
            if (!ce.info.isDetailed() && classIndex != null) {
                JavaClassInfo detailed = classIndex.detail(
                        ce.info.getQualifiedName(), juml.util.ErrorListener.silent());
                if (detailed != null) {
                    ce.info = detailed;
                }
            }
            node.removeAllChildren();
            addMethodNodes(node, ce.info);
            model.nodeStructureChanged(node);
        }
    }

    /**
     * Stage A (未昇格) のクラスが概念的にメソッドを持ちうるか。アノテーションは
     * 持たない。それ以外 (クラス/列挙/インタフェース/AIDL) は昇格してみる価値がある。
     */
    private static boolean couldHaveMethods(JavaClassInfo c) {
        return c.getKind() != JavaClassInfo.Kind.ANNOTATION;
    }

    private static boolean hasConcreteMethods(JavaClassInfo c) {
        if (c.getMethods() == null) {
            return false;
        }
        for (JavaMethodInfo m : c.getMethods()) {
            if (!m.isAbstract()) {
                return true;
            }
        }
        return false;
    }

    private static void addMethodNodes(DefaultMutableTreeNode classNode, JavaClassInfo c) {
        for (JavaMethodInfo m : c.getMethods()) {
            if (m.isAbstract()) {
                continue;
            }
            DefaultMutableTreeNode methodNode = new DefaultMutableTreeNode(new MethodEntry(c, m));
            classNode.add(methodNode);
        }
    }

    private static Map<String, Map<String, List<JavaClassInfo>>> groupByModule(
            AndroidProjectAnalysis analysis, List<JavaClassInfo> classes,
            Map<String, String> classToModule) {
        Map<String, Map<String, List<JavaClassInfo>>> out = new LinkedHashMap<>();
        if (analysis != null) {
            for (String mod : analysis.getGradleByModule().keySet()) {
                out.computeIfAbsent(mod, k -> new TreeMap<>());
            }
        }
        Map<String, String> map = classToModule != null ? classToModule : java.util.Collections.emptyMap();
        for (JavaClassInfo c : classes) {
            String qn = c.getQualifiedName();
            String mod = map.getOrDefault(qn, "(other)");
            String pkg = c.getPackageName() == null || c.getPackageName().isEmpty()
                    ? "(default)" : c.getPackageName();
            out.computeIfAbsent(mod, k -> new TreeMap<>())
                    .computeIfAbsent(pkg, k -> new java.util.ArrayList<>())
                    .add(c);
        }
        // 空のモジュールエントリは除去
        out.entrySet().removeIf(e -> e.getValue().isEmpty());
        return out;
    }
}
