// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kotlin ソースを正規表現ベースで軽量パースし、既存の {@link JavaClassInfo} ツリーに
 * 変換するブリッジ。
 *
 * <p>厳密な Kotlin パーサではなく、Java 側の解析パイプライン
 * ({@link juml.core.dataflow.RoomAnalyzer} 等) で Kotlin クラスも見えるようにする
 * ための最小実装。抽出するもの:</p>
 *
 * <ul>
 *   <li>{@code package com.x} (セミコロン任意)</li>
 *   <li>{@code import com.x.Y} / {@code import com.x.*}</li>
 *   <li>{@code class Foo} / {@code interface Foo} / {@code object Foo} /
 *       {@code data class Foo} / {@code enum class Foo} /
 *       {@code annotation class Foo} と直前の {@code @Annotation}</li>
 *   <li>クラスのプライマリコンストラクタパラメータの {@code val/var name: Type}
 *       (Room の {@code @PrimaryKey} 付きパラメータが取れる)</li>
 *   <li>クラス本体の {@code val/var name: Type} プロパティ</li>
 *   <li>クラス本体の {@code fun name(...): ReturnType { ... }} (アノテーションも保持)</li>
 * </ul>
 *
 * <p>取らないもの: 関数本体の解析、ジェネリクスの精密展開、Lambda、Compose
 * {@code @Composable} ツリー、拡張関数 (extension function)。</p>
 */
