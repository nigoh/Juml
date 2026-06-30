// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * UML 図に重ねる Markdown 付箋メモ 1 件。
 *
 * <p>位置・サイズは図 (SVG) 座標系で保持し、表示時に zoom 倍して描画する
 * (図に貼り付いて一緒に拡大縮小する付箋)。{@code text} は Markdown ソース。</p>
 *
 * <p>{@link Anchor#ELEMENT} は特定クラス/メソッドに紐づく注釈用 (後続ラウンドで
 * 利用)。その場合 {@code x},{@code y} は対象要素を基準とした相対オフセットとして扱う。
 * 現行は {@link Anchor#FREE} (図上の自由配置) のみを使う。</p>
 */
public final class DiagramNote {

    /** 付箋の貼り方。 */
    public enum Anchor { FREE, ELEMENT }

    /** 既定の付箋色 (淡い黄色の付箋)。 */
    public static final String DEFAULT_COLOR = "#FFF4B0";

    private String id;
    private Anchor anchor = Anchor.FREE;
    /** FREE: 図座標の左上。ELEMENT: 対象要素からの相対オフセット。 */
    private double x;
    private double y;
    private double width = 280;
    private double height = 160;
    private String text = "";
    private String color = DEFAULT_COLOR;
    /** ロック中は移動・リサイズを禁止する (誤操作で位置がずれるのを防ぐ)。 */
    private boolean locked;
    /** 付箋に付けるタグ (分類・絞り込み用)。重複・空文字は持たない。 */
    private final java.util.List<String> tags = new java.util.ArrayList<>();
    /** ELEMENT アンカー時の対象 (クラス FQN や {@code FQN#method})。FREE では null。 */
    private String targetRef;

    public DiagramNote() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public DiagramNote(double x, double y, double width, double height, String text) {
        this();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.text = text == null ? "" : text;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        if (id != null && !id.isEmpty()) {
            this.id = id;
        }
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor == null ? Anchor.FREE : anchor;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        // リサイズハンドルが収まり、本文も最低限読める下限 (図座標)。
        this.width = Math.max(60, width);
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = Math.max(44, height);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getColor() {
        return color == null || color.isEmpty() ? DEFAULT_COLOR : color;
    }

    public void setColor(String color) {
        this.color = color == null || color.isEmpty() ? DEFAULT_COLOR : color;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /** 付箋のタグ一覧 (コピー)。 */
    public java.util.List<String> getTags() {
        return new java.util.ArrayList<>(tags);
    }

    /** タグ一覧を差し替える。前後空白を除去し、空文字・重複は捨てる。 */
    public void setTags(java.util.List<String> newTags) {
        tags.clear();
        if (newTags != null) {
            for (String s : newTags) {
                String v = s == null ? "" : s.trim();
                if (!v.isEmpty() && !tags.contains(v)) {
                    tags.add(v);
                }
            }
        }
    }

    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String targetRef) {
        this.targetRef = targetRef;
    }

    /**
     * すべてのフィールド (id を含む) を複製した同一性のディープコピーを返す。
     * Undo/Redo のスナップショット保存に使う (元と同じ id を保つ)。
     */
    DiagramNote copyDeep() {
        DiagramNote n = new DiagramNote(x, y, width, height, text);
        n.id = this.id;
        n.anchor = this.anchor;
        n.color = this.color;
        n.locked = this.locked;
        n.targetRef = this.targetRef;
        n.tags.addAll(this.tags);
        return n;
    }

    /**
     * 新しい id を採番した複製を返す (複製 / コピー&ペースト用)。
     * 位置は呼び出し側で必要に応じてずらす。
     */
    DiagramNote duplicate() {
        DiagramNote n = copyDeep();
        n.id = java.util.UUID.randomUUID().toString();
        return n;
    }
}
