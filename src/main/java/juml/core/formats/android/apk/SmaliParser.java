// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Apktool が逆アセンブルした単一の {@code .smali} ファイルを {@link SmaliClassInfo} に
 * パースする。命令本体 (レジスタ操作・分岐) は読み飛ばし、UML 図に必要な構造ヘッダ
 * ({@code .class} / {@code .super} / {@code .implements} / {@code .field} / {@code .method})
 * のシグネチャだけを抽出する。
 *
 * <p>Smali はテキスト形式なので、文字列処理のみで完結する (外部プロセス・ネットワーク不要)。
 * 各クラスは 1 ファイル 1 宣言という Smali の規約を前提とする。</p>
 */
public final class SmaliParser {

    /** Smali のアクセスフラグキーワード集合。これ以外のトークンは型名等とみなす。 */
    private static final Set<String> ACCESS_FLAGS = Set.of(
            "public", "private", "protected", "static", "final", "abstract",
            "interface", "enum", "annotation", "synthetic", "bridge", "varargs",
            "native", "synchronized", "transient", "volatile", "strictfp",
            "declared-synchronized", "constructor");

    private SmaliParser() {
    }

    /** デフォルト (silent) リスナーでパースする。 */
    public static SmaliClassInfo parse(String content, String sourceName) {
        return parse(content, sourceName, ErrorListener.silent());
    }

