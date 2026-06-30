// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

/**
 * Doxygen の {@code @param} / {@code @throws} 1 件 (名前 + 説明) を表すモデル。
 *
 * <p>{@code <parameterlist kind="param">} / {@code kind="exception"} の
 * {@code <parameteritem>} から抽出する。R2 (API リファレンス詳細表示) で使う。</p>
 */
public final class DoxParam {

    private final String name;
    private final String description;

    public DoxParam(String name, String description) {
        this.name = name != null ? name : "";
        this.description = description != null ? description : "";
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
