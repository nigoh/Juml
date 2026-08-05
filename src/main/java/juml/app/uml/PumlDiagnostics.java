// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import java.text.MessageFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 入力中の PlantUML テキストに対する軽量な構文チェック (純関数)。
 *
 * <p>狙いは一つ、<b>閉じ忘れをその場で気づかせること</b>。{@code alt} を書いて
 * {@code end} を忘れる、{@code @enduml} を書き忘れる — この手の間違いは、今までは
 * 描画が失敗して初めて分かり、しかも PlantUML のエラーは原因行を的確に指さないことが
 * 多かった。開いたブロックの行そのものを指せれば、探す手間が消える。</p>
 *
 * <p>誤検出は「無いよりも悪い」。波線が出っぱなしのエディタは信用されなくなり、
 * 本物の指摘まで無視されるようになる。そのため規則は<b>間違えようのないものだけ</b>に
 * 絞ってある:</p>
 * <ul>
 *   <li>{@code @start…} と {@code @end…} の対応</li>
 *   <li>{@code alt}/{@code loop}/{@code if (…)} のように、終端キーワードが一意に
 *       決まっているブロックの対応</li>
 * </ul>
 *
 * <p>逆に、文脈で意味が変わるもの (波括弧は salt/json では本文そのもの、
 * {@code note} は {@code :} の有無で 1 行にも複数行にもなる) は、
 * 判断がつく形だけを見る。判断がつかない行は<b>黙って見送る</b>。</p>
 */
final class PumlDiagnostics {

    /** 検出した 1 件 (行は 1 始まり、エディタの行番号と揃える)。 */
    static final class Diagnostic {
        private final int line;
        private final String message;

        Diagnostic(int line, String message) {
            this.line = line;
            this.message = message;
        }

        /** 1 始まりの行番号。 */
        int line() {
            return line;
        }

        /** 利用者に見せる説明 (i18n 済み)。 */
        String message() {
            return message;
        }

        @Override
        public String toString() {
            return line + ": " + message;
        }
    }

    /** 開いたブロック 1 件 (開始語・開始行・期待する終端語)。 */
    private static final class Open {
        /** 表示に使う実際の綴り ({@code !ifdef} など)。 */
        private final String keyword;
        /** 対応付けに使う代表語 ({@code !ifdef} も {@code !ifndef} も {@code !if})。 */
        private final String family;
        private final int line;
        private final String expected;
        /** 中身が自由記述か (凡例・注記の本文は構文ではなく文章)。 */
        private final boolean freeText;

        Open(String keyword, int line, String expected) {
            this.keyword = keyword;
            this.family = familyOf(keyword);
            this.line = line;
            this.expected = expected;
            this.freeText = FREE_TEXT.contains(this.family);
        }
    }

    /**
     * 中身が自由記述のブロック。凡例や注記の本文には {@code alt/opt/loop} のような
     * 説明文がふつうに現れる (Juml 自身が出す凡例がまさにそれ) ので、
     * 本文を構文として読んではいけない。
     */
    private static final List<String> FREE_TEXT = List.of("legend", "note");

    /**
     * 波括弧の中身が「メンバーの一覧」になる宣言語。この中の {@code END} や
     * {@code NOTE} は列挙定数であって構文ではないため、ブロック検査から外す。
     */
    private static final List<String> MEMBER_BODIES = List.of(
            "class", "abstract", "interface", "enum", "annotation", "entity", "object",
            "struct", "protocol", "exception", "metaclass", "stereotype", "map");

    /**
     * 綴りの違う開始語を、対応付け用の代表語へ寄せる。
     * {@code !endif} は {@code !if} / {@code !ifdef} / {@code !ifndef} のどれでも閉じる。
     */
    private static String familyOf(String keyword) {
        return "!ifdef".equals(keyword) || "!ifndef".equals(keyword) ? "!if" : keyword;
    }

