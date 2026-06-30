// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.JavaMethodInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android Jetpack Room の {@code @Entity} / {@code @Dao} / {@code @Database}
 * アノテーションを {@link JavaClassInfo} ツリーから検出する。
 *
 * <p>{@link JavaClassInfo} は注釈を文字列リストで保持 (例:
 * {@code "@Entity(tableName = \"users\")"}) しているため、軽量な正規表現で
 * アノテーション引数を取り出す。フル AST パースはしない。</p>
 */
public final class RoomAnalyzer {

    /** Room データ集約結果。 */
    public static final class Result {
        private final List<RoomEntity> entities = new ArrayList<>();
        private final List<RoomDao> daos = new ArrayList<>();
        private final List<RoomDatabase> databases = new ArrayList<>();

        public List<RoomEntity> getEntities() { return entities; }
        public List<RoomDao> getDaos() { return daos; }
        public List<RoomDatabase> getDatabases() { return databases; }

        public boolean isEmpty() {
            return entities.isEmpty() && daos.isEmpty() && databases.isEmpty();
        }
    }

    private static final Pattern ENTITY_TABLENAME = Pattern.compile(
            "tableName\\s*=\\s*\"([^\"]+)\"");
    /**
     * Java {@code @ForeignKey(entity = User.class, ...)} と
     * Kotlin {@code ForeignKey(entity = User::class, ...)} 両形式に対応。
     * Kotlin の {@code @Entity(foreignKeys = [...])} 内では {@code ForeignKey} は
     * クラスコンストラクタ呼び出しとして書かれるため {@code @} は付かない。
     */
    private static final Pattern ENTITY_FOREIGN_KEY = Pattern.compile(
            "@?ForeignKey\\s*\\([^)]*entity\\s*=\\s*([A-Za-z_$][A-Za-z0-9_$.]*)"
                    + "(?:\\.class|::class)");
    private static final Pattern COLUMN_NAME = Pattern.compile(
            "name\\s*=\\s*\"([^\"]+)\"");
    /**
     * Java {@code entities = {Foo.class, Bar.class}} と
     * Kotlin {@code entities = [Foo::class, Bar::class]} 両形式に対応。
     */
    private static final Pattern DATABASE_ENTITIES = Pattern.compile(
            "entities\\s*=\\s*[\\[{]([^\\]}]*)[\\]}]");
    private static final Pattern DATABASE_VERSION = Pattern.compile(
            "version\\s*=\\s*(\\d+)");
    private static final Pattern QUERY_SQL = Pattern.compile(
            "@?Query\\s*\\(\\s*\"([^\"]+)\"");
    /**
     * {@code Foo.class} (Java) または {@code Foo::class} (Kotlin) のクラス参照。
     */
    private static final Pattern ENTITY_CLASS_REF = Pattern.compile(
            "([A-Za-z_$][A-Za-z0-9_$.]*)(?:\\.class|::class)");

    /** クラス群を走査して Room 関連クラスを抽出する。 */
    public Result analyze(Collection<JavaClassInfo> classes) {
        Result result = new Result();
        if (classes == null) {
            return result;
        }
        for (JavaClassInfo c : classes) {
            if (c == null) continue;
            String entityAnn = findAnnotation(c.getAnnotations(), "Entity");
            if (entityAnn != null) {
                result.getEntities().add(buildEntity(c, entityAnn));
                continue;
            }
            String daoAnn = findAnnotation(c.getAnnotations(), "Dao");
            if (daoAnn != null) {
                result.getDaos().add(buildDao(c));
                continue;
            }
            String dbAnn = findAnnotation(c.getAnnotations(), "Database");
            if (dbAnn != null) {
                result.getDatabases().add(buildDatabase(c, dbAnn));
            }
        }
        return result;
    }

    /** annotations リストから指定名のアノテーション本体を返す (見つからなければ null)。 */
    private static String findAnnotation(List<String> annotations, String name) {
        if (annotations == null) return null;
        for (String a : annotations) {
            String body = a.startsWith("@") ? a.substring(1) : a;
            // 引数除去前の名前
            int paren = body.indexOf('(');
            String nameOnly = paren >= 0 ? body.substring(0, paren) : body;
            int dot = nameOnly.lastIndexOf('.');
            if (dot >= 0) nameOnly = nameOnly.substring(dot + 1);
            if (name.equals(nameOnly.trim())) {
                return a;
            }
        }
        return null;
    }

