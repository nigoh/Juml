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
     * エンティティ単位の複合主キー {@code @Entity(primaryKeys = {"a", "b"})} /
     * Kotlin {@code primaryKeys = ["a", "b"]}。フィールド単位の {@code @PrimaryKey} を
     * 使わない複合キー宣言を拾うために別途解析する。
     */
    private static final Pattern ENTITY_PRIMARY_KEYS = Pattern.compile(
            "primaryKeys\\s*=\\s*[\\[{]([^\\]}]*)[\\]}]");
    private static final Pattern DATABASE_VERSION = Pattern.compile(
            "version\\s*=\\s*(\\d+)");

    /**
     * アノテーションの {@code member = …} の値を、<b>記法を問わず</b>そのまま返す
     * (見つからなければ空文字)。
     *
     * <p>値の終わりは「入れ子の外側のカンマ、または全体の終わり」。囲みの
     * {@code &#123;&#125;} / {@code []} は付いていれば剥がす。記法を数え上げないのは、
     * Java が要素 1 個のときに囲みを省略できるからで、実際
     * {@code @Database(entities = Word.class, version = 1)} — Room の公式 codelab の
     * 書き方そのもの — で entity が 1 件も取れず、ER 図のデータベース枠が空になり
     * エンティティが枠の外に孤立していた。</p>
     */
    private static String annotationMemberValue(String ann, String member) {
        Matcher m = Pattern.compile("\\b" + member + "\\s*=\\s*").matcher(ann);
        if (!m.find()) {
            return "";
        }
        int depth = 0;
        int i = m.end();
        StringBuilder sb = new StringBuilder();
        for (; i < ann.length(); i++) {
            char c = ann.charAt(i);
            if (c == '"') {
                i = endOfStringLiteral(ann, i); // 文字列リテラルは丸ごと読み飛ばす
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                depth++;
            } else if (c == '}' || c == ']' || c == ')') {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                break;
            }
            sb.append(c);
        }
        String v = sb.toString().trim();
        if (v.length() >= 2 && (v.charAt(0) == '{' || v.charAt(0) == '[')) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    /** {@code open} の {@code "} から閉じ {@code "} までの位置 (エスケープ考慮)。 */
    private static int endOfStringLiteral(String s, int open) {
        for (int i = open + 1; i < s.length(); i++) {
            if (s.charAt(i) == '\\') {
                i++;
            } else if (s.charAt(i) == '"') {
                return i;
            }
        }
        return s.length() - 1;
    }

    /**
     * アノテーション本文に含まれる<b>すべての文字列リテラルを連結</b>して返す。
     *
     * <p>SQL は「引数に書かれた文字列リテラルの連結」であって「開き括弧の直後の
     * 最初のリテラル」ではない。後者で切り出していたため、
     * {@code @Query(value = "…")} は SQL が空欄になり (SQL 無しのメソッドと区別できない)、
     * 複数リテラルに分けて書いた SQL は最初のリテラルで<b>黙って切れて</b>いた。
     * どのテーブルを触るか調べる読み手に、欠損ではなく<b>嘘</b>を返すことになる。</p>
     */
    private static String concatStringLiterals(String ann) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ann.length(); i++) {
            if (ann.charAt(i) != '"') {
                continue;
            }
            int end = endOfStringLiteral(ann, i);
            sb.append(ann, i + 1, end);
            i = end;
        }
        return sb.toString();
    }
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
        // エンティティ単位の複合主キー (primaryKeys = {"a", "b"}) を収集する。
        java.util.Set<String> compositePk = parsePrimaryKeyNames(entityAnn);
        // フィールド = 列
        for (JavaFieldInfo f : c.getFields()) {
            // static/const フィールド (テーブル名・列名の定数など) は列ではないので除外
            if (f.isStatic()) {
                continue;
            }
            boolean pk = compositePk.contains(f.getName());
            String columnName = "";
            boolean skip = false;
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
                } else if ("Ignore".equals(nameOnly) || "Relation".equals(nameOnly)
                        || "Embedded".equals(nameOnly)) {
                    // @Ignore は非永続化、@Relation は導出プロパティ、@Embedded は
                    // 実列ではなく子オブジェクトのサブ列に展開される。いずれもこの
                    // フィールド自体を単一の列として出すのは誤りなので除外する。
                    skip = true;
                }
            }
            if (skip) {
                continue;
            }
            entity.getColumns().add(new RoomEntity.Column(
                    f.getName(), f.getType(), pk, columnName));
        }
        return entity;
    }

    /**
     * {@code @Entity(primaryKeys = {"a", "b"})} から主キー列名の集合を取り出す。
     * 引用符と空白を除いて素の名前だけを返す。宣言が無ければ空集合。
     */
    private static java.util.Set<String> parsePrimaryKeyNames(String entityAnn) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        Matcher pk = ENTITY_PRIMARY_KEYS.matcher(entityAnn);
        if (pk.find()) {
            for (String raw : pk.group(1).split(",")) {
                String n = raw.replace("\"", "").trim();
                if (!n.isEmpty()) {
                    names.add(n);
                }
            }
        }
        return names;
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
                        sql = concatStringLiterals(ma);
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
        Matcher cr = ENTITY_CLASS_REF.matcher(annotationMemberValue(dbAnn, "entities"));
        while (cr.find()) {
            db.getEntityClasses().add(cr.group(1));
        }
        return db;
    }
}
