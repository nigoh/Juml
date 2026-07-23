// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * クラス情報 ({@link JavaClassInfo}) のリストから PlantUML 形式の
 * パッケージ図テキストを生成する。
 *
 * <p>各パッケージを単一のノードとして表示し、パッケージに含まれるクラス数と、
 * 継承 / 実装 / フィールド型を経由した「パッケージ間の参照関係」を矢印で示す。
 * クラス図 ({@link PlantUmlClassDiagram}) と異なりクラス単位の中身は表示せず、
 * 大規模プロジェクトでもパッケージ依存の全体像を俯瞰しやすくする。</p>
 */
public final class PlantUmlPackageDiagram {

    /** 出力オプション。 */
    public static class Options {
        /** タイトル文字列 (null で省略)。 */
        public String title;
        /** 凡例ブロックをダイアグラムに追加する。 */
        public boolean includeLegend = true;
        /** パッケージ間の自己参照を抑止する (既定で true)。 */
        public boolean suppressSelfLoop = true;
    }

    private PlantUmlPackageDiagram() {
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
        Context ctx = buildContext(classes, o);
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            // title 行に <> & が含まれると PlantUML が HTML タグとして誤認するためエスケープする。
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }

        // パッケージノード (rectangle) を出力
        for (Map.Entry<String, Integer> e : ctx.classCount.entrySet()) {
            String pkg = e.getKey();
            String alias = ctx.aliasByPkg.get(pkg);
            String label = pkg.isEmpty() ? "(default)" : pkg;
            int n = e.getValue();
            out.append("package \"").append(escape(label))
                    .append("\\n").append(n)
                    .append(n == 1 ? " class" : " classes")
                    .append("\" as ").append(alias)
                    .append(" {\n}\n");
        }

