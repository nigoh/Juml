// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

/**
 * 実寸レイアウト描画で扱うウィジェットの大分類。
 *
 * <p>{@link WidgetCatalog} がタグ名から判定し、固有サイズ (intrinsic size) の見積もりと
 * {@link AndroidLayoutSvgRenderer} での色分け・装飾の振り分けに使う。
 * 厳密な Android の View 階層ではなく「画面上どう見えるか」に必要な粒度だけ持つ。</p>
 */
public enum WidgetType {
    /** 子を並べる ViewGroup (LinearLayout / Frame / Relative / Constraint など)。 */
    CONTAINER,
    /** スクロール可能な ViewGroup (ScrollView / NestedScrollView / RecyclerView など)。 */
    SCROLL,
    /** {@code <include>} 参照。実体は別レイアウトのためプレースホルダ描画する。 */
    INCLUDE,
    /** {@code <fragment>}。実体は実行時注入のためプレースホルダ描画する。 */
    FRAGMENT,
    /** 押下可能なボタン。 */
    BUTTON,
    /** テキスト表示 (TextView など)。 */
    TEXT,
    /** テキスト入力 (EditText / TextInput など)。 */
    EDIT,
    /** 画像 (ImageView / ImageButton)。 */
    IMAGE,
    /** チェックボックス。 */
    CHECK,
    /** ラジオボタン。 */
    RADIO,
    /** スイッチ / トグル。 */
    SWITCH,
    /** 進捗・スライダ系 (ProgressBar / SeekBar / Slider)。 */
    PROGRESS,
    /** ドロップダウン (Spinner)。 */
    SPINNER,
    /** その他の末端 View。 */
    GENERIC
}
