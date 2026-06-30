// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * Gradle ビルドスクリプトで宣言された依存。
 */
public class GradleDependency {

    private final String scope;
    private final String notation;
    private String group;
    private String name;
    private String version;
    private String moduleRef;

    public GradleDependency(String scope, String notation) {
        this.scope = scope == null ? "" : scope;
        this.notation = notation == null ? "" : notation;
        parseNotation();
    }

    private void parseNotation() {
        String n = notation;
        if (n.startsWith("project(")) {
            int s = n.indexOf('(');
            int e = n.lastIndexOf(')');
            if (s >= 0 && e > s) {
                String inner = n.substring(s + 1, e).trim();
                if (inner.startsWith("'") || inner.startsWith("\"")) {
                    inner = inner.substring(1, inner.length() - 1);
                }
                if (inner.startsWith(":")) {
                    inner = inner.substring(1);
                }
                moduleRef = inner;
            }
            return;
        }
        String[] parts = n.split(":");
        if (parts.length >= 3) {
            group = parts[0];
            name = parts[1];
            version = parts[2];
        } else if (parts.length == 2) {
            group = parts[0];
            name = parts[1];
        } else {
            name = n;
        }
    }

    public String getScope() {
        return scope;
    }

    public String getNotation() {
        return notation;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getModuleRef() {
        return moduleRef;
    }

    /** プロジェクト内モジュール参照か。 */
    public boolean isModuleReference() {
        return moduleRef != null;
    }
}
