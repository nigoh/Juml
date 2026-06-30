// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * クラスのパッケージ名が「外部ライブラリ」由来かどうかを判定するユーティリティ。
 *
 * <p>{@link JavaClassInfo#getOrigin()} だけでは捕捉できない、ソース内で参照しただけの
 * {@code java.*} / {@code android.*} などの暗黙の外部クラスを、パッケージ prefix で
 * 補完的に判定するために使う。</p>
 */
public final class ExternalPackageMatcher {

    /** 既定の外部ライブラリ判定 prefix セット。 */
    public static final Set<String> DEFAULT_PREFIXES;
    static {
        Set<String> s = new LinkedHashSet<>();
        s.add("java.");
        s.add("javax.");
        s.add("android.");
        s.add("androidx.");
        s.add("kotlin.");
        s.add("kotlinx.");
        s.add("org.jetbrains.");
        s.add("dagger.");
        s.add("com.google.");
        s.add("org.junit.");
        s.add("org.mockito.");
        DEFAULT_PREFIXES = Collections.unmodifiableSet(s);
    }

    /**
     * パッケージ {@code pkg} が {@code prefixes} のいずれかに前方一致するか。
     * {@code prefixes} が空または null の場合は {@link #DEFAULT_PREFIXES} を使う。
     * パッケージ自体が null/空の場合は常に false。
     */
    public static boolean isExternal(String pkg, Set<String> prefixes) {
        if (pkg == null || pkg.isEmpty()) {
            return false;
        }
        String normalized = pkg.endsWith(".") ? pkg : pkg + ".";
        Set<String> use = (prefixes == null || prefixes.isEmpty())
                ? DEFAULT_PREFIXES : prefixes;
        for (String pre : use) {
            if (pre == null || pre.isEmpty()) {
                continue;
            }
            if (normalized.startsWith(pre)) {
                return true;
            }
        }
        return false;
    }

    private ExternalPackageMatcher() {
    }
}
