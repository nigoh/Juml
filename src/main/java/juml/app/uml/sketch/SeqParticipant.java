// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * シーケンス図デザイナー上の参加者 (ライフライン) 1 本分。
 *
 * <p>並び順は {@link SeqSketchModel#getParticipants()} のリスト順で表す
 * (シーケンス図の横位置はテキストの宣言順で決まるため座標は持たない)。</p>
 */
public final class SeqParticipant {

    /** 参加者の種別 (PlantUML の宣言キーワードに対応)。 */
    public enum Kind {
        PARTICIPANT("participant"),
        ACTOR("actor");

        private final String keyword;

        Kind(String keyword) {
            this.keyword = keyword;
        }

        /** PlantUML 宣言キーワード ({@code participant} / {@code actor})。 */
        public String keyword() {
            return keyword;
        }
    }

    private String name;
    private Kind kind;
    /** 明示宣言されたか (false = メッセージから暗黙生成。再生成時に宣言行を出さない)。 */
    private boolean declared;

    public SeqParticipant(String name, Kind kind, boolean declared) {
        this.name = name;
        this.kind = kind;
        this.declared = declared;
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

    /** 明示宣言された参加者か (暗黙参加者は再生成テキストに宣言行を出さない)。 */
    public boolean isDeclared() {
        return declared;
    }

    public void setDeclared(boolean declared) {
        this.declared = declared;
    }
}
