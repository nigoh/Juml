// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * Java/Kotlin のコードから 1 つの Android リソースへの参照を表す。
 *
 * <p>{@link ResourceLinkAnalyzer} がソースを正規表現で走査して生成する。
 * 例: {@code setContentView(R.layout.activity_main)} → {@code kind=LAYOUT},
 * {@code resourceName="activity_main"}, {@code ownerClass="MainActivity"}。</p>
 */
public final class ResourceReference {

    /** 参照されるリソースの種別。 */
    public enum Kind {
        /** {@code R.layout.*} — レイアウト。 */
        LAYOUT,
        /** {@code R.string.*} — 文字列リソース。 */
        STRING,
        /** {@code R.id.*} — View ID ({@code findViewById} 等)。 */
        ID,
        /** {@code R.style.*} — スタイル/テーマ ({@code setTheme} / {@code @style/} / {@code android:theme})。 */
        STYLE
    }

    private final String ownerClass;
    private final Kind kind;
    private final String resourceName;
    private final boolean contentBinding;
    private final String file;

    public ResourceReference(String ownerClass, Kind kind, String resourceName,
                             boolean contentBinding, String file) {
        this.ownerClass = ownerClass;
        this.kind = kind;
        this.resourceName = resourceName;
        this.contentBinding = contentBinding;
        this.file = file;
    }

    /** 参照元クラスの単純名 (ファイル先頭の型宣言から推定。不明なら {@code null})。 */
    public String getOwnerClass() {
        return ownerClass;
    }

    public Kind getKind() {
        return kind;
    }

    /** リソース名 ({@code R.layout.activity_main} の {@code activity_main})。 */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * {@code setContentView(...)} / {@code inflate(...)} / ViewBinding 経由で、
     * この参照が「画面 (content view) として束ねられている」ことを示すか。
     * 単なる {@code R.layout.x} 参照より強い結びつきを意味する (LAYOUT のときのみ意味を持つ)。
     */
    public boolean isContentBinding() {
        return contentBinding;
    }

    /** 参照元ファイルパス。 */
    public String getFile() {
        return file;
    }

    @Override
    public String toString() {
        return ownerClass + " -> R." + kind.name().toLowerCase() + "." + resourceName
                + (contentBinding ? " (content)" : "");
    }
}
