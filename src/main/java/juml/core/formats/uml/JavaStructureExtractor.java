// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import juml.core.formats.java.jp.JavaParserFrontend;
import juml.core.formats.java.jp.JpSolver;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java ソースから {@link JavaClassInfo} のリストを抽出するパーサのファサード。
 *
 * <p>解析は {@link JavaParserFrontend} (JavaParser + JavaSymbolSolver) に委譲する。
 * クラス/インタフェース/enum/@interface/record/module とその内部のフィールド・メソッド・
 * コンストラクタを構造化データとして返し、メソッド本体内の呼び出しも
 * {@link JavaMethodInfo#getCalls()} に記録する (シーケンス図・逆参照に利用)。</p>
 */
public final class JavaStructureExtractor {

    private JavaStructureExtractor() {
    }

    /** Java ソースから ClassInfo のリストを返す。 */
    public static List<JavaClassInfo> extract(String source) {
        return extract(source, null, null);
    }

    /** エラーリスナー付き。 */
    public static List<JavaClassInfo> extract(String source, ErrorListener listener) {
        return extract(source, listener, null);
    }

    /**
     * {@code solver} を渡すと呼び出し先をシンボル解決して
     * {@link JavaMethodInfo.Call#getResolvedOwnerFqn()} を埋める (FULL 解析時のみ)。
     */
    public static List<JavaClassInfo> extract(String source, ErrorListener listener,
            JpSolver solver) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        return JavaParserFrontend.parse(
                source, false, listener != null ? listener : ErrorListener.silent(), solver);
    }

    /**
     * ヘッダ情報のみ (package / simpleName / kind / modifiers / superClass / interfaces /
     * enclosingClass / アノテーション名 / module directive) を抽出する軽量モード。
     *
     * <p>fields / methods / comments / enumConstants は破棄され、各 ClassInfo の
     * {@link JavaClassInfo#isDetailed()} は false。本体の statement tree は構築しないため
     * 大規模プロジェクトでも軽い。詳細が必要になったら {@link ClassIndex#detail} で対象クラスだけ
     * Stage B 化する。</p>
     */
    public static List<JavaClassInfo> extractHeadersOnly(String source, ErrorListener listener) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }
        List<JavaClassInfo> full = JavaParserFrontend.parse(
                source, true, listener != null ? listener : ErrorListener.silent(), null);
        List<JavaClassInfo> headers = new ArrayList<>(full.size());
        for (JavaClassInfo c : full) {
            JavaClassInfo h = new JavaClassInfo();
            h.setPackageName(c.getPackageName());
            h.setSimpleName(c.getSimpleName());
            h.setKind(c.getKind());
            h.getImports().addAll(c.getImports());
            h.getModifiers().addAll(c.getModifiers());
            h.getAnnotations().addAll(c.getAnnotations());
            h.setSuperClass(c.getSuperClass());
            h.getInterfaces().addAll(c.getInterfaces());
            // sealed の permits は Stage A でも保持する (modifiers の "sealed" だけでなく
            // 「誰を許可するか」を参照する用途があるため漏らさない)。
            h.getPermittedTypes().addAll(c.getPermittedTypes());
            h.setEnclosingClass(c.getEnclosingClass());
            // 宣言開始行は Stage A でも保持する。落とすと未昇格クラスのソースジャンプが
            // 行 0 (ファイル先頭) に着地してしまう (DiagramTabSupport.classStartLine)。
            h.setStartLine(c.getStartLine());
            h.setAaosCategory(c.getAaosCategory());
            h.setAndroidComponentType(c.getAndroidComponentType());
            // 型パラメータ宣言 ("T extends Entity" など) は header-only でも保持する
            // (クラス図の型パラメータ表示に必要なため)。
            h.setTypeParameters(c.getTypeParameters());
            // モジュール宣言の directive は header-only モードでも保持する
            // (モジュールグラフ系の集計に必要なため)。
            h.getModuleDirectives().addAll(c.getModuleDirectives());
            // fields / methods / enumConstants / comment は破棄
            h.setDetailed(false);
            headers.add(h);
        }
        return Collections.unmodifiableList(headers);
    }
}
