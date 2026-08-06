// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import juml.core.formats.uml.JavaClassInfo;

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

    /**
     * {@code <>} / {@code ()} / {@code []} のネスト外にある最初の {@code :} の位置。無ければ -1。
     *
     * <p>{@code where} 節に入ったらそこで打ち切る。{@code class Sorter<T> where T : Comparable<T>}
     * の {@code :} は<b>型パラメータの制約</b>であってスーパータイプではない。区別せずに読むと
     * 図に「Sorter implements Comparable」という<b>存在しない実装線</b>が引かれ、
     * {@code where K : Any} なら {@code Any} を、複数制約なら {@code T : Cloneable} という
     * 架空のインタフェース名を継承リストに並べていた。同じヘッダ領域を読む兄弟の
     * {@link #primaryCtorParenAfter} は以前から where を正しく無視しており、片方だけが
     * 知らない状態だった。</p>
     */
    static int topLevelColon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            // コメント・文字列は宣言ではない。ヘッダ検出・プロパティ・関数・引数の
            // 4 経路は以前からこの規則を持っていて、ヘッダ区間の走査だけが生テキストの
            // ままだった (実測: 継承リストのコメントを型名として読み、改行入りの
            // 引用符付きラベルを書き出して図が 1 枚も描けなくなる)。
            int e = KotlinLightScanner.skipNonCode(s, i);
            if (e > i) {
                i = e - 1;
                continue;
            }
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
            } else if (depth == 0 && isWordAt(s, i, "where")) {
                return -1;
            }
        }
        return -1;
    }

    /** {@code s} の位置 {@code i} が語として {@code word} で始まるか (前後が識別子でない)。 */
    static boolean isWordAt(String s, int i, String word) {
        if (!s.startsWith(word, i)) {
            return false;
        }
        if (i > 0 && KotlinLightScanner.isIdentPart(s.charAt(i - 1))) {
            return false;
        }
        int after = i + word.length();
        return after >= s.length() || !KotlinLightScanner.isIdentPart(s.charAt(after));
    }

    /** ネストの外側にあるカンマで分割する (要素内の {@code <>}/{@code ()} は保つ)。 */
    static List<String> splitTopLevelCommas(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            // コメントの中のカンマ・括弧で分割しない。読み飛ばしつつ<b>本文には残す</b> —
            // 呼び出し側 (引数・スーパータイプ・enum 定数) はそれぞれ自前でコメントを
            // 落とすので、ここで消すと位置がずれる。
            int e = KotlinLightScanner.skipNonCode(s, i);
            if (e > i) {
                cur.append(s, i, e);
                i = e - 1;
                continue;
            }
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

    /**
     * クラスヘッダがどこで終わるかを 1 回の走査で決める。
     *
     * @return {@code {bodyBraceOpen, superRegionEnd}} — 本体の開き波括弧 (無ければ -1) と、
     *         スーパータイプ列を読んでよい終端 exclusive
     *
     * <p>ヘッダの終わりは 3 つの経路がそれぞれ別に決めていて、どれも違っていた:</p>
     *
     * <ul>
     *   <li>本体の {@code &#123;} は深さを数えない前方検索だった。スーパータイプの
     *       コンストラクタ引数に波括弧が入る形 — {@code ListAdapter(object : Cb() { … })} や
     *       {@code Base(onReady = { … })} — でその {@code &#123;} を本体と取り違え、
     *       <b>実在するメンバーが 1 つも出ず</b>、匿名 object のメンバーが囲むクラスへ
     *       ホイストされ、後ろに並ぶインタフェースが全部落ちていた。</li>
     *   <li>上限は「次のクラスヘッダ」だけを見ていた。間に {@code fun} が挟まると
     *       その本体を<b>本体を持たないクラスが自分のものとして飲み込む</b>。</li>
     *   <li>スーパータイプ列の終端は、後続クラスが無ければ<b>ファイル末尾</b>だった。
     *       次のトップレベル宣言の型注釈のコロンを継承コロンとして読み、
     *       {@code "Long = 30L"} のような<b>存在しない箱</b>への継承線を引いていた。</li>
     * </ul>
     *
     * <p>言明は 1 つ:<b>ヘッダは次の宣言が始まるところで終わる</b>。宣言キーワードは
     * Kotlin の閉じた集合なので数え上げてよい。入れ子の内側 ({@code ( ) [ ] &lt; &gt;}) と
     * コメント・文字列は、ここでも他の走査と同じように読み飛ばす。
     * 兄弟の {@link #primaryCtorParenAfter} は以前からこの規則を持っていた。</p>
     */
    static int[] headerEnd(String src, int from) {
        int depth = 0;
        int superEnd = -1;
        for (int i = from; i < src.length(); i++) {
            int e = KotlinLightScanner.skipNonCode(src, i);
            if (e > i) {
                i = e - 1;
                continue;
            }
            char c = src.charAt(i);
            if (c == '(' || c == '[' || c == '<') {
                depth++;
                continue;
            }
            if (c == ')' || c == ']' || c == '>') {
                if (!isArrowGreaterThan(src, i) && depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth > 0) {
                continue;
            }
            if (c == '{') {
                return new int[]{i, superEnd >= 0 ? superEnd : i};
            }
            if (c == '}') {
                return new int[]{-1, superEnd >= 0 ? superEnd : i};
            }
            if (isWordAt(src, i, "where")) {
                // 型制約はスーパータイプではないが、本体はこの後ろに来る。
                if (superEnd < 0) {
                    superEnd = i;
                }
                continue;
            }
            if (!KotlinLightScanner.isIdentPart(c)
                    || (i > 0 && KotlinLightScanner.isIdentPart(src.charAt(i - 1)))) {
                continue;
            }
            int wordEnd = i;
            while (wordEnd < src.length() && KotlinLightScanner.isIdentPart(src.charAt(wordEnd))) {
                wordEnd++;
            }
            if (DECL_KEYWORDS.contains(src.substring(i, wordEnd))) {
                return new int[]{-1, superEnd >= 0 ? superEnd : i};
            }
            i = wordEnd - 1;
        }
        return new int[]{-1, superEnd >= 0 ? superEnd : src.length()};
    }

    /**
     * ヘッダを終わらせる宣言キーワード。
     *
     * <p>可視性修飾子と {@code constructor} は<b>入れない</b> —
     * {@code class A private constructor(…)} のようにヘッダの<b>中</b>にも現れるので、
     * 入れると継承リストの手前で切ってしまう。{@code val} / {@code var} は
     * コンストラクタ引数にも現れるが、そちらは括弧の内側 (深さ &gt; 0) なので届かない。</p>
     */
    private static final java.util.Set<String> DECL_KEYWORDS =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "fun", "val", "var", "class", "interface", "object", "typealias", "init"));

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
    static int skipAngles(String src, int at) {
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

    /**
     * {@code [start, end)} 区間からスーパータイプリスト ({@code : A(), B, C}) を取り込む。
     * コンストラクタ呼び出し ({@code A()}) を伴う型をスーパークラス、それ以外をインタフェース
     * とみなす (Kotlin ではスーパークラスのみ {@code ()} を伴う)。{@code <...>} / {@code (...)}
     * のネスト内の {@code :} は無視するため、プライマリコンストラクタの {@code val x: Int} は誤検出しない。
     */
    static void extractSupertypes(String source, int start, int end,
                                          JavaClassInfo info) {
        if (start < 0 || end <= start || end > source.length()) {
            return;
        }
        String region = source.substring(start, end);
        int colon = topLevelColon(region);
        if (colon < 0) {
            return;
        }
        String list = region.substring(colon + 1);
        int depth = 0;
        for (int i = 0; i < list.length(); i++) {
            int nc = KotlinLightScanner.skipNonCode(list, i);
            if (nc > i) {
                i = nc - 1;
                continue;
            }
            char c = list.charAt(i);
            // 入れ子の内側の { } や where は打ち切りではない。深さを数えないと
            // `Base(object : Cb() { … }), Marker` の Marker が落ちる。
            if (c == '(' || c == '[' || c == '<') {
                depth++;
                continue;
            }
            if (c == ')' || c == ']' || c == '>') {
                if (!isArrowGreaterThan(list, i) && depth > 0) {
                    depth--;
                }
                continue;
            }
            if (depth > 0) {
                continue;
            }
            // `where` の型制約はスーパータイプではない。継承コロンが<b>無い</b>形は
            // topLevelColon が打ち切るが、スーパータイプが有ると先に継承コロンが
            // 返るのでここまで来る — 同じ規則を両方の経路に入れる。入れないと
            // 最後の項が where 節を丸ごと飲み込み、`Marker where T : Comparable` と
            // いう存在しない箱への実装線が引かれ、本物への線は 1 本も引かれない。
            if (c == '{' || c == '}' || isWordAt(list, i, "where")) {
                list = list.substring(0, i);
                break;
            }
        }
        for (String raw : splitTopLevelCommas(list)) {
            String e = KotlinBlockMask.codeOnly(raw).trim();
            if (e.isEmpty()) {
                continue;
            }
            // by 委譲 (`Repo by RepoImpl()`) は「インタフェース + 委譲先の式」なので、
            // 括弧判定より先に ` by <expr>` を落とす。後回しにすると委譲先が呼び出し形
            // (`by RepoImpl()` / `by MainScope()` — 最も一般的) のとき ( が先に見つかり、
            // "Repo by RepoImpl" という架空の名前がスーパークラスとして図に出てしまう。
            boolean delegated = hasDelegation(e);
            if (delegated) {
                e = stripDelegation(e);
            }
            // 型引数の中の ( ) (関数型 `(Int) -> Unit` 等) は「コンストラクタ呼び出し」では
            // ないので、深さ 0 の ( だけを見る。
            int paren = delegated ? -1 : topLevelParen(e);
            if (paren >= 0) {
                String sup = e.substring(0, paren).trim();
                if (!sup.isEmpty() && info.getSuperClass() == null) {
                    info.setSuperClass(sup);
                }
            } else if (!e.isEmpty()) {
                info.getInterfaces().add(e);
            }
        }
    }
}
