// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自由編集エディタ ({@link PumlSourcePanel}) の入力補完ロジック (純関数)。
 *
 * <p>キャレット直前の語を接頭辞として、PlantUML のキーワード/ディレクティブと、
 * 本文中に既に現れている識別子 (クラス名・参加者名など) から候補を作る。
 * あらゆる図種で「打ちかけの語」を素早く確定できるようにするのが狙い。</p>
 */
final class PumlCompletion {

    /** 補完対象の語を構成する文字か ({@code @} を先頭に含めてディレクティブも補完できる)。 */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@';
    }

    /** 補完候補の最大件数 (ポップアップが長くなりすぎないように)。 */
    static final int MAX_CANDIDATES = 20;

    /** 全図種で頻出する PlantUML のキーワード/ディレクティブ (補完辞書)。 */
    static final List<String> KEYWORDS = List.of(
            "@startuml", "@enduml", "@startmindmap", "@endmindmap", "@startgantt",
            "@endgantt", "@startsalt", "@endsalt", "@startjson", "@endjson",
            "@startyaml", "@endyaml", "@startwbs", "@endwbs",
            "abstract", "class", "interface", "enum", "entity", "annotation", "object",
            "package", "namespace", "node", "folder", "frame", "cloud", "component",
            "artifact", "rectangle", "card", "database", "queue", "stack", "storage",
            "usecase", "actor", "agent", "person", "boundary", "control", "collections",
            "participant", "activate", "deactivate", "autonumber", "create", "destroy",
            "note", "over", "ref", "as", "link",
            "alt", "opt", "loop", "par", "break", "critical", "group", "else", "end",
            "if", "then", "elseif", "endif", "repeat", "repeatwhile", "while", "endwhile",
            "fork", "forkagain", "split", "partition", "start", "stop", "return",
            "state", "concise", "robust", "choice",
            "skinparam", "title", "header", "footer", "legend", "caption", "scale",
            "hide", "show", "remove", "together", "left", "right", "top", "bottom",
            "up", "down", "direction", "order", "newpage", "archimate", "salt");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private PumlCompletion() {
    }

    /** キャレット直前の補完対象語 (無ければ空文字)。 */
    static String wordPrefix(String text, int caret) {
        if (text == null || caret <= 0 || caret > text.length()) {
            return "";
        }
        int i = caret;
        while (i > 0 && isWordChar(text.charAt(i - 1))) {
            i--;
        }
        return text.substring(i, caret);
    }

    /**
     * キャレット位置から語の終端 (排他端) を返す。語中で補完を確定したとき、
     * キャレット後方の語の残り (例: {@code cl|a} の {@code a}) も含めて候補で
     * 置換するために使う ({@code classa} のような残余崩れを防ぐ)。
     */
    static int wordEnd(String text, int caret) {
        if (text == null) {
            return Math.max(0, caret);
        }
        int i = Math.max(0, Math.min(caret, text.length()));
        while (i < text.length() && isWordChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * {@code prefix} に前方一致する候補を、キーワード → 本文識別子の順で重複なく返す。
     * {@code prefix} 自身と完全一致する候補は除く (打ち終わっているため)。空 prefix は候補なし。
     */
    static List<String> candidates(String prefix, String docText) {
        List<String> out = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) {
            return out;
        }
        String lp = prefix.toLowerCase(java.util.Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();
        for (String k : KEYWORDS) {
            if (matches(k, lp, prefix)) {
                seen.add(k);
            }
        }
        // 本文中の識別子 (クラス名・参加者名など)。@ 付きは除外 (ディレクティブはキーワード側)。
        if (docText != null) {
            Matcher m = IDENTIFIER.matcher(docText);
            for (Set<String> ids = new LinkedHashSet<>(); m.find();) {
                String id = m.group();
                if (ids.add(id) && matches(id, lp, prefix)) {
                    seen.add(id);
                }
            }
        }
        for (String c : seen) {
            out.add(c);
            if (out.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return out;
    }

    private static boolean matches(String candidate, String lowerPrefix, String prefix) {
        return candidate.toLowerCase(java.util.Locale.ROOT).startsWith(lowerPrefix)
                && !candidate.equalsIgnoreCase(prefix);
    }

    /** テスト用: 既定辞書に含まれるキーワード数 (辞書が空でないことの確認)。 */
    static int keywordCountForTest() {
        return KEYWORDS.size();
    }
}
