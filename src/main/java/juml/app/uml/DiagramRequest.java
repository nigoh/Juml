// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * GUI から {@link DiagramService} に渡す、図種とスコープを束ねた不変リクエスト。
 *
 * <p>{@link DiagramKind#SEQUENCE} の場合のみ {@link #sequenceEntryClass} と
 * {@link #sequenceEntryMethod} を使用する。{@link DiagramKind#ACTIVITY} の場合は
 * 同じ {@link #sequenceEntryClass} / {@link #sequenceEntryMethod} スロットを
 * 「対象クラス・メソッド」として再利用する (両図種が同時に有効になることはない)。
 * {@link DiagramKind#LAYOUT} の場合のみ {@link #layoutKey} を使用する。
 * それ以外の図種ではこれらの値は無視される。</p>
 *
 * <p>大規模プロジェクトの可読性確保のため、{@link DiagramScope} で表示クラスを
 * 絞り込める。null/未指定は「全件」を意味する。
 * シーケンス図では {@link #sequenceHiddenParticipants} で個別 participant の
 * 非表示も可能 (コンパクト化)。</p>
 */
public final class DiagramRequest {

    private final DiagramKind kind;
    private final String sequenceEntryClass;
    private final String sequenceEntryMethod;
    private final boolean includeLegend;
    private final DiagramScope scope;
    private final boolean interactiveLinks;
    private final String layoutKey;
    private final Set<String> sequenceHiddenParticipants;

    public DiagramRequest(DiagramKind kind) {
        this(kind, null, null, true, null, false, null, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend) {
        this(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend, null,
                false, null, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend,
                          DiagramScope scope) {
        this(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend, scope,
                false, null, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend,
                          DiagramScope scope, boolean interactiveLinks) {
        this(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend, scope,
                interactiveLinks, null, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend,
                          DiagramScope scope, boolean interactiveLinks,
                          String layoutKey) {
        this(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend, scope,
                interactiveLinks, layoutKey, null);
    }

    public DiagramRequest(DiagramKind kind, String sequenceEntryClass,
                          String sequenceEntryMethod, boolean includeLegend,
                          DiagramScope scope, boolean interactiveLinks,
                          String layoutKey,
                          Set<String> sequenceHiddenParticipants) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is null");
        }
        this.kind = kind;
        this.sequenceEntryClass = sequenceEntryClass;
        this.sequenceEntryMethod = sequenceEntryMethod;
        this.includeLegend = includeLegend;
        this.scope = scope;
        this.interactiveLinks = interactiveLinks;
        this.layoutKey = layoutKey;
        this.sequenceHiddenParticipants = sequenceHiddenParticipants == null
                || sequenceHiddenParticipants.isEmpty()
                        ? Collections.emptySet()
                        : Collections.unmodifiableSet(
                                new LinkedHashSet<>(sequenceHiddenParticipants));
    }

    /** LAYOUT 図用のショートカットコンストラクタ。 */
    public static DiagramRequest forLayout(String layoutKey, boolean includeLegend) {
        return new DiagramRequest(DiagramKind.LAYOUT, null, null, includeLegend,
                null, false, layoutKey, null);
    }

    /**
     * LAYOUT_SCREEN (Salt ワイヤーフレーム) 図用のショートカットコンストラクタ。
     * LAYOUT と同じく {@link #layoutKey} スロットでレイアウトを指定する。
     */
    public static DiagramRequest forLayoutScreen(String layoutKey, boolean includeLegend) {
        return new DiagramRequest(DiagramKind.LAYOUT_SCREEN, null, null, includeLegend,
                null, false, layoutKey, null);
    }

    /**
     * LAYOUT_RENDER (実寸 SVG) 図用のショートカットコンストラクタ。
     * LAYOUT と同じく {@link #layoutKey} スロットでレイアウトを指定する。
     */
    public static DiagramRequest forLayoutRender(String layoutKey, boolean includeLegend) {
        return new DiagramRequest(DiagramKind.LAYOUT_RENDER, null, null, includeLegend,
                null, false, layoutKey, null);
    }

    /**
     * NAVIGATION 図用のショートカットコンストラクタ。
     * 内部的には {@link #layoutKey} スロットを再利用する
     * (LAYOUT と NAVIGATION は同時に有効化されないため)。
     */
    public static DiagramRequest forNavigationGraph(String navKey, boolean includeLegend) {
        return new DiagramRequest(DiagramKind.NAVIGATION, null, null, includeLegend,
                null, false, navKey, null);
    }

    /**
     * ACTIVITY 図用のショートカットコンストラクタ。
     * {@code entryClass} と {@code entryMethod} は {@link #sequenceEntryClass} /
     * {@link #sequenceEntryMethod} スロットを共用する。SEQUENCE と ACTIVITY は
     * 同時に有効化されないため、フィールド追加を避けて引数数 (checkstyle: max 8)
     * を維持するためのデザイン。
     */
    public static DiagramRequest forActivity(String entryClass, String entryMethod,
                                              boolean includeLegend) {
        return new DiagramRequest(DiagramKind.ACTIVITY, entryClass, entryMethod,
                includeLegend, null, false, null, null);
    }

    /**
     * CALLGRAPH 図用のショートカットコンストラクタ。
     * {@code entryClass} と {@code entryMethod} は {@link #sequenceEntryClass} /
     * {@link #sequenceEntryMethod} スロットを共用する。
     */
    public static DiagramRequest forCallGraph(String entryClass, String entryMethod,
                                               boolean includeLegend) {
        return new DiagramRequest(DiagramKind.CALLGRAPH, entryClass, entryMethod,
                includeLegend, null, false, null, null);
    }

    public DiagramKind getKind() {
        return kind;
    }

    public String getSequenceEntryClass() {
        return sequenceEntryClass;
    }

    public String getSequenceEntryMethod() {
        return sequenceEntryMethod;
    }

    public boolean isIncludeLegend() {
        return includeLegend;
    }

    public DiagramScope getScope() {
        return scope;
    }

    /**
     * スコープだけ差し替えた複製を返す (他の次元は保持)。スコープはタブ固有状態のため、
     * アクティブタブの spec を起点に更新するのに使う (#40)。
     */
    public DiagramRequest withScope(DiagramScope newScope) {
        return new DiagramRequest(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend,
                newScope, interactiveLinks, layoutKey, sequenceHiddenParticipants);
    }

    /**
     * 隠し participant 集合だけ差し替えた複製を返す (他の次元は保持)。参加者フィルタも
     * タブ固有状態のため、アクティブタブの spec を起点に更新するのに使う (#40)。
     */
    public DiagramRequest withSequenceHiddenParticipants(Set<String> hidden) {
        return new DiagramRequest(kind, sequenceEntryClass, sequenceEntryMethod, includeLegend,
                scope, interactiveLinks, layoutKey, hidden);
    }

    /**
     * クラス図の各クラスに {@code [[juml://class/<FQN>]]} を埋め込むか。
     * GUI プレビューで右クリック→メソッド一覧のヒットテストに使うときだけ true にする。
     */
    public boolean isInteractiveLinks() {
        return interactiveLinks;
    }

    /**
     * LAYOUT 図でターゲットとする {@link juml.core.formats.android.AndroidLayoutInfo}
     * のキー (形式: {@code moduleName::sourceSet::qualifier::fileName})。
     * LAYOUT 図以外では null。
     */
    public String getLayoutKey() {
        return layoutKey;
    }

    /**
     * NAVIGATION 図でターゲットとする
     * {@link juml.core.formats.android.AndroidNavigationGraphInfo}
     * のキー (形式: {@code moduleName::sourceSet::fileName})。
     * 内部的には {@link #layoutKey} スロットを再利用しているため、
     * {@code kind != NAVIGATION} の場合は LAYOUT 図用の値が返る点に注意。
     */
    public String getNavigationGraphKey() {
        return layoutKey;
    }

    /**
     * シーケンス図で非表示にする participant 名集合 (不変)。
     * SEQUENCE 図以外では使用されない。空集合なら全 participant 表示。
     */
    public Set<String> getSequenceHiddenParticipants() {
        return sequenceHiddenParticipants;
    }

    /**
     * ACTIVITY 図の起点クラス名 (simple or qualified)。
     * 内部的には {@link #sequenceEntryClass} スロットを再利用しているため、
     * {@code kind != ACTIVITY} の場合は SEQUENCE 図用の値が返る点に注意。
     */
    public String getActivityEntryClass() {
        return sequenceEntryClass;
    }

    /**
     * ACTIVITY 図の起点メソッド名。
     * 内部的には {@link #sequenceEntryMethod} スロットを再利用しているため、
     * {@code kind != ACTIVITY} の場合は SEQUENCE 図用の値が返る点に注意。
     */
    public String getActivityEntryMethod() {
        return sequenceEntryMethod;
    }
}
