// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import java.io.File;

/**
 * プロジェクト一覧エントリ。{@link ProjectRepository} から取得する値オブジェクト。
 */
public final class ProjectRecord {

    private final long id;
    private final String path;
    private final String name;
    private final long lastOpenedAt;

    public ProjectRecord(long id, String path, String name, long lastOpenedAt) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.lastOpenedAt = lastOpenedAt;
    }

    public long getId() { return id; }
    public String getPath() { return path; }
    public String getName() { return name; }
    public long getLastOpenedAt() { return lastOpenedAt; }

    public File root() { return new File(path); }
}
