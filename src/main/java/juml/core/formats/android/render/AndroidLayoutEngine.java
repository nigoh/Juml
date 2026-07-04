// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import juml.core.formats.android.AndroidLayoutInfo;
import juml.core.formats.android.LayoutViewNode;

/**
 * Android layout XML の View 階層を、{@code layout_width}/{@code layout_height}/
 * {@code layout_weight}/{@code orientation}/{@code gravity}/margin/padding といった
 * <b>実際の設定値</b> から計測し、画面上の絶対座標 (dp) を持つ {@link MeasuredView}
 * ツリーへ変換する簡易レイアウトエンジン。
 *
 * <p>Android のレイアウトアルゴリズムを完全再現するものではなく、画面の見た目を
 * 近似する目的の軽量実装:</p>
 * <ul>
 *   <li>{@code LinearLayout} / {@code RadioGroup}: orientation に従って縦/横配置。
 *       {@code layout_weight} で余白を比例配分し、{@code layout_gravity} で交差軸方向を寄せる。</li>
 *   <li>{@code FrameLayout} / {@code CardView}: 子を重ねて配置 (gravity で寄せ)。</li>
 *   <li>{@code RelativeLayout} / {@code ConstraintLayout} / その他コンテナ: 制約解決は行わず
 *       縦積みで近似する。</li>
 *   <li>{@code ScrollView} 系: 縦積みで近似 (内容は画面より高くなりうる)。</li>
 *   <li>末端 View: {@link WidgetCatalog} の固有サイズで wrap_content を見積もる。</li>
 * </ul>
 */
public final class AndroidLayoutEngine {

    private static final int MAX_NODES = 2000;
    private static final int MAX_DEPTH = 40;

    private final Function<String, String> stringResolver;
    private int nodeBudget = MAX_NODES;

    private AndroidLayoutEngine(Function<String, String> stringResolver) {
        this.stringResolver = stringResolver;
    }

    /**
     * レイアウトを計測して絶対座標付きの {@link MeasuredView} ルートを返す。
     *
     * @param info     解析済みレイアウト (root が null なら null を返す)
     * @param device   画面サイズ
     * @param resolver {@code @string/foo} を実文言へ解決する関数 (null 可)
     */
    public static MeasuredView layout(AndroidLayoutInfo info, LayoutDevice device,
                                      Function<String, String> resolver) {
        if (info == null || info.getRoot() == null) {
            return null;
        }
        LayoutDevice dev = device != null ? device : LayoutDevice.phonePortrait();
        AndroidLayoutEngine engine = new AndroidLayoutEngine(resolver);
        MeasuredView root = engine.measure(info.getRoot(), dev.getWidthDp(), dev.getHeightDp(), 0);
        root.setPosition(0, 0);
        absolutize(root);
        return root;
    }

    /**
     * 各ノードの子座標 (親の境界ボックス左上を原点とした相対値) を、画面左上原点の
     * 絶対座標へ変換する。トップダウンに親の絶対座標を加算していく。
     */
    private static void absolutize(MeasuredView node) {
        for (MeasuredView child : node.getChildren()) {
            child.setPosition(node.getX() + child.getX(), node.getY() + child.getY());
            absolutize(child);
        }
    }

    /** ノードを計測する。返り値の子は親の境界ボックス左上を原点とした相対座標を持つ。 */
    private MeasuredView measure(LayoutViewNode node, double availW, double availH, int depth) {
        WidgetType type = WidgetCatalog.classify(node);
        boolean container = !node.getChildren().isEmpty()
                && type != WidgetType.INCLUDE && type != WidgetType.FRAGMENT
                && depth < MAX_DEPTH && nodeBudget > 0;
        if (!container) {
            return measureLeaf(node, type, availW, availH);
        }
        CSpec spec = new CSpec(type, Dim.parse(node.getWidth()), Dim.parse(node.getHeight()),
                padding(node));
        Ctx ctx = new Ctx(availW, availH, depth);
        if (WidgetCatalog.isOverlapContainer(node)) {
            return measureFrame(node, spec, ctx);
        }
        boolean horizontal = WidgetCatalog.isHorizontalLinear(node);
        boolean weighted = WidgetCatalog.isLinear(node);
        return measureLinear(node, spec, ctx, horizontal, weighted);
    }

    /** 末端 View の計測。固有サイズで wrap_content を見積もる。 */
    private MeasuredView measureLeaf(LayoutViewNode node, WidgetType type,
                                     double availW, double availH) {
        nodeBudget--;
        Box pad = padding(node);
        int textLen = displayTextLen(node);
        double intrinsicW = WidgetCatalog.intrinsicWidthDp(type, textLen) + pad.horizontal();
        double intrinsicH = WidgetCatalog.intrinsicHeightDp(type) + pad.vertical();
        double w = resolveAxis(Dim.parse(node.getWidth()), availW, intrinsicW);
        double h = resolveAxis(Dim.parse(node.getHeight()), availH, intrinsicH);
        return new MeasuredView(node, type, Math.max(1, w), Math.max(1, h));
    }

