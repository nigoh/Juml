// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.HashMap;
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
 *
 * <p>{@link #locals} / {@link #invoked} は「いま歩いているメソッド本体」のスコープ
 * (格納されたローカル変数コールバックと、直接呼び出される receiver の集合)。
 * 制御ブロック (if/loop 等) の中では同じスコープを共有し、別メソッド・コールバック
 * 本体へ潜るときだけ {@link #scopeFor(List)} で新しいスコープに切り替える。</p>
 */
final class SeqRender {
    final List<JavaClassInfo> classes;
    final Set<String> participants;
    final Set<String> inlineParticipants;
    final Map<String, LinkedHashSet<String>> participantMethods;
    final StringBuilder body;
    final Set<String> stack;
    final PlantUmlSequenceDiagram.Options opts;
    /**
     * 再帰展開の残り回数 (図全体で共有する 1 要素の配列)。
     *
     * <p>{@code maxDepth <= 0} (無制限) では、枝分かれのある呼び出し木が深さに対して
     * 指数的に展開される (実測: 1 メソッドが 2 つ呼ぶ形が 18 段で 100 万行超)。
     * 深さではなく「出力した呼び出しの総数」で頭打ちにして、無警告の OutOfMemoryError を
     * 防ぐ。上限に達したことは note で図に残す。</p>
     */
    final int[] expansionBudget;
    /** 現メソッドスコープ: 格納されたローカル変数コールバック (変数名 → inline 本体)。 */
    final Map<String, List<JavaMethodInfo>> locals = new HashMap<>();
    /** 現メソッドスコープ: 本体内で直接呼び出される receiver の先頭識別子。 */
    private Set<String> invoked = Set.of();

    SeqRender(List<JavaClassInfo> classes, Set<String> participants,
              Set<String> inlineParticipants,
              Map<String, LinkedHashSet<String>> participantMethods,
              StringBuilder body, Set<String> stack,
              PlantUmlSequenceDiagram.Options opts) {
        this(classes, participants, inlineParticipants, participantMethods, body, stack, opts,
                new int[]{MAX_EXPANSIONS});
    }

    private SeqRender(List<JavaClassInfo> classes, Set<String> participants,
              Set<String> inlineParticipants,
              Map<String, LinkedHashSet<String>> participantMethods,
              StringBuilder body, Set<String> stack,
              PlantUmlSequenceDiagram.Options opts, int[] expansionBudget) {
        this.expansionBudget = expansionBudget;
        this.classes = classes;
        this.participants = participants;
        this.inlineParticipants = inlineParticipants;
        this.participantMethods = participantMethods;
        this.body = body;
        this.stack = stack;
        this.opts = opts;
    }

    /**
     * 1 枚のシーケンス図で展開する呼び出しの上限。{@code --seq-depth 0} (無制限) でも
     * 枝分かれのある木は指数的に膨らむため、総数で頭打ちにして OutOfMemoryError を防ぐ。
     */
    static final int MAX_EXPANSIONS = 20_000;

    /**
     * 展開の残枠を 1 つ消費する。使い切っていたら note を残して false を返す
     * (呼び出し側はそこで展開を打ち切る)。
     */
    boolean spendExpansion(StringBuilder out, String indent, String target,
            JavaMethodInfo.Call call, JavaClassInfo currentClass,
            JavaClassInfo nextCls, JavaMethodInfo nextMethod) {
        if (expansionBudget[0] <= 0) {
            SeqEmitters.emitBudgetLimitNote(out, indent, target, call,
                    currentClass, nextCls, nextMethod);
            return false;
        }
        expansionBudget[0]--;
        return true;
    }

    /** 本体内で直接呼び出される receiver の先頭識別子 (現メソッドスコープ)。 */
    Set<String> invoked() {
        return invoked;
    }

    /**
     * 別のメソッド本体・コールバック本体へ潜るときの新しいスコープを作る
     * (共有アキュムレータはそのまま、locals / invoked だけ入れ替える)。
     */
    SeqRender scopeFor(List<JavaMethodInfo.Statement> stmts) {
        SeqRender next = new SeqRender(classes, participants, inlineParticipants,
                participantMethods, body, stack, opts, expansionBudget);
        next.invoked = InlineCallbacks.collectInvokedHeads(stmts);
        return next;
    }
}
