// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.function.Function;

/**
 * {@link AndroidLayoutInfo} の View 階層を、PlantUML <b>Salt</b> (ワイヤーフレーム記法) で
 * 「画面としてどう表示されるか」を近似描画する。
 *
 * <p>{@link PlantUmlLayoutDiagram} が入れ子の rectangle で <em>構造ツリー</em> を示すのに対し、
 * こちらは実際のウィジェット ({@code Button} → {@code [ ボタン ]}、{@code EditText} → 入力欄、
 * {@code CheckBox} → チェックボックス…) に置き換えた <em>画面イメージ</em> を出力する。</p>
 *
 * <p>レイアウト近似:</p>
 * <ul>
 *   <li>{@code LinearLayout orientation="horizontal"} の子は横並び ({@code |} 区切り)</li>
 *   <li>それ以外の ViewGroup (縦 LinearLayout / Frame / Relative / Constraint 等) は縦積み</li>
 *   <li>各 ViewGroup は枠付きグリッド {@code &#123;+ ... &#125;} で囲み、見出し行を添える</li>
 * </ul>
 *
 * <p>{@code android:text} が {@code @string/foo} 参照のときは
 * {@link Options#stringResolver} で実文言へ解決して表示する (未解決なら参照のまま)。</p>
 */
public final class PlantUmlLayoutScreenDiagram {

    /** 出力オプション。 */
    public static class Options {
        public boolean showIds = true;
        public int maxNodes = 200;
        public int maxDepth = 12;
        public int textMaxLen = 0;
        public String title;
        /** {@code @string/foo} を実文言へ解決する関数。null なら解決しない。 */
        public Function<String, String> stringResolver;
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
        out.append("@startsalt\n");
        out.append('{').append('\n');
        String title = o.title != null && !o.title.isEmpty()
                ? o.title : buildDefaultTitle(layout);
        if (!title.isEmpty()) {
            out.append("<b>").append(sanitize(title, 80)).append('\n');
        }
        LayoutViewNode root = layout.getRoot();
        if (root == null) {
            out.append("(no view hierarchy parsed)\n");
        } else {
            Counter counter = new Counter(o.maxNodes);
            emit(out, root, 0, o, counter);
            if (counter.truncated > 0) {
                out.append("--\n");
                out.append("<i>").append(counter.truncated)
                        .append(" more view(s) truncated (maxNodes=")
                        .append(o.maxNodes).append(")\n");
            }
        }
        out.append('}').append('\n');
        out.append("@endsalt\n");
        return out.toString();
    }

    /** ノードを再帰的に Salt へ出力する。 */
    private static void emit(StringBuilder out, LayoutViewNode node, int depth,
                             Options o, Counter counter) {
        if (counter.emitted >= counter.limit) {
            counter.truncated++;
            return;
        }
        counter.emitted++;

        boolean hasChildren = !node.getChildren().isEmpty();
        if (!hasChildren || depth >= o.maxDepth) {
            out.append(leafWidget(node, o)).append('\n');
            return;
        }

        // ViewGroup: 枠付きグリッド + 見出し行
        out.append("{+\n");
        out.append("<i>").append(groupCaption(node, o)).append('\n');
        out.append("--\n");
        if (isHorizontal(node)) {
            emitHorizontal(out, node, depth, o, counter);
        } else {
            for (LayoutViewNode child : node.getChildren()) {
                if (counter.emitted >= counter.limit) {
                    counter.truncated++;
                    break;
                }
                emit(out, child, depth + 1, o, counter);
            }
        }
        out.append("}\n");
    }

    /** 横並び ViewGroup の子を {@code |} 区切りで 1 行に並べる。 */
    private static void emitHorizontal(StringBuilder out, LayoutViewNode node, int depth,
                                       Options o, Counter counter) {
        boolean first = true;
        for (LayoutViewNode child : node.getChildren()) {
            if (counter.emitted >= counter.limit) {
                counter.truncated++;
                break;
            }
            if (!first) {
                out.append(" | ");
            }
            first = false;
            counter.emitted++;
            if (child.getChildren().isEmpty() || depth + 1 >= o.maxDepth) {
                out.append(leafWidget(child, o));
            } else {
                // 横並びセル内のネストはそのまま枠付きグリッドを埋め込む
                out.append("{+ ").append("<i>").append(groupCaption(child, o)).append(" | ");
                boolean inner = true;
                for (LayoutViewNode gc : child.getChildren()) {
                    if (!inner) {
                        out.append(" | ");
                    }
                    inner = false;
                    out.append(leafWidget(gc, o));
                }
                out.append(" }");
            }
        }
        out.append('\n');
    }

