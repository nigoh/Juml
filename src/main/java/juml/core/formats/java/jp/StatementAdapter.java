// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.ContinueStmt;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.LocalClassDeclarationStmt;
import com.github.javaparser.ast.stmt.LocalRecordDeclarationStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SynchronizedStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * メソッド/ラムダ本体の文ツリーを {@link JavaMethodInfo.Statement} ツリーへ変換する。
 *
 * <p>制御ブロック（if/while/for/do/switch/try/synchronized）の条件式に含まれる呼び出しは、
 * 既存パーサーと同じくブロックの直前に兄弟 Call として持ち上げる。
 * {@code return}/{@code throw}/{@code yield} は式中の呼び出しを兄弟 Call にしてから本文ノードを足す。</p>
 */
final class StatementAdapter {

    private StatementAdapter() {
    }

    static void emitBody(BlockStmt body, List<JavaMethodInfo.Statement> out, JpContext ctx) {
        emitBody(body, out, ctx, null);
    }

    static void emitBody(BlockStmt body, List<JavaMethodInfo.Statement> out,
                         JpContext ctx, JavaClassInfo owner) {
        List<juml.core.formats.uml.JavaCommentScanner.Comment> direct =
                directComments(body, ctx);
        int ci = 0;
        for (Statement s : body.getStatements()) {
            int sb = ctx.comments.beginOffset(s);
            ci = flushComments(direct, ci, sb, out);
            emit(s, out, ctx, owner);
        }
        flushComments(direct, ci, Integer.MAX_VALUE, out);
    }

    private static int flushComments(
            List<juml.core.formats.uml.JavaCommentScanner.Comment> direct,
            int from, int beforeOffset, List<JavaMethodInfo.Statement> out) {
        int ci = from;
        while (ci < direct.size() && direct.get(ci).start < beforeOffset) {
            String t = juml.core.formats.uml.JavaCommentScanner.cleanText(direct.get(ci));
            if (t != null && !t.isEmpty()) {
                out.add(new JavaMethodInfo.InlineComment(t));
            }
            ci++;
        }
        return ci;
    }

    /** body 直下の（子文の内部に含まれない）コメントだけを返す。 */
    private static List<juml.core.formats.uml.JavaCommentScanner.Comment> directComments(
            BlockStmt body, JpContext ctx) {
        List<juml.core.formats.uml.JavaCommentScanner.Comment> out = new ArrayList<>();
        for (juml.core.formats.uml.JavaCommentScanner.Comment c
                : ctx.comments.commentsIn(body)) {
            boolean insideChild = false;
            for (Statement s : body.getStatements()) {
                int sb = ctx.comments.beginOffset(s);
                int se = ctx.comments.endOffset(s);
                if (sb <= c.start && c.end <= se) {
                    insideChild = true;
                    break;
                }
            }
            if (!insideChild) {
                out.add(c);
            }
        }
        return out;
    }

    private static void emitInto(Statement s, List<JavaMethodInfo.Statement> body,
                                 JpContext ctx, JavaClassInfo owner) {
        if (s instanceof BlockStmt) {
            emitBody((BlockStmt) s, body, ctx, owner);
        } else {
            emit(s, body, ctx, owner);
        }
    }

