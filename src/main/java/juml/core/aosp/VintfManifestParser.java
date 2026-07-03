// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

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
import java.util.ArrayList;
import java.util.List;

/**
 * AOSP の VINTF manifest / compatibility-matrix XML を解析する。
 *
 * <p>DOM ベースで {@link AndroidManifestParser} と同様に XXE/DOCTYPE 解決を
 * 無効化したセキュアな {@link DocumentBuilder} を使う。HAL の宣言一覧と
 * カーネル要求バージョン、sepolicy バージョン等を {@link VintfManifest} に
 * 取り出す。device 側 / framework 側 / compat-matrix のいずれも 1 つの
 * 出力モデルに統合する。</p>
 *
 * <p>Android のアプリ manifest ({@code AndroidManifest.xml}) との混同を避ける
 * ため、ルート要素名で判別する: ルートが {@code <manifest>} で {@code type}
 * 属性が無いか {@code package} 属性がある場合は VINTF ではないとして
 * {@link VintfManifest.Kind#UNKNOWN} を返す。</p>
 */
public final class VintfManifestParser {

    /** デフォルト ErrorListener (silent) でパース。 */
    public static VintfManifest parse(String xml) {
        return parse(xml, null);
    }

    /** ErrorListener 付きでパース。XML 不正は listener に通知して空の情報を返す。 */
    public static VintfManifest parse(String xml, ErrorListener listener) {
        if (xml == null) {
            throw new IllegalArgumentException("xml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
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
            doc = builder.parse(new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            l.onError(juml.util.ErrorCode.PRJ_008, null, -1, "vintf parse failed: " + ex.getMessage());
            return new VintfManifest(VintfManifest.Kind.UNKNOWN);
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            return new VintfManifest(VintfManifest.Kind.UNKNOWN);
        }
        VintfManifest.Kind kind = classifyRoot(root);
        VintfManifest info = new VintfManifest(kind);
        if (kind == VintfManifest.Kind.UNKNOWN) {
            return info;
        }
        // version 属性 (両方共有)
        String version = attr(root, "version", "");
        if (!version.isEmpty()) {
            info.setVersion(version);
        }
        // compat-matrix の level 属性
        if (kind == VintfManifest.Kind.COMPATIBILITY_MATRIX) {
            String levelStr = attr(root, "level", "");
            if (!levelStr.isEmpty()) {
                try {
                    info.setLevel(Integer.parseInt(levelStr));
                } catch (NumberFormatException ignore) {
                    // 数値で無ければ無視
                }
            }
        }
        for (Element hal : childElements(root, "hal")) {
            VintfHal h = parseHal(hal);
            if (h != null) {
                info.getHals().add(h);
            }
        }
        Element kernel = firstChildElement(root, "kernel");
        if (kernel != null) {
            String kver = attr(kernel, "version", "");
            if (!kver.isEmpty()) {
                info.setKernelVersion(kver);
            }
        }
        Element sepolicy = firstChildElement(root, "sepolicy");
        if (sepolicy != null) {
            Element sver = firstChildElement(sepolicy, "version");
            if (sver != null) {
                info.setSepolicyVersion(textOf(sver));
            }
        }
        return info;
    }

    private static VintfManifest.Kind classifyRoot(Element root) {
        String tag = root.getNodeName();
        String typeAttr = attr(root, "type", "");
        if ("compatibility-matrix".equals(tag)) {
            return VintfManifest.Kind.COMPATIBILITY_MATRIX;
        }
        if (!"manifest".equals(tag)) {
            return VintfManifest.Kind.UNKNOWN;
        }
        // Android の AndroidManifest.xml は <manifest package="..."> 形式で
        // type 属性が無い。VINTF は <manifest type="device|framework"> なので
        // ここで判別する。
        if (typeAttr.isEmpty()) {
            return VintfManifest.Kind.UNKNOWN;
        }
        if ("device".equalsIgnoreCase(typeAttr)) {
            return VintfManifest.Kind.DEVICE_MANIFEST;
        }
        if ("framework".equalsIgnoreCase(typeAttr)) {
            return VintfManifest.Kind.FRAMEWORK_MANIFEST;
        }
        return VintfManifest.Kind.UNKNOWN;
    }

    private static VintfHal parseHal(Element hal) {
        String format = attr(hal, "format", "");
        Element nameEl = firstChildElement(hal, "name");
        if (nameEl == null) {
            return null;
        }
        String halName = textOf(nameEl);
        VintfHal h = new VintfHal(format, halName);
        Element transport = firstChildElement(hal, "transport");
        if (transport != null) {
            h.setTransport(textOf(transport));
        }
        for (Element v : childElements(hal, "version")) {
            String s = textOf(v);
            if (!s.isEmpty()) {
                h.getVersions().add(s);
            }
        }
        for (Element iface : childElements(hal, "interface")) {
            Element ifaceName = firstChildElement(iface, "name");
            if (ifaceName == null) {
                continue;
            }
            VintfInterface vi = findOrAddInterface(h, textOf(ifaceName));
            for (Element inst : childElements(iface, "instance")) {
                String instText = textOf(inst);
                if (!instText.isEmpty() && !vi.getInstances().contains(instText)) {
                    vi.getInstances().add(instText);
                }
            }
        }
        // AIDL HAL (format="aidl") は <interface> ではなく
        // <fqname>IFoo/default</fqname> 形式でインスタンスを宣言するのが主流。
        // interface 名と instance を分解し、同名 interface へマージする。
        for (Element fq : childElements(hal, "fqname")) {
            String fqText = textOf(fq);
            if (fqText.isEmpty()) {
                continue;
            }
            int slash = fqText.indexOf('/');
            String ifaceName = (slash >= 0 ? fqText.substring(0, slash) : fqText).trim();
            String instance = slash >= 0 ? fqText.substring(slash + 1).trim() : "";
            if (ifaceName.isEmpty()) {
                continue;
            }
            VintfInterface vi = findOrAddInterface(h, ifaceName);
            if (!instance.isEmpty() && !vi.getInstances().contains(instance)) {
                vi.getInstances().add(instance);
            }
        }
        String optional = attr(hal, "optional", "");
        if (!optional.isEmpty()) {
            if ("true".equalsIgnoreCase(optional)) {
                h.setOptional(Boolean.TRUE);
            } else if ("false".equalsIgnoreCase(optional)) {
                h.setOptional(Boolean.FALSE);
            }
        }
        return h;
    }

    /** 同名 interface が既にあれば再利用、無ければ新規追加して返す。 */
    private static VintfInterface findOrAddInterface(VintfHal h, String name) {
        for (VintfInterface vi : h.getInterfaces()) {
            if (vi.getName().equals(name)) {
                return vi;
            }
        }
        VintfInterface vi = new VintfInterface(name);
        h.getInterfaces().add(vi);
        return vi;
    }

    private VintfManifestParser() {
    }

    // --- XML ヘルパ ---

    private static DocumentBuilder createSecureBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private static String attr(Element e, String localName, String fallback) {
        String v = e.getAttribute(localName);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static String textOf(Element e) {
        if (e == null) {
            return "";
        }
        String t = e.getTextContent();
        return t == null ? "" : t.trim();
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

    private static List<Element> childElements(Element parent, String name) {
        List<Element> list = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                list.add((Element) n);
            }
        }
        return list;
    }
}
