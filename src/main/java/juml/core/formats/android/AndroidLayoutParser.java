// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
import java.util.Map;

/**
 * Android {@code res/layout/*.xml} をパースして {@link AndroidLayoutInfo} を返すクラス。
 *
 * <p>{@link AndroidManifestParser} と同じ作法で {@link javax.xml.parsers.DocumentBuilder} を
 * 用いた DOM パースを行う。外部エンティティ解決を無効化し XXE 攻撃を防止する。</p>
 *
 * <p>特殊要素の扱い:</p>
 * <ul>
 *   <li>{@code <layout>} (DataBinding ルート): {@code <data>} 子要素をスキップし、
 *       残りの最初の Element をルートとして扱う</li>
 *   <li>{@code <include>}: {@link LayoutViewNode#setIncludeLayoutRef(String)} に
 *       {@code layout="@layout/foo"} の値を格納 (展開は行わない)</li>
 *   <li>{@code <fragment>}: {@link LayoutViewNode#setFragmentClassName(String)} に
 *       {@code android:name} を格納</li>
 *   <li>{@code tools:} 名前空間の属性: ノイズなので {@link LayoutViewNode#getExtraAttributes()}
 *       に入れない</li>
 * </ul>
 */
public final class AndroidLayoutParser {

    /** XML 名前空間: Android。 */
    public static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    /** XML 名前空間: tools (Android Studio 専用、ノイズ)。 */
    public static final String NS_TOOLS = "http://schemas.android.com/tools";

    /** デフォルト ErrorListener (silent) でパース。 */
    public static AndroidLayoutInfo parse(String xml) {
        return parse(xml, null);
    }

    /** ErrorListener 付きでパース。XML 不正は listener に通知して root=null の情報を返す。 */
    public static AndroidLayoutInfo parse(String xml, ErrorListener listener) {
        if (xml == null) {
            throw new IllegalArgumentException("xml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidLayoutInfo info = new AndroidLayoutInfo();
        Document doc;
        try {
            DocumentBuilder builder = createSecureBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException ex) {
                    l.onError(juml.util.ErrorCode.PRJ_007, null, ex.getLineNumber(), "warning: " + ex.getMessage());
                }

                @Override
                public void error(SAXParseException ex) {
                    l.onError(juml.util.ErrorCode.PRJ_007, null, ex.getLineNumber(), "error: " + ex.getMessage());
                }

                @Override
                public void fatalError(SAXParseException ex) throws SAXParseException {
                    throw ex;
                }
            });
            doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            l.onError(juml.util.ErrorCode.PRJ_008, null, -1, "layout parse failed: " + ex.getMessage());
            return info;
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            l.onError(juml.util.ErrorCode.PRJ_008, null, -1, "no root element");
            return info;
        }
        Element effectiveRoot = unwrapDataBindingRoot(root);
        if (effectiveRoot == null) {
            l.onError(juml.util.ErrorCode.PRJ_008, null, -1, "<layout> has no view child");
            return info;
        }
        info.setRoot(buildNode(effectiveRoot));
        return info;
    }

    /**
     * {@code <layout>} (DataBinding) ルートなら、{@code <data>} 子をスキップして
     * 最初の View 要素を返す。それ以外はそのまま返す。
     */
    private static Element unwrapDataBindingRoot(Element root) {
        if (!"layout".equals(root.getTagName())) {
            return root;
        }
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = n.getNodeName();
            if ("data".equals(name)) {
                continue;
            }
            return (Element) n;
        }
        return null;
    }

    /** DOM 要素を {@link LayoutViewNode} に再帰変換。 */
    private static LayoutViewNode buildNode(Element e) {
        LayoutViewNode node = new LayoutViewNode(e.getTagName());

        // 頻出属性は専用フィールド
        node.setId(attrAndroid(e, "id"));
        node.setText(attrAndroid(e, "text"));
        node.setContentDescription(attrAndroid(e, "contentDescription"));
        node.setWidth(attrAndroid(e, "layout_width"));
        node.setHeight(attrAndroid(e, "layout_height"));

        // <include> の layout 属性は android: 名前空間に属さない素の "layout"
        if ("include".equals(e.getTagName())) {
            String inc = e.getAttribute("layout");
            if (inc != null && !inc.isEmpty()) {
                node.setIncludeLayoutRef(inc);
            }
        }
        // <fragment android:name="...">
        if ("fragment".equals(e.getTagName())) {
            String fname = attrAndroid(e, "name");
            if (fname != null && !fname.isEmpty()) {
                node.setFragmentClassName(fname);
            }
        }

        // その他の属性を extraAttributes に。tools:* と既に専用フィールドで取った属性は除外
        collectExtraAttributes(e, node.getExtraAttributes());

        // 子要素を再帰
        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node c = children.item(i);
            if (c.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            node.getChildren().add(buildNode((Element) c));
        }
        return node;
    }

    /**
     * {@code android:} 名前空間の属性値を取得。空文字列は null として返す。
     * 名前空間付きで見つからなければ素の属性名でフォールバック。
     */
    private static String attrAndroid(Element e, String localName) {
        String v = e.getAttributeNS(NS_ANDROID, localName);
        if (v == null || v.isEmpty()) {
            v = e.getAttribute("android:" + localName);
        }
        if (v == null || v.isEmpty()) {
            v = e.getAttribute(localName);
        }
        return (v == null || v.isEmpty()) ? null : v;
    }

    /**
     * 専用フィールドで処理済みでない属性を {@code extra} に格納する。
     * {@code tools:} 名前空間と {@code xmlns:} 宣言は除外。
     */
    private static void collectExtraAttributes(Element e, Map<String, String> extra) {
        NamedNodeMap attrs = e.getAttributes();
        if (attrs == null) {
            return;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            String name = a.getNodeName();
            String value = a.getNodeValue();
            if (name == null || name.isEmpty() || value == null) {
                continue;
            }
            if (name.startsWith("xmlns")) {
                continue;
            }
            String ns = a.getNamespaceURI();
            if (NS_TOOLS.equals(ns) || name.startsWith("tools:")) {
                continue;
            }
            // 既に専用フィールドで処理した android:* 属性は重複格納しない
            if (NS_ANDROID.equals(ns) || name.startsWith("android:")) {
                String local = a.getLocalName() != null ? a.getLocalName()
                        : name.substring(name.indexOf(':') + 1);
                if (isReservedAndroidAttr(local)) {
                    continue;
                }
            }
            // <include layout="..."> / <fragment android:name="..."> も重複格納しない
            if ("include".equals(e.getTagName()) && "layout".equals(name)) {
                continue;
            }
            if ("fragment".equals(e.getTagName())
                    && ("android:name".equals(name) || "name".equals(a.getLocalName()))) {
                continue;
            }
            extra.put(name, value);
        }
    }

    private static boolean isReservedAndroidAttr(String localName) {
        return "id".equals(localName)
                || "text".equals(localName)
                || "contentDescription".equals(localName)
                || "layout_width".equals(localName)
                || "layout_height".equals(localName);
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

    private AndroidLayoutParser() {
    }
}
