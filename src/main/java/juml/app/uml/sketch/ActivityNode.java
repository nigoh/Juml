// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * アクティビティ図デザイナーのノード 1 個分 (開始・終了・アクション・分岐)。
 *
 * <p>PlantUML の新形式アクティビティ構文に対応する。{@code IF} ノードは
 * then / else の子ブランチ (ノード列) を再帰的に持つ。レイアウトは並び順から
 * 決定的に計算するため座標は持たない。</p>
 */
public final class ActivityNode {

    /** ノードの種類。 */
    public enum Kind {
        /** {@code start} (黒丸)。 */
        START,
        /** {@code stop} (二重丸)。 */
        STOP,
        /** {@code end} (終了マーク)。 */
        END,
        /** {@code :text;} (角丸ボックス)。 */
        ACTION,
        /** {@code if (condition) then (label) ... else (label) ... endif} (ひし形)。 */
        IF
    }

    private final Kind kind;
    /** ACTION のテキスト。 */
    private String text;
    /** IF の条件式。 */
    private String condition;
    /** IF の then 側ラベル (無ければ null)。 */
    private String thenLabel;
    /** IF の else 側ラベル (無ければ null)。 */
    private String elseLabel;
    /** IF の then ブランチ。 */
    private final List<ActivityNode> thenBranch = new ArrayList<>();
    /** IF の else ブランチ (else 節が無い場合は null)。 */
    private List<ActivityNode> elseBranch;

    private ActivityNode(Kind kind) {
        this.kind = kind;
    }

    /** {@code start} / {@code stop} / {@code end} ノードを作る。 */
    public static ActivityNode terminal(Kind kind) {
        if (kind == Kind.ACTION || kind == Kind.IF) {
            throw new IllegalArgumentException("terminal kind required: " + kind);
        }
        return new ActivityNode(kind);
    }

    /** アクションノードを作る。 */
    public static ActivityNode action(String text) {
        ActivityNode n = new ActivityNode(Kind.ACTION);
        n.text = text;
        return n;
    }

    /** 分岐ノードを作る (else ブランチは {@link #ensureElseBranch()} で後付けする)。 */
    public static ActivityNode branch(String condition, String thenLabel, String elseLabel) {
        ActivityNode n = new ActivityNode(Kind.IF);
        n.condition = condition;
        n.thenLabel = thenLabel;
        n.elseLabel = elseLabel;
        return n;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getThenLabel() {
        return thenLabel;
    }

    public void setThenLabel(String thenLabel) {
        this.thenLabel = thenLabel;
    }

    public String getElseLabel() {
        return elseLabel;
    }

    public void setElseLabel(String elseLabel) {
        this.elseLabel = elseLabel;
    }

    public List<ActivityNode> getThenBranch() {
        return thenBranch;
    }

    /** else ブランチ (else 節が無い場合は null)。 */
    public List<ActivityNode> getElseBranch() {
        return elseBranch;
    }

    /** else 節を持たない IF へ空の else ブランチを追加する (既にあればそれを返す)。 */
    public List<ActivityNode> ensureElseBranch() {
        if (elseBranch == null) {
            elseBranch = new ArrayList<>();
        }
        return elseBranch;
    }

    /** else 節を取り除く (中のノードごと捨てる)。 */
    public void removeElseBranch() {
        elseBranch = null;
    }
}
