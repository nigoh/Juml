// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Android の {@code android:foregroundServiceType} 値と
 * 対応する {@code FOREGROUND_SERVICE_*} permission・要求 API レベルのカタログ。
 *
 * <p>Android 10 (API 29) 以降に段階的に追加された種別を網羅する。Android 14 (API 34)
 * では foreground service 起動時に種別を必須宣言とし、種別ごとの permission も要求する
 * ようになった。Android 15 (API 35) では新種別 {@code mediaProcessing} が追加された。</p>
 *
 * <p>解析結果の可視化 (Manifest 図 / Markdown サマリー) でラベルや
 * 「Android XX+」表示の根拠として利用する。</p>
 */
public final class ForegroundServiceTypeCatalog {

    /** 1 つの foregroundServiceType 値に対する仕様。 */
    public static final class Entry {
        private final String type;
        private final int minApiLevel;
        private final String runtimePermission;

        Entry(String type, int minApiLevel, String runtimePermission) {
            this.type = type;
            this.minApiLevel = minApiLevel;
            this.runtimePermission = runtimePermission;
        }

        public String getType() {
            return type;
        }

        /** この値が manifest に書ける最小 API レベル。 */
        public int getMinApiLevel() {
            return minApiLevel;
        }

        /**
         * Android 14 (API 34) 以降にこの type で foreground service を起動する際、
         * 併せて {@code <uses-permission>} で要求する必要がある permission の名前。
         * Permission 不要なタイプは null。
         */
        public String getRuntimePermission() {
            return runtimePermission;
        }
    }

    private static final Map<String, Entry> ENTRIES;

    static {
        // Android 10 (API 29) で導入された最初期の種別 + 後追いで増えたもの。
        Map<String, Entry> m = new LinkedHashMap<>();
        m.put("dataSync",
                new Entry("dataSync", 29, "android.permission.FOREGROUND_SERVICE_DATA_SYNC"));
        m.put("mediaPlayback",
                new Entry("mediaPlayback", 29, "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"));
        m.put("phoneCall",
                new Entry("phoneCall", 29, "android.permission.FOREGROUND_SERVICE_PHONE_CALL"));
        m.put("location",
                new Entry("location", 29, "android.permission.FOREGROUND_SERVICE_LOCATION"));
        m.put("connectedDevice",
                new Entry("connectedDevice", 29,
                        "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"));
        m.put("mediaProjection",
                new Entry("mediaProjection", 29,
                        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"));
        // Android 11 (API 30) で追加
        m.put("camera",
                new Entry("camera", 30, "android.permission.FOREGROUND_SERVICE_CAMERA"));
        m.put("microphone",
                new Entry("microphone", 30, "android.permission.FOREGROUND_SERVICE_MICROPHONE"));
        // Android 14 (API 34) で追加された種別群
        m.put("health",
                new Entry("health", 34, "android.permission.FOREGROUND_SERVICE_HEALTH"));
        m.put("remoteMessaging",
                new Entry("remoteMessaging", 34,
                        "android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"));
        m.put("shortService",
                new Entry("shortService", 34,
                        "android.permission.FOREGROUND_SERVICE_SHORT_SERVICE"));
        m.put("specialUse",
                new Entry("specialUse", 34,
                        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE"));
        m.put("systemExempted",
                new Entry("systemExempted", 34,
                        "android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"));
        // Android 15 (API 35) で追加
        m.put("mediaProcessing",
                new Entry("mediaProcessing", 35,
                        "android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING"));
        ENTRIES = Collections.unmodifiableMap(m);
    }

    /** カタログに存在する種別か。 */
    public static boolean isKnown(String type) {
        return type != null && ENTRIES.containsKey(type);
    }

    /** 種別エントリを取得。未知の値は null。 */
    public static Entry get(String type) {
        return type == null ? null : ENTRIES.get(type);
    }

    /**
     * {@code "dataSync|connectedDevice"} のような {@code |} 連結値を個別の種別に分解する。
     * Android 14 では複数指定が可能。
     */
    public static List<String> split(String foregroundServiceType) {
        if (foregroundServiceType == null || foregroundServiceType.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(foregroundServiceType.split("\\|"));
    }

    /**
     * 指定した foregroundServiceType 文字列 (連結可) に必要な最小 API レベルを返す。
     * 構成種別の最大値が答え。未知の値しか無ければ 0 を返す。
     */
    public static int minApiLevelFor(String foregroundServiceType) {
        int max = 0;
        for (String t : split(foregroundServiceType)) {
            Entry e = ENTRIES.get(t.trim());
            if (e != null && e.getMinApiLevel() > max) {
                max = e.getMinApiLevel();
            }
        }
        return max;
    }

    /**
     * permission 名 ({@code android.permission.FOREGROUND_SERVICE_*}) から対応する
     * foregroundServiceType を逆引きする。Manifest 図で permission ←→ service を結ぶ際に
     * 使う。マッチしなければ null。
     */
    public static String typeForPermission(String permissionName) {
        if (permissionName == null) {
            return null;
        }
        for (Entry e : ENTRIES.values()) {
            if (permissionName.equals(e.getRuntimePermission())) {
                return e.getType();
            }
        }
        return null;
    }

    /** permission 名が {@code FOREGROUND_SERVICE_*} カタログに含まれるか。 */
    public static boolean isForegroundServicePermission(String permissionName) {
        return typeForPermission(permissionName) != null;
    }

    private ForegroundServiceTypeCatalog() {
    }
}
