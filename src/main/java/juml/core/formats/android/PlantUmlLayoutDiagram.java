// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * {@link AndroidLayoutInfo} の View 階層を 1 枚の PlantUML 図に描画する。
 *
 * <p>表現は nested {@code rectangle}: ViewGroup を入れ子の rectangle として表し、
 * リーフ View / include / fragment は内側の rectangle ノードとして配置する。
 * 種別はステレオタイプ ({@code <<ViewGroup>>}, {@code <<View>>}, {@code <<include>>},
 * {@code <<fragment>>}, {@code <<merge>>}) で示し、色分けする。</p>
 *
 * <p>巨大 layout 対策として {@link Options#maxNodes} と {@link Options#maxDepth} を持ち、
 * 超過すると {@code note} で truncated 警告を出す。</p>
 */
public final class PlantUmlLayoutDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean includeLegend = true;
        public boolean showIds = true;
        public boolean showText = true;
        public boolean showDimensions = true;
        public int maxNodes = 300;
        public int maxDepth = 20;
        public int textMaxLen = 0;
        public String title;
    }

    /** デフォルト Options で生成。 */
    public static String generate(AndroidLayoutInfo layout) {
        return generate(layout, null);
    }

    /** オプション付き生成。 */
    public static String generate(AndroidLayoutInfo layout, Options opts) {
        if (layout == null) {
            throw new IllegalArgumentException("layout is null");
        }
        Options o = opts != null ? opts : new Options();
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        String title = o.title != null && !o.title.isEmpty()
                ? o.title
                : buildDefaultTitle(layout);
        if (!title.isEmpty()) {
            out.append("title ").append(escape(title)).append('\n');
        }
        out.append("skinparam shadowing false\n");
        out.append("skinparam rectangle {\n");
        out.append("  BackgroundColor<<ViewGroup>> #E8F0FE\n");
        out.append("  BorderColor<<ViewGroup>> #4A6FB7\n");
        out.append("  BackgroundColor<<View>> #FFFFFF\n");
        out.append("  BorderColor<<View>> #888888\n");
        out.append("  BackgroundColor<<include>> #FFF4D6\n");
        out.append("  BorderColor<<include>> #B59C3A\n");
        out.append("  BackgroundColor<<fragment>> #E8F5E9\n");
        out.append("  BorderColor<<fragment>> #4CAF50\n");
        out.append("  BackgroundColor<<merge>> #F5F5F5\n");
        out.append("  BorderColor<<merge>> #999999\n");
        out.append("}\n");

        LayoutViewNode root = layout.getRoot();
        if (root == null) {
            out.append("note as N1\n  (no view hierarchy parsed)\nend note\n");
        } else {
            Counter counter = new Counter(o.maxNodes);
            emitNode(out, root, "", 0, o, counter);
            if (counter.truncated > 0) {
                out.append("note as TRUNC\n  ").append(counter.truncated)
                        .append(" node(s) truncated (maxNodes=").append(o.maxNodes)
                        .append(")\nend note\n");
            }
            if (counter.depthTruncated > 0) {
                out.append("note as DEPTHTRUNC\n  ").append(counter.depthTruncated)
                        .append(" subtree(s) collapsed (maxDepth=").append(o.maxDepth)
                        .append(")\nend note\n");
            }
        }
        if (o.includeLegend) {
            emitLegend(out, o);
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static String buildDefaultTitle(AndroidLayoutInfo layout) {
        StringBuilder sb = new StringBuilder("Layout: ");
        sb.append(layout.getFileName() != null && !layout.getFileName().isEmpty()
                ? layout.getFileName() : "(unnamed)");
        if (layout.getConfigQualifier() != null && !layout.getConfigQualifier().isEmpty()) {
            sb.append(" [").append(layout.getConfigQualifier()).append(']');
        }
        if (!"main".equals(layout.getSourceSet())) {
            sb.append(" (").append(layout.getSourceSet()).append(')');
        }
        if (!":root".equals(layout.getModuleName())) {
            sb.append(" — ").append(layout.getModuleName());
        }
        return sb.toString();
    }

    /** ノードを再帰的に出力。{@link Counter} で打ち切り判定。 */
    private static void emitNode(StringBuilder out, LayoutViewNode node,
                                  String indent, int depth,
                                  Options o, Counter counter) {
        if (counter.emitted >= counter.limit) {
            counter.truncated++;
            return;
        }
        counter.emitted++;
        String alias = "N" + counter.aliasSeq++;
        String label = buildLabel(node, o);
        LayoutViewNode.Kind kind = node.classify();
        String stereo = stereotypeOf(kind);

        boolean hasChildren = !node.getChildren().isEmpty();
        boolean canDescend = depth < o.maxDepth;

        if (hasChildren && canDescend) {
            out.append(indent).append("rectangle \"").append(label).append("\" as ")
                    .append(alias).append(' ').append(stereo).append(" {\n");
            for (LayoutViewNode child : node.getChildren()) {
                if (counter.emitted >= counter.limit) {
                    counter.truncated++;
                    break;
                }
                emitNode(out, child, indent + "  ", depth + 1, o, counter);
            }
            out.append(indent).append("}\n");
        } else {
            // リーフ、または深さ打ち切り
            if (hasChildren && !canDescend) {
                counter.depthTruncated++;
                label = label + "\\n[" + node.getChildren().size() + " children...]";
            }
            out.append(indent).append("rectangle \"").append(label).append("\" as ")
                    .append(alias).append(' ').append(stereo).append('\n');
        }
    }

    private static String stereotypeOf(LayoutViewNode.Kind kind) {
        switch (kind) {
            case VIEW_GROUP:
                return "<<ViewGroup>>";
            case INCLUDE:
                return "<<include>>";
            case FRAGMENT:
                return "<<fragment>>";
            case MERGE:
                return "<<merge>>";
            case VIEW:
            default:
                return "<<View>>";
        }
    }

    /** ノードのラベル文字列を組み立てる。PlantUML の "..." 内で使う形式。 */
    private static String buildLabel(LayoutViewNode node, Options o) {
        StringBuilder sb = new StringBuilder();
        sb.append(escape(node.shortTag()));
        boolean firstLine = true;
        if (o.showIds && node.shortId() != null) {
            sb.append("\\n----");
            firstLine = false;
            sb.append("\\nid: ").append(escape(node.shortId()));
        }
        if (o.showDimensions && (node.getWidth() != null || node.getHeight() != null)) {
            if (firstLine) {
                sb.append("\\n----");
                firstLine = false;
            }
            sb.append("\\nsize: ")
                    .append(shortDim(node.getWidth()))
                    .append(" x ")
                    .append(shortDim(node.getHeight()));
        }
        if (o.showText && node.getText() != null && !node.getText().isEmpty()) {
            if (firstLine) {
                sb.append("\\n----");
                firstLine = false;
            }
            sb.append("\\ntext: ").append(escape(truncate(node.getText(), o.textMaxLen)));
        }
        if (node.getIncludeLayoutRef() != null) {
            if (firstLine) {
                sb.append("\\n----");
                firstLine = false;
            }
            sb.append("\\nlayout: ").append(escape(node.getIncludeLayoutRef()));
        }
        if (node.getFragmentClassName() != null) {
            if (firstLine) {
                sb.append("\\n----");
                firstLine = false;
            }
            sb.append("\\nclass: ").append(escape(shortClass(node.getFragmentClassName())));
        }
        return sb.toString();
    }

    private static String shortDim(String dim) {
        if (dim == null) {
            return "?";
        }
        if ("match_parent".equals(dim)) {
            return "MP";
        }
        if ("wrap_content".equals(dim)) {
            return "WC";
        }
        return dim;
    }

    private static String shortClass(String fqn) {
        if (fqn == null) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        if (max <= 0 || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "...";
    }

    /** PlantUML 文字列リテラル内で問題になりやすい文字を最小限置換。 */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "'");
    }

    private static void emitLegend(StringBuilder out, Options o) {
        out.append("legend top left\n");
        out.append("== 画面レイアウトの構造 (Layout ビュー階層) ==\n");
        out.append("入れ子の四角 = 画面上の部品の親子関係を表す\n");
        out.append("ViewGroup : 他の部品を中に並べる入れ物 (枠)\n");
        out.append("View      : ボタンや文字など、1 つの部品\n");
        out.append("include   : 別レイアウトファイルを取り込んだ部分\n");
        out.append("fragment  : 差し替え可能な画面の一部\n");
        out.append("merge     : 余計な枠を作らずまとめる指定\n");
        out.append("MP = 親いっぱいに広げる (match_parent)\n");
        out.append("WC = 中身に合わせる大きさ (wrap_content)\n");
        if (o.maxNodes > 0) {
            out.append("maxNodes = ").append(o.maxNodes).append('\n');
        }
        out.append("endlegend\n");
    }

    /** 再帰時の打ち切り管理用カウンタ。 */
    private static final class Counter {
        final int limit;
        int emitted = 0;
        int aliasSeq = 0;
        int truncated = 0;
        int depthTruncated = 0;

        Counter(int limit) {
            this.limit = limit > 0 ? limit : Integer.MAX_VALUE;
        }
    }

    private PlantUmlLayoutDiagram() {
    }
}
