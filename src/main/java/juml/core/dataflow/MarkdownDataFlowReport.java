// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;


/**
 * {@link RoomAnalyzer.Result} を Markdown レポートに整形する。
 *
 * <p>章構成:</p>
 * <ol>
 *   <li>サマリー (Entity / DAO / Database 数)</li>
 *   <li>Databases: version と所属 Entity</li>
 *   <li>Entities: テーブル名 / 列一覧 / 外部キー</li>
 *   <li>DAOs: 各オペレーションと SQL/種別</li>
 * </ol>
 */
public final class MarkdownDataFlowReport {

    private MarkdownDataFlowReport() {
    }

    public static String render(RoomAnalyzer.Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Room Data Flow Report\n\n");
        if (result.isEmpty()) {
            sb.append("(no Room @Entity / @Dao / @Database detected)\n");
            return sb.toString();
        }
        sb.append("- Entities: ").append(result.getEntities().size()).append('\n');
        sb.append("- DAOs: ").append(result.getDaos().size()).append('\n');
        sb.append("- Databases: ").append(result.getDatabases().size()).append('\n');
        sb.append('\n');

        if (!result.getDatabases().isEmpty()) {
            sb.append("## Databases\n\n");
            sb.append("| Database | Version | Entities |\n");
            sb.append("|---|---|---|\n");
            for (RoomDatabase db : result.getDatabases()) {
                String v = db.getVersion() >= 0 ? String.valueOf(db.getVersion()) : "—";
                String entities = db.getEntityClasses().isEmpty() ? "—"
                        : String.join(", ", db.getEntityClasses());
                sb.append("| `").append(cell(db.getClassFqn())).append("` | ")
                        .append(v).append(" | ")
                        .append(cell(entities)).append(" |\n");
            }
            sb.append('\n');
        }

        if (!result.getEntities().isEmpty()) {
            sb.append("## Entities\n\n");
            for (RoomEntity e : result.getEntities()) {
                sb.append("### `").append(e.getDisplayName()).append('`');
                if (!e.getTableName().isEmpty()) {
                    sb.append(" — table `").append(e.getTableName()).append('`');
                }
                sb.append("\n\n");
                sb.append("- FQN: `").append(e.getClassFqn()).append("`\n");
                RoomEntity.Column pk = e.getPrimaryKey();
                if (pk != null) {
                    sb.append("- Primary key: `").append(pk.getName()).append("` (")
                            .append(pk.getType()).append(")\n");
                }
                if (!e.getForeignKeyTargets().isEmpty()) {
                    sb.append("- Foreign keys: ");
                    for (int i = 0; i < e.getForeignKeyTargets().size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append('`').append(e.getForeignKeyTargets().get(i)).append('`');
                    }
                    sb.append('\n');
                }
                sb.append('\n');
                if (!e.getColumns().isEmpty()) {
                    sb.append("| Column | Type | PK | Alias |\n");
                    sb.append("|---|---|---|---|\n");
                    for (RoomEntity.Column col : e.getColumns()) {
                        sb.append("| `").append(cell(col.getName())).append("` | `")
                                .append(cell(col.getType())).append("` | ")
                                .append(col.isPrimaryKey() ? "✔" : "")
                                .append(" | ")
                                .append(col.getColumnAlias().isEmpty() ? "—"
                                        : "`" + cell(col.getColumnAlias()) + "`")
                                .append(" |\n");
                    }
                    sb.append('\n');
                }
            }
        }

        if (!result.getDaos().isEmpty()) {
            sb.append("## DAOs\n\n");
            for (RoomDao dao : result.getDaos()) {
                sb.append("### `").append(dao.getDisplayName()).append("`\n\n");
                sb.append("- FQN: `").append(dao.getClassFqn()).append("`\n\n");
                if (dao.getOperations().isEmpty()) {
                    sb.append("(no annotated operations)\n\n");
                    continue;
                }
                sb.append("| Method | Kind | Return | SQL |\n");
                sb.append("|---|---|---|---|\n");
                for (RoomDao.Operation op : dao.getOperations()) {
                    String sql = op.getSql().isEmpty() ? "—"
                            : "`" + cell(truncate(op.getSql(), 80)) + "`";
                    sb.append("| `").append(cell(op.getMethodName())).append("` | ")
                            .append(op.getKind().name()).append(" | ")
                            .append(op.getReturnType().isEmpty() ? "—"
                                    : "`" + cell(op.getReturnType()) + "`")
                            .append(" | ").append(sql).append(" |\n");
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    /**
     * GFM テーブルのセル値を安全化する。{@code |} は列区切りと解釈される
     * (コードスパン内でも) ため {@code \|} にエスケープし、改行は行を壊すので空白に畳む。
     * 例: {@code SELECT a || ' ' || b} を含む @Query が行をずらすのを防ぐ。
     */
    private static String cell(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replaceAll("[\\r\\n]+", " ");
    }
}