    private static void emit(Statement s, List<JavaMethodInfo.Statement> out,
                             JpContext ctx, JavaClassInfo owner) {
        if (s instanceof ExpressionStmt) {
            emitExprStatement(((ExpressionStmt) s).getExpression(), out, ctx, owner);
        } else if (s instanceof IfStmt) {
            emitIf((IfStmt) s, out, ctx, owner);
        } else if (s instanceof WhileStmt) {
            WhileStmt w = (WhileStmt) s;
            emitSingle(JavaMethodInfo.Block.Kind.WHILE, "while",
                    w.getCondition(), w.getBody(), out, ctx, owner);
        } else if (s instanceof ForStmt) {
            emitSingle(JavaMethodInfo.Block.Kind.FOR, "for",
                    null, ((ForStmt) s).getBody(), out, ctx, owner,
                    forHeader((ForStmt) s));
        } else if (s instanceof ForEachStmt) {
            ForEachStmt fe = (ForEachStmt) s;
            emitSingle(JavaMethodInfo.Block.Kind.FOR, "for", null, fe.getBody(), out, ctx, owner,
                    fe.getVariable() + " : " + fe.getIterable());
        } else if (s instanceof DoStmt) {
            DoStmt d = (DoStmt) s;
            emitSingle(JavaMethodInfo.Block.Kind.DO_WHILE, "do",
                    d.getCondition(), d.getBody(), out, ctx, owner,
                    ctx.comments.raw(d.getCondition()));
        } else if (s instanceof SynchronizedStmt) {
            SynchronizedStmt y = (SynchronizedStmt) s;
            emitSingle(JavaMethodInfo.Block.Kind.SYNCHRONIZED, "synchronized",
                    y.getExpression(), y.getBody(), out, ctx, owner);
        } else if (s instanceof SwitchStmt) {
            emitSwitch((SwitchStmt) s, ((SwitchStmt) s).getSelector(),
                    ((SwitchStmt) s).getEntries(), out, ctx, owner);
        } else if (s instanceof TryStmt) {
            emitTry((TryStmt) s, out, ctx, owner);
        } else if (s instanceof ReturnStmt) {
            Expression e = ((ReturnStmt) s).getExpression().orElse(null);
            emitExprValue(e, out, ctx, owner);
            out.add(new JavaMethodInfo.Return(e == null ? "" : ctx.comments.raw(e)));
        } else if (s instanceof ThrowStmt) {
            Expression e = ((ThrowStmt) s).getExpression();
            emitExprValue(e, out, ctx, owner);
            out.add(new JavaMethodInfo.Throw(e == null ? "" : ctx.comments.raw(e)));
        } else if (s instanceof YieldStmt) {
            Expression e = ((YieldStmt) s).getExpression();
            emitExprValue(e, out, ctx, owner);
            out.add(new JavaMethodInfo.Yield(e == null ? "" : ctx.comments.raw(e)));
        } else if (s instanceof BreakStmt) {
            out.add(new JavaMethodInfo.Break(
                    ((BreakStmt) s).getLabel().map(Object::toString).orElse("")));
        } else if (s instanceof ContinueStmt) {
            out.add(new JavaMethodInfo.Continue(
                    ((ContinueStmt) s).getLabel().map(Object::toString).orElse("")));
        } else if (s instanceof LocalClassDeclarationStmt) {
            TypeDeclAdapter.adaptLocal(
                    ((LocalClassDeclarationStmt) s).getClassDeclaration(), ctx);
        } else if (s instanceof LocalRecordDeclarationStmt) {
            TypeDeclAdapter.adaptLocal(
                    ((LocalRecordDeclarationStmt) s).getRecordDeclaration(), ctx);
        }
    }

    /** 式文（メソッド呼び出し・代入・ローカル変数宣言）を処理する。 */
    private static void emitExprStatement(Expression ex, List<JavaMethodInfo.Statement> out,
                                          JpContext ctx, JavaClassInfo owner) {
        if (ex instanceof VariableDeclarationExpr) {
            for (VariableDeclarator v : ((VariableDeclarationExpr) ex).getVariables()) {
                emitLocalVar(v, out, ctx);
            }
            return;
        }
        if (ex instanceof AssignExpr && owner != null
                && captureFieldAssignmentInline((AssignExpr) ex, ctx, owner)) {
            return;
        }
        emitExprValue(ex, out, ctx, owner);
    }

    /** {@code this.field = lambda/anon/ref} を所有クラスのフィールド inlineMethods に取り込む。 */
    private static boolean captureFieldAssignmentInline(AssignExpr ae, JpContext ctx,
                                                        JavaClassInfo owner) {
        if (ctx.headersOnly || !isInline(ae.getValue())) {
            return false;
        }
        String name = assignTargetField(ae.getTarget());
        if (name == null) {
            return false;
        }
        for (juml.core.formats.uml.JavaFieldInfo f : owner.getFields()) {
            if (name.equals(f.getName())) {
                ExpressionAdapter.buildInline(ae.getValue(), f.getType(), name,
                        f.getInlineMethods(), ctx);
                return true;
            }
        }
        return false;
    }

    private static String assignTargetField(Expression target) {
        if (target instanceof NameExpr) {
            return ((NameExpr) target).getNameAsString();
        }
        if (target instanceof FieldAccessExpr) {
            FieldAccessExpr fa = (FieldAccessExpr) target;
            if (fa.getScope() instanceof com.github.javaparser.ast.expr.ThisExpr) {
                return fa.getNameAsString();
            }
        }
        return null;
    }

