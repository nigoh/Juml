// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * シーケンス図の制御構造 (if/loop/switch/try/synchronized) とコメント note の描画ヘルパ。
 * {@link PlantUmlSequenceDiagram} 本体から切り出した package-private な静的メソッド群で、
 * 再帰展開は {@link PlantUmlSequenceDiagram#walkStatements} に戻る。
 */
final class SeqEmitters {

    private SeqEmitters() {
    }

    static void emitCallSiteComment(StringBuilder body, String indent,
                                             String target, JavaMethodInfo method,
                                             PlantUmlSequenceDiagram.Options o) {
        if (o == null || !o.showComments
                || o.commentPlacement != PlantUmlSequenceDiagram.CommentPlacement.AT_CALL_SITE) {
            return;
        }
        if (method == null || method.getComment() == null
                || method.getComment().isEmpty()) {
            return;
        }
        if (o.commentStyle == PlantUmlClassDiagram.CommentStyle.NOTE) {
            emitNoteBlockAtCall(body, indent, target, method, o);
        } else {
            emitInlineNoteAtCall(body, indent, target, method, o);
        }
    }

    /** INLINE: 呼び出しの直下に 1 行 note を出す。 */
    static void emitInlineNoteAtCall(StringBuilder body, String indent,
                                              String target, JavaMethodInfo method,
                                              PlantUmlSequenceDiagram.Options o) {
        String first = JavaCommentScanner.firstLine(method.getComment());
        if (first == null || first.isEmpty()) {
            return;
        }
        String line = PlantUmlCommentFormatter.sanitizeInlineComment(first, o.commentMaxLength);
        if (line == null || line.isEmpty()) {
            return;
        }
        body.append(indent).append("note right of ").append(PlantUmlSequenceDiagram.quote(target)).append(" : ");
        if (o.commentColor != null && !o.commentColor.isEmpty()) {
            body.append("<color:").append(o.commentColor).append('>')
                    .append(line).append("</color>");
        } else {
            body.append(line);
        }
        body.append('\n');
    }

    /** NOTE: 呼び出しの直下に複数行 note ブロックを出す。 */
    static void emitNoteBlockAtCall(StringBuilder body, String indent,
                                             String target, JavaMethodInfo method,
                                             PlantUmlSequenceDiagram.Options o) {
        boolean hasBody = o.showMethodBodyComments
                && method.getBodyComments() != null
                && !method.getBodyComments().isEmpty();
        body.append(indent).append("note right of ").append(PlantUmlSequenceDiagram.quote(target)).append('\n');
        String[] lines = method.getComment().split("\n", -1);
        boolean any = false;
        for (String raw : lines) {
            String t = raw.replace('\r', ' ').replace('\t', ' ').trim();
            if (t.isEmpty()) {
                continue;
            }
            for (String wl : PlantUmlCommentFormatter.wordWrap(t, o.commentMaxLength).split("\n", -1)) {
                if (!wl.isEmpty()) {
                    body.append(indent).append("  ")
                            .append(PlantUmlCommentFormatter.escapeHtml(wl)).append('\n');
                    any = true;
                }
            }
        }
        if (!any) {
            body.append(indent).append("  ").append(method.getName()).append("()\n");
        }
        if (hasBody) {
            for (String bc : method.getBodyComments()) {
                String[] bl = bc.split("\n", -1);
                for (String raw : bl) {
                    String t = raw.replace('\r', ' ').replace('\t', ' ').trim();
                    if (t.isEmpty()) {
                        continue;
                    }
                    for (String wl : PlantUmlCommentFormatter.wordWrap(t, o.commentMaxLength).split("\n", -1)) {
                        if (!wl.isEmpty()) {
                            body.append(indent).append("  // ")
                                    .append(PlantUmlCommentFormatter.escapeHtml(wl)).append('\n');
                        }
                    }
                }
            }
        }
        body.append(indent).append("end note\n");
    }

    static void emitBlock(JavaMethodInfo.Block block, JavaClassInfo currentClass, int depth, String indent, SeqRender r) {
            List<JavaClassInfo> classes = r.classes;
            Set<String> participants = r.participants;
            Set<String> inlineParticipants = r.inlineParticipants;
            Map<String, LinkedHashSet<String>> participantMethods = r.participantMethods;
            StringBuilder body = r.body;
            Set<String> stack = r.stack;
            PlantUmlSequenceDiagram.Options opts = r.opts;

        List<JavaMethodInfo.Branch> bs = block.getBranches();
        if (bs.isEmpty()) {
            return;
        }
        String inner = indent + "    ";
        switch (block.getKind()) {
            case IF:
                emitIf(bs, currentClass, depth, indent, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
                break;
            case WHILE:
            case FOR:
            case DO_WHILE:
                emitLoop(block, bs.get(0), currentClass, depth, indent, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
                break;
            case SWITCH:
                emitSwitch(bs, currentClass, depth, indent, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
                break;
            case TRY:
                emitTry(bs, currentClass, depth, indent, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
                break;
            case SYNCHRONIZED:
                emitSynchronized(bs.get(0), currentClass, depth, indent, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
                break;
            default:
                break;
        }
    }

    static void emitIf(List<JavaMethodInfo.Branch> bs, JavaClassInfo currentClass, int depth, String indent, String inner, SeqRender r) {
            List<JavaClassInfo> classes = r.classes;
            Set<String> participants = r.participants;
            Set<String> inlineParticipants = r.inlineParticipants;
            Map<String, LinkedHashSet<String>> participantMethods = r.participantMethods;
            StringBuilder body = r.body;
            Set<String> stack = r.stack;
            PlantUmlSequenceDiagram.Options opts = r.opts;

        JavaMethodInfo.Branch first = bs.get(0);
        boolean hasElseChain = bs.size() > 1;
        if (!hasElseChain) {
            // 単一分岐 → opt
            body.append(indent).append("opt ").append(PlantUmlCommentFormatter.escapeLabel(first.getLabel())).append('\n');
            PlantUmlSequenceDiagram.walkStatements(first.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
            body.append(indent).append("end\n");
            return;
        }
        // 複数分岐 → alt + else
        body.append(indent).append("alt ").append(PlantUmlCommentFormatter.escapeLabel(first.getLabel())).append('\n');
        PlantUmlSequenceDiagram.walkStatements(first.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
        for (int i = 1; i < bs.size(); i++) {
            JavaMethodInfo.Branch b = bs.get(i);
            if ("else if".equals(b.getType())) {
                body.append(indent).append("else ").append(PlantUmlCommentFormatter.escapeLabel(b.getLabel())).append('\n');
            } else {
                body.append(indent).append("else\n");
            }
            PlantUmlSequenceDiagram.walkStatements(b.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
        }
        body.append(indent).append("end\n");
    }

    static void emitLoop(JavaMethodInfo.Block block, JavaMethodInfo.Branch br, JavaClassInfo currentClass, int depth, String indent, String inner, SeqRender r) {
            List<JavaClassInfo> classes = r.classes;
            Set<String> participants = r.participants;
            Set<String> inlineParticipants = r.inlineParticipants;
            Map<String, LinkedHashSet<String>> participantMethods = r.participantMethods;
            StringBuilder body = r.body;
            Set<String> stack = r.stack;
            PlantUmlSequenceDiagram.Options opts = r.opts;

        String label;
        switch (block.getKind()) {
            case WHILE:
                label = "while (" + br.getLabel() + ")";
                break;
            case FOR:
                label = "for (" + br.getLabel() + ")";
                break;
            case DO_WHILE:
                label = br.getLabel().isEmpty() ? "do-while" : "do-while (" + br.getLabel() + ")";
                break;
            default:
                label = br.getLabel();
                break;
        }
        body.append(indent).append("loop ").append(PlantUmlCommentFormatter.escapeLabel(label)).append('\n');
        PlantUmlSequenceDiagram.walkStatements(br.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
        body.append(indent).append("end\n");
    }

    static void emitSwitch(List<JavaMethodInfo.Branch> bs, JavaClassInfo currentClass, int depth, String indent, String inner, SeqRender r) {
            List<JavaClassInfo> classes = r.classes;
            Set<String> participants = r.participants;
            Set<String> inlineParticipants = r.inlineParticipants;
            Map<String, LinkedHashSet<String>> participantMethods = r.participantMethods;
            StringBuilder body = r.body;
            Set<String> stack = r.stack;
            PlantUmlSequenceDiagram.Options opts = r.opts;

        // bs[0] は switch ヘッダ ("switch", cond)、残りが case/default
        JavaMethodInfo.Branch head = bs.get(0);
        if (bs.size() <= 1) {
            return;
        }
        String switchLabel = "switch (" + head.getLabel() + ")";
        // 最初の case を alt の条件部に、それ以降を else として連ねる
        boolean openedAlt = false;
        for (int i = 1; i < bs.size(); i++) {
            JavaMethodInfo.Branch b = bs.get(i);
            String caseLabel;
            if ("default".equals(b.getType())) {
                caseLabel = "default";
            } else {
                caseLabel = "case " + b.getLabel();
            }
            if (!openedAlt) {
                body.append(indent).append("alt ").append(PlantUmlCommentFormatter.escapeLabel(switchLabel + " / " + caseLabel)).append('\n');
                openedAlt = true;
            } else {
                body.append(indent).append("else ").append(PlantUmlCommentFormatter.escapeLabel(caseLabel)).append('\n');
            }
            PlantUmlSequenceDiagram.walkStatements(b.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
        }
        if (openedAlt) {
            body.append(indent).append("end\n");
        }
    }

    static void emitTry(List<JavaMethodInfo.Branch> bs, JavaClassInfo currentClass, int depth, String indent, String inner, SeqRender r) {
            List<JavaClassInfo> classes = r.classes;
            Set<String> participants = r.participants;
            Set<String> inlineParticipants = r.inlineParticipants;
            Map<String, LinkedHashSet<String>> participantMethods = r.participantMethods;
            StringBuilder body = r.body;
            Set<String> stack = r.stack;
            PlantUmlSequenceDiagram.Options opts = r.opts;

        body.append(indent).append("group try\n");
        for (JavaMethodInfo.Branch b : bs) {
            if ("try".equals(b.getType())) {
                PlantUmlSequenceDiagram.walkStatements(b.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
            } else if ("catch".equals(b.getType())) {
                body.append(indent).append("else catch ").append(PlantUmlCommentFormatter.escapeLabel(b.getLabel())).append('\n');
                PlantUmlSequenceDiagram.walkStatements(b.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
            } else if ("finally".equals(b.getType())) {
                body.append(indent).append("else finally\n");
                PlantUmlSequenceDiagram.walkStatements(b.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
            }
        }
        body.append(indent).append("end\n");
    }

    static void emitSynchronized(JavaMethodInfo.Branch br, JavaClassInfo currentClass, int depth, String indent, String inner, SeqRender r) {
            List<JavaClassInfo> classes = r.classes;
            Set<String> participants = r.participants;
            Set<String> inlineParticipants = r.inlineParticipants;
            Map<String, LinkedHashSet<String>> participantMethods = r.participantMethods;
            StringBuilder body = r.body;
            Set<String> stack = r.stack;
            PlantUmlSequenceDiagram.Options opts = r.opts;

        body.append(indent).append("critical synchronized(")
                .append(PlantUmlCommentFormatter.escapeLabel(br.getLabel())).append(")\n");
        PlantUmlSequenceDiagram.walkStatements(br.getBody(), currentClass, depth, inner, new SeqRender(classes, participants, inlineParticipants, participantMethods, body, stack, opts));
        body.append(indent).append("end\n");
    }
}
