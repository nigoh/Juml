// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import java.util.ArrayList;
import java.util.List;

/**
 * Kotlin のクラスヘッダ文字列を読むための純ロジック ({@link KotlinLightScanner} から分離)。
 *
 * <p>括弧の深さを数えて「トップレベルの {@code :}」や「トップレベルのカンマ区切り」を
 * 見つける走査と、ネスト型の {@code enclosingClass} を決めるためのクラス本体スタックを持つ。
 * 走査本体と分けているのは責務の分離に加え、{@link KotlinLightScanner} を
 * checkstyle の FileLength 上限 (902 行) 内に保つため。</p>
 *
 * <p><b>関数型矢印の扱い (重要):</b> Kotlin の関数型 {@code (Int) -> Unit} に含まれる
 * {@code >} は {@code ->} の一部でジェネリクスの閉じではない。これを閉じとして数えると
 * 括弧の深さが崩れ、プライマリコンストラクタ引数リストの内側にいるのに深さ 0 とみなして
 * 後続の {@code 名前: 型} を継承コロンと誤認し、偽のスーパータイプを生む
 * ({@link #isArrowGreaterThan} でガードする)。</p>
 */
final class KotlinHeaderScan {

    private KotlinHeaderScan() {
    }

    /** {@code s[i]} が Kotlin の関数型矢印 {@code ->} の {@code >} か。 */
    static boolean isArrowGreaterThan(String s, int i) {
        return s.charAt(i) == '>' && i > 0 && s.charAt(i - 1) == '-';
    }

    /** {@code <>} / {@code ()} / {@code []} のネスト外にある最初の {@code :} の位置。無ければ -1。 */
    static int topLevelColon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']') {
                if (!isArrowGreaterThan(s, i) && depth > 0) {
                    depth--;
                }
            } else if (c == ':' && depth == 0) {
                return i;
            } else if ((c == '{' || c == '}') && depth == 0) {
                return -1;
            }
        }
        return -1;
    }

    /** ネストの外側にあるカンマで分割する (要素内の {@code <>}/{@code ()} は保つ)。 */
    static List<String> splitTopLevelCommas(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']' || c == '}') {
                if (!isArrowGreaterThan(s, i) && depth > 0) {
                    depth--;
                }
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /**
     * ソース順に現れるクラスヘッダから、いま「開いている」クラス本体を追って
     * ネスト型の {@code enclosingClass} を決めるスタック。
     *
     * <p>設定しないと {@code com.x.Outer.State} が {@code com.x.State} になり、同名の
     * ネスト型 (画面ごとの State/UiState など) が同じ完全修飾名へ衝突してクラス図で
     * 1 つの箱へ統合されてしまう。</p>
     */
    static final class Nesting {

        private record OpenBody(String name, int endOffset) {
        }

        private final List<OpenBody> open = new ArrayList<>();

        /**
         * {@code declStart} 位置に現れるクラスの enclosingClass を返す。閉じ終わった本体は
         * ここで捨てる。トップレベルなら null (Java 側 {@code TypeDeclAdapter} と同じ規約)。
         */
        String enclosingAt(int declStart) {
            while (!open.isEmpty() && open.get(open.size() - 1).endOffset() <= declStart) {
                open.remove(open.size() - 1);
            }
            if (open.isEmpty()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (OpenBody b : open) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(b.name());
            }
            return sb.toString();
        }

        /** 本体を持つクラスを「開いている」として積む ({@code endOffset} は閉じ波括弧の位置)。 */
        void openBody(String name, int endOffset) {
            open.add(new OpenBody(name, endOffset));
        }
    }
}
