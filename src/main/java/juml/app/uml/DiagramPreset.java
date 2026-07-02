// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.PlantUmlClassDiagram;
import juml.core.formats.uml.UmlGenerator;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * クラス図の表示密度プリセット。
 *
 * <p>{@link #applyTo(PlantUmlClassDiagram.Options)} と {@link #applyTo(DiagramScope.Builder)}
 * の 2 経路で適用する。前者は生成側のフラグを書き換え、後者はスコープ側のフィルタを設定する。
 * {@link #CUSTOM} は何もしない (現状値を維持する)。</p>
 */
public enum DiagramPreset {

    /**
     * 最小限の表示。フィールドを隠し、利用関係/アノテーション/コメントも出さず、
     * クラス数を 40 に制限。public 可視性のみ、外部ライブラリは除外、ヘッダのみ抽出。
     * ドリルダウンの起点として「概要図」を作る用途に最適。
     */
    MINIMAL("Minimal"),
    /**
     * 既定の表示。現状の {@code PlantUmlClassDiagram.Options} 初期値と等価。
     * {@code applyTo()} を呼ぶ前後で出力差分が出ない (互換性の柱)。
     */
    BALANCED("Balanced"),
    /**
     * 詳細表示。NOTE スタイルのコメント、長めの commentMaxLength、Jetpack 装飾、
     * 利用関係の上限も緩める。レビュー用の精緻な図を作る用途。
     */
    DETAILED("Detailed"),
    /** 何もしない (現状の Options/Scope を維持)。 */
    CUSTOM("Custom");

    private final String displayName;

    DiagramPreset(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 現在の表示言語でのプリセット名を返す。
     *
     * <p>{@code diagram.preset.<NAME>} のリソースを引き、未定義なら英語の既定値へ
     * フォールバックする。CLI 解釈 ({@link #fromCli(String)}) は英語の
     * {@code displayName} フィールドを使うため、言語切替の影響を受けない。</p>
     */
    public String getDisplayName() {
        return DiagramKind.localized("diagram.preset." + name(), displayName);
    }

    /** クラス図生成側の Options にプリセットを反映する。CUSTOM は何もしない。 */
    public void applyTo(PlantUmlClassDiagram.Options o) {
        if (o == null) {
            return;
        }
        switch (this) {
            case MINIMAL:
                o.showFields = false;
                o.showMethods = true;
                o.showVisibility = false;
                o.showInheritance = true;
                o.showImplementations = true;
                o.showUsageRelations = false;
                o.showAnnotations = false;
                o.hiddenAnnotations = new HashSet<>(Arrays.asList(
                        "Override", "SuppressWarnings", "Nullable", "NonNull",
                        "Keep", "RestrictTo", "RequiresApi", "Deprecated"));
                o.showEnumConstants = false;
                o.showFinal = false;
                o.showComments = false;
                o.commentStyle = PlantUmlClassDiagram.CommentStyle.INLINE;
                o.commentMaxLength = 0;
                o.groupByPackage = false;
                o.maxClasses = 40;
                o.maxUsagePerClass = 5;
                o.includeLegend = false;
                if (o.jetpack != null) {
                    o.jetpack.enabled = false;
                }
                o.interactiveLinks = true;
                o.publicOnly = true;
                o.excludeExternalLibraries = true;
                break;
            case BALANCED:
                // 既定値と等価 (新しい Options() を作ったときの値)。
                o.showFields = true;
                o.showMethods = true;
                o.showVisibility = true;
                o.showInheritance = true;
                o.showImplementations = true;
                o.showUsageRelations = true;
                o.showAnnotations = true;
                o.hiddenAnnotations = new HashSet<>(Arrays.asList(
                        "Override", "SuppressWarnings"));
                o.showEnumConstants = true;
                o.showFinal = true;
                o.showComments = true;
                o.commentStyle = PlantUmlClassDiagram.CommentStyle.INLINE;
                // 0 = 無制限 (コメントを "..." で省略せず全文表示する)
                o.commentMaxLength = 0;
                o.groupByPackage = true;
                o.maxClasses = 0;
                o.maxUsagePerClass = 30;
                o.includeLegend = true;
                if (o.jetpack != null) {
                    o.jetpack.enabled = false;
                }
                o.interactiveLinks = true;
                o.publicOnly = false;
                o.excludeExternalLibraries = false;
                break;
            case DETAILED:
                o.showFields = true;
                o.showMethods = true;
                o.showVisibility = true;
                o.showInheritance = true;
                o.showImplementations = true;
                o.showUsageRelations = true;
                o.showAnnotations = true;
                o.hiddenAnnotations = new HashSet<>(Arrays.asList("Override"));
                o.showEnumConstants = true;
                o.showFinal = true;
                o.showComments = true;
                o.commentStyle = PlantUmlClassDiagram.CommentStyle.NOTE;
                o.commentMaxLength = 200;
                o.groupByPackage = true;
                o.maxClasses = 0;
                o.maxUsagePerClass = 50;
                o.includeLegend = true;
                if (o.jetpack != null) {
                    o.jetpack.enabled = true;
                }
                o.interactiveLinks = false;
                o.publicOnly = false;
                o.excludeExternalLibraries = false;
                break;
            case CUSTOM:
            default:
                break;
        }
    }

    /** スコープ側のフィルタ (関連線・可視性・外部除外・パースモード) を反映する。CUSTOM は何もしない。 */
    public void applyTo(DiagramScope.Builder b) {
        if (b == null) {
            return;
        }
        switch (this) {
            case MINIMAL:
                b.relationKinds(EnumSet.of(RelationKind.INHERITANCE, RelationKind.IMPLEMENTATION));
                b.visibilityFilter(VisibilityFilter.PUBLIC_ONLY);
                b.excludeExternalLibraries(true);
                b.parseMode(UmlGenerator.ParseMode.HEADERS_ONLY);
                b.maxClasses(40);
                b.preset(MINIMAL);
                break;
            case BALANCED:
                b.relationKinds(EnumSet.allOf(RelationKind.class));
                b.visibilityFilter(VisibilityFilter.ALL);
                b.excludeExternalLibraries(false);
                b.parseMode(UmlGenerator.ParseMode.FULL);
                b.maxClasses(0);
                b.preset(BALANCED);
                break;
            case DETAILED:
                b.relationKinds(EnumSet.allOf(RelationKind.class));
                b.visibilityFilter(VisibilityFilter.ALL);
                b.excludeExternalLibraries(false);
                b.externalPackagePrefixes(new LinkedHashSet<>());
                b.parseMode(UmlGenerator.ParseMode.FULL);
                b.maxClasses(0);
                b.preset(DETAILED);
                break;
            case CUSTOM:
            default:
                break;
        }
    }

    /** CLI フラグ {@code --preset} の値を解釈する。不明値は {@link #CUSTOM} を返す。 */
    public static DiagramPreset fromCli(String name) {
        if (name == null || name.isEmpty()) {
            return CUSTOM;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (DiagramPreset p : values()) {
            if (p.name().toLowerCase(Locale.ROOT).equals(normalized)
                    || p.displayName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return p;
            }
        }
        return CUSTOM;
    }
}
