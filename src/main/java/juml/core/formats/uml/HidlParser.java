// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.java.JavaLexer;
import juml.core.formats.java.JavaToken;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

/**
 * HIDL (HAL Interface Definition Language) ソースの簡易パーサ。
 *
 * <p>AOSP の HAL (Hardware Abstraction Layer) で歴史的に使われてきた
 * {@code .hal} ファイルから、パッケージ宣言・import・{@code interface} 宣言と
 * そのメソッドを抽出して {@link JavaClassInfo}
 * (Kind = {@link JavaClassInfo.Kind#AIDL_INTERFACE}) に変換する。</p>
 *
 * <p>HIDL 固有の構文:</p>
 * <ul>
 *   <li>{@code package android.hardware.foo@1.0;} — バージョン付きパッケージ</li>
 *   <li>{@code import android.hardware.bar@1.0;} — バージョン付き import
 *       (末尾の {@code ::types} 形式にも対応)</li>
 *   <li>{@code methodName(params) generates (return_params);} —
 *       戻り値は {@code generates} 句で宣言する</li>
 *   <li>{@code oneway} メソッド (戻り値なし)</li>
 *   <li>{@code interface IFooExtra extends IFoo { ... }}</li>
 *   <li>ネスト宣言 ({@code struct} / {@code enum} / {@code union} /
 *       {@code typedef}) はメソッドではないので本体スキップ</li>
 * </ul>
 *
 * <p>Kind は AIDL と同じ {@link JavaClassInfo.Kind#AIDL_INTERFACE} を採用し、
 * パッケージ名にバージョン suffix ({@code @1.0}) を保持することで HIDL を
 * 区別できるようにしている。これにより既存の AIDL 用クラス図・シーケンス図
 * 描画ロジックがそのまま HIDL にも適用される。</p>
 */
public final class HidlParser {

    /** HIDL ソースから ClassInfo のリストを抽出する。 */
    public static List<JavaClassInfo> parse(String source) {
        return parse(source, null);
    }

