// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.util.ErrorListener;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gradle の {@code gradle/libs.versions.toml} を解析する簡易 TOML パーサ。
 *
 * <p>Version Catalog の用途に必要な範囲のみを対象とする ([versions] / [libraries] /
 * [plugins] の 3 セクション、シンプル key=value とインラインテーブル {@code { ... }}
 * の 2 形式)。バンドル、テーブル配列等の高度な TOML 機能は未サポート。</p>
 */
public final class VersionCatalogParser {

    /** 通常の TOML 仕様で十分なケース向けに、外部依存無しで動く軽量パーサ。 */
    public static VersionCatalog parse(String toml) {
        return parse(toml, null);
    }

    /** リスナー付き。 */
    public static VersionCatalog parse(String toml, ErrorListener listener) {
        if (toml == null) {
            throw new IllegalArgumentException("toml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        VersionCatalog cat = new VersionCatalog();
        String section = "";
        int lineNo = 0;
        for (String rawLine : toml.split("\\r?\\n")) {
            lineNo++;
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = findTopLevelEquals(line);
            if (eq < 0) {
                l.onError(juml.util.ErrorCode.PRJ_009, null, lineNo, "expected '=' in TOML line: " + rawLine);
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            try {
                switch (section) {
                    case "versions":
                        parseVersion(cat, key, value);
                        break;
                    case "libraries":
                        parseLibrary(cat, key, value);
                        break;
                    case "plugins":
                        parsePlugin(cat, key, value);
                        break;
                    default:
                        // [bundles] や知らないセクションは無視
                        break;
                }
            } catch (RuntimeException ex) {
                l.onError(juml.util.ErrorCode.PRJ_009, null, lineNo, "TOML parse error: " + ex.getMessage());
            }
        }
        return cat;
    }

    // --- セクション別パース ---

    private static void parseVersion(VersionCatalog cat, String key, String value) {
        String v = unquote(value);
        if (v != null) {
            cat.getVersions().put(VersionCatalog.normalize(key), v);
        }
    }

    private static void parseLibrary(VersionCatalog cat, String key, String value) {
        // 形式 1: 文字列リテラル "group:name:version"
        String shortForm = unquote(value);
        if (shortForm != null) {
            String[] parts = shortForm.split(":");
            String group = parts.length > 0 ? parts[0] : null;
            String name = parts.length > 1 ? parts[1] : null;
            String version = parts.length > 2 ? parts[2] : null;
            cat.getLibraries().put(VersionCatalog.normalize(key),
                    new VersionCatalog.Library(group, name, version));
            return;
        }
        // 形式 2: インラインテーブル
        InlineTable t = parseInlineTable(value);
        if (t == null) {
            return;
        }
        String group = t.get("group");
        String name = t.get("name");
        String module = t.get("module");
        if (module != null) {
            String[] parts = module.split(":");
            if (parts.length >= 2) {
                group = parts[0];
                name = parts[1];
            }
        }
        String version = resolveVersion(t, cat);
        cat.getLibraries().put(VersionCatalog.normalize(key),
                new VersionCatalog.Library(group, name, version));
    }

    private static void parsePlugin(VersionCatalog cat, String key, String value) {
        // 形式 1: "id:version"
        String shortForm = unquote(value);
        if (shortForm != null) {
            String id = shortForm;
            String version = null;
            int colon = shortForm.indexOf(':');
            if (colon > 0) {
                id = shortForm.substring(0, colon);
                version = shortForm.substring(colon + 1);
            }
            cat.getPlugins().put(VersionCatalog.normalize(key),
                    new VersionCatalog.Plugin(id, version));
            return;
        }
        // 形式 2: { id = "...", version.ref = "..." }
        InlineTable t = parseInlineTable(value);
        if (t == null) {
            return;
        }
        String id = t.get("id");
        if (id == null) {
            return;
        }
        String version = resolveVersion(t, cat);
        cat.getPlugins().put(VersionCatalog.normalize(key),
                new VersionCatalog.Plugin(id, version));
    }

    /** version / version.ref のいずれかを解決して文字列で返す。 */
    private static String resolveVersion(InlineTable t, VersionCatalog cat) {
        String v = t.get("version");
        if (v != null) {
            return v;
        }
        String ref = t.get("version.ref");
        if (ref != null) {
            return cat.getVersions().get(VersionCatalog.normalize(ref));
        }
        return null;
    }

    // --- ユーティリティ ---

    private static String stripComment(String line) {
        int q = -1;
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (c == '#' && !inString) {
                q = i;
                break;
            }
        }
        return q < 0 ? line : line.substring(0, q);
    }

    private static int findTopLevelEquals(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (c == '=' && !inString) {
                return i;
            }
        }
        return -1;
    }

    private static String unquote(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        // 長さ 2 未満 (単一クォート) では substring(1, 0) が例外になるためガードする。
        if (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\""))
                    || (t.startsWith("'") && t.endsWith("'")))) {
            return t.substring(1, t.length() - 1);
        }
        return null;
    }

    /** インラインテーブル {@code { key = "v", key.ref = "x" }} を Map 風に保持。 */
    private static final class InlineTable {
        private final java.util.Map<String, String> kvs = new java.util.LinkedHashMap<>();

        void put(String k, String v) {
            kvs.put(k.trim(), v);
        }

        String get(String k) {
            return kvs.get(k);
        }
    }

    // Gradle Version Catalog のインラインテーブルはダブル/シングル両方のクオートを許容する
    // ({ id = 'x', version = '1.0' })。TOML 標準はダブルのみだが Gradle 実装はシングルも通すため、
    // どちらの値も取りこぼさないよう両対応にする。
    private static final Pattern KV_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_.\\-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private static InlineTable parseInlineTable(String text) {
        String t = text.trim();
        if (!t.startsWith("{") || !t.endsWith("}")) {
            return null;
        }
        String inner = t.substring(1, t.length() - 1);
        InlineTable result = new InlineTable();
        Matcher m = KV_PATTERN.matcher(inner);
        while (m.find()) {
            String dq = m.group(2);
            result.put(m.group(1), dq != null ? dq : m.group(3));
        }
        return result;
    }

    private VersionCatalogParser() {
    }
}
