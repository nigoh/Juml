// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * シーケンス図デザイナーのタイムライン 1 行分 (メッセージ / activate / deactivate)。
 * PlantUML の行と 1:1 対応で保持し、リスト順が時系列を表す。
 */
public final class SeqItem {

    /** タイムライン項目の種類。 */
    public enum Kind {
        /** メッセージ行: {@code from ARROW to : label}。 */
        MESSAGE,
        /** {@code activate <target>} 行。 */
        ACTIVATE,
        /** {@code deactivate <target>} 行。 */
        DEACTIVATE
    }

    /** メッセージの矢印種別 (PlantUML 表記に対応)。 */
    public enum Arrow {
        /** 同期呼び出し: {@code ->} (実線 + 塗り矢印)。 */
        SYNC("->"),
        /** 非同期呼び出し: {@code ->>} (実線 + 開き矢印)。 */
        ASYNC("->>"),
        /** 応答: {@code -->} (破線 + 開き矢印)。 */
        REPLY("-->"),
        /** 非同期応答: {@code -->>} (破線 + 開き矢印)。 */
        ASYNC_REPLY("-->>");

        private final String puml;

        Arrow(String puml) {
            this.puml = puml;
        }

        /** PlantUML の矢印表記。 */
        public String puml() {
            return puml;
        }

        /** 破線で描くべき矢印か (応答系)。 */
        public boolean dashed() {
            return this == REPLY || this == ASYNC_REPLY;
        }

        /** 矢印表記から種別を引く (未対応表記は null)。 */
        public static Arrow fromPuml(String s) {
            for (Arrow a : values()) {
                if (a.puml.equals(s)) {
                    return a;
                }
            }
            return null;
        }
    }

    private final Kind kind;
    // MESSAGE 用
    private String from;
    private String to;
    private Arrow arrow;
    private String label;
    // ACTIVATE / DEACTIVATE 用
    private String target;

    private SeqItem(Kind kind) {
        this.kind = kind;
    }

    /** メッセージ項目を作る (label は無ければ null)。 */
    public static SeqItem message(String from, Arrow arrow, String to, String label) {
        SeqItem m = new SeqItem(Kind.MESSAGE);
        m.from = from;
        m.arrow = arrow;
        m.to = to;
        m.label = label;
        return m;
    }

    /** {@code activate <target>} 項目を作る。 */
    public static SeqItem activate(String target) {
        SeqItem m = new SeqItem(Kind.ACTIVATE);
        m.target = target;
        return m;
    }

    /** {@code deactivate <target>} 項目を作る。 */
    public static SeqItem deactivate(String target) {
        SeqItem m = new SeqItem(Kind.DEACTIVATE);
        m.target = target;
        return m;
    }

    public Kind getKind() {
        return kind;
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

    public Arrow getArrow() {
        return arrow;
    }

    public void setArrow(Arrow arrow) {
        this.arrow = arrow;
    }

    /** メッセージラベル (無ければ null)。 */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** activate / deactivate の対象参加者名。 */
    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    /** この項目が指定参加者名に関係しているか (削除・改名の追随判定用)。 */
    public boolean touches(String name) {
        if (kind == Kind.MESSAGE) {
            return from.equals(name) || to.equals(name);
        }
        return target.equals(name);
    }
}
