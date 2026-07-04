// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.core.formats.uml.PlantUmlCommentFormatter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * smali のメソッド本体 ({@code invoke-*} 命令) を辿って PlantUML シーケンス図を生成する。
 *
 * <p>「APK の処理 (制御フロー) を UML にしたい」という用途に応える。指定したエントリ
 * メソッド {@code Class.method} を起点に、本体の呼び出しを出現順にメッセージとして描き、
 * 呼び出し先がスコープ内クラスなら深さ制限付きで再帰する (フレームワーク呼び出しは葉として
 * 1 本のメッセージで止める)。再帰スタックでサイクルを防ぎ、メッセージ総数で暴発を防ぐ。</p>
 */
public final class PlantUmlSmaliSequenceDiagram {

    /** 出力オプション。 */
    public static final class Options {
        public int maxDepth = 5;
        public int maxMessages = 300;
        public boolean includeFrameworkCalls = true;
        public boolean includeConstructors;
        public boolean includeLegend = true;
        public String title;
    }

    private final Map<String, SmaliClassInfo> byFqn = new LinkedHashMap<>();
    private final Map<String, String> alias = new LinkedHashMap<>();
    private final StringBuilder body = new StringBuilder();
    private final Deque<String> stack = new ArrayDeque<>();
    private final Options o;
    private int messageCount;

    private PlantUmlSmaliSequenceDiagram(List<SmaliClassInfo> classes, Options opts) {
        this.o = opts != null ? opts : new Options();
        for (SmaliClassInfo c : classes) {
            byFqn.putIfAbsent(c.getClassName(), c);
        }
    }

    /**
     * エントリ {@code Class.method} (Class は FQN でも単純名でも可) からシーケンス図を生成する。
     * 解決できない場合は {@code @startuml} に note を入れた図を返す。
     */
    public static String generate(ApkAnalysis analysis, String entry, Options opts) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        return generate(analysis.getClasses(), entry, opts);
    }

    /** クラス一覧版。 */
    public static String generate(List<SmaliClassInfo> classes, String entry, Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        PlantUmlSmaliSequenceDiagram gen = new PlantUmlSmaliSequenceDiagram(classes, opts);
        return gen.build(entry);
    }

    private String build(String entry) {
        SmaliClassInfo entryClass = resolveClass(entry);
        SmaliMethodInfo entryMethod = resolveMethod(entryClass, entry);
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }
        out.append("skinparam participant {\n");
        out.append("  BackgroundColor<<external>> #F2F2F2\n");
        out.append("  BorderColor<<external>> #999999\n");
        out.append("}\n");

        if (entryClass == null || entryMethod == null) {
            out.append("note as N1\n  could not resolve entry: ")
                    .append(escape(entry == null ? "(null)" : entry))
                    .append("\nend note\n@enduml\n");
            return out.toString();
        }
        aliasFor(entryClass.getClassName()); // エントリを先頭 participant として登録
        traverse(entryClass, entryMethod, 0);

        // participant 宣言 → メッセージ本体の順で出力する。
        for (Map.Entry<String, String> e : alias.entrySet()) {
            SmaliClassInfo c = byFqn.get(e.getKey());
            boolean external = c == null;
            out.append("participant \"")
                    .append(escape(SmaliTypeDescriptor.simpleName(e.getKey())))
                    .append("\" as ").append(e.getValue());
            if (external) {
                out.append(" <<external>>");
            }
            out.append('\n');
        }
        out.append("activate ").append(alias.get(entryClass.getClassName())).append('\n');
        out.append(body);
        out.append("deactivate ").append(alias.get(entryClass.getClassName())).append('\n');
        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private void traverse(SmaliClassInfo caller, SmaliMethodInfo method, int depth) {
        String frame = caller.getClassName() + "#" + method.getName() + "#"
                + method.getParameterTypes().size();
        if (stack.contains(frame)) {
            return; // サイクル防止
        }
        stack.push(frame);
        String callerAlias = alias.get(caller.getClassName());
        for (SmaliInvoke inv : method.getInvokes()) {
            if (messageCount >= o.maxMessages) {
                break;
            }
            if (!o.includeConstructors
                    && ("<init>".equals(inv.getMethodName())
                        || "<clinit>".equals(inv.getMethodName()))) {
                continue;
            }
            SmaliClassInfo target = byFqn.get(inv.getOwnerClass());
            boolean inScope = target != null;
            if (!inScope && !o.includeFrameworkCalls) {
                continue;
            }
            String targetAlias = aliasFor(inv.getOwnerClass());
            body.append(callerAlias).append(" -> ").append(targetAlias)
                    .append(" : ").append(escape(inv.displayLabel())).append('\n');
            messageCount++;
            if (inScope && depth < o.maxDepth) {
                SmaliMethodInfo tm = findMethod(target, inv);
                if (tm != null && !tm.getInvokes().isEmpty()) {
                    body.append("activate ").append(targetAlias).append('\n');
                    traverse(target, tm, depth + 1);
                    body.append(targetAlias).append(" --> ").append(callerAlias).append('\n');
                    body.append("deactivate ").append(targetAlias).append('\n');
                }
            }
        }
        stack.pop();
    }

    /** owner クラスから呼び出しに最も合うメソッドを探す (名前一致 + 引数数優先)。 */
    private static SmaliMethodInfo findMethod(SmaliClassInfo c, SmaliInvoke inv) {
        SmaliMethodInfo byNameOnly = null;
        for (SmaliMethodInfo m : c.getMethods()) {
            if (!m.getName().equals(inv.getMethodName())) {
                continue;
            }
            if (m.getParameterTypes().size() == inv.getParameterTypes().size()) {
                return m;
            }
            if (byNameOnly == null) {
                byNameOnly = m;
            }
        }
        return byNameOnly;
    }

    /** FQN に participant エイリアスを割り当てる (登録順 = 図の participant 並び順)。 */
    private String aliasFor(String fqn) {
        return alias.computeIfAbsent(fqn, k -> "P" + alias.size());
    }

    private SmaliClassInfo resolveClass(String entry) {
        if (entry == null || entry.isEmpty()) {
            return null;
        }
        int dot = entry.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String classPart = entry.substring(0, dot);
        SmaliClassInfo exact = byFqn.get(classPart);
        if (exact != null) {
            return exact;
        }
        // 単純名一致 (最初に見つかったもの)
        for (SmaliClassInfo c : byFqn.values()) {
            if (c.getSimpleName().equals(classPart)) {
                return c;
            }
        }
        return null;
    }

    private static SmaliMethodInfo resolveMethod(SmaliClassInfo c, String entry) {
        if (c == null || entry == null) {
            return null;
        }
        int dot = entry.lastIndexOf('.');
        if (dot < 0 || dot + 1 >= entry.length()) {
            return null;
        }
        String methodName = entry.substring(dot + 1);
        SmaliMethodInfo withBody = null;
        SmaliMethodInfo any = null;
        for (SmaliMethodInfo m : c.getMethods()) {
            if (m.getName().equals(methodName)) {
                if (any == null) {
                    any = m;
                }
                if (withBody == null && !m.getInvokes().isEmpty()) {
                    withBody = m;
                }
            }
        }
        return withBody != null ? withBody : any;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'");
    }

    private void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== APK (smali) シーケンス図 ==\n");
        out.append("A -> B : m()     A の本体が B.m() を invoke\n");
        out.append("<<external>>     スコープ外 (framework 等) の呼び出し先\n");
        out.append("maxDepth=").append(o.maxDepth)
                .append(" / maxMessages=").append(o.maxMessages).append('\n');
        out.append("endlegend\n");
    }
}
