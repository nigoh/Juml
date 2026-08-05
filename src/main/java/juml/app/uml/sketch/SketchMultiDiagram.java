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
     * 開始行から図名を取り出す。10 個の codec がここを通る。
     *
     * <p>開始語の直後の<b>区切りそのものが意味を持つ</b>。実測 (同梱 PlantUML 1.2026.6、
     * {@code StartUtils.patternFilename} = 開始語の次は空白<b>または</b> {@code &#123;}):</p>
     *
     * <pre>
     *   &#64;startuml(id=X)   -&gt; ファイル名指定なし (d.svg)。id は別の走査が拾う
     *   &#64;startuml (id=X)  -&gt; ファイル名 "(id=X)"  -&gt; (id=X).svg
     *   &#64;startuml&#123;foo&#125;    -&gt; ファイル名 "foo"     -&gt; foo.svg
     *   &#64;startuml &#123;foo&#125;   -&gt; ファイル名 "&#123;foo"    -&gt; &#123;foo.svg
     * </pre>
     *
     * <p>つまり空白 1 文字の有無で成果物のファイル名が変わる。以前はここを
     * {@code substring(token.length()).trim()} と書いて<b>区切りを捨てて</b>いたため、
     * 書き戻し側は図名の 1 文字目から区切りを推測するしかなく、どちらに倒しても
     * 片方が壊れた。区切りを推測しないで済むように、<b>復元に必要なときだけ図名に
     * 残して</b>返す — 直後が空白で、その次が {@code (} か {@code &#123;} のときだけである。
     * それ以外は従来どおり trim した図名になる。</p>
     */
    static String parseDiagramName(String line, String startToken) {
        if (line == null || startToken == null || !line.startsWith(startToken)) {
            return "";
        }
        String rest = line.substring(startToken.length());
        String name = rest.trim();
        if (name.isEmpty() || rest.isEmpty() || !Character.isWhitespace(rest.charAt(0))) {
            return name;
        }
        char first = name.charAt(0);
        return first == '(' || first == '{' ? " " + name : name;
    }

    /**
     * 開始行を組み立てる。{@link #parseDiagramName} が返した図名をそのまま戻す。
     *
     * <p>図名が区切りを内包しているとき (先頭が空白 / {@code (} / {@code &#123;}) は
     * そのまま連結する。それ以外は空白 1 つを置く。この 2 つを合わせて
     * 「読んだ開始行がそのまま書き戻る」が成り立つ。</p>
     */
    static String startLine(String startToken, String name) {
        if (name == null || name.isEmpty()) {
            return startToken;
        }
        char first = name.charAt(0);
        if (Character.isWhitespace(first) || first == '(' || first == '{') {
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
