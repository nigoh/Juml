// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 変数・フィールドに「格納」されたコールバック (ラムダ / 匿名クラス) 本体を
 * 呼び出し位置で解決するための共通ヘルパ。
 *
 * <p>{@link PlantUmlSequenceDiagram} と {@link PlantUmlActivityDiagram} の両方が使う。
 * 展開ロジックを「コールバックが構文的に出現した宣言位置」ではなく
 * 「実際に呼び出された位置」中心へ寄せるための道具立てで、
 * フィールド由来 ({@link JavaFieldInfo#getInlineMethods()}) とローカル変数由来
 * ({@link JavaMethodInfo.LocalVar#getInlineMethods()}) を同じ規約で検索する。</p>
 */
final class InlineCallbacks {

    /**
     * 「コールバックを保持していそうな」フィールド型のヒューリスティック。
     * java.util.function 系 + 慣習的な Listener/Callback/Handler/Observer 命名。
     * 未解決 note の誤発火 (通常のサービス呼び出し等) を防ぐために使う。
     */
    private static final Pattern FUNCTIONAL_TYPE = Pattern.compile(
            "(java\\.lang\\.)?Runnable"
                    + "|(java\\.util\\.concurrent\\.)?Callable(<.*>)?"
                    + "|(java\\.util\\.function\\.)?(Supplier|Consumer|BiConsumer|Function"
                    + "|BiFunction|Predicate|BiPredicate|UnaryOperator|BinaryOperator)(<.*>)?"
                    + "|.*(Listener|Callback|Handler|Observer)(<.*>)?");

    private InlineCallbacks() {
    }

    /** 呼び出し receiver の先頭識別子 ({@code "cb.run()"} の {@code "cb"})。無ければ null。 */
    static String receiverHead(JavaMethodInfo.Call call) {
        if (call == null) {
            return null;
        }
        String receiver = call.getReceiver();
        if (receiver == null || receiver.isEmpty()) {
            return null;
        }
        int dot = receiver.indexOf('.');
        return dot >= 0 ? receiver.substring(0, dot) : receiver;
    }

    /**
     * receiver が {@code cls} のフィールドであり、そのフィールドに捕捉された inline
     * メソッド (初期化子 / 代入されたラムダ・匿名クラス本体) の中から、呼び出された
     * メソッド名に合致するものを返す。見つからなければ null。SAM 名が解決できず
     * {@code <inline>} で保持されているケースは receiver 一致だけで fall through する。
     */
    static JavaMethodInfo findFieldInline(JavaClassInfo cls, JavaMethodInfo.Call call) {
        String head = receiverHead(call);
        if (cls == null || head == null) {
            return null;
        }
        for (JavaFieldInfo f : cls.getFields()) {
            if (!head.equals(f.getName())) {
                continue;
            }
            JavaMethodInfo m = matchByName(f.getInlineMethods(), call.getMethodName());
            if (m != null) {
                return m;
            }
        }
        return null;
    }

    /**
     * receiver が走査中メソッドのローカル変数であり、そこに格納された inline メソッド
     * の中から呼び出されたメソッド名に合致するものを返す。見つからなければ null。
     */
    static JavaMethodInfo findLocalInline(Map<String, List<JavaMethodInfo>> locals,
                                          JavaMethodInfo.Call call) {
        String head = receiverHead(call);
        if (locals == null || head == null) {
            return null;
        }
        List<JavaMethodInfo> inlines = locals.get(head);
        return inlines == null ? null : matchByName(inlines, call.getMethodName());
    }

    private static JavaMethodInfo matchByName(List<JavaMethodInfo> inlines, String methodName) {
        for (JavaMethodInfo m : inlines) {
            if (methodName.equals(m.getName()) || "<inline>".equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    /**
     * メソッド本体 (制御ブロックの中も含む) で直接呼び出される receiver の
     * 先頭識別子を集める。ローカル変数コールバックを「宣言位置で展開する
     * (どこからも直接呼ばれない = 他メソッドへ渡すだけ等)」か「呼び出し位置で
     * 展開する」かの振り分けに使う。ラムダ/匿名クラスの本体は別スコープなので
     * 中には立ち入らない。
     */
    static Set<String> collectInvokedHeads(List<JavaMethodInfo.Statement> stmts) {
        Set<String> heads = new HashSet<>();
        collect(stmts, heads);
        return heads;
    }

    private static void collect(List<JavaMethodInfo.Statement> stmts, Set<String> out) {
        if (stmts == null) {
            return;
        }
        for (JavaMethodInfo.Statement s : stmts) {
            if (s instanceof JavaMethodInfo.Call) {
                String head = receiverHead((JavaMethodInfo.Call) s);
                if (head != null) {
                    out.add(head);
                }
            } else if (s instanceof JavaMethodInfo.Block) {
                for (JavaMethodInfo.Branch b : ((JavaMethodInfo.Block) s).getBranches()) {
                    collect(b.getBody(), out);
                }
            }
        }
    }

    /**
     * 呼び出し receiver が「コールバックを格納していそうな」フィールドか。
     * setter 経由・コレクション格納・他クラスからの注入などで実体が静的に
     * 解決できないとき、無言で消す代わりに note を出すかの判定に使う
     * (関数型らしくない通常呼び出しにまで note を出してノイズにしないためのガード)。
     */
    static boolean looksLikeStoredCallback(JavaClassInfo cls, JavaMethodInfo.Call call) {
        String head = receiverHead(call);
        if (cls == null || head == null) {
            return false;
        }
        for (JavaFieldInfo f : cls.getFields()) {
            if (head.equals(f.getName())) {
                String type = f.getType();
                return type != null && FUNCTIONAL_TYPE.matcher(type.trim()).matches();
            }
        }
        return false;
    }

    /** 未解決コールバック note の本文 (シーケンス図・アクティビティ図で共通の文言)。 */
    static String unresolvedNoteText(JavaMethodInfo.Call call) {
        return "⋯ " + call.getMethodName()
                + "() の実体は実行時に格納されるため未展開 (setter/コレクション経由 等)";
    }
}