    /** 線形配置 (縦 or 横)。weighted=false のコンテナ (Relative 等) は縦積み近似。 */
    private MeasuredView measureLinear(LayoutViewNode node, CSpec spec, Ctx ctx,
                                       boolean horizontal, boolean weighted) {
        nodeBudget--;
        Box pad = spec.pad;
        double contentAvailW = Math.max(0,
                (spec.wSpec.isExact() ? spec.wSpec.value : ctx.availW) - pad.horizontal());
        double contentAvailH = Math.max(0,
                (spec.hSpec.isExact() ? spec.hSpec.value : ctx.availH) - pad.vertical());
        MeasuredView self = new MeasuredView(node, spec.type, 0, 0);
        double mainUsed = 0;
        double crossMax = 0;
        double totalWeight = 0;
        for (LayoutViewNode child : node.getChildren()) {
            if (nodeBudget <= 0) {
                break;
            }
            Box m = margins(child);
            double cw = horizontal ? contentAvailW : Math.max(0, contentAvailW - m.horizontal());
            double ch = horizontal ? Math.max(0, contentAvailH - m.vertical()) : contentAvailH;
            MeasuredView mv = measure(child, cw, ch, ctx.depth + 1);
            self.getChildren().add(mv);
            totalWeight += weighted ? weight(child) : 0;
            double main = horizontal ? mv.getWidth() + m.horizontal() : mv.getHeight() + m.vertical();
            double cross = horizontal ? mv.getHeight() + m.vertical() : mv.getWidth() + m.horizontal();
            mainUsed += main;
            crossMax = Math.max(crossMax, cross);
        }
        boolean mainBounded = horizontal ? !spec.wSpec.isWrap() : !spec.hSpec.isWrap();
        double mainAvail = horizontal ? contentAvailW : contentAvailH;
        if (weighted && totalWeight > 0 && mainBounded) {
            distributeWeight(self, node, horizontal, mainAvail - mainUsed, totalWeight);
            mainUsed = mainAvail;
        }
        finalizeContainerSize(self, spec, ctx,
                horizontal ? mainUsed : crossMax, horizontal ? crossMax : mainUsed);
        positionLinearChildren(self, node, pad, horizontal);
        return self;
    }

    /** weight を持つ子へ余白を比例配分し、主軸サイズを伸縮させる。 */
    private void distributeWeight(MeasuredView self, LayoutViewNode node,
                                  boolean horizontal, double leftover, double totalWeight) {
        List<LayoutViewNode> srcChildren = node.getChildren();
        int n = Math.min(self.getChildren().size(), srcChildren.size());
        for (int i = 0; i < n; i++) {
            double wt = weight(srcChildren.get(i));
            if (wt <= 0) {
                continue;
            }
            MeasuredView mv = self.getChildren().get(i);
            double extra = leftover * (wt / totalWeight);
            if (horizontal) {
                mv.setWidth(Math.max(1, mv.getWidth() + extra));
            } else {
                mv.setHeight(Math.max(1, mv.getHeight() + extra));
            }
        }
    }

    /** 線形配置の子を主軸に沿って並べ、交差軸方向を gravity で寄せる。 */
    private void positionLinearChildren(MeasuredView self, LayoutViewNode node,
                                        Box pad, boolean horizontal) {
        double contentW = self.getWidth() - pad.horizontal();
        double contentH = self.getHeight() - pad.vertical();
        double cursor = horizontal ? pad.left : pad.top;
        List<MeasuredView> kids = self.getChildren();
        List<LayoutViewNode> src = node.getChildren();
        for (int i = 0; i < kids.size(); i++) {
            MeasuredView mv = kids.get(i);
            Box m = margins(src.get(i));
            String grav = layoutGravity(src.get(i), node);
            if (horizontal) {
                double y = pad.top + m.top + crossOffset(grav, contentH, mv.getHeight(), m, false);
                mv.setPosition(cursor + m.left, y);
                cursor += m.horizontal() + mv.getWidth();
            } else {
                double x = pad.left + m.left + crossOffset(grav, contentW, mv.getWidth(), m, true);
                mv.setPosition(x, cursor + m.top);
                cursor += m.vertical() + mv.getHeight();
            }
        }
    }

