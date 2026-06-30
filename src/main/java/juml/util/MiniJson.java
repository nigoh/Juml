// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 依存ライブラリを増やさないための、ごく小さな JSON 読み書きユーティリティ。
 *
 * <p>対応する値: オブジェクト ({@link Map}{@code <String,Object>})、配列
 * ({@link List}{@code <Object>})、文字列、数値 ({@link Double})、真偽値、null。
 * パースは寛容すぎない最小限の実装で、UTF-8 のソースコメント/設定ファイルではなく
 * このアプリが自分で書き出した JSON を読み戻す用途を想定する。</p>
 *
 * <p>出力は git で差分が見やすいよう 2 スペースインデントの整形済み。</p>
 */
public final class MiniJson {

    private MiniJson() {
    }

    // -------------------------------------------------------------------------
    // 書き出し
    // -------------------------------------------------------------------------

    /** 値ツリーを整形 JSON 文字列にする。 */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map) {
            writeObject(sb, (Map<?, ?>) v, indent);
        } else if (v instanceof List) {
            writeArray(sb, (List<?>) v, indent);
        } else if (v instanceof String) {
            writeString(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v) ? "true" : "false");
        } else if (v instanceof Number) {
            sb.append(formatNumber(((Number) v).doubleValue()));
        } else {
            writeString(sb, v.toString());
        }
    }

    private static String formatNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            indent(sb, indent + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            writeValue(sb, e.getValue(), indent + 1);
            if (++i < map.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, indent + 1);
            writeValue(sb, list.get(i), indent + 1);
            if (i + 1 < list.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void indent(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) {
            sb.append("  ");
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // -------------------------------------------------------------------------
    // パース
    // -------------------------------------------------------------------------

    /** JSON 文字列を値ツリーにする。失敗時は {@link IllegalArgumentException}。 */
    public static Object parse(String json) {
        Parser p = new Parser(json);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("trailing content at " + p.pos);
        }
        return v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object readValue() {
            skipWs();
            if (atEnd()) {
                throw new IllegalArgumentException("unexpected end");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{': return readObject();
                case '[': return readArray();
                case '"': return readString();
                case 't': case 'f': return readBoolean();
                case 'n': expect("null"); return null;
                default: return readNumber();
            }
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                if (peek() != ':') {
                    throw new IllegalArgumentException("expected ':' at " + pos);
                }
                pos++;
                map.put(key, readValue());
                skipWs();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected ',' or '}' at " + pos);
                }
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWs();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalArgumentException("expected ',' or ']' at " + pos);
                }
            }
        }

        private String readString() {
            if (peek() != '"') {
                throw new IllegalArgumentException("expected string at " + pos);
            }
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > s.length()) {
                                throw new IllegalArgumentException("incomplete \\u escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("bad escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Boolean readBoolean() {
            if (s.charAt(pos) == 't') {
                expect("true");
                return Boolean.TRUE;
            }
            expect("false");
            return Boolean.FALSE;
        }

        private Double readNumber() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("invalid value at " + start);
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        private void expect(String word) {
            if (!s.startsWith(word, pos)) {
                throw new IllegalArgumentException("expected '" + word + "' at " + pos);
            }
            pos += word.length();
        }

        private char peek() {
            return atEnd() ? '\0' : s.charAt(pos);
        }

        private char next() {
            return atEnd() ? '\0' : s.charAt(pos++);
        }
    }
}
