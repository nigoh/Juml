// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.TypeParameter;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.Visibility;

/**
 * JavaParser のメンバ宣言（フィールド/メソッド/コンストラクタ）を既存モデルへ変換する。
 *
 * <p>P1 では署名（名前・型・可視性・修飾子・throws・アノテーション）のみを移送し、
 * 本体の statement tree は扱わない（P2 の {@code StatementAdapter} で追加する）。</p>
 */
final class MemberAdapter {

    private MemberAdapter() {
    }

    /** {@code int a, b;} のように複数宣言子があれば 1 変数ごとに {@link JavaFieldInfo} を作る。 */
    static void addField(JavaClassInfo owner, FieldDeclaration fd, JpContext ctx) {
        Visibility vis = JpText.visibility(fd);
        boolean isStatic = fd.isStatic();
        boolean isFinal = fd.isFinal();
        java.util.List<String> anns = JpText.annotations(fd);
        String comment = ctx.comments.before(fd);
        for (VariableDeclarator v : fd.getVariables()) {
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(v.getNameAsString());
            f.setType(v.getType().toString());
            f.setVisibility(vis);
            f.setStatic(isStatic);
            f.setFinal(isFinal);
            f.getAnnotations().addAll(anns);
            f.setComment(comment);
            // static final 定数は初期化値を保持し、クラス図に併記する
            if (isStatic && isFinal) {
                v.getInitializer().ifPresent(init -> f.setConstantValue(init.toString()));
            }
            if (!ctx.headersOnly) {
                v.getInitializer().ifPresent(
                        init -> ExpressionAdapter.buildInline(
                                init, f.getType(), f.getName(), f.getInlineMethods(), ctx));
            }
            owner.getFields().add(f);
        }
    }

    /**
     * record のヘッダコンポーネント ({@code record Point(int x, int y)} の {@code x}/{@code y}) を
     * 暗黙の {@code private final} フィールドとして追加する。これらは {@code FieldDeclaration} では
     * なく {@code RecordDeclaration.getParameters()} に現れるため、別途取り込む必要がある。
     */
    static void addRecordComponent(JavaClassInfo owner, Parameter p) {
        JavaFieldInfo f = new JavaFieldInfo();
        f.setName(p.getNameAsString());
        f.setType(p.getType().toString());
        f.setVisibility(Visibility.PRIVATE);
        f.setStatic(false);
        f.setFinal(true);
        f.getAnnotations().addAll(JpText.annotations(p));
        owner.getFields().add(f);
    }

    static void addMethod(JavaClassInfo owner, MethodDeclaration md, JpContext ctx) {
        owner.getMethods().add(toMethod(md, ctx, owner));
    }

    /** 匿名クラス本体のメソッド用（所有クラスのフィールド代入取り込みは行わない）。 */
    static JavaMethodInfo toMethod(MethodDeclaration md, JpContext ctx) {
        return toMethod(md, ctx, null);
    }

