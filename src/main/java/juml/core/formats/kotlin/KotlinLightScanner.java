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
     * クラスヘッダパターン。グループ 1 = annotations + modifiers (空白区切り),
     * グループ 2 = 種別キーワード, グループ 3 = クラス名。
     *
     * <p>アノテーション引数の {@code (...)} は 1 レベルのネストを許容するように
     * {@code (?:[^()]|\([^()]*\))*} を使う。これにより
     * {@code @Entity(foreignKeys = [ForeignKey(...)])} のような Room の Kotlin
     * スタイルもクラスヘッダの annotation prefix として認識できる。</p>
     */
    private static final Pattern CLASS_HEADER = Pattern.compile(
            "((?:@[A-Za-z_][\\w.]*(?:\\((?:[^()]|\\([^()]*\\))*\\))?\\s*|"
                    + "public\\s+|protected\\s+|private\\s+|internal\\s+|"
                    + "open\\s+|abstract\\s+|final\\s+|sealed\\s+|data\\s+|"
                    + "inner\\s+|companion\\s+|enum\\s+|annotation\\s+)*)"
                    + "(class|interface|object)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    /** プライマリコンストラクタ引数の {@code val/var name: Type}。 */
    private static final Pattern PRIMARY_CTOR_PARAM = Pattern.compile(
            "((?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                    + "(?:private\\s+|protected\\s+|public\\s+|internal\\s+)?"
                    + "(val|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*"
                    + "([A-Za-z_$][\\w.<>?\\[\\]\\s,]*)");
    /** クラス本体内の {@code val/var name: Type}。 */
    private static final Pattern PROPERTY = Pattern.compile(
            "((?:@(?:[A-Za-z]+:)?[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                    + "((?:private\\s+|protected\\s+|public\\s+|internal\\s+"
                    + "|lateinit\\s+|const\\s+|override\\s+)*)"
                    + "(val|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*"
                    + "([A-Za-z_$][\\w.<>?\\[\\]\\s,]*?)(?=\\s*[=\\n{;])");
    /** {@code fun name(params): ReturnType}。 */
    private static final Pattern FUN_DECL = Pattern.compile(
            "((?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                    + "((?:public\\s+|private\\s+|protected\\s+|internal\\s+"
                    + "|open\\s+|abstract\\s+|final\\s+|override\\s+|suspend\\s+|inline\\s+)*)"
                    + "fun\\s+(?:<[^>]+>\\s+)?"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)"
                    + "(?:\\s*:\\s*([A-Za-z_$][\\w.<>?\\[\\]\\s,]*?))?(?=\\s*[={\\n])");

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

        // クラスヘッダごとに本体を切り出す
        Matcher cm = CLASS_HEADER.matcher(source);
        while (cm.find()) {
            String annsAndMods = cm.group(1);
            String kindKw = cm.group(2);
            String name = cm.group(3);
            int headerEnd = cm.end();

            JavaClassInfo info = new JavaClassInfo();
            info.setPackageName(pkg);
            info.setSimpleName(name);
            info.setKind(mapKind(kindKw, annsAndMods));
            info.getImports().addAll(imports);
            extractAnnotations(annsAndMods, info.getAnnotations());

            // プライマリコンストラクタ引数 (class Foo(val x: Int, ...))
            // 次のクラス宣言位置を上限に探索し、本体 {} を持たないクラスが後続クラスの
            // ブレース/括弧を誤って取り込まないようにする。
            int nextHeader = nextClassHeaderStart(source, headerEnd);
            int primaryCtorParen = findNextChar(source, headerEnd, '(');
            int bodyBraceOpen = findNextChar(source, headerEnd, '{');
            if (nextHeader >= 0 && primaryCtorParen >= nextHeader) {
                primaryCtorParen = -1;
            }
            if (nextHeader >= 0 && bodyBraceOpen >= nextHeader) {
                bodyBraceOpen = -1;
            }
            if (primaryCtorParen >= 0
                    && (bodyBraceOpen < 0 || primaryCtorParen < bodyBraceOpen)) {
                int primaryCtorClose = matchParen(source, primaryCtorParen);
                if (primaryCtorClose > primaryCtorParen) {
                    String paramsText = source.substring(primaryCtorParen + 1,
                            primaryCtorClose);
                    extractPrimaryCtorFields(paramsText, info);
                }
            }

            // スーパークラス / インタフェースの取り込み (: A(), B, C)
            int superRegionEnd = bodyBraceOpen >= 0 ? bodyBraceOpen
                    : (nextHeader >= 0 ? nextHeader : source.length());
            extractSupertypes(source, headerEnd, superRegionEnd, info);

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
                    boolean[] codeMask = codeBlockMask(body);
                    extractProperties(body, info, codeMask);
                    extractFunctions(body, info, codeMask);
                }
            }

            out.add(info);
        }
        return out;
    }

    /** {@code from} 以降で次に現れるクラス/インタフェース/object 宣言の開始位置。無ければ -1。 */
    private static int nextClassHeaderStart(String source, int from) {
        Matcher m = CLASS_HEADER.matcher(source);
        return m.find(from) ? m.start() : -1;
    }

    /**
     * {@code [start, end)} 区間からスーパータイプリスト ({@code : A(), B, C}) を取り込む。
     * コンストラクタ呼び出し ({@code A()}) を伴う型をスーパークラス、それ以外をインタフェース
     * とみなす (Kotlin ではスーパークラスのみ {@code ()} を伴う)。{@code <...>} / {@code (...)}
     * のネスト内の {@code :} は無視するため、プライマリコンストラクタの {@code val x: Int} は誤検出しない。
     */
    private static void extractSupertypes(String source, int start, int end,
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
        for (int i = 0; i < list.length(); i++) {
            char c = list.charAt(i);
            if (c == '{' || c == '}') {
                list = list.substring(0, i);
                break;
            }
        }
        for (String raw : splitTopLevelCommas(list)) {
            String e = raw.trim();
            if (e.isEmpty()) {
                continue;
            }
            int paren = e.indexOf('(');
            if (paren >= 0) {
                String sup = e.substring(0, paren).trim();
                if (!sup.isEmpty() && info.getSuperClass() == null) {
                    info.setSuperClass(sup);
                }
            } else {
                // by 委譲 ({@code B by b}) は最初のトークンのみ採用
                String iface = e.split("\\s+", 2)[0].trim();
                if (!iface.isEmpty()) {
                    info.getInterfaces().add(iface);
                }
            }
        }
    }

    /** enum 定数: 先頭の (任意アノテーション付き) 識別子と、続く {@code (...)} 引数。 */
    private static final Pattern ENUM_CONST = Pattern.compile(
            "^\\s*(?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*(\\(.*\\))?", Pattern.DOTALL);

    /**
     * {@code enum class} 本体から定数を取り込む。定数は本体先頭、最初のトップレベル {@code ;}
     * (なければ本体全体) までをカンマ区切りで列挙したもの。{@code EARTH(5.976e+24)} の引数は
     * {@link JavaClassInfo#getEnumConstantArgs()} に括弧付きで対応保持する。
     */
    private static void extractEnumConstants(String body, JavaClassInfo info) {
        int semi = topLevelSemicolon(body);
        String constPart = semi >= 0 ? body.substring(0, semi) : body;
        for (String raw : splitTopLevelCommas(constPart)) {
            String e = raw.trim();
            if (e.isEmpty()) {
                continue;
            }
            Matcher m = ENUM_CONST.matcher(e);
            if (!m.find()) {
                continue;
            }
            info.getEnumConstants().add(m.group(1));
            info.getEnumConstantArgs().add(m.group(2) == null ? "" : m.group(2));
        }
    }

    /** {@code ()} / {@code []} / {@code &#123;&#125;} のネスト外にある最初の {@code ;} の位置。無ければ -1。 */
    private static int topLevelSemicolon(String s) {
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

    /** {@code <>} / {@code ()} / {@code []} のネスト外にある最初の {@code :} の位置。無ければ -1。 */
    private static int topLevelColon(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[') {
                depth++;
            } else if (c == '>' || c == ')' || c == ']') {
                if (depth > 0) {
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

    private static JavaClassInfo.Kind mapKind(String kindKw, String modifiers) {
        if ("interface".equals(kindKw)) {
            return JavaClassInfo.Kind.INTERFACE;
        }
        if ("object".equals(kindKw)) {
            // Kotlin object は事実上シングルトン → CLASS として扱う
            return JavaClassInfo.Kind.CLASS;
        }
        // class: enum / annotation / data class を分類
        if (modifiers != null) {
            if (modifiers.contains("enum")) return JavaClassInfo.Kind.ENUM;
            if (modifiers.contains("annotation")) return JavaClassInfo.Kind.ANNOTATION;
        }
        return JavaClassInfo.Kind.CLASS;
    }

    private static void extractAnnotations(String annsAndMods, List<String> into) {
        if (annsAndMods == null) return;
        // 引数の () は 1 レベルのネストを許容 (Kotlin の Entity(foreignKeys = [ForeignKey(...)]) 等)
        Pattern annPattern = Pattern.compile(
                "@([A-Za-z_][\\w.]*)(\\((?:[^()]|\\([^()]*\\))*\\))?");
        Matcher m = annPattern.matcher(annsAndMods);
        while (m.find()) {
            String full = "@" + m.group(1) + (m.group(2) == null ? "" : m.group(2));
            into.add(full);
        }
    }

    /**
     * プライマリコンストラクタ引数を解析してフィールドとして追加。
     * カンマで分割した後、各パラメータごとに {@code val/var name: Type} を取り出す。
     * 通常のメソッド引数 (val/var なしの単純 {@code name: Type}) はフィールド化しない。
     */
    private static void extractPrimaryCtorFields(String paramsText, JavaClassInfo info) {
        if (paramsText == null) return;
        Pattern perParam = Pattern.compile(
                "^\\s*((?:@(?:[A-Za-z]+:)?[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                        + "(?:private\\s+|protected\\s+|public\\s+|internal\\s+)?"
                        + "(?:val|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*(.+?)"
                        + "(?:\\s*=.*)?\\s*$");
        for (String p : splitTopLevelCommas(paramsText)) {
            Matcher m = perParam.matcher(p);
            if (m.matches()) {
                String anns = m.group(1);
                String name = m.group(2);
                String type = m.group(3).trim();
                JavaFieldInfo f = new JavaFieldInfo();
                f.setName(name);
                f.setType(type);
                f.setVisibility(Visibility.PUBLIC);
                extractAnnotations(anns, f.getAnnotations());
                info.getFields().add(f);
            }
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
    private static void extractProperties(String body, JavaClassInfo info, boolean[] codeMask) {
        Matcher m = PROPERTY.matcher(body);
        while (m.find()) {
            // 関数本体等のコードブロック内のローカル val/var は除外する。
            if (m.start() < codeMask.length && codeMask[m.start()]) {
                continue;
            }
            String anns = m.group(1);
            String mods = m.group(2);
            String name = m.group(4);
            String type = m.group(5).trim();
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(name);
            f.setType(type);
            f.setVisibility(visibilityOf(mods));
            extractAnnotations(anns, f.getAnnotations());
            // const val はコンパイル時定数 (実質 static)。companion object の
            // 列名定数などが Room の列やインスタンスフィールドと混同されないよう static 扱い。
            if (mods != null && mods.matches("(?s).*\\bconst\\b.*")) {
                f.setStatic(true);
            }
            info.getFields().add(f);
        }
    }

    /** クラス本体の {@code fun ...} を解析してメソッドとして追加。 */
    private static void extractFunctions(String body, JavaClassInfo info, boolean[] codeMask) {
        Matcher m = FUN_DECL.matcher(body);
        while (m.find()) {
            // 関数本体等のコードブロック内のローカル fun は除外する。
            if (m.start() < codeMask.length && codeMask[m.start()]) {
                continue;
            }
            String anns = m.group(1);
            String mods = m.group(2);
            String name = m.group(3);
            String paramsText = m.group(4);
            String returnType = m.group(5);
            JavaMethodInfo mth = new JavaMethodInfo();
            mth.setName(name);
            mth.setReturnType(returnType == null ? "Unit" : returnType.trim());
            mth.setVisibility(visibilityOf(mods));
            extractAnnotations(anns, mth.getAnnotations());
            parseParameters(paramsText, mth);
            // メソッド本体内の呼び出しを抽出。ブロック本体か式本体かを判定。
            int afterSig = m.end();
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

    /** {@code name: Type, name2: Type2 = default} を解析してパラメータに追加。 */
    private static void parseParameters(String text, JavaMethodInfo mth) {
        if (text == null || text.trim().isEmpty()) return;
        // ジェネリクスを尊重した split
        List<String> parts = splitTopLevelCommas(text);
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            // "@A name: Type = default" / "name: Type"
            // アノテーションと修飾子を取り除き、name: Type を取る
            Pattern simple = Pattern.compile(
                    "(?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*"
                            + "(?:vararg\\s+|crossinline\\s+|noinline\\s+)?"
                            + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*([^=]+?)\\s*(?:=.*)?$");
            Matcher s = simple.matcher(trimmed);
            if (s.matches()) {
                mth.getParameterNames().add(s.group(1));
                mth.getParameterTypes().add(s.group(2).trim());
            }
        }
    }

    private static List<String> splitTopLevelCommas(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[' || c == '{') depth++;
            else if (c == '>' || c == ')' || c == ']' || c == '}') {
                if (depth > 0) depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
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

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentPart(char c) {
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

    private static int matchParen(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '(') return open;
        return matchBalance(src, open, '(', ')');
    }

    private static int matchBrace(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') return open;
        return matchBalance(src, open, '{', '}');
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
    private static boolean[] codeBlockMask(String body) {
        int n = body.length();
        boolean[] mask = new boolean[n];
        for (int i = 0; i < n; i++) {
            int e = skipNonCode(body, i);
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
                } else if (isIdentPart(pc)) {
                    int ws = p;
                    while (ws >= 0 && isIdentPart(body.charAt(ws))) ws--;
                    if ("init".equals(body.substring(ws + 1, p + 1))) {
                        codeBlock = true;
                    }
                }
            }
            if (codeBlock) {
                int close = matchBrace(body, i);
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
     * {@code src[i]} が「コードでない範囲」の開始なら、その範囲全体を読み飛ばして
     * 「次の」インデックスを返す。開始でなければ {@code i} をそのまま返す。
     *
     * <p>対象: 行コメント {@code //}、ブロックコメント (ネスト可)、通常文字列 {@code "…"}、
     * 生文字列 {@code """…"""} (エスケープなし)、文字リテラル {@code '…'} (エスケープ考慮)。
     * これを各走査ループで使うことで、文字列/コメント/文字リテラル内の {@code {} } {@code "}
     * などをコードのブレース/引用符と取り違えてクラス本体を途中で切ってしまうのを防ぐ。
     * 未終端は末尾 (通常文字列/文字リテラル/行コメントは改行) で打ち切る。</p>
     */
    private static int skipNonCode(String src, int i) {
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