    private static RoomEntity buildEntity(JavaClassInfo c, String entityAnn) {
        String tableName = "";
        Matcher m = ENTITY_TABLENAME.matcher(entityAnn);
        if (m.find()) {
            tableName = m.group(1);
        }
        RoomEntity entity = new RoomEntity(c.getQualifiedName(), tableName, "");
        // ForeignKey
        Matcher fk = ENTITY_FOREIGN_KEY.matcher(entityAnn);
        while (fk.find()) {
            entity.getForeignKeyTargets().add(fk.group(1));
        }
        // フィールド = 列
        for (JavaFieldInfo f : c.getFields()) {
            // static/const フィールド (テーブル名・列名の定数など) は列ではないので除外
            if (f.isStatic()) {
                continue;
            }
            boolean pk = false;
            String columnName = "";
            for (String fa : f.getAnnotations()) {
                String body = fa.startsWith("@") ? fa.substring(1) : fa;
                int paren = body.indexOf('(');
                String nameOnly = paren >= 0 ? body.substring(0, paren) : body;
                int dot = nameOnly.lastIndexOf('.');
                if (dot >= 0) nameOnly = nameOnly.substring(dot + 1);
                nameOnly = nameOnly.trim();
                if ("PrimaryKey".equals(nameOnly)) {
                    pk = true;
                } else if ("ColumnInfo".equals(nameOnly)) {
                    Matcher cn = COLUMN_NAME.matcher(fa);
                    if (cn.find()) {
                        columnName = cn.group(1);
                    }
                }
            }
            entity.getColumns().add(new RoomEntity.Column(
                    f.getName(), f.getType(), pk, columnName));
        }
        return entity;
    }

    private static RoomDao buildDao(JavaClassInfo c) {
        RoomDao dao = new RoomDao(c.getQualifiedName(), "");
        for (JavaMethodInfo mth : c.getMethods()) {
            RoomDao.OperationKind kind = RoomDao.OperationKind.OTHER;
            String sql = "";
            for (String ma : mth.getAnnotations()) {
                String body = ma.startsWith("@") ? ma.substring(1) : ma;
                int paren = body.indexOf('(');
                String nameOnly = paren >= 0 ? body.substring(0, paren) : body;
                int dot = nameOnly.lastIndexOf('.');
                if (dot >= 0) nameOnly = nameOnly.substring(dot + 1);
                nameOnly = nameOnly.trim();
                switch (nameOnly) {
                    case "Query":
                        kind = RoomDao.OperationKind.QUERY;
                        Matcher qm = QUERY_SQL.matcher(ma);
                        if (qm.find()) sql = qm.group(1);
                        break;
                    case "Insert":
                        kind = RoomDao.OperationKind.INSERT;
                        break;
                    case "Update":
                        kind = RoomDao.OperationKind.UPDATE;
                        break;
                    case "Delete":
                        kind = RoomDao.OperationKind.DELETE;
                        break;
                    case "RawQuery":
                        kind = RoomDao.OperationKind.RAW_QUERY;
                        break;
                    default:
                        break;
                }
                if (kind != RoomDao.OperationKind.OTHER) break;
            }
            if (kind != RoomDao.OperationKind.OTHER) {
                dao.getOperations().add(new RoomDao.Operation(
                        mth.getName(), kind, sql, mth.getReturnType()));
            }
        }
        return dao;
    }

    private static RoomDatabase buildDatabase(JavaClassInfo c, String dbAnn) {
        int version = -1;
        Matcher vm = DATABASE_VERSION.matcher(dbAnn);
        if (vm.find()) {
            try {
                version = Integer.parseInt(vm.group(1));
            } catch (NumberFormatException ex) {
                // ignore
            }
        }
        RoomDatabase db = new RoomDatabase(c.getQualifiedName(), version, "");
        Matcher em = DATABASE_ENTITIES.matcher(dbAnn);
        if (em.find()) {
            String inner = em.group(1);
            Matcher cr = ENTITY_CLASS_REF.matcher(inner);
            while (cr.find()) {
                db.getEntityClasses().add(cr.group(1));
            }
        }
        return db;
    }
}
