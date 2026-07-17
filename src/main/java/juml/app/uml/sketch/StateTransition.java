// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * GUI デザイナー上の状態遷移 (エッジ) 1 本分。PlantUML の
 * {@code from --> to : label} 表記と 1:1 対応で保持する。
 *
 * <p>端点 {@code from} / {@code to} は状態名、または初期/終了の擬似状態
 * {@code "[*]"}。ラベル (トリガ/ガード) は無ければ null。</p>
 */
public final class StateTransition {

    /** 初期/終了の擬似状態を表す端点名。 */
    public static final String PSEUDO = "[*]";

    private String from;
    private String to;
    private String label;

    public StateTransition(String from, String to, String label) {
        this.from = from;
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

    /** 遷移ラベル (トリガ/ガード。無ければ null)。 */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** この遷移が指定状態名に接続しているか ({@code [*]} は状態名ではないので対象外)。 */
    public boolean touches(String stateName) {
        return from.equals(stateName) || to.equals(stateName);
    }
}
