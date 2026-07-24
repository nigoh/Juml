// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集する配置図 (デプロイ図) モデル (ノード群 + リンク群)。
 *
 * <p>PlantUML テキストとの相互変換は {@link DeploySketchCodec} が担う。
 * このクラスは構造の保持と基本操作 (追加・削除・名前解決) のみ。ノードと
 * リンクの値クラスはこのモデル固有なので入れ子クラスとして同梱する。
 * ノードは入れ子コンテナ ({@code node X { ... }}) として子ノードを持てる。
 * {@link #getNodes()} はトップレベル (最上位) のノードのみを返し、子ノードは
 * {@link DeployNode#getChildren()} で辿る。id はネスト位置に関わらずモデル全体で
 * 一意 (PlantUML のエイリアス名前空間がグローバルなことに対応)。</p>
 */
public final class DeploySketchModel {

    /**
     * 配置図ノード 1 個分 (物理ノード/成果物/データベース/クラウド等)。
     *
     * <p>{@code id} はリンクの端点・{@code '@pos}・一意性に使う識別子 (別名)。
     * {@code label} は空白を含みうる表示名で、{@code id} と異なる場合のみ
     * {@code "label" as id} 形式で出力する。コンテナ ({@link #isContainer()}) は
     * {@code x}/{@code y} が最上位なら盤面の絶対座標、入れ子なら親の内側原点からの
     * 相対座標になる ({@link DeploySketchCodec} 参照)。</p>
     */
    public static final class DeployNode {

        /** ノードの種別 (PlantUML の宣言キーワードに対応)。 */
        public enum Kind {
            NODE("node"),
            ARTIFACT("artifact"),
            DATABASE("database"),
            CLOUD("cloud"),
            COMPONENT("component"),
            RECTANGLE("rectangle"),
            FOLDER("folder"),
            FRAME("frame");

            private final String keyword;

            Kind(String keyword) {
                this.keyword = keyword;
            }

            /** PlantUML 宣言キーワード ({@code node} / {@code database} 等)。 */
            public String keyword() {
                return keyword;
            }

            /** 宣言キーワードから種別を引く (未対応キーワードは null)。 */
            public static Kind fromKeyword(String s) {
                for (Kind k : values()) {
                    if (k.keyword.equals(s)) {
                        return k;
                    }
                }
                return null;
            }
        }

        private Kind kind;
        private String id;
        private String label;
        private int x;
        private int y;
        /** 直属の子ノード (コンテナの中身)。空なら葉ノード扱い (下記 container フラグ参照)。 */
        private final List<DeployNode> children = new ArrayList<>();
        /** 親コンテナ (最上位なら null)。 */
        private DeployNode parent;
        /** 明示的にブロック ({@code { }}) として宣言されたか (子が空でも維持する)。 */
        private boolean container;

        public DeployNode(Kind kind, String id, String label, int x, int y) {
            this.kind = kind;
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
        }

        public Kind getKind() {
            return kind;
        }

        public void setKind(Kind kind) {
            this.kind = kind;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        /** 表示名 (無ければ null。描画・出力では {@link #displayText()} を使う)。 */
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        /** 描画・比較に使う表示テキスト (label が無ければ id)。 */
        public String displayText() {
            return label != null && !label.isEmpty() ? label : id;
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

        /** 直属の子ノード一覧 (可変、追加/削除に使ってよい)。トップレベルなら常に空。 */
        public List<DeployNode> getChildren() {
            return children;
        }

        /** 親コンテナ (最上位なら null)。 */
        public DeployNode getParent() {
            return parent;
        }

        void setParent(DeployNode parent) {
            this.parent = parent;
        }

        /**
         * コンテナ (枠付き) として描画・出力すべきか。子を持てば自動的に true になるほか、
         * 空コンテナ ({@code node X { }}) を保つために明示フラグでも true にできる。
         */
        public boolean isContainer() {
            return container || !children.isEmpty();
        }

        public void setContainer(boolean container) {
            this.container = container;
        }
    }

    /**
     * 配置図のリンク (エッジ) 1 本分。
     * PlantUML の {@code from ARROW to : label} 表記と 1:1 対応で保持する。
     */
    public static final class DeployLink {

        /** 配置図で扱うリンク種別。 */
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

        public DeployLink(String from, Kind kind, String to, String label) {
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

        /** リンクラベル (無ければ null)。 */
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        /** このリンクが指定 id に接続しているか。 */
        public boolean touches(String id) {
            return from.equals(id) || to.equals(id);
        }
    }

    /** トップレベル (最上位) のノードのみ。子ノードは {@link DeployNode#getChildren()} 経由。 */
    private final List<DeployNode> nodes = new ArrayList<>();
    private final List<DeployLink> links = new ArrayList<>();
    private String diagramName = "";

    /** トップレベルのノード一覧 (可変)。全ノード (入れ子含む) は {@link #allNodes()}。 */
    public List<DeployNode> getNodes() {
        return nodes;
    }

    public List<DeployLink> getLinks() {
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

    /** トップレベル・入れ子を問わず全ノードを深さ優先で平坦化して返す (id 探索・位置適用用)。 */
    public List<DeployNode> allNodes() {
        List<DeployNode> out = new ArrayList<>();
        collect(nodes, out);
        return out;
    }

    private static void collect(List<DeployNode> level, List<DeployNode> out) {
        for (DeployNode n : level) {
            out.add(n);
            collect(n.getChildren(), out);
        }
    }

    /** id でノードを探す (入れ子も含めて探索。無ければ null)。 */
    public DeployNode findNode(String id) {
        return findNode(nodes, id);
    }

    private static DeployNode findNode(List<DeployNode> level, String id) {
        for (DeployNode n : level) {
            if (n.getId().equals(id)) {
                return n;
            }
            DeployNode hit = findNode(n.getChildren(), id);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /** {@code base}, {@code base2}, ... の形式で未使用の id を作る (入れ子も含めて一意)。 */
    public String uniqueId(String base) {
        if (findNode(base) == null) {
            return base;
        }
        int n = 2;
        while (findNode(base + n) != null) {
            n++;
        }
        return base + n;
    }

    /** {@code child} を {@code parent} の子として追加する (親をコンテナ化する)。 */
    public void addChild(DeployNode parent, DeployNode child) {
        child.setParent(parent);
        parent.getChildren().add(child);
        parent.setContainer(true);
    }

    /**
     * ノードを削除し、そのノード自身と子孫すべてに接続するリンクをまとめて取り除く
     * (コンテナを消せば中身も一緒に消える)。
     */
    public void removeNode(DeployNode target) {
        List<DeployNode> siblings = target.getParent() != null
                ? target.getParent().getChildren() : nodes;
        siblings.remove(target);
        List<DeployNode> subtree = new ArrayList<>();
        subtree.add(target);
        collect(target.getChildren(), subtree);
        for (DeployNode d : subtree) {
            links.removeIf(l -> l.touches(d.getId()));
        }
    }

    /** id の変更に追随してリンクの端点も付け替える。 */
    public void renameNode(DeployNode target, String newId) {
        String old = target.getId();
        target.setId(newId);
        for (DeployLink l : links) {
            if (l.getFrom().equals(old)) {
                l.setFrom(newId);
            }
            if (l.getTo().equals(old)) {
                l.setTo(newId);
            }
        }
    }
}