    /**
     * Smali テキストをパースする。{@code .class} 行が無い場合は null を返す
     * (空ファイルや非 smali ファイルへの誤適用を弾く)。
     *
     * @param content    {@code .smali} ファイル全文
     * @param sourceName エラー通知に使うファイル識別子 (null 可)
     * @param listener   パース上の注意点の通知先 (null なら silent)
     */
    public static SmaliClassInfo parse(String content, String sourceName,
                                       ErrorListener listener) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }
        ErrorListener log = listener != null ? listener : ErrorListener.silent();
        String[] lines = content.split("\n", -1);
        SmaliClassInfo info = null;
        boolean inAnnotation = false;
        int annotationDepth = 0;
        // 現在パース中のメソッド (本体の invoke-* を収集する対象)。null なら本体外。
        SmaliMethodInfo currentMethod = null;

        for (int idx = 0; idx < lines.length; idx++) {
            String raw = lines[idx];
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            // クラス/フィールド/メソッドに付くアノテーションブロックは丸ごと読み飛ばす。
            if (line.startsWith(".annotation")) {
                inAnnotation = true;
                annotationDepth++;
                continue;
            }
            if (inAnnotation) {
                if (line.startsWith(".end annotation")) {
                    annotationDepth--;
                    if (annotationDepth <= 0) {
                        inAnnotation = false;
                        annotationDepth = 0;
                    }
                }
                continue;
            }

            if (line.startsWith(".class")) {
                info = parseClassLine(line);
            } else if (info == null) {
                // .class より前の有効行は想定外。最初の .class を待つ。
                continue;
            } else if (line.startsWith(".super")) {
                info.setSuperClass(SmaliTypeDescriptor.decodeClassName(operand(line)));
            } else if (line.startsWith(".implements")) {
                info.getInterfaces().add(
                        SmaliTypeDescriptor.decodeClassName(operand(line)));
            } else if (line.startsWith(".source")) {
                info.setSourceFile(unquote(operand(line)));
            } else if (line.startsWith(".field")) {
                SmaliFieldInfo f = parseFieldLine(line);
                if (f != null) {
                    info.getFields().add(f);
                }
            } else if (line.startsWith(".method")) {
                SmaliMethodInfo m = parseMethodLine(line);
                if (m != null) {
                    info.getMethods().add(m);
                }
                currentMethod = m;
            } else if (line.startsWith(".end method")) {
                currentMethod = null;
            } else if (currentMethod != null && line.startsWith("invoke-")) {
                SmaliInvoke inv = parseInvokeLine(line);
                if (inv != null) {
                    currentMethod.getInvokes().add(inv);
                }
            }
        }

        if (info == null) {
            log.onError(sourceName, -1, "no .class declaration found in smali file");
        }
        return info;
    }

    /** {@code .class public final Lcom/Foo;} → SmaliClassInfo(modifiers, className)。 */
    private static SmaliClassInfo parseClassLine(String line) {
        List<String> tokens = tokenize(line, 1);
        List<String> mods = new ArrayList<>();
        String classDesc = null;
        for (String t : tokens) {
            if (ACCESS_FLAGS.contains(t)) {
                mods.add(t);
            } else if (t.startsWith("L")) {
                classDesc = t;
            }
        }
        String fqn = classDesc == null ? "" : SmaliTypeDescriptor.decodeClassName(classDesc);
        SmaliClassInfo info = new SmaliClassInfo(fqn);
        info.getModifiers().addAll(mods);
        return info;
    }

    /** {@code .field private static final TAG:Ljava/lang/String; = "x"} を解析。 */
    private static SmaliFieldInfo parseFieldLine(String line) {
        List<String> tokens = tokenize(line, 1);
        List<String> mods = new ArrayList<>();
        String nameAndType = null;
        for (String t : tokens) {
            if (ACCESS_FLAGS.contains(t)) {
                mods.add(t);
            } else if (nameAndType == null && t.indexOf(':') >= 0) {
                nameAndType = t;
            }
        }
        if (nameAndType == null) {
            return null;
        }
        int colon = nameAndType.indexOf(':');
        String name = nameAndType.substring(0, colon);
        String typeDesc = nameAndType.substring(colon + 1);
        String constant = null;
        int eq = line.indexOf('=');
        if (eq >= 0) {
            constant = line.substring(eq + 1).trim();
        }
        return new SmaliFieldInfo(name, SmaliTypeDescriptor.decode(typeDesc), mods, constant);
    }

    /** {@code .method public foo(II)Z} を解析。 */
    private static SmaliMethodInfo parseMethodLine(String line) {
        List<String> tokens = tokenize(line, 1);
        List<String> mods = new ArrayList<>();
        String sig = null;
        for (String t : tokens) {
            if (ACCESS_FLAGS.contains(t)) {
                mods.add(t);
            } else if (sig == null && t.indexOf('(') >= 0) {
                sig = t;
            }
        }
        if (sig == null) {
            return null;
        }
        int paren = sig.indexOf('(');
        String name = sig.substring(0, paren);
        String desc = sig.substring(paren);
        SmaliTypeDescriptor.Method m = SmaliTypeDescriptor.parseMethodDescriptor(desc);
        return new SmaliMethodInfo(name, m.getParameterTypes(), m.getReturnType(), mods);
    }

    /**
     * {@code invoke-virtual {p0, v0}, Lcom/Foo;->bar(I)V} 形式の呼び出し命令を解析する。
     * 解析できない行 (polymorphic / custom など想定外形式) は null を返す。
     */
    private static SmaliInvoke parseInvokeLine(String line) {
        int arrow = line.indexOf("->");
        if (arrow < 0) {
            return null;
        }
        // kind: "invoke-virtual" / "invoke-virtual/range" → "virtual"
        int sp = line.indexOf(' ');
        String head = sp < 0 ? line : line.substring(0, sp);
        String kind = head.startsWith("invoke-") ? head.substring("invoke-".length()) : head;
        int slash = kind.indexOf('/');
        if (slash >= 0) {
            kind = kind.substring(0, slash);
        }
        // owner: 直前の ',' から arrow までの間にあるクラス記述子 (Lpkg/Cls;)
        int comma = line.lastIndexOf(',', arrow);
        if (comma < 0) {
            return null;
        }
        String ownerDesc = line.substring(comma + 1, arrow).trim();
        if (ownerDesc.isEmpty() || ownerDesc.charAt(0) != 'L') {
            return null; // 配列や不正形式は対象外
        }
        String rest = line.substring(arrow + 2).trim();
        int paren = rest.indexOf('(');
        if (paren < 0) {
            return null;
        }
        String name = rest.substring(0, paren);
        SmaliTypeDescriptor.Method m =
                SmaliTypeDescriptor.parseMethodDescriptor(rest.substring(paren));
        return new SmaliInvoke(SmaliTypeDescriptor.decodeClassName(ownerDesc), name,
                m.getParameterTypes(), m.getReturnType(), kind);
    }

    /** 先頭ディレクティブ ({@code .class} 等) を除いた残りトークンの最初の 1 つを返す。 */
    private static String operand(String line) {
        List<String> tokens = tokenize(line, 1);
        return tokens.isEmpty() ? "" : tokens.get(0);
    }

    /** 空白区切りでトークン化し、先頭 {@code skip} 個 (ディレクティブ) を捨てる。 */
    private static List<String> tokenize(String line, int skip) {
        String[] parts = line.split("\\s+");
        List<String> out = new ArrayList<>();
        for (int i = skip; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                out.add(parts[i]);
            }
        }
        return out;
    }

    /** 前後のダブルクォートを除去する ({@code "Foo.java"} → {@code Foo.java})。 */
    private static String unquote(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /** デバッグ補助: 既知アクセスフラグの一覧 (テスト用)。 */
    static List<String> knownAccessFlags() {
        List<String> l = new ArrayList<>(ACCESS_FLAGS);
        l.sort(String::compareTo);
        return Arrays.asList(l.toArray(new String[0]));
    }
}
