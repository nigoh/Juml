// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * 自由編集エディタ ({@link PumlSourcePanel}) の「挿入パレット」に並べる PlantUML スニペット集。
 *
 * <p>図種別 ({@link Group}) にグループ化した断片を提供し、あらゆる図種の作図を
 * テキストで素早く書けるようにする。本文中の {@code ${caret}} は挿入後のキャレット
 * 位置を表すマーカーで、{@link PumlSourcePanel#insertSnippet(String)} が取り除いて
 * その位置へキャレットを置く (続きをすぐ打てるようにする)。</p>
 */
public final class PumlSnippets {

    /** 挿入後キャレット位置を表すマーカー。 */
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

    /** 1 個のスニペット (グループ + i18n ラベルキー + 挿入本文)。 */
    public static final class Snippet {
        private final Group group;
        private final String labelKey;
        private final String body;

        Snippet(Group group, String labelKey, String body) {
            this.group = group;
            this.labelKey = labelKey;
            this.body = body;
        }

        public Group group() {
            return group;
        }

        /** メニュー表示用の i18n 済みラベル。 */
        public String displayName() {
            return Messages.get(labelKey);
        }

        /** 挿入本文 ({@code ${caret}} マーカーを含みうる)。 */
        public String body() {
            return body;
        }
    }

    private static final List<Snippet> ALL = List.of(
            // クラス図
            s(Group.CLASS, "puml.snip.class.class", "class " + CARET + "Name {\n}\n"),
            s(Group.CLASS, "puml.snip.class.interface", "interface Name {\n}\n"),
            s(Group.CLASS, "puml.snip.class.enum", "enum Name {\n  VALUE_A\n  VALUE_B\n}\n"),
            s(Group.CLASS, "puml.snip.class.extends", "Parent <|-- Child\n"),
            s(Group.CLASS, "puml.snip.class.assoc", "A --> B : label\n"),
            s(Group.CLASS, "puml.snip.class.note", "note right of Name : text\n"),
            // シーケンス図
            s(Group.SEQUENCE, "puml.snip.seq.participant", "participant Name\n"),
            s(Group.SEQUENCE, "puml.snip.seq.message", "A -> B : message()\n"),
            s(Group.SEQUENCE, "puml.snip.seq.alt",
                    "alt condition\n  " + CARET + "\nelse\nend\n"),
            s(Group.SEQUENCE, "puml.snip.seq.loop", "loop times\n  " + CARET + "\nend\n"),
            s(Group.SEQUENCE, "puml.snip.seq.activate",
                    "activate Name\n" + CARET + "\ndeactivate Name\n"),
            s(Group.SEQUENCE, "puml.snip.seq.note", "note over A, B : text\n"),
            // アクティビティ図
            s(Group.ACTIVITY, "puml.snip.act.startStop", "start\n" + CARET + "\nstop\n"),
            s(Group.ACTIVITY, "puml.snip.act.action", ":" + CARET + ";\n"),
            s(Group.ACTIVITY, "puml.snip.act.if",
                    "if (cond?) then (yes)\n  " + CARET + "\nelse (no)\nendif\n"),
            s(Group.ACTIVITY, "puml.snip.act.while",
                    "while (cond?) is (yes)\n  " + CARET + "\nendwhile\n"),
            s(Group.ACTIVITY, "puml.snip.act.fork",
                    "fork\n  " + CARET + "\nfork again\nend fork\n"),
            // 状態図
            s(Group.STATE, "puml.snip.state.state", "state Name\n"),
            s(Group.STATE, "puml.snip.state.transition", "State1 --> State2 : event\n"),
            s(Group.STATE, "puml.snip.state.initial", "[*] --> " + CARET + "State\n"),
            s(Group.STATE, "puml.snip.state.composite",
                    "state Composite {\n  [*] --> " + CARET + "Sub\n}\n"),
            // ユースケース図
            s(Group.USECASE, "puml.snip.uc.actor", "actor " + CARET + "User\n"),
            s(Group.USECASE, "puml.snip.uc.usecase", "usecase (" + CARET + "Do Something)\n"),
            s(Group.USECASE, "puml.snip.uc.link", "User --> (Do Something)\n"),
            s(Group.USECASE, "puml.snip.uc.include",
                    "(A) ..> (B) : " + CARET + "<<include>>\n"),
            // コンポーネント図
            s(Group.COMPONENT, "puml.snip.comp.component", "component " + CARET + "Name\n"),
            s(Group.COMPONENT, "puml.snip.comp.interface", "interface \"API\" as I\n"),
            s(Group.COMPONENT, "puml.snip.comp.connect", "[A] --> [B]\n"),
            s(Group.COMPONENT, "puml.snip.comp.package",
                    "package \"" + CARET + "Group\" {\n  [Comp]\n}\n"),
            // ER 図 (IE 記法)
            s(Group.ER, "puml.snip.er.entity",
                    "entity \"" + CARET + "Name\" as e {\n  * id : int\n  --\n  name : string\n}\n"),
            s(Group.ER, "puml.snip.er.relation", "e1 ||--o{ e2 : has\n"),
            // オブジェクト図
            s(Group.OBJECT, "puml.snip.obj.object", "object " + CARET + "Name\n"),
            s(Group.OBJECT, "puml.snip.obj.attribute", "Name : field = \"value\"\n"),
            s(Group.OBJECT, "puml.snip.obj.link", "Obj1 --> Obj2\n"),
            // デプロイ図
            s(Group.DEPLOYMENT, "puml.snip.depl.node",
                    "node \"" + CARET + "Server\" {\n  artifact app\n}\n"),
            s(Group.DEPLOYMENT, "puml.snip.depl.database", "database DB\n"),
            s(Group.DEPLOYMENT, "puml.snip.depl.cloud", "cloud \"Cloud\" {\n  " + CARET + "\n}\n"),
            s(Group.DEPLOYMENT, "puml.snip.depl.link", "Server --> DB\n"),
            // タイミング図
            s(Group.TIMING, "puml.snip.tim.robust", "robust \"" + CARET + "Signal\" as R\n"),
            s(Group.TIMING, "puml.snip.tim.concise", "concise \"User\" as U\n"),
            s(Group.TIMING, "puml.snip.tim.state", "@0\n" + CARET + "R is Idle\n@100\nR is Busy\n"),
            // JSON
            s(Group.JSON, "puml.snip.json.object",
                    "{\n  \"" + CARET + "key\": \"value\",\n  \"items\": [1, 2, 3]\n}\n"),
            // YAML
            s(Group.YAML, "puml.snip.yaml.mapping",
                    CARET + "key: value\nlist:\n  - a\n  - b\n"),
            // マインドマップ
            s(Group.MINDMAP, "puml.snip.mm.orgmode",
                    "* " + CARET + "Root\n** Branch A\n*** Leaf\n** Branch B\n"),
            s(Group.MINDMAP, "puml.snip.mm.markdown", "+ Root\n++ Child\n-- Left Child\n"),
            // WBS
            s(Group.WBS, "puml.snip.wbs.node",
                    "* " + CARET + "Project\n** Phase 1\n*** Task A\n** Phase 2\n"),
            // ガント
            s(Group.GANTT, "puml.snip.gantt.task", "[" + CARET + "Task] lasts 5 days\n"),
            s(Group.GANTT, "puml.snip.gantt.depend", "[Next] starts at [Task]'s end\n"),
            s(Group.GANTT, "puml.snip.gantt.milestone",
                    "[Milestone] happens at [Task]'s end\n"),
            // ワイヤーフレーム (salt)
            s(Group.SALT, "puml.snip.salt.buttons", "{\n  [" + CARET + "OK] | [Cancel]\n}\n"),
            s(Group.SALT, "puml.snip.salt.form",
                    "{\n  Name  | \"          \"\n  Passwd| \"****      \"\n  [Login]\n}\n"),
            s(Group.SALT, "puml.snip.salt.checkbox", "{\n  [X] Enabled\n  [ ] Disabled\n}\n"),
            // 共通
            s(Group.COMMON, "puml.snip.common.title", "title " + CARET + "My Diagram\n"),
            s(Group.COMMON, "puml.snip.common.note", "note as N\n  text\nend note\n"),
            s(Group.COMMON, "puml.snip.common.legend", "legend right\n  text\nendlegend\n"),
            s(Group.COMMON, "puml.snip.common.skinparam",
                    "skinparam backgroundColor #FEFEFE\n"),
            s(Group.COMMON, "puml.snip.common.comment", "' " + CARET + "comment\n"));

    private static Snippet s(Group g, String labelKey, String body) {
        return new Snippet(g, labelKey, body);
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

    /** すべてのスニペット (テスト用)。 */
    public static List<Snippet> all() {
        return ALL;
    }

    private PumlSnippets() {
    }
}
