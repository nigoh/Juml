// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集する状態遷移図モデル (状態群 + 遷移群)。
 *
 * <p>PlantUML テキストとの相互変換は {@link StateSketchCodec} が担う。
 * このクラスは構造の保持と基本操作 (追加・削除・名前解決) のみ。
 * 擬似状態 {@code [*]} は状態としては保持せず、遷移の端点名として扱う。</p>
 */
public final class StateSketchModel {

    private final List<StateNode> states = new ArrayList<>();
    private final List<StateTransition> transitions = new ArrayList<>();
    /** {@code @startuml <name>} の名前サフィックス (無ければ空文字)。往復で保全する。 */
    private String diagramName = "";

    public List<StateNode> getStates() {
        return states;
    }

    public List<StateTransition> getTransitions() {
        return transitions;
    }

    /** {@code @startuml} に付いた図名 (無ければ空文字)。 */
    public String getDiagramName() {
        return diagramName;
    }

    /** {@code @startuml} の図名を設定する (null は空文字として扱う)。 */
    public void setDiagramName(String name) {
        this.diagramName = name != null ? name : "";
    }

    /** 名前で状態を探す (無ければ null)。 */
    public StateNode findState(String name) {
        for (StateNode s : states) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    /** {@code base}, {@code base2}, ... の形式で未使用の状態名を作る。 */
    public String uniqueName(String base) {
        if (findState(base) == null) {
            return base;
        }
        int n = 2;
        while (findState(base + n) != null) {
            n++;
        }
        return base + n;
    }

    /** 状態を削除し、その状態に接続する遷移もまとめて取り除く。 */
    public void removeState(StateNode target) {
        states.remove(target);
        transitions.removeIf(t -> t.touches(target.getName()));
    }

    /** 状態名の変更に追随して遷移の端点も付け替える。 */
    public void renameState(StateNode target, String newName) {
        String old = target.getName();
        target.setName(newName);
        for (StateTransition t : transitions) {
            if (t.getFrom().equals(old)) {
                t.setFrom(newName);
            }
            if (t.getTo().equals(old)) {
                t.setTo(newName);
            }
        }
    }
}
