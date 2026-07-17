// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集するユースケース図モデル (アクター/ユースケース群 + 関係群)。
 *
 * <p>PlantUML テキストとの相互変換は {@link UseCaseSketchCodec} が担う。
 * このクラスは構造の保持と基本操作 (追加・削除・名前解決) のみ。</p>
 */
public final class UseCaseSketchModel {

    private final List<UseCaseNode> nodes = new ArrayList<>();
    private final List<UseCaseRelation> relations = new ArrayList<>();
    private String diagramName = "";

    public List<UseCaseNode> getNodes() {
        return nodes;
    }

    public List<UseCaseRelation> getRelations() {
        return relations;
    }

    /** {@code @startuml} に付いた図名 (無ければ空文字)。 */
    public String getDiagramName() {
        return diagramName;
    }

    /** {@code @startuml} の図名を設定する (null は空文字として扱う)。 */
    public void setDiagramName(String name) {
        this.diagramName = name != null ? name : "";
    }

    /** id で要素を探す (無ければ null)。 */
    public UseCaseNode findNode(String id) {
        for (UseCaseNode n : nodes) {
            if (n.getId().equals(id)) {
                return n;
            }
        }
        return null;
    }

    /** {@code base}, {@code base2}, ... の形式で未使用の id を作る。 */
    public String uniqueId(String base) {
        if (findNode(base) == null) {
            return base;
        }
        int n = 2;
        while (findNode(base + n) != null) {
            n++;
        }
        return base + n;
    }

    /** 要素を削除し、その要素に接続する関係もまとめて取り除く。 */
    public void removeNode(UseCaseNode target) {
        nodes.remove(target);
        relations.removeIf(r -> r.touches(target.getId()));
    }

    /** id の変更に追随して関係の端点も付け替える。 */
    public void renameNode(UseCaseNode target, String newId) {
        String old = target.getId();
        target.setId(newId);
        for (UseCaseRelation r : relations) {
            if (r.getFrom().equals(old)) {
                r.setFrom(newId);
            }
            if (r.getTo().equals(old)) {
                r.setTo(newId);
            }
        }
    }
}
