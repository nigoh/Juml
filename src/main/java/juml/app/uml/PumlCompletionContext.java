// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 補完を出す「その場の文脈」。キャレット位置から、いま書いている図種
 * ({@link PumlSnippets.Group}) と行の状況を読み取る。
 *
 * <p>候補を文脈で絞る/並べ替えることが、打鍵数を減らす一番の効き所になる。
 * シーケンス図を書いている最中に {@code usecase} や {@code skinparamMonochrome} が
 * 上位に来ると、目的の候補まで矢印キーを何度も押すことになるため。</p>
 *
 * <p>図種の判定は 2 段構え: {@code @startmindmap} 等のブロック指定子があれば
 * それを最優先し、無ければ ({@code @startuml} の内側なので) 本文の書式シグネチャを
 * 数え上げて多数決する。判定できなければ {@link PumlSnippets.Group#COMMON} を返し、
 * 「全図種の候補を平等に出す」既定動作へ落とす。</p>
 */
final class PumlCompletionContext {

    /** シグネチャ 1 件 (どの図種を示す書式か)。 */
    private static final class Signal {
        private final PumlSnippets.Group group;
        private final Pattern pattern;
        private final int weight;

        Signal(PumlSnippets.Group group, String regex, int weight) {
            this.group = group;
            this.pattern = Pattern.compile(regex, Pattern.MULTILINE);
            this.weight = weight;
        }
    }

    /**
     * 図種を示す書式シグネチャ表。1 つの書式が複数図種で使われる場合 (例: {@code interface} は
     * クラス図とコンポーネント図の双方) は、両方に弱い重みで登録して多数決に委ねる。
     */
    private static final List<Signal> SIGNALS = List.of(
            // シーケンス図: 参加者宣言・メッセージ・活性化・複合フラグメント。
            new Signal(PumlSnippets.Group.SEQUENCE,
                    "^\\s*(participant|boundary|control|collections)\\b", 3),
            new Signal(PumlSnippets.Group.SEQUENCE,
                    "^\\s*[\\w\"()\\[\\]]+\\s*(->|-->|->>|-->>|<-|<--|<<-)", 3),
            new Signal(PumlSnippets.Group.SEQUENCE,
                    "^\\s*(activate|deactivate|autonumber|hnote|rnote|newpage)\\b", 3),
            new Signal(PumlSnippets.Group.SEQUENCE,
                    "^\\s*(alt|opt|loop|par|critical|group|break|ref)\\b", 2),
            // クラス図: 型宣言と UML の関係記法。
            new Signal(PumlSnippets.Group.CLASS,
                    "^\\s*(abstract\\s+)?(class|enum|annotation)\\b", 3),
            new Signal(PumlSnippets.Group.CLASS, "<\\|--|<\\|\\.\\.|--\\|>|\\.\\.\\|>", 3),
            new Signal(PumlSnippets.Group.CLASS, "\\*--|--\\*|o--|--o(?!\\{)", 2),
            new Signal(PumlSnippets.Group.CLASS, "^\\s*interface\\b", 1),
            // アクティビティ図: アクション記法と制御構造。
            new Signal(PumlSnippets.Group.ACTIVITY, "^\\s*:.*;\\s*$", 3),
            new Signal(PumlSnippets.Group.ACTIVITY,
                    "^\\s*(start|stop|detach|kill|backward|repeat|fork(\\s+again)?|split)\\b", 3),
            new Signal(PumlSnippets.Group.ACTIVITY,
                    "^\\s*(if|while|switch|elseif)\\s*\\(|^\\s*(endif|endwhile|endswitch"
                            + "|repeat\\s*while|end\\s+fork|end\\s+split)\\b", 3),
            // 状態遷移図: state 宣言と開始/終了擬似状態。
            new Signal(PumlSnippets.Group.STATE, "^\\s*state\\b", 3),
            new Signal(PumlSnippets.Group.STATE, "\\[\\*\\]", 3),
            new Signal(PumlSnippets.Group.STATE, "^\\s*hide\\s+empty\\s+description\\b", 2),
            // ユースケース図: usecase 宣言・丸括弧のユースケース・include/extend。
            new Signal(PumlSnippets.Group.USECASE, "^\\s*usecase\\b", 3),
            new Signal(PumlSnippets.Group.USECASE, "<<(include|extend)>>", 3),
            new Signal(PumlSnippets.Group.USECASE, "(-->|\\.\\.>|--)\\s*\\(", 2),
            new Signal(PumlSnippets.Group.USECASE, "^\\s*actor\\b", 1),
            // コンポーネント図: component 宣言と [角括弧] 記法。
            new Signal(PumlSnippets.Group.COMPONENT, "^\\s*component\\b", 3),
            new Signal(PumlSnippets.Group.COMPONENT, "\\[\\w[^\\]]*\\]\\s*(-->|--|\\.\\.>)", 3),
            new Signal(PumlSnippets.Group.COMPONENT, "^\\s*(package|frame|rectangle)\\b", 1),
            // ER 図: entity 宣言と多重度記法。
            new Signal(PumlSnippets.Group.ER, "^\\s*entity\\b", 3),
            new Signal(PumlSnippets.Group.ER, "\\|\\|--|--o\\{|\\}o--|\\}\\|--|--\\|\\{", 3),
            // オブジェクト図。
            new Signal(PumlSnippets.Group.OBJECT, "^\\s*object\\b", 3),
            // 配置図: ノード・成果物。
            new Signal(PumlSnippets.Group.DEPLOYMENT,
                    "^\\s*(node|artifact|storage|cloud)\\b", 3),
            new Signal(PumlSnippets.Group.DEPLOYMENT, "^\\s*database\\b", 1),
            // タイミング図: 信号宣言と時刻指定。
            new Signal(PumlSnippets.Group.TIMING, "^\\s*(robust|concise|binary|clock)\\b", 3),
            new Signal(PumlSnippets.Group.TIMING, "^\\s*@\\d+\\b", 3));

    /** ブロック指定子 ({@code @startX}) から図種への対応。 */
    private static final Map<String, PumlSnippets.Group> BLOCKS = Map.of(
            "@startmindmap", PumlSnippets.Group.MINDMAP,
            "@startwbs", PumlSnippets.Group.WBS,
            "@startgantt", PumlSnippets.Group.GANTT,
            "@startsalt", PumlSnippets.Group.SALT,
            "@startjson", PumlSnippets.Group.JSON,
            "@startyaml", PumlSnippets.Group.YAML);

    /** 宣言済みの名前を拾う書式 (クラス・参加者・状態など)。 */
    private static final Pattern DECLARATION = Pattern.compile(
            "^\\s*(?:abstract\\s+)?(?:class|interface|enum|annotation|entity|object|actor"
                    + "|participant|boundary|control|collections|queue|database|usecase"
                    + "|component|node|artifact|storage|cloud|folder|frame|rectangle|card"
                    + "|state|agent|person|robust|concise)\\s+"
                    + "(?:\"([^\"]+)\"|([A-Za-z_][\\w]*))"
                    + "(?:\\s+as\\s+([A-Za-z_][\\w]*))?",
            Pattern.MULTILINE);

    private final String text;
    private final int caret;
    private final String prefix;
    private final String linePrefix;
    private final PumlSnippets.Group flavor;

    private PumlCompletionContext(String text, int caret, String prefix, String linePrefix,
                                  PumlSnippets.Group flavor) {
        this.text = text;
        this.caret = caret;
        this.prefix = prefix;
        this.linePrefix = linePrefix;
        this.flavor = flavor;
    }

    /** {@code caret} 位置の文脈を読み取る。 */
    static PumlCompletionContext at(String text, int caret) {
        String src = text == null ? "" : text;
        int pos = Math.max(0, Math.min(caret, src.length()));
        String prefix = PumlCompletion.wordPrefix(src, pos);
        int wordStart = pos - prefix.length();
        int lineStart = src.lastIndexOf('\n', Math.max(0, wordStart - 1)) + 1;
        String linePrefix = src.substring(lineStart, wordStart);
        return new PumlCompletionContext(src, pos, prefix, linePrefix, detectFlavor(src, pos));
    }

    /** 補完対象の本文全体。 */
    String text() {
        return text;
    }

    /** キャレット直前の補完対象語。 */
    String prefix() {
        return prefix;
    }

    /** 行頭から補完対象語の直前までのテキスト (引数位置の判定に使う)。 */
    String linePrefix() {
        return linePrefix;
    }

    /** 補完対象語が行の先頭トークンか (= 手前が空白だけか)。 */
    boolean atLineStart() {
        return linePrefix.isBlank();
    }

    /** 現在行の字下げ (スニペット展開を現在行に揃えるために使う)。 */
    String indent() {
        int n = 0;
        while (n < linePrefix.length() && Character.isWhitespace(linePrefix.charAt(n))) {
            n++;
        }
        return linePrefix.substring(0, n);
    }

    /** 判定した図種 ({@link PumlSnippets.Group#COMMON} なら不明 = 全図種を平等に扱う)。 */
    PumlSnippets.Group flavor() {
        return flavor;
    }

    /**
     * 本文中で宣言済みの名前 (クラス名・参加者名・別名) を宣言順で返す。
     * 矢印の後などで「相手の名前」を出すのに使う。
     */
    List<String> declaredNames() {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = DECLARATION.matcher(text);
        while (m.find()) {
            // as 別名があればそれが本文中で参照される名前。無ければ宣言名そのもの。
            String alias = m.group(3);
            String quoted = m.group(1);
            String bare = m.group(2);
            if (alias != null) {
                out.add(alias);
            } else if (bare != null) {
                out.add(bare);
            } else if (quoted != null && !quoted.isBlank()) {
                out.add(quoted);
            }
        }
        return new ArrayList<>(out);
    }

    /** テスト用: キャレット位置。 */
    int caretForTest() {
        return caret;
    }

    /**
     * 図種を判定する。ブロック指定子が明示されていればそれを採用し、無ければ
     * 本文の書式シグネチャを数えて最多の図種を返す。決め手が無ければ COMMON。
     */
    private static PumlSnippets.Group detectFlavor(String text, int caret) {
        PumlSnippets.Group block = enclosingBlock(text, caret);
        if (block != null) {
            return block;
        }
        Map<PumlSnippets.Group, Integer> score = new EnumMap<>(PumlSnippets.Group.class);
        for (Signal sig : SIGNALS) {
            Matcher m = sig.pattern.matcher(text);
            int hits = 0;
            while (m.find() && hits < 20) {
                hits++;
            }
            if (hits > 0) {
                score.merge(sig.group, hits * sig.weight, Integer::sum);
            }
        }
        PumlSnippets.Group best = PumlSnippets.Group.COMMON;
        int bestScore = 0;
        int tie = 0;
        for (Map.Entry<PumlSnippets.Group, Integer> e : score.entrySet()) {
            if (e.getValue() > bestScore) {
                best = e.getKey();
                bestScore = e.getValue();
                tie = 0;
            } else if (e.getValue() == bestScore) {
                tie++;
            }
        }
        // 決定的な差が無い (同点 / シグネチャ 1 個だけ) なら決め打ちしない。
        // 誤判定して正しい候補を沈めるより、全候補を平等に出したほうが害が小さい。
        return bestScore >= 3 && tie == 0 ? best : PumlSnippets.Group.COMMON;
    }

    /**
     * キャレットを含むブロックの {@code @startX} 指定子から図種を引く。
     * {@code @startuml} や指定子なしは null (シグネチャ判定へ回す)。
     */
    private static PumlSnippets.Group enclosingBlock(String text, int caret) {
        String head = text.substring(0, caret);
        int at = head.lastIndexOf("@start");
        if (at < 0) {
            return null;
        }
        int end = at;
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))
                || end < text.length() && text.charAt(end) == '@') {
            end++;
        }
        String tag = text.substring(at, end).toLowerCase(Locale.ROOT);
        return BLOCKS.get(tag);
    }
}
