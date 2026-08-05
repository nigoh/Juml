// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlantUML テキストから「名前」を読み取る共通解析 (純関数)。
 *
 * <p>宣言された記号 (クラス・参加者・状態…) と、メッセージ行で使われているのに
 * 宣言されていない参加者を返す。補完の候補・アウトラインの一覧・未宣言の宣言追加が
 * すべて同じ読み取りを必要とするため、規則を 1 か所に集めて食い違いを防ぐ。</p>
 */
final class PumlSymbols {

    /** 宣言 1 件 (表示名・参照名・種別・行)。 */
    static final class Symbol {
        private final String name;
        private final String display;
        private final String kind;
        private final int line;

        Symbol(String name, String display, String kind, int line) {
            this.name = name;
            this.display = display;
            this.kind = kind;
            this.line = line;
        }

        /** 本文中でこの記号を指すのに使う名前 (別名があればそれ)。 */
        String name() {
            return name;
        }

        /** 一覧に出す表示名 (引用符付きで書かれていればその中身)。 */
        String display() {
            return display;
        }

        /** 宣言に使われたキーワード ({@code class} / {@code participant} など)。 */
        String kind() {
            return kind;
        }

        /** 1 始まりの行番号。 */
        int line() {
            return line;
        }

        @Override
        public String toString() {
            return kind + " " + display + " (" + line + ")";
        }
    }

    /** 宣言行の書式。引用符付きの表示名と {@code as 別名} の双方を拾う。 */
    private static final Pattern DECLARATION = Pattern.compile(
            "^\\s*(?:abstract\\s+)?(class|interface|enum|annotation|entity|object|actor"
                    + "|participant|boundary|control|collections|queue|database|usecase"
                    + "|component|node|artifact|storage|cloud|folder|frame|rectangle|card"
                    + "|state|agent|person|robust|concise|package|namespace|partition)\\s+"
                    + "(?:\"([^\"]+)\"|([A-Za-z_][\\w.]*))"
                    + "(?:\\s+as\\s+(?:\"([^\"]+)\"|([A-Za-z_][\\w]*)))?",
            Pattern.MULTILINE);

    /**
     * メッセージ行 ({@code A -> B : msg})。両端の名前を取り出す。
     * 名前は素の識別子か引用符付きに限る (角括弧やアクター記法は宣言側で拾う)。
     */
    private static final Pattern MESSAGE = Pattern.compile(
            "^\\s*(\"[^\"]+\"|[A-Za-z_][\\w.]*)\\s*"
                    + "(?:->>?|-->>?|<<--?|<--?|->x|->o|-\\\\|/-)"
                    + "\\s*(\"[^\"]+\"|[A-Za-z_][\\w.]*)\\s*(?::|$)",
            Pattern.MULTILINE);

    /** 参加者として宣言に使えるキーワード (未宣言の補完で使う)。 */
    private static final Set<String> PARTICIPANT_KINDS = Set.of(
            "participant", "actor", "boundary", "control", "collections", "queue",
            "database", "entity", "person");

    private PumlSymbols() {
    }

    /** 本文中の宣言を出現順に返す。 */
    static List<Symbol> declarations(String text) {
        List<Symbol> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        Matcher m = DECLARATION.matcher(text);
        while (m.find()) {
            String kind = m.group(1);
            String quoted = m.group(2);
            String bare = m.group(3);
            String aliasQuoted = m.group(4);
            String alias = m.group(5);
            String declared = quoted != null ? quoted : bare;
            if (declared == null || declared.isBlank()) {
                continue;
            }
            // 本文から参照されるのは別名があればそちら。無ければ宣言名そのもの。
            String ref = alias != null ? alias : aliasQuoted != null ? aliasQuoted : declared;
            out.add(new Symbol(ref, declared, kind, lineOf(text, m.start())));
        }
        return out;
    }

    /** 宣言済みの参照名 (宣言順、重複なし)。 */
    static List<String> declaredNames(String text) {
        Set<String> out = new LinkedHashSet<>();
        for (Symbol s : declarations(text)) {
            out.add(s.name());
        }
        return new ArrayList<>(out);
    }

