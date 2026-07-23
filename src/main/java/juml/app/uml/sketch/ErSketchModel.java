// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集する ER 図モデル (エンティティ群 + リレーション群)。
 *
 * <p>PlantUML テキストとの相互変換は {@link ErSketchCodec} が担う。このクラスは
 * 構造の保持と基本操作 (追加・削除・別名解決) のみ。エンティティは表示名・別名 (alias)・
 * 列 ({@link Column}: 主キーか / 名前 / 型) を持ち、リレーションは crow's-foot 記法の
 * 左右カーディナリティ ({@link Cardinality}) とラベルを持つ。</p>
 */
public final class ErSketchModel {

    private final List<Entity> entities = new ArrayList<>();
    private final List<Relation> relations = new ArrayList<>();
    /** {@code @startuml <name>} の名前サフィックス (無ければ空文字)。往復で保全する。 */
    private String diagramName = "";

    public List<Entity> getEntities() {
        return entities;
    }

    public List<Relation> getRelations() {
        return relations;
    }

    /** {@code @startuml} に付いた図名 (無ければ空文字)。 */
    public String getDiagramName() {
        return diagramName;
    }

    /** {@code @startuml} の図名を設定する (null は空文字として扱う)。 */
    public void setDiagramName(String name) {
        this.diagramName = name != null ? name : "";
    }

    /** 別名 (alias) でエンティティを探す (無ければ null)。 */
    public Entity findEntity(String alias) {
        for (Entity e : entities) {
            if (e.getAlias().equals(alias)) {
                return e;
            }
        }
        return null;
    }

    /** {@code base}, {@code base2}, ... の形式で未使用の別名を作る。 */
    public String uniqueAlias(String base) {
        if (findEntity(base) == null) {
            return base;
        }
        int n = 2;
        while (findEntity(base + n) != null) {
            n++;
        }
        return base + n;
    }

    /** エンティティを削除し、そのエンティティに接続するリレーションもまとめて取り除く。 */
    public void removeEntity(Entity target) {
        entities.remove(target);
        relations.removeIf(r -> r.touches(target.getAlias()));
    }

    /** 別名の変更に追随してリレーションの端点も付け替える。 */
    public void renameEntity(Entity target, String newAlias) {
        String old = target.getAlias();
        target.setAlias(newAlias);
        for (Relation r : relations) {
            if (r.getLeft().equals(old)) {
                r.setLeft(newAlias);
            }
            if (r.getRight().equals(old)) {
                r.setRight(newAlias);
            }
        }
    }

    // -------------------------------------------------------------------------
    // カーディナリティ (crow's-foot 記法)
    // -------------------------------------------------------------------------

    /**
     * crow's-foot (IE) 記法のカーディナリティ。左端 ({@link #left()}) と右端
     * ({@link #right()}) で記号の向きが反転するため、両方のトークンを保持する。
     * 例: 「ちょうど 1」は左端 {@code ||}・右端 {@code ||}、「0 以上」は左端 {@code }o}・
     * 右端 {@code o{}。リレーション演算子は {@code 左端 + "--" + 右端} で組み立てる。
     */
    public enum Cardinality {
        /** ちょうど 1: 左 {@code ||} / 右 {@code ||}。 */
        EXACTLY_ONE("||", "||"),
        /** 0 または 1: 左 {@code |o} / 右 {@code o|}。 */
        ZERO_OR_ONE("|o", "o|"),
        /** 0 以上: 左 {@code }o} / 右 {@code o{}。 */
        ZERO_OR_MANY("}o", "o{"),
        /** 1 以上: 左 {@code }|} / 右 {@code |{}。 */
        ONE_OR_MANY("}|", "|{");

        private final String left;
        private final String right;

        Cardinality(String left, String right) {
            this.left = left;
            this.right = right;
        }

        /** 左端 (左エンティティ側) のトークン。 */
        public String left() {
            return left;
        }

        /** 右端 (右エンティティ側) のトークン。 */
        public String right() {
            return right;
        }

        /** 左端トークンから種別を引く (未対応表記は null)。 */
        public static Cardinality fromLeft(String token) {
            for (Cardinality c : values()) {
                if (c.left.equals(token)) {
                    return c;
                }
            }
            return null;
        }

        /** 右端トークンから種別を引く (未対応表記は null)。 */
        public static Cardinality fromRight(String token) {
            for (Cardinality c : values()) {
                if (c.right.equals(token)) {
                    return c;
                }
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 列
    // -------------------------------------------------------------------------

    /** ER エンティティの列 1 個分 (主キーか / 名前 / 型)。 */
    public static final class Column {

        private boolean primaryKey;
        private String name;
        private String type;

        public Column(boolean primaryKey, String name, String type) {
            this.primaryKey = primaryKey;
            this.name = name;
            this.type = type != null ? type : "";
        }

        public boolean isPrimaryKey() {
            return primaryKey;
        }

        public void setPrimaryKey(boolean primaryKey) {
            this.primaryKey = primaryKey;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /** 列の型 (無ければ空文字)。 */
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type != null ? type : "";
        }
    }

    // -------------------------------------------------------------------------
    // エンティティ
    // -------------------------------------------------------------------------

    /**
     * ER 図のエンティティ 1 個分。{@code alias} はリレーション端点・{@code '@pos}・
     * 一意性に使う識別子。{@code displayName} は空白を含みうる表示名で、{@code alias} と
     * 異なる場合のみ {@code "displayName" as alias} 形式で出力する。
     */
    public static final class Entity {

        private String alias;
        private String displayName;
        private final List<Column> columns = new ArrayList<>();
        private int x;
        private int y;

        public Entity(String alias, String displayName, int x, int y) {
            this.alias = alias;
            this.displayName = displayName;
            this.x = x;
            this.y = y;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        /** 表示名 (無ければ null。描画・出力では {@link #displayText()} を使う)。 */
        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        /** 描画・出力に使う表示テキスト (displayName が空なら alias)。 */
        public String displayText() {
            return displayName != null && !displayName.isEmpty() ? displayName : alias;
        }

        public List<Column> getColumns() {
            return columns;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public void moveTo(int newX, int newY) {
            this.x = newX;
            this.y = newY;
        }
    }

    // -------------------------------------------------------------------------
    // リレーション
    // -------------------------------------------------------------------------

    /**
     * ER 図のリレーション (エッジ) 1 本分。PlantUML の
     * {@code left <左端>--<右端> right : label} 表記と 1:1 対応で保持する。
     */
    public static final class Relation {

        private String left;
        private Cardinality leftCard;
        private Cardinality rightCard;
        private String right;
        private String label;

        public Relation(String left, Cardinality leftCard, Cardinality rightCard,
                        String right, String label) {
            this.left = left;
            this.leftCard = leftCard;
            this.rightCard = rightCard;
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

        public Cardinality getLeftCard() {
            return leftCard;
        }

        public void setLeftCard(Cardinality leftCard) {
            this.leftCard = leftCard;
        }

        public Cardinality getRightCard() {
            return rightCard;
        }

        public void setRightCard(Cardinality rightCard) {
            this.rightCard = rightCard;
        }

        /** リレーションラベル (無ければ null)。 */
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        /** PlantUML の crow's-foot 演算子 (例: {@code ||--o{})。 */
        public String arrow() {
            return leftCard.left() + "--" + rightCard.right();
        }

        /** このリレーションが指定 alias に接続しているか。 */
        public boolean touches(String alias) {
            return left.equals(alias) || right.equals(alias);
        }
    }
}
