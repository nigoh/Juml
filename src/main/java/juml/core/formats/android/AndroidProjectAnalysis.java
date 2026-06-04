// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AndroidProjectAnalyzer} が返すプロジェクト解析結果。
 *
 * <p>モジュールごとの {@link GradleProjectInfo} と {@link AndroidManifestInfo} を保持する。
 * 同モジュール内に複数 manifest (flavor 上書きなど) がある場合はリストで保持する。</p>
 */
public class AndroidProjectAnalysis {

    private GradleProjectInfo rootSettings;
    private final Map<String, GradleProjectInfo> gradleByModule = new LinkedHashMap<>();
    private final Map<String, List<AndroidManifestInfo>> manifestsByModule = new LinkedHashMap<>();
    private final Map<String, List<AndroidLayoutInfo>> layoutsByModule = new LinkedHashMap<>();
    private final Map<String, List<AndroidNavigationGraphInfo>> navigationsByModule
            = new LinkedHashMap<>();
    private final Map<String, List<AndroidStringResources>> stringResourcesByModule
            = new LinkedHashMap<>();

    public GradleProjectInfo getRootSettings() {
        return rootSettings;
    }

    public void setRootSettings(GradleProjectInfo rootSettings) {
        this.rootSettings = rootSettings;
    }

    public Map<String, GradleProjectInfo> getGradleByModule() {
        return gradleByModule;
    }

    public Map<String, List<AndroidManifestInfo>> getManifestsByModule() {
        return manifestsByModule;
    }

    /**
     * モジュール名 → そのモジュールに含まれる layout XML のリスト。
     * {@link AndroidLayoutParser} 経由でパース済みの結果。空 Map で初期化されるので、
     * layout を解析していない場合も null にはならない。
     */
    public Map<String, List<AndroidLayoutInfo>> getLayoutsByModule() {
        return layoutsByModule;
    }

    /** 全モジュールの layout を 1 つのリストに連結。 */
    public List<AndroidLayoutInfo> allLayouts() {
        List<AndroidLayoutInfo> all = new ArrayList<>();
        for (List<AndroidLayoutInfo> list : layoutsByModule.values()) {
            all.addAll(list);
        }
        return all;
    }

    /**
     * モジュール名 → そのモジュールに含まれる Navigation グラフ XML のリスト。
     * {@link AndroidNavigationGraphParser} 経由でパース済みの結果。
     */
    public Map<String, List<AndroidNavigationGraphInfo>> getNavigationsByModule() {
        return navigationsByModule;
    }

    /** 全モジュールの Navigation グラフを 1 つのリストに連結。 */
    public List<AndroidNavigationGraphInfo> allNavigationGraphs() {
        List<AndroidNavigationGraphInfo> all = new ArrayList<>();
        for (List<AndroidNavigationGraphInfo> list : navigationsByModule.values()) {
            all.addAll(list);
        }
        return all;
    }

    /** {@link AndroidNavigationGraphInfo#getKey()} で Navigation グラフを検索。見つからなければ null。 */
    public AndroidNavigationGraphInfo findNavigationByKey(String key) {
        if (key == null) {
            return null;
        }
        for (AndroidNavigationGraphInfo info : allNavigationGraphs()) {
            if (key.equals(info.getKey())) {
                return info;
            }
        }
        return null;
    }

    /** {@link AndroidLayoutInfo#getKey()} で layout を検索。見つからなければ null。 */
    public AndroidLayoutInfo findLayoutByKey(String key) {
        if (key == null) {
            return null;
        }
        for (AndroidLayoutInfo info : allLayouts()) {
            if (key.equals(info.getKey())) {
                return info;
            }
        }
        return null;
    }

    /**
     * モジュール名 → そのモジュールに含まれる文字列リソースファイル (strings.xml 等) のリスト。
     * {@link StringResourceParser} 経由でパース済み。空 Map で初期化されるので null にはならない。
     */
    public Map<String, List<AndroidStringResources>> getStringResourcesByModule() {
        return stringResourcesByModule;
    }

    /** 全モジュールの文字列リソースファイルを 1 つのリストに連結。 */
    public List<AndroidStringResources> allStringResources() {
        List<AndroidStringResources> all = new ArrayList<>();
        for (List<AndroidStringResources> list : stringResourcesByModule.values()) {
            all.addAll(list);
        }
        return all;
    }

    /**
     * {@code @string/foo} / {@code R.string.foo} の {@code foo} を実文言へ解決する。
     *
     * <p>デフォルト locale (qualifier 無し) を優先し、見つからなければ任意の locale から
     * 最初に見つかった値を返す。未定義なら null。引数は {@code @string/} や
     * {@code R.string.} の接頭辞を付けても付けなくても良い。</p>
     */
    public String resolveString(String ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        String name = ref;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        String fallback = null;
        for (AndroidStringResources sr : allStringResources()) {
            String v = sr.getString(name);
            if (v == null) {
                continue;
            }
            if (sr.isDefaultLocale()) {
                return v;
            }
            if (fallback == null) {
                fallback = v;
            }
        }
        return fallback;
    }

    /** 全モジュールのマニフェストを 1 つのリストに連結。 */
    public List<AndroidManifestInfo> allManifests() {
        List<AndroidManifestInfo> all = new ArrayList<>();
        for (List<AndroidManifestInfo> list : manifestsByModule.values()) {
            all.addAll(list);
        }
        return all;
    }

    /** 全モジュールの全コンポーネントを 1 つのリストに連結。 */
    public List<AndroidComponentInfo> allComponents() {
        List<AndroidComponentInfo> all = new ArrayList<>();
        for (AndroidManifestInfo m : allManifests()) {
            all.addAll(m.allComponents());
        }
        return all;
    }

    /** FQN でコンポーネントを検索。 */
    public AndroidComponentInfo findComponentByFqn(String fqn) {
        if (fqn == null) {
            return null;
        }
        for (AndroidComponentInfo c : allComponents()) {
            if (fqn.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }
}
