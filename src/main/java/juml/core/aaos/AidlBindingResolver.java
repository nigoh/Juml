// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import juml.core.formats.uml.JavaClassInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AIDL インタフェース ({@code Kind.AIDL_INTERFACE}) と、その {@code Stub} を継承する
 * Java 実装クラスとの対応を解決する。
 *
 * <p>解決ロジック:</p>
 * <ol>
 *   <li>入力クラス群から AIDL インタフェース (Kind = AIDL_INTERFACE) を抽出</li>
 *   <li>同じく入力クラス群から {@code extends X.Stub} 形式の親クラスを持つクラスを抽出</li>
 *   <li>{@code X} の単純名が AIDL インタフェース名と一致するものを紐付け</li>
 *   <li>AIDL の {@code import} 文を活用してパッケージマッチも試行</li>
 * </ol>
 *
 * <p>結果は AIDL FQN → 実装クラス FQN のリスト形式で返す。</p>
 */
public final class AidlBindingResolver {

    /**
     * クラス群から AIDL 実装紐付けを解決する。
     *
     * @param classes Stage A/B どちらでも可。{@code superClass} と {@code kind} を見る
     * @return AIDL インタフェース FQN → 実装クラス {@link AidlBinding} リスト (順序保持)
     */
    public Map<String, List<AidlBinding>> resolve(Collection<JavaClassInfo> classes) {
        Map<String, List<AidlBinding>> out = new LinkedHashMap<>();
        if (classes == null || classes.isEmpty()) {
            return out;
        }

        // 1. AIDL インタフェース一覧 (simpleName → FQN リスト)。
        //    同名 AIDL (a.IFoo と b.IFoo) の衝突を潰さないよう、単純名ごとに候補を保持する。
        Map<String, List<String>> aidlBySimpleName = new LinkedHashMap<>();
        Set<String> aidlFqnSet = new LinkedHashSet<>();
        for (JavaClassInfo c : classes) {
            if (c == null || c.getKind() != JavaClassInfo.Kind.AIDL_INTERFACE) {
                continue;
            }
            String fqn = c.getQualifiedName();
            aidlBySimpleName.computeIfAbsent(c.getSimpleName(), k -> new ArrayList<>())
                    .add(fqn);
            aidlFqnSet.add(fqn);
            out.put(fqn, new ArrayList<>());
        }

        if (aidlBySimpleName.isEmpty()) {
            return out;
        }

        // 2. extends ...Stub を探して紐付け
        for (JavaClassInfo c : classes) {
            if (c == null || c.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
                continue;
            }
            String parent = c.getSuperClass();
            if (parent == null || parent.isEmpty()) {
                continue;
            }
            // "IFoo.Stub" / "com.x.IFoo.Stub" / "IFoo.Stub<T>" を許容
            String stripped = stripGenerics(parent);
            if (!stripped.endsWith(".Stub")) {
                continue;
            }
            String aidlPart = stripped.substring(0, stripped.length() - ".Stub".length());
            String aidlFqn = matchAidl(aidlPart, aidlBySimpleName, aidlFqnSet, c);
            if (aidlFqn == null) {
                continue;
            }
            AidlBinding binding = new AidlBinding(aidlFqn, c.getQualifiedName(), "");
            out.get(aidlFqn).add(binding);
        }
        return out;
    }

    /**
     * {@code IFoo} / {@code com.x.IFoo} から該当 AIDL FQN を引く。
     * - FQN なら直接マッチ
     * - 単純名で候補が 1 つならそれを採用
     * - 単純名で複数候補 (同名 AIDL 衝突) なら、実装クラスの imports で絞り込み、
     *   絞り込めなければ先頭を best-effort 採用
     */
    private String matchAidl(String aidlPart, Map<String, List<String>> aidlBySimpleName,
                              Set<String> aidlFqnSet, JavaClassInfo impl) {
        if (aidlPart.isEmpty()) {
            return null;
        }
        if (aidlPart.indexOf('.') >= 0) {
            // FQN: そのまま一致を確認
            if (aidlFqnSet.contains(aidlPart)) {
                return aidlPart;
            }
            // simpleName 部分だけ取り出してフォールバック
            int dot = aidlPart.lastIndexOf('.');
            return firstOf(aidlBySimpleName.get(aidlPart.substring(dot + 1)));
        }
        List<String> candidates = aidlBySimpleName.get(aidlPart);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        // 複数候補: imports で「どのパッケージの IFoo か」を絞り込む
        for (String imp : impl.getImports()) {
            String body = imp.startsWith("static ") ? imp.substring(7) : imp;
            if ((body.endsWith("." + aidlPart) || body.equals(aidlPart))
                    && candidates.contains(body)) {
                return body;
            }
        }
        // 絞り込めなければ先頭を best-effort 採用
        return candidates.get(0);
    }

    private static String firstOf(List<String> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    private static String stripGenerics(String type) {
        int lt = type.indexOf('<');
        return lt < 0 ? type.trim() : type.substring(0, lt).trim();
    }
}
