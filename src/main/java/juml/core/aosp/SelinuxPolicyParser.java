// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import juml.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AOSP の SELinux policy ({@code *.te}, {@code *.cil}) を軽量パースする。
 *
 * <p>厳密な SELinux パーサではなく、AOSP プロジェクトの「ドメイン宣言」と
 * 「allow/neverallow ルール」を素早く一覧化するためのヘルパ。マクロ展開や
 * include 解決は行わない。</p>
 *
 * <p>抽出対象:</p>
 * <ul>
 *   <li>{@code type foo_t [, attr1, attr2];}</li>
 *   <li>{@code typeattribute foo_t attr1;}</li>
 *   <li>{@code allow|neverallow|dontaudit|auditallow X Y:CLASS { ops };}</li>
 *   <li>1 行コメント ({@code # ...}) と C 風コメント ({@code /* &#42;/}) を除去</li>
 * </ul>
 */
public final class SelinuxPolicyParser {

    private static final Pattern TYPE_DECL = Pattern.compile(
            "(?m)^\\s*type\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*"
                    + "(?:,\\s*([^;]+?))?\\s*;");
    private static final Pattern TYPE_ATTRIBUTE = Pattern.compile(
            "(?m)^\\s*typeattribute\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+"
                    + "([^;]+?)\\s*;");
    // subject / target / class / permission には SELinux のワイルドカード '*' と
    // 補集合演算子 '~' が現れ得る (例: allow domain *:process ...; / allow X Y:file ~{ append };)。
    // 文字クラスに '*' '~' を許容し、permission は '~{ ... }' 補集合・'*' 全許可も拾う。
    private static final Pattern ALLOW_RULE = Pattern.compile(
            "(?m)^\\s*(allow|neverallow|dontaudit|auditallow)\\s+"
                    + "([A-Za-z_{*~][A-Za-z0-9_{}\\s,~*-]*?)\\s+"
                    + "([A-Za-z_{*~][A-Za-z0-9_{}\\s,~*-]*?)\\s*:\\s*"
                    + "([A-Za-z_{*~][A-Za-z0-9_{}\\s,~*-]*?)\\s*"
                    + "(?:(~)?\\{\\s*([^}]+?)\\s*\\}|(\\*|[A-Za-z_][A-Za-z0-9_]*))\\s*;");

    /** プロジェクト下を再帰走査して {@code *.te} を全件パースする。 */
    public List<SelinuxRule> analyzeProject(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> teFiles = new ArrayList<>();
        collectTeFiles(projectRoot, teFiles);
        List<SelinuxRule> all = new ArrayList<>();
        for (File f : teFiles) {
            try {
                String src = AndroidProjectScanner.readFile(f);
                all.addAll(parseSource(src, f.getPath()));
            } catch (IOException ex) {
                // skip
            }
        }
        return all;
    }

    /** 単一ソースをパースする (テスト用)。 */
    public List<SelinuxRule> parseSource(String src, String filePath) {
        List<SelinuxRule> out = new ArrayList<>();
        if (src == null || src.isEmpty()) return out;
        String stripped = stripComments(src);

        // type declarations
        Matcher m = TYPE_DECL.matcher(stripped);
        while (m.find()) {
            SelinuxRule.Builder b = SelinuxRule.builder(SelinuxRule.Kind.TYPE_DECL)
                    .subject(m.group(1))
                    .file(filePath)
                    .line(lineOf(src, m.start()));
            String attrs = m.group(2);
            if (attrs != null) {
                for (String a : attrs.split(",")) {
                    b.addAttribute(a.trim());
                }
            }
            out.add(b.build());
        }

        // typeattribute
        Matcher ta = TYPE_ATTRIBUTE.matcher(stripped);
        while (ta.find()) {
            SelinuxRule.Builder b = SelinuxRule.builder(SelinuxRule.Kind.TYPE_ATTRIBUTE)
                    .subject(ta.group(1))
                    .file(filePath)
                    .line(lineOf(src, ta.start()));
            String attrs = ta.group(2);
            if (attrs != null) {
                for (String a : attrs.split("[,\\s]+")) {
                    b.addAttribute(a.trim());
                }
            }
            out.add(b.build());
        }

        // allow / neverallow / dontaudit / auditallow
        Matcher ar = ALLOW_RULE.matcher(stripped);
        while (ar.find()) {
            SelinuxRule.Kind kind;
            switch (ar.group(1)) {
                case "allow": kind = SelinuxRule.Kind.ALLOW; break;
                case "neverallow": kind = SelinuxRule.Kind.NEVERALLOW; break;
                case "dontaudit": kind = SelinuxRule.Kind.DONTAUDIT; break;
                case "auditallow": kind = SelinuxRule.Kind.AUDITALLOW; break;
                default: continue;
            }
            SelinuxRule.Builder b = SelinuxRule.builder(kind)
                    .subject(ar.group(2).trim())
                    .target(ar.group(3).trim())
                    .objectClass(stripBraces(ar.group(4)).trim())
                    .complement(ar.group(5) != null)
                    .file(filePath)
                    .line(lineOf(src, ar.start()));
            String permsGroup = ar.group(6);
            String singlePerm = ar.group(7);
            if (permsGroup != null) {
                for (String p : permsGroup.split("\\s+")) {
                    b.addPermission(p.trim());
                }
            } else if (singlePerm != null) {
                b.addPermission(singlePerm.trim());
            }
            out.add(b.build());
        }
        return out;
    }

    private static String stripBraces(String s) {
        if (s == null) return "";
        return s.replace("{", " ").replace("}", " ").trim();
    }

    /** プロジェクト下を再帰走査して {@code *.te} を集める。 */
    private static void collectTeFiles(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                String name = c.getName();
                if (name.equals(".git") || name.equals(".gradle")
                        || name.equals("build") || name.equals("out")) {
                    continue;
                }
                collectTeFiles(c, out);
            } else if (c.isFile()) {
                String name = c.getName();
                if (name.endsWith(".te")) {
                    out.add(c);
                }
            }
        }
    }

    /** {@code #} 行コメントと {@code /* &#42;/} ブロックコメントを空白に置換 (オフセット保持)。 */
    static String stripComments(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        boolean inString = false;
        while (i < n) {
            char c = src.charAt(i);
            if (inString) {
                // .te には複数行文字列が無いので、対になっていない裸の " が行を跨いで
                // 文字列扱いを広げ、後続行の # コメントを消し損ねる (コメントアウトされた
                // allow 行が誤って生きたルールになる) のを防ぐため、改行で文字列状態を閉じる。
                if (c == '\n') {
                    inString = false;
                    sb.append(c);
                    i++;
                    continue;
                }
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
            if (c == '"') { inString = true; sb.append(c); i++; continue; }
            if (c == '#') {
                // 行末まで空白に置換
                while (i < n && src.charAt(i) != '\n') {
                    sb.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
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
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int lineOf(String src, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < src.length(); i++) {
            if (src.charAt(i) == '\n') line++;
        }
        return line;
    }
}
