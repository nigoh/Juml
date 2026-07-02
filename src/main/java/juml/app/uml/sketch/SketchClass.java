// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナー上のクラス 1 個分の可変データ (名前・種別・メンバー・キャンバス座標)。
 */
public final class SketchClass {

    /** クラスの種別 (PlantUML の宣言キーワードに対応)。 */
    public enum Kind {
        CLASS("class"),
        ABSTRACT("abstract class"),
        INTERFACE("interface"),
        ENUM("enum");

        private final String keyword;

        Kind(String keyword) {
            this.keyword = keyword;
        }

        /** PlantUML 宣言キーワード ({@code class} / {@code abstract class} など)。 */
        public String keyword() {
            return keyword;
        }
    }

    private String name;
    private Kind kind;
    private final List<String> fields = new ArrayList<>();
    private final List<String> methods = new ArrayList<>();
    private int x;
    private int y;

    public SketchClass(String name, Kind kind, int x, int y) {
        this.name = name;
        this.kind = kind;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    /** フィールド行 (PlantUML メンバー表記のまま。例: {@code - name : String})。 */
    public List<String> getFields() {
        return fields;
    }

    /** メソッド行 (PlantUML メンバー表記のまま。例: {@code + getName() : String})。 */
    public List<String> getMethods() {
        return methods;
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
