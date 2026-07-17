// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * GUI デザイナー上のコンポーネント図の関係 (エッジ) 1 本分。
 * PlantUML の {@code from ARROW to : label} 表記と 1:1 対応で保持する。
 */
public final class ComponentRelation {

    /** コンポーネント図で扱う関係。 */
    public enum Kind {
        /** 関連/矢印: {@code A --> B} (実線 + 開き矢印)。 */
        ARROW("-->"),
        /** 依存: {@code A ..> B} (破線 + 開き矢印)。 */
        DEPENDENCY("..>"),
        /** 接続 (向きなし): {@code A -- B} (実線)。 */
        LINK("--");

        private final String arrow;

        Kind(String arrow) {
            this.arrow = arrow;
        }

        /** PlantUML の矢印表記。 */
        public String arrow() {
            return arrow;
        }

        /** 矢印表記から種別を引く (未対応表記は null)。 */
        public static Kind fromArrow(String s) {
            for (Kind k : values()) {
                if (k.arrow.equals(s)) {
                    return k;
                }
            }
            return null;
        }
    }

    private String from;
    private String to;
    private Kind kind;
    private String label;

    public ComponentRelation(String from, Kind kind, String to, String label) {
        this.from = from;
        this.kind = kind;
        this.to = to;
        this.label = label;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    /** 関係ラベル (無ければ null)。 */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** この関係が指定 id に接続しているか。 */
    public boolean touches(String id) {
        return from.equals(id) || to.equals(id);
    }
}
