// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gradle の Version Catalog ({@code gradle/libs.versions.toml}) の解析結果。
 *
 * <p>3 つのセクション ({@code [versions]} / {@code [libraries]} / {@code [plugins]}) を
 * Map として保持する。Gradle 側からの参照 ({@code libs.plugins.android.application} など)
 * は TOML キーの {@code -} と {@code _} を全て {@code .} に正規化した形式で検索する。</p>
 *
 * <p>例: TOML キー {@code android-application} → 検索キー {@code android.application}。</p>
 */
public class VersionCatalog {

    /** TOML の [libraries] 1 エントリ。 */
    public static class Library {
        public final String group;
        public final String name;
        public final String version;

        public Library(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        /**
         * {@code group:name:version} 形式の notation を返す。version が無ければ
         * {@code group:name} のみ。
         */
        public String toNotation() {
            StringBuilder sb = new StringBuilder();
            if (group != null && !group.isEmpty()) {
                sb.append(group);
            }
            if (name != null && !name.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(':');
                }
                sb.append(name);
            }
            if (version != null && !version.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(':');
                }
                sb.append(version);
            }
            return sb.toString();
        }
    }

    /** TOML の [plugins] 1 エントリ。 */
    public static class Plugin {
        public final String id;
        public final String version;

        public Plugin(String id, String version) {
            this.id = id;
            this.version = version;
        }
    }

    private final Map<String, String> versions = new LinkedHashMap<>();
    private final Map<String, Library> libraries = new LinkedHashMap<>();
    private final Map<String, Plugin> plugins = new LinkedHashMap<>();

    public Map<String, String> getVersions() {
        return versions;
    }

    public Map<String, Library> getLibraries() {
        return libraries;
    }

    public Map<String, Plugin> getPlugins() {
        return plugins;
    }

    /** Gradle 側 alias パスでバージョンを引く。例: {@code "compileSdk"}。 */
    public String findVersion(String alias) {
        return versions.get(normalize(alias));
    }

    /** Gradle 側 alias パスでライブラリを引く。例: {@code "androidx.appcompat"}。 */
    public Library findLibrary(String alias) {
        return libraries.get(normalize(alias));
    }

    /** Gradle 側 alias パスでプラグインを引く。例: {@code "android.application"}。 */
    public Plugin findPlugin(String alias) {
        return plugins.get(normalize(alias));
    }

    /**
     * 検索用に key を正規化する。TOML 側 {@code androidx-core-ktx} と
     * Gradle 側 {@code androidx.core.ktx} のどちらでも引けるよう
     * {@code -} と {@code _} をいずれも {@code .} に変換する。
     */
    public static String normalize(String key) {
        if (key == null) {
            return "";
        }
        return key.replace('-', '.').replace('_', '.').toLowerCase();
    }
}
