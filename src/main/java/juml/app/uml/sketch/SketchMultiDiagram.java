// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.List;

/**
 * 1 ファイルに複数の図が入っているかを判定する、全 codec 共有の番人。
 *
 * <p>どの codec も {@code @startuml} 行を「図名の上書き」として読み飛ばす作りになっている。
 * 素直な実装だが、図が 2 つ入ったファイルを読ませると<b>2 つめの中身が 1 つめのモデルへ
 * そのまま積み増され</b>、未対応行ゼロ = 編集可能と判定される。設計器で 1 回編集すると
 * 書き戻しが全文を置き換えるため、図の区切りも先頭の図名も消えて 1 枚に統合された図が
 * 残る。利用者が壊したつもりのない破壊なので、編集をロックして書き戻し自体を止める。</p>
 *
 * <p>判定をここに集約するのは、以前 1 つの codec にだけ入れて他が取り残されたため。
 * 図種ごとに開始トークンが違う ({@code @startuml} / {@code @startmindmap} …) ので、
 * <b>その codec 自身が受け付けるトークン</b>を渡す。別のトークンで数えると、見逃すか、
 * 逆に正しい 1 図のファイルを誤ってロックする。</p>
 */
final class SketchMultiDiagram {

    private SketchMultiDiagram() {
    }

    /**
     * 2 本目以降の開始行を {@code unsupported} へ積む (= 編集ロック)。
     *
     * <p>判定は各 codec とまったく同じ {@code trim().startsWith(token)}。ここだけ厳しく
     * 「トークンの直後は行末か空白」と条件を足していたが、codec 側は残りをそのまま図名に
     * するので、両者がずれた瞬間に取りこぼす。実際 PlantUML の複数図記法
     * {@code @startuml(id=NAME)} は直後が {@code (} なのでこの番人だけが素通しし、codec は
     * 開始行として受け入れていた — 番人を入れる前とまったく同じ統合が起きていた。
     * <b>codec が開始行として扱う行を、1 行の狂いもなく同じに数える</b>のが唯一の正しさ。</p>
     *
     * @param lines       解析対象の全行 (未 trim で可)
     * @param startToken  この codec が開始行として受け付けるトークン ({@code "@startuml"} 等)
     * @param unsupported 未対応行の収集先
     */
    static void reportExtraDiagrams(String[] lines, String startToken, List<String> unsupported) {
        if (lines == null) {
            return;
        }
        boolean seen = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (!line.startsWith(startToken)) {
                continue;
            }
            if (seen) {
                unsupported.add(line);
            }
            seen = true;
        }
    }
}
