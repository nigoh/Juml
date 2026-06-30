// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.util.ErrorListener;

/**
 * {@code apktool.yml} を {@link ApktoolYmlInfo} にパースする軽量パーサ。
 *
 * <p>Apktool が出力する YAML は限られたキー集合・2 階層程度のネスト・単純なリストしか
 * 含まないため、汎用 YAML ライブラリを足さずにインデントベースの行パーサで処理する
 * (依存追加を避け、fat jar を肥大化させない方針)。先頭の {@code !!brut...} タグ行や
 * 未知キーは無視する。</p>
 */
public final class ApktoolYmlParser {

    private ApktoolYmlParser() {
    }

    /** デフォルト (silent) リスナーでパースする。 */
    public static ApktoolYmlInfo parse(String content) {
        return parse(content, ErrorListener.silent());
    }

    /**
     * {@code apktool.yml} のテキストをパースする。
     *
     * @param content  ファイル全文
     * @param listener 注意点の通知先 (null なら silent)
     */
    public static ApktoolYmlInfo parse(String content, ErrorListener listener) {
        if (content == null) {
            throw new IllegalArgumentException("content is null");
        }
        ErrorListener log = listener != null ? listener : ErrorListener.silent();
        ApktoolYmlInfo info = new ApktoolYmlInfo();
        String[] lines = content.split("\n", -1);
        String section = null;

        for (String raw : lines) {
            if (raw.isBlank() || raw.trim().startsWith("#") || raw.trim().startsWith("!!")) {
                continue;
            }
            int indent = leadingSpaces(raw);
            String line = raw.trim();

            // リスト項目は直前のセクションに属する。Apktool は top-level キー直下の
            // リストをインデント 0 で書く (doNotCompress) ため、indent 判定より先に処理する。
            if (line.startsWith("- ")) {
                String item = stripQuotes(line.substring(2).trim());
                if ("doNotCompress".equals(section)) {
                    info.getDoNotCompress().add(item);
                } else if ("usesFramework".equals(section) || "ids".equals(section)) {
                    info.getUsesFrameworkIds().add(item);
                }
                continue;
            }

            if (indent == 0) {
                // トップレベルのキー。値が無ければセクション見出しとして記憶する。
                section = null;
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                if (value.isEmpty()) {
                    section = key;
                } else {
                    applyTopLevel(info, key, value);
                }
                continue;
            }

            // ネストしたサブキー行は直前のトップレベルセクションに属する。
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = stripQuotes(line.substring(colon + 1).trim());
            if (value.isEmpty()) {
                // さらにネストする見出し (usesFramework.ids など) はセクションを切り替える。
                if ("ids".equals(key)) {
                    section = "ids";
                }
                continue;
            }
            applyNested(info, section, key, value, log);
        }
        return info;
    }

    private static void applyTopLevel(ApktoolYmlInfo info, String key, String value) {
        String v = stripQuotes(value);
        switch (key) {
            case "version":
                info.setApktoolVersion(v);
                break;
            case "apkFileName":
                info.setApkFileName(v);
                break;
            case "isFrameworkApk":
                info.setFrameworkApk(Boolean.parseBoolean(v));
                break;
            case "sharedLibrary":
                info.setSharedLibrary(Boolean.parseBoolean(v));
                break;
            case "sparseResources":
                info.setSparseResources(Boolean.parseBoolean(v));
                break;
            default:
                break;
        }
    }

    private static void applyNested(ApktoolYmlInfo info, String section, String key,
                                    String value, ErrorListener log) {
        if ("sdkInfo".equals(section)) {
            if ("minSdkVersion".equals(key)) {
                info.setMinSdkVersion(value);
            } else if ("targetSdkVersion".equals(key)) {
                info.setTargetSdkVersion(value);
            }
        } else if ("versionInfo".equals(section)) {
            if ("versionCode".equals(key)) {
                info.setVersionCode(value);
            } else if ("versionName".equals(key)) {
                info.setVersionName(value);
            }
        }
    }

    private static int leadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /** 前後のシングル/ダブルクォートを除去する。{@code null} 文字列はそのまま空扱い。 */
    private static String stripQuotes(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if ("null".equals(t)) {
            return "";
        }
        if (t.length() >= 2) {
            char a = t.charAt(0);
            char b = t.charAt(t.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return t.substring(1, t.length() - 1);
            }
        }
        return t;
    }
}
