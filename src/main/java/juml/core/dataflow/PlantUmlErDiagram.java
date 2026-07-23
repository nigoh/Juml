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
        Map<String, String> aliasByFqn = new LinkedHashMap<>();
        for (RoomEntity e : result.getEntities()) {
            String simple = e.getDisplayName();
            bySimple.put(simple, e);
            byFqn.put(e.getClassFqn(), e);
            // エイリアスを FQN のサニタイズ (非英数字→_) で作ると非単射になり、区切り位置だけ
            // 異なる FQN (com.x_foo.Bar と com.x.foo_Bar) が同一エイリアスへ畳まれて entity が
            // 消え、FK も誤結線する。PACKAGE(P0..)/NAVIGATION(D0..) と同様に FQN ごとの連番
            // エイリアスで一意化する。
            aliasByFqn.putIfAbsent(e.getClassFqn(), "e" + aliasByFqn.size());
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
                    renderEntity(sb, e, "  ", aliasByFqn);
                }
            }
            sb.append("}\n");
        }
        // Database に属さない孤立 Entity
        for (RoomEntity e : result.getEntities()) {
            if (placed.add(e.getClassFqn())) {
                renderEntity(sb, e, "", aliasByFqn);
            }
        }

        // 外部キーエッジ
        for (RoomEntity e : result.getEntities()) {
            for (String fkTarget : e.getForeignKeyTargets()) {
                RoomEntity to = resolveEntity(fkTarget, bySimple, byFqn);
                if (to == null) continue;
                sb.append(alias(e, aliasByFqn)).append(" }o--|| ")
                        .append(alias(to, aliasByFqn)).append(" : FK\n");
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

    private static void renderEntity(StringBuilder sb, RoomEntity e, String indent,
                                     Map<String, String> aliasByFqn) {
        sb.append(indent).append("entity \"").append(escape(e.getDisplayName()));
        if (!e.getTableName().isEmpty()) {
            sb.append("\\n<<").append(escape(e.getTableName())).append(">>");
        }
        sb.append("\" as ").append(alias(e, aliasByFqn)).append(" {\n");
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

    /** 一意採番済みエイリアスを返す。未登録 (通常起きない) の場合のみサニタイズ名へフォールバック。 */
    private static String alias(RoomEntity e, Map<String, String> aliasByFqn) {
        String a = aliasByFqn.get(e.getClassFqn());
        return a != null ? a : sanitizeAlias(e.getClassFqn());
    }

    private static String sanitizeAlias(String fqn) {
        StringBuilder sb = new StringBuilder("e_");
        for (char c : fqn.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    /**
     * 完全修飾型を単純名へ畳む。ジェネリクスは生型と各型引数を再帰的に単純化する
     * (例: {@code java.util.List<java.lang.String>} → {@code List<String>})。
     * 単純に最後のドット以降を取ると {@code String>} のように壊れるため、
     * {@code <...>} を分離してから畳む。
     */
    private static String simpleType(String type) {
        if (type == null) {
            return "";
        }
        String t = type.trim();
        int lt = t.indexOf('<');
        if (lt < 0) {
            int dot = t.lastIndexOf('.');
            return dot < 0 ? t : t.substring(dot + 1);
        }
        int gt = t.lastIndexOf('>');
        if (gt < lt) {
            gt = t.length();
        }
        String rawSimple = simpleType(t.substring(0, lt));
        String args = t.substring(lt + 1, gt);
        StringBuilder sb = new StringBuilder(rawSimple).append('<');
        java.util.List<String> parts = splitTopLevelArgs(args);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(simpleType(parts.get(i)));
        }
        return sb.append('>').toString();
    }

    /** ジェネリクス引数を、入れ子の {@code <>} を跨がないトップレベルのカンマで分割する。 */
    private static java.util.List<String> splitTopLevelArgs(String args) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(args.substring(start, i));
                start = i + 1;
            }
        }
        out.add(args.substring(start));
        return out;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
