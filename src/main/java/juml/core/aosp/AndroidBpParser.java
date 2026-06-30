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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AOSP の {@code Android.bp} (Soong Blueprint) ファイルから主要なモジュール宣言を抽出する。
 *
 * <p>厳密な Soong パーサではなく、最小限のキー (name, srcs, *_libs, *_deps) だけ
 * 取り出す軽量パーサ。複雑な式 (条件付き、map、include 解決) は無視する。</p>
 *
 * <p>抽出戦略:</p>
 * <ol>
 *   <li>コメント (行 {@code //} と ブロック {@code /* &#42;/}) を除去</li>
 *   <li>トップレベルの {@code <ident> { ... }} ブロックを順次切り出す</li>
 *   <li>各ブロック内で {@code name: "..."} と {@code srcs: [...]}, 各種 deps を抽出</li>
 * </ol>
 */
public final class AndroidBpParser {

    /** {@code shared_libs} / {@code static_libs} 等、依存とみなすキー一覧。 */
    private static final Set<String> DEP_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "shared_libs", "static_libs", "libs", "java_libs",
                    "header_libs", "runtime_libs", "whole_static_libs",
                    "required", "defaults", "system_shared_libs",
                    "export_static_lib_headers", "export_shared_lib_headers")));

    /**
     * 配置 (パーティション)・SDK・パッケージ等、依存以外で図/レポートに有用な
     * トップレベルのスカラ属性キー。真偽値・文字列・数値いずれも生の文字列で保持する。
     */
    private static final Set<String> SCALAR_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "sdk_version", "min_sdk_version", "target_sdk_version",
                    "package_name", "manifest", "stem", "owner", "team",
                    "vendor", "proprietary", "soc_specific", "device_specific",
                    "product_specific", "system_ext_specific", "host_supported",
                    "recovery", "ramdisk", "vendor_ramdisk", "vendor_available",
                    "product_available", "installable", "compile_multilib",
                    "stability", "unstable", "frozen")));

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "\\bname\\s*:\\s*\"([^\"]+)\"");

    /** プロジェクト全体を走査し、見つかった全 {@code Android.bp} のモジュールを返す。 */
    public List<AndroidBpModule> analyzeProject(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> bpFiles = new ArrayList<>();
        collectBpFiles(projectRoot, bpFiles);
        List<AndroidBpModule> all = new ArrayList<>();
        for (File bp : bpFiles) {
            try {
                String src = AndroidProjectScanner.readFile(bp);
                all.addAll(parseSource(src, bp.getPath()));
            } catch (IOException ex) {
                // skip unreadable
            }
        }
        // 全ファイルを集めた後に defaults 継承を解決する (defaults は別ファイルで
        // 定義されることがあるため、ファイル横断で名前解決する)。
        resolveDefaults(all);
        return all;
    }

    /**
     * {@code defaults: ["x_defaults"]} 経由のプロパティ継承を解決し、参照先
     * {@code *_defaults} モジュールの {@code srcs} / 依存を各モジュールへマージする。
     *
     * <p>{@code deps} には {@code defaults} の値も既に集約されているが、参照先の型が
     * {@code *_defaults} で終わるものだけを継承対象として展開する（通常の {@code shared_libs}
     * 依存を推移的に取り込まないため）。チェーン (defaults が defaults を持つ) は再帰し、
     * 循環は visited 集合で防ぐ。元の各モジュールのスナップショットを使うので適用順に依存しない。</p>
     */
    static void resolveDefaults(List<AndroidBpModule> modules) {
        if (modules == null || modules.isEmpty()) {
            return;
        }
        Map<String, AndroidBpModule> byName = new HashMap<>();
        Map<String, List<String>> origSrcs = new HashMap<>();
        Map<String, List<String>> origDeps = new HashMap<>();
        Map<String, Map<String, List<String>>> origByKind = new HashMap<>();
        for (AndroidBpModule m : modules) {
            String name = m.getName();
            if (!name.isEmpty()) {
                byName.put(name, m);
                origSrcs.put(name, new ArrayList<>(m.getSrcs()));
                origDeps.put(name, new ArrayList<>(m.getDeps()));
                Map<String, List<String>> kindSnapshot = new HashMap<>();
                for (Map.Entry<String, List<String>> e : m.getDepsByKind().entrySet()) {
                    kindSnapshot.put(e.getKey(), new ArrayList<>(e.getValue()));
                }
                origByKind.put(name, kindSnapshot);
            }
        }
        for (AndroidBpModule m : modules) {
            List<String> addSrcs = new ArrayList<>();
            // {kind, name} のペアを蓄積し、種別を保ったまま取り込む。
            List<String[]> addDeps = new ArrayList<>();
            List<String> base = origDeps.getOrDefault(m.getName(), m.getDeps());
            walkDefaults(base, byName, origSrcs, origDeps, origByKind,
                    new HashSet<>(), addSrcs, addDeps);
            for (String s : addSrcs) {
                if (!m.getSrcs().contains(s)) {
                    m.getSrcs().add(s);
                }
            }
            for (String[] kd : addDeps) {
                if (!m.getDeps().contains(kd[1])) {
                    m.addDep(kd[0], kd[1]);
                }
            }
        }
    }

    private static void walkDefaults(List<String> depNames,
            Map<String, AndroidBpModule> byName,
            Map<String, List<String>> origSrcs, Map<String, List<String>> origDeps,
            Map<String, Map<String, List<String>>> origByKind,
            Set<String> visited, List<String> addSrcs, List<String[]> addDeps) {
        for (String name : depNames) {
            AndroidBpModule d = byName.get(name);
            if (d == null || !d.getType().endsWith("_defaults")) {
                continue;
            }
            if (!visited.add(name)) {
                continue;
            }
            addSrcs.addAll(origSrcs.getOrDefault(name, Collections.emptyList()));
            Map<String, List<String>> dByKind =
                    origByKind.getOrDefault(name, Collections.emptyMap());
            for (Map.Entry<String, List<String>> e : dByKind.entrySet()) {
                for (String depName : e.getValue()) {
                    addDeps.add(new String[] {e.getKey(), depName});
                }
            }
            List<String> dDeps = origDeps.getOrDefault(name, Collections.emptyList());
            walkDefaults(dDeps, byName, origSrcs, origDeps, origByKind,
                    visited, addSrcs, addDeps);
        }
    }

    /** 1 ファイルの Android.bp ソースをパースして含まれるモジュールリストを返す。 */
    public List<AndroidBpModule> parseSource(String src, String filePath) {
        List<AndroidBpModule> out = new ArrayList<>();
        if (src == null || src.isEmpty()) {
            return out;
        }
        String stripped = stripComments(src);
        // トップレベルの変数代入 (foo = [...], foo += [...]) を先に集める。
        // モジュールの srcs/deps が変数を参照する場合に展開できるようにするため。
        Map<String, List<String>> vars = collectVariables(stripped);
        int i = 0;
        int n = stripped.length();
        while (i < n) {
            // skip whitespace
            while (i < n && Character.isWhitespace(stripped.charAt(i))) i++;
            if (i >= n) break;
            // read identifier
            int idStart = i;
            while (i < n && (Character.isLetterOrDigit(stripped.charAt(i))
                    || stripped.charAt(i) == '_')) {
                i++;
            }
            if (i == idStart) {
                // 非識別子文字に当たった → 1 文字飛ばして続行
                i++;
                continue;
            }
            String ident = stripped.substring(idStart, i);
            // skip whitespace
            while (i < n && Character.isWhitespace(stripped.charAt(i))) i++;
            if (i >= n || stripped.charAt(i) != '{') {
                // ブロックでない: 単純な代入や Bp 専用構文の可能性。スキップ。
                continue;
            }
            // ブロック範囲を取る
            int braceStart = i;
            int braceEnd = findMatchingBrace(stripped, braceStart);
            if (braceEnd <= braceStart) break;
            String body = stripped.substring(braceStart + 1, braceEnd);
            int lineOfStart = lineOf(src, mapStrippedOffset(src, stripped, idStart));
            AndroidBpModule mod = buildModule(ident, body, filePath, lineOfStart, vars);
            if (mod != null && !mod.getName().isEmpty()) {
                out.add(mod);
            }
            i = braceEnd + 1;
        }
        return out;
    }

    private AndroidBpModule buildModule(String type, String body, String file,
                                          int line, Map<String, List<String>> vars) {
        // package { } や license { } のように name 不要のものは name 抽出時に空文字。
        // name はモジュールの識別子なので「トップレベルの name:」だけを採用する。
        // ネストブロック (target/arch/product_variables 等) 内の name: に
        // 先取りされてモジュール名が誤って付くのを防ぐため、ネスト部をマスクしてから探す。
        Matcher nm = NAME_PATTERN.matcher(maskNestedBlocks(body));
        String name = nm.find() ? nm.group(1) : "";
        AndroidBpModule m = new AndroidBpModule(type, name, file, line);
        // srcs (変数参照 / + 連結を解決)
        extractListProperty(body, "srcs", m.getSrcs(), vars);
        // deps — キー種別を保ったまま登録する (図で static/shared/header 等を描き分けるため)
        for (String key : DEP_KEYS) {
            List<String> values = new ArrayList<>();
            extractListProperty(body, key, values, vars);
            for (String v : values) {
                m.addDep(key, v);
            }
        }
        // スカラ属性 (配置 / SDK / package_name 等) はトップレベルのみ採用する。
        // ネストブロック (target/arch/product_variables) 内の同名キーに誤って
        // 引っ張られないよう、name 抽出と同じくマスク済み body から拾う。
        String topLevel = maskNestedBlocks(body);
        for (String key : SCALAR_KEYS) {
            String v = extractScalarProperty(topLevel, key);
            if (v != null) {
                m.putScalar(key, v);
            }
        }
        // aidl_interface 等は versions / backend を持つ。バージョン数と有効バックエンドを
        // メタとして拾い、AIDL インタフェースの安定度・言語結合を把握できるようにする。
        if (type.contains("aidl")) {
            List<String> versions = new ArrayList<>();
            extractListProperty(topLevel, "versions", versions, vars);
            extractListProperty(topLevel, "versions_with_info", versions, vars);
            if (!versions.isEmpty()) {
                m.putScalar("versions_count", String.valueOf(versions.size()));
                m.putScalar("latest_version", versions.get(versions.size() - 1));
            }
            String backends = detectAidlBackends(body);
            if (!backends.isEmpty()) {
                m.putScalar("backends", backends);
            }
        }
        return m;
    }

    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]*)\"");

    /**
     * {@code key: <expr>} 形式のプロパティを body 中の全出現について抽出し {@code into} に追加する。
     *
     * <p>値の式は次に対応する:</p>
     * <ul>
     *   <li>リストリテラル {@code ["a", "b"]} (複数行可)</li>
     *   <li>単一文字列 {@code "single"}</li>
     *   <li>変数参照 {@code my_srcs} (トップレベル {@code vars} から展開)</li>
     *   <li>{@code +} 連結 {@code base + ["x"] + extra}</li>
     * </ul>
     *
     * <p>nested ブロック (target/arch 等) 内の同名キーも従来どおり拾う (srcs 集約のため)。</p>
     */
    private static void extractListProperty(String body, String key,
                                            List<String> into,
                                            Map<String, List<String>> vars) {
        Pattern keyPat = Pattern.compile("\\b" + Pattern.quote(key) + "\\s*:\\s*");
        Matcher m = keyPat.matcher(body);
        while (m.find()) {
            readListExpression(body, m.end(), vars, into);
        }
    }

    /**
     * {@code [..] + ident + "str"} のようなリスト式を {@code pos} から読み取り、
     * 解決した文字列を {@code into} に追加して、消費後のオフセットを返す。
     */
    private static int readListExpression(String s, int pos,
                                          Map<String, List<String>> vars,
                                          List<String> into) {
        int n = s.length();
        while (true) {
            while (pos < n && Character.isWhitespace(s.charAt(pos))) pos++;
            if (pos >= n) break;
            char c = s.charAt(pos);
            if (c == '[') {
                int end = findMatchingBracket(s, pos);
                String list = s.substring(pos + 1, end < 0 ? n : end);
                Matcher sm = STRING_LITERAL.matcher(list);
                while (sm.find()) {
                    into.add(sm.group(1));
                }
                pos = end < 0 ? n : end + 1;
            } else if (c == '"') {
                int end = pos + 1;
                while (end < n && s.charAt(end) != '"') {
                    if (s.charAt(end) == '\\' && end + 1 < n) {
                        end += 2;
                    } else {
                        end++;
                    }
                }
                into.add(s.substring(pos + 1, Math.min(end, n)));
                pos = end + 1;
            } else if (Character.isLetterOrDigit(c) || c == '_') {
                int start = pos;
                while (pos < n && (Character.isLetterOrDigit(s.charAt(pos))
                        || s.charAt(pos) == '_')) {
                    pos++;
                }
                List<String> v = vars.get(s.substring(start, pos));
                if (v != null) {
                    into.addAll(v);
                }
                // 未知の識別子 (解決できない変数) は無視する
            } else {
                break;
            }
            // 次オペランドへ続くのは '+' のときだけ
            int save = pos;
            while (pos < n && Character.isWhitespace(s.charAt(pos))) pos++;
            if (pos < n && s.charAt(pos) == '+') {
                pos++;
                continue;
            }
            pos = save;
            break;
        }
        return pos;
    }

    /**
     * トップレベルの変数代入 ({@code foo = [...]}, {@code foo += [...]}) を収集する。
     * モジュールブロック {@code ident { ... }} は読み飛ばし、ソース順に評価して
     * 後方の変数が前方の変数を参照できるようにする ({@code +=} は既存値へ追記)。
     */
    private static Map<String, List<String>> collectVariables(String stripped) {
        Map<String, List<String>> vars = new HashMap<>();
        int i = 0;
        int n = stripped.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(stripped.charAt(i))) i++;
            if (i >= n) break;
            int idStart = i;
            while (i < n && (Character.isLetterOrDigit(stripped.charAt(i))
                    || stripped.charAt(i) == '_')) {
                i++;
            }
            if (i == idStart) {
                i++;
                continue;
            }
            String ident = stripped.substring(idStart, i);
            int afterId = i;
            while (i < n && Character.isWhitespace(stripped.charAt(i))) i++;
            if (i >= n) break;
            char c = stripped.charAt(i);
            if (c == '{') {
                // モジュール (または無名ブロック) はスキップ
                int end = findMatchingBrace(stripped, i);
                i = end <= i ? n : end + 1;
                continue;
            }
            boolean append = false;
            if (c == '+' && i + 1 < n && stripped.charAt(i + 1) == '=') {
                append = true;
                i += 2;
            } else if (c == '=') {
                i += 1;
            } else {
                // 代入でもブロックでもない → 巻き戻して 1 文字進める
                i = afterId;
                continue;
            }
            List<String> rhs = new ArrayList<>();
            i = readListExpression(stripped, i, vars, rhs);
            List<String> cur = vars.computeIfAbsent(ident, k -> new ArrayList<>());
            if (!append) {
                cur.clear();
            }
            cur.addAll(rhs);
        }
        return vars;
    }

    /** {@code [} の対応する {@code ]} を返す (文字列リテラル考慮)。見つからなければ -1。 */
    private static int findMatchingBracket(String s, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < s.length()) { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * {@code aidl_interface} の {@code backend: { java: {...}, cpp: {...}, ndk: {...}, rust: {...} }}
     * から有効な言語バックエンドをカンマ区切りで返す。
     *
     * <p>各言語サブブロックに {@code enabled: false} が無ければ有効とみなす。backend ブロック
     * 自体が無い場合は Soong のデフォルト ({@code java, cpp, ndk}) を返す。</p>
     */
    private static String detectAidlBackends(String body) {
        Matcher bm = Pattern.compile("\\bbackend\\s*:\\s*\\{").matcher(body);
        if (!bm.find()) {
            return "java,cpp,ndk";
        }
        int braceStart = bm.end() - 1;
        int braceEnd = findMatchingBrace(body, braceStart);
        if (braceEnd <= braceStart) {
            return "";
        }
        String backendBody = body.substring(braceStart + 1, braceEnd);
        List<String> enabled = new ArrayList<>();
        for (String lang : new String[] {"java", "cpp", "ndk", "rust"}) {
            Matcher lm = Pattern.compile("\\b" + lang + "\\s*:\\s*\\{").matcher(backendBody);
            if (!lm.find()) {
                continue;
            }
            int ls = lm.end() - 1;
            int le = findMatchingBrace(backendBody, ls);
            String langBody = le > ls ? backendBody.substring(ls + 1, le) : "";
            boolean disabled = Pattern.compile("\\benabled\\s*:\\s*false\\b")
                    .matcher(langBody).find();
            if (!disabled) {
                enabled.add(lang);
            }
        }
        return String.join(",", enabled);
    }

    /**
     * {@code key: "str"} / {@code key: true} / {@code key: 29} 形式のスカラ値を抽出する。
     * 引用符付き文字列・素のトークン (真偽/数値/識別子) の両方に対応。見つからなければ null。
     */
    private static String extractScalarProperty(String body, String key) {
        Pattern pat = Pattern.compile(
                "\\b" + Pattern.quote(key) + "\\s*:\\s*"
                        + "(?:\"([^\"]*)\"|([A-Za-z0-9_.]+))");
        Matcher m = pat.matcher(body);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }

    /** プロジェクト下を再帰走査して Android.bp を集める。 */
    private static void collectBpFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                // .git や build ディレクトリは除外
                String name = c.getName();
                if (name.equals(".git") || name.equals(".gradle")
                        || name.equals("build") || name.equals("out")) {
                    continue;
                }
                collectBpFiles(c, out);
            } else if (c.isFile() && c.getName().equals("Android.bp")) {
                out.add(c);
            }
        }
    }

    /** {@code //} と {@code /* &#42;/} コメントを空白に置換する (長さ保持)。 */
    static String stripComments(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        boolean inString = false;
        while (i < n) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < n) {
                    sb.append(c).append(src.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') inString = false;
                sb.append(c);
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(c);
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char d = src.charAt(i + 1);
                if (d == '/') {
                    // 行末まで空白に置換
                    int j = i;
                    while (j < n && src.charAt(j) != '\n') {
                        sb.append(' ');
                        j++;
                    }
                    i = j;
                    continue;
                } else if (d == '*') {
                    // 閉じ */ まで空白 (改行は保持)
                    int j = i + 2;
                    sb.append("  ");
                    while (j < n) {
                        if (src.charAt(j) == '\n') {
                            sb.append('\n');
                            j++;
                        } else if (j + 1 < n && src.charAt(j) == '*'
                                && src.charAt(j + 1) == '/') {
                            sb.append("  ");
                            j += 2;
                            break;
                        } else {
                            sb.append(' ');
                            j++;
                        }
                    }
                    i = j;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * モジュール body のうち、ネストブロック ({@code key: { ... }}) の内側を空白に置換する
     * (長さ保持)。トップレベルのプロパティだけを残したいときに使う。
     * 文字列リテラルとブレースのネストを考慮する。
     */
    static String maskNestedBlocks(String body) {
        StringBuilder sb = new StringBuilder(body.length());
        int depth = 0;
        boolean inString = false;
        int n = body.length();
        for (int i = 0; i < n; i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < n) {
                    sb.append(depth == 0 ? c : ' ');
                    i++;
                    sb.append(depth == 0 ? body.charAt(i) : ' ');
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                sb.append(depth == 0 ? c : ' ');
                continue;
            }
            if (c == '"') {
                inString = true;
                sb.append(depth == 0 ? c : ' ');
            } else if (c == '{') {
                depth++;
                sb.append(' ');
            } else if (c == '}') {
                if (depth > 0) {
                    depth--;
                }
                sb.append(' ');
            } else {
                sb.append(depth == 0 ? c : ' ');
            }
        }
        return sb.toString();
    }

    private static int findMatchingBrace(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') return open;
        int depth = 1;
        boolean inString = false;
        for (int i = open + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < src.length()) { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return src.length();
    }

    /** コメント除去後オフセット → 元ソースのオフセット (長さ保持なので等しい)。 */
    private static int mapStrippedOffset(String origSrc, String strippedSrc,
                                          int strippedOffset) {
        // stripComments は長さ保持のため、オフセットは等価
        return Math.min(strippedOffset, origSrc.length());
    }

    private static int lineOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }
}
