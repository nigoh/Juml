// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

/**
 * クラス本体の {@code &#123;} を「コードブロック」と「メンバー宣言」に切り分ける判定。
 *
 * <p>{@link KotlinLightScanner} から分離してある。あちらは 900 行を超える走査本体で、
 * ここは「この波括弧の中はクラスのメンバーか、それとも実装の中身か」という 1 つの問いに
 * だけ答える。誤るとどちらの向きにも壊れる: コードブロックを見落とせばローカル変数が
 * 存在しないメンバーとして図に出るし、逆にメンバーの波括弧をコードブロックと見なせば
 * 実在するメンバーが消える。</p>
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
        return body.substring(hs + 1, bracePos)
                .matches("(?s).*\\bcompanion\\s+object\\b.*");
    }
}