    /**
     * 終端語が一意に決まる開始語。値は「期待する終端の表示名」。
     * 判定は行頭の語に対して行う。
     */
    private static final Map<String, String> SIMPLE_OPENERS = Map.ofEntries(
            Map.entry("alt", "end"), Map.entry("opt", "end"), Map.entry("loop", "end"),
            Map.entry("par", "end"), Map.entry("critical", "end"),
            Map.entry("group", "end"), Map.entry("break", "end"),
            Map.entry("box", "end box"), Map.entry("legend", "endlegend"),
            Map.entry("!if", "!endif"), Map.entry("!ifdef", "!endif"),
            Map.entry("!ifndef", "!endif"), Map.entry("!while", "!endwhile"),
            Map.entry("!function", "!endfunction"),
            Map.entry("!procedure", "!endprocedure"),
            Map.entry("!definelong", "!enddefinelong"));

    /** {@code end} 1 語で閉じられる開始語 (シーケンス図の複合フラグメント)。 */
    private static final List<String> END_CLOSED = List.of(
            "alt", "opt", "loop", "par", "critical", "group", "break");

    private PumlDiagnostics() {
    }

    /** {@code text} を検査して見つかった問題を行番号順に返す。 */
    static List<Diagnostic> analyze(String text) {
        List<Diagnostic> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        String[] lines = text.split("\n", -1);
        Deque<Open> stack = new ArrayDeque<>();
        Deque<Open> blocks = new ArrayDeque<>();
        boolean inComment = false;
        // 図種ごとに文法が別物なので、ブロック対応の検査は @startuml の中だけで行う
        // (salt の波括弧、mindmap の *、gantt の [Task] を構文エラーと見なさないため)。
        boolean checkable = true;
        // クラス/列挙の本体に入っている深さ。0 より大きい間は中身をメンバーとみなす。
        int memberDepth = 0;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            int lineNo = i + 1;
            String line = raw.strip();
            if (inComment) {
                inComment = !line.contains("'/");
                continue;
            }
            if (line.startsWith("/'")) {
                inComment = !line.contains("'/");
                continue;
            }
            if (line.isEmpty() || line.startsWith("'")) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("@start")) {
                blocks.push(new Open(firstWord(lower), lineNo, "@end…"));
                checkable = lower.startsWith("@startuml");
                continue;
            }
            if (lower.startsWith("@end")) {
                if (blocks.isEmpty()) {
                    out.add(new Diagnostic(lineNo, msg("puml.diag.unexpected", line)));
                } else {
                    blocks.pop();
                }
                checkable = true;
                continue;
            }
            if (memberDepth > 0) {
                // クラス/列挙の本体。END や NOTE といった列挙定数を構文語と読まない。
                if (lower.startsWith("}")) {
                    memberDepth--;
                } else if (opensMemberBody(lower)) {
                    memberDepth++;
                }
                continue;
            }
            if (opensMemberBody(lower)) {
                memberDepth++;
                continue;
            }
            if (checkable) {
                scanBlockLine(lower, lineNo, stack, out);
            }
        }
        reportUnclosed(blocks, out);
        reportUnclosed(stack, out);
        out.sort((a, b) -> Integer.compare(a.line, b.line));
        return out;
    }

    /** 1 行を見て、ブロックの開始なら積み、終端なら下ろす。 */
    private static void scanBlockLine(String lower, int lineNo, Deque<Open> stack,
                                      List<Diagnostic> out) {
        Open top = stack.peek();
        if (top != null && top.freeText) {
            // 自由記述ブロックの中身は文章。終端だけを探し、他は一切見ない。
            String closer = closerOf(lower);
            if (top.family.equals(closer)) {
                stack.pop();
            }
            return;
        }
        String closed = closerOf(lower);
        if (closed != null) {
            popFor(closed, lower, lineNo, stack, out);
            return;
        }
        String opener = openerOf(lower);
        if (opener != null) {
            stack.push(new Open(opener, lineNo, SIMPLE_OPENERS.getOrDefault(opener,
                    expectedFor(opener))));
        }
    }

    /**
     * この行が閉じている開始語 (無ければ null)。複合終端 ({@code end note} 等) を
     * 先に見てから、素の {@code end} を見る。
     */
    private static String closerOf(String lower) {
        // 複合終端。"end fork" と "endfork" の双方の綴りを許す。
        // title / header / footer は複数行形式かどうかを行から判断できない
        // (単独行でも成立する) ので、終端としても開始としても扱わず見送る。
        for (String kw : new String[] {"note", "box", "fork", "split", "legend"}) {
            if (lower.equals("end " + kw) || lower.equals("end" + kw)) {
                return kw;
            }
        }
        for (String kw : new String[] {"if", "while", "switch", "legend"}) {
            if (lower.equals("end" + kw) || lower.equals("end " + kw)) {
                return kw;
            }
        }
        if (lower.startsWith("repeat while")) {
            return "repeat";
        }
        if (lower.equals("!endif")) {
            return "!if";
        }
        if (lower.equals("!endwhile")) {
            return "!while";
        }
        if (lower.equals("!endfunction")) {
            return "!function";
        }
        if (lower.equals("!endprocedure")) {
            return "!procedure";
        }
        if (lower.equals("!enddefinelong")) {
            return "!definelong";
        }
        // 素の end はシーケンス図の複合フラグメントを閉じる。
        return lower.equals("end") ? "end" : null;
    }

    /** この行が開いているブロックの開始語 (無ければ null)。 */
    private static String openerOf(String lower) {
        String first = firstWord(lower);
        if (SIMPLE_OPENERS.containsKey(first)) {
            return first;
        }
        // 制御構造は「キーワード + 丸括弧」の形のときだけ開始とみなす
        // (シーケンス図の "if" のような別用法を巻き込まないため)。
        for (String kw : new String[] {"if", "while", "switch"}) {
            if (lower.startsWith(kw + " (") || lower.startsWith(kw + "(")) {
                return kw;
            }
        }
        // "fork again" / "split again" は継続であって開始ではない。
        if (lower.equals("fork") || lower.equals("split")) {
            return lower;
        }
        if (lower.equals("repeat")) {
            return "repeat";
        }
        // 複数行の note は ":" を持たない形だけ。":" があればその行で完結している。
        if ((first.equals("note") || first.equals("hnote") || first.equals("rnote"))
                && lower.indexOf(':') < 0) {
            return "note";
        }
        return null;
    }

    /** 開始語に対して期待する終端の表示名。 */
    private static String expectedFor(String opener) {
        switch (opener) {
            case "if":     return "endif";
            case "while":  return "endwhile";
            case "switch": return "endswitch";
            case "fork":   return "end fork";
            case "split":  return "end split";
            case "repeat": return "repeat while";
            case "note":   return "end note";
            default:       return "end";
        }
    }

    /**
     * 終端に対応する開始をスタックから下ろす。素の {@code end} は
     * 直近の「end で閉じる」開始を探す (入れ子の中に endif 待ちが挟まっていても、
     * それは別の指摘として既に出るため、ここで二重に騒がない)。
     */
    private static void popFor(String closed, String line, int lineNo, Deque<Open> stack,
                               List<Diagnostic> out) {
        if ("end".equals(closed)) {
            for (Open open : stack) {
                if (END_CLOSED.contains(open.family)) {
                    stack.remove(open);
                    return;
                }
            }
            out.add(new Diagnostic(lineNo, msg("puml.diag.unexpected", line)));
            return;
        }
        for (Open open : stack) {
            if (open.family.equals(closed)) {
                stack.remove(open);
                return;
            }
        }
        out.add(new Diagnostic(lineNo, msg("puml.diag.unexpected", line)));
    }

    /** 閉じられずに残った開始をすべて報告する。 */
    private static void reportUnclosed(Deque<Open> stack, List<Diagnostic> out) {
        for (Open open : stack) {
            out.add(new Diagnostic(open.line,
                    MessageFormat.format(Messages.get("puml.diag.unclosed"),
                            open.keyword, open.expected)));
        }
    }

    private static String msg(String key, String arg) {
        return MessageFormat.format(Messages.get(key), arg);
    }

    /**
     * この行がクラス/列挙などの「メンバー一覧」本体を開いているか。
     * {@code package X {} } や {@code partition X {} } は中身が文であって
     * メンバーではないので含めない (中の閉じ忘れは引き続き見たい)。
     */
    private static boolean opensMemberBody(String lower) {
        return lower.endsWith("{") && MEMBER_BODIES.contains(firstWord(lower));
    }

    /** 行頭の語 (空白まで)。 */
    private static String firstWord(String lower) {
        int i = 0;
        while (i < lower.length() && !Character.isWhitespace(lower.charAt(i))) {
            i++;
        }
        return lower.substring(0, i);
    }
}