    /** FrameLayout/CardView: 子を重ね、gravity で寄せる。 */
    private MeasuredView measureFrame(LayoutViewNode node, CSpec spec, Ctx ctx) {
        nodeBudget--;
        Box pad = spec.pad;
        double contentAvailW = Math.max(0,
                (spec.wSpec.isExact() ? spec.wSpec.value : ctx.availW) - pad.horizontal());
        double contentAvailH = Math.max(0,
                (spec.hSpec.isExact() ? spec.hSpec.value : ctx.availH) - pad.vertical());
        MeasuredView self = new MeasuredView(node, spec.type, 0, 0);
        double maxW = 0;
        double maxH = 0;
        for (LayoutViewNode child : node.getChildren()) {
            if (nodeBudget <= 0) {
                break;
            }
            Box m = margins(child);
            MeasuredView mv = measure(child, Math.max(0, contentAvailW - m.horizontal()),
                    Math.max(0, contentAvailH - m.vertical()), ctx.depth + 1);
            self.getChildren().add(mv);
            maxW = Math.max(maxW, mv.getWidth() + m.horizontal());
            maxH = Math.max(maxH, mv.getHeight() + m.vertical());
        }
        finalizeContainerSize(self, spec, ctx, maxW, maxH);
        double contentW = self.getWidth() - pad.horizontal();
        double contentH = self.getHeight() - pad.vertical();
        List<LayoutViewNode> src = node.getChildren();
        for (int i = 0; i < self.getChildren().size(); i++) {
            MeasuredView mv = self.getChildren().get(i);
            Box m = margins(src.get(i));
            String grav = layoutGravity(src.get(i), node);
            double x = pad.left + m.left + crossOffset(grav, contentW, mv.getWidth(), m, true);
            double y = pad.top + m.top + crossOffset(grav, contentH, mv.getHeight(), m, false);
            mv.setPosition(x, y);
        }
        return self;
    }

    /** コンテナの最終サイズを width/height spec と内容サイズから確定する。 */
    private void finalizeContainerSize(MeasuredView self, CSpec spec, Ctx ctx,
                                       double contentW, double contentH) {
        double w = resolveAxis(spec.wSpec, ctx.availW, contentW + spec.pad.horizontal());
        double h = resolveAxis(spec.hSpec, ctx.availH, contentH + spec.pad.vertical());
        self.setSize(Math.max(1, w), Math.max(1, h));
    }

    /** width/height spec を解決する。MATCH→avail、EXACT→値、WRAP→intrinsic。 */
    private static double resolveAxis(Dim spec, double avail, double intrinsic) {
        switch (spec.mode) {
            case EXACT:
                return spec.value;
            case MATCH:
                return Math.max(0, avail);
            case WRAP:
            default:
                return intrinsic;
        }
    }

    /**
     * 交差軸方向のオフセット (gravity による寄せ)。
     * {@code horizontalAxis=true} なら left/right/center_horizontal/start/end を、
     * false なら top/bottom/center_vertical を解釈する。
     */
    private static double crossOffset(String gravity, double containerCross,
                                      double childCross, Box margin, boolean horizontalAxis) {
        if (gravity == null || gravity.isEmpty()) {
            return 0;
        }
        String g = gravity.toLowerCase(Locale.ROOT);
        double free = containerCross - childCross - (horizontalAxis ? margin.horizontal() : margin.vertical());
        if (free <= 0) {
            return 0;
        }
        // gravity は '|' 区切りのトークン集合。substring で "center" を見ると
        // center_vertical/center_horizontal まで拾ってしまい、反対軸を誤って中央寄せする。
        // トークン単位で「両軸中央 (center)」と各軸中央を厳密に判定する。
        boolean centerBoth = false;
        boolean centerH = false;
        boolean centerV = false;
        for (String tok : g.split("\\|")) {
            String t = tok.trim();
            if (t.equals("center")) {
                centerBoth = true;
            } else if (t.equals("center_horizontal")) {
                centerH = true;
            } else if (t.equals("center_vertical")) {
                centerV = true;
            }
        }
        if (horizontalAxis) {
            if (g.contains("right") || g.contains("end")) {
                return free;
            }
            if (centerBoth || centerH) {
                return free / 2;
            }
        } else {
            if (g.contains("bottom")) {
                return free;
            }
            if (centerBoth || centerV) {
                return free / 2;
            }
        }
        return 0;
    }

    // ----------------------------------------------------------------------
    // 属性の取得・パース
    // ----------------------------------------------------------------------

    private int displayTextLen(LayoutViewNode node) {
        String t = node.getText();
        if (t != null && t.startsWith("@string/") && stringResolver != null) {
            String r = stringResolver.apply(t);
            if (r != null && !r.isEmpty()) {
                t = r;
            }
        }
        if (t == null || t.isEmpty()) {
            t = attr(node, "android:hint");
        }
        if ((t == null || t.isEmpty()) && node.shortId() != null) {
            t = node.shortId();
        }
        return t == null ? 0 : t.length();
    }

    private static double weight(LayoutViewNode node) {
        return parseDp(attr(node, "android:layout_weight"));
    }

