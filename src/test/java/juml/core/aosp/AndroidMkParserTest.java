// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link AndroidMkParser} のユニットテスト。
 */
public class AndroidMkParserTest {

    private final AndroidMkParser parser = new AndroidMkParser();

    @Test
    public void testEmptyInputReturnsEmpty() {
        assertTrue(parser.parseSource("", "Android.mk").isEmpty());
        assertTrue(parser.parseSource(null, "Android.mk").isEmpty());
    }

    @Test
    public void testSingleSharedLibraryModule() {
        String src = ""
                + "LOCAL_PATH := $(call my-dir)\n"
                + "\n"
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfoo\n"
                + "LOCAL_SRC_FILES := a.cpp b.cpp\n"
                + "LOCAL_SHARED_LIBRARIES := libc liblog\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        List<AndroidBpModule> mods = parser.parseSource(src, "Android.mk");
        assertEquals(1, mods.size());
        AndroidBpModule m = mods.get(0);
        assertEquals("cc_library_shared", m.getType());
        assertEquals("libfoo", m.getName());
        assertEquals(2, m.getSrcs().size());
        assertEquals("a.cpp", m.getSrcs().get(0));
        assertEquals("b.cpp", m.getSrcs().get(1));
        assertEquals(2, m.getDeps().size());
        assertEquals("libc", m.getDeps().get(0));
        assertEquals("liblog", m.getDeps().get(1));
    }

    @Test
    public void testUnexpandedVariableDepsExcluded() {
        // 未展開の Make 変数参照 ($(my_libs)) は依存名ではないので deps から除外する。
        // 実モジュール名 (libfoo) のみ残ること。
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libbar\n"
                + "LOCAL_SHARED_LIBRARIES := $(my_libs) libfoo\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        List<AndroidBpModule> mods = parser.parseSource(src, "Android.mk");
        AndroidBpModule m = mods.get(0);
        assertEquals(1, m.getDeps().size());
        assertEquals("libfoo", m.getDeps().get(0));
    }

