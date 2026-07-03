// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.java.JavaLexer;
import juml.core.formats.java.JavaToken;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

/**
 * AIDL (Android Interface Definition Language) ソースの簡易パーサ。
 *
 * <p>パッケージ宣言、import 文、{@code interface} 宣言とそのメソッドを抽出して
 * {@link JavaClassInfo} (Kind = {@link JavaClassInfo.Kind#AIDL_INTERFACE}) に変換する。
 * {@code in} / {@code out} / {@code inout} のパラメータ方向子、
 * {@code oneway} メソッド修飾子に対応する。</p>
 */
public final class AidlParser {

    /** AIDL ソースから ClassInfo のリストを抽出する。 */
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

    private AidlParser() {
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
                    packageName = readDottedName();
                    consume(";");
                    continue;
                }
                if (peek().isKw("import")) {
                    skipUntilSemicolon();
                    continue;
                }
                List<String> ann = readAnnotations();
                // parcelable / enum forward decls
                if (peek().isKw("parcelable") || peek().isKw("enum")) {
                    skipUntilSemicolonOrBlock();
                    continue;
                }
                if (peek().isKw("interface")) {
                    parseInterface(ann);
                    continue;
                }
                next();
            }
        }

        private void parseInterface(List<String> annotations) {
            int startLine = peek().line;
            next(); // interface
            String name = "Anonymous";
            if (peek().type == JavaToken.Type.IDENT) {
                name = next().text;
            } else {
                warn(startLine, "AIDL interface name missing");
            }
            JavaClassInfo info = new JavaClassInfo();
            info.setKind(JavaClassInfo.Kind.AIDL_INTERFACE);
            info.setSimpleName(name);
            info.setPackageName(packageName);
            info.getAnnotations().addAll(annotations);
            while (!atEnd() && !peek().is("{")) {
                next();
            }
            if (!peek().is("{")) {
                warn(startLine, "AIDL interface '" + name + "' body '{' not found");
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
                    return;
                }
                if (peek().is(";")) {
                    next();
                    continue;
                }
                List<String> annotations = readAnnotations();
                boolean oneway = false;
                if (peek().isKw("oneway")) {
                    next();
                    oneway = true;
                }
                parseAidlMethod(info, annotations, oneway);
            }
        }

        private void parseAidlMethod(JavaClassInfo cls, List<String> annotations,
                                      boolean oneway) {
            int startPos = peek().start;
            int lastIdentEnd = startPos;
            String methodName = "";
            while (!atEnd() && !peek().is("(")) {
                JavaToken t = peek();
                if (t.is(";") || t.is("}")) {
                    return;
                }
                if (t.type == JavaToken.Type.IDENT) {
                    methodName = t.text;
                    lastIdentEnd = t.start;
                }
                next();
            }
            if (atEnd()) {
                return;
            }
            String returnType = stripAidlAnnotations(src.substring(startPos, lastIdentEnd).trim());

            JavaMethodInfo m = new JavaMethodInfo();
            m.setName(methodName);
            m.setReturnType(returnType);
            m.setVisibility(Visibility.PUBLIC); // AIDL は常に public
            m.setAbstract(true);
            m.getAnnotations().addAll(annotations);
            if (oneway) {
                m.getAnnotations().add("oneway");
            }
            parseParameters(m);
            // 末尾 ;
            while (!atEnd() && !peek().is(";")) {
                next();
            }
            if (peek().is(";")) {
                next();
            }
            cls.getMethods().add(m);
        }

        private void parseParameters(JavaMethodInfo m) {
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
                String cleaned = raw.replaceAll("@\\w+(\\.\\w+)*(\\([^)]*\\))?", " ");
                // 方向修飾子 in/out/inout を除去
                cleaned = cleaned.replaceAll("(^|\\s)(in|out|inout)(\\s|$)", " ").trim();
                String[] sp = cleaned.split("\\s+");
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
                m.getParameterTypes().add(type);
                m.getParameterNames().add(paramName);
            }
        }

        // --- ヘルパ ---

        private static String stripAidlAnnotations(String s) {
            return s.replaceAll("@\\w+(\\.\\w+)*(\\([^)]*\\))?", " ")
                    .replaceAll("\\s+", " ").trim();
        }

        private List<String> readAnnotations() {
            List<String> result = new ArrayList<>();
            while (!atEnd() && peek().is("@")) {
                next();
                StringBuilder name = new StringBuilder();
                while (peek().type == JavaToken.Type.IDENT) {
                    name.append(next().text);
                    if (peek().is(".")) {
                        next();
                        name.append('.');
                    } else {
                        break;
                    }
                }
                String args = "";
                if (peek().is("(")) {
                    int s = peek().start;
                    skipBalanced("(", ")");
                    int e = idx > 0 ? tokens.get(idx - 1).end : s;
                    args = src.substring(s, e);
                }
                result.add(name.toString() + args);
            }
            return result;
        }

        private String readDottedName() {
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
                        return;
                    }
                }
                next();
            }
        }

        private void skipBalanced(String open, String close) {
            if (!peek().is(open)) {
                return;
            }
            next();
            int depth = 1;
            while (!atEnd() && depth > 0) {
                JavaToken t = peek();
                if (t.is(open)) {
                    depth++;
                } else if (t.is(close)) {
                    depth--;
                    if (depth == 0) {
                        next();
                        return;
                    }
                }
                next();
            }
        }
    }
}