    /** 末端 View 1 つを Salt ウィジェット表記へ変換する。 */
    private static String leafWidget(LayoutViewNode node, Options o) {
        LayoutViewNode.Kind kind = node.classify();
        if (kind == LayoutViewNode.Kind.INCLUDE) {
            String ref = node.getIncludeLayoutRef();
            return "« include " + sanitize(ref != null ? ref : "?", o.textMaxLen) + " »";
        }
        if (kind == LayoutViewNode.Kind.FRAGMENT) {
            return "« fragment " + sanitize(shortClass(node.getFragmentClassName()), o.textMaxLen)
                    + " »";
        }
        String tag = node.shortTag().toLowerCase();
        String text = displayText(node, o);
        String label = !text.isEmpty() ? text : fallbackLabel(node, o);
        String safe = sanitize(label, o.textMaxLen);

        if (tag.endsWith("button") && !tag.contains("image") && !tag.contains("radio")) {
            return "[ " + safe + " ]";
        }
        if (tag.contains("edittext") || tag.contains("textinput") || tag.contains("autocomplete")) {
            // text が無ければ hint (android:hint) → id の順でプレースホルダ表示
            String inField = text;
            if (inField.isEmpty()) {
                inField = resolveAttr(node, "android:hint", o);
            }
            if (inField.isEmpty() && node.shortId() != null) {
                inField = node.shortId();
            }
            return "\"" + sanitize(inField, o.textMaxLen) + "\"";
        }
        if (tag.equals("checkbox") || tag.contains("checkedtextview")) {
            return "[] " + safe;
        }
        if (tag.contains("radiobutton")) {
            return "() " + safe;
        }
        if (tag.contains("switch") || tag.contains("togglebutton")) {
            return "[ ] " + safe;
        }
        if (tag.contains("imagebutton")) {
            return "[ <&image> ]";
        }
        if (tag.contains("imageview") || tag.equals("image")) {
            return "<&image> " + (node.shortId() != null ? sanitize(node.shortId(), o.textMaxLen) : "");
        }
        if (tag.contains("progressbar") || tag.contains("seekbar") || tag.contains("slider")) {
            return "<&media-play> " + safe + " [####------]";
        }
        if (tag.contains("spinner") || tag.contains("dropdown")) {
            return "^" + (text.isEmpty() ? "select" : safe) + "^";
        }
        // TextView およびその他: プレーンテキスト
        return safe;
    }

    /** ViewGroup の見出し (種別 + id)。 */
    private static String groupCaption(LayoutViewNode node, Options o) {
        StringBuilder sb = new StringBuilder(node.shortTag());
        if (isHorizontal(node)) {
            sb.append(" ▸");
        }
        if (o.showIds && node.shortId() != null) {
            sb.append(" #").append(node.shortId());
        }
        return sanitize(sb.toString(), o.textMaxLen > 0 ? o.textMaxLen + 12 : 0);
    }

    /** {@code android:text} を表示用テキストへ解決する ({@code @string/} は実文言へ)。 */
    private static String displayText(LayoutViewNode node, Options o) {
        String raw = node.getText();
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("@string/") && o.stringResolver != null) {
            String resolved = o.stringResolver.apply(raw);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        }
        return raw;
    }

    /** extraAttributes の属性値を取得し、{@code @string/} なら実文言へ解決する。無ければ空文字列。 */
    private static String resolveAttr(LayoutViewNode node, String attr, Options o) {
        String raw = node.getExtraAttributes().get(attr);
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("@string/") && o.stringResolver != null) {
            String resolved = o.stringResolver.apply(raw);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        }
        return raw;
    }

    /** text を持たない View のラベル: id があれば {@code #id}、無ければタグ名。 */
    private static String fallbackLabel(LayoutViewNode node, Options o) {
        if (o.showIds && node.shortId() != null) {
            return "#" + node.shortId();
        }
        return node.shortTag();
    }

    private static boolean isHorizontal(LayoutViewNode node) {
        if (!node.shortTag().toLowerCase().contains("linearlayout")) {
            return false;
        }
        String orient = node.getExtraAttributes().get("android:orientation");
        if (orient == null) {
            orient = node.getExtraAttributes().get("orientation");
        }
        return "horizontal".equals(orient);
    }

    private static String buildDefaultTitle(AndroidLayoutInfo layout) {
        StringBuilder sb = new StringBuilder("Screen: ");
        sb.append(layout.getFileName() != null && !layout.getFileName().isEmpty()
                ? layout.getFileName() : "(unnamed)");
        if (layout.getConfigQualifier() != null && !layout.getConfigQualifier().isEmpty()) {
            sb.append(" [").append(layout.getConfigQualifier()).append(']');
        }
        return sb.toString();
    }

    private static String shortClass(String fqn) {
        if (fqn == null) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    /**
     * Salt のグリッド文字 ({@code { } | [ ] " ^}) や改行を無害な文字へ置換し、
     * 最大長で切り詰める。
     */
    static String sanitize(String s, int max) {
        if (s == null) {
            return "";
        }
        String r = s.replace('|', '/')
                .replace('{', '(')
                .replace('}', ')')
                .replace('[', '(')
                .replace(']', ')')
                .replace('"', '\'')
                .replace('^', '\'')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (max > 0 && r.length() > max) {
            r = r.substring(0, max) + "…";
        }
        return r;
    }

    /** 再帰時の打ち切り管理用カウンタ。 */
    private static final class Counter {
        final int limit;
        int emitted = 0;
        int truncated = 0;

        Counter(int limit) {
            this.limit = limit > 0 ? limit : Integer.MAX_VALUE;
        }
    }

    private PlantUmlLayoutScreenDiagram() {
    }
}
