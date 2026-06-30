// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.apk;

import java.util.ArrayList;
import java.util.List;

/**
 * Apktool が逆コンパイル時に書き出す {@code apktool.yml} の解析結果。
 *
 * <p>{@code apktool.yml} は APK の復元に必要なメタ情報 (元の apk 名・Apktool の
 * バージョン・min/target SDK・version code/name・無圧縮ファイル種別・参照フレームワーク)
 * を保持する。Juml はこのメタ情報を図やレポートのヘッダに使い、解析対象が
 * 「Apktool で展開された APK」であることを示す。</p>
 */
public final class ApktoolYmlInfo {

    private String apktoolVersion;
    private String apkFileName;
    private boolean frameworkApk;
    private boolean sharedLibrary;
    private boolean sparseResources;
    private String minSdkVersion;
    private String targetSdkVersion;
    private String versionCode;
    private String versionName;
    private final List<String> doNotCompress = new ArrayList<>();
    private final List<String> usesFrameworkIds = new ArrayList<>();

    /** apktool.yml を書き出した Apktool のバージョン (例: {@code 2.9.3})。 */
    public String getApktoolVersion() {
        return apktoolVersion;
    }

    public void setApktoolVersion(String apktoolVersion) {
        this.apktoolVersion = apktoolVersion;
    }

    /** 元 APK のファイル名 (例: {@code app-release.apk})。 */
    public String getApkFileName() {
        return apkFileName;
    }

    public void setApkFileName(String apkFileName) {
        this.apkFileName = apkFileName;
    }

    /** {@code isFrameworkApk}: framework-res.apk のようなフレームワーク APK か。 */
    public boolean isFrameworkApk() {
        return frameworkApk;
    }

    public void setFrameworkApk(boolean frameworkApk) {
        this.frameworkApk = frameworkApk;
    }

    public boolean isSharedLibrary() {
        return sharedLibrary;
    }

    public void setSharedLibrary(boolean sharedLibrary) {
        this.sharedLibrary = sharedLibrary;
    }

    public boolean isSparseResources() {
        return sparseResources;
    }

    public void setSparseResources(boolean sparseResources) {
        this.sparseResources = sparseResources;
    }

    /** {@code sdkInfo.minSdkVersion} (文字列のまま保持。未宣言なら null)。 */
    public String getMinSdkVersion() {
        return minSdkVersion;
    }

    public void setMinSdkVersion(String minSdkVersion) {
        this.minSdkVersion = minSdkVersion;
    }

    /** {@code sdkInfo.targetSdkVersion}。 */
    public String getTargetSdkVersion() {
        return targetSdkVersion;
    }

    public void setTargetSdkVersion(String targetSdkVersion) {
        this.targetSdkVersion = targetSdkVersion;
    }

    /** {@code versionInfo.versionCode}。 */
    public String getVersionCode() {
        return versionCode;
    }

    public void setVersionCode(String versionCode) {
        this.versionCode = versionCode;
    }

    /** {@code versionInfo.versionName}。 */
    public String getVersionName() {
        return versionName;
    }

    public void setVersionName(String versionName) {
        this.versionName = versionName;
    }

    /** {@code doNotCompress}: APK 内で無圧縮で格納されたファイル拡張子/パスの一覧。 */
    public List<String> getDoNotCompress() {
        return doNotCompress;
    }

    /** {@code usesFramework.ids}: 参照しているフレームワークパッケージ ID の一覧。 */
    public List<String> getUsesFrameworkIds() {
        return usesFrameworkIds;
    }
}
