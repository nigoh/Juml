// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

import java.util.Locale;
import java.util.function.Function;

import juml.core.formats.android.AndroidLayoutInfo;
import juml.core.formats.android.LayoutViewNode;

/**
 * {@link AndroidLayoutEngine} が計測した {@link MeasuredView} ツリーを、
 * 実寸の SVG (PlantUML を介さないベクタ画像) へ描画する。
 *
 * <p>各 View は実際の {@code layout_*} 値で算出された矩形として描かれ、ボタン・入力欄・
 * 画像などはウィジェット種別ごとに色分け・装飾される。出力は単体で表示可能な
 * {@code <?xml ...><svg>…</svg>} 文字列で、{@code PlantUmlSvgRenderer} がそのまま
 * Batik で描画できる。</p>
 */
public final class AndroidLayoutSvgRenderer {

    /** 描画オプション。 */
    public static final class Options {
        /** ウィジェットの id を併記するか。 */
        public boolean showIds = true;
        /** 実寸サイズ (dp) の注記を併記するか。 */
        public boolean showSizes = true;
        /** 画面タイトル。null なら自動生成。 */
        public String title;
        /** SVG のスケール (dp に対する拡大率)。既定 2.0。 */
        public double scale = 2.0;
        /** {@code @string/foo} を実文言へ解決する関数。null なら参照のまま。 */
        public Function<String, String> stringResolver;
        /** 画面サイズ。null なら自動 (qualifier 由来 or スマホ縦)。 */
        public LayoutDevice device;
    }

    private static final String NS = "http://www.w3.org/2000/svg";
    private static final double FRAME_MARGIN = 24;
    private static final double TITLE_H = 26;

    /** 既定オプションで生成。 */
    public static String render(AndroidLayoutInfo layout) {
        return render(layout, null);
    }

    /** オプション付きで SVG 文字列を生成する。 */
    public static String render(AndroidLayoutInfo layout, Options opts) {
        if (layout == null) {
            throw new IllegalArgumentException("layout is null");
        }
        Options o = opts != null ? opts : new Options();
        LayoutDevice device = o.device != null ? o.device
                : LayoutDevice.fromQualifier(layout.getConfigQualifier());
        MeasuredView root = AndroidLayoutEngine.layout(layout, device, o.stringResolver);
        double scale = o.scale > 0 ? o.scale : 2.0;
        double contentW = device.getWidthDp();
        double contentH = device.getHeightDp();
        if (root != null) {
            contentW = Math.max(contentW, root.getX() + root.getWidth());
            contentH = Math.max(contentH, root.getY() + root.getHeight());
        }
        double frameW = contentW;
        double frameH = contentH;
        double canvasW = (frameW + FRAME_MARGIN * 2) * scale;
        double canvasH = (frameH + FRAME_MARGIN * 2 + TITLE_H) * scale;

        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
        sb.append("<svg xmlns=\"").append(NS).append("\" width=\"")
                .append(fmt(canvasW)).append("\" height=\"").append(fmt(canvasH))
                .append("\" viewBox=\"0 0 ").append(fmt(canvasW)).append(' ').append(fmt(canvasH))
                .append("\" font-family=\"'Segoe UI',Roboto,Helvetica,Arial,sans-serif\">\n");
        sb.append("<rect x=\"0\" y=\"0\" width=\"").append(fmt(canvasW)).append("\" height=\"")
                .append(fmt(canvasH)).append("\" fill=\"#FAFAFA\"/>\n");
        String title = o.title != null && !o.title.isEmpty() ? o.title : defaultTitle(layout, device);
        sb.append("<text x=\"").append(fmt(FRAME_MARGIN * scale)).append("\" y=\"")
                .append(fmt(18 * scale)).append("\" font-size=\"").append(fmt(13 * scale))
                .append("\" font-weight=\"bold\" fill=\"#202124\">").append(esc(title)).append("</text>\n");

        sb.append("<g transform=\"translate(").append(fmt(FRAME_MARGIN * scale)).append(',')
                .append(fmt((FRAME_MARGIN + TITLE_H) * scale)).append(") scale(").append(fmt(scale))
                .append(")\">\n");
        // デバイス枠 (実機画面の外周)。
        sb.append("<rect x=\"0\" y=\"0\" width=\"").append(fmt(frameW)).append("\" height=\"")
                .append(fmt(frameH))
                .append("\" fill=\"#FFFFFF\" stroke=\"#3C4043\" stroke-width=\"1.5\" rx=\"6\"/>\n");
        if (root == null) {
            sb.append("<text x=\"12\" y=\"28\" font-size=\"12\" fill=\"#B00020\">")
                    .append(esc("(no view hierarchy parsed)")).append("</text>\n");
        } else {
            emit(sb, root, o, 0);
            // ViewGroup の見出しは子の上に重ねて描く (子テキストとの被りを避ける)。
            // ルート (depth 0) はタイトル行と重複し先頭の子と被るため省略する。
            emitCaptions(sb, root, o, 0);
        }
        sb.append("</g>\n");
        sb.append("</svg>\n");
        return sb.toString();
    }

