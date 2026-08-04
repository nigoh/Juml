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
        String src = "void onSaveInstanceState() {\n"
                + "  Bundle outState = new Bundle();\n"
                + "  outState.putString(\"saved_scroll\", \"x\");\n"
                + "  outState.putInt(\"saved_page\", 3);\n"
                + "}\n"
                + "void parse() {\n"
                + "  JSONObject o = body();\n"
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

    /**
     * 回帰: 受け手が読み取れない/知らない形でも取りこぼさないこと。
     *
     * <p>一時期「prefs らしい受け手」だけを通す許可リストにしていたため、行をまたぐ連鎖・
     * 入れ子引数・{@code this.prefs} が軒並み 0 件になった。設定キーの一覧が目的なので、
     * 拾い過ぎより取りこぼしの方が害が大きい。</p>
     */
    @Test
    public void keysAreNotLostWhenTheReceiverIsHardToRead() {
        String src = "SharedPreferences prefs = ctx.getSharedPreferences(\"app\", 0);\n"
                + "prefs.edit()\n"
                + "        .putString(\"wrapped_write\", t)\n"
                + "        .apply();\n"
                + "String a = PreferenceManager"
                + ".getDefaultSharedPreferences(getApplicationContext())"
                + ".getString(\"nested_arg\", \"dark\");\n"
                + "String b = this.prefs.getString(\"qualified\", \"light\");\n"
                + "int n = prefs.getInt(\"deep_default\", Math.max(1, cfg.min(2)));\n";

        java.util.Set<String> keys = new java.util.HashSet<>();
        for (SharedPreferencesEntry e : scanner.analyzeSource(src, "T.java")) {
            keys.add(e.key);
        }

        assertTrue("行をまたぐ連鎖: " + keys, keys.contains("wrapped_write"));
        assertTrue("入れ子引数の受け手: " + keys, keys.contains("nested_arg"));
        assertTrue("this. 修飾の受け手: " + keys, keys.contains("qualified"));
        assertTrue("2 段入れ子の初期値: " + keys, keys.contains("deep_default"));
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

    /** 与えたソースから拾えたキーの集合。 */
    private java.util.Set<String> keysOf(String src, String path) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (SharedPreferencesEntry e : scanner.analyzeSource(src, path)) {
            keys.add(e.key);
        }
        return keys;
    }

    /**
     * 回帰: Kotlin の {@code Bundle} も「設定ではない受け手」として弾くこと。
     *
     * <p>除外リストは Java の {@code 型 名} 順しか見ていなかった。Kotlin は
     * {@code 名: 型} と逆順で、型推論なら型注釈すら無いため {@code .kt} では 1 件も
     * 集まらず、除外がまるごと効いていなかった ({@code analyzeProject} は
     * {@code includeKotlin = true} で {@code .kt} も読む)。結果、
     * {@code onSaveInstanceState} の一時退避や解析用 {@code Bundle} のキーが、
     * 同じファイルに実在するストア名の下に「保存される設定」として並んでいた。</p>
     */
    @Test
    public void kotlinBundleReceiversAreNotCountedAsSettings() {
        String src = "class ProfileActivity {\n"
                + "    private val prefs = getSharedPreferences(\"profile_prefs\", 0)\n"
                + "    override fun onSaveInstanceState(outState: Bundle) {\n"
                + "        outState.putString(\"draft_bio\", bio)\n"
                + "    }\n"
                + "    override fun onCreate(savedInstanceState: Bundle?) {\n"
                + "        savedInstanceState.putInt(\"tab\", 1)\n"
                + "    }\n"
                + "    private fun track(event: String) {\n"
                + "        val params = Bundle()\n"
                + "        params.putString(\"item_id\", event)\n"
                + "    }\n"
                + "    fun nickname(): String = prefs.getString(\"nickname\", \"\")\n"
                + "}\n";

        java.util.Set<String> keys = keysOf(src, "ProfileActivity.kt");

        assertTrue("本物の設定キーは残ること: " + keys, keys.contains("nickname"));
        assertFalse("引数の Bundle は設定ではない: " + keys, keys.contains("draft_bio"));
        assertFalse("nullable な引数の Bundle も同じ: " + keys, keys.contains("tab"));
        assertFalse("型推論で作った Bundle も同じ: " + keys, keys.contains("item_id"));
    }

    /** 非退行: Kotlin の本物の SharedPreferences 受け手は弾かないこと。 */
    @Test
    public void kotlinPreferenceReceiversAreStillDetected() {
        String src = "class S {\n"
                + "    private val prefs by lazy { getSharedPreferences(\"s\", 0) }\n"
                + "    val a = prefs.getString(\"by_lazy\", \"\")\n"
                + "    val b = PreferenceManager.getDefaultSharedPreferences(this)"
                + ".getString(\"default_sp\", \"\")\n"
                + "    val c = this.prefs.getString(\"qualified\", \"\")\n"
                + "}\n";

        java.util.Set<String> keys = keysOf(src, "S.kt");

        assertTrue("by lazy の受け手: " + keys, keys.contains("by_lazy"));
        assertTrue("getDefaultSharedPreferences: " + keys, keys.contains("default_sp"));
        assertTrue("this. 修飾: " + keys, keys.contains("qualified"));
    }

    /**
     * 回帰: 入れ子や文字列リテラルを含む初期値を、途中で切って表に出さないこと。
     *
     * <p>初期値の切り出しを正規表現でやっていたため、許す入れ子の段数を増やしても
     * その 1 段先で必ず破れた。表に出ていたのは
     * {@code (String.format("%s/%s", host()} のような<b>括弧の閉じない、原文のどこにも
     * 無い文字列</b>で、リテラル中の {@code )} でも同じことが起きていた。</p>
     */
    @Test
    public void nestedAndLiteralParenDefaultsAreNotTruncated() {
        String src = "SharedPreferences prefs = ctx.getSharedPreferences(\"app\", 0);\n"
                + "String u = prefs.getString(\"url\", String.format(\"%s/%s\", host(), path));\n"
                + "int r = prefs.getInt(\"retry\", Math.max(1, cfg.min(2)));\n"
                + "String g = prefs.getString(\"greeting\", \"Hi :) there\");\n";

        java.util.Map<String, String> defaults = new java.util.HashMap<>();
        for (SharedPreferencesEntry e : scanner.analyzeSource(src, "T.java")) {
            defaults.put(e.key, e.defaultValue);
        }

        assertEquals("2 段入れ子の初期値が丸ごと残ること",
                "(String.format(\"%s/%s\", host(), path))", defaults.get("url"));
        assertEquals("入れ子の呼び出しも同じ",
                "(Math.max(1, cfg.min(2)))", defaults.get("retry"));
        assertEquals("リテラル中の ) で切らないこと", "Hi :) there", defaults.get("greeting"));
        // 式として表示する形 (丸括弧で包んだもの) は括弧が釣り合っていること。
        // 文字列リテラルの中身はそのまま出すので対象外 ("Hi :) there" は釣り合わなくてよい)。
        for (java.util.Map.Entry<String, String> e : defaults.entrySet()) {
            if (e.getValue().startsWith("(")) {
                assertEquals("式の初期値は括弧が釣り合うこと: " + e,
                        count(e.getValue(), '('), count(e.getValue(), ')'));
            }
        }
    }

    /**
     * 回帰: キー引数が呼び出しのとき、呼ばれる側の名前をキーとして報告しないこと。
     *
     * <p>初期値を走査へ移したとき、キーの直後に閉じ括弧を要求していた条件も一緒に外れた。
     * その結果 {@code ctx.getString(R.string.pref_theme_key)} の<b>関数名</b>である
     * {@code ctx.getString} が「設定キー」として一覧に並ぶようになった。本当のキーは
     * strings.xml 側にあり静的には解決できないので、出すなら何も出さないのが正しい。</p>
     */
    @Test
    public void aCallUsedAsTheKeyArgumentIsNotReportedAsAKey() {
        String src = "SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);\n"
                + "String t = prefs.getString(ctx.getString(R.string.pref_theme_key), \"light\");\n"
                + "String u = prefs.getString(buildKey(id), \"\");\n";

        java.util.Set<String> keys = keysOf(src, "T.java");

        assertFalse("呼び出しの関数名をキーにしないこと: " + keys, keys.contains("ctx.getString"));
        assertFalse("同上: " + keys, keys.contains("buildKey"));
        assertFalse("リソース参照もキーにしないこと: " + keys,
                keys.contains("R.string.pref_theme_key"));
    }

    /**
     * 回帰: 連結式の初期値を「リテラル」として出さないこと。
     *
     * <p>先頭と末尾が {@code "} かどうかだけで判定していたため、
     * {@code "Hello " + name + "!"} も literal 扱いになり、外側の引用符だけ剥がれた
     * {@code Hello " + name + "!} が初期値として表に出ていた。式なら括弧で包まれるので
     * 読み手が式と分かるが、この形は括弧も付かず<b>本物のリテラルと区別がつかない</b>。</p>
     */
    @Test
    public void aConcatenatedDefaultIsShownAsAnExpressionNotALiteral() {
        String src = "SharedPreferences prefs = ctx.getSharedPreferences(\"app\", 0);\n"
                + "String s = prefs.getString(\"greet\", \"Hello \" + name + \"!\");\n"
                + "String p = prefs.getString(\"plain\", \"Tokyo\");\n";

        java.util.Map<String, String> defaults = new java.util.HashMap<>();
        for (SharedPreferencesEntry e : scanner.analyzeSource(src, "T.java")) {
            defaults.put(e.key, e.defaultValue);
        }

        assertEquals("連結式は式として括弧で包むこと",
                "(\"Hello \" + name + \"!\")", defaults.get("greet"));
        assertEquals("非退行: 本物のリテラルは中身だけ", "Tokyo", defaults.get("plain"));
    }

    /**
     * 回帰: キー引数が定数でない<b>あらゆる形</b>で、先頭の識別子をキーにしないこと。
     *
     * <p>「直後が {@code (} でない」という個別の形の否定だけを入れていたため、その外側が
     * まるごと残っていた。三項・連結・添字・限定呼び出しはどれも先頭の識別子を報告し、
     * しかも {@code useNew} や {@code PREFIX} はキー名として通りそうな見た目なので
     * 読み手には本物と区別が付かない。get と put の両方で確かめる。</p>
     */
    @Test
    public void onlyACompleteConstantOrLiteralIsTakenAsTheKey() {
        String src = "SharedPreferences prefs = ctx.getSharedPreferences(\"app\", 0);\n"
                + "String a = prefs.getString(useNew ? KEY_NEW : KEY_OLD, \"\");\n"
                + "String b = prefs.getString(PREFIX + name, \"\");\n"
                + "String c = prefs.getString(KEYS[0], \"\");\n"
                + "String d = prefs.getString(Foo.<String>bar(), \"\");\n"
                + "editor.putString(useNew ? KEY_NEW : KEY_OLD, v);\n"
                + "editor.putString(buildKey(id), v);\n"
                + "editor.putString(ctx.getString(R.string.k), v);\n"
                + "String ok = prefs.getString(KEY_THEME, \"light\");\n";

        java.util.Set<String> keys = keysOf(src, "T.java");

        for (String bogus : java.util.List.of("useNew", "PREFIX", "KEYS", "Foo.",
                "buildKey", "ctx.getString")) {
            assertFalse("式の先頭の識別子をキーにしないこと: " + bogus + " in " + keys,
                    keys.contains(bogus));
        }
        assertTrue("本物の定数キーは残ること: " + keys, keys.contains("KEY_THEME"));
    }

    /**
     * 回帰: 引数を折り返した定数キーの読み取りを落とさないこと。
     *
     * <p>「初期値が無い = {@code Context.getString(resId)} だから除外」という判定が、
     * 「初期値がこの行に無い」まで巻き込んでいた。put 側は同じ折り返しでも残るので、
     * 読み書きしているキーが「書くだけで読まない」ように見えていた。</p>
     */
    @Test
    public void aWrappedConstantKeyReadIsNotDropped() {
        String src = "SharedPreferences prefs = ctx.getSharedPreferences(\"app\", 0);\n"
                + "String t = prefs.getString(KEY_THEME,\n"
                + "        DEFAULT_THEME);\n"
                + "editor.putString(KEY_THEME,\n"
                + "        theme);\n";

        boolean read = false;
        boolean write = false;
        for (SharedPreferencesEntry e : scanner.analyzeSource(src, "T.java")) {
            if ("KEY_THEME".equals(e.key)) {
                read |= !e.isWrite;
                write |= e.isWrite;
            }
        }

        assertTrue("折り返した読み取りも記録されること", read);
        assertTrue("非退行: 書き込みも記録されること", write);
    }

    /** 非退行: 引数が 1 つだけの Context.getString(resId) は従来どおり除外すること。 */
    @Test
    public void contextGetStringWithNoDefaultIsStillIgnored() {
        String src = "SharedPreferences prefs = ctx.getSharedPreferences(\"app\", 0);\n"
                + "String s = ctx.getString(titleRes);\n";

        assertTrue("初期値の無い定数キーは設定ではない: " + keysOf(src, "T.java"),
                keysOf(src, "T.java").isEmpty());
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
