// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * GUI デザイナー上の関係 (エッジ) 1 本分。PlantUML の {@code left ARROW right : label}
 * 表記と 1:1 対応で保持する (意味づけは矢印形状の描画にのみ使う)。
 */
public final class SketchRelation {

    /** 対応する PlantUML 矢印。デザイナーで扱うクラス図の基本 7 種に限定する。 */
    public enum Kind {
        /** 継承: {@code Parent <|-- Child} (左に白三角)。 */
        EXTENDS("<|--"),
        /** 実現: {@code Interface <|.. Impl} (左に白三角 + 破線)。 */
        IMPLEMENTS("<|.."),
        /** 関連 (向きあり): {@code A --> B}。 */
        ASSOCIATION("-->"),
        /** 関連 (向きなし): {@code A -- B}。 */
        LINK("--"),
        /** 集約: {@code Whole o-- Part} (左に白ひし形)。 */
        AGGREGATION("o--"),
        /** コンポジション: {@code Whole *-- Part} (左に黒ひし形)。 */
        COMPOSITION("*--"),
        /** 依存: {@code A ..> B} (破線 + 開き矢印)。 */
        DEPENDENCY("..>");

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

    private String left;
    private String right;
    private Kind kind;
    private String label;

    public SketchRelation(String left, Kind kind, String right, String label) {
        this.left = left;
        this.kind = kind;
        this.right = right;
        this.label = label;
    }

    public String getLeft() {
        return left;
    }

    public void setLeft(String left) {
        this.left = left;
    }

    public String getRight() {
        return right;
    }

    public void setRight(String right) {
        this.right = right;
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

    /** この関係が指定クラス名に接続しているか。 */
    public boolean touches(String className) {
        return left.equals(className) || right.equals(className);
    }
}