    /** ノードを再帰的に描画する (座標は dp、スケールは親 {@code <g>} が適用)。 */
    private static void emit(StringBuilder sb, MeasuredView mv, Options o, int depth) {
        Palette p = paletteFor(mv.getType());
        if (!mv.getChildren().isEmpty()) {
            drawContainer(sb, mv, p, o);
        } else {
            drawLeaf(sb, mv, p, o);
        }
        for (MeasuredView child : mv.getChildren()) {
            emit(sb, child, o, depth + 1);
        }
    }

    /** ViewGroup の枠を描画する (見出しは {@link #emitCaptions} で別パス描画)。 */
    private static void drawContainer(StringBuilder sb, MeasuredView mv, Palette p, Options o) {
        double x = mv.getX();
        double y = mv.getY();
        double w = mv.getWidth();
        double h = mv.getHeight();
        sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(w)).append("\" height=\"").append(fmt(h))
                .append("\" fill=\"").append(p.fill).append("\" fill-opacity=\"0.20\" stroke=\"")
                .append(p.stroke).append("\" stroke-width=\"0.75\" stroke-dasharray=\"3,2\"/>\n");
    }

    /** ViewGroup の見出しを子の上から半透明の小ピル付きで描く (被り対策の上書きパス)。 */
    private static void emitCaptions(StringBuilder sb, MeasuredView mv, Options o, int depth) {
        if (depth > 0 && !mv.getChildren().isEmpty()) {
            Palette p = paletteFor(mv.getType());
            String label = caption(mv, o);
            if (!label.isEmpty() && mv.getHeight() > 12) {
                String shown = truncate(label, mv.getWidth() - 6, 8);
                double pillW = Math.min(mv.getWidth(), shown.length() * 4.4 + 6);
                sb.append("<rect x=\"").append(fmt(mv.getX() + 1)).append("\" y=\"")
                        .append(fmt(mv.getY() + 1.5)).append("\" width=\"").append(fmt(pillW))
                        .append("\" height=\"11\" rx=\"2\" fill=\"#FFFFFF\" fill-opacity=\"0.78\"/>\n");
                sb.append("<text x=\"").append(fmt(mv.getX() + 3)).append("\" y=\"")
                        .append(fmt(mv.getY() + 10)).append("\" font-size=\"8\" fill=\"")
                        .append(p.text).append("\">").append(esc(shown)).append("</text>\n");
            }
        }
        for (MeasuredView child : mv.getChildren()) {
            emitCaptions(sb, child, o, depth + 1);
        }
    }

    /** 末端ウィジェットを種別ごとに装飾して描画する。 */
    private static void drawLeaf(StringBuilder sb, MeasuredView mv, Palette p, Options o) {
        double x = mv.getX();
        double y = mv.getY();
        double w = mv.getWidth();
        double h = mv.getHeight();
        WidgetType type = mv.getType();
        String text = displayText(mv.getNode(), o);
        switch (type) {
            case BUTTON:
                roundRect(sb, x, y, w, h, 6, p.fill, p.stroke);
                centerText(sb, x + w / 2, y + h / 2, w,
                        text.isEmpty() ? fallback(mv, o) : text, p.text, 10, true);
                return;
            case EDIT:
                rect(sb, x, y, w, h, "#FFFFFF", p.stroke, 0.75);
                sb.append("<line x1=\"").append(fmt(x)).append("\" y1=\"").append(fmt(y + h))
                        .append("\" x2=\"").append(fmt(x + w)).append("\" y2=\"").append(fmt(y + h))
                        .append("\" stroke=\"").append(p.stroke).append("\" stroke-width=\"1.5\"/>\n");
                leftText(sb, x + 4, y, h, editPlaceholder(mv, text, o), "#5F6368", 10);
                return;
            case IMAGE:
                rect(sb, x, y, w, h, p.fill, p.stroke, 0.75);
                sb.append("<line x1=\"").append(fmt(x)).append("\" y1=\"").append(fmt(y))
                        .append("\" x2=\"").append(fmt(x + w)).append("\" y2=\"").append(fmt(y + h))
                        .append("\" stroke=\"").append(p.stroke).append("\" stroke-width=\"0.5\"/>\n");
                sb.append("<line x1=\"").append(fmt(x + w)).append("\" y1=\"").append(fmt(y))
                        .append("\" x2=\"").append(fmt(x)).append("\" y2=\"").append(fmt(y + h))
                        .append("\" stroke=\"").append(p.stroke).append("\" stroke-width=\"0.5\"/>\n");
                centerText(sb, x + w / 2, y + h / 2, w, "IMG", p.text, 8, false);
                return;
            case CHECK:
            case RADIO:
            case SWITCH:
                drawToggle(sb, mv, p, o, text);
                return;
            case PROGRESS:
                rect(sb, x, y + h / 2 - 2, w, 4, "#E0E0E0", "#E0E0E0", 0);
                rect(sb, x, y + h / 2 - 2, w * 0.4, 4, p.fill, p.fill, 0);
                return;
            case SPINNER:
                rect(sb, x, y, w, h, "#FFFFFF", p.stroke, 0.75);
                leftText(sb, x + 4, y, h, text.isEmpty() ? "select" : text, "#202124", 10);
                sb.append("<text x=\"").append(fmt(x + w - 8)).append("\" y=\"").append(fmt(y + h / 2 + 3))
                        .append("\" font-size=\"9\" fill=\"#5F6368\">").append("▾").append("</text>\n");
                return;
            case INCLUDE:
            case FRAGMENT:
                rect(sb, x, y, w, h, p.fill, p.stroke, 0.75);
                centerText(sb, x + w / 2, y + h / 2, w, placeholderLabel(mv), p.text, 9, false);
                return;
            case TEXT:
                rect(sb, x, y, w, h, "none", "#E8EAED", 0.5);
                leftText(sb, x + 2, y, h, text.isEmpty() ? fallback(mv, o) : text, p.text, 10);
                return;
            case CONTAINER:
            case SCROLL:
            case GENERIC:
            default:
                rect(sb, x, y, w, h, p.fill, p.stroke, 0.75);
                centerText(sb, x + w / 2, y + h / 2, w,
                        text.isEmpty() ? fallback(mv, o) : text, p.text, 9, false);
        }
    }

    /** チェック/ラジオ/スイッチの操作部 + ラベルを描画する。 */
    private static void drawToggle(StringBuilder sb, MeasuredView mv, Palette p, Options o, String text) {
        double x = mv.getX();
        double y = mv.getY();
        double h = mv.getHeight();
        double cy = y + h / 2;
        WidgetType type = mv.getType();
        if (type == WidgetType.RADIO) {
            sb.append("<circle cx=\"").append(fmt(x + 9)).append("\" cy=\"").append(fmt(cy))
                    .append("\" r=\"6\" fill=\"#FFFFFF\" stroke=\"").append(p.stroke)
                    .append("\" stroke-width=\"1\"/>\n");
        } else if (type == WidgetType.SWITCH) {
            roundRect(sb, x, cy - 6, 22, 12, 6, "#E0E0E0", p.stroke);
            sb.append("<circle cx=\"").append(fmt(x + 16)).append("\" cy=\"").append(fmt(cy))
                    .append("\" r=\"5\" fill=\"").append(p.stroke).append("\"/>\n");
        } else {
            rect(sb, x + 3, cy - 6, 12, 12, "#FFFFFF", p.stroke, 1);
        }
        String label = text.isEmpty() ? fallback(mv, o) : text;
        double labelX = type == WidgetType.SWITCH ? x + 28 : x + 20;
        leftText(sb, labelX, y, h, label, "#202124", 10);
    }

    // ----------------------------------------------------------------------
    // 低レベル SVG ヘルパ
    // ----------------------------------------------------------------------

    private static void rect(StringBuilder sb, double x, double y, double w, double h,
                             String fill, String stroke, double sw) {
        sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(w)).append("\" height=\"").append(fmt(h))
                .append("\" fill=\"").append(fill).append('"');
        if (sw > 0) {
            sb.append(" stroke=\"").append(stroke).append("\" stroke-width=\"").append(fmt(sw)).append('"');
        }
        sb.append("/>\n");
    }

    private static void roundRect(StringBuilder sb, double x, double y, double w, double h,
                                  double r, String fill, String stroke) {
        sb.append("<rect x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(w)).append("\" height=\"").append(fmt(h))
                .append("\" rx=\"").append(fmt(r)).append("\" fill=\"").append(fill)
                .append("\" stroke=\"").append(stroke).append("\" stroke-width=\"0.75\"/>\n");
    }

    private static void centerText(StringBuilder sb, double cx, double cy, double maxW,
                                   String text, String color, double size, boolean bold) {
        if (text == null || text.isEmpty()) {
            return;
        }
        sb.append("<text x=\"").append(fmt(cx)).append("\" y=\"").append(fmt(cy + size * 0.35))
                .append("\" font-size=\"").append(fmt(size)).append("\" text-anchor=\"middle\" fill=\"")
                .append(color).append('"');
        if (bold) {
            sb.append(" font-weight=\"bold\"");
        }
        sb.append('>').append(esc(truncate(text, maxW - 4, size))).append("</text>\n");
    }

    private static void leftText(StringBuilder sb, double x, double y, double h,
                                 String text, String color, double size) {
        if (text == null || text.isEmpty()) {
            return;
        }
        sb.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(fmt(y + h / 2 + size * 0.35))
                .append("\" font-size=\"").append(fmt(size)).append("\" fill=\"").append(color)
                .append("\">").append(esc(truncate(text, 1000, size))).append("</text>\n");
    }

    // ----------------------------------------------------------------------
    // ラベル / テキスト解決
    // ----------------------------------------------------------------------

    private static String caption(MeasuredView mv, Options o) {
        LayoutViewNode node = mv.getNode();
        StringBuilder sb = new StringBuilder(node.shortTag());
        if (o.showIds && node.shortId() != null) {
            sb.append(" #").append(node.shortId());
        }
        if (o.showSizes) {
            sb.append("  ").append(sizeLabel(node));
        }
        return sb.toString();
    }

    private static String sizeLabel(LayoutViewNode node) {
        return dimLabel(node.getWidth()) + "×" + dimLabel(node.getHeight());
    }

    private static String dimLabel(String spec) {
        if (spec == null || spec.isEmpty()) {
            return "wrap";
        }
        String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.equals("match_parent") || s.equals("fill_parent")) {
            return "match";
        }
        if (s.equals("wrap_content")) {
            return "wrap";
        }
        return spec;
    }

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

    /** EditText の表示文字: text → hint (android:hint, @string 解決) → id の順。 */
    private static String editPlaceholder(MeasuredView mv, String text, Options o) {
        if (!text.isEmpty()) {
            return text;
        }
        String hint = mv.getNode().getExtraAttributes().get("android:hint");
        if (hint == null) {
            hint = mv.getNode().getExtraAttributes().get("hint");
        }
        if (hint != null && hint.startsWith("@string/") && o.stringResolver != null) {
            String resolved = o.stringResolver.apply(hint);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        }
        if (hint != null && !hint.isEmpty()) {
            return hint;
        }
        return fallback(mv, o);
    }

    private static String fallback(MeasuredView mv, Options o) {
        LayoutViewNode node = mv.getNode();
        if (o.showIds && node.shortId() != null) {
            return "#" + node.shortId();
        }
        return node.shortTag();
    }

    private static String placeholderLabel(MeasuredView mv) {
        LayoutViewNode node = mv.getNode();
        if (mv.getType() == WidgetType.INCLUDE) {
            String ref = node.getIncludeLayoutRef();
            return "« include " + (ref != null ? shortName(ref) : "?") + " »";
        }
        String fqn = node.getFragmentClassName();
        return "« fragment " + (fqn != null ? shortClass(fqn) : "?") + " »";
    }

    private static String shortClass(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private static String shortName(String ref) {
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }

    private static String defaultTitle(AndroidLayoutInfo layout, LayoutDevice device) {
        StringBuilder sb = new StringBuilder();
        sb.append(layout.getFileName() != null && !layout.getFileName().isEmpty()
                ? layout.getFileName() : "(unnamed)");
        if (layout.getConfigQualifier() != null && !layout.getConfigQualifier().isEmpty()) {
            sb.append(" [").append(layout.getConfigQualifier()).append(']');
        }
        sb.append("  —  ").append(device.getWidthDp()).append('×')
                .append(device.getHeightDp()).append("dp");
        return sb.toString();
    }

    /** テキストを概算文字幅で切り詰める。{@code maxWidth} は dp。 */
    private static String truncate(String s, double maxWidth, double fontSize) {
        if (s == null) {
            return "";
        }
        String r = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        int maxChars = (int) Math.max(1, maxWidth / (fontSize * 0.58));
        if (r.length() <= maxChars) {
            return r;
        }
        if (maxChars <= 1) {
            return "…";
        }
        return r.substring(0, maxChars - 1) + "…";
    }

    /** XML テキスト用エスケープ。 */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 数値を SVG 用に整形 (小数は 2 桁まで、整数は小数点なし)。 */
    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }

    /** ウィジェット種別ごとの配色。 */
    private static final class Palette {
        final String fill;
        final String stroke;
        final String text;

        Palette(String fill, String stroke, String text) {
            this.fill = fill;
            this.stroke = stroke;
            this.text = text;
        }
    }

    private static Palette paletteFor(WidgetType type) {
        switch (type) {
            case BUTTON:
                return new Palette("#6750A4", "#4F378B", "#FFFFFF");
            case EDIT:
                return new Palette("#FFFFFF", "#6750A4", "#5F6368");
            case IMAGE:
                return new Palette("#E8EAED", "#9AA0A6", "#5F6368");
            case CHECK:
            case RADIO:
            case SWITCH:
                return new Palette("#FFFFFF", "#6750A4", "#202124");
            case PROGRESS:
                return new Palette("#6750A4", "#6750A4", "#202124");
            case SPINNER:
                return new Palette("#FFFFFF", "#9AA0A6", "#202124");
            case INCLUDE:
                return new Palette("#FFF8E1", "#F9A825", "#7A5900");
            case FRAGMENT:
                return new Palette("#E1F5FE", "#0288D1", "#014361");
            case SCROLL:
                return new Palette("#F1F3F4", "#80868B", "#5F6368");
            case TEXT:
                return new Palette("none", "#E8EAED", "#202124");
            case CONTAINER:
                return new Palette("#E3F2FD", "#42A5F5", "#1565C0");
            case GENERIC:
            default:
                return new Palette("#FAFAFA", "#BDBDBD", "#5F6368");
        }
    }

    private AndroidLayoutSvgRenderer() {
    }
}
