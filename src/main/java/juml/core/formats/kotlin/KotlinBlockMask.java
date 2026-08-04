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
     * クラス本体文字列のうち「コードブロック」(関数本体・getter/setter・二次コンストラクタ本体・
     * init ブロック) の中身を true にしたマスクを返す。
     *
     * <p>ローカルの {@code val}/{@code var}/{@code fun} をクラスのフィールド/メソッドとして
     * 誤抽出しないために使う。判定は {@code {} の直前の非空白文字が {@code )} (関数/アクセサ/
     * コンストラクタのシグネチャ末尾)、または直前の語が {@code init} の場合をコードブロックとみなす。
     * 型本体 ({@code class}/{@code object}/{@code companion object}/{@code enum}/{@code interface})
     * の {@code {} はマスクせず走査を継続するため、ネストした型やコンパニオンのメンバは従来どおり
     * 抽出 (ホイスト) される。ラムダ ({@code = { ... }}) はコードブロックだが稀なため対象外。</p>
     */
    static boolean[] codeBlockMask(String body) {
        int n = body.length();
        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) {
            int e = KotlinLightScanner.skipNonCode(body, i);
            if (e > i) { i = e - 1; continue; }
            char c = body.charAt(i);
            if (c != '{') { continue; }
            int p = i - 1;
            while (p >= 0 && Character.isWhitespace(body.charAt(p))) p--;
            boolean codeBlock = false;
            if (p >= 0) {
                char pc = body.charAt(p);
                if (pc == ')') {
                    codeBlock = true;
                } else if (KotlinLightScanner.isIdentPart(pc)) {
                    int ws = p;
                    while (ws >= 0 && KotlinLightScanner.isIdentPart(body.charAt(ws))) ws--;
                    if ("init".equals(body.substring(ws + 1, p + 1))) {
                        codeBlock = true;
                    }
                }
            }
            if (!codeBlock && isInitialiserBlock(body, p)) {
                codeBlock = true;
            }
            // 名前付きネスト型 (class / interface / object / enum) の本体はマスクする。
            // これらは独立した JavaClassInfo エントリとして別途出力されるため、囲む型へ
            // ホイストするとメンバが重複・誤付与される。ただし companion object だけは
            // 従来どおり外側へホイストする (Outer.CONST のように静的的に参照されるため)。
            if (!codeBlock && isNestedTypeHeader(body, i)) {
                codeBlock = true;
            }
            if (codeBlock) {
                int close = KotlinLightScanner.matchBrace(body, i);
                if (close > i) {
                    for (int k = i; k <= close && k < n; k++) {
                        mask[k] = true;
                    }
                    i = close; // ブロック全体 (入れ子のコードブロック含む) を一括スキップ
                }
            }
        }
        return mask;
    }

    /**
     * {@code p} ({@code &#123;} の直前の非空白位置) から見て、初期化式のブロック
     * (ラムダ代入 / {@code by} 委譲) かどうか。マスクしないとラムダの中身がクラス本体として
     * 走査され、ローカル変数とローカル関数が<b>存在しないメンバー</b>として図に出る。
     */
    private static boolean isInitialiserBlock(String body, int p) {
        if (p < 0 || (body.charAt(p) != '=' && !KotlinLightScanner.isIdentPart(body.charAt(p)))) {
            return false;
        }
        if (body.charAt(p) == '=') {
            return true;
        }
        // `by lazy {` = 「by + 識別子 + {」。`by X(...) {` は直前が ) なので別経路で拾う。
        int s = p;
        while (s >= 0 && KotlinLightScanner.isIdentPart(body.charAt(s))) {
            s--;
        }
        while (s >= 0 && Character.isWhitespace(body.charAt(s))) {
            s--;
        }
        int we = s;
        while (s >= 0 && KotlinLightScanner.isIdentPart(body.charAt(s))) {
            s--;
        }
        return we > s && "by".equals(body.substring(s + 1, we + 1));
    }

    /**
     * {@code body} の位置 {@code bracePos} の {@code &#123;} が、名前付きネスト型
     * (class / interface / object / enum) の本体開始かどうかを判定する。直前の文
     * 境界 ({@code ;} / {@code &#125;} / {@code &#123;}) までのヘッダに型宣言キーワードが
     * 含まれ、かつ {@code companion object} でなければ true。companion object は
     * 外側へホイストしたいので false を返す (従来どおり降りて抽出する)。
     */
    private static boolean isNestedTypeHeader(String body, int bracePos) {
        int hs = bracePos - 1;
        while (hs >= 0) {
            char ch = body.charAt(hs);
            if (ch == ';' || ch == '}' || ch == '{') {
                break;
            }
            hs--;
        }
        String header = body.substring(hs + 1, bracePos);
        if (header.matches("(?s).*\\bcompanion\\s+object\\b.*")) {
            return false;
        }
        return header.matches("(?s).*\\b(class|interface|object|enum)\\b.*");
    }
}
