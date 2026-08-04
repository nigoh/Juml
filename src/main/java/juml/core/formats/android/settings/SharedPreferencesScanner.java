// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

import juml.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java / Kotlin ソースから SharedPreferences の読み書きパターンを検出する。
 *
 * <p>対象パターン:</p>
 * <ul>
 *   <li>{@code getSharedPreferences("name", mode)} — ストア名の記録</li>
 *   <li>{@code getDefaultSharedPreferences(ctx)} — デフォルトストア</li>
 *   <li>{@code prefs.getString("key", "def")} など get* 呼び出し — 読み取り</li>
 *   <li>{@code editor.putString("key", value)} など put* 呼び出し — 書き込み</li>
 * </ul>
 *
 * <p>単純な正規表現スキャンのため、同一ファイル内のストア名を全 get/put エントリに
 * 紐付ける (厳密なデータフロー解析は行わない)。</p>
 */
public final class SharedPreferencesScanner {

    /**
     * {@code put*} / {@code get*} の直前にある受け手の式 (グループ 1)。
     *
     * <p>受け手を見ないと {@code Bundle.putString} や {@code JSONObject.getString} まで
     * 設定キーとして拾ってしまう。実際、同じファイルに {@code getSharedPreferences} が
     * 1 つでもあると、それらが<b>その無関係なストア名の下に</b>並んでいた。</p>
     */
    private static final String RECEIVER =
            "([A-Za-z_][A-Za-z0-9_.]*(?:\\s*\\([^()]*\\))?)";

    /** {@code SharedPreferences p = ...} / {@code Editor e = ...} の変数名 (グループ 1)。 */
    private static final Pattern PREFS_VAR = Pattern.compile(
            "(?:SharedPreferences|SharedPreferences\\s*\\.\\s*Editor|Editor)\\s+"
                    + "([A-Za-z_][A-Za-z0-9_]*)\\s*=");

