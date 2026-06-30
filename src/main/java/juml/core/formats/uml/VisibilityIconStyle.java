// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

/**
 * クラス図の可視性をカラーアイコンで描画するための {@code skinparam} と色定義。
 *
 * <p>記号 ({@code +}/{@code -}/{@code #}/{@code ~}) は視認性が低いため、既定では
 * PlantUML 標準のカラーアイコン (緑丸=public, 橙ひし形=protected, 青三角=package,
 * 赤四角=private) で描画する。ここで定義する色は凡例 ({@link PlantUmlClassLegend})
 * の glyph 色と一致させ、図と凡例の対応が崩れないようにする。</p>
 */
final class VisibilityIconStyle {

    static final String PUBLIC_COLOR = "#2E7D32";    // 緑
    static final String PROTECTED_COLOR = "#EF6C00"; // 橙
    static final String PACKAGE_COLOR = "#1565C0";   // 青
    static final String PRIVATE_COLOR = "#C62828";   // 赤

    /**
     * 可視性の描画方式を決める {@code skinparam} を出力する。
     *
     * @param out   出力先
     * @param icons {@code true} ならカラーアイコン (図形 + 色)、{@code false} なら
     *              従来どおり記号テキスト ({@code classAttributeIconSize 0})
     */
    static void appendSkinparams(StringBuilder out, boolean icons) {
        if (!icons) {
            out.append("skinparam classAttributeIconSize 0\n");
            return;
        }
        out.append("skinparam classAttributeIconSize 12\n");
        out.append("skinparam IconPublicColor ").append(PUBLIC_COLOR).append('\n');
        out.append("skinparam IconPublicBackgroundColor #A5D6A7\n");
        out.append("skinparam IconProtectedColor ").append(PROTECTED_COLOR).append('\n');
        out.append("skinparam IconProtectedBackgroundColor #FFCC80\n");
        out.append("skinparam IconPackageColor ").append(PACKAGE_COLOR).append('\n');
        out.append("skinparam IconPackageBackgroundColor #90CAF9\n");
        out.append("skinparam IconPrivateColor ").append(PRIVATE_COLOR).append('\n');
        out.append("skinparam IconPrivateBackgroundColor #EF9A9A\n");
    }

    private VisibilityIconStyle() {
    }
}
