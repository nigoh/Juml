// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.kotlin;

import org.junit.Test;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaFieldInfo;
import juml.core.formats.uml.Visibility;
import juml.util.ErrorListener;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Kotlin → JavaClassInfo ブリッジの単体テスト。
 */
public class KotlinLightScannerTest {

    @Test
    public void parsesSimpleClass() {
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  val name: String = \"\"\n"
                + "  fun greet(): String = \"hi\"\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals("com.x", c.getPackageName());
        assertEquals("Foo", c.getSimpleName());
        assertEquals(JavaClassInfo.Kind.CLASS, c.getKind());
        assertEquals(1, c.getFields().size());
        assertEquals("name", c.getFields().get(0).getName());
        assertEquals("String", c.getFields().get(0).getType());
    }

    @Test
    public void kdocMentioningClassWordIsNotParsedAsDeclaration() {
        // KDoc/コメント内の "class" という単語 (例: "This class holds ...") を実クラス宣言と
        // 誤認しないこと。誤認すると "holds" という擬似クラスが生成され、後続の英文が
        // スーパータイプ名として抽出されて不正な PlantUML になっていた (Android サンプルの
        // Donut.kt でクラス図がレンダリング失敗した回帰)。
        String src = "package com.x\n"
                + "/**\n"
                + " * This class holds the data we track for each donut: its name, a description, and\n"
                + " * a rating.\n"
                + " */\n"
                + "data class Donut(val id: Long, val name: String)\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals("only the real 'Donut' class should be extracted", 1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals("Donut", c.getSimpleName());
        // コメント由来の擬似スーパータイプ ("its name" / "a description" / "*/") が無いこと。
        assertTrue("no bogus interfaces from comment text: " + c.getInterfaces(),
                c.getInterfaces().isEmpty());
        assertTrue("no bogus superclass from comment text: " + c.getSuperClass(),
                c.getSuperClass() == null || c.getSuperClass().isEmpty());
    }

    @Test
    public void classKeywordInsideStringLiteralIsIgnored() {
        // 文字列リテラル内の "class Foo" をクラス宣言と誤認しないこと。
        String src = "package com.x\n"
                + "class Real {\n"
                + "  val msg: String = \"use class Fake for testing\"\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals("Real", infos.get(0).getSimpleName());
    }

    @Test
    public void bodylessClassDoesNotAbsorbNextClassMembers() {
        // 本体 {} を持たないクラス (data class / object) が、後続クラスの本体ブレースを
        // 誤って取り込み、メンバを横取りしないこと。
        String src = "package com.x\n"
                + "sealed class Result {\n"
                + "  data class Success(val data: String) : Result()\n"
                + "  object Loading : Result()\n"
                + "}\n"
                + "class ViewModel {\n"
                + "  val users: List<String> = emptyList()\n"
                + "  fun load(): String = \"\"\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo success = infos.stream()
                .filter(c -> "Success".equals(c.getSimpleName())).findFirst().orElse(null);
        assertNotNull(success);
        // Success は自身の primary ctor プロパティ data のみを持つ
        assertTrue("Success must have its own 'data' field", success.getFields().stream()
                .anyMatch(f -> "data".equals(f.getName())));
        assertFalse("Success must not absorb ViewModel.users", success.getFields().stream()
                .anyMatch(f -> "users".equals(f.getName())));
        assertTrue("Success must not absorb ViewModel.load", success.getMethods().stream()
                .noneMatch(m -> "load".equals(m.getName())));
    }

    @Test
    public void capturesSupertypesAndInterfaces() {
        // : SuperClass(...) はスーパークラス、() の無い型はインタフェースとして取り込む
        String src = "package com.x\n"
                + "class MainActivity(private val dep: Dep) : AppCompatActivity(), View.OnClickListener, Serializable {\n"
                + "  fun onCreate() {}\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo c = infos.get(0);
        assertEquals("AppCompatActivity", c.getSuperClass());
        assertTrue("must implement View.OnClickListener",
                c.getInterfaces().contains("View.OnClickListener"));
        assertTrue("must implement Serializable",
                c.getInterfaces().contains("Serializable"));
    }