    /**
     * メッセージ行で使われているのに宣言されていない参加者を、初出順で返す。
     *
     * <p>PlantUML はシーケンス図の参加者を自動生成するが、生成順は最初に登場した順で
     * 固定される。並びを整えたい・種別 (actor/database) を付けたいとなった時点で
     * 手で宣言を書き足すことになり、その書き足しがまさに面倒な作業にあたる。</p>
     */
    static List<String> undeclaredParticipants(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return new ArrayList<>(out);
        }
        Set<String> declared = new LinkedHashSet<>(declaredNames(text));
        Matcher m = MESSAGE.matcher(stripNoise(text));
        while (m.find()) {
            addIfNew(out, declared, m.group(1));
            addIfNew(out, declared, m.group(2));
        }
        return new ArrayList<>(out);
    }

    private static void addIfNew(Set<String> out, Set<String> declared, String raw) {
        String name = unquote(raw);
        if (name.isEmpty() || declared.contains(name)) {
            return;
        }
        // 制御構文の行頭語をメッセージの送り手と読み違えないようにする。
        if (PumlCompletionDictionary.groupsOf(name.toLowerCase(Locale.ROOT)) != null) {
            return;
        }
        out.add(name);
    }

    /** 参加者の宣言行を組み立てる ({@code participant Foo})。 */
    static String declarationFor(String name) {
        // 空白や記号を含む名前は引用が要る。
        boolean plain = !name.isEmpty() && name.chars()
                .allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '.');
        return "participant " + (plain ? name : "\"" + name + "\"");
    }

    /** 参加者として宣言に使えるキーワードか。 */
    static boolean isParticipantKind(String kind) {
        return PARTICIPANT_KINDS.contains(kind);
    }

    /**
     * 未宣言の宣言をまとめて挿入する位置 (オフセット)。既存の宣言があればその直後、
     * 無ければ {@code @startuml} や見出し行の後ろ。
     */
    static int declarationInsertOffset(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        List<Symbol> declared = declarations(text);
        if (!declared.isEmpty()) {
            int lastLine = 0;
            for (Symbol s : declared) {
                if (isParticipantKind(s.kind())) {
                    lastLine = Math.max(lastLine, s.line());
                }
            }
            if (lastLine > 0) {
                return lineEndOffset(text, lastLine);
            }
        }
        // 宣言がまだ無い図。@startuml や title/skinparam の直後へ置く。
        String[] lines = text.split("\n", -1);
        int last = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].strip().toLowerCase(Locale.ROOT);
            if (l.startsWith("@start") || l.startsWith("title") || l.startsWith("skinparam")
                    || l.startsWith("!") || l.startsWith("'") || l.isEmpty()) {
                last = i + 1;
            } else {
                break;
            }
        }
        return last == 0 ? 0 : lineEndOffset(text, last);
    }

    /** {@code line} (1 始まり) の行末の直後 (改行を含めた位置)。 */
    private static int lineEndOffset(String text, int line) {
        int at = 0;
        for (int n = 1; n < line; n++) {
            int nl = text.indexOf('\n', at);
            if (nl < 0) {
                return text.length();
            }
            at = nl + 1;
        }
        int nl = text.indexOf('\n', at);
        return nl < 0 ? text.length() : nl + 1;
    }

    /** コメント行と注記の本文を空行に置き換える (メッセージと読み違えないように)。 */
    private static String stripNoise(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean inComment = false;
        for (String line : text.split("\n", -1)) {
            String t = line.strip();
            boolean drop = inComment || t.startsWith("'");
            if (t.startsWith("/'")) {
                inComment = !t.contains("'/");
                drop = true;
            } else if (inComment && t.contains("'/")) {
                inComment = false;
            }
            sb.append(drop ? "" : line).append('\n');
        }
        return sb.toString();
    }

    private static String unquote(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** {@code offset} を含む行の番号 (1 始まり)。 */
    private static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