public final class KotlinLightScanner {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([\\w.]+)\\s*;?\\s*$");
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+([\\w.*]+)\\s*;?\\s*$");
    /**
     * クラスヘッダパターン。グループ 1 = 種別キーワード, グループ 2 = クラス名。
     *
     * <p><b>annotation と修飾子はここに書かない</b>。書けばこの経路だけが独自の
     * 「引数の括弧を何段まで許すか」を持つことになり、実際そうなっていた
     * ({@code @Entity(foreignKeys = [ForeignKey(… arrayOf("id") …)])} が 2 段目で
     * 切れてクラスの annotation が空になり、ER 図からテーブルごと消えた)。前置は
     * {@link KotlinBlockMask#declPrefixes} から引く — 6 経路で 1 つの規則を使う。</p>
     */
    private static final Pattern CLASS_HEADER = Pattern.compile(
            "(?<![A-Za-z0-9_$.:])(class|interface|object)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    /**
     * クラス本体内の {@code val/var name:} まで。<b>型は正規表現で切らない</b>。
     *
     * <p>型を正規表現で取ろうとするかぎり、「型に使ってよい文字」と「型の直後に来てよい
     * トークン」の 2 つを<b>数え上げる</b>ことになる。数え上げは必ず取りこぼし、そのたびに
     * 同じ壊れ方をする: {@code (} を足せば次は {@code *} ({@code Class<*>} が丸ごと消える)、
     * {@code *} を足せば次はコメント ({@code val a: Int // 個数} が消える)、その次は
     * 1 行本体の {@code }}…。しかも足した文字が別の意味を持つこともある —
     * {@code )} を型文字にした結果、入れ子クラスのコンストラクタ引数
     * {@code class Item(val id: Long)} が<b>外側のクラス</b>に型 {@code Long)} で生えた。</p>
     *
     * <p>正しい言明は 1 つ:<b>型は {@code :} の次から宣言の終わりまで</b>。終わりの判定は
     * 入れ子 ({@code &lt;&gt; () []}) と文字列・コメントを見ながら走査する
     * {@link KotlinBlockMask#propertyTypeEnd} が行う。</p>
     */
    private static final Pattern PROPERTY = Pattern.compile(
            "(?<![A-Za-z0-9_$.:])(val|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*");

    /**
     * {@code fun} キーワードだけ。<b>型パラメータも引数も戻り値もここで切らない</b>。
     *
     * <p>型パラメータは {@code (?:<[^>]+>\s+)?} と書いていた — 「{@code >} を含まない 1 段」
     * という数え上げなので、制約付き {@code fun <T : Comparable<T>> maxOf(…)} で照合が
     * 失敗し、そのメソッドが<b>丸ごとクラス図から消えて</b>いた。同じ入れ子はクラスヘッダ側の
     * {@code KotlinHeaderScan.skipAngles} が深さを数えて正しく読んでいる。引数リストと
     * 戻り値の型はすでに走査へ寄せてあったので、残っていた型パラメータもそこへ揃える。</p>
     */
    private static final Pattern FUN_DECL = Pattern.compile(
            "(?<![A-Za-z0-9_$.:])fun(?![A-Za-z0-9_$])");

    /** Kotlin ソースから {@link JavaClassInfo} のリストを抽出する。 */
    public static List<JavaClassInfo> scan(String source, ErrorListener listener) {
        List<JavaClassInfo> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        String pkg = "";
        Matcher pm = PACKAGE_PATTERN.matcher(source);
        if (pm.find()) {
            pkg = pm.group(1);
        }
        List<String> imports = new ArrayList<>();
        Matcher im = IMPORT_PATTERN.matcher(source);
        while (im.find()) {
            imports.add(im.group(1));
        }

        // コメント/文字列/文字リテラルの領域マスク。KDoc やコード内文字列に現れる
        // "class" 等 (例: "This class holds ...") を実クラス宣言と誤認しないために使う。
        boolean[] nonCode = nonCodeMask(source);

        // クラスヘッダごとに本体を切り出す。ネスト型も同じループで列挙されるため、
        // 「いま開いているクラス本体」をスタックで追って enclosingClass を決める
        // (設定しないと Outer.Inner が Outer 抜きの QN になり、同名ネスト型が
        //  同じ QN へ衝突して 1 ノードに統合される)。
        KotlinHeaderScan.Nesting nesting = new KotlinHeaderScan.Nesting();
        java.util.Map<Integer, KotlinBlockMask.DeclPrefix> prefixes =
                KotlinBlockMask.declPrefixes(source);
        Matcher cm = CLASS_HEADER.matcher(source);
        while (cm.find()) {
            // 種別キーワード (class/interface/object) がコメント/文字列内なら誤検出。読み飛ばす。
            if (isMasked(nonCode, cm.start(1))) {
                continue;
            }
            KotlinBlockMask.DeclPrefix pre = prefixAt(prefixes, cm.start(1));
            String kindKw = cm.group(1);
            String name = cm.group(2);
            int headerEnd = cm.end();

            JavaClassInfo info = new JavaClassInfo();
            info.setPackageName(pkg);
            info.setSimpleName(name);
            info.setEnclosingClass(nesting.enclosingAt(pre.start));
            info.setKind(mapKind(kindKw, pre.modifiers));
            info.getImports().addAll(imports);
            info.getAnnotations().addAll(pre.annotations);

            // プライマリコンストラクタ引数 (class Foo(val x: Int, ...))
            // 次のクラス宣言位置を上限に探索し、本体 {} を持たないクラスが後続クラスの
            // ブレース/括弧を誤って取り込まないようにする。
            int nextHeader = nextClassHeaderStart(source, headerEnd, nonCode, prefixes);
            int primaryCtorParen =
                    KotlinHeaderScan.primaryCtorParenAfter(source, headerEnd, kindKw);
            if (nextHeader >= 0 && primaryCtorParen >= nextHeader) {
                primaryCtorParen = -1;
            }
            // 本体の { はプライマリコンストラクタの ) より後ろから探す。クラス名の直後から
            // 探していたため `class Foo(val onClick: () -> Unit = {}) { … }` の既定値の {
            // を本体の開きと取り違え、本体が空のラムダになってメンバーが丸ごと消えていた。
            int primaryCtorClose = primaryCtorParen >= 0
                    ? matchParen(source, primaryCtorParen) : -1;
            int bodySearchFrom = primaryCtorClose > primaryCtorParen
                    ? primaryCtorClose + 1 : headerEnd;
            // ヘッダの終わり (本体の { と、スーパータイプを読んでよい終端) は 1 回の走査で
            // 決める。3 経路がばらばらに決めていて、どれも違っていた。
            int[] hdr = KotlinHeaderScan.headerEnd(source, bodySearchFrom);
            int bodyBraceOpen = hdr[0];
            if (nextHeader >= 0 && bodyBraceOpen >= nextHeader) {
                bodyBraceOpen = -1;
            }
            if (primaryCtorClose > primaryCtorParen) {
                extractPrimaryCtorFields(
                        source.substring(primaryCtorParen + 1, primaryCtorClose), info);
            }
            // 本体を持つクラスは「開いている本体」として積み、以降のヘッダが本体内なら
            // このクラスを enclosing とする (閉じ位置を超えたら上の while で捨てられる)。
            if (bodyBraceOpen >= 0) {
                int bodyEnd = matchBrace(source, bodyBraceOpen);
                if (bodyEnd > bodyBraceOpen) {
                    nesting.openBody(name, bodyEnd);
                }
            }

            // スーパークラス / インタフェースの取り込み (: A(), B, C)
            int superRegionEnd = hdr[1];
            if (nextHeader >= 0 && superRegionEnd > nextHeader) {
                superRegionEnd = nextHeader;
            }
            KotlinHeaderScan.extractSupertypes(source, headerEnd, superRegionEnd, info);

            // クラス本体
            if (bodyBraceOpen >= 0) {
                int bodyEnd = matchBrace(source, bodyBraceOpen);
                if (bodyEnd > bodyBraceOpen) {
                    String body = source.substring(bodyBraceOpen + 1, bodyEnd);
                    if (info.getKind() == JavaClassInfo.Kind.ENUM) {
                        extractEnumConstants(body, info);
                    }
                    // 関数本体・init/getter 等のコードブロック内を無視するためのマスク。
                    // ローカル val/var/fun をクラスメンバとして誤抽出しないようにする。
                    // 型本体 (nested class / object / companion object) は従来どおり降りて
                    // メンバをホイストするため、マスク対象にしない。
                    // コメント・文字列の中身も「メンバー宣言として読まない」領域に
                    // 含める。クラスヘッダの走査は最初から nonCodeMask を見ていたのに
                    // メンバー抽出だけが生テキストを見ていたため、コメントアウトした
                    // `// fun legacy(): String` や KDoc 中の `fun close()` が
                    // <b>実在するメンバーとして</b>図に出ていた。
                    boolean[] codeMask = KotlinBlockMask.codeBlockMask(body);
                    boolean[] nonCodeInBody = nonCodeMask(body);
                    for (int k = 0; k < codeMask.length && k < nonCodeInBody.length; k++) {
                        codeMask[k] |= nonCodeInBody[k];
                    }
                    // 前置 (annotation・修飾子) の走査もクラスヘッダとまったく同じ
                    // 実装を使う。本体は切り出した部分文字列なので位置が変わるため
                    // ここで作り直す。
                    java.util.Map<Integer, KotlinBlockMask.DeclPrefix> bodyPres =
                            KotlinBlockMask.declPrefixes(body);
                    extractProperties(body, info, codeMask, bodyPres);
                    extractFunctions(body, info, codeMask, bodyPres);
                }
            }

            out.add(info);
        }
        return out;
    }

    /** {@code from} 以降で次のクラス/interface/object 宣言の開始位置 (無ければ -1)。
     * コメント/文字列内の擬似ヘッダ ({@code nonCode} が true) は読み飛ばす。
     * 返すのは<b>前置 (annotation・修飾子) を含めた先頭</b> — 上限として使うので、
     * ここでキーワード位置を返すと次のクラスの annotation の {@code (} を
     * 手前のクラスのプライマリコンストラクタと取り違える。 */
    private static int nextClassHeaderStart(
            String source, int from, boolean[] nonCode,
            java.util.Map<Integer, KotlinBlockMask.DeclPrefix> prefixes) {
        Matcher m = CLASS_HEADER.matcher(source);
        int at = from;
        while (m.find(at)) {
            if (!isMasked(nonCode, m.start(1))) {
                return prefixAt(prefixes, m.start(1)).start;
            }
            at = m.end();
        }
        return -1;
    }

    /**
     * 宣言キーワード位置 {@code at} の前置を引く。前置が無ければ空の前置を返す。
     */
    private static KotlinBlockMask.DeclPrefix prefixAt(
            java.util.Map<Integer, KotlinBlockMask.DeclPrefix> prefixes, int at) {
        KotlinBlockMask.DeclPrefix pre = prefixes.get(at);
        return pre != null ? pre
                : new KotlinBlockMask.DeclPrefix(at, at, java.util.List.of(), "");
    }

    /**
     * ソース全体について、コメント ({@code //} / {@code /* *}{@code /})・通常/生文字列・
     * 文字リテラルに含まれる位置を true にしたマスクを構築する。{@link #skipNonCode} と
     * 同じ判定を使うため、コード解釈と齟齬が出ない。
     */
    private static boolean[] nonCodeMask(String source) {
        int n = source.length();
        boolean[] mask = new boolean[n];
        int i = 0;
        while (i < n) {
            int e = skipNonCode(source, i);
            if (e > i) {
                for (int k = i; k < e && k < n; k++) {
                    mask[k] = true;
                }
                i = e;
            } else {
                i++;
            }
        }
        return mask;
    }

    /** {@code idx} がマスク範囲内かつ非コードなら true。範囲外は false。 */
    private static boolean isMasked(boolean[] mask, int idx) {
        return idx >= 0 && idx < mask.length && mask[idx];
    }

    /**
     * enum 定数: 識別子と、続く {@code (...)} 引数。<b>前置はここに書かない</b>。
     *
     * <p>書いていた頃はここだけが 0 段の数え上げ {@code \([^)]*\)} を持っていて、
     * annotation の引数に入れ子括弧や文字列中の {@code )} があると照合が失敗し、
     * しかも {@code @[A-Za-z_][\w.]*} がバックトラックするので<b>失敗せずに
     * annotation 名の最後の 1 文字が定数名として採用された</b> —
     * {@code @Deprecated("use ACTIVE", ReplaceWith("ACTIVE")) RUNNING} が
     * {@code d} という名前の定数になって図に出ていた。前置は他の宣言と同じく
     * {@link KotlinBlockMask#scanDeclPrefix} が読む。</p>
     */
    private static final Pattern ENUM_CONST = Pattern.compile(
            "^([A-Za-z_$][A-Za-z0-9_$]*)\\s*(\\(.*\\))?", Pattern.DOTALL);

    /**
     * {@code enum class} 本体から定数を取り込む。定数は本体先頭、最初のトップレベル {@code ;}
     * (なければ本体全体) までをカンマ区切りで列挙したもの。{@code EARTH(5.976e+24)} の引数は
     * {@link JavaClassInfo#getEnumConstantArgs()} に括弧付きで対応保持する。
     */
    private static void extractEnumConstants(String body, JavaClassInfo info) {
        // コメント中のカンマ・セミコロン・括弧で定数を切らない。メンバー抽出は
        // codeMask に nonCodeMask を畳み込んで守られているのに、enum 定数だけが
        // 生の body を読んでいた (実測: コメントに書いた語が定数として描かれ、
        // 実在する定数が消える / コメント中の `;` で定数がゼロになる)。
        body = KotlinBlockMask.codeOnly(body);
        int semi = KotlinBlockMask.topLevelSemicolon(body);
        String constPart = semi >= 0 ? body.substring(0, semi) : body;
        for (String raw : KotlinHeaderScan.splitTopLevelCommas(constPart)) {
            String e = raw.trim();
            if (e.isEmpty()) {
                continue;
            }
            // 読み飛ばすのは<b>annotation だけ</b>。他の 5 経路は宣言キーワード
            // (val/var/fun/class/…) で必ず止まるので修飾子まで食ってよいが、
            // enum 定数には宣言キーワードが無く<b>名前が先頭のトークン</b>なので、
            // 修飾子まで食うと `enum class Mode { open, locked }` の open のように
            // 綴りが修飾子と同じ定数が丸ごと消える。
            String afterPrefix = e.substring(KotlinBlockMask.annotationRunEnd(e, 0));
            Matcher m = ENUM_CONST.matcher(afterPrefix);
            if (!m.find()) {
                continue;
            }
            info.getEnumConstants().add(m.group(1));
            info.getEnumConstantArgs().add(m.group(2) == null ? "" : m.group(2));
        }
    }

    private static JavaClassInfo.Kind mapKind(String kindKw, String modifiers) {
        if ("interface".equals(kindKw)) {
            return JavaClassInfo.Kind.INTERFACE;
        }
        if ("object".equals(kindKw)) {
            // Kotlin object は事実上シングルトン → CLASS として扱う
            return JavaClassInfo.Kind.CLASS;
        }
        // class: enum / annotation / data class を分類。アノテーション (@Foo(...)) を除去して
        // から修飾子トークンだけを whole-word で見る。substring 一致だと
        // @Entity(tableName = "enum_table") の "enum" 等で誤分類する。
        if (modifiers != null) {
            String mods = modifiers.replaceAll(
                    "@[A-Za-z_][\\w.]*(\\((?:[^()]|\\([^()]*\\))*\\))?", " ");
            java.util.Set<String> tokens = new java.util.HashSet<>(
                    java.util.Arrays.asList(mods.trim().split("\\s+")));
            if (tokens.contains("enum")) return JavaClassInfo.Kind.ENUM;
            if (tokens.contains("annotation")) return JavaClassInfo.Kind.ANNOTATION;
        }
        return JavaClassInfo.Kind.CLASS;
    }

    /**
     * プライマリコンストラクタ引数を解析してフィールドとして追加。
     * カンマで分割した後、各パラメータごとに {@code val/var name: Type} を取り出す。
     * 通常のメソッド引数 (val/var なしの単純 {@code name: Type}) はフィールド化しない。
     *
     * <p>前置は {@link KotlinBlockMask#scanDeclPrefix}、型は
     * {@link KotlinBlockMask#propertyTypeEnd} — どちらもクラス本体のプロパティと
     * <b>同じ実装</b>を通す。以前はこの経路だけが正規表現 1 本 ({@code matches()}) で
     * 前置も型も既定値もまとめて取っていたため、型や既定値が改行を跨いだだけで
     * 全体が不一致になり、そのプロパティが<b>警告も無く図から消えて</b>いた
     * (同じ宣言をクラス本体に書けば通る、という経路依存の食い違い)。</p>
     */
    private static void extractPrimaryCtorFields(String paramsText, JavaClassInfo info) {
        if (paramsText == null) return;
        for (String raw : KotlinHeaderScan.splitTopLevelCommas(paramsText)) {
            int[] span = KotlinBlockMask.nameBeforeTopLevelColon(raw);
            if (span == null) {
                continue;
            }
            // val/var が付いた引数だけがプロパティ。名前は関数引数と同じ規則で取る。
            KotlinBlockMask.DeclPrefix pre = KotlinBlockMask.scanDeclPrefix(raw, 0);
            if (!KotlinBlockMask.declaresProperty(raw, pre.declStart, span[0])) {
                continue;
            }
            int typeEnd = KotlinBlockMask.propertyTypeEnd(raw, span[2]);
            String type = raw.substring(span[2], typeEnd).trim();
            if (type.isEmpty()) {
                continue;
            }
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(raw.substring(span[0], span[1]));
            f.setType(type);
            f.setVisibility(visibilityOf(pre.modifiers));
            f.getAnnotations().addAll(pre.annotations);
            info.getFields().add(f);
        }
    }

    /**
     * Kotlin の可視性修飾子を UML 可視性へ写像する。{@code internal} はモジュール内可視のため
     * 最も近い package-private ({@code ~}) に割り当て、無指定は {@code public} とする。
     */
    private static Visibility visibilityOf(String mods) {
        if (mods == null) {
            return Visibility.PUBLIC;
        }
        if (mods.contains("private")) {
            return Visibility.PRIVATE;
        }
        if (mods.contains("protected")) {
            return Visibility.PROTECTED;
        }
        if (mods.contains("internal")) {
            return Visibility.PACKAGE;
        }
        return Visibility.PUBLIC;
    }

    /** クラス本体のプロパティを解析してフィールドとして追加。 */
    private static void extractProperties(String body, JavaClassInfo info, boolean[] codeMask,
                                          java.util.Map<Integer, KotlinBlockMask.DeclPrefix> pres) {
        boolean[] inParen = KotlinBlockMask.insideParenMask(body);
        Matcher m = PROPERTY.matcher(body);
        while (m.find()) {
            // 関数本体等のコードブロック内のローカル val/var は除外する。
            if (m.start() < codeMask.length && codeMask[m.start()]) {
                continue;
            }
            // 丸括弧の内側 = 入れ子クラスの primary constructor 引数。外側のクラスの
            // プロパティではない (そのクラス自身の JavaClassInfo 側で別途拾われる)。
            if (m.start() < inParen.length && inParen[m.start()]) {
                continue;
            }
            KotlinBlockMask.DeclPrefix pre = prefixAt(pres, m.start());
            String type = body.substring(m.end(), KotlinBlockMask.propertyTypeEnd(body, m.end())).trim();
            if (type.isEmpty()) {
                continue; // `:` の直後がいきなり終端 = プロパティ宣言ではない
            }
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(m.group(2));
            f.setType(type);
            f.setVisibility(visibilityOf(pre.modifiers));
            f.getAnnotations().addAll(pre.annotations);
            // const val はコンパイル時定数 (実質 static)。companion object の
            // 列名定数などが Room の列やインスタンスフィールドと混同されないよう static 扱い。
            if (pre.modifiers.matches("(?s).*\\bconst\\b.*")) {
                f.setStatic(true);
            }
            info.getFields().add(f);
        }
    }

    /** クラス本体の {@code fun ...} を解析してメソッドとして追加。 */
    private static void extractFunctions(String body, JavaClassInfo info, boolean[] codeMask,
                                         java.util.Map<Integer, KotlinBlockMask.DeclPrefix> pres) {
        Matcher m = FUN_DECL.matcher(body);
        while (m.find()) {
            // 関数本体等のコードブロック内のローカル fun は除外する。
            if (m.start() < codeMask.length && codeMask[m.start()]) {
                continue;
            }
            KotlinBlockMask.DeclPrefix pre = prefixAt(pres, m.start());
            // 型パラメータ <…> は入れ子を数えて読み飛ばす (深さを数えないと
            // `fun <T : Comparable<T>> maxOf(…)` でメソッドごと消える)。
            int at = nextNonSpaceChar(body, m.end());
            if (at >= 0 && body.charAt(at) == '<') {
                int angleEnd = KotlinHeaderScan.skipAngles(body, at);
                if (angleEnd < 0) {
                    continue;
                }
                at = nextNonSpaceChar(body, angleEnd);
            }
            if (at < 0 || !isIdentStart(body.charAt(at))) {
                continue; // 拡張関数のレシーバ型など、名前が直接来ない形は取らない
            }
            int nameEnd = at;
            while (nameEnd < body.length() && isIdentPart(body.charAt(nameEnd))) {
                nameEnd++;
            }
            String name = body.substring(at, nameEnd);
            int open = nextNonSpaceChar(body, nameEnd);
            if (open < 0 || body.charAt(open) != '(') {
                continue;
            }
            // 引数リストは括弧の対応で切る。
            int close = matchParen(body, open);
            if (close <= open) {
                continue; // 閉じていない = 宣言として読めない
            }
            String paramsText = body.substring(open + 1, close);
            // 戻り値の型はプロパティとまったく同じ走査で読む。
            int afterSig = close + 1;
            String returnType = null;
            int colon = nextNonSpaceChar(body, afterSig);
            if (colon >= 0 && body.charAt(colon) == ':') {
                int typeEnd = KotlinBlockMask.propertyTypeEnd(body, colon + 1);
                returnType = body.substring(colon + 1, typeEnd).trim();
                afterSig = typeEnd;
            }
            JavaMethodInfo mth = new JavaMethodInfo();
            mth.setName(name);
            mth.setReturnType(returnType == null || returnType.isEmpty() ? "Unit" : returnType);
            mth.setVisibility(visibilityOf(pre.modifiers));
            mth.getAnnotations().addAll(pre.annotations);
            parseParameters(paramsText, mth);
            // メソッド本体内の呼び出しを抽出。ブロック本体か式本体かを判定。
            int next = nextNonSpaceChar(body, afterSig);
            if (next >= 0 && body.charAt(next) == '{') {
                int braceEnd = matchBrace(body, next);
                if (braceEnd > next) {
                    extractCallsFromBody(body.substring(next + 1, braceEnd), mth);
                }
            } else if (next >= 0 && body.charAt(next) == '=') {
                // 式本体: `fun foo(...) = expression` または
                // `fun foo(...): Type = expression`
                int exprEnd = findExpressionBodyEnd(body, next + 1);
                if (exprEnd > next + 1) {
                    extractCallsFromBody(body.substring(next + 1, exprEnd), mth);
                }
            }
            info.getMethods().add(mth);
        }
    }

    /**
     * {@code from} 位置以降で空白以外の最初の文字オフセットを返す。改行は空白として扱う。
     * 見つからなければ -1。
     */
    private static int nextNonSpaceChar(String body, int from) {
        for (int i = from; i < body.length(); i++) {
            if (!Character.isWhitespace(body.charAt(i))) return i;
        }
        return -1;
    }

    /**
     * 式本体 {@code = expression} の終了オフセットを返す。
     *
     * <p>Kotlin の式本体関数 {@code fun foo() = bar.baz()} の終端は、
     * トップレベル (深さ 0) で次の {@code fun}, {@code val}, {@code var},
     * {@code class}, {@code object}, {@code @}, {@code }} (クラス閉じ),
     * もしくはファイル末尾。各種括弧の対応を取りながら走査する。</p>
     */
    private static int findExpressionBodyEnd(String body, int from) {
        int n = body.length();
        int depth = 0;
        int braceDepth = 0;
        for (int i = from; i < n; i++) {
            int e = skipNonCode(body, i);
            if (e > i) { i = e - 1; continue; }
            char c = body.charAt(i);
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') { if (depth > 0) depth--; }
            else if (c == '{') braceDepth++;
            else if (c == '}') {
                if (braceDepth > 0) braceDepth--;
                else return i; // クラス本体の閉じ
            }
            else if (c == '\n' && depth == 0 && braceDepth == 0) {
                // 改行後に次の宣言が来るなら式本体終了
                int j = nextNonSpaceChar(body, i + 1);
                if (j < 0) return i;
                if (looksLikeDeclarationStart(body, j)) return i;
            }
        }
        return n;
    }

    /**
     * 指定位置 {@code at} が宣言の始まりに見えるか? ({@code fun}, {@code val},
     * {@code var}, {@code class}, {@code object}, {@code @}, {@code private},
     * {@code protected}, {@code internal}, {@code public}, {@code abstract},
     * {@code override}, {@code companion} など)。
     */
    private static boolean looksLikeDeclarationStart(String body, int at) {
        if (at < 0 || at >= body.length()) return false;
        char c = body.charAt(at);
        if (c == '@' || c == '}') return true;
        if (!isIdentStart(c)) return false;
        int end = at;
        while (end < body.length() && isIdentPart(body.charAt(end))) end++;
        String word = body.substring(at, end);
        switch (word) {
            case "fun":
            case "val":
            case "var":
            case "class":
            case "interface":
            case "object":
            case "private":
            case "protected":
            case "internal":
            case "public":
            case "abstract":
            case "override":
            case "open":
            case "final":
            case "sealed":
            case "data":
            case "inner":
            case "companion":
            case "lateinit":
            case "const":
            case "suspend":
            case "inline":
            case "operator":
            case "infix":
            case "init":
                return true;
            default:
                return false;
        }
    }

    /**
     * {@code name: Type, name2: Type2 = default} を解析してパラメータに追加。
     *
     * <p>前置と型の読み方はプライマリコンストラクタ引数と<b>同じ実装</b>を通す。
     * 以前はこの経路だけが正規表現 1 本 ({@code matches()}) で、先頭のコメントも
     * 入れ子括弧を含む annotation も食えず、引数リストのどこかにコメントを 1 つ書くだけで
     * <b>どれか 1 つの引数が黙って消えて</b>いた。消えた引数はシグネチャからも消えるので、
     * クラス図には実在しない引数列が出る (欠損ではなく誤りになる)。</p>
     */
    private static void parseParameters(String text, JavaMethodInfo mth) {
        if (text == null || text.trim().isEmpty()) return;
        // ジェネリクスを尊重した split
        for (String raw : KotlinHeaderScan.splitTopLevelCommas(text)) {
            int[] span = KotlinBlockMask.nameBeforeTopLevelColon(raw);
            if (span == null) continue;
            int typeFrom = span[2];
            String type = raw.substring(
                    typeFrom, KotlinBlockMask.propertyTypeEnd(raw, typeFrom)).trim();
            if (type.isEmpty()) continue;
            mth.getParameterNames().add(raw.substring(span[0], span[1]));
            mth.getParameterTypes().add(type);
        }
    }

    private static String stripLeadingNonCode(String s) {
        int i = 0;
        while (i < s.length()) {
            if (Character.isWhitespace(s.charAt(i))) {
                i++;
                continue;
            }
            int e = skipNonCode(s, i);
            if (e <= i) {
                break;
            }
            i = e;
        }
        return s.substring(i);
    }

    /**
     * Kotlin の制御フローキーワード/予約語。{@code foo(...)} 呼び出し検出時に
     * これらが識別子として現れたら call とみなさない。
     */
    private static final java.util.Set<String> CONTROL_KEYWORDS =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "if", "else", "while", "for", "do", "when", "try", "catch",
                    "finally", "return", "throw", "break", "continue",
                    "val", "var", "fun", "class", "object", "interface",
                    "is", "as", "in", "by", "this", "super",
                    "true", "false", "null", "package", "import",
                    "fun", "operator", "infix", "lateinit", "const",
                    "private", "protected", "public", "internal", "open",
                    "abstract", "final", "override", "suspend", "inline",
                    "data", "sealed", "enum", "annotation", "inner",
                    "companion", "out", "in", "where", "init"));

