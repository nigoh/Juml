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
            "([A-Za-z_][A-Za-z0-9_.]*(?:\\s*\\([^()]*\\))?)?";

    /** <b>明らかに設定ではない</b>型。ここを増やすと取りこぼしが増えるので安易に足さない。 */
    private static final String NON_PREFS_TYPES =
            "(?:Bundle|JSONObject|JSONArray|ContentValues|Intent|Cursor|Properties|"
                    + "Map|HashMap|ArrayMap)";

    /**
     * 上記の型で宣言された変数名を拾うパターン (どれも<b>グループ 1 が変数名</b>)。
     *
     * <p>以前は Java の {@code 型 名} 順だけを見ていた。Kotlin は {@code 名: 型} と
     * 逆順で書き、型推論なら型注釈すら無いため、{@code .kt} では 1 件も集まらなかった
     * ({@code analyzeProject} は {@code includeKotlin = true} で {@code .kt} も読む)。
     * その結果、{@code onSaveInstanceState(outState: Bundle)} の {@code putString} や
     * 解析用の {@code val params = Bundle()} が、同じファイルに実在するストア名の下に
     * 「保存される設定キー」として並んでいた — 除外リストを入れる前と同じ症状が、
     * いまの Android で主流の言語側だけ残っていた。</p>
     */
    private static final List<Pattern> NON_PREFS_VAR_PATTERNS = List.of(
            // Java: `Bundle b` / `Map<String, String> m`
            Pattern.compile("\\b" + NON_PREFS_TYPES
                    + "\\s*(?:<[^>]*>)?\\s+([A-Za-z_][A-Za-z0-9_]*)\\b"),
            // Kotlin の型注釈: `outState: Bundle` / `saved: Bundle?` / `m: HashMap<String, Int>`
            // 末尾の (?![\w.]) は `x ? a : Bundle.EMPTY` の `a` や `BundleCompat` を弾く。
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*" + NON_PREFS_TYPES + "(?![\\w.])"),
            // Kotlin の型推論: `val params = Bundle()` / `var j = JSONObject()`
            Pattern.compile("\\b(?:val|var)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*"
                    + NON_PREFS_TYPES + "\\s*(?:<[^>]*>)?\\s*\\("));

    /** {@code getSharedPreferences("name", ...)} のストア名抽出。グループ 1: ストア名。 */
    private static final Pattern GET_SP = Pattern.compile(
            "getSharedPreferences\\s*\\(\\s*\"([^\"]+)\"");

    /** {@code getDefaultSharedPreferences(...)} の検出。 */
    private static final Pattern GET_DEFAULT_SP = Pattern.compile(
            "getDefaultSharedPreferences\\s*\\(");

    /**
     * キー引数: 文字列リテラル (グループ a) か定数名 (グループ b)。
     *
     * <p>先読みで<b>引数の区切りに接していること</b>を要求する。これが言明すべき不変条件で、
     * 「直後が {@code (} でないこと」のような個別の形の否定ではない。否定を数え上げると
     * その外側が必ず残る: 実測で {@code useNew ? KEY_NEW : KEY_OLD} は {@code useNew}、
     * {@code PREFIX + name} は {@code PREFIX}、{@code KEYS[0]} は {@code KEYS}、
     * {@code Foo.<String>bar()} は {@code Foo.} (識別子ですらない) をキーとして報告して
     * いた。どれも本物のキーは報告されないうえ、キー名として通りそうな見た目なので
     * 読み手には区別が付かない。</p>
     *
     * <p>get と put で同じ定数を使う。put 側にだけ入れ忘れて同じ欠陥が残っていた。</p>
     */
    private static final String KEY_ARG =
            "(?:\"([^\"]+)\"|([A-Za-z_][A-Za-z0-9_.]*)(?=\\s*[,)]))";

    /**
     * get* 呼び出しの<b>キーまで</b>。グループ 1: 受け手。グループ 2: 型。
     * グループ 3: 文字列キー。グループ 4: 定数名キー。キーは文字列リテラルだけでなく
     * 定数参照 ({@code getString(KEY_TOKEN, "")}) も許容する (Android では定数キーが一般的)。
     *
     * <p>デフォルト値は<b>正規表現で切らない</b>。括弧の対応は正規表現で数えられないため、
     * 何段許すかを増やしてもその 1 段先で必ず破れる: 1 段許した版は
     * {@code getString("url", String.format("%s/%s", host(), path))} を
     * {@code (String.format("%s/%s", host()} と、閉じない・原文のどこにも無い文字列として
     * 表に出していた。文字列リテラル中の {@code )} ({@code "Hi :) there"}) でも同じ。
     * 代わりに {@link #defaultArgumentSpan} が括弧の深さと文字列/文字リテラルを見ながら
     * 走査する。</p>
     */
    private static final Pattern GET_VALUE = Pattern.compile(
            RECEIVER + "\\.get(String|Boolean|Int|Long|Float|StringSet)\\s*\\(\\s*"
                    + KEY_ARG);

    /**
     * キー直後から get 呼び出しの閉じ括弧までを走査し、
     * {@code {デフォルト値の開始位置, 閉じ括弧の位置}} を返す。
     *
     * <p>デフォルト値が無ければ開始位置は -1。行内で括弧が閉じていなければ {@code null}
     * (次行へ続く連鎖などは判定材料にしない)。括弧の深さは {@code ( [ {} を数え、
     * 文字列リテラル・文字リテラルの中身はエスケープを見ながら読み飛ばす。</p>
     */
    private static int[] defaultArgumentSpan(String line, int afterKey) {
        int depth = 0;
        int valueStart = -1;
        boolean inString = false;
        boolean inChar = false;
        for (int i = afterKey; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inString || inChar) {
                if (c == '\\') {
                    i++;
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth--;
            } else if (c == ')') {
                if (depth == 0) {
                    return new int[]{valueStart, i};
                }
                depth--;
            } else if (c == ',' && depth == 0 && valueStart < 0) {
                valueStart = i + 1;
            }
        }
        return null;
    }

    /**
     * {@code s} が<b>ちょうど 1 個の文字列リテラル</b>ならその中身を、違えば {@code null}。
     *
     * <p>「先頭と末尾が {@code "}」で判定していたため、連結式
     * {@code "Hello " + name + "!"} もリテラル扱いになり、外側の引用符だけ剥がれた
     * {@code Hello " + name + "!} が「初期値」として表に出ていた。式なら括弧で包まれる
     * ので読み手が式と分かるが、この形は括弧も付かないので<b>本物のリテラルと区別が
     * つかない</b>。</p>
     */
    private static String singleStringLiteral(String s) {
        if (s.length() < 2 || s.charAt(0) != '"') {
            return null;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i == s.length() - 1 ? s.substring(1, i) : null;
            }
        }
        return null;
    }

    /** put* 呼び出し。グループ 1: 型。グループ 2: 文字列キー。グループ 3: 定数名キー。 */
    private static final Pattern PUT_VALUE = Pattern.compile(
            RECEIVER + "\\.put(String|Boolean|Int|Long|Float|StringSet)\\s*\\(\\s*"
                    + KEY_ARG);

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

    /** このファイルで「設定ではない」型として宣言されている変数名を集める (Java / Kotlin 両方)。 */
    private static java.util.Set<String> collectNonPreferencesVars(String src) {
        java.util.Set<String> vars = new java.util.LinkedHashSet<>();
        for (Pattern p : NON_PREFS_VAR_PATTERNS) {
            Matcher m = p.matcher(src);
            while (m.find()) {
                vars.add(m.group(1));
            }
        }
        return vars;
    }

    /**
     * その {@code put*} / {@code get*} を設定アクセスとして数えるか。
     *
     * <p><b>除外リスト</b>で判定する。以前は逆に「prefs らしい受け手」だけを通す許可リストに
     * していたが、それは知らない書き方をすべて<b>取りこぼす</b>: 行をまたぐ連鎖
     * ({@code prefs.edit()} の次行に {@code .putString(...)})、入れ子引数
     * ({@code getDefaultSharedPreferences(getApplicationContext()).getString(...)})、
     * {@code this.prefs} のような修飾つき受け手が軒並み 0 件になった。設定キーの一覧を
     * 出すのが目的なので、<b>拾い過ぎより取りこぼしの方が害が大きい</b>。
     * 明らかに設定ではない型 (Bundle / JSONObject など) の変数だけを弾き、
     * 判定できない受け手は通す。</p>
     */
    private static boolean isPreferencesReceiver(String receiver,
                                                 java.util.Set<String> nonPrefsVars) {
        if (receiver == null || receiver.isEmpty()) {
            return true; // 連鎖の継続行など。受け手が読めないだけで除外はしない。
        }
        String r = receiver.trim();
        String head = r.contains(".") ? r.substring(0, r.indexOf('.')) : r;
        return !nonPrefsVars.contains(head) && !nonPrefsVars.contains(r);
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
        java.util.Set<String> nonPrefsVars = collectNonPreferencesVars(src);

        String[] lines = src.split("\n", -1);
        List<SharedPreferencesEntry> entries = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            // 読み取り (get*)
            Matcher gm = GET_VALUE.matcher(line);
            int from = 0;
            while (from <= line.length() && gm.find(from)) {
                int[] span = defaultArgumentSpan(line, gm.end());
                // 次はキーの直後から探す。閉じ括弧の先まで飛ばしていたため、初期値の中に
                // 入れ子になった get が丸ごと見えなくなっていた
                // (`prefs.getString(KEY_NEW, prefs.getString(KEY_OLD, ""))` で KEY_OLD が
                // 落ちる)。put 側は素の find() なので同じ入れ子を両方報告する — 同じ
                // 入れ子が読みと書きで違う結果になるのは、どちらかが必ず間違っている。
                // 二重計上の心配は無い: find は<b>キーの後ろ</b>から再開するので、外側の
                // 呼び出しが自分自身に再びマッチすることはない。
                from = gm.end();
                if (!isPreferencesReceiver(gm.group(1), nonPrefsVars)) {
                    continue;
                }
                String type = gm.group(2);
                String strKey = gm.group(3);
                String constKey = gm.group(4);
                boolean hasDefault = span != null && span[0] >= 0;
                // 定数名キーは Context.getString(int resId) のリソース取得と紛らわしい。
                // SharedPreferences.getString は必ずデフォルト値 (第2引数) を伴うので、
                // リテラルでない定数キーはデフォルト値が無い / リソース参照なら除外する。
                //
                // ただし「無い」と言えるのは行内で括弧が閉じているときだけ。span == null は
                // <b>この行では分からない</b>という意味で、引数を折り返した
                // `prefs.getString(KEY_THEME,\n        DEFAULT_THEME)` がそれに当たる。
                // これを「無い」と同一視していたため、読み書き両方している設定キーが
                // 「書くだけで読まない」ように見えていた (put 側は同じ折り返しでも残る)。
                boolean knownNoDefault = span != null && span[0] < 0;
                if (strKey == null && (knownNoDefault || isResourceRef(constKey))) {
                    continue;
                }
                String key = strKey != null ? strKey : constKey;
                String defVal = hasDefault ? line.substring(span[0], span[1]).trim() : "";
                // 初期値が「文字列リテラル 1 個ちょうど」ならその中身、そうでなければ
                // 式として括弧で包む。式を括弧で包むのは、読み手が「リテラルの初期値」と
                // 「式の初期値」を見分けられるようにするため。
                String literal = singleStringLiteral(defVal);
                if (literal != null) {
                    defVal = literal;
                } else if (!defVal.isEmpty()) {
                    defVal = "(" + defVal + ")";
                }
                entries.add(new SharedPreferencesEntry(
                        key, type, defVal, resolvedStore, false, filePath, lineNum));
            }

            // 書き込み (put*)
            Matcher pm = PUT_VALUE.matcher(line);
            while (pm.find()) {
                if (!isPreferencesReceiver(pm.group(1), nonPrefsVars)) {
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
