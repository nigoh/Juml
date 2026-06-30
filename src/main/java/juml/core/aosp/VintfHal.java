// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.List;

/**
 * VINTF manifest / compatibility-matrix 内の {@code <hal>} 1 つ分の情報。
 *
 * <p>device 側 manifest では「この HAL を実装している」、framework 側 compat-matrix
 * では「この HAL を必要としている」ことを表す。{@link #isOptional()} は compat-matrix
 * 由来の場合に意味を持つ (manifest 側は常に null)。</p>
 *
 * <p>例 (HIDL):</p>
 * <pre>{@code
 * <hal format="hidl">
 *   <name>android.hardware.audio</name>
 *   <transport>hwbinder</transport>
 *   <version>6.0</version>
 *   <interface>
 *     <name>IDevicesFactory</name>
 *     <instance>default</instance>
 *   </interface>
 * </hal>
 * }</pre>
 */
public final class VintfHal {

    private final String format;
    private final String name;
    private String transport;
    private final List<String> versions = new ArrayList<>();
    private final List<VintfInterface> interfaces = new ArrayList<>();
    private Boolean optional;

    public VintfHal(String format, String name) {
        this.format = format == null ? "" : format;
        this.name = name == null ? "" : name;
    }

    /** {@code "hidl"} / {@code "aidl"} / {@code "native"} のいずれか。 */
    public String getFormat() {
        return format;
    }

    /** HAL のパッケージ名 (例 {@code "android.hardware.audio"})。 */
    public String getName() {
        return name;
    }

    /** {@code "hwbinder"} / {@code "passthrough"} (HIDL のみ)。未設定なら null。 */
    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    /**
     * HAL のバージョン文字列。HIDL なら {@code "6.0"} / {@code "1.0"}、
     * AIDL なら {@code "2"} のような整数。複数バージョンを許容する
     * (compat-matrix で複数バージョンを並べたり、{@code "6.0-7"} のような範囲も
     * そのまま文字列で保持する)。
     */
    public List<String> getVersions() {
        return versions;
    }

    public List<VintfInterface> getInterfaces() {
        return interfaces;
    }

    /**
     * compatibility-matrix 由来の場合の {@code optional} 属性。
     * device 側 manifest や属性指定なしの場合は null。
     */
    public Boolean isOptional() {
        return optional;
    }

    public void setOptional(Boolean optional) {
        this.optional = optional;
    }

    @Override
    public String toString() {
        return format + ":" + name + " " + versions;
    }
}
