// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成した PlantUML テキストの「描画前サニティチェック」を行う軽量リンタ。
 *
 * <p>完全な PlantUML パーサではなく、過去に実際の描画失敗
 * (「この図を描画できませんでした」) を招いた既知のゴミ／構文崩れを安価に検出する
 * ことを目的とする。図生成器のリグレッションテストで使うほか、描画失敗時の
 * 原因切り分け (どの行が怪しいか) のヒント出力にも使える。</p>
 *
 * <p>検出ルール (false positive を避けるため高シグナルなものに絞る):</p>
 * <ul>
 *   <li>{@code @startuml} / {@code @enduml} の対応 (数の不一致)</li>
 *   <li>クラス/ノード宣言で <b>色がリンクより前</b> ({@code #color [[link]]}) になっている。
 *       PlantUML はこの順序を構文エラー扱いし、図全体が描画失敗する。正しくは
 *       {@code [[link]] #color}</li>
 *   <li>1 行内の {@code [[} と {@code ]]} の数が不一致 (壊れたリンク)</li>
 * </ul>
 */
public final class PlantUmlSyntaxChecker {

    private PlantUmlSyntaxChecker() {
    }

    /** 検出した 1 件の問題 (行番号は 1 始まり; 取れないときは 0)。 */
    public static final class Issue {
        public final int line;
        public final String message;
        public final String snippet;

        Issue(int line, String message, String snippet) {
            this.line = line;
            this.message = message;
            this.snippet = snippet;
        }

        @Override
        public String toString() {
            return "line " + line + ": " + message
                    + (snippet.isEmpty() ? "" : "  >> " + snippet);
        }
    }

    /**
     * 色 ({@code #ABC123} または {@code #ColorName}) の直後に空白を挟んで {@code [[}
     * が続く誤順序を検出する。これは class/コンポーネント宣言で色とリンクを併記したとき
     * の典型的な描画失敗パターン。
     */
    private static final Pattern COLOR_BEFORE_LINK =
            Pattern.compile("#[0-9A-Za-z_]+\\s*\\[\\[");

    /** {@code puml} を検査し、見つかった問題の一覧を返す (問題なしなら空)。 */
    public static List<Issue> check(String puml) {
        List<Issue> issues = new ArrayList<>();
        if (puml == null || puml.isEmpty()) {
            issues.add(new Issue(0, "PlantUML text is empty", ""));
            return issues;
        }

        int starts = countOccurrences(puml, "@startuml");
        int ends = countOccurrences(puml, "@enduml");
        if (starts == 0) {
            issues.add(new Issue(0, "missing @startuml", ""));
        }
        if (starts != ends) {
            issues.add(new Issue(0,
                    "@startuml/@enduml count mismatch (" + starts + " vs " + ends + ")", ""));
        }

        String[] lines = puml.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;

            Matcher m = COLOR_BEFORE_LINK.matcher(line);
            if (m.find()) {
                issues.add(new Issue(lineNo,
                        "background color precedes [[link]] — PlantUML requires "
                        + "'[[link]] #color' order (this order fails to render)",
                        line.trim()));
            }

            int open = countOccurrences(line, "[[");
            int close = countOccurrences(line, "]]");
            if (open != close) {
                issues.add(new Issue(lineNo,
                        "unbalanced link brackets ([[=" + open + " ]]=" + close + ")",
                        line.trim()));
            }
        }
        return issues;
    }

    /** {@code check} の結果を 1 行サマリにする (問題なしなら空文字)。診断ログ用。 */
    public static String summarize(String puml) {
        List<Issue> issues = check(puml);
        if (issues.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(issues.size(), 5);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(issues.get(i));
        }
        if (issues.size() > limit) {
            sb.append(" | (+").append(issues.size() - limit).append(" more)");
        }
        return sb.toString();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
