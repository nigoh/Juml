// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

/**
 * Kotlin のクラス本体を<b>構造として走査する</b>判定群 ({@link KotlinLightScanner} から分離)。
 *
 * <p>いずれも「正規表現で数え上げると必ず取りこぼす」ことが実測で分かった判定を、括弧の
 * 深さと文字列・コメントを見ながら走る走査に置き換えたもの。どちらの向きに誤っても壊れる:
 * 実装の中身をメンバーと読めば存在しないメンバーが図に出るし、メンバーを実装と読めば
 * 実在するメンバーが消える。</p>
 *
 * <ul>
 *   <li>{@link #codeBlockMask} — この波括弧の中はメンバー宣言か実装か</li>
 *   <li>{@link #propertyTypeEnd} — プロパティの型はどこで終わるか</li>
 *   <li>{@link #insideParenMask} — この位置は丸括弧の内側 (= ctor 引数) か</li>
 * </ul>
 */
final class KotlinBlockMask {

    private KotlinBlockMask() {
    }

    /**
     * クラス本体文字列のうち、メンバー宣言として読んではいけない {@code &#123;…&#125;} の
     * 中身を true にしたマスクを返す。
     *
     * <p>判定は<b>反転</b>している。クラス本体に現れる波括弧のうち、中身が「囲むクラスの
     * メンバー」なのは {@code companion object} の本体<b>だけ</b>で、それ以外は関数本体・
     * アクセサ・{@code init}・二次コンストラクタ・ラムダ代入・{@code by} 委譲・
     * {@code when} / {@code if} の枝・匿名 {@code object} と、すべて実装の中身である。
     * 名前付きネスト型も独立した {@link juml.core.formats.uml.JavaClassInfo} として別途
     * 出力されるので、囲む型へホイストしてはいけない。</p>
     *
     * <p>以前は逆に「コードブロックらしい形」を列挙していた ({@code )} の直後 /
     * {@code init} / {@code = &#123;} / {@code by 識別子 &#123;})。列挙は必ず取りこぼす:
     * {@code Config().apply &#123; … &#125;}、{@code flow &#123; … &#125;}、
     * {@code MyAdapter &#123; … &#125;}、{@code by Holder.make &#123; … &#125;}、
     * {@code when &#123; … &#125;} がどれも素通りし、ラムダ内のローカル変数とローカル関数が
     * <b>存在しないメンバー</b>としてクラス図に並んでいた。許可する形を数え上げる代わりに、
     * 許可する形が 1 つしかないことを使う。</p>
     */
    static boolean[] codeBlockMask(String body) {
        int n = body.length();
        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) {
            int e = KotlinLightScanner.skipNonCode(body, i);
            if (e > i) { i = e - 1; continue; }
            if (body.charAt(i) != '{') { continue; }
            if (isCompanionObjectBody(body, i)) {
                continue; // companion のメンバは従来どおり外側へホイストする
            }
            int close = KotlinLightScanner.matchBrace(body, i);
            if (close > i) {
                for (int k = i; k <= close && k < n; k++) {
                    mask[k] = true;
                }
                i = close; // ブロック全体 (入れ子含む) を一括スキップ
            }
        }
        return mask;
    }

    /**
     * {@code bracePos} の {@code &#123;} が {@code companion object} の本体開始か。
     *
     * <p>companion のメンバだけは囲むクラスへホイストする ({@code Outer.CONST} のように
     * 静的メンバとして参照されるため)。直前の文境界 ({@code ;} / {@code &#125;} /
     * {@code &#123;}) までをヘッダとみなして判定する。</p>
     */
    private static boolean isCompanionObjectBody(String body, int bracePos) {
        int hs = bracePos - 1;
        while (hs >= 0) {
            char ch = body.charAt(hs);
            if (ch == ';' || ch == '}' || ch == '{') {
                break;
            }
            hs--;
        }
        // ヘッダからコメント・文字列を除いてから判定する。除かないと、直前のメンバーに
        // 付いた KDoc に「companion object」の語が出てくるだけで次のブロックが
        // <b>マスクを外され</b>、その中のローカルがクラスのメンバーとして図に出る。
        // 走査本体は skipNonCode を通しているのに、この判定だけが生テキストを見ていた。
        return codeOnly(body, hs + 1, bracePos)
                .matches("(?s).*\\bcompanion\\s+object\\b.*");
    }

    /** {@code [from, to)} からコメント・文字列リテラルを取り除いた文字列。 */
    private static String codeOnly(String body, int from, int to) {
        StringBuilder sb = new StringBuilder(to - from);
        for (int i = from; i < to; i++) {
            int e = KotlinLightScanner.skipNonCode(body, i);
            if (e > i) {
                sb.append(' ');   // 語の連結を防ぐため空白 1 つに畳む
                i = e - 1;
                continue;
            }
            sb.append(body.charAt(i));
        }
        return sb.toString();
    }

    /**
     * {@code from} (型の開始位置) から型の終端 exclusive を返す。
     *
     * <p>型が終わるのは、<b>入れ子の外側で</b>次のいずれかに達したとき:
     * {@code =} (初期化子) / {@code ;} / {@code }} (本体の終わり) / 改行 /
     * 語としての {@code get} {@code set} {@code by} / コメントの開始。
     * 入れ子 {@code &lt;&gt; () []} の内側にいる間はどれも終端にしない (関数型
     * {@code (Int, String) -> Unit} の中の改行やカンマで切らないため)。</p>
     */
    static int propertyTypeEnd(String s, int from) {
        int depth = 0;
        for (int i = from; i < s.length(); i++) {
            int e = KotlinLightScanner.skipNonCode(s, i);
            if (e > i) {
                return depth == 0 ? i : e - 1; // コメント/文字列は型の外
            }
            char c = s.charAt(i);
            // 関数型の矢印。`>` を閉じ括弧として扱う前に判定しないと `-> Unit` が切れる。
            if (c == '-' && i + 1 < s.length() && s.charAt(i + 1) == '>') {
                i++;
                continue;
            }
            if (c == '<' || c == '(' || c == '[') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']') {
                if (depth == 0) {
                    return i; // 入れ子の外の閉じ括弧 = 宣言の外 (ctor 引数の末尾など)
                }
                depth--;
            } else if (depth == 0) {
                if (c == '=' || c == ';' || c == '}' || c == '{' || c == '\n') {
                    return i;
                }
                if (isKeywordAt(s, i, "get") || isKeywordAt(s, i, "set")
                        || isKeywordAt(s, i, "by")) {
                    return i;
                }
            }
        }
        return s.length();
    }

    /** {@code s} の位置 {@code i} が語として {@code word} で始まるか (前後が識別子でない)。 */
    private static boolean isKeywordAt(String s, int i, String word) {
        if (!s.startsWith(word, i)) {
            return false;
        }
        if (i > 0 && KotlinLightScanner.isIdentPart(s.charAt(i - 1))) {
            return false;
        }
        int after = i + word.length();
        return after >= s.length() || !KotlinLightScanner.isIdentPart(s.charAt(after));
    }

    /**
     * 位置ごとに「丸括弧の内側か」を示すマスク。
     *
     * <p>入れ子クラスのヘッダ {@code class Item(val id: Long)} は<b>外側のクラス本体</b>に
     * あり、しかも自分の {@code &#123;} より前なのでコードブロックのマスクが効かない。
     * そのため primary constructor の {@code val} が外側のクラスのフィールドとして
     * 生えていた。丸括弧の内側にある宣言はクラス本体のプロパティではない。</p>
     */
    static boolean[] insideParenMask(String body) {
        boolean[] mask = new boolean[body.length()];
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            int e = KotlinLightScanner.skipNonCode(body, i);
            if (e > i) {
                for (int k = i; k < e && k < mask.length; k++) {
                    mask[k] = depth > 0;
                }
                i = e - 1;
                continue;
            }
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            }
            mask[i] = depth > 0;
            if (c == ')' && depth > 0) {
                depth--;
            }
        }
        return mask;
    }
}
