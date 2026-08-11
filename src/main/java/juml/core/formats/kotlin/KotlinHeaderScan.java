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
     * 入れ子の深さ。このファイルの走査は<b>すべてこれを使う</b>。
     *
     * <p>括弧 {@code ( [ &#123;} と山括弧 {@code &lt;} は<b>別々に</b>数える。括弧は必ず対で
     * 閉じるが、{@code &lt;} はジェネリクスの開きにも<b>比較演算子</b>にもなるからである。
     * 1 つの深さでまとめて数えると {@code Base(n &lt; 10)} のような引数で深さが 0 に戻らず、
     * 以降のトップレベル判定がすべて偽になる。比較が現れるのは必ず括弧の内側なので、
     * 山括弧は<b>括弧の外にいるときだけ</b>数える (ジェネリクスの {@code &lt;} は宣言名の
     * 直後 = 括弧の外にしか現れない)。</p>
     *
     * <p>この規則は {@link #headerEnd} にだけ入れられていた。同じファイルの兄弟走査
     * ({@link #topLevelColon} / {@link #splitTopLevelCommas} / {@link #topLevelParen} と
     * {@link #extractSupertypes} の打ち切り) は 1 つの深さのままだったため、
     * {@code Dialog(ctx, if (SDK_INT &lt; 21) A else B), Checkable} で<b>本体は見つかるのに
     * 継承リストのカンマが見えず</b>、後ろに並ぶインタフェースが 1 つ残らず消えていた。
     * {@code &gt;} の綴りなら偶然通るという非対称も同じ。実装をここへ 1 本化する。</p>
     */
    static final class Depth {

        private int brackets;
        private int angles;

        /** {@code s[i]} を読み込んで深さを更新する。 */
        void feed(String s, int i) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                brackets++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (brackets > 0) {
                    brackets--;
                }
            } else if (brackets == 0 && c == '<') {
                angles++;
            } else if (brackets == 0 && c == '>'
                    && !isArrowGreaterThan(s, i) && angles > 0) {
                angles--;
            }
        }

        /** 入れ子の外側 (トップレベル) にいるか。 */
        boolean top() {
            return brackets == 0 && angles == 0;
        }
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
        // 走査の開始位置はプライマリコンストラクタの引数リストを<b>含む</b>ので、
        // 既定値の中の比較 (`style: Int = if (SDK_INT < 21) 0 else 1` は Android の
        // カスタム View の定型) がここへ届く。1 つの深さで数えていたため、本物の
        // 継承コロンが「入れ子の内側」と判定されて -1 が返り、スーパークラスも
        // インタフェースも<b>1 つ残らず</b>消えていた (関係線がゼロ本の箱になる)。
        Depth depth = new Depth();
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
            // 打ち切り判定は深さを進める<b>前</b>に見る (本体の { は深さではなく終端)。
            if (depth.top()) {
                if (c == ':') {
                    return i;
                }
                if (c == '{' || c == '}' || isWordAt(s, i, "where")) {
                    return -1;
                }
            }
            depth.feed(s, i);
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
        Depth depth = new Depth();
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
            depth.feed(s, i);
            if (c == ',' && depth.top()) {
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
        // 深さの数え方は {@link Depth} に 1 本化してある (括弧と山括弧を別々に数え、
        // 山括弧は括弧の外だけ)。以前はこの規則がこの走査にだけ書かれていて、
        // 同じファイルの兄弟走査は 1 つの深さのままだった。
        Depth depth = new Depth();
        int superEnd = -1;
        for (int i = from; i < src.length(); i++) {
            int e = KotlinLightScanner.skipNonCode(src, i);
            if (e > i) {
                i = e - 1;
                continue;
            }
            char c = src.charAt(i);
            if (!depth.top()) {
                depth.feed(src, i);
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
                // 語でない文字 (括弧・山括弧を含む) はここで深さへ数える。
                depth.feed(src, i);
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
     * <p>可視性修飾子は<b>入れない</b> — {@code class A private constructor(…)} のように
     * ヘッダの<b>中</b>にも現れるので、入れると継承リストの手前で切ってしまう。
     * {@code val} / {@code var} はコンストラクタ引数にも現れるが、そちらは括弧の内側
     * (深さ &gt; 0) なので届かない。</p>
     *
     * <p>{@code constructor} は<b>入れる</b>。プライマリコンストラクタの
     * {@code constructor} はここへ届かない — 呼び出し側が
     * {@link #primaryCtorParenAfter} の {@code )} の後ろから走査を始めるからで、
     * 兄弟経路が既にその位置を知っている。届くのは二次コンストラクタだけであり、
     * それは<b>次の宣言</b>である。入れていなかったため、本体を持たない宣言
     * ({@code object X} など) の直後に二次コンストラクタが並ぶと、委譲の
     * {@code : this(…)} を継承コロンと読んで {@code "this"} という存在しない箱への
     * 継承線を引き、{@code constructor(…) &#123; … &#125;} の本体をその宣言のものとして
     * 取り込んでローカル変数をフィールドに仕立てていた。</p>
     */
    private static final java.util.Set<String> DECL_KEYWORDS =
            new java.util.HashSet<>(java.util.Arrays.asList("fun", "val", "var", "class",
                    "interface", "object", "typealias", "init", "constructor"));

    /** ジェネリック引数の外側にある最初の {@code (} の位置。無ければ -1。 */
    static int topLevelParen(String s) {
        Depth depth = new Depth();
        for (int i = 0; i < s.length(); i++) {
            int e = KotlinLightScanner.skipNonCode(s, i);
            if (e > i) {
                i = e - 1;
                continue;
            }
            // 「コンストラクタ呼び出しの (」は深さを進める<b>前</b>に見る。
            if (s.charAt(i) == '(' && depth.top()) {
                return i;
            }
            depth.feed(s, i);
        }
        return -1;
    }

    /** ` by <委譲式>` (スーパータイプ項の末尾)。 */
    private static final Pattern DELEGATION = Pattern.compile("\\s+by\\s+.*$");

    /** コロンの後ろが {@code this(…)} / {@code super(…)} = コンストラクタ委譲か。 */
    private static boolean isConstructorDelegation(String afterColon) {
        String s = KotlinBlockMask.codeOnly(afterColon).trim();
        for (String kw : new String[] {"this", "super"}) {
            if (s.startsWith(kw)) {
                int i = kw.length();
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                if (i < s.length() && s.charAt(i) == '(') {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * クラス名の直後から、<b>プライマリコンストラクタ</b>の開き括弧を探す。
     *
     * <p>単純に最初の {@code (} を採ると、{@code class Repo @Named("main") constructor(...)}
     * のように引数付きアノテーションが挟まる場合にアノテーション引数の {@code (} を掴み、
     * 本来のプロパティが 1 つも抽出されなくなる ({@code @Inject constructor(...)} は引数が
     * 無いため偶然通り、見落とされやすい)。型パラメータ {@code <T>}・アノテーション
     * (引数の括弧ごと)・可視性修飾子・{@code constructor} キーワードを読み飛ばして判定する。</p>
     *
     * <p>{@code kindKw} が {@code object} / {@code interface} のときは常に -1。これらは
     * Kotlin の文法上<b>プライマリコンストラクタを持てない</b>ので、直後に現れる
     * {@code constructor(…)} は必ず<b>囲みクラスの二次コンストラクタ</b>である。
     * 見分けずに掴んでいたため、本体を持たない {@code object X} の直後に二次
     * コンストラクタが並ぶと、その引数と本体を {@code object X} のものとして読み、
     * ローカル変数がフィールドとして図に出ていた。</p>
     *
     * @return プライマリコンストラクタの {@code (} の位置。無ければ -1
     */
    static int primaryCtorParenAfter(String src, int from, String kindKw) {
        if ("object".equals(kindKw) || "interface".equals(kindKw)) {
            return -1;
        }
        return primaryCtorParenAfter(src, from);
    }

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
        if (isConstructorDelegation(list)) {
            // `: this(…)` / `: super(…)` は<b>コンストラクタの委譲</b>であって継承ではない。
            // 継承リストの走査はコメント・where・括弧の深さを知るようになったのに、
            // 委譲だけ知らなかった。読み違えると "this" / "super" という<b>存在しない箱</b>への
            // 継承線が引かれる (二次コンストラクタを持つクラスで実測)。
            return;
        }
        // 入れ子の内側の { } や where は打ち切りではない。深さを数えないと
        // `Base(object : Cb() { … }), Marker` の Marker が落ちる。
        Depth depth = new Depth();
        for (int i = 0; i < list.length(); i++) {
            int nc = KotlinLightScanner.skipNonCode(list, i);
            if (nc > i) {
                i = nc - 1;
                continue;
            }
            char c = list.charAt(i);
            // `where` の型制約はスーパータイプではない。継承コロンが<b>無い</b>形は
            // topLevelColon が打ち切るが、スーパータイプが有ると先に継承コロンが
            // 返るのでここまで来る — 同じ規則を両方の経路に入れる。入れないと
            // 最後の項が where 節を丸ごと飲み込み、`Marker where T : Comparable` と
            // いう存在しない箱への実装線が引かれ、本物への線は 1 本も引かれない。
            if (depth.top() && (c == '{' || c == '}' || isWordAt(list, i, "where"))) {
                list = list.substring(0, i);
                break;
            }
            depth.feed(list, i);
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
