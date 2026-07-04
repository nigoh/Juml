// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 「共通クラス図 (Common Classes Diagram)」用の PlantUML テキスト生成器。
 *
 * <p>プロジェクト内のクラス群から、他クラスから参照される回数 (fan-in) が多い
 * クラスを「共通 (= 多くの場所で使い回されている) クラス」として抽出し、
 * 上位 N 件を中心にしたクラス図を出力する。</p>
 *
 * <p>fan-in の対象とする参照種別:
 * <ul>
 *   <li>{@code extends} の親クラス</li>
 *   <li>{@code implements} のインタフェース</li>
 *   <li>フィールド型 (ジェネリクス内も再帰的に走査)</li>
 *   <li>メソッドの引数型 / 戻り値型</li>
 * </ul>
 * </p>
 *
 * <p>外部ライブラリ ({@link JavaClassInfo.Origin#EXTERNAL_JAR} /
 * {@link JavaClassInfo.Origin#MISSING_JAR}、または {@link ExternalPackageMatcher#DEFAULT_PREFIXES}
 * 前方一致) は集計対象から除外する。</p>
 */
public final class PlantUmlCommonClassesDiagram {

    /** 出力オプション。 */
    public static class Options {
        /** タイトル。null/空で省略。 */
        public String title;
        /** 凡例ブロックを末尾に出す。 */
        public boolean includeLegend = true;
        /** 上位何件のクラスを「共通クラス」として表示するか。0 以下で 20 を既定値に使う。 */
        public int topN = 20;
        /** 各共通クラスの参照元クラスを最大何件まで表示するか。0 で参照元は出さない。 */
        public int referrersPerClass = 5;
        /** 参照回数がこの値未満のクラスは除外する。1 で全件 (1 回でも参照されていれば対象)。 */
        public int minReferences = 2;
        /** 外部ライブラリ (java.*, android.* 等) を集計対象から除外する。 */
        public boolean excludeExternalLibraries = true;
        /** 外部ライブラリ判定用パッケージ prefix。null/空なら {@link ExternalPackageMatcher#DEFAULT_PREFIXES}。 */
        public Set<String> externalPackagePrefixes =
                new LinkedHashSet<>(ExternalPackageMatcher.DEFAULT_PREFIXES);
    }

    /** 1 件の集計結果 (テストや GUI ステータス表示で再利用しやすいよう公開構造体)。 */
    public static final class Entry {
        private final JavaClassInfo target;
        private final int referenceCount;
        private final List<JavaClassInfo> referrers;

        Entry(JavaClassInfo target, int referenceCount, List<JavaClassInfo> referrers) {
            this.target = target;
            this.referenceCount = referenceCount;
            this.referrers = Collections.unmodifiableList(new ArrayList<>(referrers));
        }

        public JavaClassInfo getTarget() {
            return target;
        }

        public int getReferenceCount() {
            return referenceCount;
        }

        public List<JavaClassInfo> getReferrers() {
            return referrers;
        }
    }

    private PlantUmlCommonClassesDiagram() {
    }

    /** デフォルト Options で生成。 */
    public static String generate(List<JavaClassInfo> classes) {
        return generate(classes, null);
    }

    /** オプション付き生成。 */
    public static String generate(List<JavaClassInfo> classes, Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        Options o = opts != null ? opts : new Options();
        int topN = o.topN <= 0 ? 20 : o.topN;
        int minRefs = Math.max(1, o.minReferences);

        List<Entry> entries = analyze(classes, o);
        // 上位 N + minRefs フィルタ
        List<Entry> hot = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            if (e.referenceCount < minRefs) {
                break;
            }
            hot.add(e);
            if (hot.size() >= topN) {
                break;
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            // title も他の生成器と同様にエスケープする。生のままだと改行で描画が
            // ハードエラーになり、< が creole 記法として解釈されて消える。
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        } else {
            out.append("title Common Classes (top ").append(hot.size()).append(")\n");
        }
        // 可視性は記号より視認性の高いカラーアイコンで描画する (PlantUmlClassDiagram と統一)。
        VisibilityIconStyle.appendSkinparams(out, true);
        out.append("top to bottom direction\n");

        if (hot.isEmpty()) {
            out.append("note as N1\n");
            out.append("  No common classes found.\n");
            out.append("  (no class is referenced ").append(minRefs)
                    .append(" or more time(s) by other classes)\n");
            out.append("end note\n");
            if (o.includeLegend) {
                emitLegend(out, minRefs);
            }
            out.append("@enduml\n");
            return out.toString();
        }

        // alias 発行: ハブ (共通クラス) + 参照元クラス
        Map<String, String> aliasByQn = new LinkedHashMap<>();
        Map<String, JavaClassInfo> classByQn = new LinkedHashMap<>();
        int seq = 0;
        for (Entry e : hot) {
            String qn = e.target.getQualifiedName();
            aliasByQn.put(qn, "H" + seq++);
            classByQn.put(qn, e.target);
        }
        int maxRefs = o.referrersPerClass;
        for (Entry e : hot) {
            if (maxRefs <= 0) {
                break;
            }
            int shown = 0;
            for (JavaClassInfo r : e.referrers) {
                if (shown >= maxRefs) {
                    break;
                }
                String qn = r.getQualifiedName();
                if (!aliasByQn.containsKey(qn)) {
                    aliasByQn.put(qn, "R" + seq++);
                    classByQn.put(qn, r);
                }
                shown++;
            }
        }

        // ハブ (共通クラス) を強調 (背景色) 付きで出力
        for (Entry e : hot) {
            String qn = e.target.getQualifiedName();
            emitHubClass(out, e.target, aliasByQn.get(qn), e.referenceCount);
        }
        // 参照元クラスを通常スタイルで出力
        Set<String> hubQns = new HashSet<>();
        for (Entry e : hot) {
            hubQns.add(e.target.getQualifiedName());
        }
        for (Map.Entry<String, JavaClassInfo> me : classByQn.entrySet()) {
            if (hubQns.contains(me.getKey())) {
                continue;
            }
            emitReferrerClass(out, me.getValue(), aliasByQn.get(me.getKey()));
        }

        // 参照元 → ハブ の矢印 (破線、利用関係)
        Set<String> emitted = new LinkedHashSet<>();
        for (Entry e : hot) {
            String hubAlias = aliasByQn.get(e.target.getQualifiedName());
            int shown = 0;
            for (JavaClassInfo r : e.referrers) {
                if (maxRefs > 0 && shown >= maxRefs) {
                    break;
                }
                String refAlias = aliasByQn.get(r.getQualifiedName());
                if (refAlias == null) {
                    continue;
                }
                String key = refAlias + "->" + hubAlias;
                if (emitted.add(key)) {
                    out.append(refAlias).append(" ..> ").append(hubAlias)
                            .append(" : uses\n");
                }
                shown++;
            }
        }

        if (o.includeLegend) {
            emitLegend(out, minRefs);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * 入力クラスを走査して fan-in 集計を返す (降順、同点は QN 昇順)。
     * GUI 側で件数表示などに転用しやすいよう public。
     */
    public static List<Entry> analyze(List<JavaClassInfo> classes, Options opts) {
        Options o = opts != null ? opts : new Options();
        if (classes == null || classes.isEmpty()) {
            return Collections.emptyList();
        }

        // 対象クラス集合 (外部ライブラリは集計対象から除外)
        Map<String, JavaClassInfo> byQn = new LinkedHashMap<>();
        Map<String, String> qnBySimple = new HashMap<>();
        for (JavaClassInfo c : classes) {
            if (isExcluded(c, o)) {
                continue;
            }
            byQn.put(c.getQualifiedName(), c);
            qnBySimple.putIfAbsent(c.getSimpleName(), c.getQualifiedName());
        }
        Set<String> knownNames = byQn.keySet();
        // 型解決の接尾辞索引を 1 度だけ構築 (参照ごとに knownNames を全走査しない)。
        KnownTypeIndex knownIdx = new KnownTypeIndex(knownNames);

        // 各ターゲット QN → 参照元クラスのリスト
        Map<String, Set<String>> referrersByQn = new LinkedHashMap<>();
        for (JavaClassInfo c : classes) {
            if (isExcluded(c, o)) {
                continue;
            }
            String srcQn = c.getQualifiedName();
            for (String type : collectReferencedTypes(c)) {
                String target = resolveKnown(type, knownIdx, qnBySimple);
                if (target == null || target.equals(srcQn)) {
                    continue;
                }
                referrersByQn.computeIfAbsent(target, k -> new LinkedHashSet<>()).add(srcQn);
            }
        }

        List<Entry> result = new ArrayList<>(referrersByQn.size());
        for (Map.Entry<String, Set<String>> me : referrersByQn.entrySet()) {
            JavaClassInfo target = byQn.get(me.getKey());
            if (target == null) {
                continue;
            }
            List<JavaClassInfo> referrers = new ArrayList<>(me.getValue().size());
            for (String rqn : me.getValue()) {
                JavaClassInfo r = byQn.get(rqn);
                if (r != null) {
                    referrers.add(r);
                }
            }
            result.add(new Entry(target, referrers.size(), referrers));
        }

        result.sort(Comparator
                .comparingInt((Entry e) -> -e.referenceCount)
                .thenComparing(e -> e.target.getQualifiedName()));
        return result;
    }

    private static boolean isExcluded(JavaClassInfo c, Options o) {
        if (c == null) {
            return true;
        }
        if (!o.excludeExternalLibraries) {
            return false;
        }
        JavaClassInfo.Origin origin = c.getOrigin();
        if (origin == JavaClassInfo.Origin.EXTERNAL_JAR
                || origin == JavaClassInfo.Origin.MISSING_JAR) {
            return true;
        }
        return ExternalPackageMatcher.isExternal(c.getPackageName(), o.externalPackagePrefixes);
    }

    /** クラスが参照する型名 (生文字列) を集める。重複は許容 (呼出側で resolve)。 */
    private static List<String> collectReferencedTypes(JavaClassInfo c) {
        List<String> refs = new ArrayList<>();
        if (c.getSuperClass() != null) {
            refs.add(c.getSuperClass());
        }
        if (c.getInterfaces() != null) {
            refs.addAll(c.getInterfaces());
        }
        if (c.getFields() != null) {
            for (JavaFieldInfo f : c.getFields()) {
                if (f.getType() != null) {
                    refs.add(f.getType());
                }
            }
        }
        if (c.getMethods() != null) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.getReturnType() != null) {
                    refs.add(m.getReturnType());
                }
                if (m.getParameterTypes() != null) {
                    refs.addAll(m.getParameterTypes());
                }
            }
        }
        return refs;
    }

    /**
     * 型文字列を既知クラス名 (FQN) に解決する。
     * ジェネリクスや配列の装飾を剥がして、完全修飾名 → 単純名の順で検索する。
     */
    private static String resolveKnown(String typeRef, KnownTypeIndex knownNames,
                                       Map<String, String> qnBySimple) {
        if (typeRef == null || typeRef.isEmpty()) {
            return null;
        }
        // 完全修飾名 / 単純名のどちらでも資料できるよう、まずは pickUsageTarget で 1 段
        // 取り出す (Map<String, Foo> → Foo)。
        String picked = PlantUmlClassRelations.pickUsageTarget(typeRef, knownNames);
        if (picked != null) {
            if (knownNames.containsExact(picked)) {
                return picked;
            }
        }
        // fallback: 配列/ジェネリクスを剥がして単純名で検索
        String base = typeRef.replaceAll("\\[\\]", "").trim();
        int lt = base.indexOf('<');
        if (lt >= 0) {
            base = base.substring(0, lt).trim();
        }
        if (knownNames.containsExact(base)) {
            return base;
        }
        int dot = base.lastIndexOf('.');
        String simple = dot >= 0 ? base.substring(dot + 1) : base;
        String qn = qnBySimple.get(simple);
        if (qn != null && knownNames.containsExact(qn)) {
            return qn;
        }
        return null;
    }

    private static void emitHubClass(StringBuilder out, JavaClassInfo c,
                                      String alias, int refCount) {
        out.append("class \"").append(escape(displayName(c)))
                .append("\\n<size:11><color:#666666>")
                .append(refCount).append(" refs</color></size>\" as ")
                .append(alias).append(" <<common>> #LightGoldenRodYellow");
        out.append(" {\n}\n");
    }

    private static void emitReferrerClass(StringBuilder out, JavaClassInfo c, String alias) {
        out.append("class \"").append(escape(displayName(c)))
                .append("\" as ").append(alias).append(" {\n}\n");
    }

    private static String displayName(JavaClassInfo c) {
        if (c.getPackageName() == null || c.getPackageName().isEmpty()) {
            return c.getSimpleName();
        }
        return c.getSimpleName() + "\\n<size:10>" + c.getPackageName() + "</size>";
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private static void emitLegend(StringBuilder out, int minRefs) {
        out.append("legend top left\n");
        out.append("== 共通クラス図 ==\n");
        out.append("  多くのクラスから参照されるクラス (fan-in)\n");
        out.append("  黄色ボックス: 共通/共有クラス\n");
        out.append("  破線矢印: 参照元 ..> 共通クラス (\"uses\")\n");
        out.append("  閾値: ").append(minRefs).append("+ 回参照されるクラス\n");
        out.append("endlegend\n");
    }
}
