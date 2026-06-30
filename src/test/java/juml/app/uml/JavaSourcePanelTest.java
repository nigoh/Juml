// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link JavaSourcePanel#findMethodDefinition} のヒューリスティック検証。
 *
 * <p>メソッドタブからソースを開いた際、呼び出し箇所ではなく宣言行へ
 * スクロールできることを保証する (誤スクロールはユーザー体験を損なうため)。</p>
 */
public class JavaSourcePanelTest {

    /** その offset が含まれる行の本文 (前後の空白除去) を返すヘルパ。 */
    private static String lineAt(String text, int offset) {
        int start = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int end = text.indexOf('\n', offset);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(start, end).trim();
    }

    @Test
    public void prefersDeclarationOverEarlierCall() {
        String src = ""
                + "class Foo {\n"
                + "    void run() {\n"
                + "        helper();\n"          // 呼び出しが先
                + "    }\n"
                + "    private int helper() {\n" // 宣言は後
                + "        return 1;\n"
                + "    }\n"
                + "}\n";
        int idx = JavaSourcePanel.findMethodDefinition(src, "helper");
        assertTrue("宣言が見つかること", idx >= 0);
        assertEquals("private int helper() {", lineAt(src, idx));
    }

    @Test
    public void skipsDottedCalls() {
        String src = ""
                + "class Foo {\n"
                + "    void use() {\n"
                + "        obj.compute();\n"     // 呼び出し (前が '.')
                + "    }\n"
                + "    int compute() {\n"        // 宣言
                + "        return 0;\n"
                + "    }\n"
                + "}\n";
        int idx = JavaSourcePanel.findMethodDefinition(src, "compute");
        assertEquals("int compute() {", lineAt(src, idx));
    }

    @Test
    public void findsConstructorLikeDeclaration() {
        String src = ""
                + "public class Bar {\n"
                + "    public Bar(int x) {\n"
                + "        this.x = x;\n"
                + "    }\n"
                + "}\n";
        int idx = JavaSourcePanel.findMethodDefinition(src, "Bar");
        assertEquals("public Bar(int x) {", lineAt(src, idx));
    }

    @Test
    public void returnsNegativeWhenAbsent() {
        String src = "class Foo {\n    void a() {}\n}\n";
        assertEquals(-1, JavaSourcePanel.findMethodDefinition(src, "nonexistent"));
    }
}