    /** {@link MethodDeclaration} を本体込みで {@link JavaMethodInfo} に変換する。 */
    static JavaMethodInfo toMethod(MethodDeclaration md, JpContext ctx, JavaClassInfo owner) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(md.getNameAsString());
        md.getBegin().ifPresent(p -> m.setStartLine(p.line));
        m.setReturnType(md.getType().toString());
        m.setTypeParameters(typeParametersOf(md.getTypeParameters()));
        addParams(m, md.getParameters());
        m.setVisibility(JpText.visibility(md));
        m.setStatic(md.isStatic());
        boolean interfaceImplicitAbstract = !md.getBody().isPresent()
                && !md.isDefault() && !md.isStatic() && md.findCompilationUnit().isPresent()
                && isInterfaceMethod(md);
        m.setAbstract(md.isAbstract() || interfaceImplicitAbstract);
        m.getAnnotations().addAll(JpText.annotations(md));
        md.getThrownExceptions().forEach(t -> m.getThrowsTypes().add(t.toString()));
        m.setComment(ctx.comments.before(md));
        if (!ctx.headersOnly) {
            md.getBody().ifPresent(b -> {
                StatementAdapter.emitBody(b, m.getStatements(), ctx, owner);
                m.getBodyComments().addAll(ctx.comments.within(b));
            });
        }
        return m;
    }

    private static boolean isInterfaceMethod(MethodDeclaration md) {
        return md.getParentNode()
                .filter(p -> p instanceof com.github.javaparser.ast.body
                        .ClassOrInterfaceDeclaration)
                .map(p -> ((com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) p)
                        .isInterface())
                .orElse(false);
    }

    /** アノテーション属性 ({@code int[] value() default {};}) を引数なしメソッドとして扱う。 */
    static void addAnnotationMember(JavaClassInfo owner, AnnotationMemberDeclaration amd,
                                    JpContext ctx) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(amd.getNameAsString());
        amd.getBegin().ifPresent(p -> m.setStartLine(p.line));
        m.setReturnType(amd.getType().toString());
        m.setVisibility(Visibility.PUBLIC);
        m.setAbstract(true);
        m.getAnnotations().addAll(JpText.annotations(amd));
        // default 値 (int timeout() default 30;) を保持し、クラス図に併記する
        amd.getDefaultValue().ifPresent(v -> m.setDefaultValue(v.toString()));
        m.setComment(ctx.comments.before(amd));
        owner.getMethods().add(m);
    }

    static void addConstructor(JavaClassInfo owner, ConstructorDeclaration cd, JpContext ctx) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(cd.getNameAsString());
        cd.getBegin().ifPresent(p -> m.setStartLine(p.line));
        m.setConstructor(true);
        addParams(m, cd.getParameters());
        m.setVisibility(JpText.visibility(cd));
        m.getAnnotations().addAll(JpText.annotations(cd));
        cd.getThrownExceptions().forEach(t -> m.getThrowsTypes().add(t.toString()));
        m.setComment(ctx.comments.before(cd));
        if (!ctx.headersOnly) {
            StatementAdapter.emitBody(cd.getBody(), m.getStatements(), ctx, owner);
            m.getBodyComments().addAll(ctx.comments.within(cd.getBody()));
        }
        owner.getMethods().add(m);
    }

    /**
     * static / instance イニシャライザブロック ({@code static { ... }} / {@code { ... }}) を
     * 擬似メソッド {@code <clinit>} / {@code <init>} として取り込み、ブロック内の呼び出しが
     * シーケンス図に現れるようにする。これらは {@code MethodDeclaration} ではないため別途処理が要る。
     */
    static void addInitializer(JavaClassInfo owner, InitializerDeclaration id, JpContext ctx) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(id.isStatic() ? "<clinit>" : "<init>");
        id.getBegin().ifPresent(p -> m.setStartLine(p.line));
        m.setStatic(id.isStatic());
        m.setVisibility(Visibility.PRIVATE);
        if (!ctx.headersOnly) {
            StatementAdapter.emitBody(id.getBody(), m.getStatements(), ctx, owner);
            m.getBodyComments().addAll(ctx.comments.within(id.getBody()));
        }
        owner.getMethods().add(m);
    }

    /** メソッドの型パラメータ宣言を {@code "<T, R extends X>"} 形式に整形する。なければ null。 */
    private static String typeParametersOf(NodeList<TypeParameter> tps) {
        if (tps.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("<");
        for (int i = 0; i < tps.size(); i++) {
            sb.append(i == 0 ? "" : ", ").append(tps.get(i).toString());
        }
        return sb.append('>').toString();
    }

    private static void addParams(JavaMethodInfo m, NodeList<Parameter> params) {
        for (Parameter p : params) {
            String type = p.getType().toString();
            if (p.isVarArgs()) {
                type = type + "...";
            }
            m.getParameterTypes().add(type);
            m.getParameterNames().add(p.getNameAsString());
        }
    }
}
