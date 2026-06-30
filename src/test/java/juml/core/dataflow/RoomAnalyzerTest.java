// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.dataflow;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaStructureExtractor;
import juml.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Room {@code @Entity} / {@code @Dao} / {@code @Database} 検出の単体テスト。
 */
public class RoomAnalyzerTest {

    private static List<JavaClassInfo> parse(String... sources) {
        List<JavaClassInfo> all = new ArrayList<>();
        for (String src : sources) {
            all.addAll(JavaStructureExtractor.extract(src, ErrorListener.silent()));
        }
        return all;
    }

    @Test
    public void detectsEntityWithTableNameAndPrimaryKey() {
        String src = "package com.x;\n"
                + "@Entity(tableName = \"users\")\n"
                + "public class User {\n"
                + "  @PrimaryKey\n"
                + "  public long id;\n"
                + "  @ColumnInfo(name = \"display_name\")\n"
                + "  public String name;\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(src));
        assertEquals(1, r.getEntities().size());
        RoomEntity e = r.getEntities().get(0);
        assertEquals("com.x.User", e.getClassFqn());
        assertEquals("users", e.getTableName());
        assertEquals(2, e.getColumns().size());
        RoomEntity.Column pk = e.getPrimaryKey();
        assertNotNull(pk);
        assertEquals("id", pk.getName());
        // ColumnInfo alias
        boolean foundAlias = false;
        for (RoomEntity.Column c : e.getColumns()) {
            if ("name".equals(c.getName()) && "display_name".equals(c.getColumnAlias())) {
                foundAlias = true;
            }
        }
        assertTrue(foundAlias);
    }

    @Test
    public void detectsDaoOperations() {
        String src = "package com.x;\n"
                + "@Dao\n"
                + "public interface UserDao {\n"
                + "  @Query(\"SELECT * FROM users WHERE id = :id\")\n"
                + "  User findById(long id);\n"
                + "  @Insert\n"
                + "  void insert(User u);\n"
                + "  @Update\n"
                + "  void update(User u);\n"
                + "  @Delete\n"
                + "  void delete(User u);\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(src));
        assertEquals(1, r.getDaos().size());
        RoomDao dao = r.getDaos().get(0);
        assertEquals(4, dao.getOperations().size());
        // Kind 分類
        RoomDao.Operation query = dao.getOperations().get(0);
        assertEquals(RoomDao.OperationKind.QUERY, query.getKind());
        assertTrue(query.getSql().contains("SELECT"));
        assertEquals(RoomDao.OperationKind.INSERT, dao.getOperations().get(1).getKind());
        assertEquals(RoomDao.OperationKind.UPDATE, dao.getOperations().get(2).getKind());
        assertEquals(RoomDao.OperationKind.DELETE, dao.getOperations().get(3).getKind());
    }

    @Test
    public void detectsDatabaseEntitiesAndVersion() {
        String src = "package com.x;\n"
                + "@Database(entities = {User.class, Post.class}, version = 3)\n"
                + "public abstract class AppDb extends RoomDatabase {\n"
                + "  public abstract UserDao userDao();\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(src));
        assertEquals(1, r.getDatabases().size());
        RoomDatabase db = r.getDatabases().get(0);
        assertEquals("com.x.AppDb", db.getClassFqn());
        assertEquals(3, db.getVersion());
        assertEquals(2, db.getEntityClasses().size());
        assertTrue(db.getEntityClasses().contains("User"));
        assertTrue(db.getEntityClasses().contains("Post"));
    }

    @Test
    public void detectsForeignKey() {
        String src = "package com.x;\n"
                + "@Entity(tableName = \"posts\", foreignKeys = {\n"
                + "  @ForeignKey(entity = User.class, parentColumns = \"id\","
                + " childColumns = \"userId\")\n"
                + "})\n"
                + "public class Post {\n"
                + "  @PrimaryKey public long id;\n"
                + "  public long userId;\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(src));
        assertEquals(1, r.getEntities().size());
        RoomEntity post = r.getEntities().get(0);
        assertEquals(1, post.getForeignKeyTargets().size());
        assertEquals("User", post.getForeignKeyTargets().get(0));
    }

    @Test
    public void erDiagramContainsEntityAndForeignKey() {
        String userSrc = "package com.x;\n"
                + "@Entity public class User {\n"
                + "  @PrimaryKey public long id;\n"
                + "}\n";
        String postSrc = "package com.x;\n"
                + "@Entity(foreignKeys = {@ForeignKey(entity = User.class,"
                + " parentColumns = \"id\", childColumns = \"userId\")})\n"
                + "public class Post {\n"
                + "  @PrimaryKey public long id;\n"
                + "  public long userId;\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(userSrc, postSrc));
        String puml = PlantUmlErDiagram.render(r);
        assertTrue(puml.contains("@startuml"));
        assertTrue(puml.contains("@enduml"));
        assertTrue(puml.contains("User"));
        assertTrue(puml.contains("Post"));
        assertTrue("ER must include FK edge", puml.contains(": FK"));
    }

    @Test
    public void markdownReportSummarizesEntities() {
        String src = "package com.x;\n"
                + "@Entity(tableName = \"users\") public class User {\n"
                + "  @PrimaryKey public long id;\n"
                + "  public String name;\n"
                + "}\n";
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(parse(src));
        String md = MarkdownDataFlowReport.render(r);
        assertTrue(md.contains("Room Data Flow Report"));
        assertTrue(md.contains("User"));
        assertTrue(md.contains("users"));
        assertTrue(md.contains("Primary key"));
    }

    @Test
    public void emptyResultRendersPlaceholder() {
        RoomAnalyzer.Result r = new RoomAnalyzer().analyze(new ArrayList<>());
        String md = MarkdownDataFlowReport.render(r);
        assertTrue(md.contains("no Room"));
    }
}