    /**
     * 式 {@code ex} の直接子孫 SwitchExpr を返す。
     *
     * <p>{@link com.github.javaparser.ast.Node#findAll} はネストを含む全子孫を返すため、
     * 別の SwitchExpr の内側に入れ子になっているものを除外する。
     * 内側の switch は {@link #emitSwitch} の case アーム処理で再帰的に扱われる。</p>
     */
    private static List<SwitchExpr> topLevelSwitchExprs(Expression ex) {
        List<SwitchExpr> result = new ArrayList<>();
        for (SwitchExpr se : ex.findAll(SwitchExpr.class)) {
            // ex 自身が SwitchExpr のとき: findAll はノード自身を含むため se == ex が起きる。
            // 祖先 walk を始める前に自分自身は必ずトップレベルとして追加する。
            if (se == ex) {
                result.add(se);
                continue;
            }
            // se から ex に向かって祖先をたどり、途中に別の SwitchExpr があれば内側とみなす
            com.github.javaparser.ast.Node n = se.getParentNode().orElse(null);
            boolean nested = false;
            while (n != null && n != ex) {
                if (n instanceof SwitchExpr) {
                    nested = true;
                    break;
                }
                n = n.getParentNode().orElse(null);
            }
            // ex 自身が SwitchExpr のとき、祖先 walk が se → ... → ex で終了した場合、
            // ex という SwitchExpr の中に se が直接ネストされていることを意味する。
            // ex を「別の SwitchExpr」として扱い内側とみなす。
            if (!nested && n == ex && ex instanceof SwitchExpr) {
                nested = true;
            }
            if (!nested) {
                result.add(se);
            }
        }
        return result;
    }

    /** switch 式を Block 化しつつ、式中の呼び出しを兄弟 Call として持ち上げる。 */
    private static void emitExprValue(Expression ex, List<JavaMethodInfo.Statement> out,
                                      JpContext ctx, JavaClassInfo owner) {
        if (ex == null) {
            return;
        }
        // ネスト switch の二重 emit を防ぐため、直接子の SwitchExpr のみ処理する。
        // 内側の switch は emitSwitch 内の case アーム処理で再帰的に扱われる。
        for (SwitchExpr se : topLevelSwitchExprs(ex)) {
            emitSwitch(se, se.getSelector(), se.getEntries(), out, ctx, owner);
        }
        ExpressionAdapter.emitCalls(ex, out, ctx);
    }

    private static void emitLocalVar(VariableDeclarator v, List<JavaMethodInfo.Statement> out,
                                     JpContext ctx) {
        String type = v.getType().toString();
        String name = v.getNameAsString();
        Expression init = v.getInitializer().orElse(null);
        JavaMethodInfo.LocalVar lv;
        if (init != null && isInline(init)) {
            lv = new JavaMethodInfo.LocalVar(type, name, "<lambda>");
            ExpressionAdapter.buildInline(init, type, name, lv.getInlineMethods(), ctx);
            out.add(lv);
            return;
        }
        // switch 式初期化子の有無は AST 全走査になるため 1 度だけ実行して使い回す。
        // ネスト switch の二重 emit を防ぐため topLevelSwitchExprs で直接子のみ取得。
        List<SwitchExpr> switchInits = init == null
                ? java.util.Collections.emptyList() : topLevelSwitchExprs(init);
        if (!switchInits.isEmpty()) {
            // switch 式初期化子: Block を out に出し、LocalVar も残す
            for (SwitchExpr se : switchInits) {
                emitSwitch(se, se.getSelector(), se.getEntries(), out, ctx, null);
            }
            lv = new JavaMethodInfo.LocalVar(type, name, ctx.comments.raw(init));
        } else {
            lv = new JavaMethodInfo.LocalVar(type, name,
                    init == null ? "" : ctx.comments.raw(init));
        }
        out.add(lv);
    }

    private static boolean isInline(Expression e) {
        if (e instanceof com.github.javaparser.ast.expr.LambdaExpr
                || e instanceof com.github.javaparser.ast.expr.MethodReferenceExpr) {
            return true;
        }
        return e instanceof com.github.javaparser.ast.expr.ObjectCreationExpr
                && ((com.github.javaparser.ast.expr.ObjectCreationExpr) e)
                        .getAnonymousClassBody().isPresent();
    }

