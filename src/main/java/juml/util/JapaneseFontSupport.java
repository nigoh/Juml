// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * PlantUML 図で日本語（JavaDoc コメント・日本語クラス名など）が
 * 文字化け（豆腐 □）しないよう、見やすい日本語対応フォントの
 * ファミリ名を解決するユーティリティ。
 *
 * <p>解決順は次のとおり（先頭ほど優先）。</p>
 * <ol>
 *   <li>jar に同梱した <b>BIZ UDPGothic</b>（モリサワのユニバーサルデザイン書体。
 *       SIL OFL）を起動時に JVM へ登録して採用する。どの実行環境でも同じ
 *       「見やすい」日本語表示を保証するため、これを既定とする。</li>
 *   <li>同梱フォントを登録できない場合（リソース欠落・ヘッドレス例外など）は、
 *       実行環境にインストール済みの日本語対応フォントを {@link #PREFERRED}
 *       の優先順で探す。</li>
 * </ol>
 *
 * <p>{@code skinparam defaultFontName} に渡すための既定フォント名を返す。
 * 解決結果は一度だけ計算してキャッシュする。日本語対応フォントが
 * まったく見つからない場合は空文字を返し、呼び出し側はフォント指定を省略する
 * （= 従来どおり PlantUML 既定フォントにフォールバック）。</p>
 */
public final class JapaneseFontSupport {

    private JapaneseFontSupport() {
    }

    /** 同梱する既定日本語フォント（jar 内リソース）。見やすさ重視の BIZ UDPGothic。 */
    private static final String BUNDLED_RESOURCE = "/fonts/BIZUDPGothic-Regular.ttf";

    /** 優先的に採用したい日本語フォントファミリ（OS 横断）。先頭ほど優先。 */
    private static final String[] PREFERRED = {
        "Noto Sans CJK JP", "Noto Sans JP", "Source Han Sans JP", "Source Han Sans",
        "Yu Gothic UI", "Yu Gothic", "Meiryo", "MS PGothic", "MS Gothic",
        "Hiragino Sans", "Hiragino Kaku Gothic ProN", "Hiragino Kaku Gothic Pro",
        "IPAexGothic", "IPAGothic", "TakaoGothic", "VL PGothic", "VL Gothic"
    };

    /** 日本語表示可否の判定に使うサンプル文字（ひらがな・漢字・カタカナ）。 */
    private static final char[] SAMPLE = {'あ', '日', 'ア'};

    private static volatile boolean resolved;
    private static volatile String cached = "";

    /**
     * 日本語を表示できる既定フォントファミリ名を返す。
     * 見つからない場合は空文字。結果はキャッシュされる。
     */
    public static String defaultFontFamily() {
        if (!resolved) {
            synchronized (JapaneseFontSupport.class) {
                if (!resolved) {
                    cached = resolve();
                    resolved = true;
                }
            }
        }
        return cached;
    }

    private static String resolve() {
        // 0) まず同梱フォント（BIZ UDPGothic）を登録して採用する。
        //    どの実行環境でも同じ見やすい日本語表示を保証するため最優先で試みる。
        String bundled = registerBundledFont();
        if (!bundled.isEmpty()) {
            return bundled;
        }
        String[] families;
        try {
            families = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
        } catch (Throwable t) {
            // ヘッドレス例外など、フォント環境を取得できない場合は指定なし。
            return "";
        }
        if (families == null || families.length == 0) {
            return "";
        }
        // 1) 優先リストにある実在フォントを順に探す（大文字小文字無視）。
        for (String pref : PREFERRED) {
            for (String fam : families) {
                if (pref.equalsIgnoreCase(fam) && canDisplayJapanese(fam)) {
                    return fam;
                }
            }
        }
        // 2) インストール済みフォントから日本語を表示できるものを探す。
        for (String fam : families) {
            if (canDisplayJapanese(fam)) {
                return fam;
            }
        }
        return "";
    }

    /**
     * jar に同梱した日本語フォント（BIZ UDPGothic）を読み込み、ローカルの
     * {@link GraphicsEnvironment} に登録してそのファミリ名を返す。
     *
     * <p>登録に成功すると、以降 {@code new Font("BIZ UDPGothic", ...)} で解決できるようになり、
     * PlantUML / Batik による SVG・PNG 描画でも同じフォントが使われる。リソースが無い・
     * ヘッドレス例外などで登録できない場合は空文字を返し、呼び出し側はインストール済み
     * フォントの探索にフォールバックする。</p>
     */
    private static String registerBundledFont() {
        try (InputStream in = JapaneseFontSupport.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                // jar に同梱されていない（IDE 実行でリソース未配置など）。
                return "";
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            // 既に登録済みでも registerFont は false を返すだけで害はない。
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font.getFamily();
        } catch (Throwable t) {
            // フォント生成・登録に失敗した場合はインストール済みフォント探索に委ねる。
            return "";
        }
    }

    private static boolean canDisplayJapanese(String family) {
        try {
            Font f = new Font(family, Font.PLAIN, 12);
            for (char c : SAMPLE) {
                if (!f.canDisplay(c)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
