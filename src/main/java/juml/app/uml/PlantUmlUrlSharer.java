// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.io.IOException;

/**
 * PlantUML テキストを公式 PlantUML サーバーの共有 URL へ変換する。
 *
 * <p>PlantUML エコシステム標準の deflate + 独自 base64 エンコード
 * ({@code net.sourceforge.plantuml.code.TranscoderUtil}) を用いるため、
 * 生成した URL は plantuml.com のオンラインサーバーや各種 IDE プラグインで
 * そのまま開ける。図を URL 1 本で共有するための最小実装。</p>
 */
final class PlantUmlUrlSharer {

    /** 公式サーバーの SVG エンドポイント。 */
    static final String SERVER_BASE = "https://www.plantuml.com/plantuml/svg/";

    private PlantUmlUrlSharer() {
    }

    /**
     * PlantUML テキストを共有 URL (SVG 表示) へ変換する。
     *
     * @throws IOException エンコードに失敗した場合 (通常は起きない)
     */
    static String buildUrl(String puml) throws IOException {
        String encoded = net.sourceforge.plantuml.code.TranscoderUtil
                .getDefaultTranscoder().encode(puml);
        return SERVER_BASE + encoded;
    }

    /** テスト用: URL のエンコード部を元テキストへ復号する (round-trip 検証)。 */
    static String decodeForTest(String encoded) throws IOException {
        return net.sourceforge.plantuml.code.TranscoderUtil
                .getDefaultTranscoder().decode(encoded);
    }
}
