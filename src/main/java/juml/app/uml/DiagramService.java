// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.android.AndroidLayoutInfo;
import juml.core.formats.android.AndroidNavigationGraphInfo;
import juml.core.formats.android.AndroidProjectAnalysis;
import juml.core.formats.android.PlantUmlComponentDiagram;
import juml.core.formats.android.PlantUmlGradleDependencyGraph;
import juml.core.formats.android.PlantUmlLayoutDiagram;
import juml.core.formats.android.PlantUmlLayoutScreenDiagram;
import juml.core.formats.android.PlantUmlManifestDiagram;
import juml.core.formats.android.PlantUmlNavigationGraphDiagram;
import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.DependencyJarIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.PlantUmlActivityDiagram;
import juml.core.formats.uml.PlantUmlCommonClassesDiagram;
import juml.core.formats.uml.PlantUmlModuleDiagram;
import juml.core.formats.uml.PlantUmlPackageDiagram;
import juml.core.formats.uml.PlantUmlCallGraphDiagram;
import juml.core.formats.uml.PlantUmlSequenceDiagram;
import juml.util.ErrorListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link DiagramRequest} を受け取り、対応する PlantUML テキストを生成する。
 *
 * <p>既存の各 {@code PlantUml*Diagram.generate(...)} へディスパッチするだけの
 * 薄いラッパー。{@link ProjectAnalysisCache} から取り出した解析結果と組み合わせ、
 * GUI 側の UI イベントとレンダリングの間に挟む変換層として機能する。</p>
 *
 * <p>大規模プロジェクト対応として {@link DiagramScope} によるフィルタリングと、
 * 必要なクラスだけ Stage B (詳細) に昇格させる処理 ({@link ClassIndex#detail}) を担う。</p>
 */
public final class DiagramService {

    private DiagramService() {
    }

    /**
     * 図種に応じた PlantUML テキストを返す。
     *
     * @param request 図種・スコープ等
     * @param cache   プロジェクト解析結果 (ロード済みである必要がある)
     * @return PlantUML テキスト (@startuml ... @enduml を含む)
     */
    public static String generatePuml(DiagramRequest request, ProjectAnalysisCache cache) {
        if (cache == null || !cache.isLoaded()) {
            throw new IllegalStateException("ProjectAnalysisCache is not loaded");
        }
        // 画面遷移図はソースを再走査するためプロジェクトルートが要る。
        // ルートを持つこの入口でだけ処理し、ルート非依存の switch には渡さない。
        if (request != null && request.getKind() == DiagramKind.SCREEN_FLOW) {
            return ProjectRootDiagrams.screenFlow(cache.getProjectRoot());
        }
        // Soong 依存図もソースの Android.bp を再走査するためプロジェクトルートが要る。
        if (request != null && request.getKind() == DiagramKind.SOONG) {
            return ProjectRootDiagrams.soong(cache.getProjectRoot());
        }
        // build.ninja / .intermediates もビルド成果物ツリーを再走査するためルートが要る。
        if (request != null && request.getKind() == DiagramKind.BUILD_NINJA) {
            return ProjectRootDiagrams.buildNinja(cache.getProjectRoot());
        }
        if (request != null && request.getKind() == DiagramKind.INTERMEDIATES) {
            return ProjectRootDiagrams.intermediates(cache.getProjectRoot());
        }
        // リソース紐づけ図はコード (R.layout/R.string) を再走査するためルートが要る。
        if (request != null && request.getKind() == DiagramKind.RESOURCE_LINK) {
            return ProjectRootDiagrams.resourceLink(cache.getProjectRoot());
        }
        return generatePuml(request, cache.getAnalysis(), cache.getClasses(),
                cache.getIndex(), cache.getDependencyIndex());
    }

    /**
     * 生データ版。テストや CLI 経路など、{@link ProjectAnalysisCache} を介さない場合に使用。
     */
    public static String generatePuml(DiagramRequest request,
                                       AndroidProjectAnalysis analysis,
                                       List<JavaClassInfo> classes) {
        return generatePuml(request, analysis, classes, null, null);
    }

    /**
     * インデックス込み版。スコープ適用時の Stage B 昇格に {@link ClassIndex} を使う。
     */
    public static String generatePuml(DiagramRequest request,
                                       AndroidProjectAnalysis analysis,
                                       List<JavaClassInfo> classes,
                                       ClassIndex index) {
        return generatePuml(request, analysis, classes, index, null);
    }

    /**
     * 依存 JAR インデックスを併用する版。シーケンス図/クラス図で {@code <<external>>} /
     * {@code <<missing>>} ステレオタイプを描画するために
     * {@link DependencyJarIndex} を引き渡す。
     */
    public static String generatePuml(DiagramRequest request,
                                       AndroidProjectAnalysis analysis,
                                       List<JavaClassInfo> classes,
                                       ClassIndex index,
                                       DependencyJarIndex depIndex) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
                switch (request.getKind()) {
            case CLASS:
                return generateClassDiagram(request, analysis, classes, index, depIndex);
            case PACKAGE:
                return generatePackageDiagram(request, analysis, classes, index, depIndex);
            case SEQUENCE:
                return generateSequenceDiagram(request, analysis, classes, index, depIndex);
            case ACTIVITY:
                return generateActivityDiagram(request, analysis, classes, index, depIndex);
            case COMPONENT:
                return generateComponentDiagram(request, analysis, classes, index, depIndex);
            case DEPENDENCY:
                return generateDependencyDiagram(request, analysis, classes, index, depIndex);
            case MANIFEST:
                return generateManifestDiagram(request, analysis, classes, index, depIndex);
            case COMMON:
                return generateCommonDiagram(request, analysis, classes, index, depIndex);
            case CYCLES:
                // 逆参照インデックスを都度構築する (Impact パネル初回実行と同等のコスト)
                return juml.core.insights.PlantUmlPackageCycleDiagram.render(
                        juml.core.insights.InsightsAnalyzer.analyzeBuildingIndex(
                                classes, index, depIndex));
            case LAYOUT:
                return generateLayoutDiagram(request, analysis, classes, index, depIndex);
            case LAYOUT_SCREEN:
                return generateLayoutScreenDiagram(request, analysis, classes, index, depIndex);
            case LAYOUT_RENDER:
                return generateLayoutRenderDiagram(request, analysis, classes, index, depIndex);
            case NAVIGATION:
                return generateNavigationDiagram(request, analysis, classes, index, depIndex);
            case MODULE:
                return generateModuleDiagram(request, analysis, classes, index, depIndex);
            case INHERITANCE:
                return generateInheritanceDiagram(request, analysis, classes, index, depIndex);
            case CALLGRAPH:
                return generateCallgraphDiagram(request, analysis, classes, index, depIndex);
            case RESOURCE_LINK:
            case SOONG:
            case BUILD_NINJA:
            case INTERMEDIATES:
                // ソース再走査系の図種はプロジェクトルートが必須。
                // ルートを持つ generatePuml(request, cache) 経路から呼ぶこと。
                throw new IllegalStateException(request.getKind()
                        + " diagram requires a project root; "
                        + "call generatePuml(request, cache) instead");
            default:
                throw new IllegalStateException("Unknown diagram kind: " + request.getKind());
        }
    }

    private static String generateClassDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            List<JavaClassInfo> scoped = applyScope(classes, request.getScope(),
                    index != null ? index.moduleMap() : null);
            int originalTotal = (classes != null) ? classes.size() : 0;
            int scopedTotal = scoped.size();
            int maxClasses = request.getScope() != null ? request.getScope().getMaxClasses() : 0;
            if (index != null) {
                scoped = promoteToDetailed(scoped, index);
            }
            PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            o.maxClasses = maxClasses;
            o.interactiveLinks = request.isInteractiveLinks();
            // Setting → Options (GUI で永続化されたクラス図設定の既定)
            applyClassDiagramSettings(o);
            // Scope → Options (Scope に明示値があれば上書き)
            applyScopeToClassOptions(request.getScope(), o);
            // 機能A: 継承先の単純名を FQN へ解決する resolver (index/depIndex があるとき)。
            if (o.markExternalSupertypes && index != null) {
                o.supertypeResolver = new juml.core.refs.NameResolver(index, depIndex)::resolve;
            }
            // 依存 JAR (リポジトリ同梱のローカル JAR 含む) に実在するクラスは
            // prefix 判定に載らなくても <<external>> 扱いにする。
            if (depIndex != null) {
                o.dependencyClassPredicate = fqn -> depIndex.resolve(fqn).isPresent();
            }
            if (scopedTotal < originalTotal) {
                o.footerWarning = "scope filter: " + scopedTotal + " of "
                        + originalTotal + " classes";
            }
            return PlantUmlClassDiagram.generate(scoped, o);
    }

    private static String generatePackageDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            List<JavaClassInfo> scoped = applyScope(classes, request.getScope(),
                    index != null ? index.moduleMap() : null);
            PlantUmlPackageDiagram.Options o = new PlantUmlPackageDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlPackageDiagram.generate(scoped, o);
    }

    private static String generateSequenceDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            String cls = request.getSequenceEntryClass();
            String method = request.getSequenceEntryMethod();
            if (cls == null || cls.isEmpty() || method == null || method.isEmpty()) {
                throw new IllegalArgumentException(
                        "Sequence diagram requires entry Class.method");
            }
            List<JavaClassInfo> source = classes != null
                    ? promoteToDetailed(classes, index) : classes;
            PlantUmlSequenceDiagram.Options o = new PlantUmlSequenceDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            o.dependencyIndex = depIndex;
            applySequenceCommentSettings(o);
            Set<String> hidden = request.getSequenceHiddenParticipants();
            if (hidden != null && !hidden.isEmpty()) {
                o.hiddenParticipants = hidden;
            }
            return PlantUmlSequenceDiagram.generate(source, cls, method, o);
    }

    private static String generateActivityDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            String cls = request.getActivityEntryClass();
            String method = request.getActivityEntryMethod();
            if (cls == null || cls.isEmpty() || method == null || method.isEmpty()) {
                throw new IllegalArgumentException(
                        "Activity diagram requires entry Class.method");
            }
            List<JavaClassInfo> source = classes != null
                    ? promoteToDetailed(classes, index) : classes;
            PlantUmlActivityDiagram.Options o = new PlantUmlActivityDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            applyActivityDetailSettings(o);
            return PlantUmlActivityDiagram.generate(source, cls, method, o);
    }

    private static String generateComponentDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            PlantUmlComponentDiagram.Options o = new PlantUmlComponentDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlComponentDiagram.generate(analysis, o);
    }

    private static String generateDependencyDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            PlantUmlGradleDependencyGraph.Options o =
                    new PlantUmlGradleDependencyGraph.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlGradleDependencyGraph.generate(analysis, o);
    }

    private static String generateManifestDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            PlantUmlManifestDiagram.Options o = new PlantUmlManifestDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlManifestDiagram.generate(analysis, o);
    }

    private static String generateCommonDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            List<JavaClassInfo> scoped = applyScope(classes, request.getScope(),
                    index != null ? index.moduleMap() : null);
            if (index != null) {
                scoped = promoteToDetailed(scoped, index);
            }
            PlantUmlCommonClassesDiagram.Options o =
                    new PlantUmlCommonClassesDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlCommonClassesDiagram.generate(scoped, o);
    }

    private static String generateLayoutDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            PlantUmlLayoutDiagram.Options o = new PlantUmlLayoutDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlLayoutDiagram.generate(lookupLayout(request, analysis), o);
    }

    private static String generateLayoutScreenDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            AndroidLayoutInfo layout = lookupLayout(request, analysis);
            PlantUmlLayoutScreenDiagram.Options o = new PlantUmlLayoutScreenDiagram.Options();
            // @string/foo をプロジェクトの strings.xml で実文言へ解決する
            o.stringResolver = analysis::resolveString;
            return PlantUmlLayoutScreenDiagram.generate(layout, o);
    }

    /**
     * LAYOUT_RENDER: 実際の {@code layout_*} 値で計測した実寸レイアウトを SVG として返す。
     * 返り値は PlantUML ではなく単体表示可能な SVG 文字列で、レンダラ側 ({@link
     * juml.core.formats.uml.PlantUmlRenderer#renderSvg}) が SVG をそのまま通す。
     */
    private static String generateLayoutRenderDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            AndroidLayoutInfo layout = lookupLayout(request, analysis);
            juml.core.formats.android.render.AndroidLayoutSvgRenderer.Options o =
                    new juml.core.formats.android.render.AndroidLayoutSvgRenderer.Options();
            o.stringResolver = analysis::resolveString;
            return juml.core.formats.android.render.AndroidLayoutSvgRenderer.render(layout, o);
    }

    /** LAYOUT / LAYOUT_SCREEN 共通: layoutKey から {@link AndroidLayoutInfo} を引く。 */
    private static AndroidLayoutInfo lookupLayout(DiagramRequest request,
                                                  AndroidProjectAnalysis analysis) {
            String key = request.getLayoutKey();
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException(
                        "Layout diagram requires layoutKey (select a layout file)");
            }
            if (analysis == null) {
                throw new IllegalStateException(
                        "Layout diagram requires Android project analysis");
            }
            AndroidLayoutInfo layout = analysis.findLayoutByKey(key);
            if (layout == null) {
                throw new IllegalArgumentException("Layout not found for key: " + key);
            }
            return layout;
    }

    private static String generateNavigationDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            String navKey = request.getNavigationGraphKey();
            if (navKey == null || navKey.isEmpty()) {
                throw new IllegalArgumentException(
                        "Navigation diagram requires navigationGraphKey"
                                + " (select a navigation file)");
            }
            if (analysis == null) {
                throw new IllegalStateException(
                        "Navigation diagram requires Android project analysis");
            }
            AndroidNavigationGraphInfo nav = analysis.findNavigationByKey(navKey);
            if (nav == null) {
                throw new IllegalArgumentException(
                        "Navigation graph not found for key: " + navKey);
            }
            PlantUmlNavigationGraphDiagram.Options o =
                    new PlantUmlNavigationGraphDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlNavigationGraphDiagram.generate(nav, o);
    }

    private static String generateModuleDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            PlantUmlModuleDiagram.Options o = new PlantUmlModuleDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            return PlantUmlModuleDiagram.generate(classes, o);
    }

    private static String generateInheritanceDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            List<JavaClassInfo> scoped = applyScope(classes, request.getScope(),
                    index != null ? index.moduleMap() : null);
            if (index != null) {
                scoped = promoteToDetailed(scoped, index);
            }
            PlantUmlClassDiagram.Options o = new PlantUmlClassDiagram.Options();
            // Setting から共通設定を先に適用し、その後 INHERITANCE 固定値で上書き
            applyClassDiagramSettings(o);
            applyScopeToClassOptions(request.getScope(), o);
            // 継承図固有: 関係線のみ・クラス名のみ・上から下レイアウト
            o.showFields = false;
            o.showMethods = false;
            o.showVisibility = false;
            o.showInheritance = true;
            o.showImplementations = true;
            o.showUsageRelations = false;
            o.showAnnotations = false;
            o.showComments = false;
            o.showEnumConstants = false;
            o.showFinal = false;
            o.showInlineFunctions = false;
            // パッケージボックスを廃止: package ネストが横幅を膨らませるため
            o.groupByPackage = false;
            o.excludeExternalLibraries = false;
            o.markAaosCategories = true;
            o.topToBottomDirection = true;
            // 兄弟ノードが 5 個を超えたら次の行に折り返す
            o.maxSiblingsPerRow = 5;
            o.includeLegend = request.isIncludeLegend();
            o.interactiveLinks = request.isInteractiveLinks();
            return PlantUmlClassDiagram.generate(scoped, o);
    }

    private static String generateCallgraphDiagram(DiagramRequest request,
                                  AndroidProjectAnalysis analysis,
                                  List<JavaClassInfo> classes,
                                  ClassIndex index,
                                  DependencyJarIndex depIndex) {
            String cls = request.getSequenceEntryClass();
            String method = request.getSequenceEntryMethod();
            if (cls == null || cls.isEmpty() || method == null || method.isEmpty()) {
                throw new IllegalArgumentException(
                        "Call graph diagram requires entry Class.method");
            }
            List<JavaClassInfo> source = classes != null
                    ? promoteToDetailed(classes, index) : classes;
            PlantUmlCallGraphDiagram.Options o = new PlantUmlCallGraphDiagram.Options();
            o.includeLegend = request.isIncludeLegend();
            applyCallGraphSettings(o);
            return PlantUmlCallGraphDiagram.generate(source, cls, method, o);
    }

    /**
     * コールグラフの最大深さ設定を {@link juml.SettingManager} から読み出して反映する。
     */
    private static void applyCallGraphSettings(PlantUmlCallGraphDiagram.Options o) {
        try {
            juml.Setting s = juml.SettingManager.getInstance().getSetting();
            if (s == null) {
                return;
            }
            int depth = s.getCallGraphMaxDepth();
            if (depth > 0) {
                o.maxDepth = depth;
            }
        } catch (RuntimeException ex) {
            juml.util.AppLog.warn(juml.util.ErrorCode.DIAG_001, "DiagramService",
                    "call graph settings unavailable — using defaults", ex);
        }
    }

    /**
     * シーケンス図のコメント表示・修飾設定を {@link juml.SettingManager} から
     * 読み出して {@link PlantUmlSequenceDiagram.Options} に反映する。
     * SettingManager が利用できない場合 (テスト / 単体実行) は既定値のまま。
     */
    private static void applySequenceCommentSettings(PlantUmlSequenceDiagram.Options o) {
        try {
            juml.Setting s = juml.SettingManager.getInstance().getSetting();
            if (s == null) {
                return;
            }
            o.showComments = s.isSequenceShowComments();
            if ("NOTE".equalsIgnoreCase(s.getSequenceCommentStyle())) {
                o.commentStyle = juml.core.formats.uml.PlantUmlClassDiagram.CommentStyle.NOTE;
            } else {
                o.commentStyle = juml.core.formats.uml.PlantUmlClassDiagram.CommentStyle.INLINE;
            }
            if ("PARTICIPANT_TOP".equalsIgnoreCase(s.getSequenceCommentPlacement())) {
                o.commentPlacement =
                        PlantUmlSequenceDiagram.CommentPlacement.PARTICIPANT_TOP;
            } else {
                o.commentPlacement =
                        PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE;
            }
            o.qualifyMethodNames = s.isSequenceQualifyMethodNames();
            o.maxDepth = s.getSequenceMaxDepth();
            o.showCallArguments = s.isSequenceShowCallArguments();
        } catch (RuntimeException ex) {
            // 設定取得失敗時は既定値のまま (showComments=true, INLINE, AT_CALL_SITE)
            juml.util.AppLog.warn(juml.util.ErrorCode.DIAG_001, "DiagramService",
                    "sequence diagram settings unavailable — using defaults", ex);
        }
    }

    /**
     * アクティビティ図の詳細表示設定 ({@code activity.*}) を {@link juml.SettingManager}
     * から読み出して {@link PlantUmlActivityDiagram.Options} に反映する。
     * SettingManager が利用できない場合 (テスト / 単体実行) は既定値のまま。
     */
    private static void applyActivityDetailSettings(PlantUmlActivityDiagram.Options o) {
        try {
            juml.Setting s = juml.SettingManager.getInstance().getSetting();
            if (s == null) {
                return;
            }
            o.expandInlineCallbacks = s.isActivityExpandInlineCallbacks();
            o.showLocalVars = s.isActivityShowLocalVars();
            o.showAssignments = s.isActivityShowAssignments();
            o.showCallArguments = s.isActivityShowCallArguments();
            o.showInlineComments = s.isActivityShowInlineComments();
        } catch (RuntimeException ex) {
            // 設定取得失敗時は既定値のまま (すべて表示)
            juml.util.AppLog.warn(juml.util.ErrorCode.DIAG_001, "DiagramService",
                    "activity diagram settings unavailable — using defaults", ex);
        }
    }

    /** GUI 永続化のクラス図設定 ({@code classDiagram.*}) を Options に反映 (未取得時は既定値)。 */
    private static void applyClassDiagramSettings(PlantUmlClassDiagram.Options o) {
        try {
            juml.Setting s = juml.SettingManager.getInstance().getSetting();
            if (s == null) {
                return;
            }
            o.showFields = s.isClassDiagramShowFields();
            o.showMethods = s.isClassDiagramShowMethods();
            o.showAnnotations = s.isClassDiagramShowAnnotations();
            o.publicOnly = s.isClassDiagramPublicOnly();
            o.excludeExternalLibraries = s.isClassDiagramExcludeExternal();
            o.markExternalSupertypes = s.isClassDiagramMarkExternalSupertypes();
            o.colorCodeRelations = s.isClassDiagramColorCodeRelations();
            o.commentMaxLength = s.getClassDiagramCommentMaxLength();
            String csv = s.getClassDiagramHiddenAnnotations();
            if (csv != null) {
                java.util.Set<String> set = new java.util.LinkedHashSet<>();
                for (String tok : csv.split(",")) {
                    String t = tok.trim();
                    if (!t.isEmpty()) {
                        set.add(t);
                    }
                }
                o.hiddenAnnotations = new java.util.HashSet<>(set);
            }
        } catch (RuntimeException ex) {
            // 設定取得失敗時は既定値のまま
            juml.util.AppLog.warn(juml.util.ErrorCode.DIAG_001, "DiagramService",
                    "class diagram settings unavailable — using defaults", ex);
        }
    }

    /**
     * スコープに保持された関連線フィルタ・可視性フィルタを
     * {@link PlantUmlClassDiagram.Options} に転写する。
     * クラスリストの除外は {@link #applyScope} で完了している前提なので、
     * Options 側の {@code excludeExternalLibraries} は触らない (二重除外を避ける)。
     */
    static void applyScopeToClassOptions(DiagramScope scope, PlantUmlClassDiagram.Options o) {
        if (scope == null || o == null) {
            return;
        }
        java.util.EnumSet<RelationKind> kinds = scope.getRelationKinds();
        if (!kinds.containsAll(java.util.EnumSet.allOf(RelationKind.class))) {
            o.showInheritance = kinds.contains(RelationKind.INHERITANCE);
            o.showImplementations = kinds.contains(RelationKind.IMPLEMENTATION);
            o.showUsageRelations = kinds.contains(RelationKind.USAGE);
        }
        if (scope.getVisibilityFilter() == VisibilityFilter.PUBLIC_ONLY) {
            o.publicOnly = true;
        }
        if (scope.getFocusClass() != null && !scope.getFocusClass().isEmpty()) {
            o.focusClass = scope.getFocusClass();
        }
    }

    /**
     * スコープに従って ClassInfo リストを絞り込む。
     *
     * <p>順序: module → package include → package exclude → external libraries →
     * regex → seed + BFS by neighborHops。
     * maxClasses 上限は呼び出し側 (PlantUmlClassDiagram.Options.maxClasses) で適用する。</p>
     */
    static List<JavaClassInfo> applyScope(List<JavaClassInfo> classes, DiagramScope scope,
                                          Map<String, String> qnToModule) {
        if (classes == null || classes.isEmpty() || scope == null || scope.isEmpty()) {
            return classes != null ? classes : new ArrayList<>();
        }
        List<JavaClassInfo> result = new ArrayList<>(classes);

        // 1. module フィルタ
        if (!scope.getIncludedModules().isEmpty() && qnToModule != null && !qnToModule.isEmpty()) {
            Set<String> mods = scope.getIncludedModules();
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                String mod = qnToModule.get(c.getQualifiedName());
                if (mod != null && mods.contains(mod)) {
                    next.add(c);
                }
            }
            result = next;
        }

        // 2. package include (前方一致または完全一致)
        if (!scope.getIncludedPackages().isEmpty()) {
            Set<String> pkgs = scope.getIncludedPackages();
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                String pkg = c.getPackageName() == null ? "" : c.getPackageName();
                for (String allowed : pkgs) {
                    if (pkg.equals(allowed) || pkg.startsWith(allowed + ".")) {
                        next.add(c);
                        break;
                    }
                }
            }
            result = next;
        }

        // 2.5. package exclude (前方一致または完全一致)
        if (!scope.getExcludedPackages().isEmpty()) {
            Set<String> excluded = scope.getExcludedPackages();
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                String pkg = c.getPackageName() == null ? "" : c.getPackageName();
                boolean drop = false;
                for (String ex : excluded) {
                    if (pkg.equals(ex) || pkg.startsWith(ex + ".")) {
                        drop = true;
                        break;
                    }
                }
                if (!drop) {
                    next.add(c);
                }
            }
            result = next;
        }

        // 2.6. exclude external libraries (Origin + prefix の 2 段判定)
        if (scope.isExcludeExternalLibraries()) {
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                JavaClassInfo.Origin origin = c.getOrigin();
                if (origin == JavaClassInfo.Origin.EXTERNAL_JAR
                        || origin == JavaClassInfo.Origin.MISSING_JAR) {
                    continue;
                }
                if (juml.core.formats.uml.ExternalPackageMatcher.isExternal(
                        c.getPackageName(), scope.getExternalPackagePrefixes())) {
                    continue;
                }
                next.add(c);
            }
            result = next;
        }

        // 3. regex フィルタ
        Pattern p = scope.getClassNameRegex();
        if (p != null) {
            List<JavaClassInfo> next = new ArrayList<>(result.size());
            for (JavaClassInfo c : result) {
                if (p.matcher(c.getSimpleName()).find()
                        || p.matcher(c.getQualifiedName()).find()) {
                    next.add(c);
                }
            }
            result = next;
        }

        // 4. seed + neighborHops BFS
        if (!scope.getSeedQualifiedNames().isEmpty()) {
            result = bfsNeighbors(result, scope.getSeedQualifiedNames(),
                    scope.getNeighborHops());
        }

        return result;
    }

    /** シード集合から継承/実装/フィールド型を辿り、N hop 以内のクラスのみを返す。 */
    private static List<JavaClassInfo> bfsNeighbors(List<JavaClassInfo> pool,
                                                     Set<String> seeds, int hops) {
        // pool 内のクラスを QN とシンプル名の両方で引けるようマップ化
        Map<String, JavaClassInfo> byQn = new HashMap<>();
        Map<String, String> bySimple = new HashMap<>();
        for (JavaClassInfo c : pool) {
            byQn.put(c.getQualifiedName(), c);
            bySimple.putIfAbsent(c.getSimpleName(), c.getQualifiedName());
        }
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> frontier = new ArrayDeque<>();
        Map<String, Integer> depth = new HashMap<>();
        for (String s : seeds) {
            if (byQn.containsKey(s)) {
                frontier.add(s);
                depth.put(s, 0);
                visited.add(s);
            } else {
                // simple 名一致をフォールバック
                String qn = bySimple.get(s);
                if (qn != null) {
                    frontier.add(qn);
                    depth.put(qn, 0);
                    visited.add(qn);
                }
            }
        }
        while (!frontier.isEmpty()) {
            String qn = frontier.poll();
            int d = depth.getOrDefault(qn, 0);
            if (d >= hops) {
                continue;
            }
            JavaClassInfo c = byQn.get(qn);
            if (c == null) {
                continue;
            }
            for (String n : neighbors(c, byQn, bySimple)) {
                if (visited.add(n)) {
                    depth.put(n, d + 1);
                    frontier.add(n);
                }
            }
        }
        List<JavaClassInfo> result = new ArrayList<>(visited.size());
        for (String qn : visited) {
            JavaClassInfo c = byQn.get(qn);
            if (c != null) {
                result.add(c);
            }
        }
        return result;
    }

    private static Set<String> neighbors(JavaClassInfo c,
                                          Map<String, JavaClassInfo> byQn,
                                          Map<String, String> bySimple) {
        Set<String> out = new LinkedHashSet<>();
        addNeighbor(c.getSuperClass(), byQn, bySimple, out);
        for (String iface : c.getInterfaces()) {
            addNeighbor(iface, byQn, bySimple, out);
        }
        if (c.getFields() != null) {
            for (JavaFieldInfo f : c.getFields()) {
                if (f.getType() != null) {
                    // 単純な型名抽出 (ジェネリクスや配列は雑に分解)
                    for (String tok : f.getType().split("[<>,\\[\\]\\s?]+")) {
                        addNeighbor(tok.trim(), byQn, bySimple, out);
                    }
                }
            }
        }
        return out;
    }

    private static void addNeighbor(String type, Map<String, JavaClassInfo> byQn,
                                     Map<String, String> bySimple, Set<String> out) {
        if (type == null || type.isEmpty()) {
            return;
        }
        if (byQn.containsKey(type)) {
            out.add(type);
            return;
        }
        String qn = bySimple.get(type);
        if (qn != null) {
            out.add(qn);
        }
    }

    /** スコープで残ったクラスを Stage B 詳細に昇格させた新リストを返す。 */
    private static List<JavaClassInfo> promoteToDetailed(List<JavaClassInfo> scoped,
                                                          ClassIndex index) {
        if (index == null || scoped == null || scoped.isEmpty()) {
            return scoped;
        }
        List<JavaClassInfo> result = new ArrayList<>(scoped.size());
        Set<String> seen = new HashSet<>();
        for (JavaClassInfo c : scoped) {
            if (c.isDetailed()) {
                result.add(c);
                continue;
            }
            String qn = c.getQualifiedName();
            if (!seen.add(qn)) {
                continue;
            }
            JavaClassInfo d = index.detail(qn, ErrorListener.silent());
            result.add(d != null ? d : c);
        }
        return result;
    }
}
