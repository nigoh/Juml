// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android.bp (Soong Blueprint) で宣言された 1 モジュール分の情報。
 *
 * <p>Soong は JSON 風だが厳密には Bazel/Starlark 風の構文を持つ。本パーサは
 * 全 property を保持せず、依存解析に必要な主要キー (name, srcs, *_libs/*_deps) と、
 * 配置・SDK・パッケージ等の代表的なスカラ属性のみ抽出する。</p>
 *
 * <p>依存は <em>結合リスト</em> ({@link #getDeps()}) と <em>種別ごとのリスト</em>
 * ({@link #getDepsByKind()}) の二系統で保持する。前者は図/レポートの後方互換用、
 * 後者は static/shared/header/defaults 等を描き分けるための情報源。</p>
 */
public final class AndroidBpModule {

    private final String type;
    private final String name;
    private final List<String> srcs = new ArrayList<>();
    /**
     * 結合した依存リスト。{@code shared_libs}, {@code static_libs},
     * {@code libs}, {@code java_libs}, {@code header_libs}, {@code defaults},
     * {@code required} 等を 1 つに集約する (後方互換)。
     */
    private final List<String> deps = new ArrayList<>();
    /**
     * 依存キー ({@code static_libs} / {@code shared_libs} / {@code header_libs} /
     * {@code libs} / {@code defaults} / {@code required} 等) 別の依存名リスト。
     * 図の矢印を種別で描き分けるために使う。挿入順 (宣言順) を保つ。
     */
    private final Map<String, List<String>> depsByKind = new LinkedHashMap<>();
    /**
     * トップレベルのスカラ属性 (文字列 / 真偽 / 数値) を生の文字列値で保持する。
     * 例: {@code sdk_version} → {@code "current"}, {@code vendor} → {@code "true"}。
     * 配置 (パーティション) や SDK レベルの判定に使う。
     */
    private final Map<String, String> scalars = new LinkedHashMap<>();
    private final String file;
    private final int lineHint;

    public AndroidBpModule(String type, String name, String file, int lineHint) {
        this.type = type == null ? "" : type;
        this.name = name == null ? "" : name;
        this.file = file == null ? "" : file;
        this.lineHint = lineHint;
    }

    /** モジュール種別 ({@code cc_library}, {@code java_library}, {@code android_app} 等)。 */
    public String getType() { return type; }
    public String getName() { return name; }
    public List<String> getSrcs() { return srcs; }
    public List<String> getDeps() { return deps; }
    public String getFile() { return file; }
    public int getLineHint() { return lineHint; }

    /**
     * 指定キー ({@code static_libs} 等) の依存として 1 件追加する。結合リストにも反映する。
     * 空文字・null は無視する。
     */
    public void addDep(String kind, String depName) {
        if (depName == null || depName.isEmpty()) {
            return;
        }
        deps.add(depName);
        depsByKind.computeIfAbsent(kind == null || kind.isEmpty() ? "libs" : kind,
                k -> new ArrayList<>()).add(depName);
    }

    /** 依存キー別の依存名リスト (読み取り専用ビュー、宣言順)。 */
    public Map<String, List<String>> getDepsByKind() {
        return Collections.unmodifiableMap(depsByKind);
    }

    /** トップレベルのスカラ属性を 1 件登録する (生の文字列値)。空キーは無視。 */
    public void putScalar(String key, String value) {
        if (key == null || key.isEmpty()) {
            return;
        }
        scalars.put(key, value == null ? "" : value);
    }

    /** スカラ属性値を返す。未設定なら空文字。 */
    public String scalar(String key) {
        return scalars.getOrDefault(key, "");
    }

    /** スカラ属性が真偽値 {@code true} かどうか。 */
    public boolean boolProp(String key) {
        return "true".equals(scalars.get(key));
    }

    /** 全スカラ属性 (読み取り専用ビュー)。 */
    public Map<String, String> getScalars() {
        return Collections.unmodifiableMap(scalars);
    }

    /**
     * モジュールがインストールされる配置 (パーティション) を Soong の慣習から推定する。
     *
     * <p>判定順 (より特殊な配置を優先): {@code vendor/proprietary/soc_specific} → vendor、
     * {@code device_specific} → odm、{@code product_specific} → product、
     * {@code system_ext_specific} → system_ext、{@code recovery} → recovery、
     * {@code ramdisk/vendor_ramdisk} → ramdisk。いずれも無ければ system。</p>
     */
    public String getPartition() {
        if (boolProp("vendor") || boolProp("proprietary") || boolProp("soc_specific")) {
            return "vendor";
        }
        if (boolProp("device_specific")) {
            return "odm";
        }
        if (boolProp("product_specific")) {
            return "product";
        }
        if (boolProp("system_ext_specific")) {
            return "system_ext";
        }
        if (boolProp("recovery")) {
            return "recovery";
        }
        if (boolProp("ramdisk") || boolProp("vendor_ramdisk")) {
            return "ramdisk";
        }
        return "system";
    }

    /** 名前末尾やキーから判定するテストモジュールか ({@code *_test} 種別)。 */
    public boolean isTest() {
        return type.endsWith("_test") || type.endsWith("_test_host")
                || type.contains("_test_");
    }

    /**
     * 指定した依存名が宣言されたキー種別を返す (複数キーに現れる場合は最初に登録された種別)。
     * 見つからなければ空文字。図の矢印スタイル判定に使う。
     */
    public String kindOf(String depName) {
        for (Map.Entry<String, List<String>> e : depsByKind.entrySet()) {
            if (e.getValue().contains(depName)) {
                return e.getKey();
            }
        }
        return "";
    }

    /** モジュール種別を大分類する: cc / java / android / aidl / hidl / その他。 */
    public String getCategory() {
        if (type.startsWith("cc_") || type.startsWith("ndk_")
                || type.equals("cc_binary") || type.equals("cc_test")) {
            return "cc";
        }
        if (type.startsWith("java_") || type.equals("java_library")
                || type.equals("java_binary") || type.equals("java_defaults")) {
            return "java";
        }
        if (type.startsWith("android_") || type.equals("android_app")
                || type.equals("android_library")) {
            return "android";
        }
        if (type.contains("aidl")) {
            return "aidl";
        }
        if (type.contains("hidl")) {
            return "hidl";
        }
        if (type.startsWith("filegroup") || type.startsWith("genrule")) {
            return "build";
        }
        return "other";
    }

    @Override
    public String toString() {
        return type + " { name: \"" + name + "\" }"
                + (file.isEmpty() ? "" : " @ " + file + ":" + lineHint);
    }
}
