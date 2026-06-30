// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import org.junit.Test;
import juml.core.formats.kotlin.KotlinLightScanner;
import juml.core.formats.uml.JavaClassInfo;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin ソースから {@link KotlinLightScanner} で生成した {@link JavaClassInfo} を
 * {@link RoomAnalyzer} に流すブリッジテスト。Java と同じ精度で Room スキーマが取れることを確認。
 */
public class RoomAnalyzerKotlinTest {

    private static List<JavaClassInfo> scanKotlin(String src) {
        return KotlinLightScanner.scan(src, ErrorListener.silent());
    }

    @Test
    public void detectsKotlinDataClassEntity() {
        String src = "package com.x\n"
                + "@Entity(tableName = \"users\")\n"
                + "data class User(\n"
                + "  @PrimaryKey val id: Long,\n"
                + "  val name: String,\n"
                + "  @ColumnInfo(name = \"email_addr\") val email: String\n"
                + ")\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(scanKotlin(src));
        assertEquals(1, r.getEntities().size());
        RoomEntity e = r.getEntities().get(0);
        assertEquals("com.x.User", e.getClassFqn());
        assertEquals("users", e.getTableName());
        assertEquals(3, e.getColumns().size());
        RoomEntity.Column pk = e.getPrimaryKey();
        assertNotNull(pk);
        assertEquals("id", pk.getName());
        boolean aliasFound = false;
        for (RoomEntity.Column c : e.getColumns()) {
            if ("email".equals(c.getName()) && "email_addr".equals(c.getColumnAlias())) {
                aliasFound = true;
            }
        }
        assertTrue("ColumnInfo alias must be captured", aliasFound);
    }

    @Test
    public void detectsKotlinDaoOperations() {
        String src = "package com.x\n"
                + "@Dao\n"
                + "interface UserDao {\n"
                + "  @Query(\"SELECT * FROM users WHERE id = :id\")\n"
                + "  fun findById(id: Long): User\n"
                + "  @Insert\n"
                + "  fun insert(u: User)\n"
                + "  @Update\n"
                + "  fun update(u: User)\n"
                + "  @Delete\n"
                + "  fun delete(u: User)\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(scanKotlin(src));
        assertEquals(1, r.getDaos().size());
        RoomDao dao = r.getDaos().get(0);
        assertEquals(4, dao.getOperations().size());
        RoomDao.Operation query = dao.getOperations().get(0);
        assertEquals(RoomDao.OperationKind.QUERY, query.getKind());
        assertTrue(query.getSql().contains("SELECT"));
        assertEquals(RoomDao.OperationKind.INSERT, dao.getOperations().get(1).getKind());
        assertEquals(RoomDao.OperationKind.UPDATE, dao.getOperations().get(2).getKind());
        assertEquals(RoomDao.OperationKind.DELETE, dao.getOperations().get(3).getKind());
    }

    @Test
    public void detectsKotlinDatabase() {
        String src = "package com.x\n"
                + "@Database(entities = [User::class, Post::class], version = 3)\n"
                + "abstract class AppDb : RoomDatabase() {\n"
                + "  abstract fun userDao(): UserDao\n"
                + "}\n";
        List<JavaClassInfo> infos = scanKotlin(src);
        assertEquals(1, infos.size());
        boolean hasDatabaseAnn = false;
        for (String a : infos.get(0).getAnnotations()) {
            if (a.startsWith("@Database")) hasDatabaseAnn = true;
        }
        assertTrue("Must capture @Database annotation", hasDatabaseAnn);
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(infos);
        assertEquals(1, r.getDatabases().size());
        RoomDatabase db = r.getDatabases().get(0);
        assertEquals(3, db.getVersion());
        // Kotlin の [Foo::class, Bar::class] 形式から entities を抽出できること
        assertEquals(2, db.getEntityClasses().size());
        assertTrue(db.getEntityClasses().contains("User"));
        assertTrue(db.getEntityClasses().contains("Post"));
    }

    @Test
    public void detectsKotlinForeignKey() {
        String src = "package com.x\n"
                + "@Entity(tableName = \"posts\", foreignKeys = [\n"
                + "  ForeignKey(entity = User::class, parentColumns = [\"id\"],"
                + " childColumns = [\"userId\"])\n"
                + "])\n"
                + "data class Post(\n"
                + "  @PrimaryKey val id: Long,\n"
                + "  val userId: Long\n"
                + ")\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(scanKotlin(src));
        assertEquals(1, r.getEntities().size());
        RoomEntity post = r.getEntities().get(0);
        // Kotlin の @ForeignKey(entity = User::class, ...) からターゲットを抽出
        assertEquals(1, post.getForeignKeyTargets().size());
        assertEquals("User", post.getForeignKeyTargets().get(0));
    }

    @Test
    public void mixedJavaAndKotlinEntities() {
        // Java と Kotlin 両方の Entity を入れて結合できることを確認
        String kotlin = "package com.x\n"
                + "@Entity data class User(@PrimaryKey val id: Long)\n";
        String java = "package com.x;\n"
                + "@Entity public class Post {\n"
                + "  @PrimaryKey public long id;\n"
                + "}\n";
        List<JavaClassInfo> all = new ArrayList<>();
        all.addAll(scanKotlin(kotlin));
        all.addAll(juml.core.formats.uml.JavaStructureExtractor.extract(
                java, ErrorListener.silent()));
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(all);
        assertEquals(2, r.getEntities().size());
    }

    @Test
    public void companionConstantsAreNotColumns() {
        // companion object の const val (テーブル名/列名の定数) は列として数えない
        String src = "package com.x\n"
                + "@Entity(tableName = User.TABLE)\n"
                + "data class User(\n"
                + "  @PrimaryKey val id: Long,\n"
                + "  val name: String\n"
                + ") {\n"
                + "  companion object {\n"
                + "    const val TABLE: String = \"users\"\n"
                + "    const val COL_NAME: String = \"name\"\n"
                + "  }\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(scanKotlin(src));
        RoomEntity e = r.getEntities().get(0);
        java.util.Set<String> names = new java.util.HashSet<>();
        for (RoomEntity.Column c : e.getColumns()) {
            names.add(c.getName());
        }
        assertEquals(2, e.getColumns().size());
        assertTrue("real columns id/name kept", names.contains("id") && names.contains("name"));
        assertTrue("const constants must not be columns",
                !names.contains("TABLE") && !names.contains("COL_NAME"));
    }
}
