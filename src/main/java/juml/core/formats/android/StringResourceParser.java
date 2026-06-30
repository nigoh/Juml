// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import juml.util.ErrorListener;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Android {@code res/values/strings.xml} ({@code values-ja/} 等のバリアント含む) を
 * パースして {@link AndroidStringResources} を返すクラス。
 *
 * <p>{@link AndroidLayoutParser} と同じ作法で {@link javax.xml.parsers.DocumentBuilder} を
 * 用い、外部エンティティ解決を無効化して XXE 攻撃を防止する。</p>
 *
 * <p>抽出対象は {@code <resources>} ルート直下の {@code <string name="...">value</string>}
 * のみ。{@code <color>} / {@code <dimen>} / {@code <style>} など文言以外の要素は無視する。
 * そのため strings 以外の values XML (colors.xml など) を渡しても空の結果を返すだけで害は無い。</p>
 *
 * <p>値のエスケープ正規化:</p>
 * <ul>
 *   <li>前後を囲む {@code "..."} があれば取り除く (Android の空白保持記法)</li>
 *   <li>{@code \'} → {@code '}、{@code \"} → {@code "}、{@code \n} → 改行、{@code \t} → タブ</li>
 *   <li>連続する空白・改行は 1 つの空白に畳む (図のラベル用途のため)</li>
 * </ul>
 */
public final class StringResourceParser {

    /** デフォルト ErrorListener (silent) でパース。 */
    public static AndroidStringResources parse(String xml) {
        return parse(xml, null);
    }

    /** ErrorListener 付きでパース。XML 不正は listener に通知して空の結果を返す。 */
    public static AndroidStringResources parse(String xml, ErrorListener listener) {
        if (xml == null) {
            throw new IllegalArgumentException("xml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidStringResources res = new AndroidStringResources();
        Document doc;
        try {
            DocumentBuilder builder = createSecureBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException ex) {
                    l.onError(null, ex.getLineNumber(), "warning: " + ex.getMessage());
                }

                @Override
                public void error(SAXParseException ex) {
                    l.onError(null, ex.getLineNumber(), "error: " + ex.getMessage());
                }

                @Override
                public void fatalError(SAXParseException ex) throws SAXParseException {
                    throw ex;
                }
            });
            doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            l.onError(null, -1, "strings parse failed: " + ex.getMessage());
            return res;
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            return res;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element e = (Element) n;
            if (!"string".equals(e.getTagName())) {
                continue;
            }
            String name = e.getAttribute("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            // translatable="false" でも文言自体は有効なので取り込む。
            // <xliff:g id="name">%1$s</xliff:g> は {name} トークンに正規化して
            // プレースホルダの意味 (どの引数か) を残す。
            res.getStrings().put(name, normalizeValue(extractStringText(e)));
        }
        return res;
    }

    /**
     * {@code <string>} 要素のテキストを取り出す。{@code getTextContent} と異なり、
     * {@code <xliff:g id="name">…</xliff:g>} を {@code {name}} トークンに置換して
     * プレースホルダの意味を保持する。その他の要素 (b/i 等) は中身のテキストを連結する。
     */
    private static String extractStringText(Element stringEl) {
        StringBuilder sb = new StringBuilder();
        appendNodeText(stringEl, sb);
        return sb.toString();
    }

    private static void appendNodeText(Node node, StringBuilder sb) {
        NodeList kids = node.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node k = kids.item(i);
            short t = k.getNodeType();
            if (t == Node.TEXT_NODE || t == Node.CDATA_SECTION_NODE) {
                sb.append(k.getNodeValue());
            } else if (t == Node.ELEMENT_NODE) {
                Element el = (Element) k;
                if (isXliffG(el)) {
                    String id = el.getAttribute("id");
                    if (id != null && !id.isEmpty()) {
                        sb.append('{').append(id).append('}');
                    } else {
                        appendNodeText(el, sb);
                    }
                } else {
                    appendNodeText(el, sb);
                }
            }
        }
    }

    private static boolean isXliffG(Element el) {
        return "g".equals(el.getLocalName()) || "xliff:g".equals(el.getTagName());
    }

    /** Android の文字列値表記を表示用に正規化する。 */
    static String normalizeValue(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        // 前後を囲む二重引用符 (空白保持記法) を 1 段だけ剥がす
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append(' '); i++; continue;
                    case 't': sb.append(' '); i++; continue;
                    case '\'': sb.append('\''); i++; continue;
                    case '"': sb.append('"'); i++; continue;
                    case '\\': sb.append('\\'); i++; continue;
                    default: break;
                }
            }
            sb.append(c);
        }
        // 連続空白を 1 つに畳む
        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static DocumentBuilder createSecureBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private StringResourceParser() {
    }
}
