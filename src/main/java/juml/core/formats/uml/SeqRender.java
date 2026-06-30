// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * シーケンス図の再帰描画を通じて不変に引き回すアキュムレータ・設定の束。
 *
 * <p>{@code emit*} ヘルパが participants / 出力バッファ / 呼び出しスタック / オプション等を
 * 個別引数で受け渡していたのを 1 つにまとめ、引数肥大化 (ParameterNumber) を解消する。
 * 再帰のたびに変わる currentClass / depth / indent は引数のまま残す。</p>
 */
final class SeqRender {
    final List<JavaClassInfo> classes;
    final Set<String> participants;
    final Set<String> inlineParticipants;
    final Map<String, LinkedHashSet<String>> participantMethods;
    final StringBuilder body;
    final Set<String> stack;
    final PlantUmlSequenceDiagram.Options opts;

    SeqRender(List<JavaClassInfo> classes, Set<String> participants,
              Set<String> inlineParticipants,
              Map<String, LinkedHashSet<String>> participantMethods,
              StringBuilder body, Set<String> stack,
              PlantUmlSequenceDiagram.Options opts) {
        this.classes = classes;
        this.participants = participants;
        this.inlineParticipants = inlineParticipants;
        this.participantMethods = participantMethods;
        this.body = body;
        this.stack = stack;
        this.opts = opts;
    }
}
