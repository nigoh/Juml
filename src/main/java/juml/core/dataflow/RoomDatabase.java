// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Room の {@code @Database} 付きクラス (抽象クラス) の情報。
 *
 * <p>{@code @Database(entities = {Foo.class, Bar.class}, version = 1)} から
 * Entity リストと version を抽出する。</p>
 */
public final class RoomDatabase {

    private final String classFqn;
    private final List<String> entityClasses = new ArrayList<>();
    private final int version;
    private final String file;

    public RoomDatabase(String classFqn, int version, String file) {
        this.classFqn = classFqn == null ? "" : classFqn;
        this.version = version;
        this.file = file == null ? "" : file;
    }

    public String getClassFqn() { return classFqn; }
    /** Entity クラスの単純名 or FQN リスト (アノテーション元のまま)。 */
    public List<String> getEntityClasses() { return entityClasses; }
    /** {@code version = N} の値。未指定なら -1。 */
    public int getVersion() { return version; }
    public String getFile() { return file; }

    public String getDisplayName() {
        int dot = classFqn.lastIndexOf('.');
        return dot < 0 ? classFqn : classFqn.substring(dot + 1);
    }
}