    @Test
    public void testBuildPackageUsesLocalPackageName() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_PACKAGE_NAME := MyApp\n"
                + "LOCAL_SRC_FILES := Main.java\n"
                + "include $(BUILD_PACKAGE)\n";
        List<AndroidBpModule> mods = parser.parseSource(src, "Android.mk");
        assertEquals(1, mods.size());
        AndroidBpModule m = mods.get(0);
        assertEquals("android_app", m.getType());
        assertEquals("MyApp", m.getName());
        // BUILD_PACKAGE は category() で "android"
        assertEquals("android", m.getCategory());
    }

    @Test
    public void testJavaLibraryModule() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := my-java\n"
                + "LOCAL_SRC_FILES := A.java B.java\n"
                + "LOCAL_JAVA_LIBRARIES := framework\n"
                + "LOCAL_STATIC_JAVA_LIBRARIES := guava\n"
                + "include $(BUILD_JAVA_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals("java_library", m.getType());
        assertEquals("my-java", m.getName());
        assertEquals(2, m.getSrcs().size());
        assertEquals(2, m.getDeps().size());
        assertTrue(m.getDeps().contains("framework"));
        assertTrue(m.getDeps().contains("guava"));
    }

    @Test
    public void testStaticLibraryAndExecutableTypes() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libstatic\n"
                + "include $(BUILD_STATIC_LIBRARY)\n"
                + "\n"
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := mytool\n"
                + "include $(BUILD_EXECUTABLE)\n";
        List<AndroidBpModule> mods = parser.parseSource(src, "Android.mk");
        assertEquals(2, mods.size());
        assertEquals("cc_library_static", mods.get(0).getType());
        assertEquals("cc_binary", mods.get(1).getType());
    }

    @Test
    public void testMultipleModulesInOneFile() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfoo\n"
                + "include $(BUILD_SHARED_LIBRARY)\n"
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libbar\n"
                + "include $(BUILD_SHARED_LIBRARY)\n"
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := myapp\n"
                + "include $(BUILD_PACKAGE)\n";
        List<AndroidBpModule> mods = parser.parseSource(src, "Android.mk");
        assertEquals(3, mods.size());
        assertEquals("libfoo", mods.get(0).getName());
        assertEquals("libbar", mods.get(1).getName());
        assertEquals("myapp", mods.get(2).getName());
    }

    @Test
    public void testLineContinuationJoined() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfoo\n"
                + "LOCAL_SRC_FILES := \\\n"
                + "    a.cpp \\\n"
                + "    b.cpp \\\n"
                + "    c.cpp\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals(3, m.getSrcs().size());
        assertEquals("a.cpp", m.getSrcs().get(0));
        assertEquals("b.cpp", m.getSrcs().get(1));
        assertEquals("c.cpp", m.getSrcs().get(2));
    }

    @Test
    public void testAppendOperatorMergesValues() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfoo\n"
                + "LOCAL_SHARED_LIBRARIES := libc\n"
                + "LOCAL_SHARED_LIBRARIES += liblog\n"
                + "LOCAL_SHARED_LIBRARIES += libutils\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals(3, m.getDeps().size());
        assertEquals("libc", m.getDeps().get(0));
        assertEquals("liblog", m.getDeps().get(1));
        assertEquals("libutils", m.getDeps().get(2));
    }

    @Test
    public void testCommentsSkipped() {
        String src = ""
                + "# Top-level comment\n"
                + "include $(CLEAR_VARS)\n"
                + "  # indented comment\n"
                + "LOCAL_MODULE := libfoo\n"
                + "LOCAL_SHARED_LIBRARIES := libc   # inline comment after deps\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals("libfoo", m.getName());
        assertEquals(1, m.getDeps().size());
        assertEquals("libc", m.getDeps().get(0));
    }

    @Test
    public void testIncompleteModuleIgnored() {
        // CLEAR_VARS で開始しても BUILD_XXX が無ければモジュールは出力されない
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfoo\n";
        assertTrue(parser.parseSource(src, "Android.mk").isEmpty());
    }

    @Test
    public void testClearVarsResetsState() {
        // CLEAR_VARS の後、前回の LOCAL_* が引きずられないことを確認
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfirst\n"
                + "LOCAL_SHARED_LIBRARIES := libc\n"
                + "include $(BUILD_SHARED_LIBRARY)\n"
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libsecond\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        List<AndroidBpModule> mods = parser.parseSource(src, "Android.mk");
        assertEquals(2, mods.size());
        AndroidBpModule second = mods.get(1);
        assertEquals("libsecond", second.getName());
        // 2 つ目には libc が引き継がれていない
        assertTrue("deps should be empty on second module: " + second.getDeps(),
                second.getDeps().isEmpty());
    }

    @Test
    public void testUnknownBuildIncludeFallsBackToLowercase() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libcustom\n"
                + "include $(BUILD_RAW_BLOB)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        // 未知の BUILD_XXX は名前を lower-case 化
        assertEquals("build_raw_blob", m.getType());
        assertEquals("libcustom", m.getName());
    }

    @Test
    public void testNonBuildIncludeIgnored() {
        // include $(LOCAL_PATH)/sub.mk のようなものは BUILD_XXX ではないので無視
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfoo\n"
                + "include $(LOCAL_PATH)/extra.mk\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals("libfoo", m.getName());
        assertEquals("cc_library_shared", m.getType());
    }

    @Test
    public void testMixedDepKinds() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libmix\n"
                + "LOCAL_STATIC_LIBRARIES := libstatic\n"
                + "LOCAL_SHARED_LIBRARIES := libshared\n"
                + "LOCAL_HEADER_LIBRARIES := libhdr\n"
                + "LOCAL_REQUIRED_MODULES := tool1\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals(4, m.getDeps().size());
        assertTrue(m.getDeps().contains("libstatic"));
        assertTrue(m.getDeps().contains("libshared"));
        assertTrue(m.getDeps().contains("libhdr"));
        assertTrue(m.getDeps().contains("tool1"));
    }

    @Test
    public void testJavaCategoryClassification() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libjava\n"
                + "include $(BUILD_JAVA_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals("java", m.getCategory());
    }

    @Test
    public void testCcCategoryClassification() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libcc\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals("cc", m.getCategory());
    }

    @Test
    public void testPrebuiltType() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := preb\n"
                + "LOCAL_SRC_FILES := preb.so\n"
                + "include $(BUILD_PREBUILT)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals("prebuilt", m.getType());
    }

    @Test
    public void testHostExecutableType() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := hosttool\n"
                + "include $(BUILD_HOST_EXECUTABLE)\n";
        AndroidBpModule m = parser.parseSource(src, "Android.mk").get(0);
        assertEquals("cc_binary_host", m.getType());
    }

    @Test
    public void testFilePathRecorded() {
        String src = ""
                + "include $(CLEAR_VARS)\n"
                + "LOCAL_MODULE := libfoo\n"
                + "include $(BUILD_SHARED_LIBRARY)\n";
        AndroidBpModule m = parser.parseSource(src, "device/v/p/Android.mk").get(0);
        assertEquals("device/v/p/Android.mk", m.getFile());
        assertTrue("lineHint should be positive", m.getLineHint() > 0);
    }

    @Test
    public void testJoinContinuationsHelper() {
        java.util.List<int[]> ranges = new java.util.ArrayList<>();
        java.util.List<String> out = AndroidMkParser.joinContinuations(
                "A := 1 \\\n   2 \\\n   3\nB := 4\n", ranges);
        assertEquals(3, out.size());
        // 1 行目: A := 1 と 2 と 3 が連結。継続行の前後空白は連結時にそのまま残るため、
        // 後段の splitValues が空白で分割する前提で「正規化はしない」。
        String joined = out.get(0).replaceAll("\\s+", " ");
        assertEquals("A := 1 2 3", joined);
        assertEquals("B := 4", out.get(1));
        // 末尾 \n 後の空行
        assertEquals("", out.get(2));
        assertEquals(1, ranges.get(0)[0]);
        assertEquals(3, ranges.get(0)[1]);
        assertEquals(4, ranges.get(1)[0]);
        assertEquals(4, ranges.get(1)[1]);
    }

    @Test
    public void testParseAssignmentHelper() {
        AndroidMkParser.Assignment a;
        a = AndroidMkParser.parseAssignment("LOCAL_MODULE := foo");
        assertNotNull(a);
        assertEquals("LOCAL_MODULE", a.key);
        assertEquals("foo", a.value);
        assertFalse(a.append);

        a = AndroidMkParser.parseAssignment("LOCAL_SHARED_LIBRARIES += libc");
        assertNotNull(a);
        assertEquals("LOCAL_SHARED_LIBRARIES", a.key);
        assertEquals("libc", a.value);
        assertTrue(a.append);

        a = AndroidMkParser.parseAssignment("LOCAL_X = y");
        assertNotNull(a);
        assertEquals("y", a.value);

        // 代入演算子が無い行
        assertEquals(null, AndroidMkParser.parseAssignment("include $(BUILD_FOO)"));
        // 識別子から始まらない行
        assertEquals(null, AndroidMkParser.parseAssignment("   := foo"));
    }

    @Test
    public void testSplitValuesPreservesMakeVariables() {
        List<String> v = AndroidMkParser.splitValues("$(LOCAL_PATH)/a.cpp b.cpp");
        assertEquals(2, v.size());
        assertEquals("$(LOCAL_PATH)/a.cpp", v.get(0));
        assertEquals("b.cpp", v.get(1));

        // 空白を含む変数参照は 1 トークン扱い (paren depth 計算)
        v = AndroidMkParser.splitValues("$(call foo,bar baz) x");
        assertEquals(2, v.size());
        assertEquals("$(callfoo,barbaz)", v.get(0).replace(" ", ""));
        assertEquals("x", v.get(1));
    }
}
