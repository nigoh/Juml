// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.refs;

import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.DependencyJarIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.util.ErrorListener;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link JavaClassInfo} のツリーから {@link ReferenceIndex} を構築するビルダ。
 *
 * <p>Stage B 化したクラスを受け取り、その内部のメソッド呼出・extends/implements・
 * フィールド型・アノテーション・引数型・戻り型・throws 型を全て解析して
 * 「誰が何を参照しているか」のエントリを {@link ReferenceIndex} に追加する。</p>
 *
 * <p>名前解決は {@link NameResolver} に委譲。解決不能なシンボルは
 * {@link ReferenceIndex#addUnresolved(String)} に記録する。</p>
 */
public final class ReferenceIndexBuilder {

    private final ReferenceIndex index;
    private final NameResolver resolver;
    private final ClassIndex classIndex;
    private final ErrorListener listener;

    public ReferenceIndexBuilder(ReferenceIndex index,
                                  ClassIndex classIndex,
                                  DependencyJarIndex depIndex,
                                  ErrorListener listener) {
        this.index = index;
        this.classIndex = classIndex;
        this.resolver = new NameResolver(classIndex, depIndex);
        this.listener = listener != null ? listener : ErrorListener.silent();
    }

    /** 複数クラスを一括追加。 */
    public void addAll(Collection<JavaClassInfo> classes) {
        if (classes == null) {
            return;
        }
        for (JavaClassInfo c : classes) {
            addClass(c);
        }
    }

    /**
     * 1 クラスをインデックスに追加。Stage B (detailed) のクラスだけが対象。
     * ヘッダのみのクラスはスキップする (呼出情報が無いため)。
     */
    public void addClass(JavaClassInfo cls) {
        if (cls == null) {
            return;
        }
        // Stage A (ヘッダのみ) のクラスは、ソース登録済みの ClassIndex 経由で
        // Stage B 昇格してからメソッド/フィールドの参照を拾う。lazyDetails ロード時に
        // Impact / Reverse-Reference / Cycles が呼び出し参照を取りこぼさないため。
        if (!cls.isDetailed() && classIndex != null) {
            JavaClassInfo detailed = classIndex.detail(cls.getQualifiedName(), listener);
            if (detailed != null) {
                cls = detailed;
            }
        }
        String ownerFqn = cls.getQualifiedName();
        String file = sourceFileOf(ownerFqn);

        // クラスヘッダ由来の参照
        addTypeRef(cls.getSuperClass(), cls, ownerFqn, "", file, ReferenceSite.Kind.EXTENDS);
        for (String iface : cls.getInterfaces()) {
            addTypeRef(iface, cls, ownerFqn, "", file, ReferenceSite.Kind.IMPLEMENTS);
        }
        for (String ann : cls.getAnnotations()) {
            addAnnotationRef(ann, cls, ownerFqn, "", file);
        }
        for (String imp : cls.getImports()) {
            String body = imp.startsWith("static ") ? imp.substring(7) : imp;
            if (body.endsWith(".*")) {
                continue; // ワイルドカードは特定クラスへの参照ではない
            }
            ReferenceSite site = new ReferenceSite(
                    ownerFqn, "", file, -1, ReferenceSite.Kind.IMPORT);
            index.addReference(ReferenceKey.ofClass(body), site);
            // メンバ import (Java の `import static a.b.C.FOO` / Kotlin の
            // `import a.b.C.MEMBER`) では body が member の FQN になり、含有クラス C への
            // 参照が落ちる。親 (最後のセグメントを除いた FQN) の単純名が大文字始まり
            // = クラスらしいときは、その含有クラスにも参照を立てて取りこぼしを防ぐ。
            int lastDot = body.lastIndexOf('.');
            if (lastDot > 0) {
                String parent = body.substring(0, lastDot);
                int pDot = parent.lastIndexOf('.');
                String parentSimple = pDot >= 0 ? parent.substring(pDot + 1) : parent;
                if (!parentSimple.isEmpty() && Character.isUpperCase(parentSimple.charAt(0))) {
                    index.addReference(ReferenceKey.ofClass(parent), site);
                }
            }
        }

        if (!cls.isDetailed()) {
            // 詳細未取得なら以降のメソッド/フィールド走査は不可
            return;
        }

        // フィールド名 → 型名のマップを作る (呼出受信側がフィールドの場合の解決に使う)
        Map<String, String> fieldTypes = new HashMap<>();
        for (JavaFieldInfo f : cls.getFields()) {
            addTypeRef(f.getType(), cls, ownerFqn, "", file,
                    ReferenceSite.Kind.TYPE_REFERENCE);
            for (String ann : f.getAnnotations()) {
                addAnnotationRef(ann, cls, ownerFqn, "", file);
            }
            if (f.getName() != null && f.getType() != null) {
                fieldTypes.put(f.getName(), f.getType());
            }
        }

        // メソッド
        for (JavaMethodInfo m : cls.getMethods()) {
            String mname = m.getName();
            addTypeRef(m.getReturnType(), cls, ownerFqn, mname, file,
                    ReferenceSite.Kind.TYPE_REFERENCE);
            // メソッドパラメータ名 → 型名 (引数フォーカスの呼出解決用)
            Map<String, String> paramTypes = new HashMap<>();
            List<String> pnames = m.getParameterNames();
            List<String> ptypes = m.getParameterTypes();
            for (int i = 0; i < ptypes.size(); i++) {
                addTypeRef(ptypes.get(i), cls, ownerFqn, mname, file,
                        ReferenceSite.Kind.TYPE_REFERENCE);
                if (i < pnames.size()) {
                    paramTypes.put(pnames.get(i), ptypes.get(i));
                }
            }
            for (String th : m.getThrowsTypes()) {
                addTypeRef(th, cls, ownerFqn, mname, file,
                        ReferenceSite.Kind.TYPE_REFERENCE);
            }
            for (String ann : m.getAnnotations()) {
                addAnnotationRef(ann, cls, ownerFqn, mname, file);
            }
            // メソッド本体内の呼出。フィールドと引数のローカルスコープを渡す。
            for (JavaMethodInfo.Call call : m.getCalls()) {
                addCall(call, cls, ownerFqn, mname, file, fieldTypes, paramTypes);
            }
        }
    }

    /** {@code receiver.method()} 形式の呼出を index に登録する。 */
    private void addCall(JavaMethodInfo.Call call, JavaClassInfo owner,
                          String ownerFqn, String callerMethod, String file,
                          Map<String, String> fieldTypes,
                          Map<String, String> paramTypes) {
        if (call == null) {
            return;
        }
        String methodName = call.getMethodName();
        String receiver = call.getReceiver();
        if (methodName == null || methodName.isEmpty()) {
            return;
        }
        // シンボル解決器が宣言型を特定済みなら、それを優先する
        // (チェーン/オーバーロード/継承/ジェネリクスを正確に辿れる)。
        String resolvedOwner = call.getResolvedOwnerFqn();
        String receiverFqn = (resolvedOwner != null && !resolvedOwner.isEmpty())
                ? resolvedOwner
                : resolveReceiver(receiver, owner, fieldTypes, paramTypes);
        if (receiverFqn == null) {
            // 受信側不明 — 診断目的で未解決を記録
            index.addUnresolved(methodName + "() in " + ownerFqn + "." + callerMethod);
            return;
        }
        ReferenceSite site = new ReferenceSite(
                ownerFqn, callerMethod, file, -1, ReferenceSite.Kind.CALL);
        String sig = call.getResolvedSignature();
        ReferenceKey key = (sig != null && !sig.isEmpty())
                ? ReferenceKey.ofMethod(receiverFqn, methodName, sig)
                : ReferenceKey.ofMethod(receiverFqn, methodName);
        index.addReference(key, site);
    }

    /**
     * 呼出 receiver から FQN を推定する。
     * <ul>
     *   <li>receiver が空文字 → 自クラスへの暗黙呼出 (this) として owner FQN を返す</li>
     *   <li>receiver が "this" → owner FQN</li>
     *   <li>receiver がフィールド名 → フィールド型を解決</li>
     *   <li>receiver がパラメータ名 → パラメータ型を解決</li>
     *   <li>receiver がクラス名 (単純名) → NameResolver で解決 (Static.method 呼出)</li>
     *   <li>receiver がドット込みで先頭が型名 → 型部分を解決</li>
     *   <li>解決できなければ null (チェーン呼出 / 未知のローカル変数)</li>
     * </ul>
     */
    private String resolveReceiver(String receiver, JavaClassInfo owner,
                                     Map<String, String> fieldTypes,
                                     Map<String, String> paramTypes) {
        if (receiver == null || receiver.isEmpty() || "this".equals(receiver)) {
            return owner.getQualifiedName();
        }
        if (receiver.indexOf('.') < 0) {
            // 1. パラメータ
            String paramType = paramTypes.get(receiver);
            if (paramType != null) {
                return resolveTypeToFqn(paramType, owner);
            }
            // 2. フィールド
            String fieldType = fieldTypes.get(receiver);
            if (fieldType != null) {
                return resolveTypeToFqn(fieldType, owner);
            }
            // 3. 型名 (Static.method)
            String r = resolver.resolve(receiver, owner);
            if (r != null && !r.isEmpty() && !r.equals(receiver)) {
                return r;
            }
            // 4. 未知のローカル変数
            return null;
        }
        // ドット入り: "this.field.bar()" → field の型を見る
        if (receiver.startsWith("this.")) {
            String rest = receiver.substring(5);
            int dot = rest.indexOf('.');
            String fieldName = dot < 0 ? rest : rest.substring(0, dot);
            String fieldType = fieldTypes.get(fieldName);
            if (fieldType != null && dot < 0) {
                return resolveTypeToFqn(fieldType, owner);
            }
            return null;
        }
        // "Foo.bar.method()" のように先頭が型名らしい場合は型部分を解決
        String head = receiver.substring(0, receiver.indexOf('.'));
        if (!head.isEmpty() && Character.isUpperCase(head.charAt(0))) {
            String r = resolver.resolve(head, owner);
            if (r != null && !r.isEmpty() && !r.equals(head)) {
                return r;
            }
        }
        // それ以外はチェーン呼出 (a.b.c.foo()) の終端まで追えないので null
        return null;
    }

    /** 型名 (ジェネリクス/配列付きでも可) を FQN に解決する。 */
    private String resolveTypeToFqn(String type, JavaClassInfo owner) {
        String resolved = resolver.resolve(type, owner);
        if (resolved == null || resolved.isEmpty()) {
            return null;
        }
        return resolved;
    }

    /** 型参照 ({@code extends X}, フィールド型など) を登録。 */
    private void addTypeRef(String typeName, JavaClassInfo owner,
                             String ownerFqn, String callerMethod,
                             String file, ReferenceSite.Kind kind) {
        if (typeName == null || typeName.isEmpty()) {
            return;
        }
        String stripped = stripGenericsAndArrays(typeName);
        if (stripped.isEmpty() || isPrimitive(stripped)) {
            return;
        }
        String resolved = resolver.resolve(stripped, owner);
        if (resolved == null || resolved.isEmpty()) {
            return;
        }
        if (resolved.equals(stripped) && resolved.indexOf('.') < 0) {
            // 単純名のまま、FQN に解決できなかった
            index.addUnresolved(stripped + " (typeref in " + ownerFqn + ")");
            return;
        }
        ReferenceSite site = new ReferenceSite(ownerFqn, callerMethod, file, -1, kind);
        index.addReference(ReferenceKey.ofClass(resolved), site);
    }

    /** アノテーション参照を登録。{@code @Foo(...)} の {@code Foo} を解決する。 */
    private void addAnnotationRef(String annName, JavaClassInfo owner,
                                   String ownerFqn, String callerMethod, String file) {
        if (annName == null || annName.isEmpty()) {
            return;
        }
        // "@Foo(...)" や "@Foo" の形式を想定
        String raw = annName.startsWith("@") ? annName.substring(1) : annName;
        int paren = raw.indexOf('(');
        String body = (paren >= 0 ? raw.substring(0, paren) : raw).trim();
        if (body.isEmpty()) {
            return;
        }
        String resolved = resolver.resolve(body, owner);
        if (resolved == null || resolved.isEmpty() || resolved.indexOf('.') < 0) {
            // 解決失敗
            index.addUnresolved("@" + body + " in " + ownerFqn);
        } else {
            ReferenceSite site = new ReferenceSite(
                    ownerFqn, callerMethod, file, -1, ReferenceSite.Kind.ANNOTATION);
            index.addReference(ReferenceKey.ofClass(resolved), site);
        }
        // アノテーション引数内のクラスリテラル (@TypeConverters({Foo.class}) /
        // @Database(entities = {Bar.class}) / Kotlin の Foo::class) も参照として数える。
        // (Room/Dagger 等で多用され、これを無視すると対象クラスが dead-code 誤検出される)
        if (paren >= 0) {
            addClassLiteralRefs(raw.substring(paren + 1), owner, ownerFqn, callerMethod, file);
        }
    }

    /** {@code Foo.class} / {@code Foo::class} の前にある型名を抜き出すパターン。 */
    private static final Pattern CLASS_LITERAL =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_.]*?)\\s*(?:\\.class|::class)\\b");

    /** 文字列中の {@code X.class} / {@code X::class} 型リテラルを参照として登録する。 */
    private void addClassLiteralRefs(String args, JavaClassInfo owner, String ownerFqn,
                                     String callerMethod, String file) {
        Matcher m = CLASS_LITERAL.matcher(args);
        while (m.find()) {
            String typeName = m.group(1);
            String resolved = resolver.resolve(typeName, owner);
            if (resolved == null || resolved.isEmpty() || resolved.indexOf('.') < 0) {
                continue;
            }
            index.addReference(ReferenceKey.ofClass(resolved), new ReferenceSite(
                    ownerFqn, callerMethod, file, -1, ReferenceSite.Kind.ANNOTATION));
        }
    }

    private String sourceFileOf(String fqn) {
        if (classIndex == null) {
            return "";
        }
        File f = classIndex.source(fqn).orElse(null);
        return f != null ? f.getPath() : "";
    }

    private static String stripGenericsAndArrays(String type) {
        String s = type;
        int lt = s.indexOf('<');
        if (lt >= 0) {
            s = s.substring(0, lt);
        }
        while (s.endsWith("[]")) {
            s = s.substring(0, s.length() - 2);
        }
        // varargs マーク
        if (s.endsWith("...")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private static boolean isPrimitive(String type) {
        switch (type) {
            case "void":
            case "boolean":
            case "byte":
            case "short":
            case "int":
            case "long":
            case "float":
            case "double":
            case "char":
                return true;
            default:
                return false;
        }
    }
}
