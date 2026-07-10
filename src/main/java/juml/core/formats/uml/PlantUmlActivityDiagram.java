// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * クラス情報から 1 メソッド本体のアクティビティ図 (PlantUML 新構文) を生成する。
 *
 * <p>{@link PlantUmlSequenceDiagram} がクラス間の呼び出し関係を表現するのに対し、
 * 本クラスは選択した 1 メソッド内の制御フロー (if / while / for / switch / try /
 * return / throw) をフローチャートとして可視化する。
 * シーケンス図と組み合わせて「クラス間の関係はシーケンス図、関数の処理はアクティビティ図」
 * という確認フローを実現する。</p>
 *
 * <p>呼び出し先メソッドの展開は行わない (起点メソッド内のみ)。メソッド呼び出しは
 * {@code :receiver.method();} のアクションノードとして 1 行で表現する。</p>
 */
public final class PlantUmlActivityDiagram {

    /** 出力オプション。 */
    public static class Options {
        /** ダイアグラムのタイトル。null/空ならクラス名.メソッド名を使用。 */
        public String title;
        /** 凡例ブロックを末尾に追加する。 */
        public boolean includeLegend = true;
        /** メソッド JavaDoc / 直前コメントを冒頭に note として出力する。 */
        public boolean showComments = true;
        /** コメント・アクションラベルの 1 行最大文字数。0 以下は無制限 (全文表示、既定)。 */
        public int commentMaxLength = 0;
        /** コメント色 (PlantUML の {@code <color:#RRGGBB>} 値)。null/空で色付け無効。 */
        public String commentColor = "#008800";
        /** ラムダ/匿名クラスのコールバック本体を partition ブロックに展開する。 */
        public boolean expandInlineCallbacks = true;
        /** ローカル変数宣言をアクションノードとして表示する。 */
        public boolean showLocalVars = true;
        /** 代入・インクリメント文 ({@code x = ...;} / {@code i++;}) をアクションノードとして表示する。 */
        public boolean showAssignments = true;
        /** メソッド本体内のインラインコメントを note として表示する。 */
        public boolean showInlineComments = true;
    }

    /** {@link #listCandidates(List)} の戻り値要素。 */
    public static class Candidate {
        public final String className;
        public final String methodName;
        public final int statementCount;
        public final Visibility visibility;

        public Candidate(String className, String methodName, int statementCount,
                          Visibility visibility) {
            this.className = className;
            this.methodName = methodName;
            this.statementCount = statementCount;
            this.visibility = visibility;
        }

        public String getEntry() {
            return className + "." + methodName;
        }

        @Override
        public String toString() {
            return getEntry();
        }
    }

