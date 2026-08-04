// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * メッセージリソースにキーの重複が無いことを保証する。
 *
 * <p>{@code .properties} は<b>後に書かれた定義が勝つ</b>。同じキーを二度書いても
 * ビルドも起動も通るため、重複は「片方を編集しても表示が変わらない」「先に書いた側の
 * 書式 ({@code {0}} の有無) を前提にした呼び出し側だけが壊れる」という形で表に出る。
 * 実際に Doxygen 関連の 7 キーが二重定義されており、後勝ちした {@code {0}} 付きの文言を
 * 文字列連結で使っていた Groups/Todo パネルが <b>"doxygen を設定しました: {0}/usr/bin/doxygen"</b>
 * のように literal の {@code {0}} を表示していた。</p>
 */
public class MessagesDuplicateKeyTest {

    /** 定義順を保ったままキー → 出現行番号一覧を集める (Properties だと重複が潰れるため自前で読む)。 */
    private static Map<String, List<Integer>> keyLines(String resource) throws IOException {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        try (InputStream in = MessagesDuplicateKeyTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(resource + " が見つからない", in);
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            int no = 0;
            while ((line = r.readLine()) != null) {
                no++;
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) {
                    continue;
                }
                int eq = t.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                out.computeIfAbsent(t.substring(0, eq).trim(), k -> new ArrayList<>()).add(no);
            }
        }
        return out;
    }

    private static void assertNoDuplicates(String resource) throws IOException {
        List<String> dups = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : keyLines(resource).entrySet()) {
            if (e.getValue().size() > 1) {
                dups.add(e.getKey() + " @ lines " + e.getValue());
            }
        }
        assertTrue(resource + " にキーの重複がある (後勝ちで片方が死ぬ): " + dups, dups.isEmpty());
    }

    @Test
    public void englishBundleHasNoDuplicateKeys() throws IOException {
        assertNoDuplicates("messages.properties");
    }

    @Test
    public void japaneseBundleHasNoDuplicateKeys() throws IOException {
        assertNoDuplicates("messages_ja.properties");
    }

    @Test
    public void doxygenStatusMessagesArePlaceholderStyle() {
        // 連結ではなく MessageFormat で使う契約を固定する (Groups/Todo/Panel の 3 箇所が共有)。
        for (String key : new String[] {"doxygen.status.set", "doxygen.status.failed"}) {
            assertTrue(key + " は {0} を持つ書式であること", Messages.get(key).contains("{0}"));
        }
    }

    @Test
    public void formattedDoxygenStatusHasNoLiteralPlaceholder() {
        String msg = java.text.MessageFormat.format(
                Messages.get("doxygen.status.set"), "/usr/bin/doxygen");
        assertTrue("引数が埋め込まれること: " + msg, msg.contains("/usr/bin/doxygen"));
        assertEquals("literal の {0} が残らないこと: " + msg, -1, msg.indexOf("{0}"));
    }
}
