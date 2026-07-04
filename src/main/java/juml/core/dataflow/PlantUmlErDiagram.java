// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link RoomAnalyzer.Result} から Room エンティティの ER 図を生成する。
 *
 * <p>各 Entity を PlantUML の {@code entity} ブロックとして描画。Primary Key
 * は {@code *} マーク、外部キーは矢印リンク。{@code @Database(entities = ...)} に
 * 含まれる Entity は同じパッケージにグループ化する。</p>
 */
public final class PlantUmlErDiagram {

    private PlantUmlErDiagram() {
    }

    public static String render(RoomAnalyzer.Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("title Room Entity-Relationship\n");
        sb.append("hide circle\n");
        sb.append("skinparam linetype ortho\n");
        sb.append("skinparam shadowing false\n");

        Map<String, RoomEntity> bySimple = new LinkedHashMap<>();
        Map<String, RoomEntity> byFqn = new LinkedHashMap<>();
        for (RoomEntity e : result.getEntities()) {
            String simple = e.getDisplayName();
            bySimple.put(simple, e);
            byFqn.put(e.getClassFqn(), e);
        }

        // Database グループ
        Set<String> placed = new LinkedHashSet<>();
        for (RoomDatabase db : result.getDatabases()) {
            sb.append("package \"").append(escape(db.getDisplayName()));
            if (db.getVersion() >= 0) {
                sb.append(" (v").append(db.getVersion()).append(")");
            }
            sb.append("\" {\n");
            for (String entityRef : db.getEntityClasses()) {
                RoomEntity e = resolveEntity(entityRef, bySimple, byFqn);
                if (e != null && placed.add(e.getClassFqn())) {
                    renderEntity(sb, e, "  ");
                }
            }
            sb.append("}\n");
        }
        // Database に属さない孤立 Entity
        for (RoomEntity e : result.getEntities()) {
            if (placed.add(e.getClassFqn())) {
                renderEntity(sb, e, "");
            }
        }

        // 外部キーエッジ
        for (RoomEntity e : result.getEntities()) {
            for (String fkTarget : e.getForeignKeyTargets()) {
                RoomEntity to = resolveEntity(fkTarget, bySimple, byFqn);
                if (to == null) continue;
                sb.append(alias(e)).append(" }o--|| ").append(alias(to))
                        .append(" : FK\n");
            }
        }
        sb.append("@enduml\n");
        return sb.toString();
    }

    private static RoomEntity resolveEntity(String ref,
                                              Map<String, RoomEntity> bySimple,
                                              Map<String, RoomEntity> byFqn) {
        if (ref == null || ref.isEmpty()) return null;
        if (ref.indexOf('.') >= 0) {
            RoomEntity e = byFqn.get(ref);
            if (e != null) return e;
            // FQN の simpleName 部だけ取り出してリトライ
            int dot = ref.lastIndexOf('.');
            return bySimple.get(ref.substring(dot + 1));
        }
        return bySimple.get(ref);
    }

    private static void renderEntity(StringBuilder sb, RoomEntity e, String indent) {
        sb.append(indent).append("entity \"").append(escape(e.getDisplayName()));
        if (!e.getTableName().isEmpty()) {
            sb.append("\\n<<").append(escape(e.getTableName())).append(">>");
        }
        sb.append("\" as ").append(alias(e)).append(" {\n");
        for (RoomEntity.Column col : e.getColumns()) {
            sb.append(indent).append("  ");
            sb.append(col.isPrimaryKey() ? "* " : "  ");
            sb.append(escape(col.getName())).append(" : ")
                    .append(escape(simpleType(col.getType())));
            if (!col.getColumnAlias().isEmpty()) {
                sb.append(" (").append(escape(col.getColumnAlias())).append(")");
            }
            sb.append('\n');
        }
        sb.append(indent).append("}\n");
    }

    private static String alias(RoomEntity e) {
        StringBuilder sb = new StringBuilder("e_");
        for (char c : e.getClassFqn().toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    private static String simpleType(String type) {
        if (type == null) return "";
        int dot = type.lastIndexOf('.');
        return dot < 0 ? type : type.substring(dot + 1);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
