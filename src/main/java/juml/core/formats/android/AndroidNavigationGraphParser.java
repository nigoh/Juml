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
 * Jetpack Navigation グラフ XML ({@code res/navigation/*.xml}) をパースして
 * {@link AndroidNavigationGraphInfo} を返すクラス。
 *
 * <p>{@link AndroidLayoutParser} と同じ作法で {@link DocumentBuilder} を用いた DOM パースを行う。
 * 外部エンティティ解決を無効化し XXE 攻撃を防止する。</p>
 *
 * <p>パース対象要素:</p>
 * <ul>
 *   <li>{@code <navigation>}: ルートグラフ。{@code android:id} / {@code app:startDestination} /
 *       {@code android:label} を取得する</li>
 *   <li>{@code <fragment>} / {@code <activity>} / {@code <dialog>}: 各 Destination</li>
 *   <li>ネスト {@code <navigation>}: サブグラフを 1 Destination として扱う</li>
 *   <li>{@code <include>}: 外部グラフ参照 ({@code app:graph})</li>
 *   <li>{@code <action>}: Destination 内またはグローバルアクション</li>
 *   <li>{@code <argument>}: Destination の引数定義</li>
 *   <li>{@code <deepLink>}: Destination の Deep Link URI ({@code app:uri})</li>
 * </ul>
 */
public final class AndroidNavigationGraphParser {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String NS_APP = "http://schemas.android.com/apk/res-auto";
    private static final String NS_TOOLS = "http://schemas.android.com/tools";

    /** デフォルト ErrorListener (silent) でパース。 */
    public static AndroidNavigationGraphInfo parse(String xml) {
        return parse(xml, null);
    }

    /** ErrorListener 付きでパース。XML 不正は listener に通知して空の情報を返す。 */
    public static AndroidNavigationGraphInfo parse(String xml, ErrorListener listener) {
        if (xml == null) {
            throw new IllegalArgumentException("xml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidNavigationGraphInfo info = new AndroidNavigationGraphInfo();
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
            l.onError(null, -1, "navigation parse failed: " + ex.getMessage());
            return info;
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            l.onError(null, -1, "no root element");
            return info;
        }
        String rootTag = root.getLocalName() != null ? root.getLocalName() : root.getTagName();
        if (!"navigation".equals(rootTag)) {
            l.onError(null, -1, "root element is not <navigation>: " + root.getTagName());
        }
        parseNavigationRoot(root, info);
        return info;
    }

    private static void parseNavigationRoot(Element root, AndroidNavigationGraphInfo info) {
        info.setGraphId(normalizeRef(attrAndroid(root, "id")));
        info.setLabel(attrAndroid(root, "label"));
        info.setStartDestination(normalizeRef(attrApp(root, "startDestination")));

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            String tag = localName(child);
            switch (tag) {
                case "fragment":
                    info.getDestinations().add(
                            parseDestination(child, NavigationDestination.Kind.FRAGMENT));
                    break;
                case "activity":
                    info.getDestinations().add(
                            parseDestination(child, NavigationDestination.Kind.ACTIVITY));
                    break;
                case "dialog":
                    info.getDestinations().add(
                            parseDestination(child, NavigationDestination.Kind.DIALOG));
                    break;
                case "navigation":
                    info.getDestinations().add(
                            parseDestination(child, NavigationDestination.Kind.NAVIGATION));
                    break;
                case "include":
                    info.getDestinations().add(parseInclude(child));
                    break;
                case "action":
                    NavigationAction ga = parseAction(child);
                    ga.setGlobal(true);
                    info.getGlobalActions().add(ga);
                    break;
                default:
                    break;
            }
        }
    }

    private static NavigationDestination parseDestination(Element e,
                                                           NavigationDestination.Kind kind) {
        NavigationDestination dest = new NavigationDestination();
        dest.setKind(kind);
        String rawId = attrAndroid(e, "id");
        dest.setId(rawId);
        dest.setIdRef(normalizeRef(rawId));
        dest.setName(attrAndroid(e, "name"));
        dest.setLabel(attrAndroid(e, "label"));
        String toolsLayout = e.getAttributeNS(NS_TOOLS, "layout");
        if (toolsLayout == null || toolsLayout.isEmpty()) {
            toolsLayout = e.getAttribute("tools:layout");
        }
        dest.setToolsLayout(toolsLayout.isEmpty() ? null : toolsLayout);
        if (kind == NavigationDestination.Kind.NAVIGATION) {
            dest.setStartDestination(normalizeRef(attrApp(e, "startDestination")));
        }

        NodeList children = e.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) children.item(i);
            String tag = localName(child);
            switch (tag) {
                case "action":
                    dest.getActions().add(parseAction(child));
                    break;
                case "argument":
                    dest.getArguments().add(parseArgument(child));
                    break;
                case "deepLink":
                    String uri = attrApp(child, "uri");
                    if (uri != null && !uri.isEmpty()) {
                        dest.getDeepLinks().add(uri);
                    } else {
                        // uri を持たず action/mimeType だけで宣言される暗黙 deeplink も
                        // 取りこぼさず、記述子としてラベル化して保持する。
                        String descriptor = buildImplicitDeepLink(
                                attrApp(child, "action"), attrApp(child, "mimeType"));
                        if (descriptor != null) {
                            dest.getDeepLinks().add(descriptor);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return dest;
    }

    private static NavigationDestination parseInclude(Element e) {
        NavigationDestination dest = new NavigationDestination();
        dest.setKind(NavigationDestination.Kind.INCLUDE);
        String graph = attrApp(e, "graph");
        dest.setId(graph);
        dest.setIdRef(normalizeRef(graph));
        return dest;
    }

    private static NavigationAction parseAction(Element e) {
        NavigationAction action = new NavigationAction();
        String rawId = attrAndroid(e, "id");
        action.setId(rawId);
        action.setIdRef(normalizeRef(rawId));
        action.setDestination(normalizeRef(attrApp(e, "destination")));
        action.setPopUpTo(normalizeRef(attrApp(e, "popUpTo")));
        action.setPopUpToInclusive("true".equals(attrApp(e, "popUpToInclusive")));
        return action;
    }

    private static NavigationArgument parseArgument(Element e) {
        NavigationArgument arg = new NavigationArgument();
        arg.setName(attrAndroid(e, "name"));
        arg.setArgType(attrApp(e, "argType"));
        arg.setDefaultValue(attrAndroid(e, "defaultValue"));
        arg.setNullable("true".equals(attrApp(e, "nullable")));
        return arg;
    }

    /**
     * {@code @+id/foo}, {@code @id/foo}, {@code @navigation/foo} などから
     * {@code foo} を抽出する。それ以外はそのまま返す。null/empty は null を返す。
     */
    static String normalizeRef(String ref) {
        if (ref == null || ref.isEmpty()) {
            return null;
        }
        int slash = ref.lastIndexOf('/');
        return slash >= 0 ? ref.substring(slash + 1) : ref;
    }

    /** {@code android:} 名前空間の属性値を取得。空文字列は null として返す。 */
    private static String attrAndroid(Element e, String localName) {
        String v = e.getAttributeNS(NS_ANDROID, localName);
        if (v == null || v.isEmpty()) {
            v = e.getAttribute("android:" + localName);
        }
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** {@code app:} 名前空間の属性値を取得。空文字列は null として返す。 */
    /**
     * uri の無い暗黙 deeplink を {@code action}/{@code mimeType} から記述子文字列に組み立てる。
     * どちらも無ければ {@code null}。
     */
    private static String buildImplicitDeepLink(String action, String mimeType) {
        boolean hasAction = action != null && !action.isEmpty();
        boolean hasMime = mimeType != null && !mimeType.isEmpty();
        if (hasAction && hasMime) {
            return action + " (" + mimeType + ")";
        }
        if (hasAction) {
            return action;
        }
        if (hasMime) {
            return mimeType;
        }
        return null;
    }

    private static String attrApp(Element e, String localName) {
        String v = e.getAttributeNS(NS_APP, localName);
        if (v == null || v.isEmpty()) {
            v = e.getAttribute("app:" + localName);
        }
        return (v == null || v.isEmpty()) ? null : v;
    }

    /** 要素のローカル名を返す。名前空間なしの場合は tagName を使う。 */
    private static String localName(Element e) {
        String ln = e.getLocalName();
        return (ln != null && !ln.isEmpty()) ? ln : e.getTagName();
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

    private AndroidNavigationGraphParser() {
    }
}