    /** エラーリスナー付き。 */
    public static List<JavaClassInfo> parse(String source, ErrorListener listener) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        List<JavaToken> tokens = new JavaLexer(source).tokenize();
        Impl impl = new Impl(tokens, source,
                listener != null ? listener : ErrorListener.silent());
        impl.parseFile();
        return impl.results;
    }

    private HidlParser() {
    }

    private static final class Impl {
        private final List<JavaToken> tokens;
        private final String src;
        private final ErrorListener listener;
        private final List<JavaClassInfo> results = new ArrayList<>();
        private String packageName = "";
        private int idx;

        Impl(List<JavaToken> tokens, String src, ErrorListener listener) {
            this.tokens = tokens;
            this.src = src;
            this.listener = listener;
        }

        private void warn(int line, String msg) {
            listener.onError(juml.util.ErrorCode.PRJ_006, null, line, msg);
        }

        private JavaToken peek() {
            return tokens.get(idx);
        }

        private JavaToken peek(int n) {
            int i = idx + n;
            if (i >= tokens.size()) {
                i = tokens.size() - 1;
            }
            return tokens.get(i);
        }

        private JavaToken next() {
            JavaToken t = tokens.get(idx);
            if (t.type != JavaToken.Type.EOF) {
                idx++;
            }
            return t;
        }

        private boolean atEnd() {
            return peek().type == JavaToken.Type.EOF;
        }

        void parseFile() {
            while (!atEnd()) {
                if (peek().isKw("package")) {
                    next();
                    packageName = readVersionedDottedName();
                    consume(";");
                    continue;
                }
                if (peek().isKw("import")) {
                    skipUntilSemicolon();
                    continue;
                }
                // ファイルレベルの typedef/struct/enum/union 宣言は body をスキップ
                if (peek().isKw("typedef") || peek().isKw("struct")
                        || peek().isKw("enum") || peek().isKw("union")) {
                    skipUntilSemicolonOrBlock();
                    continue;
                }
                if (peek().isKw("interface")) {
                    parseInterface();
                    continue;
                }
                next();
            }
        }

        private void parseInterface() {
            int startLine = peek().line;
            next(); // interface
            String name = "Anonymous";
            if (peek().type == JavaToken.Type.IDENT) {
                name = next().text;
            } else {
                warn(startLine, "HIDL interface name missing");
            }
            JavaClassInfo info = new JavaClassInfo();
            info.setKind(JavaClassInfo.Kind.AIDL_INTERFACE);
            info.setSimpleName(name);
            info.setPackageName(packageName);
            // extends で親 interface (HIDL は単一継承)
            if (peek().isKw("extends")) {
                next();
                String parent = readVersionedDottedName();
                if (!parent.isEmpty()) {
                    info.setSuperClass(parent);
                }
            }
            // body 開始 '{' まで飛ばす
            while (!atEnd() && !peek().is("{")) {
                next();
            }
            if (!peek().is("{")) {
                warn(startLine, "HIDL interface '" + name + "' body '{' not found");
                results.add(info);
                return;
            }
            next();
            parseInterfaceBody(info);
            results.add(info);
        }

        private void parseInterfaceBody(JavaClassInfo info) {
            while (!atEnd()) {
                if (peek().is("}")) {
                    next();
                    // 直後に ';' が続く場合 (HIDL の interface 宣言終端) を吸収
                    if (peek().is(";")) {
                        next();
                    }
                    return;
                }
                if (peek().is(";")) {
                    next();
                    continue;
                }
                // ネスト宣言 (struct / enum / union / typedef) は本体をまるごとスキップ
                if (peek().isKw("struct") || peek().isKw("enum")
                        || peek().isKw("union") || peek().isKw("typedef")) {
                    skipUntilSemicolonOrBlock();
                    continue;
                }
                boolean oneway = false;
                if (peek().isKw("oneway")) {
                    next();
                    oneway = true;
                }
                parseHidlMethod(info, oneway);
            }
        }

        /**
         * {@code methodName(params) generates (return_params);} もしくは
         * {@code oneway methodName(params);} の 1 メソッドを読む。
         */
        private void parseHidlMethod(JavaClassInfo cls, boolean oneway) {
            // 戻り値型は AIDL と違って必ず空 (generates 句で別途宣言される)
            // メソッド名は最初の IDENT
            String methodName = "";
            while (!atEnd() && !peek().is("(") && !peek().is(";")
                    && !peek().is("}")) {
                if (peek().type == JavaToken.Type.IDENT) {
                    methodName = peek().text;
                }
                next();
            }
            if (atEnd() || peek().is(";") || peek().is("}") || methodName.isEmpty()) {
                if (peek().is(";")) {
                    next();
                }
                return;
            }
            JavaMethodInfo m = new JavaMethodInfo();
            m.setName(methodName);
            m.setVisibility(Visibility.PUBLIC); // HIDL は常に public
            m.setAbstract(true);
            if (oneway) {
                m.getAnnotations().add("oneway");
            }
            // (input params)
            parseParameters(m, false);
            // generates 句があれば戻り値型/追加パラメータを読む
            if (peek().isKw("generates")) {
                next();
                parseGenerates(m);
            }
            // 末尾 ;
            while (!atEnd() && !peek().is(";") && !peek().is("}")) {
                next();
            }
            if (peek().is(";")) {
                next();
            }
            cls.getMethods().add(m);
        }

        /** {@code (type name, type name, ...)} を消費し、{@code m} の parameter*** に追加。 */
        private void parseParameters(JavaMethodInfo m, boolean asReturn) {
            if (!peek().is("(")) {
                return;
            }
            next();
            int parenDepth = 1;
            int angleDepth = 0;
            int paramStart = peek().start;
            int paramEnd = paramStart;
            List<int[]> ranges = new ArrayList<>();
            while (!atEnd() && parenDepth > 0) {
                JavaToken t = peek();
                if (t.is("(") || t.is("[")) {
                    parenDepth++;
                } else if (t.is(")") || t.is("]")) {
                    parenDepth--;
                    if (parenDepth == 0) {
                        if (paramEnd > paramStart) {
                            ranges.add(new int[]{paramStart, paramEnd});
                        }
                        next();
                        break;
                    }
                } else if (t.is("<")) {
                    angleDepth++;
                } else if (t.is(">")) {
                    angleDepth = Math.max(0, angleDepth - 1);
                } else if (t.is(">>") || t.is(">>>")) {
                    angleDepth = Math.max(0, angleDepth - t.text.length());
                } else if (t.is(",") && parenDepth == 1 && angleDepth == 0) {
                    ranges.add(new int[]{paramStart, paramEnd});
                    next();
                    paramStart = peek().start;
                    paramEnd = paramStart;
                    continue;
                }
                paramEnd = t.end;
                next();
            }
            for (int[] r : ranges) {
                String raw = src.substring(r[0], r[1]).trim();
                if (raw.isEmpty()) {
                    continue;
                }
                String[] sp = raw.split("\\s+");
                if (sp.length == 0 || sp[0].isEmpty()) {
                    continue;
                }
                String paramName;
                String type;
                if (sp.length == 1) {
                    type = sp[0];
                    paramName = "";
                } else {
                    paramName = sp[sp.length - 1];
                    StringBuilder tb = new StringBuilder();
                    for (int i = 0; i < sp.length - 1; i++) {
                        if (tb.length() > 0) {
                            tb.append(' ');
                        }
                        tb.append(sp[i]);
                    }
                    type = tb.toString();
                }
                if (asReturn) {
                    // generates 句の出力は returnType に最初の 1 つを採用、
                    // 2 つ目以降は無視 (HIDL 多戻り値を 1 値に圧縮)
                    if (m.getReturnType() == null || m.getReturnType().isEmpty()) {
                        m.setReturnType(type);
                    }
                } else {
                    m.getParameterTypes().add(type);
                    m.getParameterNames().add(paramName);
                }
            }
        }

        /** {@code generates (type name)} の出力パラメータを処理。 */
        private void parseGenerates(JavaMethodInfo m) {
            parseParameters(m, true);
        }

        // --- ヘルパ ---

        /**
         * HIDL のバージョン付きパッケージ名 ({@code android.hardware.foo@1.0} や
         * {@code android.hardware.foo@1.0::types}) を読み取る。
         */
        private String readVersionedDottedName() {
            StringBuilder sb = new StringBuilder();
            while (!atEnd() && peek().type == JavaToken.Type.IDENT) {
                sb.append(next().text);
                if (peek().is(".")) {
                    next();
                    sb.append('.');
                } else {
                    break;
                }
            }
            // 末尾の @<version> を取り込む。JavaLexer は @ を PUNCT、
            // 1.0 を NUMBER として返す。
            if (peek().is("@") && peek(1).type == JavaToken.Type.NUMBER) {
                next(); // @
                sb.append('@').append(next().text);
            }
            // 末尾の ::types のような segment qualifier
            if (peek().is("::") && peek(1).type == JavaToken.Type.IDENT) {
                next(); // ::
                sb.append("::").append(next().text);
            }
            return sb.toString();
        }

        private void consume(String s) {
            if (peek().is(s)) {
                next();
            }
        }

        private void skipUntilSemicolon() {
            while (!atEnd() && !peek().is(";")) {
                next();
            }
            if (peek().is(";")) {
                next();
            }
        }

        private void skipUntilSemicolonOrBlock() {
            int depth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (depth == 0 && t.is(";")) {
                    next();
                    return;
                }
                if (t.is("{")) {
                    depth++;
                } else if (t.is("}")) {
                    depth--;
                    if (depth == 0) {
                        next();
                        // 閉じ '}' の直後に終端 ';' が来るケース (struct Foo { ... };)
                        if (peek().is(";")) {
                            next();
                        }
                        return;
                    }
                }
                next();
            }
        }
    }
}
