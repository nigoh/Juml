// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * GUI デザイナー上のユースケース図の関係 (エッジ) 1 本分。
 * PlantUML の {@code from ARROW to : label} 表記と 1:1 対応で保持する。
 */
public final class UseCaseRelation {

    /** ユースケース図で扱う関係。 */
    public enum Kind {
        /** 関連: {@code Actor --> UseCase} (実線 + 開き矢印)。 */
        ASSOCIATION("-->"),
        /** 依存 (include/extend): {@code A ..> B} (破線 + 開き矢印。ラベルで区別)。 */
        DEPENDENCY("..>"),
        /** 汎化: {@code Child --|> Parent} (実線 + 白三角)。 */
        GENERALIZATION("--|>");

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

    public UseCaseRelation(String from, Kind kind, String to, String label) {
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

    /** 関係ラベル (include/extend の別など。無ければ null)。 */
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
