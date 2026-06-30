// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * i18nメッセージリソースを提供するユーティリティクラス。
 *
 * <p>表示言語は {@link #setLanguage(String)} で切り替える。既定は日本語 ("ja")。
 * 起動時に {@link juml.Setting#getLanguage()} の値で初期化される。言語切替は
 * 既に生成済みの Swing コンポーネントには遡及しないため、UI への反映は
 * アプリ再起動後となる (Look &amp; Feel と同じ方針)。</p>
 */
public final class Messages {

    /** リソースバンドルのベース名 (classpath 直下の {@code messages*.properties})。 */
    private static final String BASE = "messages";

    /**
     * 現在のバンドル。既定は日本語。
     *
     * <p>実行時の OS ロケールに依存させないため、明示的に日本語で初期化する
     * (要件: 「デフォルトは日本語」)。</p>
     */
    private static volatile ResourceBundle bundle = load("ja");

    /** 現在の言語キー ("ja" / "en")。バンドルのロケールからは復元できないため別途保持する。 */
    private static volatile String language = "ja";

    private Messages() {
    }

    /**
     * 言語キー ("ja" / "en") から対応するバンドルを読み込む。
     *
     * <p>OS の既定ロケールへフォールバックしないよう {@code NoFallbackControl} を使う。
     * これにより "en" 指定時に、日本語環境であっても確実にベース (英語) の
     * {@code messages.properties} が選ばれる。</p>
     */
    private static ResourceBundle load(String lang) {
        Locale locale = "en".equalsIgnoreCase(lang) ? Locale.ENGLISH : Locale.JAPANESE;
        return ResourceBundle.getBundle(BASE, locale,
                ResourceBundle.Control.getNoFallbackControl(
                        ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /**
     * 表示言語を設定する。
     *
     * @param lang 言語キー ("ja" / "en")。null や未知の値は日本語 ("ja") 扱い。
     */
    public static void setLanguage(String lang) {
        String normalized = "en".equalsIgnoreCase(lang) ? "en" : "ja";
        bundle = load(normalized);
        language = normalized;
    }

    /** 現在の表示言語キー ("ja" / "en") を返す。 */
    public static String getLanguage() {
        return language;
    }

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** キーが存在すればその値を、存在しなければ {@code null} を返す。 */
    public static String getOrNull(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return null;
        }
    }
}
