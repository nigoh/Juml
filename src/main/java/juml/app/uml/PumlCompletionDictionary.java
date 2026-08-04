// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PumlSnippets.Group;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 入力補完の静的辞書 (キーワード・矢印記法・引数値)。データのみを持ち、
 * 絞り込み/並べ替えは {@link PumlCompletion} が担う。
 *
 * <p>各キーワードには「どの図種で使うか」を持たせている。書いている図種で使わない語を
 * 下げ、使う語を上げるための材料で、これが候補一覧を短く保ち矢印キーの打鍵を減らす。
 * 複数図種で使う語 ({@code actor} など) は該当図種をすべて登録し、どちらでも沈まない
 * ようにする。{@link Group#COMMON} は「全図種で有効」を意味する。</p>
 *
 * <p>表示用の文言は i18n されるため、辞書はメッセージ<em>キー</em>だけを保持し、
 * 実文字列の解決は候補生成時に行う (言語切替後も追従させるため)。</p>
 */
final class PumlCompletionDictionary {

    /** 辞書 1 語 (語 + 有効な図種 + 補足のメッセージキー)。 */
    static final class Entry {
        private final String word;
        private final Set<Group> groups;
        private final String detailKey;

        Entry(String word, Set<Group> groups, String detailKey) {
            this.word = word;
            this.groups = groups;
            this.detailKey = detailKey;
        }

        String word() {
            return word;
        }

        Set<Group> groups() {
            return groups;
        }

        /** 補足文言のメッセージキー。null なら代表図種名を補足に使う。 */
        String detailKey() {
            return detailKey;
        }

        /** この語が {@code flavor} の図種で使われるか (COMMON は常に真)。 */
        boolean appliesTo(Group flavor) {
            return groups.contains(Group.COMMON) || groups.contains(flavor);
        }

        /** 補足に使う代表図種 (COMMON を含むなら COMMON)。 */
        Group primaryGroup() {
            if (groups.contains(Group.COMMON)) {
                return Group.COMMON;
            }
            return groups.isEmpty() ? Group.COMMON : groups.iterator().next();
        }
    }

    /**
     * {@code !theme} の引数に出すテーマ名。
     *
     * <p>キーワード表 ({@link #KEYWORD_GROUPS}) の構築がこれを読むため、
     * 静的初期化の順序上、必ずキーワード表より前に宣言しておく必要がある。</p>
     */
    private static final List<String> THEMES = List.of(
            "plain", "cerulean", "materia", "sketchy", "spacelab", "superhero", "united",
            "cyborg", "hacker", "sandstone", "silver", "toy", "vibrant", "bluegray",
            "blueprint", "mimeograph", "reddress-darkblue", "amiga", "aws-orange",
            "black-knight", "carbon-gray", "crt-amber", "crt-green", "lightgray",
            "metal", "mars", "minty", "sunlust");

    /** {@code skinparam} の引数に出す主要属性。 */
    private static final List<String> SKINPARAMS = List.of(
            "backgroundColor", "defaultFontName", "defaultFontSize", "defaultFontColor",
            "roundcorner", "handwritten", "monochrome", "shadowing", "dpi", "linetype",
            "nodesep", "ranksep", "padding", "ArrowColor", "BorderColor", "FontColor",
            "FontSize", "FontName", "FontStyle", "componentStyle", "sequenceMessageAlign",
            "wrapWidth", "ortho", "polyline", "classAttributeIconSize", "hyperlinkColor",
            "responseMessageBelowArrow", "maxMessageSize", "participantPadding",
            "boxPadding", "titleFontSize", "noteBackgroundColor", "noteBorderColor",
            "stereotypeCBackgroundColor", "packageStyle", "actorStyle", "usecaseStyle");

    /** {@code note} の直後に来る位置指定。 */
    private static final List<String> NOTE_POSITIONS = List.of(
            "over", "left of", "right of", "top of", "bottom of", "as");

    /** {@code hide} / {@code show} の対象指定。 */
    private static final List<String> HIDE_TARGETS = List.of(
            "empty members", "empty description", "empty attributes", "empty methods",
            "members", "attributes", "methods", "circle", "stereotype", "footbox",
            "unlinked");

    /**
     * {@code <<...>>} の中に書くステレオタイプ。閉じ {@code >>} まで入れて
     * 打鍵を最後まで肩代わりする。
     */
    private static final List<String> STEREOTYPES = List.of(
            "include>>", "extend>>", "interface>>", "abstract>>", "enumeration>>",
            "actor>>", "entity>>", "boundary>>", "control>>", "singleton>>");

    /**
     * 引数位置の候補規則。行の状況が {@link ArgRule#matches} に当てはまったら、
     * その候補<em>だけ</em>を出す。{@code !theme } の後にクラス図のキーワードが
     * 並んでも選べないので、混ぜずに絞り込む方が速い。
     */
    static final class ArgRule {
        private final Pattern linePattern;
        private final List<String> values;
        private final String detailKey;

        ArgRule(String regex, List<String> values, String detailKey) {
            this.linePattern = Pattern.compile(regex);
            this.values = values;
            this.detailKey = detailKey;
        }

        /** {@code line} (行頭から打ちかけの語の末尾まで) がこの引数位置か。 */
        boolean matches(String line) {
            return linePattern.matcher(line).matches();
        }

        List<String> values() {
            return values;
        }

        String detailKey() {
            return detailKey;
        }
    }

    private static final List<ArgRule> ARG_RULES = List.of(
            new ArgRule("(?i)\\s*!theme\\s+\\S*", THEMES,
                    "puml.completion.detail.theme"),
            new ArgRule("(?i)\\s*skinparam\\s+\\S*", SKINPARAMS,
                    "puml.completion.detail.skinparam"),
            new ArgRule("(?i)\\s*note\\s+\\S*", NOTE_POSITIONS,
                    "puml.completion.detail.notePos"),
            new ArgRule("(?i)\\s*(hide|show)\\s+\\S*", HIDE_TARGETS,
                    "puml.completion.detail.hideTarget"),
            // ステレオタイプは行頭に限らず現れるので、行末が << で始まる語かで見る。
            new ArgRule(".*<<\\w*", STEREOTYPES,
                    "puml.completion.detail.stereotype"));

    /** 引数位置の候補規則 (宣言順に評価する)。 */
    static List<ArgRule> argRules() {
        return ARG_RULES;
    }

    private static final Map<String, Set<Group>> KEYWORD_GROUPS = buildKeywordGroups();

    private static final List<Entry> KEYWORDS = buildKeywordEntries();

    private static final List<Entry> ARROWS = buildArrows();

    private PumlCompletionDictionary() {
    }

    /** 全キーワード (宣言順)。 */
    static List<Entry> keywords() {
        return KEYWORDS;
    }

    /** 語 → 有効図種の索引 (未知語は null)。 */
    static Set<Group> groupsOf(String word) {
        return KEYWORD_GROUPS.get(word);
    }

    /** 矢印記法の候補。 */
    static List<Entry> arrows() {
        return ARROWS;
    }

    /** {@code !theme} の引数候補。 */
    static List<String> themes() {
        return THEMES;
    }

    /** {@code skinparam} の引数候補。 */
    static List<String> skinparams() {
        return SKINPARAMS;
    }

    /** 引数値としてだけ意味を持つ語か (通常の補完では下げる)。 */
    static boolean isValueWord(String word) {
        return THEMES.contains(word) || SKINPARAMS.contains(word);
    }

    private static List<Entry> buildKeywordEntries() {
        List<Entry> out = new ArrayList<>(KEYWORD_GROUPS.size());
        for (Map.Entry<String, Set<Group>> e : KEYWORD_GROUPS.entrySet()) {
            out.add(new Entry(e.getKey(), e.getValue(), null));
        }
        return List.copyOf(out);
    }

    private static Map<String, Set<Group>> buildKeywordGroups() {
        Map<String, Set<Group>> m = new LinkedHashMap<>();
        // 宣言順がそのまま「図種が判らないときの既定の並び」になるため、日常的に打つ
        // 構造キーワードを先に、めったに使わないブロック指定子や引数値を後ろに置く。
        add(m, Group.COMMON, "@startuml", "@enduml");
        // クラス図。
        add(m, Group.CLASS, "class", "abstract", "enum", "annotation", "extends",
                "implements", "circle", "diamond", "allowmixing", "allow_mixing");
        // インタフェースはクラス図とコンポーネント図の双方で使う。
        add(m, Group.CLASS, "interface");
        add(m, Group.COMPONENT, "interface");
        // オブジェクト図 / ER 図。
        add(m, Group.OBJECT, "object", "map");
        add(m, Group.ER, "entity");
        // シーケンス図。actor / entity / database / queue / collections は参加者としても使う。
        add(m, Group.SEQUENCE, "participant", "actor", "boundary", "control",
                "collections", "activate", "deactivate", "autonumber", "create",
                "destroy", "over", "ref", "hnote", "rnote", "box", "endbox",
                "mainframe", "database", "queue", "return");
        add(m, Group.SEQUENCE, "alt", "opt", "loop", "par", "break", "critical", "group",
                "else", "end");
        // アクティビティ図。
        add(m, Group.ACTIVITY, "start", "stop", "end", "if", "then", "elseif", "endif",
                "repeat", "repeatwhile", "while", "endwhile", "fork", "forkagain",
                "split", "partition", "switch", "case", "endswitch", "detach", "kill",
                "label", "goto", "backward", "else", "return");
        // 状態遷移図。
        add(m, Group.STATE, "state", "choice", "fork", "join", "history", "concurrent");
        // ユースケース図。
        add(m, Group.USECASE, "usecase", "agent", "person", "actor");
        // コンポーネント図 / パッケージ。
        add(m, Group.COMPONENT, "component", "package", "namespace", "rectangle", "card",
                "frame", "port", "portin", "portout");
        // 配置図。
        add(m, Group.DEPLOYMENT, "node", "artifact", "storage", "cloud", "folder",
                "database", "queue", "stack");
        // タイミング図。
        add(m, Group.TIMING, "concise", "robust", "binary", "clock", "highlight");
        // 全図種で使う装飾・レイアウト・メタ指定。
        add(m, Group.COMMON, "note", "as", "link", "title", "header", "footer", "legend",
                "endlegend", "caption", "scale", "hide", "show", "remove", "restore",
                "together", "left", "right", "top", "bottom", "up", "down", "direction",
                "order", "newpage", "archimate", "skinparam", "sprite", "style",
                "endstyle", "also", "of", "on", "is", "with");
        // プリプロセッサ・ディレクティブ。
        add(m, Group.COMMON, "!include", "!includesub", "!includeurl", "!import",
                "!define", "!undef", "!definelong", "!enddefinelong", "!if", "!elseif",
                "!else", "!endif", "!ifdef", "!ifndef", "!while", "!endwhile",
                "!function", "!endfunction", "!procedure", "!endprocedure", "!return",
                "!theme", "!pragma", "!log", "!assert", "!unquoted", "!startsub",
                "!endsub", "!local", "!global", "!dump_memory");
        // 図種別のブロック指定子 (打つのは 1 図につき 1 回なので後ろで良い)。
        add(m, Group.MINDMAP, "@startmindmap", "@endmindmap");
        add(m, Group.WBS, "@startwbs", "@endwbs");
        add(m, Group.GANTT, "@startgantt", "@endgantt");
        add(m, Group.SALT, "@startsalt", "@endsalt", "salt");
        add(m, Group.JSON, "@startjson", "@endjson");
        add(m, Group.YAML, "@startyaml", "@endyaml");
        add(m, Group.COMMON, "@startdot", "@enddot", "@startditaa", "@endditaa",
                "@startmath", "@endmath", "@startlatex", "@endlatex",
                "@startchen", "@endchen");
        // Map.copyOf は反復順を保証しない。宣言順が既定の候補順になるので
        // LinkedHashMap のまま読み取り専用にする。
        // 引数値として使う語も辞書に載せる (打ち始めから引けるようにする)。
        // 通常文脈では PumlCompletion 側で順位を下げ、対応する指示語の後でだけ押し上げる。
        for (String t : THEMES) {
            add(m, Group.COMMON, t);
        }
        for (String p : SKINPARAMS) {
            add(m, Group.COMMON, p);
        }
        return java.util.Collections.unmodifiableMap(m);
    }

    private static void add(Map<String, Set<Group>> m, Group g, String... words) {
        for (String w : words) {
            m.computeIfAbsent(w, k -> EnumSet.noneOf(Group.class)).add(g);
        }
    }

    /**
     * 矢印記法。図種ごとに意味が違うので、意味の説明 (メッセージキー) を必ず持たせる
     * ({@code <|--} と {@code *--} の違いは覚えにくく、説明が無いと候補として役に立たない)。
     */
    private static List<Entry> buildArrows() {
        List<Entry> out = new ArrayList<>();
        // クラス図。親→子 (<|--) と子→親 (--|>) の双方を載せる。どちらの向きで
        // 書くかは書き手の癖なので、片方だけだと「打ちかけたが候補が出ない」になる。
        arrow(out, "<|--", Group.CLASS, "puml.arrow.inherit");
        arrow(out, "--|>", Group.CLASS, "puml.arrow.inherit");
        arrow(out, "<|..", Group.CLASS, "puml.arrow.realize");
        arrow(out, "..|>", Group.CLASS, "puml.arrow.realize");
        arrow(out, "*--", Group.CLASS, "puml.arrow.compose");
        arrow(out, "--*", Group.CLASS, "puml.arrow.compose");
        arrow(out, "o--", Group.CLASS, "puml.arrow.aggregate");
        arrow(out, "--o", Group.CLASS, "puml.arrow.aggregate");
        // -->/<-- は関連 (クラス図) と遷移 (状態図) の双方で日常的に使う。
        arrow(out, "-->", "puml.arrow.assoc", Group.CLASS, Group.STATE,
                Group.ACTIVITY, Group.USECASE, Group.COMPONENT);
        arrow(out, "..>", "puml.arrow.depend", Group.CLASS, Group.USECASE,
                Group.COMPONENT);
        arrow(out, "<..", Group.CLASS, "puml.arrow.depend");
        arrow(out, "--", "puml.arrow.link", Group.CLASS, Group.USECASE,
                Group.COMPONENT, Group.DEPLOYMENT);
        arrow(out, "..", Group.CLASS, "puml.arrow.dotted");
        arrow(out, "->", Group.SEQUENCE, "puml.arrow.sync");
        arrow(out, "->>", Group.SEQUENCE, "puml.arrow.async");
        arrow(out, "-->>", Group.SEQUENCE, "puml.arrow.asyncReply");
        arrow(out, "<--", "puml.arrow.reply", Group.SEQUENCE, Group.CLASS);
        arrow(out, "->x", Group.SEQUENCE, "puml.arrow.lost");
        arrow(out, "->o", Group.SEQUENCE, "puml.arrow.found");
        arrow(out, "-\\", Group.SEQUENCE, "puml.arrow.half");
        arrow(out, "-[#red]>", Group.SEQUENCE, "puml.arrow.colored");
        arrow(out, "-down->", Group.STATE, "puml.arrow.dir");
        arrow(out, "-right->", Group.STATE, "puml.arrow.dir");
        arrow(out, "-left->", Group.STATE, "puml.arrow.dir");
        arrow(out, "-up->", Group.STATE, "puml.arrow.dir");
        arrow(out, "||--||", Group.ER, "puml.arrow.erOneOne");
        arrow(out, "||--o{", Group.ER, "puml.arrow.erOneMany");
        arrow(out, "}o--o{", Group.ER, "puml.arrow.erManyMany");
        arrow(out, "}|--||", Group.ER, "puml.arrow.erManyOne");
        return List.copyOf(out);
    }

    private static void arrow(List<Entry> out, String glyph, Group group, String detailKey) {
        arrow(out, glyph, detailKey, group);
    }

    /** 同じ記法を複数図種で使う場合は、該当図種をすべて挙げてどこでも沈まないようにする。 */
    private static void arrow(List<Entry> out, String glyph, String detailKey,
                              Group... groups) {
        out.add(new Entry(glyph, EnumSet.copyOf(List.of(groups)), detailKey));
    }
}
