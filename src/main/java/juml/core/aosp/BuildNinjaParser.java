// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Soong が生成する {@code build.ninja} を解析し、rule 使用統計とターゲット依存エッジを抽出する。
 *
 * <p>厳密な Ninja パーサではなく、{@link AndroidBpParser} と同じ軽量・ベストエフォート方針。
 * 抽出するのは:</p>
 * <ul>
 *   <li>{@code rule <name>} と直後の {@code command =} / {@code description =}</li>
 *   <li>{@code build <out...>: <rule> <in...> [| implicit...] [|| order-only...]} の依存</li>
 * </ul>
 *
 * <p>出力/入力のパスは {@link #groupOf(String)} で「モジュール/ディレクトリ」粒度に畳み込み、
 * 巨大な build.ninja でも図が破綻しないよう集約する。{@code build.ninja} は数百 MB〜GB に
 * なり得るため {@link BufferedReader} で逐次読み込みし、{@link #maxLines} 行で打ち切る。</p>
 */
public final class BuildNinjaParser {

    /** 読み込み行数の上限 (これを超えると打ち切り、{@code truncated} を立てる)。 */
    private long maxLines = 5_000_000L;

    public BuildNinjaParser() {
    }

    /** 読み込み行数上限を設定する (テスト用)。 */
    public BuildNinjaParser maxLines(long maxLines) {
        this.maxLines = maxLines;
        return this;
    }

    /**
     * プロジェクト下 (または {@code out/soong} 直下) の {@code build.ninja} を解析する。
     * 複数見つかった場合は全て統合する。
     */
    public BuildNinjaGraph analyzeProject(File projectRoot) throws IOException {
        BuildNinjaGraph graph = new BuildNinjaGraph();
        if (projectRoot == null) {
            return graph;
        }
        List<File> ninjaFiles = new ArrayList<>();
        if (projectRoot.isFile() && projectRoot.getName().endsWith(".ninja")) {
            ninjaFiles.add(projectRoot);
        } else if (projectRoot.isDirectory()) {
            collectNinjaFiles(projectRoot, ninjaFiles, 0);
        }
        for (File f : ninjaFiles) {
            parseFile(f, graph);
            if (graph.isTruncated()) {
                break;
            }
        }
        return graph;
    }

    /** 1 つの {@code .ninja} ファイルを {@code graph} に解析・加算する。 */
    public void parseFile(File file, BuildNinjaGraph graph) throws IOException {
        long lines = 0;
        BuildNinjaRule currentRule = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String logical;
            while ((logical = readLogicalLine(r)) != null) {
                lines++;
                if (lines > maxLines) {
                    graph.markTruncated();
                    return;
                }
                if (logical.isEmpty()) {
                    continue;
                }
                char first = logical.charAt(0);
                boolean indented = first == ' ' || first == '\t';
                String trimmed = logical.trim();
                if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                    continue;
                }
                if (indented) {
                    // rule ブロック内の変数束縛 (command / description)
                    if (currentRule != null) {
                        applyRuleBinding(currentRule, trimmed);
                    }
                    continue;
                }
                // トップレベル宣言
                currentRule = null;
                if (trimmed.startsWith("rule ")) {
                    String name = trimmed.substring(5).trim();
                    currentRule = graph.ruleFor(name);
                } else if (trimmed.startsWith("build ")) {
                    parseBuildStatement(trimmed.substring(6), graph);
                }
                // default / pool / include / subninja / 変数代入 は無視
            }
        }
    }

    /** {@code command = ...} / {@code description = ...} を rule に反映。 */
    private static void applyRuleBinding(BuildNinjaRule rule, String binding) {
        int eq = binding.indexOf('=');
        if (eq <= 0) {
            return;
        }
        String key = binding.substring(0, eq).trim();
        String val = binding.substring(eq + 1).trim();
        if (key.equals("command")) {
            rule.setCommand(summarize(val, 120));
        } else if (key.equals("description")) {
            rule.setDescription(summarize(val, 80));
        }
    }

    /**
     * {@code <out...>: <rule> <in...> | <implicit...> || <order-only...>} を解析。
     * 先頭の (未エスケープな) コロンで outputs と rest を分割する。
     */
    private void parseBuildStatement(String body, BuildNinjaGraph graph) {
        int colon = firstUnescapedColon(body);
        if (colon < 0) {
            return;
        }
        // 出力側の暗黙出力区切り `|` (build explicit | implicit: ...) を除去する。
        // 除かないと "|" 自体がパスとして splitPaths に拾われ、groupOf("|") の擬似ノードが
        // 生成される (入力側は下で同様に除去済み)。
        String outputsPart = body.substring(0, colon).replace("|", " ");
        String rest = body.substring(colon + 1).trim();
        List<String> outputs = splitPaths(outputsPart);
        if (outputs.isEmpty()) {
            return;
        }
        // rest = "<rule> <inputs...>" — | や || で区切られた入力種別はまとめて入力扱い
        String afterRule;
        int sp = rest.indexOf(' ');
        String ruleName;
        if (sp < 0) {
            ruleName = rest;
            afterRule = "";
        } else {
            ruleName = rest.substring(0, sp);
            afterRule = rest.substring(sp + 1);
        }
        if (!ruleName.isEmpty()) {
            graph.ruleFor(ruleName).incrementBuildCount();
        }
        graph.addBuildStatement(outputs.size());

        String inputsPart = afterRule.replace("||", " ").replace("|", " ");
        List<String> inputs = splitPaths(inputsPart);

        // 集約: 出力グループ <- 入力グループ のエッジを張る
        java.util.LinkedHashSet<String> outGroups = new java.util.LinkedHashSet<>();
        for (String o : outputs) {
            outGroups.add(groupOf(o));
        }
        java.util.LinkedHashSet<String> inGroups = new java.util.LinkedHashSet<>();
        for (String in : inputs) {
            inGroups.add(groupOf(in));
        }
        for (String og : outGroups) {
            for (String ig : inGroups) {
                graph.addGroupEdge(og, ig);
            }
        }
    }

    /**
     * パスを「モジュール/ディレクトリ」単位に畳み込む。
     * {@code .intermediates/<a>/<b>/...} は {@code <a>/<b>}、それ以外は先頭ディレクトリ。
     */
    static String groupOf(String path) {
        if (path == null || path.isEmpty()) {
            return "(unknown)";
        }
        String p = path;
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        int idx = p.indexOf(".intermediates/");
        if (idx >= 0) {
            String rest = p.substring(idx + ".intermediates/".length());
            String[] seg = rest.split("/");
            if (seg.length >= 2) {
                return seg[0] + "/" + seg[1];
            }
            if (seg.length == 1 && !seg[0].isEmpty()) {
                return seg[0];
            }
        }
        int slash = p.indexOf('/');
        if (slash > 0) {
            return p.substring(0, slash);
        }
        return p;
    }

    /** 空白区切りのパス列を取り出す ({@code $ } のエスケープ空白は結合)。 */
    private static List<String> splitPaths(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '$' && i + 1 < n) {
                char d = s.charAt(i + 1);
                // $ + space/colon/$ はエスケープ。リテラルとして取り込む。
                cur.append(d);
                i++;
                continue;
            }
            if (c == ' ' || c == '\t') {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** {@code $:} を避けつつ最初のコロン位置を返す。見つからなければ -1。 */
    private static int firstUnescapedColon(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '$') {
                i++; // 次の 1 文字はエスケープ
                continue;
            }
            if (c == ':') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 論理行を 1 行読む。行末が単一の未エスケープ {@code $} なら次行へ継続する。
     */
    private static String readLogicalLine(BufferedReader r) throws IOException {
        String line = r.readLine();
        if (line == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(line);
        while (endsWithContinuation(sb)) {
            sb.setLength(sb.length() - 1); // 末尾の $ を除去
            String next = r.readLine();
            if (next == null) {
                break;
            }
            // 継続行の先頭インデントは Ninja 仕様では除去される
            sb.append(stripLeadingWhitespace(next));
        }
        return sb.toString();
    }

    private static boolean endsWithContinuation(StringBuilder sb) {
        int len = sb.length();
        if (len == 0 || sb.charAt(len - 1) != '$') {
            return false;
        }
        // 末尾の連続する $ の数が奇数なら継続 ($$ はリテラル $)
        int count = 0;
        for (int i = len - 1; i >= 0 && sb.charAt(i) == '$'; i--) {
            count++;
        }
        return (count % 2) == 1;
    }

    private static String stripLeadingWhitespace(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return s.substring(i);
    }

    private static String summarize(String s, int max) {
        String one = s.replace('\n', ' ').replace('\r', ' ').trim();
        if (one.length() <= max) {
            return one;
        }
        return one.substring(0, max) + "…";
    }

    /** プロジェクト下を再帰走査して {@code build.ninja} / {@code *.ninja} を集める。 */
    private static void collectNinjaFiles(File dir, List<File> out, int depth) {
        if (depth > 12) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                String name = c.getName();
                // .git / .repo のみ除外。out/ は build.ninja の所在地なので除外しない。
                if (name.equals(".git") || name.equals(".repo")) {
                    continue;
                }
                collectNinjaFiles(c, out, depth + 1);
            } else if (c.isFile()) {
                String name = c.getName();
                if (name.equals("build.ninja") || name.endsWith(".ninja")) {
                    out.add(c);
                }
            }
        }
    }
}
