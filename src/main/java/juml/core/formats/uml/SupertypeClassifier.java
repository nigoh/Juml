// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 継承先 (extends/implements 先) の型参照が、プロジェクト内クラスか、JDK 標準ライブラリか、
 * その他の外部ライブラリかを判定するユーティリティ。
 *
 * <p>判定手順:</p>
 * <ol>
 *   <li>{@link TypeRefResolver} があれば型参照を FQN に解決する (無ければドット入りの参照を FQN とみなす)。</li>
 *   <li>FQN がプロジェクト既知クラス集合 ({@code knownQns}) に含まれれば {@link Kind#PROJECT}。</li>
 *   <li>パッケージが {@code java.}/{@code javax.} 始まりなら {@link Kind#STANDARD}
 *       ({@code distinguishStd=false} のときは外部扱い)。</li>
 *   <li>パッケージが外部 prefix セットに前方一致すれば {@link Kind#EXTERNAL}。</li>
 *   <li>いずれにも当たらない (パッケージ不明な単純名など) は {@link Kind#UNKNOWN}。</li>
 * </ol>
 *
 * @see ExternalPackageMatcher
 */
public final class SupertypeClassifier {

    /** 継承先クラスの由来区分。 */
    public enum Kind {
        /** プロジェクト内で定義されているクラス。 */
        PROJECT,
        /** JDK 標準ライブラリ ({@code java.*} / {@code javax.*})。 */
        STANDARD,
        /** その他の外部ライブラリ ({@code android.*} / {@code kotlin.*} など)。 */
        EXTERNAL,
        /** 判定不能 (パッケージ不明な単純名など)。 */
        UNKNOWN
    }

    /** JDK 標準ライブラリと判定する prefix セット。 */
    public static final Set<String> STANDARD_PREFIXES;
    static {
        Set<String> s = new LinkedHashSet<>();
        s.add("java.");
        s.add("javax.");
        STANDARD_PREFIXES = Collections.unmodifiableSet(s);
    }

    /** 判定結果と解決済み FQN を保持する。 */
    public static final class Result {
        public final Kind kind;
        /** 解決済み FQN。解決できなかった場合は入力型参照のまま。 */
        public final String fqn;

        Result(Kind kind, String fqn) {
            this.kind = kind;
            this.fqn = fqn;
        }
    }

    private SupertypeClassifier() {
    }

    /**
     * 型参照を分類する。
     *
     * @param typeRef         継承先の型参照 (単純名 or FQN, ジェネリクスは内部で除去)
     * @param owner           解決の起点となるクラス (resolver 利用時のコンテキスト)
     * @param resolver        FQN 解決器。null 可
     * @param knownQns        プロジェクト既知クラスの FQN 集合
     * @param externalPrefixes 外部ライブラリ判定 prefix。null/空なら
     *                         {@link ExternalPackageMatcher#DEFAULT_PREFIXES}
     * @param distinguishStd  true なら {@code java.}/{@code javax.} を STANDARD として区別する
     * @return 判定結果 (kind + 解決済み FQN)
     */
    public static Result classify(String typeRef, JavaClassInfo owner,
                                  TypeRefResolver resolver, Set<String> knownQns,
                                  Set<String> externalPrefixes, boolean distinguishStd) {
        if (typeRef == null || typeRef.isEmpty()) {
            return new Result(Kind.UNKNOWN, typeRef);
        }
        String ref = stripGenerics(typeRef).trim();
        while (ref.endsWith("[]")) {
            ref = ref.substring(0, ref.length() - 2).trim();
        }
        if (ref.isEmpty()) {
            return new Result(Kind.UNKNOWN, typeRef);
        }
        String fqn = ref;
        if (resolver != null) {
            String resolved = resolver.resolveFqn(ref, owner);
            if (resolved != null && !resolved.isEmpty()) {
                fqn = resolved;
            }
        }
        if (knownQns != null && knownQns.contains(fqn)) {
            return new Result(Kind.PROJECT, fqn);
        }
        int dot = fqn.lastIndexOf('.');
        if (dot < 0) {
            // パッケージ不明な単純名は判定できない (従来通り暗黙ノード扱い)
            return new Result(Kind.UNKNOWN, fqn);
        }
        String pkg = fqn.substring(0, dot);
        if (distinguishStd && ExternalPackageMatcher.isExternal(pkg, STANDARD_PREFIXES)) {
            return new Result(Kind.STANDARD, fqn);
        }
        Set<String> prefixes = (externalPrefixes == null || externalPrefixes.isEmpty())
                ? ExternalPackageMatcher.DEFAULT_PREFIXES : externalPrefixes;
        if (ExternalPackageMatcher.isExternal(pkg, prefixes)) {
            return new Result(Kind.EXTERNAL, fqn);
        }
        return new Result(Kind.UNKNOWN, fqn);
    }

    private static String stripGenerics(String type) {
        int lt = type.indexOf('<');
        return lt < 0 ? type : type.substring(0, lt);
    }
}
