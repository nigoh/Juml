// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.JavaParseSupport;
import juml.core.formats.uml.Visibility;

import java.util.List;

/**
 * 式から {@link JavaMethodInfo.Call} を抽出し、ラムダ/匿名クラス/メソッド参照の
 * インライン本体を取り込む。既存パーサーの receiver 文字列・firstArgLabel・SAM 名の
 * 規約に合わせる。
 */
final class ExpressionAdapter {

    private ExpressionAdapter() {
    }

    /**
     * 式 {@code e} 内のメソッド呼び出し/メソッド参照を {@code out} に Call として追加する。
     * 呼び出しの第 1 引数がラムダ/匿名クラスならその本体は inlineMethods に取り込み、
     * 兄弟 Call としては出さない。
     */
    static void emitCalls(Expression e, List<JavaMethodInfo.Statement> out, JpContext ctx) {
        if (e != null) {
            walk(e, out, ctx);
        }
    }

    private static void walk(Node n, List<JavaMethodInfo.Statement> out, JpContext ctx) {
        if (n instanceof SwitchExpr) {
            // switch 式は StatementAdapter が Block 化して扱うのでここでは降下しない
            return;
        }
        if (n instanceof MethodCallExpr) {
            walkCall((MethodCallExpr) n, out, ctx);
            return;
        }
        if (n instanceof MethodReferenceExpr) {
            MethodReferenceExpr mr = (MethodReferenceExpr) n;
            if (!"new".equals(mr.getIdentifier())) {
                out.add(new JavaMethodInfo.Call(methodRefReceiver(mr), mr.getIdentifier()));
            }
            mr.getScope().getChildNodes().forEach(c -> walk(c, out, ctx));
            return;
        }
        for (Node c : n.getChildNodes()) {
            walk(c, out, ctx);
        }
    }

    private static void walkCall(MethodCallExpr mc, List<JavaMethodInfo.Statement> out,
                                 JpContext ctx) {
        JavaMethodInfo.Call call = new JavaMethodInfo.Call(receiver(mc), mc.getNameAsString());
        setFirstArgLabel(call, mc);
        if (ctx.resolve) {
            resolveTarget(mc, call);
        }
        // 実行順 (レシーバ → 引数 → 自身の呼び出し) で emit する。自身を先に足すと
        // b.getC().hello() のような fluent chain が [hello, getC] と逆順になり、
        // シーケンス図が実行と逆の順序で呼び出しを描いてしまう。
        mc.getScope().ifPresent(s -> walk(s, out, ctx));
        // 引数の位置に関わらず、インライン (ラムダ/匿名クラス/メソッド参照) は
        // コールバック本体として call.getInlineMethods() に取り込む。
        // (第2引数以降の匿名クラス — 例: addTextChangedListener(null, new TextWatcher(){..}) —
        // の本体を取りこぼさない)。それ以外の引数は通常どおり walk で降下する。
        for (Expression arg : mc.getArguments()) {
            if (isInlineArg(arg)) {
                buildInline(arg, null, mc.getNameAsString(), call.getInlineMethods(), ctx);
            } else {
                walk(arg, out, ctx);
            }
        }
        out.add(call);
    }

    /** 呼び出しの receiver 文字列（{@code ""} / {@code "foo"} / {@code "a.b"} / {@code "this.x"}）。 */
    static String receiver(MethodCallExpr mc) {
        return mc.getScope().map(ExpressionAdapter::receiverOf).orElse("");
    }

    /**
     * スコープ式から receiver 文字列を作る。名前/フィールドアクセスは原文どおり
     * ({@code "foo"} / {@code "a.b"} / {@code "this.x"}) を保ち、シンボル未解決でも
     * 読みやすいよう {@code new X(...)} → {@code X}、キャスト {@code ((T) x)} → {@code T} に畳む。
     */
    private static String receiverOf(Expression scope) {
        Expression e = scope;
        while (e instanceof com.github.javaparser.ast.expr.EnclosedExpr) {
            e = ((com.github.javaparser.ast.expr.EnclosedExpr) e).getInner();
        }
        if (e instanceof ObjectCreationExpr) {
            return JpText.outer(((ObjectCreationExpr) e).getType().toString());
        }
        if (e instanceof com.github.javaparser.ast.expr.CastExpr) {
            return JpText.outer(((com.github.javaparser.ast.expr.CastExpr) e)
                    .getType().toString());
        }
        if (e instanceof MethodCallExpr) {
            // チェーン呼び出しはルートまで辿る (new X().a().b() → X、getScreenManager().push() → self)
            return ((MethodCallExpr) e).getScope()
                    .map(ExpressionAdapter::receiverOf).orElse("");
        }
        return e.toString();
    }

