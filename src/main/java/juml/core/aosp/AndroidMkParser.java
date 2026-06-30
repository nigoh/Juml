// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import juml.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AOSP の {@code Android.mk} (legacy GNU Make 形式) ファイルからモジュール宣言を抽出する。
 *
 * <p>Soong ({@code Android.bp}) への移行が進んだ現在も、AOSP 内には大量の
 * {@code Android.mk} が残っている。本パーサは {@link AndroidBpParser} と同じ
 * 出力モデル ({@link AndroidBpModule}) を採用し、Soong 解析結果と統一されたモジュール
 * グラフを構築できるようにする。</p>
 *
 * <p>パース戦略は完全な GNU Make 評価ではなく、AOSP のお決まり (Boilerplate)
 * パターンに特化した軽量実装:</p>
 * <ol>
 *   <li>行ベース、{@code \} による継続行を 1 行として連結</li>
 *   <li>{@code #} で始まる行は (前置空白を許容して) コメント扱いでスキップ</li>
 *   <li>{@code include $(CLEAR_VARS)} で {@code LOCAL_*} 蓄積をリセット</li>
 *   <li>{@code LOCAL_*  := / = / += 値} を捕捉</li>
 *   <li>{@code include $(BUILD_XXX)} で現在モジュールを完成させ、
 *       {@code BUILD_XXX} を Soong 風 type ({@code cc_library_shared} 等) に
 *       マップする</li>
 * </ol>
 */
public final class AndroidMkParser {

    /** 依存とみなす {@code LOCAL_*_LIBRARIES} / {@code LOCAL_REQUIRED_MODULES} のキー。 */
    private static final List<String> DEP_KEYS = Collections.unmodifiableList(
            Arrays.asList(
                    "LOCAL_STATIC_LIBRARIES",
                    "LOCAL_SHARED_LIBRARIES",
                    "LOCAL_WHOLE_STATIC_LIBRARIES",
                    "LOCAL_JAVA_LIBRARIES",
                    "LOCAL_STATIC_JAVA_LIBRARIES",
                    "LOCAL_HEADER_LIBRARIES",
                    "LOCAL_REQUIRED_MODULES",
                    "LOCAL_RUNTIME_LIBRARIES"));

    /** {@code BUILD_XXX} include の Soong 等価 type へのマップ。 */
    private static final Map<String, String> BUILD_TO_TYPE;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("BUILD_SHARED_LIBRARY", "cc_library_shared");
        m.put("BUILD_STATIC_LIBRARY", "cc_library_static");
        m.put("BUILD_EXECUTABLE", "cc_binary");
        m.put("BUILD_HOST_SHARED_LIBRARY", "cc_library_host_shared");
        m.put("BUILD_HOST_STATIC_LIBRARY", "cc_library_host_static");
        m.put("BUILD_HOST_EXECUTABLE", "cc_binary_host");
        m.put("BUILD_HEADER_LIBRARY", "cc_library_headers");
        m.put("BUILD_JAVA_LIBRARY", "java_library");
        m.put("BUILD_STATIC_JAVA_LIBRARY", "java_library_static");
        m.put("BUILD_HOST_JAVA_LIBRARY", "java_library_host");
        m.put("BUILD_PACKAGE", "android_app");
        m.put("BUILD_PREBUILT", "prebuilt");
        m.put("BUILD_MULTI_PREBUILT", "prebuilt");
        m.put("BUILD_HOST_PREBUILT", "prebuilt_host");
        BUILD_TO_TYPE = Collections.unmodifiableMap(m);
    }

    /** プロジェクト全体を走査し、見つかった全 {@code Android.mk} のモジュールを返す。 */
    public List<AndroidBpModule> analyzeProject(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> mkFiles = new ArrayList<>();
        collectMkFiles(projectRoot, mkFiles);
        List<AndroidBpModule> all = new ArrayList<>();
        for (File mk : mkFiles) {
            try {
                String src = AndroidProjectScanner.readFile(mk);
                all.addAll(parseSource(src, mk.getPath()));
            } catch (IOException ex) {
                // 読み取り失敗はスキップ
            }
        }
        return all;
    }

    /** 1 ファイルの {@code Android.mk} ソースをパースしてモジュールリストを返す。 */
    public List<AndroidBpModule> parseSource(String src, String filePath) {
        List<AndroidBpModule> out = new ArrayList<>();
        if (src == null || src.isEmpty()) {
            return out;
        }
        // 行継続 (\) を結合しつつ元行番号も保持する
        List<int[]> originalLineRanges = new ArrayList<>();
        List<String> logicalLines = joinContinuations(src, originalLineRanges);

        Map<String, List<String>> currentVars = new LinkedHashMap<>();
        // 現モジュールが何行目で開始したか (CLEAR_VARS の直後の行)。0 = 未開始。
        int moduleStartLine = 0;

        for (int li = 0; li < logicalLines.size(); li++) {
            String raw = logicalLines.get(li);
            String line = stripInlineCommentAndTrim(raw);
            if (line.isEmpty()) {
                continue;
            }
            int physLine = originalLineRanges.get(li)[0];

            // include $(CLEAR_VARS) → 状態リセット (新モジュール開始)
            if (matchesInclude(line, "CLEAR_VARS")) {
                currentVars = new LinkedHashMap<>();
                moduleStartLine = physLine;
                continue;
            }
            // include $(BUILD_XXX) → モジュール確定
            String build = matchedBuildInclude(line);
            if (build != null) {
                AndroidBpModule mod = finalizeModule(
                        build, currentVars,
                        filePath, moduleStartLine > 0 ? moduleStartLine : physLine);
                if (mod != null) {
                    out.add(mod);
                }
                currentVars = new LinkedHashMap<>();
                moduleStartLine = 0;
                continue;
            }
            // その他の include 行はスキップ (ヘッダや他 .mk の読み込み)
            if (line.startsWith("include ")
                    || line.startsWith("-include ")
                    || line.startsWith("sinclude ")) {
                continue;
            }
            // LOCAL_xxx := value / = value / += value
            Assignment a = parseAssignment(line);
            if (a == null) {
                continue;
            }
            if (a.append) {
                currentVars.computeIfAbsent(a.key, k -> new ArrayList<>())
                        .addAll(splitValues(a.value));
            } else {
                List<String> vs = splitValues(a.value);
                currentVars.put(a.key, new ArrayList<>(vs));
            }
        }
        return out;
    }

    private AndroidBpModule finalizeModule(String buildIdent,
                                            Map<String, List<String>> vars,
                                            String filePath, int lineHint) {
        String type = BUILD_TO_TYPE.getOrDefault(buildIdent,
                buildIdent.toLowerCase());
        // LOCAL_MODULE 優先、無ければ LOCAL_PACKAGE_NAME を採用 (BUILD_PACKAGE 用)
        String name = firstValue(vars, "LOCAL_MODULE");
        if (name == null || name.isEmpty()) {
            name = firstValue(vars, "LOCAL_PACKAGE_NAME");
        }
        if (name == null) {
            name = "";
        }
        AndroidBpModule mod = new AndroidBpModule(type, name, filePath, lineHint);
        List<String> srcs = vars.get("LOCAL_SRC_FILES");
        if (srcs != null) {
            mod.getSrcs().addAll(srcs);
        }
        for (String depKey : DEP_KEYS) {
            List<String> v = vars.get(depKey);
            if (v != null) {
                for (String dep : v) {
                    // 未展開の Make 変数参照 ($(my_libs) / ${x} / $x) は依存名ではないので除外。
                    // SRC_FILES は原文保持だが、依存名は実モジュール名のみを採用する。
                    if (!isMakeVarRef(dep)) {
                        mod.getDeps().add(dep);
                    }
                }
            }
        }
        // name が空でも type だけは有効なので、name 必須にせず返す
        // (呼び出し側で getName().isEmpty() を見て除外できるように残す)
        return mod;
    }

    /** トークンが未展開の Make 変数参照 ({@code $(VAR)} / {@code ${VAR}} / {@code $V}) か。 */
    static boolean isMakeVarRef(String token) {
        return token != null && token.startsWith("$");
    }

    private static String firstValue(Map<String, List<String>> vars, String key) {
        List<String> v = vars.get(key);
        if (v == null || v.isEmpty()) {
            return null;
        }
        return v.get(0);
    }

    /** {@code "LOCAL_XXX (op) value"} を分解。マッチしなければ null。 */
    static Assignment parseAssignment(String line) {
        // op の候補: := += = (?= は make の immediate eval; 同じ扱い)
        // 左辺は識別子 + 数字 + アンダースコア
        int n = line.length();
        int i = 0;
        // 先頭スペース除去後の前提だが念のため
        while (i < n && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        int keyStart = i;
        while (i < n) {
            char c = line.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                i++;
            } else {
                break;
            }
        }
        if (i == keyStart) {
            return null;
        }
        String key = line.substring(keyStart, i);
        // 空白を飛ばす
        while (i < n && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        if (i >= n) {
            return null;
        }
        boolean append = false;
        // ':=' / '+=' / '?=' / '='
        if (line.startsWith(":=", i)) {
            i += 2;
        } else if (line.startsWith("+=", i)) {
            i += 2;
            append = true;
        } else if (line.startsWith("?=", i)) {
            i += 2;
        } else if (line.charAt(i) == '=') {
            i += 1;
        } else {
            return null;
        }
        // 値部分
        String value = line.substring(i).trim();
        return new Assignment(key, value, append);
    }

    /** {@code include $(NAME)} もしくは {@code include $(value:%=...)} の形を検出。 */
    private static boolean matchesInclude(String line, String varName) {
        // 例: "include $(CLEAR_VARS)" / "  include   $(CLEAR_VARS)"
        String trimmed = line.trim();
        if (!trimmed.startsWith("include")) {
            return false;
        }
        String rest = trimmed.substring("include".length()).trim();
        return rest.equals("$(" + varName + ")");
    }

    /**
     * {@code include $(BUILD_XXX)} の {@code BUILD_XXX} 名を返す。
     * Build 系の include で無ければ null。{@code BUILD_PHONY_PACKAGE} のような
     * 非ターゲットも採用するが、{@code CLEAR_VARS} は除外。
     */
    private static String matchedBuildInclude(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("include")) {
            return null;
        }
        String rest = trimmed.substring("include".length()).trim();
        if (!rest.startsWith("$(") || !rest.endsWith(")")) {
            return null;
        }
        String inside = rest.substring(2, rest.length() - 1).trim();
        if (!inside.startsWith("BUILD_") || "CLEAR_VARS".equals(inside)) {
            return null;
        }
        return inside;
    }

    /**
     * 空白で値を分割。Make の変数参照 ({@code $(LOCAL_PATH)/foo.c}) はそのままトークン化し
     * 保持する (依存先名抽出の用途には不要だが、SRC_FILES では原文を保持したい)。
     */
    static List<String> splitValues(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        int n = value.length();
        int i = 0;
        StringBuilder sb = new StringBuilder();
        int parenDepth = 0;
        while (i < n) {
            char c = value.charAt(i);
            if (c == '(' || c == '{') {
                parenDepth++;
                sb.append(c);
            } else if (c == ')' || c == '}') {
                if (parenDepth > 0) {
                    parenDepth--;
                }
                sb.append(c);
            } else if (Character.isWhitespace(c) && parenDepth == 0) {
                if (sb.length() > 0) {
                    out.add(sb.toString());
                    sb.setLength(0);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        if (sb.length() > 0) {
            out.add(sb.toString());
        }
        return out;
    }

    /**
     * Make の行継続 ({@code \\} 直後に改行) を 1 行へ結合する。
     * 出力 {@code ranges} の各要素は {@code [先頭行番号, 末尾行番号]} (1-origin)。
     */
    static List<String> joinContinuations(String src, List<int[]> ranges) {
        List<String> out = new ArrayList<>();
        String[] lines = src.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int startLine = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            // 末尾のキャリッジリターン
            if (l.endsWith("\r")) {
                l = l.substring(0, l.length() - 1);
            }
            int line1 = i + 1;
            if (sb.length() == 0) {
                startLine = line1;
            }
            // バックスラッシュ継続: 末尾の \ を取り除き、後続を空白で繋ぐ
            // ただし \\ のようにエスケープされている場合は継続ではない (極めて稀なので素直に処理)
            if (l.endsWith("\\") && !l.endsWith("\\\\")) {
                sb.append(l, 0, l.length() - 1).append(' ');
                continue;
            }
            sb.append(l);
            out.add(sb.toString());
            ranges.add(new int[] {startLine, line1});
            sb.setLength(0);
        }
        // 末尾が継続のまま終わった場合も最終行として吐き出す
        if (sb.length() > 0) {
            out.add(sb.toString());
            ranges.add(new int[] {startLine, lines.length});
        }
        return out;
    }

    /** インライン {@code #} コメント (文字列リテラル無視) を取り除き、前後 trim する。 */
    static String stripInlineCommentAndTrim(String line) {
        if (line == null) {
            return "";
        }
        int hash = line.indexOf('#');
        // # が ' で囲まれる可能性は Android.mk ではほぼ無いので素朴に処理
        if (hash >= 0) {
            // 直前が \\ ならエスケープ (これも稀)
            if (hash == 0 || line.charAt(hash - 1) != '\\') {
                return line.substring(0, hash).trim();
            }
        }
        return line.trim();
    }

    /** プロジェクト下を再帰走査して {@code Android.mk} を集める。 */
    private static void collectMkFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                String name = c.getName();
                if (name.equals(".git") || name.equals(".gradle")
                        || name.equals("build") || name.equals("out")) {
                    continue;
                }
                collectMkFiles(c, out);
            } else if (c.isFile() && c.getName().equals("Android.mk")) {
                out.add(c);
            }
        }
    }

    /** LOCAL_xxx := value の分解結果。 */
    static final class Assignment {
        final String key;
        final String value;
        final boolean append;

        Assignment(String key, String value, boolean append) {
            this.key = key;
            this.value = value;
            this.append = append;
        }
    }
}
