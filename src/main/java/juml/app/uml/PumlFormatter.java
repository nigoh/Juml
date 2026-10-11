// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * PlantUML 本文の一括再インデント (純関数)。
 *
 * <p>変えるのは<b>行頭の空白だけ</b>。語順・行順・行数には一切触れない。
 * 整形で図の意味が変わったら本末転倒なので、「各行の trim 結果が前後で一致する」
 * ことが契約になっている (テストで固定)。</p>
 *
 * <p>触らない場所も構文チェック ({@link PumlDiagnostics}) と同じ判断で決める:</p>
 * <ul>
 *   <li>{@code @startuml} 以外の図 (salt/json/mindmap…) — 字下げ自体に意味がある</li>
 *   <li>凡例・注記の本文 — 文章の字下げは書き手の意図</li>
 *   <li>ブロックコメントの中 — 図の説明に AA が書かれていることがある</li>
 * </ul>
 */
final class PumlFormatter {

    /** インデント 1 段分 ({@link PumlEditorKeys#INDENT} と同じ 2 スペース)。 */
    private static final String INDENT = PumlEditorKeys.INDENT;

    /** 積んだブロック 1 件 (代表語 null は波括弧ブロック)。 */
    private static final class Open {
        /** ブロックの代表語 ({@code alt} 等)。波括弧 ({@code {}) は null。 */
        private final String family;
        /** 中身が自由記述か (凡例・注記)。 */
        private final boolean freeText;

        Open(String family, boolean freeText) {
            this.family = family;
            this.freeText = freeText;
        }
    }

    private PumlFormatter() {
    }

    /** 本文全体を再インデントした結果を返す (行数・各行の中身は不変)。 */
    static String format(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder(text.length() + 64);
        Deque<Open> stack = new ArrayDeque<>();
        boolean inComment = false;
        boolean checkable = true;
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(formatLine(lines[i], stack, inComment, checkable));
            // 状態遷移は整形の後で行う (行自身は遷移前の深さで置くため)。
            String stripped = lines[i].strip();
            if (inComment) {
                inComment = !stripped.contains("'/");
                continue;
            }
            if (stripped.startsWith("/'")) {
                inComment = !stripped.contains("'/");
                continue;
            }
            if (stripped.isEmpty() || stripped.startsWith("'")) {
                // コメントや空行は状態を進めない (「' foo {」をブロックと読まない)。
                continue;
            }
            String lower = stripped.toLowerCase(Locale.ROOT);
            if (lower.startsWith("@start")) {
                stack.clear();
                checkable = lower.startsWith("@startuml");
                continue;
            }
            if (lower.startsWith("@end")) {
                stack.clear();
                checkable = true;
                continue;
            }
            if (checkable) {
                advance(lower, stack);
            }
        }
        return out.toString();
    }

    /** 1 行を現在の深さで置き直す (触らない行はそのまま返す)。 */
    private static String formatLine(String raw, Deque<Open> stack, boolean inComment,
                                     boolean checkable) {
        String stripped = raw.strip();
        if (stripped.isEmpty()) {
            // 空行の行末空白だけは常に落とす (どの文脈でも意味を持たない)。
            return "";
        }
        if (inComment) {
            return raw;
        }
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith("@start") || lower.startsWith("@end")) {
            return stripped;
        }
        if (!checkable) {
            return raw;
        }
        Open top = stack.peek();
        if (top != null && top.freeText) {
            // 自由記述ブロック: 終端行だけ揃え、本文は書き手の字下げのまま。
            String closer = PumlDiagnostics.closerOf(lower);
            if (closer != null && closer.equals(top.family)) {
                return indent(stack.size() - 1) + stripped;
            }
            return raw;
        }
        return indent(depthFor(lower, stack)) + stripped;
    }

    /** この行を置く深さ (終端・継続語は 1 段浅く)。 */
    private static int depthFor(String lower, Deque<Open> stack) {
        int depth = stack.size();
        if (lower.startsWith("}") || isCloser(lower, stack) || isContinuation(lower)) {
            depth--;
        }
        return Math.max(0, depth);
    }

    /** この行が積まれているブロックを実際に閉じるか。 */
    private static boolean isCloser(String lower, Deque<Open> stack) {
        String closed = PumlDiagnostics.closerOf(lower);
        if (closed == null) {
            return false;
        }
        if ("end".equals(closed)) {
            for (Open o : stack) {
                if (o.family != null && END_CLOSED.contains(o.family)) {
                    return true;
                }
            }
            return false;
        }
        for (Open o : stack) {
            if (closed.equals(o.family)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ブロックの途中で 1 段浅く置く継続語。ブロックを閉じも開きもしない
     * ({@code else} は alt/if の枝、{@code case} は switch の枝…)。
     */
    private static boolean isContinuation(String lower) {
        return lower.equals("else") || lower.startsWith("else ") || lower.startsWith("else(")
                || lower.startsWith("elseif") || lower.equals("case")
                || lower.startsWith("case ") || lower.startsWith("case(")
                || lower.equals("fork again") || lower.equals("split again")
                || lower.startsWith("!else");
    }

    /** {@code end} 1 語で閉じられる代表語 ({@link PumlDiagnostics} と同じ集合)。 */
    private static final List<String> END_CLOSED = List.of(
            "alt", "opt", "loop", "par", "critical", "group", "break");

    /** 1 行ぶんの状態遷移: ブロックを閉じる・開く。 */
    private static void advance(String lower, Deque<Open> stack) {
        Open top = stack.peek();
        if (top != null && top.freeText) {
            String closer = PumlDiagnostics.closerOf(lower);
            if (closer != null && closer.equals(top.family)) {
                stack.pop();
            }
            return;
        }
        if (lower.startsWith("}")) {
            if (top != null && top.family == null) {
                stack.pop();
            }
            return;
        }
        String closed = PumlDiagnostics.closerOf(lower);
        if (closed != null) {
            popFor(closed, stack);
            return;
        }
        if (isContinuation(lower)) {
            return;
        }
        if (PumlDiagnostics.opensMemberBody(lower) || lower.endsWith("{")) {
            stack.push(new Open(null, false));
            return;
        }
        String opener = PumlDiagnostics.openerOf(lower);
        if (opener != null) {
            String family = "!ifdef".equals(opener) || "!ifndef".equals(opener)
                    ? "!if" : opener;
            stack.push(new Open(family, "legend".equals(family) || "note".equals(family)));
        }
    }

    /** 終端に対応する開始を下ろす (対応が無い終端は何もしない)。 */
    private static void popFor(String closed, Deque<Open> stack) {
        if ("end".equals(closed)) {
            for (Open o : stack) {
                if (o.family != null && END_CLOSED.contains(o.family)) {
                    stack.remove(o);
                    return;
                }
            }
            return;
        }
        for (Open o : stack) {
            if (closed.equals(o.family)) {
                stack.remove(o);
                return;
            }
        }
    }

    private static String indent(int depth) {
        return INDENT.repeat(Math.max(0, depth));
    }
}