    /**
     * Kotlin 関数本体から {@code receiver.method(...)} 形式の呼び出しを抽出する。
     * receiver の末尾の {@code ?} ({@code obj?.method}) や {@code !!}
     * ({@code obj!!.method}) は除去して JavaMethodInfo.Call に格納する。
     */
    private static void extractCallsFromBody(String body, JavaMethodInfo mth) {
        if (body == null || body.isEmpty()) return;
        int n = body.length();
        int i = 0;
        while (i < n) {
            // 文字列・コメント・文字リテラルをスキップ (中の '(' を呼び出しと誤認しない)。
            int e = skipNonCode(body, i);
            if (e > i) { i = e; continue; }
            char c = body.charAt(i);

            // 識別子の開始?
            if (isIdentStart(c)) {
                int idStart = i;
                while (i < n && isIdentPart(body.charAt(i))) i++;
                String ident = body.substring(idStart, i);
                // 次が `(` で識別子が制御キーワードでなければ呼び出し候補
                int j = i;
                while (j < n && Character.isWhitespace(body.charAt(j))) j++;
                if (j < n && body.charAt(j) == '(' && !CONTROL_KEYWORDS.contains(ident)) {
                    // 直前のシーケンスから receiver を取り出す
                    String receiver = extractReceiverBackward(body, idStart);
                    mth.getStatements().add(new JavaMethodInfo.Call(receiver, ident));
                }
                continue;
            }
            i++;
        }
    }

