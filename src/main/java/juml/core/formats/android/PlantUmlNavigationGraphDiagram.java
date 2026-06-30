// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link AndroidNavigationGraphInfo} の画面遷移を PlantUML State 図として描画する。
 *
 * <p>各 Destination を {@code state} として、Action を遷移矢印 ({@code -->}) で表す。
 * {@code startDestination} は {@code [*] -->} で示す。種別
 * (FRAGMENT / ACTIVITY / DIALOG / NAVIGATION / INCLUDE) はステレオタイプと {@code skinparam}
 * の色分けで区別する。</p>
 */
public final class PlantUmlNavigationGraphDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        /** 引数 ({@code <argument>}) を state ラベル内に表示する。 */
        public boolean showArguments = true;
        /** Deep Link URI を {@code note} で表示する。 */
        public boolean showDeepLinks = true;
        public String title;
    }

    /** デフォルト Options で生成。 */
    public static String generate(AndroidNavigationGraphInfo info) {
        return generate(info, null);
    }

    /** オプション付き生成。 */
    public static String generate(AndroidNavigationGraphInfo info, Options opts) {
        if (info == null) {
            throw new IllegalArgumentException("info is null");
        }
        Options o = opts != null ? opts : new Options();
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");

        String title = o.title != null && !o.title.isEmpty()
                ? o.title
                : buildDefaultTitle(info);
        if (!title.isEmpty()) {
            out.append("title ").append(escape(title)).append('\n');
        }

        out.append("skinparam shadowing false\n");
        out.append("skinparam state {\n");
        out.append("  BackgroundColor<<fragment>> #E8F0FE\n");
        out.append("  BorderColor<<fragment>> #4A6FB7\n");
        out.append("  BackgroundColor<<activity>> #FFF4D6\n");
        out.append("  BorderColor<<activity>> #B59C3A\n");
        out.append("  BackgroundColor<<dialog>> #E8F5E9\n");
        out.append("  BorderColor<<dialog>> #4CAF50\n");
        out.append("  BackgroundColor<<navigation>> #F5F5F5\n");
        out.append("  BorderColor<<navigation>> #999999\n");
        out.append("  BackgroundColor<<include>> #FCE4EC\n");
        out.append("  BorderColor<<include>> #E91E63\n");
        out.append("}\n");

        if (info.getDestinations().isEmpty()) {
            out.append("note as N1\n  (no destinations found)\nend note\n");
        } else {
            Map<String, String> aliasMap = buildAliasMap(info);

            String startRef = info.getStartDestination();
            if (startRef != null && !startRef.isEmpty()) {
                String startAlias = aliasMap.get(startRef);
                if (startAlias != null) {
                    out.append("[*] --> ").append(startAlias).append('\n');
                }
            }
            out.append('\n');

            for (NavigationDestination dest : info.getDestinations()) {
                emitDestination(out, dest, o, aliasMap);
            }
            out.append('\n');

            for (NavigationDestination dest : info.getDestinations()) {
                emitActions(out, dest, aliasMap);
            }
            for (NavigationAction ga : info.getGlobalActions()) {
                emitGlobalAction(out, ga, aliasMap);
            }

            if (o.showDeepLinks) {
                emitDeepLinkNotes(out, info, aliasMap);
            }
        }

        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static Map<String, String> buildAliasMap(AndroidNavigationGraphInfo info) {
        Map<String, String> map = new HashMap<>();
        int seq = 0;
        for (NavigationDestination dest : info.getDestinations()) {
            String key = dest.getIdRef() != null ? dest.getIdRef() : dest.getId();
            if (key != null && !key.isEmpty() && !map.containsKey(key)) {
                map.put(key, "D" + seq++);
            }
        }
        return map;
    }

    private static void emitDestination(StringBuilder out, NavigationDestination dest,
                                         Options o, Map<String, String> aliasMap) {
        String key = dest.getIdRef() != null ? dest.getIdRef() : dest.getId();
        String alias = aliasMap.get(key);
        if (alias == null) {
            return;
        }
        String stereo = stereotypeOf(dest.getKind());
        StringBuilder label = new StringBuilder(escape(dest.displayName()));
        if (o.showArguments && !dest.getArguments().isEmpty()) {
            label.append("\\n--");
            for (NavigationArgument arg : dest.getArguments()) {
                label.append("\\n").append(escape(formatArg(arg)));
            }
        }
        out.append("state \"").append(label).append("\" as ").append(alias)
                .append(' ').append(stereo).append('\n');
    }

    private static void emitActions(StringBuilder out, NavigationDestination src,
                                     Map<String, String> aliasMap) {
        String srcKey = src.getIdRef() != null ? src.getIdRef() : src.getId();
        String srcAlias = aliasMap.get(srcKey);
        if (srcAlias == null) {
            return;
        }
        for (NavigationAction action : src.getActions()) {
            String dstRef = action.getDestination();
            if (dstRef == null || dstRef.isEmpty()) {
                continue;
            }
            String dstAlias = aliasMap.get(dstRef);
            if (dstAlias == null) {
                continue;
            }
            String label = action.getIdRef() != null ? action.getIdRef() : "";
            out.append(srcAlias).append(" --> ").append(dstAlias);
            if (!label.isEmpty()) {
                out.append(" : ").append(escape(label));
            }
            out.append('\n');
        }
    }

    private static void emitGlobalAction(StringBuilder out, NavigationAction ga,
                                          Map<String, String> aliasMap) {
        String dstRef = ga.getDestination();
        if (dstRef == null || dstRef.isEmpty()) {
            return;
        }
        String dstAlias = aliasMap.get(dstRef);
        if (dstAlias == null) {
            return;
        }
        String label = (ga.getIdRef() != null ? ga.getIdRef() : "") + " (global)";
        out.append("[*] --> ").append(dstAlias).append(" : ").append(escape(label)).append('\n');
    }

    private static void emitDeepLinkNotes(StringBuilder out, AndroidNavigationGraphInfo info,
                                           Map<String, String> aliasMap) {
        int noteSeq = 0;
        for (NavigationDestination dest : info.getDestinations()) {
            if (dest.getDeepLinks().isEmpty()) {
                continue;
            }
            String key = dest.getIdRef() != null ? dest.getIdRef() : dest.getId();
            String alias = aliasMap.get(key);
            if (alias == null) {
                continue;
            }
            String noteAlias = "DL" + noteSeq++;
            out.append("note left of ").append(alias).append(" as ").append(noteAlias)
                    .append('\n');
            for (String uri : dest.getDeepLinks()) {
                out.append("  ").append(escape(uri)).append('\n');
            }
            out.append("end note\n");
        }
    }

    private static String stereotypeOf(NavigationDestination.Kind kind) {
        if (kind == null) {
            return "<<fragment>>";
        }
        switch (kind) {
            case ACTIVITY:   return "<<activity>>";
            case DIALOG:     return "<<dialog>>";
            case NAVIGATION: return "<<navigation>>";
            case INCLUDE:    return "<<include>>";
            case FRAGMENT:
            default:         return "<<fragment>>";
        }
    }

    private static String formatArg(NavigationArgument arg) {
        StringBuilder sb = new StringBuilder();
        if (arg.getName() != null) {
            sb.append(arg.getName());
        }
        if (arg.getArgType() != null) {
            sb.append(": ").append(arg.getArgType());
        }
        if (arg.getDefaultValue() != null) {
            sb.append(" = ").append(arg.getDefaultValue());
        }
        if (arg.isNullable()) {
            sb.append(" (nullable)");
        }
        return sb.toString();
    }

    private static String buildDefaultTitle(AndroidNavigationGraphInfo info) {
        StringBuilder sb = new StringBuilder("Navigation: ");
        // fileName が無い (単一 XML 直接指定など) 場合でも、android:label →
        // graph id を順にフォールバックして意味のあるタイトルにする
        sb.append(graphDisplayName(info));
        if (!"main".equals(info.getSourceSet())) {
            sb.append(" (").append(info.getSourceSet()).append(')');
        }
        if (!":root".equals(info.getModuleName())) {
            sb.append(" — ").append(info.getModuleName());
        }
        return sb.toString();
    }

    /** タイトル用の表示名: fileName → android:label → graph id → "(unnamed)"。 */
    private static String graphDisplayName(AndroidNavigationGraphInfo info) {
        if (info.getFileName() != null && !info.getFileName().isEmpty()) {
            return info.getFileName();
        }
        if (info.getLabel() != null && !info.getLabel().isEmpty()) {
            return info.getLabel();
        }
        if (info.getGraphId() != null && !info.getGraphId().isEmpty()) {
            return info.getGraphId();
        }
        return "(unnamed)";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'");
    }

    private static void emitLegend(StringBuilder out) {
        // 各記号が「どんな画面・遷移か」を素人にも分かる言葉で説明する。
        // 色は emitDestination の skinparam (fragment=青, activity=黄, dialog=緑,
        // navigation=灰, include=桃) と対応している。
        out.append("legend top left\n");
        out.append("== 画面遷移図 (Navigation) の見方 ==\n");
        out.append("四角 = 1 つの画面 / 矢印 = 画面から画面への移動\n");
        out.append("fragment (青)   : 画面の中身 (Fragment)。最も一般的な画面\n");
        out.append("activity (黄)   : 独立した画面 (Activity)\n");
        out.append("dialog (緑)     : ポップアップで出る小さな画面 (ダイアログ)\n");
        out.append("navigation (灰) : 複数画面をまとめた入れ子のグループ\n");
        out.append("include (桃)    : 別ファイルから取り込んだ画面グループ\n");
        out.append("[*] --> の矢印  : アプリ起動時に最初に表示される画面\n");
        out.append("endlegend\n");
    }

    private PlantUmlNavigationGraphDiagram() {
    }
}
