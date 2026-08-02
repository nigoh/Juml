// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    /** ジェネリック引数の外側にある最初の {@code (} の位置。無ければ -1。 */
    static int topLevelParen(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                if (!isArrowGreaterThan(s, i) && depth > 0) {
                    depth--;
                }
            } else if (c == '(' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** ` by <委譲式>` (スーパータイプ項の末尾)。 */
    private static final Pattern DELEGATION = Pattern.compile("\\s+by\\s+.*$");

    /**
     * クラス名の直後から、<b>プライマリコンストラクタ</b>の開き括弧を探す。
     *
     * <p>単純に最初の {@code (} を採ると、{@code class Repo @Named("main") constructor(...)}
     * のように引数付きアノテーションが挟まる場合にアノテーション引数の {@code (} を掴み、
     * 本来のプロパティが 1 つも抽出されなくなる ({@code @Inject constructor(...)} は引数が
     * 無いため偶然通り、見落とされやすい)。型パラメータ {@code <T>}・アノテーション
     * (引数の括弧ごと)・可視性修飾子・{@code constructor} キーワードを読み飛ばして判定する。</p>
     *
     * @return プライマリコンストラクタの {@code (} の位置。無ければ -1
     */
    static int primaryCtorParenAfter(String src, int from) {
        int i = from;
        while (i < src.length()) {
            int e = KotlinLightScanner.skipNonCode(src, i);
            if (e > i) {
                i = e;
                continue;
            }
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '(') {
                return i;
            } else if (c == '@') {
                i = skipAnnotation(src, i);
                if (i < 0) {
                    return -1;
                }
            } else if (c == '<') {
                i = skipAngles(src, i);
                if (i < 0) {
                    return -1;
                }
            } else if (KotlinLightScanner.isIdentPart(c)) {
                int s = i;
                while (i < src.length() && KotlinLightScanner.isIdentPart(src.charAt(i))) {
                    i++;
                }
                String word = src.substring(s, i);
                // ここで許されるのは constructor とその可視性修飾子だけ。それ以外の語
                // (継承の where 等) が来たらプライマリコンストラクタは無い。
                if (!"constructor".equals(word) && !"private".equals(word)
                        && !"protected".equals(word) && !"public".equals(word)
                        && !"internal".equals(word)) {
                    return -1;
                }
            } else {
                // ':' (継承) / '{' (本体) 等に到達 = プライマリコンストラクタ無し。
                return -1;
            }
        }
        return -1;
    }

    /** {@code @Ann} / {@code @Ann(args)} を読み飛ばした次位置。壊れていれば -1。 */
    private static int skipAnnotation(String src, int at) {
        int i = at + 1;
        while (i < src.length()
                && (KotlinLightScanner.isIdentPart(src.charAt(i)) || src.charAt(i) == '.' || src.charAt(i) == ':')) {
            i++;
        }
        while (i < src.length() && Character.isWhitespace(src.charAt(i))) {
            i++;
        }
        if (i < src.length() && src.charAt(i) == '(') {
            int close = KotlinLightScanner.matchParen(src, i);
            if (close <= i) {
                return -1;
            }
            return close + 1;
        }
        return i;
    }

    /** 型パラメータ {@code <...>} を読み飛ばした次位置。閉じられていなければ -1。 */
    private static int skipAngles(String src, int at) {
        int depth = 0;
        for (int i = at; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>' && !isArrowGreaterThan(src, i)) {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /** スーパータイプ項が {@code by} 委譲を伴うか。 */
    static boolean hasDelegation(String s) {
        return DELEGATION.matcher(s).find();
    }

    /** スーパータイプ項から ` by <委譲式>` を取り除く。 */
    static String stripDelegation(String s) {
        return DELEGATION.matcher(s).replaceFirst("").trim();
    }
}
