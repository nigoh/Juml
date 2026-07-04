// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.uml.PlantUmlCommentFormatter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AndroidProjectAnalysis から Deep Link / App Links 専用の PlantUML 図を生成する。
 *
 * <p>{@link PlantUmlManifestDiagram} がアプリ構造を俯瞰する図であるのに対し、
 * Deep Link Diagram は intent-filter から外部から到達可能な URI 入口に焦点を当てる。</p>
 *
 * <ul>
 *   <li>{@code action.VIEW + category.BROWSABLE} を持つ intent-filter を抽出</li>
 *   <li>scheme でグルーピング (http/https は「Web (App Links)」枠、それ以外は
 *       「Custom Scheme」枠)</li>
 *   <li>{@code autoVerify="true"} の Activity は強調 (App Links 候補)</li>
 *   <li>各 scheme + host + path 系を 1 ノードにまとめ、Activity に矢印で接続</li>
 * </ul>
 */
public final class PlantUmlDeepLinkDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public boolean showMimeOnly = true;
        public String title;
    }

    /** デフォルト Options で生成。 */
    public static String generate(AndroidProjectAnalysis analysis) {
        return generate(analysis, null);
    }

    /** オプション付き生成。 */
    public static String generate(AndroidProjectAnalysis analysis, Options opts) {
        if (analysis == null) {
            throw new IllegalArgumentException("analysis is null");
        }
        Options o = opts != null ? opts : new Options();
        List<Link> links = collectLinks(analysis, o);
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(PlantUmlCommentFormatter.escapeLabel(o.title)).append('\n');
        }
        out.append("skinparam componentStyle uml2\n");
        // App Links 候補は緑、通常 Deep Link は青、MIME only は灰色で塗り分け。
        out.append("skinparam rectangle {\n");
        out.append("  BackgroundColor<<applink>> #D5F5E3\n");
        out.append("  BorderColor<<applink>>     #1E8449\n");
        out.append("  BackgroundColor<<deeplink>> #D6EAF8\n");
        out.append("  BorderColor<<deeplink>>     #2471A3\n");
        out.append("  BackgroundColor<<mime>>     #EAECEE\n");
        out.append("  BorderColor<<mime>>         #5D6D7E\n");
        out.append("}\n");

        if (links.isEmpty()) {
            out.append("note as N1\n  (no deep link intent-filter found)\nend note\n");
            if (o.includeLegend) {
                emitLegend(out);
            }
            out.append("@enduml\n");
            return out.toString();
        }

        // Activity 側のノードを先に作る (FQN 単位で 1 個)。
        Map<String, String> activityAlias = emitActivityNodes(out, links);
        emitLinkNodesAndEdges(out, links, activityAlias);

        if (o.includeLegend) {
            emitLegend(out);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static List<Link> collectLinks(AndroidProjectAnalysis analysis, Options o) {
        List<Link> links = new ArrayList<>();
        for (AndroidManifestInfo m : analysis.allManifests()) {
            for (AndroidComponentInfo c : m.getActivities()) {
                for (AndroidIntentFilter f : c.getIntentFilters()) {
                    if (!f.isViewDeepLink()) {
                        continue;
                    }
                    boolean appended = false;
                    for (AndroidDataSpec spec : f.getDataSpecs()) {
                        if (spec.hasUriComponent()) {
                            links.add(new Link(c, f, spec, false));
                            appended = true;
                        }
                    }
                    if (!appended && o.showMimeOnly) {
                        // scheme/host が無く mimeType だけのケースも拾う。
                        for (AndroidDataSpec spec : f.getDataSpecs()) {
                            if (spec.getMimeType() != null) {
                                links.add(new Link(c, f, spec, true));
                            }
                        }
                    }
                }
            }
        }
        return links;
    }

    private static Map<String, String> emitActivityNodes(StringBuilder out, List<Link> links) {
        Map<String, String> alias = new LinkedHashMap<>();
        int seq = 0;
        out.append("package \"Activities (deep link receivers)\" {\n");
        for (Link l : links) {
            String fqn = l.activity.getName();
            if (fqn == null || fqn.isEmpty() || alias.containsKey(fqn)) {
                continue;
            }
            String a = "A" + (seq++);
            alias.put(fqn, a);
            out.append("  component \"").append(escape(shortName(fqn)))
                    .append("\" as ").append(a).append(" <<Activity>>");
            if (Boolean.TRUE.equals(l.activity.getExported())) {
                out.append(" #LightYellow");
            }
            out.append('\n');
        }
        out.append("}\n");
        return alias;
    }

    private static void emitLinkNodesAndEdges(StringBuilder out, List<Link> links,
                                                Map<String, String> activityAlias) {
        // scheme グループごとに package 分け: App Links 用 web / カスタム / MIME only。
        Map<String, List<Link>> grouped = new LinkedHashMap<>();
        for (Link l : links) {
            String key = groupKey(l);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }
        int seq = 0;
        for (Map.Entry<String, List<Link>> e : grouped.entrySet()) {
            String groupLabel = e.getKey();
            List<Link> bucket = e.getValue();
            out.append("package \"").append(escape(groupLabel))
                    .append(" (").append(bucket.size()).append(")\" {\n");
            for (Link l : bucket) {
                String nodeAlias = "L" + (seq++);
                String stereo = stereoForLink(l);
                String label = labelForLink(l);
                out.append("  rectangle \"").append(escape(label))
                        .append("\" as ").append(nodeAlias).append(' ').append(stereo);
                if (Boolean.TRUE.equals(l.filter.getAutoVerify())) {
                    out.append(" <<autoVerify>>");
                }
                out.append('\n');
            }
            out.append("}\n");
        }
        // 矢印は package を閉じた後でまとめて引く (PlantUML レイアウト安定化)。
        seq = 0;
        for (Map.Entry<String, List<Link>> e : grouped.entrySet()) {
            for (Link l : e.getValue()) {
                String nodeAlias = "L" + (seq++);
                String aAlias = activityAlias.get(l.activity.getName());
                if (aAlias == null) {
                    continue;
                }
                out.append(nodeAlias).append(" --> ").append(aAlias);
                if (Boolean.TRUE.equals(l.filter.getAutoVerify())) {
                    out.append(" : autoVerify");
                }
                out.append('\n');
            }
        }
    }

    private static String groupKey(Link l) {
        if (l.mimeOnly) {
            return "MIME-only";
        }
        String s = l.spec.getScheme();
        if (s == null) {
            return "Scheme: *";
        }
        if ("http".equals(s) || "https".equals(s)) {
            return "Web (http/https) — App Links";
        }
        return "Custom scheme: " + s + "://";
    }

    private static String stereoForLink(Link l) {
        if (l.mimeOnly) {
            return "<<mime>>";
        }
        String s = l.spec.getScheme();
        if ("http".equals(s) || "https".equals(s)) {
            return "<<applink>>";
        }
        return "<<deeplink>>";
    }

    private static String labelForLink(Link l) {
        if (l.mimeOnly) {
            return "mimeType: " + nullToStar(l.spec.getMimeType());
        }
        String uri = l.spec.toDeepLinkUri();
        StringBuilder sb = new StringBuilder();
        sb.append(uri != null ? uri : "(no uri)");
        if (l.spec.getMimeType() != null) {
            sb.append("\\nmime: ").append(l.spec.getMimeType());
        }
        if (l.filter.getPriority() != null) {
            sb.append("\\npriority: ").append(l.filter.getPriority());
        }
        return sb.toString();
    }

    private static String nullToStar(String s) {
        return s == null || s.isEmpty() ? "*" : s;
    }

    private static String shortName(String fqn) {
        if (fqn == null) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'");
    }

    private static void emitLegend(StringBuilder out) {
        out.append("legend top left\n");
        out.append("== Deep Link 図 (URL から画面を開く設定) ==\n");
        out.append("Deep Link = 特定の URL をタップするとアプリの画面が開く仕組み\n");
        out.append("applink   : http/https の URL で開く (App Links 候補)\n");
        out.append("deeplink  : myapp:// など独自形式の URL で開く\n");
        out.append("mime      : ファイル種別 (mimeType) だけで開く (URL 無し)\n");
        out.append("<<autoVerify>> : URL の所有者確認を自動で行う設定 (要 Web 側設定)\n");
        out.append("#LightYellow   : exported=true (他アプリから呼び出せる=外部公開)\n");
        out.append("endlegend\n");
    }

    /** intent-filter 1 件 + AndroidDataSpec 1 件の組。 */
    private static final class Link {
        final AndroidComponentInfo activity;
        final AndroidIntentFilter filter;
        final AndroidDataSpec spec;
        final boolean mimeOnly;

        Link(AndroidComponentInfo activity, AndroidIntentFilter filter,
              AndroidDataSpec spec, boolean mimeOnly) {
            this.activity = activity;
            this.filter = filter;
            this.spec = spec;
            this.mimeOnly = mimeOnly;
        }
    }

    private PlantUmlDeepLinkDiagram() {
    }
}