    private static Box margins(LayoutViewNode node) {
        double all = parseDp(attr(node, "android:layout_margin"));
        double l = firstPositive(parseDp(attr(node, "android:layout_marginLeft")),
                parseDp(attr(node, "android:layout_marginStart")), all);
        double t = firstPositive(parseDp(attr(node, "android:layout_marginTop")), all);
        double r = firstPositive(parseDp(attr(node, "android:layout_marginRight")),
                parseDp(attr(node, "android:layout_marginEnd")), all);
        double b = firstPositive(parseDp(attr(node, "android:layout_marginBottom")), all);
        return new Box(l, t, r, b);
    }

    private static Box padding(LayoutViewNode node) {
        double all = parseDp(attr(node, "android:padding"));
        double l = firstPositive(parseDp(attr(node, "android:paddingLeft")),
                parseDp(attr(node, "android:paddingStart")), all);
        double t = firstPositive(parseDp(attr(node, "android:paddingTop")), all);
        double r = firstPositive(parseDp(attr(node, "android:paddingRight")),
                parseDp(attr(node, "android:paddingEnd")), all);
        double b = firstPositive(parseDp(attr(node, "android:paddingBottom")), all);
        return new Box(l, t, r, b);
    }

    private static String layoutGravity(LayoutViewNode child, LayoutViewNode parent) {
        String g = attr(child, "android:layout_gravity");
        if (g != null && !g.isEmpty()) {
            return g;
        }
        // LinearLayout の android:gravity は子のデフォルト寄せに使われる。
        return attr(parent, "android:gravity");
    }

    /** {@code android:foo} (名前空間付き / 素のどちらでも) の値を extraAttributes から引く。 */
    private static String attr(LayoutViewNode node, String key) {
        Map<String, String> ex = node.getExtraAttributes();
        String v = ex.get(key);
        if (v == null) {
            v = ex.get(key.substring(key.indexOf(':') + 1));
        }
        return v;
    }

    /** {@code "16dp"} / {@code "16dip"} / {@code "16px"} / {@code "16sp"} / {@code "16"} → 数値。 */
    static double parseDp(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        String s = value.trim().toLowerCase(Locale.ROOT);
        int end = s.length();
        for (String unit : new String[] {"dip", "dp", "px", "sp", "pt", "mm", "in"}) {
            if (s.endsWith(unit)) {
                end = s.length() - unit.length();
                break;
            }
        }
        try {
            double d = Double.parseDouble(s.substring(0, end).trim());
            // 非有限値 (1e400dp → Infinity, 演算で NaN) は SVG に width="Infinity"/"NaN" を
            // 吐いて図全体の描画を壊すため 0 に丸める。
            return Double.isFinite(d) ? d : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double firstPositive(double... vals) {
        for (double v : vals) {
            if (v > 0) {
                return v;
            }
        }
        return 0;
    }

    /** {@code layout_width}/{@code layout_height} の解決済み指定。 */
    private static final class Dim {
        enum Mode { EXACT, MATCH, WRAP }

        final Mode mode;
        final double value;

        private Dim(Mode mode, double value) {
            this.mode = mode;
            this.value = value;
        }

        static Dim parse(String spec) {
            if (spec == null || spec.isEmpty()) {
                return new Dim(Mode.WRAP, 0);
            }
            String s = spec.trim().toLowerCase(Locale.ROOT);
            if (s.equals("match_parent") || s.equals("fill_parent")) {
                return new Dim(Mode.MATCH, 0);
            }
            if (s.equals("wrap_content")) {
                return new Dim(Mode.WRAP, 0);
            }
            return new Dim(Mode.EXACT, parseDp(spec));
        }

        boolean isExact() {
            return mode == Mode.EXACT;
        }

        boolean isWrap() {
            return mode == Mode.WRAP;
        }
    }

    /** コンテナの解決済み指定 (種別 + width/height + padding)。引数束ね用。 */
    private static final class CSpec {
        final WidgetType type;
        final Dim wSpec;
        final Dim hSpec;
        final Box pad;

        CSpec(WidgetType type, Dim wSpec, Dim hSpec, Box pad) {
            this.type = type;
            this.wSpec = wSpec;
            this.hSpec = hSpec;
            this.pad = pad;
        }
    }

    /** 計測時の再帰コンテキスト (利用可能サイズ + 深さ)。引数束ね用。 */
    private static final class Ctx {
        final double availW;
        final double availH;
        final int depth;

        Ctx(double availW, double availH, int depth) {
            this.availW = availW;
            this.availH = availH;
            this.depth = depth;
        }
    }

    /** 上下左右の余白 (margin / padding 共用)。 */
    private static final class Box {
        final double left;
        final double top;
        final double right;
        final double bottom;

        Box(double left, double top, double right, double bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        double horizontal() {
            return left + right;
        }

        double vertical() {
            return top + bottom;
        }
    }
}
