// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * 自由編集エディタ ({@link PumlSourcePanel}) の PlantUML スニペット集。
 *
 * <p>2 つの導線から使う: 「挿入パレット」ボタン (図種別のサブメニュー) と、
 * 入力補完ポップアップ ({@link PumlCompletion})。後者では {@link Snippet#trigger()} の
 * 語を打つだけで雛形が丸ごと展開されるため、打鍵数が最も減る。</p>
 *
 * <p>本文は {@link PumlSnippetTemplate} のプレースホルダ記法を使う。
 * {@code ${1:Name}} のようなタブストップは挿入直後に選択状態になり、そのまま打てば
 * 置き換わる。{@code Tab} で次のタブストップへ送れるので、雛形の穴だけを順に
 * 埋めていける。互換のため {@code ${caret}} (終端キャレット) も解釈する。</p>
 */
public final class PumlSnippets {

    /** 挿入後キャレット位置を表すマーカー ({@code ${0}} と同義の互換記法)。 */
    public static final String CARET = "${caret}";

    /** スニペットの図種グループ (パレットのサブメニュー見出し)。 */
    public enum Group {
        CLASS("puml.snip.cat.class"),
        SEQUENCE("puml.snip.cat.sequence"),
        ACTIVITY("puml.snip.cat.activity"),
        STATE("puml.snip.cat.state"),
        USECASE("puml.snip.cat.usecase"),
        COMPONENT("puml.snip.cat.component"),
        ER("puml.snip.cat.er"),
        OBJECT("puml.snip.cat.object"),
        DEPLOYMENT("puml.snip.cat.deployment"),
        TIMING("puml.snip.cat.timing"),
        JSON("puml.snip.cat.json"),
        YAML("puml.snip.cat.yaml"),
        MINDMAP("puml.snip.cat.mindmap"),
        WBS("puml.snip.cat.wbs"),
        GANTT("puml.snip.cat.gantt"),
        SALT("puml.snip.cat.salt"),
        COMMON("puml.snip.cat.common");

        private final String labelKey;

        Group(String labelKey) {
            this.labelKey = labelKey;
        }

        /** メニュー表示用の i18n 済みグループ名。 */
        public String displayName() {
            return Messages.get(labelKey);
        }
    }

    /** 1 個のスニペット (グループ + i18n ラベルキー + 補完トリガ語 + 挿入本文)。 */
    public static final class Snippet {
        private final Group group;
        private final String labelKey;
        private final String trigger;
        private final String body;

        Snippet(Group group, String labelKey, String trigger, String body) {
            this.group = group;
            this.labelKey = labelKey;
            this.trigger = trigger;
            this.body = body;
        }

        public Group group() {
            return group;
        }

        /** メニュー表示用の i18n 済みラベル。 */
        public String displayName() {
            return Messages.get(labelKey);
        }

        /**
         * 入力補完でこのスニペットを引き当てる語。ポップアップの見出しにもなる。
         * 打鍵数を減らすため、可能なかぎり PlantUML の実キーワードに合わせている。
         */
        public String trigger() {
            return trigger;
        }

        /** 挿入本文 ({@link PumlSnippetTemplate} のプレースホルダを含みうる)。 */
        public String body() {
            return body;
        }
    }

    private static final List<Snippet> ALL = List.of(
            // クラス図
            s(Group.CLASS, "puml.snip.class.class", "class",
                    "class ${1:Name} {\n  ${0}\n}\n"),
            s(Group.CLASS, "puml.snip.class.interface", "interface",
                    "interface ${1:Name} {\n  ${0}\n}\n"),
            s(Group.CLASS, "puml.snip.class.enum", "enum",
                    "enum ${1:Name} {\n  ${2:VALUE_A}\n  ${3:VALUE_B}\n}\n"),
            s(Group.CLASS, "puml.snip.class.extends", "extends",
                    "${1:Parent} <|-- ${2:Child}\n"),
            s(Group.CLASS, "puml.snip.class.assoc", "assoc",
                    "${1:A} --> ${2:B} : ${3:label}\n"),
            s(Group.CLASS, "puml.snip.class.note", "note",
                    "note right of ${1:Name} : ${2:text}\n"),
            // シーケンス図
            s(Group.SEQUENCE, "puml.snip.seq.participant", "participant",
                    "participant ${1:Name}\n"),
            s(Group.SEQUENCE, "puml.snip.seq.message", "message",
                    "${1:A} -> ${2:B} : ${3:message()}\n"),
            s(Group.SEQUENCE, "puml.snip.seq.alt", "alt",
                    "alt ${1:condition}\n  ${2}\nelse ${3:otherwise}\n  ${4}\nend\n"),
            s(Group.SEQUENCE, "puml.snip.seq.loop", "loop",
                    "loop ${1:times}\n  ${0}\nend\n"),
            s(Group.SEQUENCE, "puml.snip.seq.activate", "activate",
                    "activate ${1:Name}\n  ${2}\ndeactivate ${3:Name}\n"),
            s(Group.SEQUENCE, "puml.snip.seq.note", "note",
                    "note over ${1:A}, ${2:B} : ${3:text}\n"),
            // アクティビティ図
            s(Group.ACTIVITY, "puml.snip.act.startStop", "start",
                    "start\n${1}\nstop\n"),
            s(Group.ACTIVITY, "puml.snip.act.action", "action",
                    ":${1:action};\n"),
            s(Group.ACTIVITY, "puml.snip.act.if", "if",
                    "if (${1:cond?}) then (${2:yes})\n  ${3}\nelse (${4:no})\n  ${5}\nendif\n"),
            s(Group.ACTIVITY, "puml.snip.act.while", "while",
                    "while (${1:cond?}) is (${2:yes})\n  ${3}\nendwhile\n"),
            s(Group.ACTIVITY, "puml.snip.act.fork", "fork",
                    "fork\n  ${1}\nfork again\n  ${2}\nend fork\n"),
            // 状態図
            s(Group.STATE, "puml.snip.state.state", "state",
                    "state ${1:Name}\n"),
            s(Group.STATE, "puml.snip.state.transition", "transition",
                    "${1:State1} --> ${2:State2} : ${3:event}\n"),
            s(Group.STATE, "puml.snip.state.initial", "initial",
                    "[*] --> ${1:State}\n"),
            s(Group.STATE, "puml.snip.state.composite", "composite",
                    "state ${1:Composite} {\n  [*] --> ${2:Sub}\n}\n"),
            // ユースケース図
            s(Group.USECASE, "puml.snip.uc.actor", "actor",
                    "actor ${1:User}\n"),
            s(Group.USECASE, "puml.snip.uc.usecase", "usecase",
                    "usecase (${1:Do Something})\n"),
            s(Group.USECASE, "puml.snip.uc.link", "link",
                    "${1:User} --> (${2:Do Something})\n"),
            s(Group.USECASE, "puml.snip.uc.include", "include",
                    "(${1:A}) ..> (${2:B}) : <<include>>\n"),
            // コンポーネント図
            s(Group.COMPONENT, "puml.snip.comp.component", "component",
                    "component ${1:Name}\n"),
            s(Group.COMPONENT, "puml.snip.comp.interface", "interface",
                    "interface \"${1:API}\" as ${2:I}\n"),
            s(Group.COMPONENT, "puml.snip.comp.connect", "connect",
                    "[${1:A}] --> [${2:B}]\n"),
            s(Group.COMPONENT, "puml.snip.comp.package", "package",
                    "package \"${1:Group}\" {\n  [${2:Comp}]\n}\n"),
            // ER 図 (IE 記法)
            s(Group.ER, "puml.snip.er.entity", "entity",
                    "entity \"${1:Name}\" as ${2:e} {\n  * ${3:id} : int\n  --\n"
                            + "  ${4:name} : string\n}\n"),
            s(Group.ER, "puml.snip.er.relation", "relation",
                    "${1:e1} ||--o{ ${2:e2} : ${3:has}\n"),
            // オブジェクト図
            s(Group.OBJECT, "puml.snip.obj.object", "object",
                    "object ${1:Name}\n"),
            s(Group.OBJECT, "puml.snip.obj.attribute", "attribute",
                    "${1:Name} : ${2:field} = \"${3:value}\"\n"),
            s(Group.OBJECT, "puml.snip.obj.link", "olink",
                    "${1:Obj1} --> ${2:Obj2}\n"),
            // デプロイ図
            s(Group.DEPLOYMENT, "puml.snip.depl.node", "node",
                    "node \"${1:Server}\" {\n  artifact ${2:app}\n}\n"),
            s(Group.DEPLOYMENT, "puml.snip.depl.database", "database",
                    "database ${1:DB}\n"),
            s(Group.DEPLOYMENT, "puml.snip.depl.cloud", "cloud",
                    "cloud \"${1:Cloud}\" {\n  ${2}\n}\n"),
            s(Group.DEPLOYMENT, "puml.snip.depl.link", "dlink",
                    "${1:Server} --> ${2:DB}\n"),
            // タイミング図
            s(Group.TIMING, "puml.snip.tim.robust", "robust",
                    "robust \"${1:Signal}\" as ${2:R}\n"),
            s(Group.TIMING, "puml.snip.tim.concise", "concise",
                    "concise \"${1:User}\" as ${2:U}\n"),
            s(Group.TIMING, "puml.snip.tim.state", "timeline",
                    "@0\n${1:R} is ${2:Idle}\n@100\n${3:R} is ${4:Busy}\n"),
            // JSON
            s(Group.JSON, "puml.snip.json.object", "json",
                    "{\n  \"${1:key}\": \"${2:value}\",\n  \"${3:items}\": [1, 2, 3]\n}\n"),
            // YAML
            s(Group.YAML, "puml.snip.yaml.mapping", "yaml",
                    "${1:key}: ${2:value}\nlist:\n  - ${3:a}\n  - ${4:b}\n"),
            // マインドマップ
            s(Group.MINDMAP, "puml.snip.mm.orgmode", "mindmap",
                    "* ${1:Root}\n** ${2:Branch A}\n*** ${3:Leaf}\n** ${4:Branch B}\n"),
            s(Group.MINDMAP, "puml.snip.mm.markdown", "mmarkdown",
                    "+ ${1:Root}\n++ ${2:Child}\n-- ${3:Left Child}\n"),
            // WBS
            s(Group.WBS, "puml.snip.wbs.node", "wbs",
                    "* ${1:Project}\n** ${2:Phase 1}\n*** ${3:Task A}\n** ${4:Phase 2}\n"),
            // ガント
            s(Group.GANTT, "puml.snip.gantt.task", "task",
                    "[${1:Task}] lasts ${2:5} days\n"),
            s(Group.GANTT, "puml.snip.gantt.depend", "depend",
                    "[${1:Next}] starts at [${2:Task}]'s end\n"),
            s(Group.GANTT, "puml.snip.gantt.milestone", "milestone",
                    "[${1:Milestone}] happens at [${2:Task}]'s end\n"),
            // ワイヤーフレーム (salt)
            s(Group.SALT, "puml.snip.salt.buttons", "buttons",
                    "{\n  [${1:OK}] | [${2:Cancel}]\n}\n"),
            s(Group.SALT, "puml.snip.salt.form", "form",
                    "{\n  ${1:Name}  | \"          \"\n  ${2:Passwd}| \"****      \"\n"
                            + "  [${3:Login}]\n}\n"),
            s(Group.SALT, "puml.snip.salt.checkbox", "checkbox",
                    "{\n  [X] ${1:Enabled}\n  [ ] ${2:Disabled}\n}\n"),
            // 共通
            s(Group.COMMON, "puml.snip.common.title", "title",
                    "title ${1:My Diagram}\n"),
            s(Group.COMMON, "puml.snip.common.note", "noteblock",
                    "note as ${1:N}\n  ${2:text}\nend note\n"),
            s(Group.COMMON, "puml.snip.common.legend", "legend",
                    "legend right\n  ${1:text}\nendlegend\n"),
            s(Group.COMMON, "puml.snip.common.skinparam", "skinparam",
                    "skinparam ${1:backgroundColor} ${2:#FEFEFE}\n"),
            s(Group.COMMON, "puml.snip.common.comment", "comment",
                    "' ${1:comment}\n"));

    private static Snippet s(Group g, String labelKey, String trigger, String body) {
        return new Snippet(g, labelKey, trigger, body);
    }

    /** 指定グループのスニペットを宣言順で返す。 */
    public static List<Snippet> forGroup(Group group) {
        List<Snippet> out = new ArrayList<>();
        for (Snippet snip : ALL) {
            if (snip.group() == group) {
                out.add(snip);
            }
        }
        return out;
    }

    /** すべてのスニペット (補完辞書・テスト用)。 */
    public static List<Snippet> all() {
        return ALL;
    }

    private PumlSnippets() {
    }
}