    /**
     * メソッド参照 {@code Scope::id} の Scope を Call の receiver 文字列へ正規化する。
     * JavaParser はシンボル解決なしだと Scope を一律 {@code TypeExpr} として返すため、
     * 表層的な綴りで「型 FQN」と「インスタンス/フィールド連鎖」を見分ける:
     * <ul>
     *   <li>ジェネリクスは PlantUML participant 名を壊すので常に除去 ({@code Foo<Bar>} → {@code Foo})</li>
     *   <li>末尾セグメントが大文字始まりの FQN は単純名へ畳む ({@code com.example.Mapper} → {@code Mapper})</li>
     *   <li>小文字終わりの連鎖は既存挙動どおり原文維持 ({@code a.b.c} / {@code System.out})</li>
     * </ul>
     */
    static String methodRefReceiver(MethodReferenceExpr mr) {
        String s = mr.getScope().toString();
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            String last = s.substring(dot + 1);
            if (!last.isEmpty() && Character.isUpperCase(last.charAt(0))) {
                return last;
            }
        }
        return s;
    }

    /** SymbolSolver で呼び出し先の宣言型 FQN とシグネチャを解決する。失敗時は何もしない。 */
    private static void resolveTarget(MethodCallExpr mc, JavaMethodInfo.Call call) {
        try {
            com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration rmd =
                    mc.resolve();
            call.setResolvedOwnerFqn(rmd.declaringType().getQualifiedName());
            call.setResolvedSignature(rmd.getSignature());
        } catch (RuntimeException ex) {
            // UnsolvedSymbolException / UnsupportedOperationException 等は無視してフォールバック
        }
    }

    private static boolean isInlineArg(Expression e) {
        if (e instanceof LambdaExpr) {
            return true;
        }
        return e instanceof ObjectCreationExpr
                && ((ObjectCreationExpr) e).getAnonymousClassBody().isPresent();
    }

    /**
     * ラムダ/匿名クラスのインライン本体を {@code sink} に取り込む。
     * SAM 名は型ヒント・名前ヒントから {@link JavaParseSupport#resolveSamMethodName} で推定する。
     */
    static void buildInline(Expression arg, String typeHint, String nameHint,
                            List<JavaMethodInfo> sink, JpContext ctx) {
        if (arg instanceof LambdaExpr) {
            LambdaExpr le = (LambdaExpr) arg;
            JavaMethodInfo m = new JavaMethodInfo();
            m.setName(JavaParseSupport.resolveSamMethodName(typeHint, nameHint));
            m.setVisibility(Visibility.PUBLIC);
            if (le.getBody() instanceof BlockStmt) {
                StatementAdapter.emitBody((BlockStmt) le.getBody(), m.getStatements(), ctx);
            } else {
                le.getExpressionBody().ifPresent(
                        ex -> emitCalls(ex, m.getStatements(), ctx));
            }
            sink.add(m);
        } else if (arg instanceof ObjectCreationExpr
                && ((ObjectCreationExpr) arg).getAnonymousClassBody().isPresent()) {
            for (MethodDeclaration md
                    : anonMethods((ObjectCreationExpr) arg)) {
                sink.add(MemberAdapter.toMethod(md, ctx));
            }
        } else if (arg instanceof MethodReferenceExpr) {
            MethodReferenceExpr mr = (MethodReferenceExpr) arg;
            if (!"new".equals(mr.getIdentifier())) {
                JavaMethodInfo m = new JavaMethodInfo();
                m.setName(JavaParseSupport.resolveSamMethodName(typeHint, nameHint));
                m.setVisibility(Visibility.PUBLIC);
                m.getStatements().add(new JavaMethodInfo.Call(
                        methodRefReceiver(mr), mr.getIdentifier()));
                sink.add(m);
            }
        }
    }

    private static List<MethodDeclaration> anonMethods(ObjectCreationExpr oce) {
        List<MethodDeclaration> out = new java.util.ArrayList<>();
        oce.getAnonymousClassBody().ifPresent(body -> body.forEach(bd -> {
            if (bd instanceof MethodDeclaration) {
                out.add((MethodDeclaration) bd);
            }
        }));
        return out;
    }

    /**
     * 第 1 引数が定数シンボル（ドット区切りで末尾が UPPER_CASE）なら firstArgLabel に設定する。
     * {@code FOO + 1} や {@code X.method()} のような複合式は対象外。
     */
    private static void setFirstArgLabel(JavaMethodInfo.Call call, MethodCallExpr mc) {
        if (mc.getArguments().isEmpty()) {
            return;
        }
        Expression a0 = mc.getArgument(0);
        String full;
        if (a0 instanceof NameExpr) {
            full = ((NameExpr) a0).getNameAsString();
        } else if (a0 instanceof FieldAccessExpr && isPureNameChain(a0)) {
            full = a0.toString();
        } else {
            return;
        }
        int dot = full.lastIndexOf('.');
        String last = dot < 0 ? full : full.substring(dot + 1);
        if (JavaParseSupport.looksLikeConstantSymbol(last)) {
            call.setFirstArgLabel(full);
        }
    }

    /** {@code a.b.C} のように名前とフィールドアクセスのみで構成される式か。 */
    private static boolean isPureNameChain(Expression e) {
        if (e instanceof NameExpr) {
            return true;
        }
        if (e instanceof FieldAccessExpr) {
            return isPureNameChain(((FieldAccessExpr) e).getScope());
        }
        return false;
    }
}
