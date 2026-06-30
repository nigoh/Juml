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
 * AndroidManifest.xml をパースして {@link AndroidManifestInfo} を返すクラス。
 *
 * <p>{@code javax.xml.parsers.DocumentBuilderFactory} を用いた DOM パース。
 * 外部エンティティ解決を無効化し、XXE 攻撃を防止する。</p>
 */
public final class AndroidManifestParser {

    /** XML 名前空間: Android。 */
    public static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";

    /** デフォルト ErrorListener (silent) でパース。 */
    public static AndroidManifestInfo parse(String xml) {
        return parse(xml, null);
    }

    /** ErrorListener 付きでパース。XML 不正は listener に通知して空の情報を返す。 */
    public static AndroidManifestInfo parse(String xml, ErrorListener listener) {
        if (xml == null) {
            throw new IllegalArgumentException("xml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidManifestInfo info = new AndroidManifestInfo();
        Document doc;
        try {
            DocumentBuilder builder = createSecureBuilder();
            // DocumentBuilder の既定 ErrorHandler は System.err に直書きするため抑止する
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
            l.onError(null, -1, "manifest parse failed: " + ex.getMessage());
            return info;
        }
        Element root = doc.getDocumentElement();
        if (root == null || !"manifest".equals(root.getNodeName())) {
            l.onError(null, -1, "root element is not <manifest>");
            return info;
        }
        info.setPackageName(attr(root, "package", ""));
        parseUsesSdk(root, info);
        parseUsesPermissions(root, info);
        parseUsesFeatures(root, info);
        parseCustomPermissions(root, info);
        Element application = firstChildElement(root, "application");
        if (application != null) {
            parseApplication(application, info);
        }
        return info;
    }

    private static void parseUsesSdk(Element root, AndroidManifestInfo info) {
        // 通常は manifest 直下に 1 つだけ。複数あっても最初の宣言を採用する。
        Element sdk = firstChildElement(root, "uses-sdk");
        if (sdk == null) {
            return;
        }
        info.setMinSdkVersion(parseSdkLevel(attr(sdk, "minSdkVersion", null)));
        info.setTargetSdkVersion(parseSdkLevel(attr(sdk, "targetSdkVersion", null)));
        info.setMaxSdkVersion(parseSdkLevel(attr(sdk, "maxSdkVersion", null)));
    }

    private static void parseCustomPermissions(Element root, AndroidManifestInfo info) {
        // <permission> はアプリ自身が宣言する独自パーミッション。
        // <uses-permission> (要求側) とは別物として保持する。
        for (Element e : childElements(root, "permission")) {
            String name = attr(e, "name", "");
            if (name.isEmpty()) {
                continue;
            }
            AndroidCustomPermission p = new AndroidCustomPermission(info.resolveClassName(name));
            p.setProtectionLevel(attr(e, "protectionLevel", null));
            p.setPermissionGroup(attr(e, "permissionGroup", null));
            p.setLabel(attr(e, "label", null));
            p.setDescription(attr(e, "description", null));
            info.getCustomPermissions().add(p);
        }
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

    private static void parseUsesPermissions(Element root, AndroidManifestInfo info) {
        // uses-permission は <manifest> 直下の宣言のみが「要求権限」。
        // getElementsByTagName だと <queries> 配下など子孫の uses-permission まで
        // 誤って拾うため、直下要素だけを対象にする。
        for (Element e : childElements(root, "uses-permission")) {
            String name = attr(e, "name", "");
            if (name.isEmpty()) {
                continue;
            }
            AndroidPermissionInfo p = new AndroidPermissionInfo(name);
            Integer max = parseInt(attr(e, "maxSdkVersion", null));
            if (max != null) {
                p.setMaxSdkVersion(max);
            }
            info.getPermissions().add(p);
        }
    }

    private static void parseUsesFeatures(Element root, AndroidManifestInfo info) {
        // uses-feature も <manifest> 直下のみを対象にする (子孫の誤収集を防ぐ)。
        for (Element e : childElements(root, "uses-feature")) {
            String name = attr(e, "name", "");
            if (!name.isEmpty()) {
                info.getFeatures().add(name);
            }
        }
    }

    private static void parseApplication(Element app, AndroidManifestInfo info) {
        String cls = attr(app, "name", null);
        if (cls != null) {
            info.setApplicationClass(info.resolveClassName(cls));
        }
        info.setApplicationLabel(attr(app, "label", null));
        info.setApplicationTheme(attr(app, "theme", null));
        info.setApplicationDebuggable(parseBool(attr(app, "debuggable", null)));
        info.setApplicationAllowBackup(parseBool(attr(app, "allowBackup", null)));
        // Android 10+ / 12+ / 13+ で重要になった application 属性。
        info.setApplicationUsesCleartextTraffic(parseBool(attr(app, "usesCleartextTraffic", null)));
        info.setApplicationNetworkSecurityConfig(attr(app, "networkSecurityConfig", null));
        info.setApplicationEnableOnBackInvokedCallback(
                parseBool(attr(app, "enableOnBackInvokedCallback", null)));
        info.setApplicationLocaleConfig(attr(app, "localeConfig", null));
        info.setApplicationDataExtractionRules(attr(app, "dataExtractionRules", null));
        info.setApplicationHardwareAccelerated(parseBool(attr(app, "hardwareAccelerated", null)));
        info.setApplicationLargeHeap(parseBool(attr(app, "largeHeap", null)));
        info.setApplicationAppCategory(attr(app, "appCategory", null));
        // application 直下の meta-data
        for (Element md : childElements(app, "meta-data")) {
            String name = attr(md, "name", "");
            String value = attr(md, "value", attr(md, "resource", ""));
            if (!name.isEmpty()) {
                info.getApplicationMetaData().put(name, value);
            }
        }
        // application 直下の <property> (Android 12+)
        for (Element prop : childElements(app, "property")) {
            AndroidPropertyInfo p = buildProperty(prop);
            if (p != null) {
                info.getApplicationProperties().add(p);
            }
        }
        // 各コンポーネント
        for (Element a : childElements(app, "activity")) {
            info.getActivities().add(buildComponent(a, AndroidComponentInfo.Kind.ACTIVITY, info));
        }
        for (Element a : childElements(app, "activity-alias")) {
            AndroidComponentInfo alias = buildComponent(a, AndroidComponentInfo.Kind.ACTIVITY, info);
            // activity-alias は targetActivity 必須属性で別 Activity を指す。
            // FQN 解決して保持し、isActivityAlias() で区別できるようにする。
            String target = attr(a, "targetActivity", null);
            if (target != null) {
                alias.setTargetActivity(info.resolveClassName(target));
            }
            info.getActivities().add(alias);
        }
        for (Element s : childElements(app, "service")) {
            AndroidComponentInfo svc = buildComponent(s, AndroidComponentInfo.Kind.SERVICE, info);
            // Android 14 以降は foreground service 用に必須化された属性。
            svc.setForegroundServiceType(attr(s, "foregroundServiceType", null));
            info.getServices().add(svc);
        }
        for (Element r : childElements(app, "receiver")) {
            info.getReceivers().add(buildComponent(r, AndroidComponentInfo.Kind.RECEIVER, info));
        }
        for (Element p : childElements(app, "provider")) {
            info.getProviders().add(buildComponent(p, AndroidComponentInfo.Kind.PROVIDER, info));
        }
    }

    private static AndroidComponentInfo buildComponent(Element e,
                                                        AndroidComponentInfo.Kind kind,
                                                        AndroidManifestInfo info) {
        String raw = attr(e, "name", "");
        AndroidComponentInfo c = new AndroidComponentInfo(kind, info.resolveClassName(raw));
        c.setExported(parseBool(attr(e, "exported", null)));
        c.setEnabled(parseBool(attr(e, "enabled", null)));
        c.setTaskAffinity(attr(e, "taskAffinity", null));
        c.setProcess(attr(e, "process", null));
        c.setPermission(attr(e, "permission", null));
        c.setAuthorities(attr(e, "authorities", null));
        for (Element f : childElements(e, "intent-filter")) {
            c.getIntentFilters().add(buildIntentFilter(f));
        }
        for (Element md : childElements(e, "meta-data")) {
            String name = attr(md, "name", "");
            String value = attr(md, "value", attr(md, "resource", ""));
            if (!name.isEmpty()) {
                c.getMetaData().put(name, value);
            }
        }
        // コンポーネント直下の <property> (Android 12+)
        for (Element prop : childElements(e, "property")) {
            AndroidPropertyInfo p = buildProperty(prop);
            if (p != null) {
                c.getProperties().add(p);
            }
        }
        return c;
    }

    private static AndroidPropertyInfo buildProperty(Element e) {
        String name = attr(e, "name", "");
        if (name.isEmpty()) {
            return null;
        }
        AndroidPropertyInfo p = new AndroidPropertyInfo(name);
        p.setValue(emptyToNull(attr(e, "value", "")));
        p.setResource(emptyToNull(attr(e, "resource", "")));
        return p;
    }

    private static AndroidIntentFilter buildIntentFilter(Element e) {
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.setPriority(parseInt(attr(e, "priority", null)));
        f.setOrder(parseInt(attr(e, "order", null)));
        f.setAutoVerify(parseBool(attr(e, "autoVerify", null)));
        for (Element a : childElements(e, "action")) {
            String name = attr(a, "name", "");
            if (!name.isEmpty()) {
                f.getActions().add(name);
            }
        }
        for (Element c : childElements(e, "category")) {
            String name = attr(c, "name", "");
            if (!name.isEmpty()) {
                f.getCategories().add(name);
            }
        }
        for (Element d : childElements(e, "data")) {
            AndroidDataSpec spec = buildDataSpec(d);
            // 既存 API (dataSchemes / dataMimeTypes) も並行で埋めて互換を保つ。
            if (spec.getScheme() != null) {
                f.getDataSchemes().add(spec.getScheme());
            }
            if (spec.getMimeType() != null) {
                f.getDataMimeTypes().add(spec.getMimeType());
            }
            f.getDataSpecs().add(spec);
        }
        return f;
    }

    private static AndroidDataSpec buildDataSpec(Element d) {
        AndroidDataSpec spec = new AndroidDataSpec();
        spec.setScheme(emptyToNull(attr(d, "scheme", "")));
        spec.setHost(emptyToNull(attr(d, "host", "")));
        spec.setPort(emptyToNull(attr(d, "port", "")));
        spec.setPath(emptyToNull(attr(d, "path", "")));
        spec.setPathPrefix(emptyToNull(attr(d, "pathPrefix", "")));
        spec.setPathPattern(emptyToNull(attr(d, "pathPattern", "")));
        spec.setPathSuffix(emptyToNull(attr(d, "pathSuffix", "")));
        spec.setPathAdvancedPattern(emptyToNull(attr(d, "pathAdvancedPattern", "")));
        spec.setMimeType(emptyToNull(attr(d, "mimeType", "")));
        return spec;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    // --- DOM ヘルパ ---

    private static String attr(Element e, String localName, String fallback) {
        // android: 名前空間付き属性を優先、無ければ素の属性名を試す
        String v = e.getAttributeNS(NS_ANDROID, localName);
        if (v == null || v.isEmpty()) {
            v = e.getAttribute(localName);
        }
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static java.util.List<Element> childElements(Element parent, String name) {
        java.util.List<Element> list = new java.util.ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static Boolean parseBool(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * SDK レベル属性 (minSdkVersion 等) を解釈する。数値でなければ Android の
     * プレビュー API コードネーム (例: {@code "VanillaIceCream"} / 単独文字 {@code "S"})
     * を対応 API レベルへ解決する。未知のコードネームは null。
     */
    private static Integer parseSdkLevel(String s) {
        Integer n = parseInt(s);
        if (n != null) {
            return n;
        }
        if (s == null) {
            return null;
        }
        return SDK_CODENAMES.get(s.trim());
    }

    /** Android プレビュー SDK コードネーム → 確定 API レベル。 */
    private static final java.util.Map<String, Integer> SDK_CODENAMES = buildSdkCodenames();

    private static java.util.Map<String, Integer> buildSdkCodenames() {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        m.put("Q", 29);
        m.put("R", 30);
        m.put("S", 31);
        m.put("Sv2", 32);
        m.put("S_V2", 32);
        m.put("Tiramisu", 33);
        m.put("UpsideDownCake", 34);
        m.put("VanillaIceCream", 35);
        m.put("Baklava", 36);
        return m;
    }

    private AndroidManifestParser() {
    }
}
