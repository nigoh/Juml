// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** {@link MiniJson} の読み書き往復・エスケープ検証。 */
public class MiniJsonTest {

    @Test
    @SuppressWarnings("unchecked")
    public void roundTripsNestedStructure() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        List<Object> arr = new ArrayList<>();
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("text", "Line1\nLine2 \"quoted\" \\ end");
        note.put("x", 12.5);
        note.put("flag", true);
        note.put("nullable", null);
        note.put("unicode", "日本語メモ");
        arr.add(note);
        root.put("items", arr);

        String json = MiniJson.write(root);
        Object parsed = MiniJson.parse(json);

        assertTrue(parsed instanceof Map);
        Map<String, Object> back = (Map<String, Object>) parsed;
        assertEquals(1.0, ((Number) back.get("version")).doubleValue(), 0);
        List<Object> items = (List<Object>) back.get("items");
        assertEquals(1, items.size());
        Map<String, Object> n = (Map<String, Object>) items.get(0);
        assertEquals("Line1\nLine2 \"quoted\" \\ end", n.get("text"));
        assertEquals(12.5, ((Number) n.get("x")).doubleValue(), 0);
        assertEquals(Boolean.TRUE, n.get("flag"));
        assertEquals("日本語メモ", n.get("unicode"));
        assertTrue(n.containsKey("nullable"));
    }

    @Test
    public void writesIntegersWithoutDecimalPoint() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("n", 42);
        assertTrue(MiniJson.write(m).contains("\"n\": 42"));
    }

    @Test
    public void parsesEmptyContainers() {
        assertTrue(((Map<?, ?>) MiniJson.parse("{}")).isEmpty());
        assertTrue(((List<?>) MiniJson.parse("[]")).isEmpty());
    }
}