        // パッケージ間の依存関係を集計する。
        // - 継承 (extends), 実装 (implements)
        // - フィールド型 (ジェネリクスの引数も含めて pickUsageTarget で 1 段だけ取り出す)
        // から、依存元パッケージ → 依存先パッケージのペアを LinkedHashSet で重複排除。
        // まず有向エッジ (srcAlias->dstAlias) を順序保持で収集してから出力する。
        // こうすることで逆向きペアの存在を判定でき、循環依存を双方向矢印 1 本にまとめられる。
        Set<String> edges = new LinkedHashSet<>();
        // フィールド型解決の接尾辞索引を 1 度だけ構築 (フィールドごとの全走査を避ける)。
        KnownTypeIndex knownIdx = new KnownTypeIndex(ctx.knownNames);
        for (JavaClassInfo c : classes) {
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                continue;
            }
            String srcPkg = ctx.pkgByQn.get(c.getQualifiedName());
            if (srcPkg == null) {
                continue;
            }
            // 参照元クラスの import から「単純名 → FQN」を作り、単純名解決を正しいパッケージへ
            // 導く。これが無いと、同じ単純名 (Builder/Config/R 等) が複数パッケージにあるとき
            // 走査順で最初に登録されたパッケージへ誤ったエッジを引いてしまう。
            Map<String, String> importMap = buildImportMap(c);
            addRef(edges, srcPkg, c.getSuperClass(), ctx, importMap);
            for (String iface : c.getInterfaces()) {
                addRef(edges, srcPkg, iface, ctx, importMap);
            }
            for (JavaFieldInfo f : c.getFields()) {
                String target = usageTargetWithImport(f.getType(), knownIdx, importMap, ctx);
                addRef(edges, srcPkg, target, ctx, importMap);
            }
            // メソッドの戻り値型・引数型も依存エッジの対象にする。
            // フィールドを持たずメソッドシグネチャだけで型を使うクラスの参照を取りこぼさないようにする。
            for (JavaMethodInfo m : c.getMethods()) {
                if (!m.isConstructor()) {
                    String retTarget = usageTargetWithImport(
                            m.getReturnType(), knownIdx, importMap, ctx);
                    addRef(edges, srcPkg, retTarget, ctx, importMap);
                }
                for (String paramType : m.getParameterTypes()) {
                    String paramTarget = usageTargetWithImport(
                            paramType, knownIdx, importMap, ctx);
                    addRef(edges, srcPkg, paramTarget, ctx, importMap);
                }
            }
        }
        emitDependencyEdges(out, edges);

        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /** クラス → パッケージ解決に必要な集計結果と表示時オプションを束ねた内部コンテキスト。 */
    private static final class Context {
        final Map<String, Integer> classCount = new LinkedHashMap<>();
        final Map<String, String> pkgByQn = new LinkedHashMap<>();
        final Map<String, String> pkgBySimple = new LinkedHashMap<>();
        final Map<String, String> aliasByPkg = new LinkedHashMap<>();
        final Set<String> knownNames = new HashSet<>();
        /**
         * 既知クラスの「単純名 (最後のドット以降)」集合。
         * {@code resolvePackage} で「その単純名を持つ既知クラスが存在するか」を
         * O(1) で判定するための索引 ({@code knownNames} 全走査の置き換え)。
         */
        final Set<String> knownSimpleNames = new HashSet<>();
        final Options options;

        Context(Options options) {
            this.options = options;
        }
    }

    /** クラス情報から集計を 1 パスで作成する。 */
    private static Context buildContext(List<JavaClassInfo> classes, Options o) {
        Context ctx = new Context(o);
        for (JavaClassInfo c : classes) {
            // module-info の宣言はパッケージ集計に含めない (パッケージを持たないため)。
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                continue;
            }
            String pkg = c.getPackageName() == null ? "" : c.getPackageName();
            ctx.classCount.merge(pkg, 1, Integer::sum);
            ctx.pkgByQn.put(c.getQualifiedName(), pkg);
            ctx.pkgBySimple.putIfAbsent(c.getSimpleName(), pkg);
            String qn = c.getQualifiedName();
            ctx.knownNames.add(qn);
            int qd = qn.lastIndexOf('.');
            ctx.knownSimpleNames.add(qd >= 0 ? qn.substring(qd + 1) : qn);
        }
        int aliasSeq = 0;
        for (String pkg : ctx.classCount.keySet()) {
            ctx.aliasByPkg.put(pkg, "P" + (aliasSeq++));
        }
        return ctx;
    }

    /**
     * 1 つの参照を有向エッジ {@code srcAlias->dstAlias} として {@code edges} に集める。
     * 既知のクラスでない参照 (外部ライブラリ等) はスキップする。重複は Set が排除する。
     */
    /** 参照元クラスの import 宣言から「単純名 → FQN」を作る (具体 import のみ; static/ワイルドカードは除外)。 */
    private static Map<String, String> buildImportMap(JavaClassInfo c) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String imp : c.getImports()) {
            if (imp == null || imp.startsWith("static ") || imp.endsWith(".*")) {
                continue;
            }
            int dot = imp.lastIndexOf('.');
            if (dot > 0 && dot < imp.length() - 1) {
                map.putIfAbsent(imp.substring(dot + 1), imp);
            }
        }
        return map;
    }

    /**
     * フィールド / メソッド型の利用対象を求めつつ、参照元クラスの具体 import を優先する。
     *
     * <p>{@link PlantUmlClassRelations#pickUsageTarget} はジェネリクス展開後に
     * {@link KnownTypeIndex#suffixMatch} で単純名を「辞書順最小」の既知 FQN へ確定させるため、
     * 同じ単純名 (Foo など) が複数パッケージにあると import を無視して先頭パッケージへ
     * 誤って解決される。ここで解決結果の単純名を import マップと照合し、明示 import 先が
     * 既知クラスならそちらを優先する ({@code extends/implements} 経路の {@code resolvePackage}
     * と同じ import 優先を、フィールド/シグネチャ経路にも一貫して効かせる)。</p>
     */
    private static String usageTargetWithImport(String rawType, KnownTypeIndex knownIdx,
                                                Map<String, String> importMap, Context ctx) {
        String target = PlantUmlClassRelations.pickUsageTarget(rawType, knownIdx);
        if (target == null || importMap == null) {
            return target;
        }
        // 明示的な完全修飾参照 (rawType に解決先 FQN がそのまま書かれている場合。配列や
        // ジェネリクス引数内も含む) は、Java でも import より優先されるため import で
        // 上書きしない。resolvePackage の FQN-first (pkgByQn 先引き) と規則を一致させる。
        if (rawType != null && target.indexOf('.') >= 0 && rawType.contains(target)) {
            return target;
        }
        int dot = target.lastIndexOf('.');
        String simple = dot >= 0 ? target.substring(dot + 1) : target;
        String importedFqn = importMap.get(simple);
        if (importedFqn != null && !importedFqn.equals(target)
                && ctx.knownNames.contains(importedFqn)) {
            return importedFqn;
        }
        return target;
    }

    private static void addRef(Set<String> edges,
                                String srcPkg, String typeRef, Context ctx,
                                Map<String, String> importMap) {
        if (typeRef == null || typeRef.isEmpty()) {
            return;
        }
        String dstPkg = resolvePackage(typeRef, ctx, importMap);
        if (dstPkg == null) {
            return;
        }
        if (ctx.options.suppressSelfLoop && dstPkg.equals(srcPkg)) {
            return;
        }
        String srcAlias = ctx.aliasByPkg.get(srcPkg);
        String dstAlias = ctx.aliasByPkg.get(dstPkg);
        if (srcAlias == null || dstAlias == null) {
            return;
        }
        edges.add(srcAlias + "->" + dstAlias);
    }

    /**
     * 収集した有向エッジを出力する。逆向きエッジも存在するペア (循環依存) は
     * 双方向矢印 {@code <-->} 1 本にまとめ、視認性と重複描画を改善する。
     */
    private static void emitDependencyEdges(StringBuilder out, Set<String> edges) {
        Set<String> handled = new HashSet<>();
        for (String e : edges) {
            if (handled.contains(e)) {
                continue;
            }
            int sep = e.indexOf("->");
            String a = e.substring(0, sep);
            String b = e.substring(sep + 2);
            String reverse = b + "->" + a;
            if (!a.equals(b) && edges.contains(reverse)) {
                out.append(a).append(" <--> ").append(b).append('\n');
                handled.add(e);
                handled.add(reverse);
            } else {
                out.append(a).append(" --> ").append(b).append('\n');
                handled.add(e);
            }
        }
    }

    /**
     * 型参照を、既知のクラスを持つパッケージ名に解決する。解決できなければ null。
     * ジェネリック / 配列を取り除いてから、まず完全修飾名で、次に単純名で照合する。
     */
    private static String resolvePackage(String typeRef, Context ctx,
                                         Map<String, String> importMap) {
        String base = stripDecorations(typeRef);
        if (base.isEmpty()) {
            return null;
        }
        // 完全修飾名で一致
        String pkg = ctx.pkgByQn.get(base);
        if (pkg != null) {
            return pkg;
        }
        // 単純名で一致
        int dot = base.lastIndexOf('.');
        String simple = dot >= 0 ? base.substring(dot + 1) : base;
        // 参照元クラスが同じ単純名を具体 import していれば、その import 先パッケージを優先する
        // (走査順まかせの pkgBySimple より正確)。import 先が既知クラスのときだけ採用する。
        if (importMap != null) {
            String importedFqn = importMap.get(simple);
            if (importedFqn != null) {
                String importedPkg = ctx.pkgByQn.get(importedFqn);
                if (importedPkg != null) {
                    return importedPkg;
                }
            }
        }
        pkg = ctx.pkgBySimple.get(simple);
        // 既知クラスに同じ単純名が存在することを O(1) で確認する。
        // (旧実装の knownNames 全走査 anyMatch と同値: 最後のドット以降が simple と一致)
        if (pkg != null && ctx.knownSimpleNames.contains(simple)) {
            return pkg;
        }
        return null;
    }

    /** 配列 [] / ジェネリクス &lt;...&gt; を取り除いて、型のベース名のみを返す。 */
    private static String stripDecorations(String type) {
        String t = type.replaceAll("\\[\\]", "").trim();
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt).trim();
        }
        return t;
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }

    private static void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== パッケージ依存グラフ ==\n");
        out.append("  各ボックスは Java パッケージを表す\n");
        out.append("  矢印はパッケージ間の参照を示す\n");
        out.append("  (継承 / 実装 / フィールド型)\n");
        out.append("endlegend\n");
    }
}