    /**
     * {@code idStart} 直前のトークンを見て receiver 文字列を組み立てる。
     * {@code .}, {@code ?.}, {@code !!.} のいずれかが直前にあれば、その前の識別子チェーンを
     * receiver として返す。なければ空文字 (同クラス呼び出し)。
     */
    private static String extractReceiverBackward(String body, int idStart) {
        int j = idStart - 1;
        // 空白を読み飛ばす
        while (j >= 0 && Character.isWhitespace(body.charAt(j))) j--;
        if (j < 0) return "";
        char c = body.charAt(j);
        // ?. or !!. or .
        if (c == '.') {
            j--; // skip '.'
        } else {
            return "";
        }
        // ? や !! を消費
        while (j >= 0 && (body.charAt(j) == '?' || body.charAt(j) == '!')) {
            j--;
        }
        // 空白
        while (j >= 0 && Character.isWhitespace(body.charAt(j))) j--;
        // 識別子チェーン (a.b.c) を逆方向に収集
        StringBuilder sb = new StringBuilder();
        while (j >= 0) {
            char cc = body.charAt(j);
            if (cc == ')' || cc == ']') {
                // チェーン経由の呼び出し: 中を全部スキップ
                int depth = 1;
                j--;
                char open = cc == ')' ? '(' : '[';
                char close = cc;
                while (j >= 0 && depth > 0) {
                    char k = body.charAt(j);
                    if (k == close) depth++;
                    else if (k == open) depth--;
                    j--;
                }
                continue;
            }
            if (isIdentPart(cc)) {
                sb.insert(0, cc);
                j--;
            } else if (cc == '.' && j > 0 && isIdentPart(body.charAt(j - 1))) {
                sb.insert(0, '.');
                j--;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int findNextChar(String src, int from, char target) {
        for (int i = from; i < src.length(); i++) {
            // 文字列/コメント/文字リテラル内の target (例: 一次コンストラクタ既定値
            // "{" の中の '{') を本体開始と誤検出しないようスキップする。
            int e = skipNonCode(src, i);
            if (e > i) { i = e - 1; continue; }
            if (src.charAt(i) == target) return i;
            // クラスヘッダ末尾と本体開始の間に出てくる文字: '<' (generics),
            // ':' (継承), 'where' などを想定して途中で他の不正な文字に遭遇しても続行
        }
        return -1;
    }

    static int matchParen(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '(') return open;
        return matchBalance(src, open, '(', ')');
    }

    static int matchBrace(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') return open;
        return matchBalance(src, open, '{', '}');
    }

    /**
     * {@code src[i]} が「コードでない範囲」の開始なら、その範囲全体を読み飛ばして
     * 「次の」インデックスを返す。開始でなければ {@code i} をそのまま返す。
     *
     * <p>対象: 行コメント {@code //}、ブロックコメント (ネスト可)、通常文字列 {@code "…"}、
     * 生文字列 {@code """…"""} (エスケープなし)、文字リテラル {@code '…'} (エスケープ考慮)。
     * これを各走査ループで使うことで、文字列/コメント/文字リテラル内の {@code {} } {@code "}
     * などをコードのブレース/引用符と取り違えてクラス本体を途中で切ってしまうのを防ぐ。
     * 未終端は末尾 (通常文字列/文字リテラル/行コメントは改行) で打ち切る。</p>
     */
    static int skipNonCode(String src, int i) {
        int n = src.length();
        char c = src.charAt(i);
        if (c == '/' && i + 1 < n) {
            char d = src.charAt(i + 1);
            if (d == '/') {
                int j = i + 2;
                while (j < n && src.charAt(j) != '\n') j++;
                return j;
            }
            if (d == '*') {
                int j = i + 2;
                int depth = 1;
                while (j < n) {
                    if (j + 1 < n && src.charAt(j) == '/' && src.charAt(j + 1) == '*') {
                        depth++; j += 2;
                    } else if (j + 1 < n && src.charAt(j) == '*' && src.charAt(j + 1) == '/') {
                        depth--; j += 2;
                        if (depth == 0) return j;
                    } else {
                        j++;
                    }
                }
                return n;
            }
        }
        if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
            int j = i + 3;
            while (j <= n - 3) {
                if (src.charAt(j) == '"' && src.charAt(j + 1) == '"' && src.charAt(j + 2) == '"') {
                    return j + 3;
                }
                j++;
            }
            return n;
        }
        if (c == '"' || c == '\'') {
            for (int j = i + 1; j < n; j++) {
                char d = src.charAt(j);
                if (d == '\\' && j + 1 < n) { j++; continue; }
                if (d == c) return j + 1;
                if (d == '\n') return j; // 未終端 (通常文字列/文字リテラルは行をまたがない)
            }
            return n;
        }
        return i;
    }

    private static int matchBalance(String src, int open, char openCh, char closeCh) {
        int depth = 1;
        int n = src.length();
        for (int i = open + 1; i < n; i++) {
            int e = skipNonCode(src, i);
            if (e > i) { i = e - 1; continue; }
            char c = src.charAt(i);
            if (c == openCh) depth++;
            else if (c == closeCh) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return n;
    }

    private KotlinLightScanner() {
    }
}