    private static void emitIf(IfStmt is, List<JavaMethodInfo.Statement> out,
                               JpContext ctx, JavaClassInfo owner) {
        ExpressionAdapter.emitCalls(is.getCondition(), out, ctx);
        JavaMethodInfo.Block block = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.IF);
        JavaMethodInfo.Branch first = new JavaMethodInfo.Branch(
                "if", ctx.comments.raw(is.getCondition()));
        block.getBranches().add(first);
        out.add(block);
        emitInto(is.getThenStmt(), first.getBody(), ctx, owner);
        Statement els = is.getElseStmt().orElse(null);
        while (els instanceof IfStmt) {
            IfStmt elif = (IfStmt) els;
            ExpressionAdapter.emitCalls(elif.getCondition(), out, ctx);
            JavaMethodInfo.Branch ei = new JavaMethodInfo.Branch(
                    "else if", ctx.comments.raw(elif.getCondition()));
            block.getBranches().add(ei);
            emitInto(elif.getThenStmt(), ei.getBody(), ctx, owner);
            els = elif.getElseStmt().orElse(null);
        }
        if (els != null) {
            JavaMethodInfo.Branch e = new JavaMethodInfo.Branch("else", "");
            block.getBranches().add(e);
            emitInto(els, e.getBody(), ctx, owner);
        }
    }

    private static void emitSingle(JavaMethodInfo.Block.Kind kind, String type,
                                   Expression cond, Statement bodyStmt,
                                   List<JavaMethodInfo.Statement> out, JpContext ctx,
                                   JavaClassInfo owner) {
        emitSingle(kind, type, cond, bodyStmt, out, ctx, owner,
                cond == null ? "" : ctx.comments.raw(cond));
    }

    private static void emitSingle(JavaMethodInfo.Block.Kind kind, String type,
                                   Expression cond, Statement bodyStmt,
                                   List<JavaMethodInfo.Statement> out, JpContext ctx,
                                   JavaClassInfo owner, String label) {
        if (cond != null) {
            ExpressionAdapter.emitCalls(cond, out, ctx);
        }
        JavaMethodInfo.Block b = new JavaMethodInfo.Block(kind);
        JavaMethodInfo.Branch br = new JavaMethodInfo.Branch(type, label);
        b.getBranches().add(br);
        out.add(b);
        emitInto(bodyStmt, br.getBody(), ctx, owner);
    }

    private static void emitSwitch(SwitchNode sw, Expression selector,
                                   List<SwitchEntry> entries,
                                   List<JavaMethodInfo.Statement> out, JpContext ctx,
                                   JavaClassInfo owner) {
        ExpressionAdapter.emitCalls(selector, out, ctx);
        JavaMethodInfo.Block block = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.SWITCH);
        block.getBranches().add(new JavaMethodInfo.Branch("switch", ctx.comments.raw(selector)));
        out.add(block);
        for (SwitchEntry entry : entries) {
            String type;
            String label;
            if (entry.getLabels().isEmpty()) {
                type = "default";
                label = "";
            } else {
                type = "case";
                StringBuilder sb = new StringBuilder();
                entry.getLabels().forEach(e -> {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(ctx.comments.raw(e));
                });
                entry.getGuard().ifPresent(g -> sb.append(" when ").append(ctx.comments.raw(g)));
                label = sb.toString();
            }
            JavaMethodInfo.Branch br = new JavaMethodInfo.Branch(type, label);
            block.getBranches().add(br);
            for (Statement st : entry.getStatements()) {
                emitInto(st, br.getBody(), ctx, owner);
            }
        }
    }

    private static void emitTry(TryStmt t, List<JavaMethodInfo.Statement> out,
                                JpContext ctx, JavaClassInfo owner) {
        t.getResources().forEach(r -> ExpressionAdapter.emitCalls(r, out, ctx));
        JavaMethodInfo.Block block = new JavaMethodInfo.Block(JavaMethodInfo.Block.Kind.TRY);
        JavaMethodInfo.Branch tryBranch = new JavaMethodInfo.Branch("try", "");
        block.getBranches().add(tryBranch);
        out.add(block);
        emitInto(t.getTryBlock(), tryBranch.getBody(), ctx, owner);
        t.getCatchClauses().forEach(cc -> {
            JavaMethodInfo.Branch c = new JavaMethodInfo.Branch(
                    "catch", ctx.comments.raw(cc.getParameter()));
            block.getBranches().add(c);
            emitInto(cc.getBody(), c.getBody(), ctx, owner);
        });
        t.getFinallyBlock().ifPresent(fb -> {
            JavaMethodInfo.Branch f = new JavaMethodInfo.Branch("finally", "");
            block.getBranches().add(f);
            emitInto(fb, f.getBody(), ctx, owner);
        });
    }

    private static String forHeader(ForStmt f) {
        StringBuilder sb = new StringBuilder();
        f.getInitialization().forEach(i -> {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(i.toString());
        });
        sb.append("; ").append(f.getCompare().map(Object::toString).orElse(""));
        sb.append("; ");
        boolean firstUpd = true;
        for (Expression u : f.getUpdate()) {
            if (!firstUpd) {
                sb.append(", ");
            }
            sb.append(u.toString());
            firstUpd = false;
        }
        return sb.toString();
    }
}
