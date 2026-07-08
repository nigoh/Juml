// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * シーケンス図 ({@link PlantUmlSequenceDiagram}) の冒頭に出力する
 * クラス/メソッド JavaDoc を集約 note として発行するための補助クラス。
 *
 * <p>本クラスは {@link PlantUmlSequenceDiagram} 本体のサイズを抑える目的で
 * 切り出された純粋な出力ユーティリティで、ステートを持たない。
 * INLINE スタイルでは {@code note over P : ...} 行を participant ごとに連発し、
 * NOTE スタイルでは {@code note over P ... end note} ブロックにまとめる。</p>
 */
final class PlantUmlSequenceComments {

    private PlantUmlSequenceComments() {
    }

    /**
     * 各 participant の JavaDoc・本体内コメントを冒頭に集約して note 出力する。
     * シーケンス中に登場したメソッドのみ対象 (participantMethods から決定)。
     * 仮想の Caller participant や、解析対象外の外部クラスは skip する。
     */
    static void emit(StringBuilder out, PlantUmlSequenceDiagram.Options o,
                     Set<String> participants,
                     Map<String, LinkedHashSet<String>> participantMethods,
                     List<JavaClassInfo> classes) {
        boolean inline = o.commentStyle == PlantUmlClassDiagram.CommentStyle.INLINE;
        for (String p : participants) {
            if (o.callerName.equals(p)) {
                continue;
            }
            JavaClassInfo c = findClass(classes, p);
            if (c == null) {
                continue;
            }
            LinkedHashSet<String> methodNames = participantMethods.get(p);
            if (methodNames == null) {
                methodNames = new LinkedHashSet<>();
            }
            if (inline) {
                emitInline(out, o, p, c, methodNames);
            } else {
                emitBlock(out, o, p, c, methodNames);
            }
        }
    }

    /** INLINE スタイル: クラス/メソッドの 1 行コメントを {@code note over P : ...} で並べる。 */
    private static void emitInline(StringBuilder out, PlantUmlSequenceDiagram.Options o,
                                    String participant, JavaClassInfo c,
                                    LinkedHashSet<String> methodNames) {
        String classFirst = JavaCommentScanner.firstSentence(c.getComment());
        if (classFirst != null && !classFirst.isEmpty()) {
            appendInlineLine(out, participant, o,
                    PlantUmlCommentFormatter.sanitizeInlineComment(classFirst, o.commentMaxLength));
        }
        for (String name : methodNames) {
            JavaMethodInfo m = findMethod(c, name);
            if (m == null) {
                continue;
            }
            String first = JavaCommentScanner.firstSentence(m.getComment());
            if (first == null || first.isEmpty()) {
                continue;
            }
            String line = PlantUmlCommentFormatter.sanitizeInlineComment(
                    name + "(): " + first, o.commentMaxLength);
            appendInlineLine(out, participant, o, line);
        }
    }

    private static void appendInlineLine(StringBuilder out, String participant,
                                          PlantUmlSequenceDiagram.Options o, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        out.append("note over ").append(quote(participant)).append(" : ");
        if (o.commentColor != null && !o.commentColor.isEmpty()) {
            out.append("<color:").append(o.commentColor).append('>')
                    .append(text).append("</color>");
        } else {
            out.append(text);
        }
        out.append('\n');
    }

