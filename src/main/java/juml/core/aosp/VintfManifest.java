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
    private final List<VintfHal> hals = new ArrayList<>();
    private String kernelVersion;
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

    /** {@code <kernel version="5.10.0"/>} の値。未指定なら null。 */
    public String getKernelVersion() {
        return kernelVersion;
    }

    public void setKernelVersion(String kernelVersion) {
        this.kernelVersion = kernelVersion;
    }

    /** {@code <sepolicy><version>30.0</version></sepolicy>} の値。未指定なら null。 */
    public String getSepolicyVersion() {
        return sepolicyVersion;
    }

    public void setSepolicyVersion(String sepolicyVersion) {
        this.sepolicyVersion = sepolicyVersion;
    }
}
