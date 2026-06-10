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

    /** {@code getSharedPreferences("name", ...)} のストア名抽出。グループ 1: ストア名。 */
    private static final Pattern GET_SP = Pattern.compile(
            "getSharedPreferences\\s*\\(\\s*\"([^\"]+)\"");

    /** {@code getDefaultSharedPreferences(...)} の検出。 */
    private static final Pattern GET_DEFAULT_SP = Pattern.compile(
            "getDefaultSharedPreferences\\s*\\(");

    /** get* 呼び出し。グループ 1: 型 (String/Boolean/Int/Long/Float/StringSet)。
     *  グループ 2: キー。グループ 3: デフォルト値 (存在すれば)。 */
    private static final Pattern GET_VALUE = Pattern.compile(
            "\\.get(String|Boolean|Int|Long|Float|StringSet)\\s*\\(\\s*\"([^\"]+)\"(?:\\s*,\\s*([^)]+?))?\\s*\\)");

    /** put* 呼び出し。グループ 1: 型。グループ 2: キー。 */
    private static final Pattern PUT_VALUE = Pattern.compile(
            "\\.put(String|Boolean|Int|Long|Float|StringSet)\\s*\\(\\s*\"([^\"]+)\"");

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

        String[] lines = src.split("\n", -1);
        List<SharedPreferencesEntry> entries = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            // 読み取り (get*)
            Matcher gm = GET_VALUE.matcher(line);
            while (gm.find()) {
                String type = gm.group(1);
                String key = gm.group(2);
                String defVal = gm.group(3) != null ? gm.group(3).trim() : "";
                // 文字列リテラルのみのデフォルト値を抽出
                if (defVal.startsWith("\"") && defVal.endsWith("\"")) {
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
                String type = pm.group(1);
                String key = pm.group(2);
                entries.add(new SharedPreferencesEntry(
                        key, type, "", resolvedStore, true, filePath, lineNum));
            }
        }
        return entries;
    }
}
