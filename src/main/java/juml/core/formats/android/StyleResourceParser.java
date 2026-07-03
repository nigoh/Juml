// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import juml.util.ErrorListener;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Android {@code res/values/styles.xml} / {@code themes.xml} 等から
 * {@code <style>} 定義を抽出して {@link AndroidStyleResources} を返すクラス。
 *
 * <p>{@link StringResourceParser} と同じセキュア DOM パース (XXE 防御) を用いる。
 * 抽出対象は {@code <resources>} 直下の {@code <style name="..." parent="...">} と、
 * その配下の {@code <item name="...">value</item>}。{@code <string>} など他要素は無視する
 * ため、文字列専用 XML を渡しても空の結果を返すだけで害は無い。</p>
 *
 * <p>{@code parent} は明示属性 ({@code parent="@style/Foo"} または {@code parent="Foo"}) を
 * 優先し、無ければ Android の暗黙継承規則 (ドット記法 {@code AppTheme.Dialog} →
 * 親 {@code AppTheme}) で補完する。</p>
 */
public final class StyleResourceParser {

    /** デフォルト ErrorListener (silent) でパース。 */
    public static AndroidStyleResources parse(String xml) {
        return parse(xml, null);
    }

    /** ErrorListener 付きでパース。XML 不正は listener に通知して空の結果を返す。 */
    public static AndroidStyleResources parse(String xml, ErrorListener listener) {
        if (xml == null) {
            throw new IllegalArgumentException("xml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidStyleResources res = new AndroidStyleResources();
        Document doc;
        try {
            DocumentBuilder builder = createSecureBuilder();
            doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            l.onError(juml.util.ErrorCode.PRJ_008, null, -1, "styles parse failed: " + ex.getMessage());
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
            if (!"style".equals(e.getTagName())) {
                continue;
            }
            String name = e.getAttribute("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            AndroidStyleResources.StyleDef def = new AndroidStyleResources.StyleDef(name);
            def.setParent(resolveParent(name, e.getAttribute("parent")));
            collectItems(e, def);
            res.getStyles().put(name, def);
        }
        return res;
    }

    /** {@code <item name="...">value</item>} を集める。 */
    private static void collectItems(Element style, AndroidStyleResources.StyleDef def) {
        NodeList items = style.getChildNodes();
        for (int i = 0; i < items.getLength(); i++) {
            Node n = items.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) n;
            if (!"item".equals(item.getTagName())) {
                continue;
            }
            String iname = item.getAttribute("name");
            if (iname != null && !iname.isEmpty()) {
                def.getItems().put(iname, item.getTextContent() == null
                        ? "" : item.getTextContent().trim());
            }
        }
    }

    /**
     * 親スタイル名を決定する。明示 {@code parent} 属性があればそれを短縮名にして返し、
     * 無ければドット記法による暗黙継承 ({@code A.B.C} → {@code A.B}) を返す。どちらも
     * 無ければ null。
     */
    static String resolveParent(String name, String parentAttr) {
        if (parentAttr != null && !parentAttr.isEmpty()) {
            String p = parentAttr;
            int slash = p.lastIndexOf('/');
            if (slash >= 0) {
                p = p.substring(slash + 1);
            }
            return p.isEmpty() ? null : p;
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : null;
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

    private StyleResourceParser() {
    }
}
