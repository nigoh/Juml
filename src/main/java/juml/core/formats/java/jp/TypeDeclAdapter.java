// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.TypeParameter;
import juml.core.formats.uml.JavaClassInfo;

/**
 * JavaParser の型宣言（class/interface/enum/@interface/record）を {@link JavaClassInfo} に変換する。
 *
 * <p>ネストした型は既存パーサーと同様に「別の top-level エントリ」として {@code out} に並べ、
 * {@code enclosingClass} に外側の単純名チェーン（{@code "Outer"} / {@code "Outer.Mid"}）を設定する。</p>
 */
final class TypeDeclAdapter {

    private TypeDeclAdapter() {
    }

    /** トップレベル/ネスト型を変換する。 */
    static void adapt(TypeDeclaration<?> td, String enclosing, JpContext ctx) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(ctx.packageName);
        c.setSimpleName(td.getNameAsString());
        // 宣言行はアノテーション行ではなく名前の行を優先する (ソースジャンプの着地点)。
        td.getName().getBegin().ifPresentOrElse(
                p -> c.setStartLine(p.line),
                () -> td.getBegin().ifPresent(p -> c.setStartLine(p.line)));
        c.setEnclosingClass(enclosing);
        c.getImports().addAll(ctx.imports);
        c.getModifiers().addAll(JpText.modifiers(td));
        c.getAnnotations().addAll(JpText.annotations(td));
        c.setKind(kindOf(td));
        c.setComment(ctx.comments.before(td));
        c.setTypeParameters(typeParametersOf(td));
        applyExtendsImplements(td, c);
        String childEnclosing = enclosing == null || enclosing.isEmpty()
                ? td.getNameAsString() : enclosing + "." + td.getNameAsString();
        String savedEnclosing = ctx.currentEnclosing;
        ctx.currentEnclosing = childEnclosing;
        // フィールドを先に確定させてから本体を解析する
        // (コンストラクタ内 this.field = ... の inline 取り込みが後方宣言でも効くように)。
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof FieldDeclaration) {
                MemberAdapter.addField(c, (FieldDeclaration) bd, ctx);
            }
        }
        // record のヘッダコンポーネントは FieldDeclaration ではないので別途取り込む
        if (td instanceof RecordDeclaration) {
            ((RecordDeclaration) td).getParameters()
                    .forEach(p -> MemberAdapter.addRecordComponent(c, p));
        }
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof MethodDeclaration) {
                MemberAdapter.addMethod(c, (MethodDeclaration) bd, ctx);
            } else if (bd instanceof ConstructorDeclaration) {
                MemberAdapter.addConstructor(c, (ConstructorDeclaration) bd, ctx);
            } else if (bd instanceof com.github.javaparser.ast.body
                    .CompactConstructorDeclaration) {
                // record の正準コンストラクタ (引数は record コンポーネント)。
                MemberAdapter.addCompactConstructor(c,
                        (com.github.javaparser.ast.body.CompactConstructorDeclaration) bd,
                        td instanceof RecordDeclaration
                                ? ((RecordDeclaration) td).getParameters() : null, ctx);
            } else if (bd instanceof com.github.javaparser.ast.body
                    .AnnotationMemberDeclaration) {
                MemberAdapter.addAnnotationMember(c,
                        (com.github.javaparser.ast.body.AnnotationMemberDeclaration) bd, ctx);
            } else if (bd instanceof com.github.javaparser.ast.body
                    .InitializerDeclaration) {
                MemberAdapter.addInitializer(c,
                        (com.github.javaparser.ast.body.InitializerDeclaration) bd, ctx);
            }
        }
        ctx.currentEnclosing = savedEnclosing;
        if (td instanceof EnumDeclaration) {
            for (EnumConstantDeclaration ec : ((EnumDeclaration) td).getEntries()) {
                c.getEnumConstants().add(ec.getNameAsString());
                // 定数引数 RED(255, 0, 0) を併記用に保持（無名サブクラス body は読み飛ばす）
                c.getEnumConstantArgs().add(enumConstantArgs(ec));
            }
        }
        ctx.out.add(c);
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof TypeDeclaration) {
                adapt((TypeDeclaration<?>) bd, childEnclosing, ctx);
            }
        }
    }

    /** メソッド本体内のローカルクラス/レコードを別 top-level エントリとして追加する。 */
    static void adaptLocal(TypeDeclaration<?> td, JpContext ctx) {
        adapt(td, ctx.currentEnclosing, ctx);
    }

    /** 型パラメータ宣言を {@code "<T, U extends Number>"} 形式に整形する。なければ null。 */
    private static String typeParametersOf(TypeDeclaration<?> td) {
        NodeList<TypeParameter> tps;
        if (td instanceof ClassOrInterfaceDeclaration) {
            tps = ((ClassOrInterfaceDeclaration) td).getTypeParameters();
        } else if (td instanceof RecordDeclaration) {
            tps = ((RecordDeclaration) td).getTypeParameters();
        } else {
            return null;
        }
        if (tps.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("<");
        for (int i = 0; i < tps.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(tps.get(i).toString());
        }
        return sb.append('>').toString();
    }

    /** enum 定数のコンストラクタ引数を {@code "(a, b)"} 形式に整形する。引数なしは空文字。 */
    private static String enumConstantArgs(EnumConstantDeclaration ec) {
        if (ec.getArguments().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < ec.getArguments().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ec.getArguments().get(i).toString());
        }
        return sb.append(')').toString();
    }

    private static JavaClassInfo.Kind kindOf(TypeDeclaration<?> td) {
        if (td instanceof RecordDeclaration) {
            return JavaClassInfo.Kind.RECORD;
        }
        if (td instanceof EnumDeclaration) {
            return JavaClassInfo.Kind.ENUM;
        }
        if (td instanceof AnnotationDeclaration) {
            return JavaClassInfo.Kind.ANNOTATION;
        }
        if (td instanceof ClassOrInterfaceDeclaration
                && ((ClassOrInterfaceDeclaration) td).isInterface()) {
            return JavaClassInfo.Kind.INTERFACE;
        }
        return JavaClassInfo.Kind.CLASS;
    }

    private static void applyExtendsImplements(TypeDeclaration<?> td, JavaClassInfo c) {
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            if (cid.isInterface()) {
                cid.getExtendedTypes().forEach(t -> c.getInterfaces().add(t.toString()));
            } else {
                if (!cid.getExtendedTypes().isEmpty()) {
                    c.setSuperClass(cid.getExtendedTypes().get(0).toString());
                }
                cid.getImplementedTypes().forEach(t -> c.getInterfaces().add(t.toString()));
            }
            // sealed の permits は「継承を許可された子型」であり、このクラスが実装する
            // インタフェースではない。interfaces に混ぜると emitInheritance が逆向き矢印
            // (Child <|.. Shape) を出してしまうため、専用の permittedTypes に保持する。
            cid.getPermittedTypes().forEach(t -> c.getPermittedTypes().add(t.toString()));
        } else if (td instanceof RecordDeclaration) {
            ((RecordDeclaration) td).getImplementedTypes()
                    .forEach(t -> c.getInterfaces().add(t.toString()));
        } else if (td instanceof EnumDeclaration) {
            ((EnumDeclaration) td).getImplementedTypes()
                    .forEach(t -> c.getInterfaces().add(t.toString()));
        }
    }
}
