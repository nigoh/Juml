// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * クラス図の関係線 (継承/実装/利用) の出力と、型参照から利用対象クラスを推定する
 * 補助ロジックを集約する。{@link PlantUmlClassDiagram} 本体から関係性の解決を切り離す。
 * 型参照ユーティリティ ({@link #pickUsageTarget}) は他の図生成器からも再利用される。
 */
final class PlantUmlClassRelations {

    private PlantUmlClassRelations() {
    }

    /** 色分け時の継承 (extends) 線の色。型階層の背骨として緑で強調する。 */
    static final String INHERIT_COLOR = "#2E7D32";
    /** 色分け時の実装 (implements) 線の色。継承と区別できるよう青にする。 */
    static final String REALIZE_COLOR = "#1565C0";
    /** 色分け時の利用 (依存) 線の色。継承/実装より後退させるため淡い灰にする。 */
    static final String USAGE_COLOR = "#9E9E9E";

    /**
     * 継承（実線・中空三角）の矢印トークンを返す。色分け時は {@link #INHERIT_COLOR} で着色。
     * 形は {@code parent <|-- child}（左が親）を保つ。
     */
    private static String inheritArrow(PlantUmlClassDiagram.Options o, boolean dim) {
        if (dim) {
            return " -[" + PlantUmlClassFocus.DIM_EDGE + "]<|-- ";
        }
        return o.colorCodeRelations ? " -[" + INHERIT_COLOR + "]<|-- " : " <|-- ";
    }

    /**
     * 実装（破線・中空三角）の矢印トークンを返す。色分け時は {@link #REALIZE_COLOR} で着色。
     * {@code <|..} の {@code ..} が破線を表すため色のみ付与する。{@code dim} 時は焦点外として淡色化。
     */
    private static String realizeArrow(PlantUmlClassDiagram.Options o, boolean dim) {
        if (dim) {
            return " -[" + PlantUmlClassFocus.DIM_EDGE + "]<|.. ";
        }
        return o.colorCodeRelations ? " -[" + REALIZE_COLOR + "]<|.. " : " <|.. ";
    }

    /**
     * 利用（依存）の矢印トークンを返す。色分け時は {@link #USAGE_COLOR} の破線にして
     * UML の依存記法（破線の開いた矢印）に寄せ、継承/実装から視覚的に分離する。
     * {@code dim} 時は焦点外として淡色の破線にする。
     */
    private static String usageArrow(PlantUmlClassDiagram.Options o, boolean dim) {
        if (dim) {
            return " -[" + PlantUmlClassFocus.DIM_EDGE + ",dashed]-> ";
        }
        return o.colorCodeRelations ? " -[" + USAGE_COLOR + ",dashed]-> " : " --> ";
    }

    /** 継承/実装の型参照を図内の既知 QN に解決する (焦点エッジ判定用、未解決は null)。 */
    private static String resolveKnownQn(String typeRef,
                                          java.util.Map<String, String> aliasByQn,
                                          java.util.Map<String, String> qnBySimple) {
        if (typeRef == null || typeRef.isEmpty()) {
            return null;
        }
        if (aliasByQn.containsKey(typeRef)) {
            return typeRef;
        }
        String qn = qnBySimple.get(typeRef);
        return (qn != null && aliasByQn.containsKey(qn)) ? qn : null;
    }

    /**
     * 図に含まれない継承先 (extends/implements) のうち、標準/外部ライブラリと判定できるものを
     * {@code class "FQN" as EXTn <<standard|external>> #color} 形式で宣言出力し、
     * 「簡約型参照 → エイリアス」マップを返す。このマップは {@link #relationId} が
     * 暗黙ノード生成より優先して引く。
     */
    static java.util.Map<String, String> emitExternalSupertypes(
            StringBuilder out, List<JavaClassInfo> classes,
            PlantUmlClassDiagram.Options o,
            Set<String> knownNames,
            java.util.Map<String, String> aliasByQn,
            java.util.Map<String, String> qnBySimple) {
        // 簡約型参照 → 解決済み FQN (STANDARD/EXTERNAL のみ)。挿入順を保持。
        java.util.Map<String, SupertypeClassifier.Result> candidates =
                new java.util.LinkedHashMap<>();
        for (JavaClassInfo c : classes) {
            if (o.showInheritance) {
                considerExternalSupertype(c, c.getSuperClass(), o,
                        knownNames, aliasByQn, qnBySimple, candidates);
            }
            if (o.showImplementations) {
                for (String iface : c.getInterfaces()) {
                    considerExternalSupertype(c, iface, o,
                            knownNames, aliasByQn, qnBySimple, candidates);
                }
            }
        }
        if (candidates.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<String, String> aliasByRef = new java.util.LinkedHashMap<>();
        int seq = 0;
        for (java.util.Map.Entry<String, SupertypeClassifier.Result> e
                : candidates.entrySet()) {
            String alias = "EXT" + (seq++);
            aliasByRef.put(e.getKey(), alias);
            SupertypeClassifier.Result r = e.getValue();
            boolean std = r.kind == SupertypeClassifier.Kind.STANDARD;
            out.append("class ").append(PlantUmlClassDiagram.quoteId(r.fqn))
                    .append(" as ").append(alias)
                    .append(std ? " <<standard>> #LightCyan" : " <<external>> #Wheat")
                    .append('\n');
        }
        return aliasByRef;
    }

    /** 1 つの継承先型参照を分類し、STANDARD/EXTERNAL かつ未登録なら candidates に追加する。 */
    private static void considerExternalSupertype(
            JavaClassInfo owner, String typeRef, PlantUmlClassDiagram.Options o,
            Set<String> knownNames,
            java.util.Map<String, String> aliasByQn,
            java.util.Map<String, String> qnBySimple,
            java.util.Map<String, SupertypeClassifier.Result> candidates) {
        if (typeRef == null || typeRef.isEmpty()) {
            return;
        }
        String key = simplifyTypeRef(typeRef);
        if (key.isEmpty() || candidates.containsKey(key)) {
            return;
        }
        // 既にプロジェクト内クラスとして図に出ているものは対象外。
        if (aliasByQn.containsKey(key) || qnBySimple.containsKey(key)) {
            return;
        }
        SupertypeClassifier.Result r = SupertypeClassifier.classify(
                key, owner, o.supertypeResolver, knownNames,
                o.externalPackagePrefixes, o.distinguishStandardLibrary);
        if ((r.kind == SupertypeClassifier.Kind.STANDARD
                || r.kind == SupertypeClassifier.Kind.EXTERNAL)
                && !knownNames.contains(r.fqn)) {
            candidates.put(key, r);
        }
    }

    static void emitInheritance(StringBuilder out, JavaClassInfo c,
                                         PlantUmlClassDiagram.Options o,
                                         java.util.Map<String, String> aliasByQn,
                                         java.util.Map<String, String> qnBySimple,
                                         java.util.Map<String, String> externalAlias) {
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
        String cQn = c.getQualifiedName();
        if (o.showInheritance
                && c.getSuperClass() != null && !c.getSuperClass().isEmpty()) {
            String parentRef = simplifyTypeRef(c.getSuperClass());
            String parent = relationId(parentRef, aliasByQn, qnBySimple, externalAlias);
            boolean dim = PlantUmlClassFocus.dimEdge(o, cQn,
                    resolveKnownQn(parentRef, aliasByQn, qnBySimple));
            out.append(parent).append(inheritArrow(o, dim)).append(me).append('\n');
        }
        if (o.showImplementations) {
            // interface の extends は「インタフェース継承」なので実線 <|-- で描く。
            // class/enum/record の implements は実装線 <|.. (破線)。
            // (interface の getInterfaces() には extendedTypes が入る — TypeDeclAdapter 参照)
            boolean isIface = c.getKind() == JavaClassInfo.Kind.INTERFACE;
            // interfaces リストに同一型が重複していても (パース復旧や permits 併合で
            // 起こり得る) エッジは 1 本だけ出す。emitUsage と同じ重複排除方針。
            Set<String> emittedIface = new LinkedHashSet<>();
            for (String iface : c.getInterfaces()) {
                String ifaceRef = simplifyTypeRef(iface);
                String parent = relationId(ifaceRef, aliasByQn, qnBySimple, externalAlias);
                if (emittedIface.add(parent)) {
                    boolean dim = PlantUmlClassFocus.dimEdge(o, cQn,
                            resolveKnownQn(ifaceRef, aliasByQn, qnBySimple));
                    String arrow = isIface ? inheritArrow(o, dim) : realizeArrow(o, dim);
                    out.append(parent).append(arrow).append(me).append('\n');
                }
            }
        }
    }

    /**
     * ネストした型と外側の型を含有エッジ ({@code Outer +-- Inner}) で結ぶ。
     * 外側の型が図に含まれない場合 (alias 未登録) はエッジを描かない。
     */
    static void emitNesting(StringBuilder out, JavaClassInfo c,
                            java.util.Map<String, String> aliasByQn) {
        String enclosing = c.getEnclosingClass();
        if (enclosing == null || enclosing.isEmpty()) {
            return;
        }
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
        String pkg = c.getPackageName();
        String parentQn = (pkg == null || pkg.isEmpty()) ? enclosing : pkg + "." + enclosing;
        String parent = aliasByQn.get(parentQn);
        if (parent == null) {
            return;
        }
        out.append(parent).append(" +-- ").append(me).append('\n');
    }

    /**
     * topToBottomDirection 時に同一親を持つ兄弟ノードが横に広がりすぎるのを防ぐため、
     * {@link Options#maxSiblingsPerRow} 個ごとに隠しリンク ({@code -[hidden]->}) を挿入する。
     *
     * <p>グループ末尾 → 次グループ先頭 に hidden リンクを打つことで、
     * Graphviz/Smetana のランク割り当てが次グループを強制的に下のランクに押し出す。</p>
     */
    static void emitSiblingWrapHints(
            StringBuilder out, List<JavaClassInfo> classes,
            java.util.Map<String, String> aliasByQn,
            java.util.Map<String, String> qnBySimple,
            java.util.Map<String, String> externalAlias,
            PlantUmlClassDiagram.Options o) {
        // parent alias → 子エイリアスの順序リスト (extends のみ追跡)
        java.util.Map<String, List<String>> childrenByParent = new java.util.LinkedHashMap<>();
        for (JavaClassInfo c : classes) {
            if (!o.showInheritance) return;
            if (c.getSuperClass() == null || c.getSuperClass().isEmpty()) continue;
            String childAlias = aliasByQn.get(c.getQualifiedName());
            if (childAlias == null) continue;
            String parentId = relationId(simplifyTypeRef(c.getSuperClass()),
                    aliasByQn, qnBySimple, externalAlias);
            childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(childAlias);
        }
        for (List<String> siblings : childrenByParent.values()) {
            if (siblings.size() <= o.maxSiblingsPerRow) continue;
            int n = o.maxSiblingsPerRow;
            // グループ境界ごとに hidden リンクを打つ
            for (int i = n; i < siblings.size(); i += n) {
                out.append(siblings.get(i - 1)).append(" -[hidden]-> ")
                   .append(siblings.get(i)).append('\n');
            }
        }
    }

    static void emitUsage(StringBuilder out, JavaClassInfo c,
                                   KnownTypeIndex known,
                                   java.util.Map<String, String> aliasByQn,
                                   java.util.Map<String, String> qnBySimple,
                                   java.util.Map<String, String> externalAlias,
                                   PlantUmlClassDiagram.Options o) {
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
        // 利用関係の候補型: フィールド型に加え、メソッドの戻り値型・引数型も対象にする
        // (フィールドを持たず引数/戻り値だけで型を使うクラスの利用線を取りこぼさない)。
        List<String> typeRefs = new ArrayList<>();
        for (JavaFieldInfo f : c.getFields()) {
            typeRefs.add(f.getType());
        }
        for (JavaMethodInfo m : c.getMethods()) {
            if (!m.isConstructor()) {
                typeRefs.add(m.getReturnType());
            }
            typeRefs.addAll(m.getParameterTypes());
        }
        Set<String> emitted = new LinkedHashSet<>();
        int count = 0;
        for (String ref : typeRefs) {
            if (count >= o.maxUsagePerClass) {
                break;
            }
            String target = pickUsageTarget(ref, known);
            if (target == null || target.equals(c.getQualifiedName())
                    || target.equals(c.getSimpleName())) {
                continue;
            }
            String tid = relationId(target, aliasByQn, qnBySimple, externalAlias);
            // 自己参照スキップ
            if (tid.equals(me)) {
                continue;
            }
            if (emitted.add(tid)) {
                boolean dim = PlantUmlClassFocus.dimEdge(o, c.getQualifiedName(), target);
                out.append(me).append(usageArrow(o, dim)).append(tid).append('\n');
                count++;
            }
        }
    }

    /**
     * 関係性の片端の識別子を返す。
     * - 完全修飾名で既知ならそのエイリアス
     * - 単純名で既知なら対応する完全修飾名のエイリアス
     * - 既知ではないなら引用符付き名 (PlantUML が暗黙生成)
     */
    private static String relationId(String typeRef,
                                      java.util.Map<String, String> aliasByQn,
                                      java.util.Map<String, String> qnBySimple,
                                      java.util.Map<String, String> externalAlias) {
        if (typeRef == null || typeRef.isEmpty()) {
            return "\"?\"";
        }
        String alias = aliasByQn.get(typeRef);
        if (alias != null) {
            return alias;
        }
        String qn = qnBySimple.get(typeRef);
        if (qn != null) {
            String a = aliasByQn.get(qn);
            if (a != null) {
                return a;
            }
        }
        // 機能A: 標準/外部ライブラリの継承先として宣言済みなら、そのステレオタイプノードへ接続。
        if (externalAlias != null) {
            String ext = externalAlias.get(typeRef);
            if (ext != null) {
                return ext;
            }
        }
        // 未定義: PlantUML に暗黙作成させる。"a.b.C" 形式は namespace 扱いされうるため
        // 末尾の単純名のみを使う。
        String simple = typeRef;
        int lastDot = simple.lastIndexOf('.');
        if (lastDot >= 0) {
            simple = simple.substring(lastDot + 1);
        }
        return PlantUmlClassDiagram.quoteId(simple);
    }

    /**
     * 型参照 (たとえば {@code Map<String, Foo>}) から、利用対象となるユーザ定義型を推定する。
     * 既知クラス集合を都度走査する後方互換オーバーロード。ループ内で繰り返し呼ぶ場合は
     * {@link KnownTypeIndex} を 1 度構築して {@link #pickUsageTarget(String, KnownTypeIndex)}
     * を使うこと (O(参照数 × クラス数) を避けられる)。
     */
    static String pickUsageTarget(String type, Set<String> known) {
        return pickUsageTarget(type, new KnownTypeIndex(known));
    }

    /** 型参照から利用対象を推定する ({@link KnownTypeIndex} で O(1) 照合)。 */
    static String pickUsageTarget(String type, KnownTypeIndex known) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        String t = type.replaceAll("\\[\\]", "").trim();
        // ワイルドカード境界を除去: "? extends Foo" / "? super Foo" → "Foo"
        // 型アノテーション付き境界にも対応: "? extends @NonNull Foo" → "Foo"
        t = t.replaceAll(
                "^\\?\\s+(?:extends|super)\\s+(?:@[A-Za-z_$][A-Za-z0-9_$]*(?:\\([^)]*\\))?\\s+)*",
                "").trim();
        // 一番外側のジェネリックがあれば、その引数を再帰的に検索
        int lt = t.indexOf('<');
        if (lt >= 0) {
            int gt = t.lastIndexOf('>');
            String inner = (gt > lt) ? t.substring(lt + 1, gt) : "";
            String outer = t.substring(0, lt).trim();
            String tgt = matchKnown(outer, known);
            if (tgt != null) {
                return tgt;
            }
            for (String part : splitTopLevelCsv(inner)) {
                String r = pickUsageTarget(part.trim(), known);
                if (r != null) {
                    return r;
                }
            }
            return null;
        }
        return matchKnown(t, known);
    }

    private static String matchKnown(String name, KnownTypeIndex known) {
        if (PlantUmlClassDiagram.PRIMITIVE_OR_BUILTIN.matcher(name).matches()) {
            return null;
        }
        if (known.containsExact(name)) {
            return name;
        }
        // k.endsWith("." + name) の決定的 O(1) 照合 (旧: known 全走査の最初の一致)。
        return known.suffixMatch(name);
    }

    static String simplifyTypeRef(String t) {
        if (t == null) {
            return "";
        }
        // ジェネリクスを除く
        int lt = t.indexOf('<');
        return (lt >= 0 ? t.substring(0, lt) : t).trim();
    }

    private static List<String> splitTopLevelCsv(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(') {
                depth++;
            } else if (c == '>' || c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