    @Test
    public void capturesSupertypeOnBodylessClass() {
        // data class Foo(...) : Base() のように本体 {} の無い宣言でもスーパークラスを取る
        String src = "package com.x\n"
                + "sealed class Result\n"
                + "data class Ok(val v: Int) : Result()\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo ok = infos.stream()
                .filter(c -> "Ok".equals(c.getSimpleName())).findFirst().orElse(null);
        assertNotNull(ok);
        assertEquals("Result", ok.getSuperClass());
    }

    @Test
    public void capturesEnumConstants() {
        String src = "package com.x\n"
                + "enum class Direction { NORTH, SOUTH, EAST, WEST }\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo c = infos.get(0);
        assertEquals(JavaClassInfo.Kind.ENUM, c.getKind());
        assertEquals(4, c.getEnumConstants().size());
        assertTrue(c.getEnumConstants().contains("NORTH"));
        assertTrue(c.getEnumConstants().contains("WEST"));
    }

    @Test
    public void capturesEnumConstantsWithArgsBeforeMembers() {
        // 引数付き定数 + ';' 区切りの後続メンバを持つ enum class
        String src = "package com.x\n"
                + "enum class Planet(val mass: Double) {\n"
                + "  EARTH(5.976e+24),\n"
                + "  MARS(6.421e+23);\n"
                + "  fun gravity(): Double = mass\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo c = infos.get(0);
        assertEquals(2, c.getEnumConstants().size());
        assertTrue(c.getEnumConstants().contains("EARTH"));
        assertTrue(c.getEnumConstants().contains("MARS"));
        // 定数の後ろの fun は従来通りメソッドとして拾う
        assertTrue(c.getMethods().stream().anyMatch(m -> "gravity".equals(m.getName())));
        // mass は primary ctor プロパティとしてフィールド化
        assertTrue(c.getFields().stream().anyMatch(f -> "mass".equals(f.getName())));
    }

    @Test
    public void capturesMemberVisibility() {
        // Kotlin の private/protected/internal を可視化記号に反映する (既定で全部 public だった)
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  private val secret: String = \"\"\n"
                + "  protected val token: Int = 0\n"
                + "  internal val region: String = \"\"\n"
                + "  val opened: Boolean = true\n"
                + "  private fun hidden() {}\n"
                + "  fun shown() {}\n"
                + "}\n";
        JavaClassInfo c = KotlinLightScanner.scan(src, ErrorListener.silent()).get(0);
        assertEquals(Visibility.PRIVATE, fieldVis(c, "secret"));
        assertEquals(Visibility.PROTECTED, fieldVis(c, "token"));
        assertEquals(Visibility.PACKAGE, fieldVis(c, "region"));
        assertEquals(Visibility.PUBLIC, fieldVis(c, "opened"));
        assertEquals(Visibility.PRIVATE, methodVis(c, "hidden"));
        assertEquals(Visibility.PUBLIC, methodVis(c, "shown"));
    }

    private static Visibility fieldVis(JavaClassInfo c, String name) {
        return c.getFields().stream().filter(f -> name.equals(f.getName()))
                .findFirst().orElseThrow(AssertionError::new).getVisibility();
    }

    private static Visibility methodVis(JavaClassInfo c, String name) {
        return c.getMethods().stream().filter(m -> name.equals(m.getName()))
                .findFirst().orElseThrow(AssertionError::new).getVisibility();
    }

    @Test
    public void parsesInterface() {
        String src = "package com.x\n"
                + "interface Listener {\n"
                + "  fun onTap(view: View): Unit\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals(JavaClassInfo.Kind.INTERFACE, infos.get(0).getKind());
    }

    @Test
    public void parsesObjectAsClass() {
        String src = "package com.x\n"
                + "object Singleton {\n"
                + "  val flag: Boolean = true\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals("Singleton", infos.get(0).getSimpleName());
        assertEquals(JavaClassInfo.Kind.CLASS, infos.get(0).getKind());
    }

    @Test
    public void parsesPrimaryConstructorFields() {
        String src = "package com.x\n"
                + "data class User(val id: Long, val name: String, var email: String)\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals(3, c.getFields().size());
        assertEquals("id", c.getFields().get(0).getName());
        assertEquals("Long", c.getFields().get(0).getType());
        assertEquals("name", c.getFields().get(1).getName());
        assertEquals("email", c.getFields().get(2).getName());
    }

