// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link ResourceLinkAnalysis} を「コード ↔ リソース」の紐づけ図に描画する。
 *
 * <p>3 種類のノードを置き、参照関係をエッジで結ぶ:</p>
 * <ul>
 *   <li>{@code <<class>>} — Activity/Fragment 等の参照元クラス</li>
 *   <li>{@code <<layout>>} — レイアウトリソース ({@code R.layout.*})</li>
 *   <li>{@code <<string>>} — 文字列リソース ({@code R.string.*} / {@code @string/*})。実文言付き</li>
 * </ul>
 *
 * <p>エッジ:</p>
 * <ul>
 *   <li>クラス ⇒ レイアウト (太線) — {@code setContentView/inflate/ViewBinding} で束ねる画面</li>
 *   <li>クラス → レイアウト (細線) — それ以外の {@code R.layout} 参照</li>
 *   <li>クラス ⋯&gt; 文字列 — {@code R.string} 参照</li>
 *   <li>レイアウト ⋯&gt; 文字列 — XML 内の {@code @string/} 参照</li>
 * </ul>
 */
public final class PlantUmlResourceLinkDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public int stringMaxLen = 28;
        public String title = "Resource Links: コード ↔ リソース";
    }

    /** デフォルト Options で生成。 */
    public static String generate(ResourceLinkAnalysis model) {
        return generate(model, null);
    }

    /** オプション付き生成。 */
    public static String generate(ResourceLinkAnalysis model, Options opts) {
        if (model == null) {
            throw new IllegalArgumentException("model is null");
        }
        Options o = opts != null ? opts : new Options();
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(escape(o.title)).append('\n');
        }
        out.append("skinparam shadowing false\n");
        out.append("skinparam rectangle {\n");
        out.append("  BackgroundColor<<class>> #E3F2FD\n");
        out.append("  BorderColor<<class>> #1565C0\n");
        out.append("  BackgroundColor<<layout>> #FFF4D6\n");
        out.append("  BorderColor<<layout>> #B59C3A\n");
        out.append("  BackgroundColor<<string>> #E8F5E9\n");
        out.append("  BorderColor<<string>> #2E7D32\n");
        out.append("}\n");

        if (model.isEmpty()) {
            out.append("note as N1\n  No code↔resource links were found.\n")
                    .append("  (no R.layout/R.string/R.id usage detected)\nend note\n");
            out.append("@enduml\n");
            return out.toString();
        }

        // ノード収集
        Map<String, String> classAlias = new LinkedHashMap<>();
        Map<String, String> layoutAlias = new LinkedHashMap<>();
        Map<String, String> stringAlias = new LinkedHashMap<>();
        Set<String> stringNames = new LinkedHashSet<>();
        Set<String> edges = new LinkedHashSet<>();

        for (ResourceReference ref : model.getReferences()) {
            String owner = ref.getOwnerClass() != null ? ref.getOwnerClass() : "(top-level)";
            String cAlias = alias(classAlias, "C_", owner);
            switch (ref.getKind()) {
                case LAYOUT: {
                    String lAlias = alias(layoutAlias, "L_", ref.getResourceName());
                    if (ref.isContentBinding()) {
                        edges.add(cAlias + " =[#1565C0]=> " + lAlias + " : binds");
                    } else {
                        edges.add(cAlias + " --> " + lAlias + " : R.layout");
                    }
                    break;
                }
                case STRING: {
                    stringNames.add(ref.getResourceName());
                    String sAlias = alias(stringAlias, "S_", ref.getResourceName());
                    edges.add(cAlias + " ..> " + sAlias + " : R.string");
                    break;
                }
                case ID:
                default:
                    // ID 参照はノードにすると煩雑なため、クラスノードは作るがエッジは引かない
                    break;
            }
        }

        // レイアウト → 文字列 (@string/) のエッジ
        for (Map.Entry<String, Set<String>> e : model.getLayoutStringRefs().entrySet()) {
            String layoutName = e.getKey();
            // コードから参照されていないレイアウトでも、文字列を参照していれば描く
            String lAlias = alias(layoutAlias, "L_", layoutName);
            for (String sName : e.getValue()) {
                stringNames.add(sName);
                String sAlias = alias(stringAlias, "S_", sName);
                edges.add(lAlias + " ..> " + sAlias + " : @string");
            }
        }

        // ノード宣言
        emitNodes(out, classAlias, "<<class>>", null, model, o);
        emitNodes(out, layoutAlias, "<<layout>>", null, model, o);
        emitNodes(out, stringAlias, "<<string>>", stringNames, model, o);

        out.append('\n');
        for (String edge : edges) {
            out.append(edge).append('\n');
        }

        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /** name→alias マップに無ければ追加し、alias を返す。 */
    private static String alias(Map<String, String> map, String prefix, String name) {
        String existing = map.get(name);
        if (existing != null) {
            return existing;
        }
        String a = prefix + sanitizeAlias(name) + "_" + map.size();
        map.put(name, a);
        return a;
    }

    private static void emitNodes(StringBuilder out, Map<String, String> aliases,
                                  String stereo, Set<String> withValue,
                                  ResourceLinkAnalysis model, Options o) {
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            String name = e.getKey();
            String alias = e.getValue();
            String label = escape(name);
            if (withValue != null) {
                String value = model.resolveString(name);
                if (value != null && !value.isEmpty()) {
                    label = label + "\\n\"" + escape(truncate(value, o.stringMaxLen)) + "\"";
                }
            }
            out.append("rectangle \"").append(label).append("\" as ")
                    .append(alias).append(' ').append(stereo).append('\n');
        }
    }

    private static void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== コード ↔ リソース ==\n");
        out.append("rectangle <<class>>   参照元クラス (Activity/Fragment 等)\n");
        out.append("rectangle <<layout>>  レイアウト (R.layout)\n");
        out.append("rectangle <<string>>  文字列リソース (R.string / @string)\n");
        out.append("A =[#1565C0]=> B  画面を束ねる (setContentView/inflate/Binding)\n");
        out.append("A --> B          R.layout 参照\n");
        out.append("A ..> B          R.string / @string 参照\n");
        out.append("endlegend\n");
    }

    private static String sanitizeAlias(String s) {
        if (s == null || s.isEmpty()) {
            return "x";
        }
        return s.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (max <= 0 || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "…";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'");
    }

    private PlantUmlResourceLinkDiagram() {
    }
}
