// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Room の {@code @Dao} 付きインタフェース/抽象クラスの情報。
 *
 * <p>DAO 内の各メソッドは {@code @Query("SELECT ...")} / {@code @Insert} /
 * {@code @Update} / {@code @Delete} / {@code @RawQuery} のいずれかが付与されており、
 * その分類と SQL 文 (Query の場合) を {@link Operation} に保持する。</p>
 */
public final class RoomDao {

    /** DAO メソッドの種別。 */
    public enum OperationKind {
        QUERY, INSERT, UPDATE, DELETE, RAW_QUERY, OTHER
    }

    /** DAO 内 1 メソッドの情報。 */
    public static final class Operation {
        private final String methodName;
        private final OperationKind kind;
        private final String sql;
        private final String returnType;

        public Operation(String methodName, OperationKind kind, String sql,
                          String returnType) {
            this.methodName = methodName == null ? "" : methodName;
            this.kind = kind == null ? OperationKind.OTHER : kind;
            this.sql = sql == null ? "" : sql;
            this.returnType = returnType == null ? "" : returnType;
        }

        public String getMethodName() { return methodName; }
        public OperationKind getKind() { return kind; }
        /** {@code @Query("SELECT ...")} の文字列。Insert/Update/Delete では空文字。 */
        public String getSql() { return sql; }
        public String getReturnType() { return returnType; }
    }

    private final String classFqn;
    private final List<Operation> operations = new ArrayList<>();
    private final String file;

    public RoomDao(String classFqn, String file) {
        this.classFqn = classFqn == null ? "" : classFqn;
        this.file = file == null ? "" : file;
    }

    public String getClassFqn() { return classFqn; }
    public List<Operation> getOperations() { return operations; }
    public String getFile() { return file; }

    public String getDisplayName() {
        int dot = classFqn.lastIndexOf('.');
        return dot < 0 ? classFqn : classFqn.substring(dot + 1);
    }
}