    /** {@code x = <なにか>.edit()} / {@code x = ...getSharedPreferences(...)} の左辺 (グループ 1)。 */
    private static final Pattern PREFS_ASSIGN = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*[^;]*?"
                    + "(?:\\.\\s*edit\\s*\\(\\s*\\)|getSharedPreferences\\s*\\(|"
                    + "getDefaultSharedPreferences\\s*\\()");

    /** {@code getSharedPreferences("name", ...)} のストア名抽出。グループ 1: ストア名。 */
    private static final Pattern GET_SP = Pattern.compile(
            "getSharedPreferences\\s*\\(\\s*\"([^\"]+)\"");

    /** {@code getDefaultSharedPreferences(...)} の検出。 */
    private static final Pattern GET_DEFAULT_SP = Pattern.compile(
            "getDefaultSharedPreferences\\s*\\(");

    /** get* 呼び出し。グループ 1: 型。グループ 2: 文字列キー。グループ 3: 定数名キー。
     *  グループ 4: デフォルト値 (存在すれば)。キーは文字列リテラルだけでなく定数参照
     *  ({@code getString(KEY_TOKEN, "")}) も許容する (Android では定数キーが一般的)。
     *
     *  <p>デフォルト値は入れ子の括弧を 1 段だけ許す。{@code [^)]+?} だと
     *  {@code getString("theme", ThemeUtil.defaultTheme())} の内側の {@code )} を
     *  get 呼び出しの終端と取り違え、初期値が {@code (ThemeUtil.defaultTheme()} という
     *  括弧の閉じない、原文のどこにも無い文字列として表に出ていた。</p> */
    private static final Pattern GET_VALUE = Pattern.compile(
            RECEIVER + "\\.get(String|Boolean|Int|Long|Float|StringSet)\\s*\\(\\s*"
                    + "(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z0-9_.]*))"
                    + "(?:\\s*,\\s*((?:[^()]|\\([^()]*\\))+?))?\\s*\\)");

    /** put* 呼び出し。グループ 1: 型。グループ 2: 文字列キー。グループ 3: 定数名キー。 */
    private static final Pattern PUT_VALUE = Pattern.compile(
            RECEIVER + "\\.put(String|Boolean|Int|Long|Float|StringSet)\\s*\\(\\s*"
                    + "(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z0-9_.]*))");

    /**
     * プロジェクト全体をスキャンして結果を返す。
     */
    public SettingsAnalysisResult analyzeProject(File projectRoot) throws IOException {
        return analyzeProject(projectRoot, false);
    }

    /** {@code includeTests} でテストソースを含めるかを制御できる版。 */
    public SettingsAnalysisResult analyzeProject(File projectRoot, boolean includeTests)
            throws IOException {
        SettingsAnalysisResult result = new SettingsAnalysisResult();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return result;
        }
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeKotlin = true;
        opts.includeTests = includeTests;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java") && !name.endsWith(".kt")) {
                continue;
            }
            try {
                String src = AndroidProjectScanner.readFile(f);
                for (SharedPreferencesEntry e : analyzeSource(src, f.getPath())) {
                    result.addCodeEntry(e);
                }
            } catch (IOException ignored) {
                // ファイル読み取り失敗は無視して続行
            }
        }
        return result;
    }

    /** このファイルで SharedPreferences / Editor を受けている変数名を集める。 */
    private static java.util.Set<String> collectPreferencesVars(String src) {
        java.util.Set<String> vars = new java.util.LinkedHashSet<>();
        Matcher declared = PREFS_VAR.matcher(src);
        while (declared.find()) {
            vars.add(declared.group(1));
        }
        Matcher assigned = PREFS_ASSIGN.matcher(src);
        while (assigned.find()) {
            vars.add(assigned.group(1));
        }
        return vars;
    }

    /**
     * その {@code put*} / {@code get*} が SharedPreferences 由来の受け手に対する呼び出しか。
     *
     * <p>受け手を見ないと {@code outState.putString(...)} (Bundle) や
     * {@code json.getString(...)} (JSONObject) まで設定キーとして数え、しかも同じファイルに
     * {@code getSharedPreferences} が 1 つでもあれば<b>その無関係なストアの中身</b>として
     * 表に並べてしまう。厳密なデータフロー解析はこのクラスの方針外なので、
     * (1) 宣言・代入から拾った変数名、(2) 式に {@code edit()} や
     * {@code getSharedPreferences} を含む連鎖、(3) 名前が prefs/editor を示す慣習、
     * の 3 段で判定する。</p>
     */
    private static boolean isPreferencesReceiver(String receiver, java.util.Set<String> vars) {
        if (receiver == null || receiver.isEmpty()) {
            return false;
        }
        String r = receiver.trim();
        if (r.contains("edit()") || r.contains("edit ()")
                || r.contains("getSharedPreferences") || r.contains("getDefaultSharedPreferences")) {
            return true;
        }
        String head = r.contains(".") ? r.substring(0, r.indexOf('.')) : r;
        if (vars.contains(head) || vars.contains(r)) {
            return true;
        }
        String lower = head.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("pref") || lower.equals("editor") || lower.equals("ed");
    }

    /**
     * 単一ソースファイルをスキャンして SharedPreferences エントリを返す。
     */
    public List<SharedPreferencesEntry> analyzeSource(String src, String filePath) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        // まずこのファイル内で宣言されているストア名を全て収集する
        List<String> storeNames = new ArrayList<>();
        Matcher spMatcher = GET_SP.matcher(src);
        while (spMatcher.find()) {
            storeNames.add(spMatcher.group(1));
        }
        boolean hasDefaultSp = GET_DEFAULT_SP.matcher(src).find();
        if (hasDefaultSp && !storeNames.contains("(default)")) {
            storeNames.add("(default)");
        }
        String resolvedStore = storeNames.isEmpty() ? "" : storeNames.get(0);
        java.util.Set<String> prefsVars = collectPreferencesVars(src);

        String[] lines = src.split("\n", -1);
        List<SharedPreferencesEntry> entries = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            // 読み取り (get*)
            Matcher gm = GET_VALUE.matcher(line);
            while (gm.find()) {
                if (!isPreferencesReceiver(gm.group(1), prefsVars)) {
                    continue;
                }
                String type = gm.group(2);
                String strKey = gm.group(3);
                String constKey = gm.group(4);
                // 定数名キーは Context.getString(int resId) のリソース取得と紛らわしい。
                // SharedPreferences.getString は必ずデフォルト値 (第2引数) を伴うので、
                // リテラルでない定数キーはデフォルト値が無い / リソース参照なら除外する。
                if (strKey == null && (gm.group(5) == null || isResourceRef(constKey))) {
                    continue;
                }
                String key = strKey != null ? strKey : constKey;
                String defVal = gm.group(5) != null ? gm.group(5).trim() : "";
                // 文字列リテラルのみのデフォルト値を抽出。
                // 単一の `"` (長さ 1) では substring(1, 0) が例外になるため長さでガードする。
                if (defVal.length() >= 2 && defVal.startsWith("\"") && defVal.endsWith("\"")) {
                    defVal = defVal.substring(1, defVal.length() - 1);
                } else if (!defVal.isEmpty()) {
                    defVal = "(" + defVal + ")";
                }
                entries.add(new SharedPreferencesEntry(
                        key, type, defVal, resolvedStore, false, filePath, lineNum));
            }

            // 書き込み (put*)
            Matcher pm = PUT_VALUE.matcher(line);
            while (pm.find()) {
                if (!isPreferencesReceiver(pm.group(1), prefsVars)) {
                    continue;
                }
                String type = pm.group(2);
                String strKey = pm.group(3);
                String constKey = pm.group(4);
                // 定数名キーがリソース参照ならプリファレンス書き込みではないので除外
                if (strKey == null && isResourceRef(constKey)) {
                    continue;
                }
                String key = strKey != null ? strKey : constKey;
                entries.add(new SharedPreferencesEntry(
                        key, type, "", resolvedStore, true, filePath, lineNum));
            }
        }
        return entries;
    }

    /** {@code R.string.x} / {@code android.R.x} 等の Android リソース参照かを判定する。 */
    private static boolean isResourceRef(String name) {
        if (name == null) {
            return false;
        }
        return name.startsWith("R.") || name.contains(".R.");
    }
}
