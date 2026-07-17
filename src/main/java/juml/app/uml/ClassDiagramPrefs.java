// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * クラス図向け Setting 永続化用 DTO (不変)。
 *
 * <p>{@link StyleSettingsDialog} の戻り値 ({@link StyleSettingsDialog.Result}) と
 * {@link juml.Setting} の間でクラス図設定を受け渡す。設定項目が増えるたびに肥大するため、
 * ファイル長 (checkstyle FileLength) を抑える目的で {@code StyleSettingsDialog} の
 * ネストクラスからトップレベルへ切り出した。後方互換のため引数の少ないコンストラクタは
 * 追加フィールドを既定 (false) で埋めて委譲する。</p>
 */
public final class ClassDiagramPrefs {
    public final boolean showFields;
    public final boolean showMethods;
    public final boolean showAnnotations;
    public final boolean publicOnly;
    public final boolean excludeExternal;
    /** 継承元が JDK 標準/外部ライブラリかをステレオタイプ表示する。 */
    public final boolean markExternalSupertypes;
    /** 関係線を種別ごとに色分け (継承=緑/実装=青/利用=灰破線) するか。 */
    public final boolean colorCodeRelations;
    public final int commentMaxLength;
    /** 表示しないアノテーション名集合 (大括弧なし、例: {"Override", "Nullable"})。 */
    public final java.util.Set<String> hiddenAnnotations;
    /** メンバーの無いコンパートメント (空のフィールド欄/メソッド欄) を隠すか。 */
    public final boolean hideEmptyMembers;
    /** どの関連線とも繋がらない孤立クラスを取り除くか (remove @unlinked)。 */
    public final boolean hideUnlinked;
    /** ステレオタイプごとにクラスボックスを色分けするか。 */
    public final boolean colorCodeStereotypes;

    /** 後方互換コンストラクタ ({@code colorCodeRelations=false})。 */
    public ClassDiagramPrefs(boolean showFields, boolean showMethods,
                              boolean showAnnotations, boolean publicOnly,
                              boolean excludeExternal, boolean markExternalSupertypes,
                              int commentMaxLength,
                              java.util.Set<String> hiddenAnnotations) {
        this(showFields, showMethods, showAnnotations, publicOnly, excludeExternal,
                markExternalSupertypes, false, commentMaxLength, hiddenAnnotations);
    }

    /** 後方互換コンストラクタ ({@code hideEmptyMembers=false, hideUnlinked=false})。 */
    public ClassDiagramPrefs(boolean showFields, boolean showMethods,
                              boolean showAnnotations, boolean publicOnly,
                              boolean excludeExternal, boolean markExternalSupertypes,
                              boolean colorCodeRelations,
                              int commentMaxLength,
                              java.util.Set<String> hiddenAnnotations) {
        this(showFields, showMethods, showAnnotations, publicOnly, excludeExternal,
                markExternalSupertypes, colorCodeRelations, commentMaxLength,
                hiddenAnnotations, false, false);
    }

    /** 後方互換コンストラクタ ({@code colorCodeStereotypes=false})。 */
    public ClassDiagramPrefs(boolean showFields, boolean showMethods,
                              boolean showAnnotations, boolean publicOnly,
                              boolean excludeExternal, boolean markExternalSupertypes,
                              boolean colorCodeRelations,
                              int commentMaxLength,
                              java.util.Set<String> hiddenAnnotations,
                              boolean hideEmptyMembers, boolean hideUnlinked) {
        this(showFields, showMethods, showAnnotations, publicOnly, excludeExternal,
                markExternalSupertypes, colorCodeRelations, commentMaxLength,
                hiddenAnnotations, hideEmptyMembers, hideUnlinked, false);
    }

    public ClassDiagramPrefs(boolean showFields, boolean showMethods,
                              boolean showAnnotations, boolean publicOnly,
                              boolean excludeExternal, boolean markExternalSupertypes,
                              boolean colorCodeRelations,
                              int commentMaxLength,
                              java.util.Set<String> hiddenAnnotations,
                              boolean hideEmptyMembers, boolean hideUnlinked,
                              boolean colorCodeStereotypes) {
        this.showFields = showFields;
        this.showMethods = showMethods;
        this.showAnnotations = showAnnotations;
        this.publicOnly = publicOnly;
        this.excludeExternal = excludeExternal;
        this.markExternalSupertypes = markExternalSupertypes;
        this.colorCodeRelations = colorCodeRelations;
        this.commentMaxLength = Math.max(0, commentMaxLength);
        this.hiddenAnnotations = (hiddenAnnotations == null)
                ? java.util.Collections.emptySet()
                : java.util.Collections.unmodifiableSet(
                        new java.util.LinkedHashSet<>(hiddenAnnotations));
        this.hideEmptyMembers = hideEmptyMembers;
        this.hideUnlinked = hideUnlinked;
        this.colorCodeStereotypes = colorCodeStereotypes;
    }

    /** カンマ区切り文字列に整形 (CSV)。 */
    public String hiddenAnnotationsCsv() {
        return String.join(",", hiddenAnnotations);
    }

    /** CSV からインスタンスを組み立てるユーティリティ。 */
    public static java.util.Set<String> parseCsv(String csv) {
        java.util.Set<String> set = new java.util.LinkedHashSet<>();
        if (csv == null || csv.isEmpty()) {
            return set;
        }
        for (String tok : csv.split(",")) {
            String t = tok.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        return set;
    }

    /** 既定値 (PlantUmlClassDiagram.Options の既定 = BALANCED 相当)。 */
    public static ClassDiagramPrefs defaults() {
        java.util.Set<String> hidden = new java.util.LinkedHashSet<>();
        hidden.add("Override");
        hidden.add("SuppressWarnings");
        return new ClassDiagramPrefs(true, true, true, false, false, false, false, 80, hidden);
    }
}
