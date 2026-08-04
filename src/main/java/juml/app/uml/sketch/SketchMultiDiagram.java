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
    /**
     * 開始行を組み立てる。図名を持つときだけ区切りを入れる。
     *
     * <p><b>既知の限界</b>: codec は開始行を {@code substring(token.length()).trim()} で
     * 読むため<b>区切りそのものを捨てている</b>。そのため図名が {@code (} で始まる 2 つの
     * ケースを区別できない — 実測で {@code @startuml(id=X)} は「区切り無し」= ファイル名
     * 指定なし ({@code d.svg}、id は別途 {@code id=(\w+)} で拾われる)、
     * {@code @startuml (foo)} は「区切りあり」= ファイル名 {@code (foo)} で
     * {@code (foo).svg}。ここでは前者 (PlantUML が複数図記法として文書化している方) を
     * 優先して区切りを入れない。後者は空白を失う。
     * 正しく直すには 10 個のモデルが図名だけでなく<b>読んだ区切り</b>を保持する必要がある。</p>
     *
     * <p>区切りは常に空白 1 つ、ではない。PlantUML の複数図記法
     * {@code @startuml(id=NAME)} はトークンに {@code (} が<b>接している</b>ことが構文で、
     * 空白を入れると意味が変わる: 実測で {@code @startuml(id=FIRST)} は {@code d.svg} を
     * 出すが、{@code @startuml (id=FIRST)} は {@code (id=FIRST)} を<b>出力ファイル名</b>と
     * 解釈して {@code (id=FIRST).svg} を出す。codec は残りを図名として読むだけなので、
     * 図名が {@code (} で始まるなら書き戻しでも接したままにしないと、設計器で 1 回
     * 動かしただけで id が消えて成果物の名前が変わり、{@code !include file!ID} も
     * 解決しなくなる。10 個の codec が同じ組み立てをしていたのでここへ寄せる。</p>
     */
    static String startLine(String startToken, String name) {
        if (name == null || name.isEmpty()) {
            return startToken;
        }
        // PlantUML 1.2026.6 の StartUtils.patternFilename は開始語の直後の区切りを
        // 「空白 <b>または {</b>」と定めている。{ は区切りそのものなので、空白を足すと
        // 別物になる (実測: `@startuml{foo}` -> foo.svg / `@startuml {foo}` -> {foo.svg)。
        if (name.startsWith("{") || name.startsWith("(")) {
            return startToken + name;
        }
        return startToken + ' ' + name;
    }

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
