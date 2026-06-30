// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Room の {@code @Entity} アノテーション付きクラス情報。
 *
 * <p>{@code RoomAnalyzer} が {@link juml.core.formats.uml.JavaClassInfo}
 * を走査して作成する。テーブル名とフィールド一覧 (Primary Key 含む) を持つ。</p>
 */
public final class RoomEntity {

    /** Entity 内のフィールド (= 列) の情報。 */
    public static final class Column {
        private final String name;
        private final String type;
        private final boolean primaryKey;
        private final String columnAlias;

        public Column(String name, String type, boolean primaryKey, String columnAlias) {
            this.name = name == null ? "" : name;
            this.type = type == null ? "" : type;
            this.primaryKey = primaryKey;
            this.columnAlias = columnAlias == null ? "" : columnAlias;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public boolean isPrimaryKey() { return primaryKey; }
        /** {@code @ColumnInfo(name="...")} で指定された別名。無ければ空文字。 */
        public String getColumnAlias() { return columnAlias; }
    }

    private final String classFqn;
    private final String tableName;
    private final List<Column> columns = new ArrayList<>();
    /** {@code foreignKeys = @ForeignKey(entity = Foo.class, ...)} から検出した参照 Entity FQN/単純名。 */
    private final List<String> foreignKeyTargets = new ArrayList<>();
    private final String file;

    public RoomEntity(String classFqn, String tableName, String file) {
        this.classFqn = classFqn == null ? "" : classFqn;
        this.tableName = tableName == null ? "" : tableName;
        this.file = file == null ? "" : file;
    }

    public String getClassFqn() { return classFqn; }
    /** 明示指定された table 名。指定なしならクラス単純名で運用 (空文字)。 */
    public String getTableName() { return tableName; }
    public List<Column> getColumns() { return columns; }
    public List<String> getForeignKeyTargets() { return foreignKeyTargets; }
    public String getFile() { return file; }

    /** Primary Key の最初のフィールドを返す (無ければ null)。 */
    public Column getPrimaryKey() {
        for (Column c : columns) {
            if (c.isPrimaryKey()) {
                return c;
            }
        }
        return null;
    }

    public String getDisplayName() {
        int dot = classFqn.lastIndexOf('.');
        return dot < 0 ? classFqn : classFqn.substring(dot + 1);
    }
}