    /** NOTE スタイル: 1 つの {@code note over P ... end note} ブロックにまとめる。 */
    private static void emitBlock(StringBuilder out, PlantUmlSequenceDiagram.Options o,
                                   String participant, JavaClassInfo c,
                                   LinkedHashSet<String> methodNames) {
        boolean hasClassComment = c.getComment() != null && !c.getComment().isEmpty();
        boolean hasAnyMethodContent = false;
        for (String name : methodNames) {
            JavaMethodInfo m = findMethod(c, name);
            if (m == null) {
                continue;
            }
            if ((m.getComment() != null && !m.getComment().isEmpty())
                    || (o.showMethodBodyComments && !m.getBodyComments().isEmpty())) {
                hasAnyMethodContent = true;
                break;
            }
        }
        if (!hasClassComment && !hasAnyMethodContent) {
            return;
        }

        out.append("note over ").append(quote(participant)).append('\n');
        if (hasClassComment) {
            PlantUmlCommentFormatter.appendNoteBody(out, c.getComment(), "", o.commentMaxLength);
        }
        // クラスコメントと各メソッドの間、および連続するメソッド同士の間に区切り線を入れる
        boolean anyEmitted = hasClassComment;
        for (String name : methodNames) {
            JavaMethodInfo m = findMethod(c, name);
            if (m == null) {
                continue;
            }
            boolean hasJavadoc = m.getComment() != null && !m.getComment().isEmpty();
            boolean hasBody = o.showMethodBodyComments && !m.getBodyComments().isEmpty();
            if (!hasJavadoc && !hasBody) {
                continue;
            }
            if (anyEmitted) {
                out.append("  ---\n");
            }
            anyEmitted = true;
            if (hasJavadoc) {
                appendMethodComment(out, name, m, o);
            } else {
                out.append("  ").append(name).append("()\n");
            }
            if (hasBody) {
                appendBodyComments(out, m, o);
            }
        }
        out.append("end note\n");
    }

    private static void appendMethodComment(StringBuilder out, String name,
                                              JavaMethodInfo m,
                                              PlantUmlSequenceDiagram.Options o) {
        // 1 行目はメソッド名: prefix、2 行目以降はインデント揃え
        String[] lines = m.getComment().split("\n", -1);
        boolean first = true;
        for (String raw : lines) {
            String t = raw.replace('\r', ' ').replace('\t', ' ').trim();
            if (t.isEmpty()) {
                continue;
            }
            if (first) {
                String[] wl = PlantUmlCommentFormatter.wordWrap(t, o.commentMaxLength).split("\n", -1);
                out.append("  ").append(name).append("(): ")
                        .append(PlantUmlCommentFormatter.escapeText(wl[0])).append('\n');
                for (int i = 1; i < wl.length; i++) {
                    if (!wl[i].isEmpty()) {
                        // "end note" だけの行は終端注入になるため無害化する
                        out.append("    ").append(PlantUmlCommentFormatter
                                .sanitizeNoteLine(
                                        PlantUmlCommentFormatter.escapeText(wl[i])))
                                .append('\n');
                    }
                }
                first = false;
            } else {
                for (String wl : PlantUmlCommentFormatter.wordWrap(t, o.commentMaxLength).split("\n", -1)) {
                    if (!wl.isEmpty()) {
                        out.append("    ").append(PlantUmlCommentFormatter
                                .sanitizeNoteLine(
                                        PlantUmlCommentFormatter.escapeText(wl)))
                                .append('\n');
                    }
                }
            }
        }
        if (first) {
            // 全行が空だった場合のフォールバック (メソッド名だけ)
            out.append("  ").append(name).append("()\n");
        }
    }

    private static void appendBodyComments(StringBuilder out, JavaMethodInfo m,
                                            PlantUmlSequenceDiagram.Options o) {
        for (String bc : m.getBodyComments()) {
            String[] lines = bc.split("\n", -1);
            for (String raw : lines) {
                String t = raw.replace('\r', ' ').replace('\t', ' ').trim();
                if (t.isEmpty()) {
                    continue;
                }
                for (String wl : PlantUmlCommentFormatter.wordWrap(t, o.commentMaxLength).split("\n", -1)) {
                    if (!wl.isEmpty()) {
                        out.append("    // ")
                                .append(PlantUmlCommentFormatter.escapeText(wl)).append('\n');
                    }
                }
            }
        }
    }

    private static String quote(String s) {
        // PlantUmlSequenceDiagram.quote と同じ理由で、名前中の ASCII " は
        // 全角引用符へ置換する (PlantUML は \" を解釈せず構文エラーになる)。
        return "\"" + s.replace('"', '＂') + "\"";
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
}
