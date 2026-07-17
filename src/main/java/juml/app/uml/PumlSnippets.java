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
