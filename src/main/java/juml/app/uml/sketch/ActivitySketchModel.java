// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集するアクティビティ図モデル (ノード列のツリー)。
 *
 * <p>PlantUML テキストとの相互変換は {@link ActivitySketchCodec} が担う。
 * このクラスは構造の保持と基本操作 (探索・削除・並べ替え) のみ。</p>
 */
public final class ActivitySketchModel {

    private final List<ActivityNode> nodes = new ArrayList<>();
    /** {@code @startuml <name>} の名前サフィックス (無ければ空文字)。往復で保全する。 */
    private String diagramName = "";

    /** 最上位のノード列 (IF の中のノードは各ブランチが持つ)。 */
    public List<ActivityNode> getNodes() {
        return nodes;
    }

    /** {@code @startuml} に付いた図名 (無ければ空文字)。 */
    public String getDiagramName() {
        return diagramName;
    }

    /** {@code @startuml} の図名を設定する (null は空文字として扱う)。 */
    public void setDiagramName(String name) {
        this.diagramName = name != null ? name : "";
    }

    /**
     * ノードが属するブランチ (ノード列) を返す (見つからなければ null)。
     * 削除・挿入・並べ替えはこの列に対して行う。
     */
    public List<ActivityNode> branchOf(ActivityNode target) {
        return branchOf(nodes, target);
    }

    private static List<ActivityNode> branchOf(List<ActivityNode> list, ActivityNode target) {
        for (ActivityNode n : list) {
            if (n == target) {
                return list;
            }
            if (n.getKind() == ActivityNode.Kind.IF) {
                List<ActivityNode> found = branchOf(n.getThenBranch(), target);
                if (found == null && n.getElseBranch() != null) {
                    found = branchOf(n.getElseBranch(), target);
                }
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** ノードを (IF なら中のブランチごと) 削除する。 */
    public void remove(ActivityNode target) {
        List<ActivityNode> branch = branchOf(target);
        if (branch != null) {
            branch.remove(target);
        }
    }

    /** {@code target} の直後へノードを挿入する (target が null なら最上位の末尾)。 */
    public void insertAfter(ActivityNode target, ActivityNode node) {
        List<ActivityNode> branch = target != null ? branchOf(target) : null;
        if (branch == null) {
            nodes.add(node);
            return;
        }
        branch.add(branch.indexOf(target) + 1, node);
    }

    /** 同一ブランチ内でノードを 1 つ前後に動かす (端では何もしない)。 */
    public void move(ActivityNode target, int delta) {
        List<ActivityNode> branch = branchOf(target);
        if (branch == null) {
            return;
        }
        int idx = branch.indexOf(target);
        int to = idx + delta;
        if (to < 0 || to >= branch.size()) {
            return;
        }
        branch.remove(idx);
        branch.add(to, target);
    }
}
