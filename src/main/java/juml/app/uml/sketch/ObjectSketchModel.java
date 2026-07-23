// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集するオブジェクト図モデル (オブジェクト群 + リンク群)。
 *
 * <p>PlantUML テキストとの相互変換は {@link ObjectSketchCodec} が担う。
 * このクラスは構造の保持と基本操作 (追加・削除・名前解決) のみ。オブジェクト 1 個分と
 * リンク 1 本分は同じファイル内の {@link ObjectInstance} / {@link ObjectLink} が表す。</p>
 */
public final class ObjectSketchModel {

    private final List<ObjectInstance> objects = new ArrayList<>();
    private final List<ObjectLink> links = new ArrayList<>();
    /** {@code @startuml <name>} の名前サフィックス (無ければ空文字)。往復で保全する。 */
    private String diagramName = "";

    public List<ObjectInstance> getObjects() {
        return objects;
    }

    public List<ObjectLink> getLinks() {
        return links;
    }

    /** {@code @startuml} に付いた図名 (無ければ空文字)。 */
    public String getDiagramName() {
        return diagramName;
    }

    /** {@code @startuml} の図名を設定する (null は空文字として扱う)。 */
    public void setDiagramName(String name) {
        this.diagramName = name != null ? name : "";
    }

    /** 名前でオブジェクトを探す (無ければ null)。 */
    public ObjectInstance findObject(String name) {
        for (ObjectInstance o : objects) {
            if (o.getName().equals(name)) {
                return o;
            }
        }
        return null;
    }

    /** {@code base}, {@code base2}, ... の形式で未使用のオブジェクト名を作る。 */
    public String uniqueName(String base) {
        if (findObject(base) == null) {
            return base;
        }
        int n = 2;
        while (findObject(base + n) != null) {
            n++;
        }
        return base + n;
    }

    /** オブジェクトを削除し、そのオブジェクトに接続するリンクもまとめて取り除く。 */
    public void removeObject(ObjectInstance target) {
        objects.remove(target);
        links.removeIf(l -> l.touches(target.getName()));
    }

    /** オブジェクト名の変更に追随してリンクの端点も付け替える。 */
    public void renameObject(ObjectInstance target, String newName) {
        String old = target.getName();
        target.setName(newName);
        for (ObjectLink l : links) {
            if (l.getLeft().equals(old)) {
                l.setLeft(newName);
            }
            if (l.getRight().equals(old)) {
                l.setRight(newName);
            }
        }
    }
}

/**
 * GUI デザイナー上のオブジェクト図インスタンス 1 個分の可変データ
 * (名前・任意のステレオタイプ・属性行・キャンバス座標)。
 *
 * <p>属性は {@code name = "Alice"} のような PlantUML の属性表記のまま 1 行 1 属性で保持する
 * (メンバー並びの再解釈をせず往復で保全する)。ステレオタイプは {@code <<...>>} を含めない
 * 素のテキスト (無ければ null/空)。</p>
 */
final class ObjectInstance {

    private String name;
    private String stereotype;
    private final List<String> attributes = new ArrayList<>();
    private int x;
    private int y;

    ObjectInstance(String name, String stereotype, int x, int y) {
        this.name = name;
        this.stereotype = stereotype;
        this.x = x;
        this.y = y;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    /** ステレオタイプ (無ければ null。描画・出力では {@code <<...>>} で囲む)。 */
    String getStereotype() {
        return stereotype;
    }

    void setStereotype(String stereotype) {
        this.stereotype = stereotype;
    }

    /** 属性行 (PlantUML 表記のまま。例: {@code name = "Alice"})。 */
    List<String> getAttributes() {
        return attributes;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    void moveTo(int newX, int newY) {
        this.x = newX;
        this.y = newY;
    }
}

/**
 * GUI デザイナー上のオブジェクト図のリンク (エッジ) 1 本分。
 * PlantUML の {@code left ARROW right : label} 表記と 1:1 対応で保持する。
 */
final class ObjectLink {

    /** オブジェクト図で扱うリンク。 */
    enum Kind {
        /** 関連/矢印: {@code A --> B} (実線 + 開き矢印)。 */
        ARROW("-->"),
        /** リンク (向きなし): {@code A -- B} (実線)。 */
        LINK("--"),
        /** 依存: {@code A ..> B} (破線 + 開き矢印)。 */
        DEPENDENCY("..>");

        private final String arrow;

        Kind(String arrow) {
            this.arrow = arrow;
        }

        /** PlantUML の矢印表記。 */
        String arrow() {
            return arrow;
        }

        /** 矢印表記から種別を引く (未対応表記は null)。 */
        static Kind fromArrow(String s) {
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

    ObjectLink(String left, Kind kind, String right, String label) {
        this.left = left;
        this.kind = kind;
        this.right = right;
        this.label = label;
    }

    String getLeft() {
        return left;
    }

    void setLeft(String left) {
        this.left = left;
    }

    String getRight() {
        return right;
    }

    void setRight(String right) {
        this.right = right;
    }

    Kind getKind() {
        return kind;
    }

    void setKind(Kind kind) {
        this.kind = kind;
    }

    /** リンクラベル (無ければ null)。 */
    String getLabel() {
        return label;
    }

    void setLabel(String label) {
        this.label = label;
    }

    /** このリンクが指定オブジェクト名に接続しているか。 */
    boolean touches(String name) {
        return left.equals(name) || right.equals(name);
    }
}
