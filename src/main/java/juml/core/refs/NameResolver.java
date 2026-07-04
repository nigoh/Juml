// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.refs;

import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.DependencyJarIndex;
import juml.core.formats.uml.JavaClassInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 単純名 (例: {@code MyClass}) を完全修飾名 (例: {@code com.foo.MyClass}) に解決する。
 *
 * <p>優先順:</p>
 * <ol>
 *   <li>すでに FQN なら ({@code .} を含み、先頭が小文字パッケージ風) そのまま返す</li>
 *   <li>同じファイルの import 文と一致するもの</li>
 *   <li>同パッケージ ({@code packageName + "." + simpleName}) が {@link ClassIndex} に存在するもの</li>
 *   <li>ワイルドカード import (例: {@code java.util.*}) のいずれかと組み合わせて存在するもの</li>
 *   <li>{@link DependencyJarIndex#resolve(String)} で JAR 索引から拾えるもの</li>
 *   <li>解決失敗時は単純名そのものを返す</li>
 * </ol>
 *
 * <p>複数候補がある場合は最初に見つかったものを採用する。曖昧解決はログのみ。</p>
 */
public final class NameResolver {

    private final ClassIndex classIndex;
    private final DependencyJarIndex depIndex;

    public NameResolver(ClassIndex classIndex, DependencyJarIndex depIndex) {
        this.classIndex = classIndex;
        this.depIndex = depIndex;
    }

    /**
     * 指定クラスのコンテキスト ({@link JavaClassInfo#getPackageName()} と
     * {@link JavaClassInfo#getImports()}) を使って単純名を FQN に解決する。
     *
     * @param simpleOrFqn 単純名もしくは既に FQN らしいもの
     * @param owner 解決の起点となるクラス。null 不可。
     * @return 解決済み FQN。失敗時は入力をそのまま返す
     */
    public String resolve(String simpleOrFqn, JavaClassInfo owner) {
        if (simpleOrFqn == null || simpleOrFqn.isEmpty() || owner == null) {
            return simpleOrFqn;
        }
        String name = stripGenerics(simpleOrFqn).trim();
        if (name.isEmpty()) {
            return simpleOrFqn;
        }
        // Kotlin nullable マーカー {@code ?} を除去 (例: {@code Listener?} → {@code Listener})
        while (name.endsWith("?")) {
            name = name.substring(0, name.length() - 1).trim();
        }
        // 配列マークを除去
        while (name.endsWith("[]")) {
            name = name.substring(0, name.length() - 2);
        }
        if (name.isEmpty()) {
            return simpleOrFqn;
        }
        if (name.indexOf('.') >= 0) {
            if (classIndex != null && classIndex.header(name).isPresent()) {
                return name;
            }
            // 部分修飾のネスト型名 (Outer.Inner / Config.Item) を解決する。先頭セグメントを
            // 単純名として解決できたら、残りのネスト部を付け直す。これをしないと同パッケージの
            // ネスト型参照が FQN 化されず、参照インデックスに載らずに fan-in ゼロ (誤 dead-code)
            // になる。java.util.List のような真の FQN は head("java") が解決できず素通しする。
            int dot = name.indexOf('.');
            String head = name.substring(0, dot);
            if (!head.isEmpty() && head.indexOf('[') < 0) {
                String resolvedHead = resolveSimple(head, owner);
                if (!resolvedHead.equals(head) && resolvedHead.indexOf('.') >= 0) {
                    return resolvedHead + name.substring(dot);
                }
            }
            // 解決できなければおおむね FQN とみなしてそのまま返す。
            return name;
        }
        return resolveSimple(name, owner);
    }

    /** 単純名 (ドット無し) を FQN に解決する内部ヘルパ。 */
    private String resolveSimple(String simple, JavaClassInfo owner) {
        // 1. import 完全一致
        for (String imp : owner.getImports()) {
            String body = imp.startsWith("static ") ? imp.substring(7) : imp;
            if (body.endsWith("." + simple)) {
                return body;
            }
        }
        // 2. 同パッケージ
        String pkg = owner.getPackageName();
        if (pkg != null && !pkg.isEmpty()) {
            String candidate = pkg + "." + simple;
            if (classIndex != null && classIndex.header(candidate).isPresent()) {
                return candidate;
            }
        }
        // 3. ワイルドカード import (java.util.* など) と組み合わせる
        for (String imp : owner.getImports()) {
            String body = imp.startsWith("static ") ? imp.substring(7) : imp;
            if (body.endsWith(".*")) {
                String prefix = body.substring(0, body.length() - 1); // "java.util."
                String candidate = prefix + simple;
                if (classIndex != null && classIndex.header(candidate).isPresent()) {
                    return candidate;
                }
            }
        }
        // 4. DependencyJarIndex で simpleName → FQN を引く
        if (depIndex != null) {
            String resolved = depIndex.resolve(simple)
                    .map(JavaClassInfo::getQualifiedName)
                    .orElse(null);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        }
        // 5. java.lang.* の特例 (常時 import 扱い)
        String javaLang = "java.lang." + simple;
        if (classIndex != null && classIndex.header(javaLang).isPresent()) {
            return javaLang;
        }
        return simple;
    }

    /**
     * 同パッケージとワイルドカード import から候補となる全 FQN を列挙する
     * (曖昧解決の診断用)。返値は単純名で見つかった候補。
     */
    public List<String> candidates(String simple, JavaClassInfo owner) {
        Set<String> out = new LinkedHashSet<>();
        if (simple == null || simple.isEmpty() || owner == null) {
            return new ArrayList<>(out);
        }
        for (String imp : owner.getImports()) {
            String body = imp.startsWith("static ") ? imp.substring(7) : imp;
            if (body.endsWith("." + simple)) {
                out.add(body);
            }
        }
        String pkg = owner.getPackageName();
        if (pkg != null && !pkg.isEmpty()) {
            String c = pkg + "." + simple;
            if (classIndex != null && classIndex.header(c).isPresent()) {
                out.add(c);
            }
        }
        for (String imp : owner.getImports()) {
            String body = imp.startsWith("static ") ? imp.substring(7) : imp;
            if (body.endsWith(".*")) {
                String prefix = body.substring(0, body.length() - 1);
                String c = prefix + simple;
                if (classIndex != null && classIndex.header(c).isPresent()) {
                    out.add(c);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /** 型表記からジェネリクス {@code <...>} を取り除く。 */
    private static String stripGenerics(String type) {
        int lt = type.indexOf('<');
        if (lt < 0) {
            return type;
        }
        return type.substring(0, lt);
    }
}
