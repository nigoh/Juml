// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PumlSnippets.Group;
import juml.util.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * 選択した行を丸ごと囲むためのブロック雛形。
 *
 * <p>スニペット ({@link PumlSnippets}) が「これから書く」ためのものなのに対し、
 * こちらは「もう書いた行を後から括る」ためのもの。図を書き進めてから
 * 「ここは条件分岐だった」と気づく場面は多く、そのたびに {@code alt} と {@code end} を
 * 手で足して中身を字下げし直すのは、まさに減らしたい打鍵にあたる。</p>
 *
 * <p>本文は {@link PumlSnippetTemplate} の記法で、{@code ${SELECTION}} が選択テキストの
 * 差し込み位置。選択は marker の桁に合わせて字下げし直される。</p>
 */
final class PumlSurrounds {

    /** 囲みブロック 1 種 (図種 + i18n ラベルキー + 本文)。 */
    static final class Surround {
        private final Group group;
        private final String labelKey;
        private final String body;

        Surround(Group group, String labelKey, String body) {
            this.group = group;
            this.labelKey = labelKey;
            this.body = body;
        }

        /** 想定する図種 ({@link Group#COMMON} は全図種)。並び順の決定に使う。 */
        Group group() {
            return group;
        }

        /** メニュー表示用の i18n 済みラベル。 */
        String displayName() {
            return Messages.get(labelKey);
        }

        /** 挿入本文 ({@code ${SELECTION}} を必ず含む)。 */
        String body() {
            return body;
        }
    }

    private static final List<Surround> ALL = List.of(
            // シーケンス図の複合フラグメント。
            s(Group.SEQUENCE, "puml.surround.alt",
                    "alt ${1:condition}\n  ${SELECTION}\nelse ${2:otherwise}\nend\n"),
            s(Group.SEQUENCE, "puml.surround.opt",
                    "opt ${1:condition}\n  ${SELECTION}\nend\n"),
            s(Group.SEQUENCE, "puml.surround.loop",
                    "loop ${1:times}\n  ${SELECTION}\nend\n"),
            s(Group.SEQUENCE, "puml.surround.par",
                    "par\n  ${SELECTION}\nelse\nend\n"),
            s(Group.SEQUENCE, "puml.surround.group",
                    "group ${1:label}\n  ${SELECTION}\nend\n"),
            s(Group.SEQUENCE, "puml.surround.critical",
                    "critical ${1:label}\n  ${SELECTION}\nend\n"),
            s(Group.SEQUENCE, "puml.surround.box",
                    "box \"${1:name}\"\n  ${SELECTION}\nend box\n"),
            // アクティビティ図の制御構造。
            s(Group.ACTIVITY, "puml.surround.if",
                    "if (${1:cond?}) then (${2:yes})\n  ${SELECTION}\nendif\n"),
            s(Group.ACTIVITY, "puml.surround.while",
                    "while (${1:cond?}) is (${2:yes})\n  ${SELECTION}\nendwhile\n"),
            s(Group.ACTIVITY, "puml.surround.repeat",
                    "repeat\n  ${SELECTION}\nrepeat while (${1:cond?})\n"),
            s(Group.ACTIVITY, "puml.surround.fork",
                    "fork\n  ${SELECTION}\nfork again\nend fork\n"),
            s(Group.ACTIVITY, "puml.surround.partition",
                    "partition ${1:name} {\n  ${SELECTION}\n}\n"),
            // 構造図のまとまり。
            s(Group.COMPONENT, "puml.surround.package",
                    "package \"${1:name}\" {\n  ${SELECTION}\n}\n"),
            s(Group.COMPONENT, "puml.surround.rectangle",
                    "rectangle \"${1:name}\" {\n  ${SELECTION}\n}\n"),
            s(Group.DEPLOYMENT, "puml.surround.node",
                    "node \"${1:name}\" {\n  ${SELECTION}\n}\n"),
            s(Group.STATE, "puml.surround.state",
                    "state ${1:name} {\n  ${SELECTION}\n}\n"),
            // 全図種で使えるもの。
            s(Group.COMMON, "puml.surround.note",
                    "note as ${1:N}\n  ${SELECTION}\nend note\n"),
            s(Group.COMMON, "puml.surround.comment", "/'\n${SELECTION}\n'/\n"),
            s(Group.COMMON, "puml.surround.uml",
                    "@startuml\n${SELECTION}\n@enduml\n"),
            s(Group.COMMON, "puml.surround.ifdef",
                    "!ifdef ${1:FLAG}\n  ${SELECTION}\n!endif\n"));

    private static Surround s(Group g, String labelKey, String body) {
        return new Surround(g, labelKey, body);
    }

    private PumlSurrounds() {
    }

    /** すべての囲みブロック (宣言順)。 */
    static List<Surround> all() {
        return ALL;
    }

    /**
     * {@code flavor} の図種で使うものを先に、残りを後に並べて返す。
     * 図種が判らない ({@link Group#COMMON}) ときは宣言順のまま返す。
     */
    static List<Surround> forFlavor(Group flavor) {
        if (flavor == null || flavor == Group.COMMON) {
            return ALL;
        }
        List<Surround> onTopic = new ArrayList<>();
        List<Surround> rest = new ArrayList<>();
        for (Surround s : ALL) {
            (s.group() == flavor ? onTopic : rest).add(s);
        }
        onTopic.addAll(rest);
        return onTopic;
    }
}
