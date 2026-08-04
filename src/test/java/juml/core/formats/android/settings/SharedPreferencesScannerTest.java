// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * SharedPreferencesScanner のユニットテスト。
 */
public class SharedPreferencesScannerTest {

    private final SharedPreferencesScanner scanner = new SharedPreferencesScanner();

    @Test
    public void detectsGetStringRead() {
        String src = "prefs.getString(\"user_name\", \"\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        SharedPreferencesEntry e = entries.get(0);
        assertEquals("user_name", e.key);
        assertEquals("String", e.type);
        assertFalse(e.isWrite);
        assertEquals(1, e.line);
    }

    @Test
    public void defaultValueStartingWithParenDoesNotCrash() {
        // デフォルト値が閉じ括弧で始まる文字列だと、正規表現が開きクォート 1 文字だけを
        // 捕捉し substring(1, 0) で例外になっていた (StringIndexOutOfBounds のガード)。
        String src = "String s = prefs.getString(\"theme\", \")\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        assertEquals("theme", entries.get(0).key);
    }

    @Test
    public void detectsConstantKeyInGetAndPut() {
        // 文字列リテラルでなく定数参照のキー (Android で一般的) も検出する
        String src = "editor.putString(KEY_TOKEN, value);\n"
                + "String t = prefs.getString(KEY_TOKEN, \"\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertTrue("write of constant key", entries.stream()
                .anyMatch(e -> "KEY_TOKEN".equals(e.key) && e.isWrite));
        assertTrue("read of constant key", entries.stream()
                .anyMatch(e -> "KEY_TOKEN".equals(e.key) && !e.isWrite));
    }

    @Test
    public void ignoresContextGetStringResourceLookups() {
        // Context.getString(int resId) は文字列リソース取得であり SharedPreferences ではない。
        // 定数名キー対応 (Loop 1) で誤検出しないことを保証する。
        String src = "mTitle = mContext.getString(title);\n"
                + "x = mContext.getString(R.string.rationale_ask_again);\n"
                + "y = mContext.getString(android.R.string.ok);\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Builder.java");
        assertTrue("Context.getString(resId) must not be treated as prefs read",
                entries.isEmpty());
    }

    @Test
    public void detectsPutBooleanWrite() {
        String src = "editor.putBoolean(\"is_dark_mode\", true);\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        SharedPreferencesEntry e = entries.get(0);
        assertEquals("is_dark_mode", e.key);
        assertEquals("Boolean", e.type);
        assertTrue(e.isWrite);
    }

    @Test
    public void detectsStoreName() {
        String src = "SharedPreferences prefs = getSharedPreferences(\"user_prefs\", MODE_PRIVATE);\n"
                + "prefs.getString(\"name\", \"\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        assertEquals("user_prefs", entries.get(0).storeName);
    }

    @Test
    public void detectsDefaultStoreViaGetDefaultSharedPreferences() {
        String src = "SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);\n"
                + "prefs.getBoolean(\"notifications\", true);\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertFalse(entries.isEmpty());
        assertEquals("(default)", entries.get(0).storeName);
    }

    @Test
    public void detectsDefaultValue() {
        String src = "prefs.getString(\"city\", \"Tokyo\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(1, entries.size());
        assertEquals("Tokyo", entries.get(0).defaultValue);
    }

    @Test
    public void detectsMultipleKeysOnSeparateLines() {
        String src = "prefs.getInt(\"score\", 0);\n"
                + "prefs.putString(\"username\", name);\n"
                + "prefs.getLong(\"timestamp\", 0L);\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");
        assertEquals(3, entries.size());
        assertEquals("score", entries.get(0).key);
        assertEquals("username", entries.get(1).key);
        assertEquals("timestamp", entries.get(2).key);
    }

    @Test
    public void emptySourceReturnsEmptyList() {
        List<SharedPreferencesEntry> entries = scanner.analyzeSource("", "Test.java");
        assertTrue(entries.isEmpty());
    }

    @Test
    public void shortFileNameIsCorrect() {
        String src = "prefs.getString(\"key\", \"\");\n";
        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "/path/to/MyFragment.java");
        assertFalse(entries.isEmpty());
        assertEquals("MyFragment.java", entries.get(0).shortFileName());
    }

    /**
     * 回帰: 初期値がメソッド呼び出しでも括弧の途中で切れないこと。
     *
     * <p>デフォルト値の捕捉が {@code [^)]+?} だったため、
     * {@code getString("theme", ThemeUtil.defaultTheme())} では内側の {@code )} を
     * get 呼び出しの終端と取り違え、初期値が {@code ThemeUtil.defaultTheme(} という
     * 括弧の閉じない、原文のどこにも現れない文字列としてレポートの表に出ていた。</p>
     */
    @Test
    public void nestedCallDefaultValueIsNotTruncated() {
        String src = "prefs.getString(\"theme\", ThemeUtil.defaultTheme());\n"
                + "prefs.getBoolean(\"dark\", isDark(ctx));\n";

        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");

        assertEquals(2, entries.size());
        for (SharedPreferencesEntry e : entries) {
            String d = e.defaultValue == null ? "" : e.defaultValue;
            long open = d.chars().filter(c -> c == '(').count();
            long close = d.chars().filter(c -> c == ')').count();
            assertEquals("初期値の括弧が閉じていること: " + d, open, close);
        }
        // 非リテラルの初期値は (...) で包んで表示する既存の約束に従う。
        assertEquals("(ThemeUtil.defaultTheme())", entries.get(0).defaultValue);
        assertEquals("(isDark(ctx))", entries.get(1).defaultValue);
    }

    /**
     * 回帰: Bundle / JSONObject の put*・get* を設定キーとして数えないこと。
     *
     * <p>パターンが受け手を見ずに {@code .putString(} 等へマッチしていたため、
     * {@code outState.putString("saved_scroll", …)} や {@code json.getString("name")} が
     * 設定キーとして並び、しかも同じファイルに {@code getSharedPreferences} が 1 つでもあると
     * <b>その無関係なストアの中身</b>として表に出ていた。</p>
     */
    @Test
    public void bundleAndJsonAccessesAreNotReportedAsPreferences() {
        String src = "void onSaveInstanceState(Bundle outState) {\n"
                + "  outState.putString(\"saved_scroll\", \"x\");\n"
                + "  outState.putInt(\"saved_page\", 3);\n"
                + "}\n"
                + "void parse(JSONObject o) {\n"
                + "  String n = o.getString(\"name\");\n"
                + "  int v = o.getInt(\"version\");\n"
                + "}\n"
                + "void real(Context ctx) {\n"
                + "  SharedPreferences prefs = ctx.getSharedPreferences(\"my_prefs\", 0);\n"
                + "  prefs.edit().putBoolean(\"opted_in\", true).apply();\n"
                + "}\n";

        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "MainActivity.java");

        java.util.Set<String> keys = new java.util.HashSet<>();
        for (SharedPreferencesEntry e : entries) {
            keys.add(e.key);
        }
        assertTrue("本物の prefs アクセスは残ること: " + keys, keys.contains("opted_in"));
        assertFalse("Bundle のキーを含めないこと: " + keys, keys.contains("saved_scroll"));
        assertFalse("Bundle のキーを含めないこと: " + keys, keys.contains("saved_page"));
        assertFalse("JSON のキーを含めないこと: " + keys, keys.contains("name"));
        assertFalse("JSON のキーを含めないこと: " + keys, keys.contains("version"));
        assertEquals("拾うのは 1 件だけ: " + keys, 1, entries.size());
    }

    /** 非退行: 宣言済み変数・連鎖・慣習的な名前のいずれでも本物は拾えること。 */
    @Test
    public void realPreferenceReceiversAreStillDetected() {
        String src = "SharedPreferences store = ctx.getSharedPreferences(\"s\", 0);\n"
                + "store.getString(\"declared_var\", \"\");\n"
                + "ctx.getSharedPreferences(\"s\", 0).getString(\"inline_chain\", \"\");\n"
                + "prefs.getString(\"conventional_name\", \"\");\n";

        List<SharedPreferencesEntry> entries = scanner.analyzeSource(src, "Test.java");

        java.util.Set<String> keys = new java.util.HashSet<>();
        for (SharedPreferencesEntry e : entries) {
            keys.add(e.key);
        }
        assertTrue(keys.contains("declared_var"));
        assertTrue(keys.contains("inline_chain"));
        assertTrue(keys.contains("conventional_name"));
    }
}
