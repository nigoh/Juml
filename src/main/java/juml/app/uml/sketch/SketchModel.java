// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI デザイナーが編集するクラス図モデル (クラス群 + 関係群)。
 *
 * <p>PlantUML テキストとの相互変換は {@link SketchPumlCodec} が担う。
 * このクラスは構造の保持と基本操作 (追加・削除・名前解決) のみ。</p>
 */
public final class SketchModel {

    private final List<SketchClass> classes = new ArrayList<>();
    private final List<SketchRelation> relations = new ArrayList<>();

    public List<SketchClass> getClasses() {
        return classes;
    }

    public List<SketchRelation> getRelations() {
        return relations;
    }

    /** 名前でクラスを探す (無ければ null)。 */
    public SketchClass findClass(String name) {
        for (SketchClass c : classes) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    /** {@code base}, {@code base2}, ... の形式で未使用のクラス名を作る。 */
    public String uniqueName(String base) {
        if (findClass(base) == null) {
            return base;
        }
        int n = 2;
        while (findClass(base + n) != null) {
            n++;
        }
        return base + n;
    }

    /** クラスを削除し、そのクラスに接続する関係もまとめて取り除く。 */
    public void removeClass(SketchClass target) {
        classes.remove(target);
        relations.removeIf(r -> r.touches(target.getName()));
    }

    /** クラス名の変更に追随して関係の端点も付け替える。 */
    public void renameClass(SketchClass target, String newName) {
        String old = target.getName();
        target.setName(newName);
        for (SketchRelation r : relations) {
            if (r.getLeft().equals(old)) {
                r.setLeft(newName);
            }
            if (r.getRight().equals(old)) {
                r.setRight(newName);
            }
        }
    }
}
