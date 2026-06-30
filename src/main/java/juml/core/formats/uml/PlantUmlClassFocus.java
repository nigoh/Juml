// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * クラス図の「フォーカス強調モード」を担う補助クラス。
 *
 * <p>{@link PlantUmlClassDiagram.Options#focusClass} に焦点クラス (FQN) が指定されると、
 * 焦点クラスとその直接の隣 (1-hop の継承/実装/利用先・元) だけを通常表示・焦点ノードを
 * アクセント色にし、それ以外のノードとエッジを淡色化して後退させる。大規模図で「この
 * クラスの周辺だけ」を追えるようにするための描画支援 (フィルタではなく強調なので、
 * 周辺外のノードも文脈として淡く残る)。</p>
 *
 * <p>重い近傍計算 (1-hop 集合) をここに集約し、{@link PlantUmlClassDiagram} 本体は
 * 焦点判定の薄い呼び出しに留める。</p>
 */
final class PlantUmlClassFocus {

    /** 焦点ノードの背景色 (アクセント。淡い金色)。 */
    static final String FOCUS_BG = "#FFF3CD";
    /** 周辺外ノードの背景色 (淡いグレーで後退)。 */
    static final String DIM_BG = "#F2F2F2";
    /** 周辺外エッジの線色 (淡いグレーで後退)。 */
    static final String DIM_EDGE = "#D0D0D0";

    private PlantUmlClassFocus() {
    }

    /**
     * 焦点モードの前処理。{@code o.focusClass} を図内 QN へ正規化し、解決できれば
     * 1-hop 近傍集合を {@code o.focusEmphasis} にセットする (解決不能なら焦点を無効化)。
     */
    static void prepare(PlantUmlClassDiagram.Options o, List<JavaClassInfo> classes,
                        Set<String> knownQns, Map<String, String> qnBySimple) {
        if (o.focusClass == null || o.focusClass.isEmpty()) {
            return;
        }
        String fq = resolveFocusQn(o.focusClass, knownQns, qnBySimple);
        o.focusClass = fq == null ? "" : fq;
        o.focusEmphasis = fq == null ? null
                : neighborhood(classes, fq, new KnownTypeIndex(knownQns), knownQns, qnBySimple);
    }

    /** 焦点モードが有効か (焦点 FQN と近傍集合が揃っているか)。 */
    static boolean active(PlantUmlClassDiagram.Options o) {
        return o != null && o.focusClass != null && !o.focusClass.isEmpty()
                && o.focusEmphasis != null;
    }

    /**
     * 焦点 FQN を図に実在する QN へ正規化する。完全修飾名でそのまま既知ならそれを、
     * 単純名なら {@code qnBySimple} 経由で解決する。解決できなければ null (= 焦点無効)。
     */
    static String resolveFocusQn(String focus, Set<String> knownQns,
                                  Map<String, String> qnBySimple) {
        if (focus == null || focus.isEmpty()) {
            return null;
        }
        if (knownQns.contains(focus)) {
            return focus;
        }
        String qn = qnBySimple.get(focus);
        return (qn != null && knownQns.contains(qn)) ? qn : null;
    }

    /**
     * 焦点 QN とその 1-hop 近傍 (継承/実装/利用の両方向) の QN 集合を返す。
     * 焦点自身を必ず含む。{@code knownIdx}/{@code qnBySimple} で型参照を図内 QN へ解決する。
     */
    static Set<String> neighborhood(List<JavaClassInfo> classes, String focusQn,
                                     KnownTypeIndex knownIdx, Set<String> knownQns,
                                     Map<String, String> qnBySimple) {
        Set<String> emph = new HashSet<>();
        emph.add(focusQn);
        for (JavaClassInfo c : classes) {
            String cQn = c.getQualifiedName();
            Set<String> targets = new HashSet<>();
            addQn(targets, resolveKnownQn(c.getSuperClass(), knownQns, qnBySimple), cQn);
            for (String iface : c.getInterfaces()) {
                addQn(targets, resolveKnownQn(iface, knownQns, qnBySimple), cQn);
            }
            for (JavaFieldInfo f : c.getFields()) {
                addQn(targets, PlantUmlClassRelations.pickUsageTarget(f.getType(), knownIdx), cQn);
            }
            for (JavaMethodInfo m : c.getMethods()) {
                if (!m.isConstructor()) {
                    addQn(targets, PlantUmlClassRelations.pickUsageTarget(
                            m.getReturnType(), knownIdx), cQn);
                }
                for (String p : m.getParameterTypes()) {
                    addQn(targets, PlantUmlClassRelations.pickUsageTarget(p, knownIdx), cQn);
                }
            }
            if (cQn.equals(focusQn)) {
                emph.addAll(targets);
            } else if (targets.contains(focusQn)) {
                emph.add(cQn);
            }
        }
        return emph;
    }

    /** {@code qn} が非 null かつ自己参照でなければ集合へ追加する。 */
    private static void addQn(Set<String> set, String qn, String selfQn) {
        if (qn != null && !qn.equals(selfQn)) {
            set.add(qn);
        }
    }

    /** 継承/実装の型参照を図内の既知 QN に解決する (未解決は null)。 */
    private static String resolveKnownQn(String ref, Set<String> knownQns,
                                          Map<String, String> qnBySimple) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        String simple = PlantUmlClassRelations.simplifyTypeRef(ref);
        if (knownQns.contains(simple)) {
            return simple;
        }
        String qn = qnBySimple.get(simple);
        return (qn != null && knownQns.contains(qn)) ? qn : null;
    }

    /**
     * 焦点モード時のノード背景色を返す。
     * 焦点ノード = アクセント、近傍 = null (通常)、周辺外 = 淡色。焦点無効時は null。
     */
    static String nodeColor(String qn, PlantUmlClassDiagram.Options o) {
        if (!active(o)) {
            return null;
        }
        if (qn.equals(o.focusClass)) {
            return FOCUS_BG;
        }
        return o.focusEmphasis.contains(qn) ? null : DIM_BG;
    }

    /**
     * エッジを淡色化すべきか。焦点モードかつ、そのエッジが焦点ノードに接していない場合に true。
     * 焦点に接するエッジ (= 焦点の依存線) は通常色のまま強調する。
     */
    static boolean dimEdge(PlantUmlClassDiagram.Options o, String aQn, String bQn) {
        if (!active(o)) {
            return false;
        }
        return !o.focusClass.equals(aQn) && !o.focusClass.equals(bQn);
    }
}
