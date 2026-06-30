// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.java.jp;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.util.Collection;

/**
 * プロジェクト単位のシンボル解決器。ソースルート群 + JDK (Reflection) を束ねた
 * {@link CombinedTypeSolver} を保持し、その解決器を仕込んだ {@link JavaParser} を都度生成する。
 *
 * <p>{@link JavaParser} はスレッド非安全なのでワーカ毎に {@link #newParser()} で生成する。
 * {@link CombinedTypeSolver} は読み取り主体で共有可能。型解決によりチェーン呼び出し・
 * オーバーロード・継承・ジェネリクスを辿れる。</p>
 */
public final class JpSolver {

    private final CombinedTypeSolver typeSolver;

    private JpSolver(CombinedTypeSolver typeSolver) {
        this.typeSolver = typeSolver;
    }

    /** ソースルート群から解決器を構築する。ルートが空でも JDK 解決は効く。 */
    public static JpSolver fromSourceRoots(Collection<File> sourceRoots) {
        CombinedTypeSolver combined = new CombinedTypeSolver();
        combined.add(new ReflectionTypeSolver(false));
        if (sourceRoots != null) {
            for (File root : sourceRoots) {
                if (root != null && root.isDirectory()) {
                    try {
                        combined.add(new JavaParserTypeSolver(root));
                    } catch (RuntimeException ignore) {
                        // 不正なルートはスキップ
                    }
                }
            }
        }
        return new JpSolver(combined);
    }

    /** シンボル解決器を仕込んだ新しい JavaParser を返す (ワーカ毎に呼ぶ)。 */
    JavaParser newParser() {
        ParserConfiguration cfg = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        return new JavaParser(cfg);
    }
}
