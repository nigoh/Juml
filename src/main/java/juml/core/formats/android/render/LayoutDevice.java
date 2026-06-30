// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

/**
 * 実寸レイアウト描画の土台となる「画面サイズ」を dp 単位で表す。
 *
 * <p>SVG の座標系をそのまま dp に対応させるため、密度 (density) は持たず幅・高さの
 * dp 値だけを保持する。{@code match_parent} のルートはこのサイズいっぱいに広がる。</p>
 *
 * <p>{@link #fromQualifier(String)} は layout バリアントの configuration qualifier
 * ({@code land} / {@code sw600dp} / {@code w820dp} など) から、おおよその画面サイズを
 * 推定する。判定できないトークンは無視し、既定のスマホ縦画面にフォールバックする。</p>
 */
public final class LayoutDevice {

    /** 既定スマホ縦画面の幅 (dp)。 */
    public static final int PHONE_WIDTH_DP = 360;
    /** 既定スマホ縦画面の高さ (dp)。 */
    public static final int PHONE_HEIGHT_DP = 640;

    private final int widthDp;
    private final int heightDp;

    public LayoutDevice(int widthDp, int heightDp) {
        this.widthDp = widthDp > 0 ? widthDp : PHONE_WIDTH_DP;
        this.heightDp = heightDp > 0 ? heightDp : PHONE_HEIGHT_DP;
    }

    /** 既定のスマホ縦画面 (360x640dp)。 */
    public static LayoutDevice phonePortrait() {
        return new LayoutDevice(PHONE_WIDTH_DP, PHONE_HEIGHT_DP);
    }

    /** 既定のスマホ横画面 (640x360dp)。 */
    public static LayoutDevice phoneLandscape() {
        return new LayoutDevice(PHONE_HEIGHT_DP, PHONE_WIDTH_DP);
    }

    /**
     * configuration qualifier から画面サイズを推定する。
     *
     * <ul>
     *   <li>{@code land} を含む → 横画面</li>
     *   <li>{@code swNNNdp} (smallest width) → 短辺をその値に</li>
     *   <li>{@code wNNNdp} (available width) → 幅をその値に</li>
     *   <li>判定不能 → 既定スマホ縦画面</li>
     * </ul>
     */
    public static LayoutDevice fromQualifier(String qualifier) {
        if (qualifier == null || qualifier.isEmpty()) {
            return phonePortrait();
        }
        String q = qualifier.toLowerCase(java.util.Locale.ROOT);
        boolean land = containsToken(q, "land");
        int sw = parseDpToken(q, "sw");
        int w = parseDpToken(q, "w");
        int h = parseDpToken(q, "h");
        int width;
        int height;
        if (w > 0 && h > 0) {
            width = w;
            height = h;
        } else if (sw > 0) {
            // smallest width = 短辺。縦横は land で決める。
            int longSide = Math.max(PHONE_HEIGHT_DP, (int) Math.round(sw * 16.0 / 9.0));
            width = land ? longSide : sw;
            height = land ? sw : longSide;
        } else if (w > 0) {
            width = w;
            height = land ? PHONE_WIDTH_DP : PHONE_HEIGHT_DP;
        } else {
            return land ? phoneLandscape() : phonePortrait();
        }
        return new LayoutDevice(width, height);
    }

    private static boolean containsToken(String q, String token) {
        for (String part : q.split("-")) {
            if (part.equals(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code <prefix>NNNdp} 形式のトークンを探して NNN を返す。見つからなければ 0。
     * {@code sw} は {@code w} の接頭辞と衝突するため、トークン完全一致で前置詞を判定する。
     */
    private static int parseDpToken(String q, String prefix) {
        for (String part : q.split("-")) {
            if (!part.startsWith(prefix) || !part.endsWith("dp")) {
                continue;
            }
            // "sw" と "w" の取り違え防止: prefix の直後が数字であること。
            String mid = part.substring(prefix.length(), part.length() - 2);
            if (mid.isEmpty() || !mid.chars().allMatch(Character::isDigit)) {
                continue;
            }
            // prefix="w" が "sw600dp" を拾わないように、part が prefix で始まるが
            // 1 文字前が 's' のような余分接頭辞を持つ場合は除外。
            if ("w".equals(prefix) && part.startsWith("sw")) {
                continue;
            }
            try {
                return Integer.parseInt(mid);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    public int getWidthDp() {
        return widthDp;
    }

    public int getHeightDp() {
        return heightDp;
    }
}