    /** クラス・メソッドを指定して 1 本のアクティビティ図を生成する。 */
    public static String generate(List<JavaClassInfo> classes,
                                   String entryClass,
                                   String entryMethod,
                                   Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        if (entryClass == null || entryMethod == null) {
            throw new IllegalArgumentException("entryClass/entryMethod is null");
        }
        Options o = opts != null ? opts : new Options();
        JavaClassInfo cls = findClass(classes, entryClass);
        if (cls == null) {
            return emptyDiagram(o, "Class not found: " + entryClass);
        }
        JavaMethodInfo method = findMethod(cls, entryMethod);
        if (method == null) {
            return emptyDiagram(o, "Method not found: " + entryClass + "." + entryMethod);
        }

        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        } else {
            // クラス名・メソッド名に < 等が含まれる場合 (合成メソッド等) をエスケープする
            // (シーケンス図の同箇所と対称。escapeLabel = 空白畳み + チルダエスケープ)
            out.append("title ")
                    .append(PlantUmlCommentFormatter.escapeLabel(
                            cls.getSimpleName() + "." + method.getName()))
                    .append('\n');
        }
        // メソッド JavaDoc を冒頭の note として出力
        if (o.showComments) {
            emitMethodComment(out, method, o);
        }
        out.append("start\n");
        // メソッドシグネチャ（パラメータ・戻り値型）を note として表示
        if (o.showComments && !method.isConstructor()) {
            emitMethodSignature(out, method, o);
        }
        boolean ended = walkStatements(method.getStatements(), out, "", o);
        if (!ended) {
            out.append("stop\n");
        }
        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * アクティビティ図の起点候補を列挙する。本体に文を 1 つ以上含むメソッドを
     * 優先 (statementCount 降順、クラス名昇順)。
     */
    public static List<Candidate> listCandidates(List<JavaClassInfo> classes) {
        List<Candidate> list = new ArrayList<>();
        if (classes == null) {
            return list;
        }
        for (JavaClassInfo c : classes) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.isAbstract()) {
                    continue;
                }
                list.add(new Candidate(c.getSimpleName(), m.getName(),
                        m.getStatements().size(), m.getVisibility()));
            }
        }
        list.sort((a, b) -> {
            if (a.statementCount != b.statementCount) {
                return Integer.compare(b.statementCount, a.statementCount);
            }
            int n = a.className.compareTo(b.className);
            if (n != 0) {
                return n;
            }
            return a.methodName.compareTo(b.methodName);
        });
        return list;
    }

    /**
     * 文ツリーを走査して PlantUML アクティビティ図構文を emit する。
     *
     * @return 直近の文が flow を終端させたかどうか (return/throw 直後)。
     *         true なら呼び出し元は自動 {@code stop} を追加しない。
     */
    private static boolean walkStatements(List<JavaMethodInfo.Statement> stmts,
                                           StringBuilder out, String indent, Options opts) {
        boolean ended = false;
        for (JavaMethodInfo.Statement s : stmts) {
            ended = false;
            if (s instanceof JavaMethodInfo.Call) {
                emitCall((JavaMethodInfo.Call) s, out, indent, opts);
            } else if (s instanceof JavaMethodInfo.Return) {
                ended = emitReturn((JavaMethodInfo.Return) s, out, indent, opts);
            } else if (s instanceof JavaMethodInfo.Throw) {
                ended = emitThrow((JavaMethodInfo.Throw) s, out, indent, opts);
            } else if (s instanceof JavaMethodInfo.Break) {
                emitBreak((JavaMethodInfo.Break) s, out, indent);
            } else if (s instanceof JavaMethodInfo.Continue) {
                emitContinue((JavaMethodInfo.Continue) s, out, indent);
            } else if (s instanceof JavaMethodInfo.Yield) {
                emitYield((JavaMethodInfo.Yield) s, out, indent, opts);
            } else if (s instanceof JavaMethodInfo.Block) {
                ended = emitBlock((JavaMethodInfo.Block) s, out, indent, opts);
            } else if (s instanceof JavaMethodInfo.LocalVar) {
                if (opts.showLocalVars) {
                    emitLocalVar((JavaMethodInfo.LocalVar) s, out, indent, opts);
                }
            } else if (s instanceof JavaMethodInfo.Assignment) {
                if (opts.showAssignments) {
                    emitAssignment((JavaMethodInfo.Assignment) s, out, indent, opts);
                }
            } else if (s instanceof JavaMethodInfo.InlineComment) {
                if (opts.showInlineComments) {
                    emitInlineComment((JavaMethodInfo.InlineComment) s, out, indent, opts);
                }
            }
        }
        return ended;
    }

    private static void emitCall(JavaMethodInfo.Call call, StringBuilder out,
                                  String indent, Options opts) {
        String rcv = call.getReceiver();
        String name = call.getMethodName();
        String text = (rcv == null || rcv.isEmpty())
                ? name + "()"
                : rcv + "." + name + "()";
        out.append(indent).append(':').append(escapeAction(text, opts.commentMaxLength))
                .append(";\n");

        if (opts.expandInlineCallbacks && !call.getInlineMethods().isEmpty()) {
            for (JavaMethodInfo inline : call.getInlineMethods()) {
                // 汎用 SAM 名 (<inline>/accept/test 等) の場合は呼び出し元メソッド名をラベルに使う
                String samLabel = isGenericSamName(inline.getName()) ? name : inline.getName();
                String partLabel = name + " → " + samLabel + "()";
                String inner = indent + "  ";
                out.append(indent).append("partition \"")
                        .append(escapeQuoted(partLabel)).append("\" {\n");
                if (inline.getStatements().isEmpty()) {
                    out.append(inner).append(':')
                            .append(escapeAction(samLabel + "()", opts.commentMaxLength))
                            .append(";\n");
                } else {
                    walkStatements(inline.getStatements(), out, inner, opts);
                }
                out.append(indent).append("}\n");
            }
        }
    }

    private static boolean isGenericSamName(String name) {
        if (name == null) {
            return true;
        }
        switch (name) {
            case "<inline>": case "accept": case "test": case "apply":
            case "get": case "getAsInt": case "getAsLong": case "getAsDouble":
                return true;
            default:
                return false;
        }
    }

    private static void emitLocalVar(JavaMethodInfo.LocalVar v, StringBuilder out,
                                      String indent, Options opts) {
        String type = v.getType();
        String name = v.getVarName();
        String init = v.getInitExpr();
        String text;
        if (init == null || init.isEmpty()) {
            text = type + " " + name;
        } else {
            text = type + " " + name + " = " + init;
        }
        out.append(indent).append(':').append(escapeAction(text, opts.commentMaxLength))
                .append(";\n");
        // ラムダ/匿名クラス付きの変数宣言はコールバックブロックを展開
        if (opts.expandInlineCallbacks && !v.getInlineMethods().isEmpty()) {
            for (JavaMethodInfo inline : v.getInlineMethods()) {
                String samLabel = isGenericSamName(inline.getName()) ? name : inline.getName();
                String partLabel = name + " → " + samLabel + "()";
                String inner = indent + "  ";
                out.append(indent).append("partition \"")
                        .append(escapeQuoted(partLabel)).append("\" {\n");
                if (inline.getStatements().isEmpty()) {
                    out.append(inner).append(':')
                            .append(escapeAction(samLabel + "()", opts.commentMaxLength))
                            .append(";\n");
                } else {
                    walkStatements(inline.getStatements(), out, inner, opts);
                }
                out.append(indent).append("}\n");
            }
        }
    }

    private static void emitAssignment(JavaMethodInfo.Assignment a, StringBuilder out,
                                        String indent, Options opts) {
        String text = a.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        out.append(indent).append(':').append(escapeAction(text, opts.commentMaxLength))
                .append(";\n");
    }

    private static void emitInlineComment(JavaMethodInfo.InlineComment comment, StringBuilder out,
                                           String indent, Options opts) {
        String text = comment.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        String oneLine = noteText(text, opts.commentMaxLength);
        if (oneLine.isEmpty()) {
            return;
        }
        if (opts.commentColor != null && !opts.commentColor.isEmpty()) {
            out.append(indent).append("note right: <color:")
                    .append(opts.commentColor).append('>')
                    .append(oneLine).append("</color>\n");
        } else {
            out.append(indent).append("note right: ").append(oneLine).append('\n');
        }
    }

    private static boolean emitReturn(JavaMethodInfo.Return r, StringBuilder out,
                                       String indent, Options opts) {
        String expr = r.getExpression();
        if (expr == null || expr.isEmpty()) {
            out.append(indent).append(":return;\n");
        } else {
            out.append(indent).append(":return ")
                    .append(escapeAction(expr, opts.commentMaxLength)).append(";\n");
        }
        out.append(indent).append("stop\n");
        return true;
    }

    private static void emitYield(JavaMethodInfo.Yield y, StringBuilder out,
                                   String indent, Options opts) {
        String expr = y.getExpression();
        if (expr == null || expr.isEmpty()) {
            out.append(indent).append(":yield;\n");
        } else {
            out.append(indent).append(":yield ")
                    .append(escapeAction(expr, opts.commentMaxLength)).append(";\n");
        }
    }

    private static boolean emitThrow(JavaMethodInfo.Throw t, StringBuilder out,
                                      String indent, Options opts) {
        String expr = t.getExpression();
        if (expr == null || expr.isEmpty()) {
            out.append(indent).append(":throw;\n");
        } else {
            out.append(indent).append(":throw ")
                    .append(escapeAction(expr, opts.commentMaxLength)).append(";\n");
        }
        out.append(indent).append("kill\n");
        return true;
    }

    private static void emitBreak(JavaMethodInfo.Break b, StringBuilder out, String indent) {
        String label = b.getLabel();
        String text = (label == null || label.isEmpty()) ? "break" : "break " + label;
        out.append(indent).append("note right: ").append(text).append('\n');
    }

    private static void emitContinue(JavaMethodInfo.Continue c, StringBuilder out, String indent) {
        String label = c.getLabel();
        String text = (label == null || label.isEmpty()) ? "continue" : "continue " + label;
        out.append(indent).append("note right: ").append(text).append('\n');
    }

    private static boolean emitBlock(JavaMethodInfo.Block block, StringBuilder out,
                                      String indent, Options opts) {
        List<JavaMethodInfo.Branch> bs = block.getBranches();
        if (bs.isEmpty()) {
            return false;
        }
        String inner = indent + "  ";
        switch (block.getKind()) {
            case IF:
                return emitIf(bs, out, indent, inner, opts);
            case WHILE:
                return emitWhile(bs.get(0), out, indent, inner, opts);
            case FOR:
                return emitFor(bs.get(0), out, indent, inner, opts);
            case DO_WHILE:
                return emitDoWhile(bs.get(0), out, indent, inner, opts);
            case SWITCH:
                return emitSwitch(bs, out, indent, inner, opts);
            case TRY:
                return emitTry(bs, out, indent, inner, opts);
            case SYNCHRONIZED:
                return emitSynchronized(bs.get(0), out, indent, inner, opts);
            default:
                return false;
        }
    }

    private static boolean emitIf(List<JavaMethodInfo.Branch> bs, StringBuilder out,
                                   String indent, String inner, Options opts) {
        JavaMethodInfo.Branch first = bs.get(0);
        out.append(indent).append("if (").append(escapeCondition(first.getLabel()))
                .append(") then (yes)\n");
        walkStatements(first.getBody(), out, inner, opts);
        for (int i = 1; i < bs.size(); i++) {
            JavaMethodInfo.Branch b = bs.get(i);
            if ("else if".equals(b.getType())) {
                out.append(indent).append("elseif (").append(escapeCondition(b.getLabel()))
                        .append(") then (yes)\n");
                walkStatements(b.getBody(), out, inner, opts);
            } else {
                out.append(indent).append("else (no)\n");
                walkStatements(b.getBody(), out, inner, opts);
            }
        }
        out.append(indent).append("endif\n");
        return false;
    }

    private static boolean emitWhile(JavaMethodInfo.Branch br, StringBuilder out,
                                      String indent, String inner, Options opts) {
        out.append(indent).append("while (").append(escapeCondition(br.getLabel()))
                .append(") is (true)\n");
        walkStatements(br.getBody(), out, inner, opts);
        out.append(indent).append("endwhile (false)\n");
        return false;
    }

    private static boolean emitFor(JavaMethodInfo.Branch br, StringBuilder out,
                                    String indent, String inner, Options opts) {
        out.append(indent).append("while (for: ").append(escapeCondition(br.getLabel()))
                .append(") is (loop)\n");
        walkStatements(br.getBody(), out, inner, opts);
        out.append(indent).append("endwhile (done)\n");
        return false;
    }

    private static boolean emitDoWhile(JavaMethodInfo.Branch br, StringBuilder out,
                                        String indent, String inner, Options opts) {
        out.append(indent).append("repeat\n");
        walkStatements(br.getBody(), out, inner, opts);
        String cond = br.getLabel();
        if (cond == null || cond.isEmpty()) {
            out.append(indent).append("repeat while (continue?) is (yes) not (no)\n");
        } else {
            out.append(indent).append("repeat while (").append(escapeCondition(cond))
                    .append(") is (yes) not (no)\n");
        }
        return false;
    }

    private static boolean emitSwitch(List<JavaMethodInfo.Branch> bs, StringBuilder out,
                                       String indent, String inner, Options opts) {
        // bs[0] = switch header (kind="switch", label=condition expression)、残りが case/default
        JavaMethodInfo.Branch head = bs.get(0);
        if (bs.size() <= 1) {
            return false;
        }
        out.append(indent).append("switch (").append(escapeCondition(head.getLabel()))
                .append(")\n");
        for (int i = 1; i < bs.size(); i++) {
            JavaMethodInfo.Branch b = bs.get(i);
            String label;
            if ("default".equals(b.getType())) {
                label = "default";
            } else {
                String lbl = b.getLabel();
                label = (lbl == null || lbl.isEmpty()) ? "case" : escapeCondition(lbl);
            }
            out.append(indent).append("case (").append(label).append(")\n");
            walkStatements(b.getBody(), out, inner, opts);
        }
        out.append(indent).append("endswitch\n");
        return false;
    }

    private static boolean emitTry(List<JavaMethodInfo.Branch> bs, StringBuilder out,
                                    String indent, String inner, Options opts) {
        for (JavaMethodInfo.Branch b : bs) {
            String name;
            if ("try".equals(b.getType())) {
                name = "try";
            } else if ("catch".equals(b.getType())) {
                String lbl = b.getLabel();
                name = (lbl == null || lbl.isEmpty()) ? "catch" : "catch (" + lbl + ")";
            } else {
                name = "finally";
            }
            out.append(indent).append("partition \"")
                    .append(escapeQuoted(name)).append("\" {\n");
            walkStatements(b.getBody(), out, inner, opts);
            out.append(indent).append("}\n");
        }
        return false;
    }

    private static boolean emitSynchronized(JavaMethodInfo.Branch br, StringBuilder out,
                                             String indent, String inner, Options opts) {
        String lock = br.getLabel();
        String header = (lock == null || lock.isEmpty())
                ? "synchronized" : "synchronized(" + lock + ")";
        out.append(indent).append("partition \"")
                .append(escapeQuoted(header)).append("\" {\n");
        walkStatements(br.getBody(), out, inner, opts);
        out.append(indent).append("}\n");
        return false;
    }

    private static void emitMethodComment(StringBuilder out, JavaMethodInfo m, Options o) {
        String comment = m.getComment();
        if (comment == null || comment.isEmpty()) {
            return;
        }
        // note ブロックは複数行を扱えるため、JavaDoc は 1 行目に限らず全文を出す。
        // (firstLine で 1 行に切ると折り返された説明文が途中で欠け「コメントが
        // 省略される」ため。@param 等の構造化タグは既に cleanText で除去済み。)
        List<String> lines = new ArrayList<>();
        for (String raw : comment.split("\n", -1)) {
            // コメント本文の @startuml/@enduml やタグ開始 (<) をそのまま note へ書くと
            // PlantUML が構文エラーになる/テキストが欠落するため、必ず無害化して出す。
            String text = noteText(raw, o.commentMaxLength);
            if (!text.isEmpty()) {
                lines.add(text);
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        out.append("note\n");
        for (String text : lines) {
            if (o.commentColor != null && !o.commentColor.isEmpty()) {
                out.append("<color:").append(o.commentColor).append('>')
                        .append(text).append("</color>");
            } else {
                out.append(text);
            }
            out.append('\n');
        }
        out.append("end note\n");
    }

    private static void emitMethodSignature(StringBuilder out, JavaMethodInfo m, Options o) {
        List<String> types = m.getParameterTypes();
        List<String> names = m.getParameterNames();
        String returnType = m.getReturnType();
        boolean hasParams = !types.isEmpty();
        boolean hasReturnType = returnType != null && !returnType.isEmpty()
                && !"void".equals(returnType);
        if (!hasParams && !hasReturnType) {
            return;
        }
        out.append("note\n");
        if (hasParams) {
            StringBuilder params = new StringBuilder("入力: ");
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) {
                    params.append(", ");
                }
                params.append(types.get(i));
                if (i < names.size() && !names.get(i).isEmpty()) {
                    params.append(' ').append(names.get(i));
                }
            }
            // ジェネリクス型 (List<String> 等) の < が creole タグと誤認されて
            // テキストが欠落しないよう、note 本文として無害化する。
            String paramLine = noteText(params.toString(), o.commentMaxLength);
            if (o.commentColor != null && !o.commentColor.isEmpty()) {
                out.append("<color:").append(o.commentColor).append('>')
                        .append(paramLine).append("</color>");
            } else {
                out.append(paramLine);
            }
            out.append('\n');
        }
        if (hasReturnType) {
            String retLine = noteText("戻り値: " + returnType, o.commentMaxLength);
            if (o.commentColor != null && !o.commentColor.isEmpty()) {
                out.append("<color:").append(o.commentColor).append('>')
                        .append(retLine).append("</color>");
            } else {
                out.append(retLine);
            }
            out.append('\n');
        }
        out.append("end note\n");
    }

    private static void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== アクティビティ図 ==\n");
        out.append(":action;           アクション (メソッド呼び出し / return / throw)\n");
        out.append(":Type var = expr;  ローカル変数宣言\n");
        out.append("note               インラインコメント / メソッドシグネチャ / JavaDoc\n");
        out.append("if/elseif/else  分岐 (Java の if-else)\n");
        out.append("while/endwhile  ループ (while / for)\n");
        out.append("repeat/repeat while  do-while ループ\n");
        out.append("switch/case   多分岐\n");
        out.append("partition     try/catch/finally / synchronized / コールバック展開ブロック\n");
        out.append("stop          正常終端 (return)\n");
        out.append("kill (x印)    例外送出 (throw)\n");
        out.append("endlegend\n");
    }

    private static JavaClassInfo findClass(List<JavaClassInfo> classes, String name) {
        for (JavaClassInfo c : classes) {
            if (c.getSimpleName().equals(name) || c.getQualifiedName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static JavaMethodInfo findMethod(JavaClassInfo cls, String name) {
        for (JavaMethodInfo m : cls.getMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    /**
     * action 用ラベル: 改行を畳み、{@code ;} とタグ開始をエスケープする。
     * {@code maxLen > 0} のときのみ長さを制限する (既定は無制限 = 全文表示)。
     */
    private static String escapeAction(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String trimmed = s.replaceAll("\\s+", " ").trim();
        trimmed = truncate(trimmed, maxLen);
        // PlantUML の :action; 構文では `;` が終端記号なのでエスケープ
        trimmed = trimmed.replace(";", "\\;");
        // <b> 等の creole/HTML タグと誤認されるとテキストが欠落するためエスケープ
        return PlantUmlCommentFormatter.escapeText(trimmed);
    }

    /**
     * note 本文 1 行分の無害化。空白を畳んで切り詰め、@startuml/@enduml の分断・
     * 制御文字除去・タグ開始 {@code <} のエスケープ ({@code escapeText}) を適用する。
     * {@code <color:...>} ラッパはこの後から付けるため影響を受けない。
     */
    private static String noteText(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String oneLine = truncate(s.replaceAll("\\s+", " ").trim(), maxLen);
        // 本文が "end note" だけになった場合の終端注入も防ぐ
        return PlantUmlCommentFormatter.sanitizeNoteLine(
                PlantUmlCommentFormatter.escapeText(oneLine));
    }

    /** {@code if (cond)} のような条件部に使うラベル。改行を畳み、タグ開始をエスケープ。 */
    private static String escapeCondition(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String trimmed = s.replaceAll("\\s+", " ").trim();
        return PlantUmlCommentFormatter.escapeText(trimmed);
    }

    /** partition 等の {@code "..."} 内に置く文字列。引用符とタグ開始をエスケープ。 */
    private static String escapeQuoted(String s) {
        if (s == null) {
            return "";
        }
        // partition "..." の名前中の ASCII " は PlantUML が \" を解釈しないため
        // 全角引用符へ置換する (シーケンス図の quote と同じ方針)。
        String t = s.replaceAll("\\s+", " ").replace('"', '＂').trim();
        return PlantUmlCommentFormatter.escapeText(t);
    }

    private static String truncate(String s, int maxLen) {
        if (maxLen <= 0 || s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(1, maxLen - 1)) + "…";
    }

    private static String emptyDiagram(Options o, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            sb.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }
        sb.append("start\n");
        sb.append(":").append(escapeAction(reason, 0)).append(";\n");
        sb.append("stop\n");
        sb.append("@enduml\n");
        return sb.toString();
    }

    private PlantUmlActivityDiagram() {
    }
}
