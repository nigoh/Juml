// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

/**
 * Doxygen の xref 項目 1 件 ({@code @todo} / {@code @bug} / {@code @deprecated}) を表すモデル。
 *
 * <p>doxygen XML では、これらは各 {@code <detaileddescription>} 内の {@code <xrefsect>}
 * ({@code <xreftitle>Todo|Bug|Deprecated</xreftitle>} + {@code <xrefdescription>}) として現れる。
 * R3 でプロジェクト横断の「宿題一覧」として集約表示する。</p>
 */
public final class DoxXrefItem {

    /** xref の種別。 */
    public enum Kind {
        TODO, BUG, DEPRECATED, OTHER;

        /** doxygen の {@code <xreftitle>} 文字列 (Todo / Bug / Deprecated) を Kind に対応づける。 */
        public static Kind fromTitle(String title) {
            if (title == null) {
                return OTHER;
            }
            String t = title.trim().toLowerCase();
            if (t.startsWith("todo")) {
                return TODO;
            }
            if (t.startsWith("bug")) {
                return BUG;
            }
            if (t.startsWith("deprecated")) {
                return DEPRECATED;
            }
            return OTHER;
        }
    }

    private final Kind kind;
    /** 発生箇所 (例: {@code com.example.Foo.doWork} / クラスなら {@code com.example.Foo})。 */
    private final String location;
    private final String description;

    public DoxXrefItem(Kind kind, String location, String description) {
        this.kind = kind != null ? kind : Kind.OTHER;
        this.location = location != null ? location : "";
        this.description = description != null ? description : "";
    }

    public Kind getKind() {
        return kind;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }
}
