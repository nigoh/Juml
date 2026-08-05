// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

/**
 * {@code legend} ブロックに<b>そのまま読ませたい</b> 1 行を組み立てる、全図種共有の規則。
 *
 * <p>legend の中では creole 記法が効き、<b>行頭の記号がマークアップとして消費される</b>。
 * 同梱 PlantUML 1.2026.6 で実測した結果:</p>
 *
 * <pre>
 *   "# protected" -&gt; "1." + "protected"  ( # が番号付きリストになり記号が消える)
 *   "* star"      -&gt; "star"              ( * が箇条書きになり記号が消える)
 *   "+ public"    -&gt; "+ public"          (そのまま)
 *   "- private"   -&gt; "- private"         (そのまま)
 *   "~ package-private" -&gt; そのまま
 * </pre>
 *
 * <p>凡例としては致命的で、「{@code #} はこういう意味です」と教えるための行から
 * <b>説明対象の記号だけが落ちる</b>。実際、可視性凡例の {@code # protected} と Deep Link 図の
 * {@code #LightYellow : exported=true} の 2 か所で同じことが起きていた — 同じ規則の
 * 兄弟経路である。</p>
 *
 * <p>逃がし方はプロジェクトの規約どおり creole のチルダエスケープを使う
 * ({@code .claude/rules/java-parsing-pipeline.md}: HTML エンティティ化は使わない)。
 * チルダは<b>次の 1 文字がマークアップのときだけ消費される</b>ので、マークアップでない
 * 文字に付けると {@code ~} が見えてしまう (実測: {@code "~+ x"} → {@code "~+ x"})。
 * よって付けるのは実測で「素だと壊れ、チルダで直る」ことを確認した行頭記号だけ。</p>
 *
 * <p><b>効かない場合</b>: 行頭の {@code =} は見出しになるが、{@code "~= x"} は
 * {@code ~} ごと描画されるためチルダでは逃がせない。凡例で {@code =} を行頭に置きたい
 * ときは文言を変えて先頭に置かないこと。なお本文中 (行頭以外) の記号は影響を受けない。</p>
 *
 * <p>この列挙が正しいかどうかは {@code LegendSymbolsSurviveRenderingTest} が
 * <b>実際に描画して</b>確かめる。列挙は必ず取りこぼすので、守りは描画側に置く。</p>
 */
public final class PlantUmlLegendText {

    /**
     * 行頭に置くと creole のリスト記法として消費される記号。
     *
     * <p>チルダで逃がせることを実測で確認したものだけを入れる。ここに足すときは
     * 必ず {@code LegendSymbolsSurviveRenderingTest} に行を足して描画で確かめること。</p>
     */
    private static final String CREOLE_LIST_MARKERS = "#*";

    private PlantUmlLegendText() {
    }

    /**
     * legend の 1 行を「書いたとおりに描かれる」形へ整える。
     *
     * <p>意図的に creole / PlantUML のマークアップを使う行 (見出しの {@code == … ==}、
     * ステレオタイプの {@code &lt;&lt;…&gt;&gt;}、{@code &lt;color:…&gt;}) には<b>使わない</b>。
     * そちらはマークアップとして解釈されるのが正しい。</p>
     */
    public static String literalLine(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;   // PlantUML は行頭の空白を落としてから creole を見る
        }
        if (i >= text.length() || CREOLE_LIST_MARKERS.indexOf(text.charAt(i)) < 0) {
            return text;
        }
        return text.substring(0, i) + '~' + text.substring(i);
    }
}
