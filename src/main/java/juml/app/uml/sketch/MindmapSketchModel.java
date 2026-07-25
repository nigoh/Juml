// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集するマインドマップモデル (単一ルートの木)。
 *
 * <p>PlantUML テキストとの相互変換は {@link MindmapSketchCodec} が担う。このクラスは
 * 構造の保持と基本操作 (探索・追加・削除・並べ替え・親付け替え) のみ。PlantUML の
 * マインドマップは複数ルート (フォレスト) を許すが、本設計は曖昧さを避けるため
 * <b>単一ルート</b>に絞る (2 本目以降の深さ 1 行は Codec が未対応として扱う)。</p>
 */
public final class MindmapSketchModel {

    /** ルートノード (null = 空図)。 */
    private MindmapNode root;
    /** {@code @startmindmap <name>} の名前サフィックス (無ければ空文字)。往復で保全する。 */
    private String diagramName = "";

    /** ルートノード (空図なら null)。 */
    public MindmapNode getRoot() {
        return root;
    }

    /** ルートノードを差し替える (null で空図)。ルートの親は必ず null にする。 */
    public void setRoot(MindmapNode root) {
        this.root = root;
        if (root != null) {
            root.setParent(null);
        }
    }

    /** {@code @startmindmap} に付いた図名 (無ければ空文字)。 */
    public String getDiagramName() {
        return diagramName;
    }

    /** {@code @startmindmap} の図名を設定する (null は空文字として扱う)。 */
    public void setDiagramName(String name) {
        this.diagramName = name != null ? name : "";
    }

    /** ルートから深さ優先で全ノードを平坦化して返す (空図なら空リスト)。 */
    public List<MindmapNode> allNodes() {
        List<MindmapNode> out = new ArrayList<>();
        if (root != null) {
            collect(root, out);
        }
        return out;
    }

    private static void collect(MindmapNode n, List<MindmapNode> out) {
        out.add(n);
        for (MindmapNode c : n.getChildren()) {
            collect(c, out);
        }
    }

    /** {@code child} を {@code parent} の子として末尾へ追加する。 */
    public void addChild(MindmapNode parent, MindmapNode child) {
        child.setParent(parent);
        parent.getChildren().add(child);
    }

    /**
     * ノードを (子孫ごと) 取り除く。ルート自身の削除はここでは行わない
     * (呼び出し側が {@link #setRoot(MindmapNode)} で空図にする)。
     */
    public void remove(MindmapNode target) {
        MindmapNode parent = target.getParent();
        if (parent != null) {
            parent.getChildren().remove(target);
            target.setParent(null);
        }
    }

    /**
     * {@code target} を {@code newParent} の子へ付け替える ({@code index < 0} なら末尾)。
     * {@code target} が {@code newParent} の祖先または同一なら循環になるため付け替えず
     * false を返す (ルートは全ノードの祖先なので、この規則により付け替え不可になる)。
     *
     * @return 実際に付け替えたら true
     */
    public boolean reparent(MindmapNode target, MindmapNode newParent, int index) {
        if (target == null || newParent == null || isAncestorOrSame(target, newParent)) {
            return false;
        }
        remove(target);
        List<MindmapNode> siblings = newParent.getChildren();
        int at = index < 0 || index > siblings.size() ? siblings.size() : index;
        siblings.add(at, target);
        target.setParent(newParent);
        return true;
    }

    /** {@code maybeAncestor} が {@code node} の祖先または同一か。 */
    private static boolean isAncestorOrSame(MindmapNode maybeAncestor, MindmapNode node) {
        for (MindmapNode cur = node; cur != null; cur = cur.getParent()) {
            if (cur == maybeAncestor) {
                return true;
            }
        }
        return false;
    }

    /** 同一の親の中で {@code target} を 1 つ前後へ動かす (端では何もしない)。 */
    public void move(MindmapNode target, int delta) {
        MindmapNode parent = target.getParent();
        if (parent == null) {
            return;
        }
        List<MindmapNode> siblings = parent.getChildren();
        int idx = siblings.indexOf(target);
        int to = idx + delta;
        if (to < 0 || to >= siblings.size()) {
            return;
        }
        siblings.remove(idx);
        siblings.add(to, target);
    }

    /**
     * {@code target} を含め祖先方向へ辿り、最初に見つかった非 AUTO の
     * {@link MindmapNode#getOwnSide()} を実効的な左右として返す。どこも AUTO なら AUTO。
     */
    public MindmapNode.Side effectiveSideOf(MindmapNode target) {
        for (MindmapNode cur = target; cur != null; cur = cur.getParent()) {
            if (cur.getOwnSide() != MindmapNode.Side.AUTO) {
                return cur.getOwnSide();
            }
        }
        return MindmapNode.Side.AUTO;
    }
}
