// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import juml.core.formats.android.AndroidManifestInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Apktool で逆コンパイルされた 1 つのディレクトリの解析結果を束ねるモデル。
 *
 * <p>{@code apktool.yml} のメタ情報・{@code AndroidManifest.xml} の解析結果・
 * {@code smali_* (multidex を含む)} 配下の全クラスを保持する。CLI/GUI 側はこの 1 オブジェクトから
 * サマリーやクラス図を生成する。</p>
 */
public final class ApkAnalysis {

    private ApktoolYmlInfo apktoolInfo;
    private AndroidManifestInfo manifest;
    private final List<SmaliClassInfo> classes = new ArrayList<>();

    /** {@code apktool.yml} のメタ情報 (無ければ null)。 */
    public ApktoolYmlInfo getApktoolInfo() {
        return apktoolInfo;
    }

    public void setApktoolInfo(ApktoolYmlInfo apktoolInfo) {
        this.apktoolInfo = apktoolInfo;
    }

    /** {@code AndroidManifest.xml} の解析結果 (無ければ null)。 */
    public AndroidManifestInfo getManifest() {
        return manifest;
    }

    public void setManifest(AndroidManifestInfo manifest) {
        this.manifest = manifest;
    }

    /** {@code smali_* (multidex を含む)} 配下から抽出した全クラス。 */
    public List<SmaliClassInfo> getClasses() {
        return classes;
    }

    /** アプリ本体のパッケージ名 (manifest 優先、無ければ最頻パッケージから推定)。 */
    public String getApplicationPackage() {
        if (manifest != null && manifest.getPackageName() != null
                && !manifest.getPackageName().isEmpty()) {
            return manifest.getPackageName();
        }
        return dominantPackagePrefix();
    }

    /**
     * smali クラスのパッケージのうち、最も多くのクラスを含む 2 階層プレフィックス
     * (例: {@code com.example}) を返す。manifest が無い framework APK などで
     * 「アプリ本体らしきパッケージ」を推定するために使う。該当なしなら空文字。
     */
    public String dominantPackagePrefix() {
        Map<String, Integer> counts = new TreeMap<>();
        for (SmaliClassInfo c : classes) {
            String pkg = c.getPackageName();
            if (pkg.isEmpty()) {
                continue;
            }
            String prefix = topTwoSegments(pkg);
            counts.merge(prefix, 1, Integer::sum);
        }
        String best = "";
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** クラス数を返す。 */
    public int classCount() {
        return classes.size();
    }

    /** パッケージ名 → そのパッケージ内クラス数の昇順マップ。 */
    public Map<String, Integer> classCountByPackage() {
        Map<String, Integer> counts = new TreeMap<>();
        for (SmaliClassInfo c : classes) {
            counts.merge(c.getPackageName(), 1, Integer::sum);
        }
        return counts;
    }

    private static String topTwoSegments(String pkg) {
        int first = pkg.indexOf('.');
        if (first < 0) {
            return pkg;
        }
        int second = pkg.indexOf('.', first + 1);
        return second < 0 ? pkg : pkg.substring(0, second);
    }
}
