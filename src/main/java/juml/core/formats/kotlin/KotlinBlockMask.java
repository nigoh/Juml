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
 *   <li>{@link #scanDeclPrefix} — 宣言の前に並ぶ annotation と修飾子はどこまでか</li>
 * </ul>
 */
final class KotlinBlockMask {

    /**
     * 宣言の前置として読んでよい修飾子。ここに無い語に当たった時点で前置は終わる。
     *
     * <p>{@code val} {@code var} {@code fun} {@code class} {@code interface}
     * {@code object} などの宣言キーワードは<b>入れない</b> — 前置はそこで止まる。</p>
     */
    private static final java.util.Set<String> MODIFIERS =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "public", "private", "protected", "internal",
                    "open", "abstract", "final", "sealed", "data", "inner",
                    "companion", "enum", "annotation", "override", "lateinit",
                    "const", "suspend", "inline", "external", "expect", "actual",
                    "tailrec", "operator", "infix", "noinline", "crossinline",
                    "vararg", "reified",
                    // value class (Kotlin 1.5+)。宣言キーワードへ到達できないと前置が
                    // 引けず annotation が黙って空になる (実測: @JvmInline value class Money)。
                    // fun interface の fun は<b>ここに入れない</b> — fun は関数の宣言
                    // キーワードでもあるので、入れると `@Query fun count()` の前置が
                    // 関数ではなく名前の位置に記録されて annotation が全滅する。
                    // 「fun の次が interface のときだけ修飾子」は下で形として判定する。
                    "value"));

    /** 宣言キーワードの直前に並んでいた annotation と修飾子。 */
    static final class DeclPrefix {
        /** 前置の開始位置 (annotation も修飾子も無ければ宣言キーワードと同じ)。 */
        final int start;
        /** 前置を読み切った位置 = 宣言キーワードの先頭。 */
        final int declStart;
        /** {@code "@Name(...)"} をソースのまま並べたもの。 */
        final java.util.List<String> annotations;
        /** 修飾子を空白区切りで連結したもの (可視性・{@code const} 判定用)。 */
        final String modifiers;

        DeclPrefix(int start, int declStart,
                   java.util.List<String> annotations, String modifiers) {
            this.start = start;
            this.declStart = declStart;
            this.annotations = annotations;
            this.modifiers = modifiers;
        }
    }

    private KotlinBlockMask() {
    }

    /**
     * {@code from} から annotation と修飾子の並びを走査し、宣言キーワードの手前まで進める。
     *
     * <p><b>annotation の引数は括弧の対応で決まる</b> — これがこのクラスの言明である。
     * 以前は 6 つの経路 (クラスヘッダ / プロパティ / 関数 / ctor 引数 / 関数引数 /
     * annotation 分解) がそれぞれ別の正規表現で引数を<b>数え上げて</b>いた:
     * {@code \([^)]*\)} は「括弧の中に {@code )} が 1 つも無い」、
     * {@code (?:[^()]|\([^()]*\))*} は「入れ子はちょうど 1 段まで」。どちらも実測で外れた —
     * {@code @Query("SELECT COUNT(*) FROM user")} は文字列の中の {@code )} で切れて
     * DAO のメソッドが 1 件も出ず、{@code @Entity(foreignKeys = [ForeignKey(…arrayOf("id")…)])}
     * は 2 段目で切れてエンティティごと ER 図から消えた。しかも経路ごとに段数が違うので、
     * <b>同じ annotation を書く場所によって答えが変わる</b>という食い違いになっていた。</p>
     *
     * <p>括弧の中に何が入りうるかを数え上げる代わりに、括弧の対応を取る。文字列・コメントは
     * {@link KotlinLightScanner#matchParen} が読み飛ばすので、SQL の中の {@code )} も
     * {@code datetime('now')} も引数の終わりと誤読しない。</p>
     */
    static DeclPrefix scanDeclPrefix(String s, int from) {
        java.util.List<String> anns = new java.util.ArrayList<>();
        StringBuilder mods = new StringBuilder();
        int i = from;
        while (i < s.length()) {
            if (Character.isWhitespace(s.charAt(i))) {
                i++;
                continue;
            }
            int nonCode = KotlinLightScanner.skipNonCode(s, i);
            if (nonCode > i) {
                i = nonCode; // 前置に挟まった KDoc・行コメントは宣言の一部ではない
                continue;
            }
            if (s.charAt(i) == '@') {
                int end = annotationEnd(s, i);
                if (end <= i) {
                    break;
                }
                // use-site target (@field: / @get: / @param: …) は annotation 名ではない。
                // 落とさないと @field:SerializedName(...) が "@field" 扱いになり本名が消える。
                anns.add("@" + s.substring(annotationNameStart(s, i), end));
                i = end;
                continue;
            }
            int wordEnd = identifierEnd(s, i);
            if (wordEnd <= i) {
                break;
            }
            String word = s.substring(i, wordEnd);
            // `fun interface` の fun だけは修飾子。関数の fun と綴りが同じなので
            // 語だけでは決まらない — 次の語が interface かどうかで決める。
            boolean funInterface = "fun".equals(word)
                    && "interface".equals(nextWord(s, wordEnd));
            if (!funInterface && !MODIFIERS.contains(word)) {
                break; // 宣言キーワードか、そもそも宣言ではない
            }
            mods.append(word).append(' ');
            i = wordEnd;
        }
        return new DeclPrefix(from, i, anns, mods.toString().trim());
    }

    /**
     * {@code from} から続く<b>annotation だけ</b>の並びの終端。修飾子は食わない。
     *
     * <p>宣言キーワードを持たない宣言 (enum 定数) 用。修飾子まで食う
     * {@link #scanDeclPrefix} をそこに使うと、名前が修飾子と同じ綴りのときに
     * 名前ごと消える。</p>
     */
    static int annotationRunEnd(String s, int from) {
        int i = from;
        while (i < s.length()) {
            if (Character.isWhitespace(s.charAt(i))) {
                i++;
                continue;
            }
            int nonCode = KotlinLightScanner.skipNonCode(s, i);
            if (nonCode > i) {
                i = nonCode;
                continue;
            }
            if (s.charAt(i) != '@') {
                break;
            }
            int end = annotationEnd(s, i);
            if (end <= i) {
                break;
            }
            i = end;
        }
        return i;
    }

    /**
     * ソース全体を 1 度走査して「宣言キーワードの位置 → その前置」の対応を作る。
     *
     * <p>正規表現側は宣言キーワードから先だけを見るようにし、前置はすべてここから引く。
     * こうしないと、正規表現に前置を書いた瞬間にその経路だけ別の数え上げ規則を持つ。</p>
     */
    static java.util.Map<Integer, DeclPrefix> declPrefixes(String s) {
        java.util.Map<Integer, DeclPrefix> out = new java.util.HashMap<>();
        for (int i = 0; i < s.length(); i++) {
            int nonCode = KotlinLightScanner.skipNonCode(s, i);
            if (nonCode > i) {
                i = nonCode - 1;
                continue;
            }
            char c = s.charAt(i);
            boolean startsPrefix = c == '@' || KotlinLightScanner.isIdentPart(c);
            if (!startsPrefix
                    || (i > 0 && KotlinLightScanner.isIdentPart(s.charAt(i - 1)))) {
                continue;
            }
            DeclPrefix pre = scanDeclPrefix(s, i);
            if (pre.declStart <= i) {
                continue; // 前置ではなかった
            }
            out.putIfAbsent(pre.declStart, pre);
            i = pre.declStart - 1; // 読んだ前置の中は再走査しない
        }
        return out;
    }

    /**
     * {@code @target:} を読み飛ばした実 annotation 名の開始位置 ({@code @} の次)。
     *
     * <p>use-site target を綴りで数え上げない — {@code @a:b} という形は Kotlin では
     * use-site target 以外の意味を持たないので、<b>形</b>で判定できる。</p>
     */
    private static int annotationNameStart(String s, int at) {
        int i = identifierEnd(s, at + 1);
        int colon = skipSpaces(s, i);
        if (i > at + 1 && colon < s.length() && s.charAt(colon) == ':') {
            int name = skipSpaces(s, colon + 1);
            if (identifierEnd(s, name) > name) {
                return name;
            }
        }
        return at + 1;
    }

    /**
     * {@code from} が annotation の始まりならその終端 exclusive、そうでなければ {@code from}。
     *
     * <p>annotation の中には {@code @get:Foo} の use-site target のように、外から見ると
     * 宣言の {@code :} と区別がつかない文字が入る。annotation を読み飛ばしてから
     * 宣言を読む経路は、この 1 本を通す。</p>
     */
    static int skipAnnotation(String s, int from) {
        return annotationEnd(s, from);
    }

    /** {@code from} の annotation 1 個の終端 exclusive。annotation でなければ {@code from}。 */
    private static int annotationEnd(String s, int from) {
        if (from >= s.length() || s.charAt(from) != '@') {
            return from;
        }
        int i = identifierEnd(s, from + 1);
        if (i <= from + 1) {
            return from;
        }
        int nameStart = annotationNameStart(s, from);
        if (nameStart > from + 1) {
            i = identifierEnd(s, nameStart);
        }
        // 修飾名 (@androidx.room.Query) の残り
        while (i + 1 < s.length() && s.charAt(i) == '.') {
            int after = identifierEnd(s, i + 1);
            if (after <= i + 1) {
                break;
            }
            i = after;
        }
        if (i < s.length() && s.charAt(i) == '(') {
            int close = KotlinLightScanner.matchParen(s, i);
            if (close <= i) {
                return from; // 閉じていない = annotation として読めない
            }
            i = close + 1;
        }
        return i;
    }

    /**
     * {@code [from, nameStart)} に語としての {@code val} / {@code var} があるか。
     * これがある primary constructor 引数だけがクラスのプロパティになる。
     */
    static boolean declaresProperty(String param, int from, int nameStart) {
        for (int i = Math.max(0, from); i < nameStart; i++) {
            int e = KotlinLightScanner.skipNonCode(param, i);
            if (e > i) { i = e - 1; continue; }
            if (!KotlinLightScanner.isIdentStart(param.charAt(i))
                    || (i > 0 && KotlinLightScanner.isIdentPart(param.charAt(i - 1)))) {
                continue;
            }
            int end = i;
            while (end < param.length() && KotlinLightScanner.isIdentPart(param.charAt(end))) end++;
            String word = param.substring(i, end);
            if ("val".equals(word) || "var".equals(word)) {
                return true;
            }
            i = end - 1;
        }
        return false;
    }

    /**
     * 引数 1 つ分から「名前」と「型の開始位置」を切り出す。
     * 返すのは {@code {nameStart, nameEnd, typeStart}}、引数でなければ null。
     *
     * <p><b>引数の名前は、入れ子の外側にある最初の {@code :} の直前の識別子</b> —
     * これが唯一の言明である。前置 (annotation・修飾子) を読み飛ばしてから名前を探すと、
     * 名前が修飾子と同じ綴り ({@code data} / {@code value} / {@code operator} …) のときに
     * 名前ごと前置として食われる。1 度そうなって「前置の手前から読み直す」逃げ道を足したが、
     * 名前の前に annotation が 1 つでもあると読み直しも失敗し、その引数が<b>黙って消えて</b>
     * いた ({@code @Body data: Payload} が丸ごと落ち、クラス図には実在しないシグネチャが出た)。
     * 数え上げた集合に名前が入っているかどうかで結果が変わる作りをやめる。</p>
     */
    static int[] nameBeforeTopLevelColon(String param) {
        int depth = 0;
        for (int i = 0; i < param.length(); i++) {
            int e = KotlinLightScanner.skipNonCode(param, i);
            if (e > i) { i = e - 1; continue; }
            char c = param.charAt(i);
            if (c == '@') {
                // annotation の中の `:` (use-site target `@get:Foo`) は宣言の `:` ではない。
                int annEnd = skipAnnotation(param, i);
                if (annEnd > i) { i = annEnd - 1; continue; }
            }
            if (c == '(' || c == '[' || c == '<' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '>' || c == '}') {
                if (depth > 0) depth--;
            } else if (c == ':' && depth == 0) {
                int end = i;
                while (end > 0 && Character.isWhitespace(param.charAt(end - 1))) end--;
                int start = end;
                while (start > 0 && KotlinLightScanner.isIdentPart(param.charAt(start - 1))) start--;
                if (start >= end || !KotlinLightScanner.isIdentStart(param.charAt(start))) {
                    return null;
                }
                return new int[]{start, end, i + 1};
            } else if (c == '=' && depth == 0) {
                return null; // 型を持たない引数 (名前付き実引数など) は宣言ではない
            }
        }
        return null;
    }

    /** 先頭の空白とコメントを取り除く (コメントは宣言の一部ではない)。 */
    /** コメント・文字列リテラルを空白 1 つに畳んだ文字列 (宣言だけを残す)。 */
    static String codeOnly(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            int e = KotlinLightScanner.skipNonCode(s, i);
            if (e > i) {
                // 文字列リテラルは<b>そのまま残す</b> — 中身は宣言の一部だからである。
                // 潰していたため enum 定数の引数が `RED( )` になり、生成 PlantUML から
                // 文字列が消えていた (Java 側の同じ enum は正しく残る、という食い違い)。
                // 消してよいのはコメントだけ。
                if (s.charAt(i) == '/') {
                    sb.append(' ');
                } else {
                    sb.append(s, i, e);
                }
                i = e - 1;
                continue;
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    /** {@code ()} / {@code []} / {@code &#123;&#125;} のネスト外にある最初の {@code ;} の位置。無ければ -1。 */
    static int topLevelSemicolon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth > 0) {
                    depth--;
                }
            } else if (c == ';' && depth == 0) {
                return i;
            }
        }
        return -1;
    }
    /** {@code from} 以降の空白を読み飛ばした先にある語 (無ければ空文字列)。 */
    private static String nextWord(String s, int from) {
        int at = skipSpaces(s, from);
        int end = identifierEnd(s, at);
        return end > at ? s.substring(at, end) : "";
    }

    /** {@code from} 以降の空白を読み飛ばした位置。 */
    private static int skipSpaces(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /** {@code from} から続く識別子の終端 exclusive (識別子で始まらなければ {@code from})。 */
    private static int identifierEnd(String s, int from) {
        if (from >= s.length() || !KotlinLightScanner.isIdentPart(s.charAt(from))
                || Character.isDigit(s.charAt(from))) {
            return from;
        }
        int i = from;
        while (i < s.length() && KotlinLightScanner.isIdentPart(s.charAt(i))) {
            i++;
        }
        return i;
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
                // 入れ子の外なら型はそこで終わり。内側なら<b>読み飛ばして続ける</b>。
                // ここで e-1 を返していたため、括弧の中にコメントや文字列があると
                // 型がその途中で切れ、`(Int /* id *` のような半端な文字列が図に出ていた。
                if (depth == 0) {
                    return i;
                }
                i = e - 1;
                continue;
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
                        || isKeywordAt(s, i, "by") || isKeywordAt(s, i, "where")) {
                    // where は関数にも書ける (`fun <T> sort(a: List<T>): List<T>
                    // where T : Comparable<T>`)。型制約は戻り値の型ではないのに、
                    // クラスヘッダ側だけが where を知っていた。
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