    @Test
    public void useSiteTargetAnnotationOnCtorParamDoesNotDropField() {
        // @get:/@field: などの use-site target 付きアノテーションがあっても
        // コンストラクタ引数フィールドが取りこぼされないこと (Room の列が消える回帰)
        String src = "package com.x\n"
                + "data class User(\n"
                + "  val id: Long,\n"
                + "  @get:NotificationMode @ColumnInfo(name = \"mode\") var mode: Int = 0\n"
                + ")\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo c = infos.get(0);
        assertEquals(2, c.getFields().size());
        assertEquals("mode", c.getFields().get(1).getName());
        assertEquals("Int", c.getFields().get(1).getType());
    }

    @Test
    public void capturesClassAnnotations() {
        String src = "package com.x\n"
                + "@Entity(tableName = \"users\")\n"
                + "data class User(@PrimaryKey val id: Long, val name: String)\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertTrue("Must capture @Entity",
                c.getAnnotations().stream().anyMatch(a -> a.startsWith("@Entity")));
        // Primary constructor parameter annotation
        JavaFieldInfo idField = c.getFields().get(0);
        assertTrue("Must capture @PrimaryKey",
                idField.getAnnotations().stream().anyMatch(a -> a.startsWith("@PrimaryKey")));
    }

    @Test
    public void capturesMethodAnnotations() {
        String src = "package com.x\n"
                + "@Dao\n"
                + "interface UserDao {\n"
                + "  @Query(\"SELECT * FROM users\")\n"
                + "  fun findAll(): List<User>\n"
                + "  @Insert\n"
                + "  fun insert(u: User)\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        assertEquals(2, c.getMethods().size());
        assertTrue(c.getMethods().get(0).getAnnotations().stream()
                .anyMatch(a -> a.startsWith("@Query")));
        assertTrue(c.getMethods().get(1).getAnnotations().stream()
                .anyMatch(a -> a.startsWith("@Insert")));
    }

    @Test
    public void capturesImports() {
        String src = "package com.x\n"
                + "import androidx.room.Entity\n"
                + "import androidx.room.PrimaryKey\n"
                + "import java.util.*\n"
                + "@Entity\n"
                + "class Foo\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals(3, infos.get(0).getImports().size());
        assertTrue(infos.get(0).getImports().contains("androidx.room.Entity"));
        assertTrue(infos.get(0).getImports().contains("java.util.*"));
    }

    @Test
    public void parsesMultipleTopLevelClasses() {
        String src = "package com.x\n"
                + "class A\n"
                + "interface B\n"
                + "data class C(val x: Int)\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(3, infos.size());
        assertEquals("A", infos.get(0).getSimpleName());
        assertEquals("B", infos.get(1).getSimpleName());
        assertEquals(JavaClassInfo.Kind.INTERFACE, infos.get(1).getKind());
        assertEquals("C", infos.get(2).getSimpleName());
        assertEquals(1, infos.get(2).getFields().size());
    }

    @Test
    public void parsesFunctionParametersAndReturnType() {
        String src = "package com.x\n"
                + "class A {\n"
                + "  fun doIt(input: String, count: Int = 1): Boolean {\n"
                + "    return true\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals(1, infos.get(0).getMethods().size());
        assertEquals("doIt", infos.get(0).getMethods().get(0).getName());
        assertEquals("Boolean", infos.get(0).getMethods().get(0).getReturnType());
        assertEquals(2, infos.get(0).getMethods().get(0).getParameterTypes().size());
        assertEquals("String", infos.get(0).getMethods().get(0).getParameterTypes().get(0));
        assertEquals("Int", infos.get(0).getMethods().get(0).getParameterTypes().get(1));
    }

    @Test
    public void parsesPackageWithoutSemicolon() {
        String src = "package com.x.y.z\nclass Foo\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        assertEquals("com.x.y.z", infos.get(0).getPackageName());
    }

    @Test
    public void emptyInputReturnsEmptyList() {
        assertEquals(0, KotlinLightScanner.scan("", ErrorListener.silent()).size());
        assertNotNull(KotlinLightScanner.scan(null, ErrorListener.silent()));
    }

    @Test
    public void localValAndFunInsideMethodBodyAreNotMembers() {
        // 関数本体内のローカル val / fun をクラスのフィールド/メソッドとして
        // 誤抽出しないこと (ネストしたコードブロックをマスクする)。
        String src = "class User(val id: Int) {\n"
                + "  fun compute() {\n"
                + "    val temp: String = load()\n"
                + "    fun helper() { }\n"
                + "  }\n"
                + "  val realField: Int = 5\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        assertEquals(1, infos.size());
        JavaClassInfo c = infos.get(0);
        java.util.Set<String> fieldNames = new java.util.HashSet<>();
        for (JavaFieldInfo f : c.getFields()) {
            fieldNames.add(f.getName());
        }
        assertTrue("primary ctor field kept", fieldNames.contains("id"));
        assertTrue("class-level field kept", fieldNames.contains("realField"));
        assertFalse("local val must not become a field", fieldNames.contains("temp"));
        java.util.Set<String> methodNames = new java.util.HashSet<>();
        for (juml.core.formats.uml.JavaMethodInfo m : c.getMethods()) {
            methodNames.add(m.getName());
        }
        assertTrue("class-level fun kept", methodNames.contains("compute"));
        assertFalse("local fun must not become a method", methodNames.contains("helper"));
    }

    @Test
    public void companionConstStillExtractedAfterLocalMasking() {
        // コードブロックのマスク導入後も companion object の const や
        // メソッドはホイストされること (退行防止)。
        String src = "data class User(val id: Long) {\n"
                + "  companion object {\n"
                + "    const val TABLE: String = \"users\"\n"
                + "    fun create(): User = User(0)\n"
                + "  }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo c = infos.get(0);
        boolean hasTable = false;
        for (JavaFieldInfo f : c.getFields()) {
            if ("TABLE".equals(f.getName())) {
                hasTable = true;
                assertTrue("const val is static", f.isStatic());
            }
        }
        assertTrue("companion const preserved", hasTable);
        boolean hasCreate = false;
        for (juml.core.formats.uml.JavaMethodInfo m : c.getMethods()) {
            if ("create".equals(m.getName())) {
                hasCreate = true;
            }
        }
        assertTrue("companion fun preserved", hasCreate);
    }

    private static juml.core.formats.uml.JavaClassInfo scanOne(String src) {
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        return infos.stream().filter(c -> "Foo".equals(c.getSimpleName()))
                .findFirst().orElse(infos.isEmpty() ? null : infos.get(0));
    }

    private static boolean hasField(JavaClassInfo c, String name) {
        return c.getFields().stream().anyMatch(f -> name.equals(f.getName()));
    }

    private static boolean hasMethod(JavaClassInfo c, String name) {
        return c.getMethods().stream().anyMatch(m -> name.equals(m.getName()));
    }

    @Test
    public void charLiteralClosingBrace_doesNotTruncateClassBody() {
        // '}' の文字リテラルを本体の閉じブレースと取り違えて、後続メンバを落とさないこと。
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  val close: Char = '}'\n"
                + "  val after: Int = 1\n"
                + "  fun bar(): Int = 2\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertTrue("'}' の後の field 'after' が残るはず", hasField(c, "after"));
        assertTrue("'}' の後の method 'bar' が残るはず", hasMethod(c, "bar"));
    }

    @Test
    public void charLiteralQuote_doesNotSwallowRestOfBody() {
        // '"' の文字リテラルを文字列開始と取り違えて以降を飲み込まないこと。
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  val q: Char = '\"'\n"
                + "  val after: Int = 1\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertTrue("'\"' の後の field 'after' が残るはず", hasField(c, "after"));
    }

    @Test
    public void rawStringWithBrace_doesNotTruncateClassBody() {
        // 生文字列 \"\"\"...}...\"\"\" 内の } を本体の閉じと取り違えないこと。
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  val sql: String = \"\"\"SELECT } FROM t WHERE x = \\\"y\\\"\"\"\"\n"
                + "  val after: Int = 1\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertTrue("生文字列の後の field 'after' が残るはず", hasField(c, "after"));
    }

    @Test
    public void primaryCtorDefaultStringWithBrace_doesNotMisplaceBody() {
        // 一次コンストラクタ既定値 "{" 内の { を本体開始と取り違えないこと。
        String src = "package com.x\n"
                + "class Foo(val x: String = \"{\") {\n"
                + "  val y: Int = 0\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertTrue("本体 field 'y' が抽出されるはず", hasField(c, "y"));
    }

    @Test
    public void useSiteTargetAnnotationCapturesRealName() {
        // @field:SerializedName(...) の実名を拾うこと ("@field" として記録して名前を落とさない)。
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  @field:SerializedName(\"user_id\") val id: Long = 0\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        JavaFieldInfo id = c.getFields().stream().filter(f -> "id".equals(f.getName()))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue("実アノテーション名 @SerializedName を拾うはず",
                id.getAnnotations().stream().anyMatch(a -> a.startsWith("@SerializedName")));
        assertFalse("use-site target @field を名前として記録しないこと",
                id.getAnnotations().stream().anyMatch(a -> a.equals("@field")));
    }

    @Test
    public void annotationArgContainingEnumWordDoesNotMisclassifyKind() {
        // @Entity(tableName = "enum_table") の "enum" 部分一致でクラスを ENUM 扱いしないこと。
        String src = "package com.x\n"
                + "@Entity(tableName = \"enum_table\")\n"
                + "data class Foo(val id: Long)\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertEquals(JavaClassInfo.Kind.CLASS, c.getKind());
    }

    @Test
    public void multiArgGenericSupertypeIsNotTruncated() {
        // Map<String, Int> のカンマ入りジェネリック supertype を切り詰めないこと。
        String src = "package com.x\n"
                + "class Foo : HashMap<String, Int>() {\n"
                + "  val y: Int = 0\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertEquals("HashMap<String, Int>", c.getSuperClass());
    }

    @Test
    public void multiArgGenericInterfaceSupertypeIsNotTruncated() {
        // interface 側 (Comparator<Map<String, Int>>) も by 委譲だけを剥がして温存する。
        String src = "package com.x\n"
                + "class Foo(cmp: Comparator<Map<String, Int>>) "
                + ": Comparator<Map<String, Int>> by cmp {\n"
                + "  val y: Int = 0\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertTrue("多引数ジェネリック interface を温存するはず",
                c.getInterfaces().contains("Comparator<Map<String, Int>>"));
    }

    @Test
    public void nestedNamedTypeMembersAreNotHoistedOntoEnclosingClass() {
        // 名前付きネスト型 (class/object/interface/enum) のメンバを外側クラスへ
        // 取り込まないこと。ネスト型は独立エントリで出力されるため二重計上になる。
        String src = "package com.x\n"
                + "class Outer {\n"
                + "  val a: Int = 1\n"
                + "  class Inner { val b: String = \"\"; fun foo() {} }\n"
                + "  object Helper { val h: Int = 0 }\n"
                + "}\n";
        List<JavaClassInfo> infos = KotlinLightScanner.scan(src, ErrorListener.silent());
        JavaClassInfo outer = infos.stream()
                .filter(c -> "Outer".equals(c.getSimpleName())).findFirst().orElseThrow(AssertionError::new);
        assertTrue("Outer は自身の a を持つ", hasField(outer, "a"));
        assertFalse("Outer は Inner.b を吸収しない", hasField(outer, "b"));
        assertFalse("Outer は Helper.h を吸収しない", hasField(outer, "h"));
        assertFalse("Outer は Inner.foo を吸収しない", hasMethod(outer, "foo"));
        JavaClassInfo inner = infos.stream()
                .filter(c -> "Inner".equals(c.getSimpleName())).findFirst().orElseThrow(AssertionError::new);
        assertTrue("Inner は自身の b を保持", hasField(inner, "b"));
        assertTrue("Inner は自身の foo を保持", hasMethod(inner, "foo"));
    }

    @Test
    public void functionWithDefaultCallArgIsNotDropped() {
        // 既定引数に呼び出し listOf() を含む関数を脱落させないこと (ネスト () を許容)。
        String src = "package com.x\n"
                + "class Foo {\n"
                + "  fun load(items: List<String> = listOf(), n: Int = 0): Int = n\n"
                + "  val after: Int = 1\n"
                + "}\n";
        JavaClassInfo c = scanOne(src);
        assertNotNull(c);
        assertTrue("既定引数に () を含む fun 'load' が残るはず", hasMethod(c, "load"));
        assertTrue("その後の field 'after' も残るはず", hasField(c, "after"));
    }
}
