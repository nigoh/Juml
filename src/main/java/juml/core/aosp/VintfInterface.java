// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.util.ArrayList;
import java.util.List;

/**
 * VINTF manifest 内の {@code <hal>} に属する {@code <interface>} 1 つ分の情報。
 *
 * <p>例: {@code <interface><name>IDevicesFactory</name><instance>default</instance></interface>}
 * → {@link #getName()} = {@code "IDevicesFactory"},
 *    {@link #getInstances()} = {@code ["default"]}。</p>
 */
public final class VintfInterface {

    private final String name;
    private final List<String> instances = new ArrayList<>();

    public VintfInterface(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() {
        return name;
    }

    /** 1 つの interface に対して宣言された全 instance 名 (例: {@code ["default", "secondary"]})。 */
    public List<String> getInstances() {
        return instances;
    }

    @Override
    public String toString() {
        return name + instances;
    }
}
