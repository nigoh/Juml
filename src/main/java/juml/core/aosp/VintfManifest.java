// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.List;

/**
 * AOSP の VINTF (Vendor Interface Object) manifest 1 ファイル分の情報。
 *
 * <p>VINTF はデバイス側 ({@code device/<vendor>/<product>/manifest.xml}) と
 * フレームワーク側 ({@code compatibility_matrix.xml}) で「HAL の宣言/要求」を
 * 互いに合わせるための形式。本クラスは両方を統一表現で保持する。</p>
 *
 * <p>判別:</p>
 * <ul>
 *   <li>{@code <manifest type="device">} → {@link Kind#DEVICE_MANIFEST}</li>
 *   <li>{@code <manifest type="framework">} → {@link Kind#FRAMEWORK_MANIFEST}</li>
 *   <li>{@code <compatibility-matrix type="...">} → {@link Kind#COMPATIBILITY_MATRIX}</li>
 * </ul>
 */
public final class VintfManifest {

    /** ファイル種別。 */
    public enum Kind { DEVICE_MANIFEST, FRAMEWORK_MANIFEST, COMPATIBILITY_MATRIX, UNKNOWN }

    private final Kind kind;
    private String version;
    private Integer level;
    private String matrixType;
    private final List<VintfHal> hals = new ArrayList<>();
    private final List<String> kernelVersions = new ArrayList<>();
    private String sepolicyVersion;

    public VintfManifest(Kind kind) {
        this.kind = kind == null ? Kind.UNKNOWN : kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** {@code <manifest version="1.0">} の値。未設定なら null。 */
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /** compatibility-matrix の {@code level} 属性 (整数)。manifest 側は null。 */
    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<VintfHal> getHals() {
        return hals;
    }

    /**
     * compatibility-matrix の {@code type} 属性 ({@code "framework"} /
     * {@code "device"}、小文字化済み)。framework matrix は device manifest への
     * 要求、device matrix は framework manifest への要求を表す。
     * matrix 以外・属性なしは null または空。
     */
    public String getMatrixType() {
        return matrixType;
    }

    public void setMatrixType(String matrixType) {
        this.matrixType = matrixType;
    }

    /**
     * {@code <kernel version="4.9.0"/>} の値 (宣言順)。compat-matrix は
     * 対応カーネルを複数並べるため、リストで保持する。
     */
    public List<String> getKernelVersions() {
        return kernelVersions;
    }

    /** 最初の kernel バージョン。未指定なら null (互換用)。 */
    public String getKernelVersion() {
        return kernelVersions.isEmpty() ? null : kernelVersions.get(0);
    }

    public void setKernelVersion(String kernelVersion) {
        kernelVersions.clear();
        if (kernelVersion != null && !kernelVersion.isEmpty()) {
            kernelVersions.add(kernelVersion);
        }
    }

    /** {@code <sepolicy><version>30.0</version></sepolicy>} の値。未指定なら null。 */
    public String getSepolicyVersion() {
        return sepolicyVersion;
    }

    public void setSepolicyVersion(String sepolicyVersion) {
        this.sepolicyVersion = sepolicyVersion;
    }
}
