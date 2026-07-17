// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * GUI デザイナー上のコンポーネント図要素 1 個分 (コンポーネント or インターフェース)。
 *
 * <p>{@code id} は関係の端点・{@code '@pos}・一意性に使う識別子 (別名)。{@code label} は
 * 空白を含みうる表示名で、{@code id} と異なる場合のみ {@code "label" as id} 形式で出力する。</p>
 */
public final class ComponentNode {

    /** 要素の種別。 */
    public enum Kind {
        COMPONENT("component"),
        INTERFACE("interface");

        private final String keyword;

        Kind(String keyword) {
            this.keyword = keyword;
        }

        /** PlantUML 宣言キーワード ({@code component} / {@code interface})。 */
        public String keyword() {
            return keyword;
        }
    }

    private Kind kind;
    private String id;
    private String label;
    private int x;
    private int y;

    public ComponentNode(Kind kind, String id, String label, int x, int y) {
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
}
