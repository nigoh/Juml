// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

/**
 * Ninja の {@code rule <name>} 宣言 1 つ分の情報と、その rule を使う
 * {@code build} 文の件数。
 *
 * <p>Soong が生成する {@code build.ninja} には {@code cc.compile} / {@code javac} /
 * {@code Cp} 等のルールが定義され、各 {@code build} 文がいずれかの rule を参照する。
 * どの rule が何件使われているかはビルド構成の概況把握に有用。</p>
 */
public final class BuildNinjaRule {

    private final String name;
    private String command = "";
    private String description = "";
    /** この rule を参照する {@code build} 文の件数。 */
    private int buildCount;

    public BuildNinjaRule(String name) {
        this.name = name == null ? "" : name;
    }

    public String getName() { return name; }
    public String getCommand() { return command; }
    public String getDescription() { return description; }
    public int getBuildCount() { return buildCount; }

    void setCommand(String command) {
        this.command = command == null ? "" : command;
    }

    void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    void incrementBuildCount() { buildCount++; }

    @Override
    public String toString() {
        return "rule " + name + " (x" + buildCount + ")";
    }
}
