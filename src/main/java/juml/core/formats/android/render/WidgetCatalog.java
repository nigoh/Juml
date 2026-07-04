// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

import juml.core.formats.android.LayoutViewNode;

/**
 * タグ名 → {@link WidgetType} の分類と、ウィジェット種別ごとの固有サイズ (dp) /
 * 表示色を提供するカタログ。
 *
 * <p>{@link AndroidLayoutEngine} (計測) と {@link AndroidLayoutSvgRenderer} (描画) の
 * 両方から参照し、「ボタンは高さ 48dp」「画像は灰色」のような既定値を一箇所に集約する。
 * 値は Android のマテリアル系の実測に概ね沿わせた近似で、厳密値ではない。</p>
 */
public final class WidgetCatalog {

    /** タグ名から大分類を判定する。{@code node} の種別 (include/fragment) を優先する。 */
    public static WidgetType classify(LayoutViewNode node) {
        if (node == null) {
            return WidgetType.GENERIC;
        }
        LayoutViewNode.Kind kind = node.classify();
        if (kind == LayoutViewNode.Kind.INCLUDE) {
            return WidgetType.INCLUDE;
        }
        if (kind == LayoutViewNode.Kind.FRAGMENT) {
            return WidgetType.FRAGMENT;
        }
        String tag = node.shortTag().toLowerCase(java.util.Locale.ROOT);
        WidgetType leaf = classifyLeafTag(tag);
        if (leaf != null) {
            return leaf;
        }
        if (isScrollTag(tag)) {
            return WidgetType.SCROLL;
        }
        // 子を持つ、または ViewGroup と判定された要素はコンテナ。
        if (!node.getChildren().isEmpty() || kind == LayoutViewNode.Kind.VIEW_GROUP
                || kind == LayoutViewNode.Kind.MERGE || isContainerTag(tag)) {
            return WidgetType.CONTAINER;
        }
        return WidgetType.GENERIC;
    }

    /** 末端ウィジェットのタグ判定。該当しなければ null。 */
    private static WidgetType classifyLeafTag(String tag) {
        if (tag.contains("imagebutton")) {
            return WidgetType.IMAGE;
        }
        if (tag.endsWith("button") && !tag.contains("radio")) {
            return WidgetType.BUTTON;
        }
        if (tag.contains("edittext") || tag.contains("textinputedittext")
                || tag.contains("autocomplete")) {
            return WidgetType.EDIT;
        }
        if (tag.contains("radiobutton")) {
            return WidgetType.RADIO;
        }
        if (tag.equals("checkbox") || tag.contains("checkedtextview")) {
            return WidgetType.CHECK;
        }
        if (tag.contains("switch") || tag.contains("togglebutton")) {
            return WidgetType.SWITCH;
        }
        if (tag.contains("imageview") || tag.equals("image")) {
            return WidgetType.IMAGE;
        }
        if (tag.contains("progressbar") || tag.contains("seekbar") || tag.contains("slider")
                || tag.contains("ratingbar")) {
            return WidgetType.PROGRESS;
        }
        if (tag.contains("spinner")) {
            return WidgetType.SPINNER;
        }
        if (tag.contains("textview") || tag.equals("textclock") || tag.equals("chronometer")) {
            return WidgetType.TEXT;
        }
        return null;
    }

    private static boolean isScrollTag(String tag) {
        return tag.contains("scrollview") || tag.contains("recyclerview")
                || tag.contains("listview") || tag.contains("gridview")
                || tag.contains("viewpager") || tag.contains("nestedscroll");
    }

    private static boolean isContainerTag(String tag) {
        return tag.contains("layout") || tag.contains("cardview")
                || tag.contains("merge") || tag.endsWith("group")
                || tag.contains("toolbar") || tag.contains("appbar");
    }

    /** {@code FrameLayout} 系 (子を重ねる) かどうか。 */
    public static boolean isOverlapContainer(LayoutViewNode node) {
        String tag = node.shortTag().toLowerCase(java.util.Locale.ROOT);
        return tag.contains("framelayout") || tag.contains("cardview");
    }

    /** {@code LinearLayout} で {@code orientation="horizontal"} かどうか。 */
    public static boolean isHorizontalLinear(LayoutViewNode node) {
        String tag = node.shortTag().toLowerCase(java.util.Locale.ROOT);
        boolean linearLayout = tag.contains("linearlayout");
        boolean radioGroup = tag.contains("radiogroup");
        if (!linearLayout && !radioGroup) {
            return false;
        }
        String o = orientation(node);
        if (o != null) {
            return "horizontal".equals(o.trim().toLowerCase(java.util.Locale.ROOT));
        }
        // orientation 省略時の Android 既定: LinearLayout は horizontal、RadioGroup は vertical。
        // (以前は省略を一律 vertical 扱いしており、横並びレイアウトが縦に崩れていた。)
        return linearLayout;
    }

    /** {@code LinearLayout} 系 (縦/横の線形配置) かどうか。 */
    public static boolean isLinear(LayoutViewNode node) {
        String tag = node.shortTag().toLowerCase(java.util.Locale.ROOT);
        return tag.contains("linearlayout") || tag.contains("radiogroup");
    }

    private static String orientation(LayoutViewNode node) {
        String o = node.getExtraAttributes().get("android:orientation");
        if (o == null) {
            o = node.getExtraAttributes().get("orientation");
        }
        return o;
    }

    /** 末端ウィジェットの既定の高さ (dp)。 */
    public static double intrinsicHeightDp(WidgetType type) {
        switch (type) {
            case BUTTON:
            case EDIT:
            case SPINNER:
                return 48;
            case CHECK:
            case RADIO:
            case SWITCH:
                return 40;
            case TEXT:
                return 24;
            case IMAGE:
                return 80;
            case PROGRESS:
                return 16;
            case INCLUDE:
            case FRAGMENT:
                return 96;
            case CONTAINER:
            case SCROLL:
            case GENERIC:
            default:
                return 24;
        }
    }

    /** 末端ウィジェットの既定の幅 (dp)。{@code textLen} は表示テキストの文字数。 */
    public static double intrinsicWidthDp(WidgetType type, int textLen) {
        double textW = textLen * 7.0;
        switch (type) {
            case BUTTON:
                return Math.max(88, textW + 32);
            case EDIT:
                return Math.max(120, textW + 16);
            case TEXT:
                return Math.max(24, textW + 8);
            case CHECK:
            case RADIO:
            case SWITCH:
                return 40 + Math.max(0, textW) + 8;
            case IMAGE:
                return 80;
            case SPINNER:
                return Math.max(140, textW + 48);
            case PROGRESS:
                return 160;
            case INCLUDE:
            case FRAGMENT:
                return 200;
            case CONTAINER:
            case SCROLL:
            case GENERIC:
            default:
                return Math.max(48, textW + 8);
        }
    }

    private WidgetCatalog() {
    }
}
