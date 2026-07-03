// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.doxygen;

import juml.util.ErrorListener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Doxygen の XML 出力 ({@code GENERATE_XML=YES} で生成される {@code xml/} ディレクトリ) を
 * パースして {@link DoxModel} に変換する。
 *
 * <p>処理は 2 段階。まず {@code index.xml} からクラス/インタフェース/列挙等の compound を列挙し、
 * 各 compound の {@code <refid>.xml} を開いて完全修飾名・brief 説明・メンバー一覧を取り出す。
 * compound ファイルが欠けている場合は {@code index.xml} の情報だけで縮退する。</p>
 *
 * <p>MVP ではツリー表示に必要な情報 (種別・名前・brief・メンバー) のみ抽出する。
 * 詳細説明や @param/@return、xrefitem (TODO/Bug/Deprecated) は後段ラウンドで拡張する。</p>
 */
public final class DoxygenXmlParser {

    /** ツリーに出す compound 種別 (パッケージ/ファイル等のノイズを除外する)。 */
    private static final Set<String> TYPE_KINDS =
            Set.of("class", "interface", "struct", "enum", "exception");

    private DoxygenXmlParser() {
    }

    /**
     * Doxygen XML 出力ディレクトリをパースする。
     *
     * @param xmlDir   {@code index.xml} を含むディレクトリ
     * @param listener 個別ファイルのパース失敗を通知するリスナー (null なら silent)
     * @return パース結果。{@code index.xml} が無い場合は空モデル。
     * @throws IOException {@code index.xml} の読み込み自体に失敗した場合
     */
    public static DoxModel parse(File xmlDir, ErrorListener listener) throws IOException {
        ErrorListener log = listener != null ? listener : ErrorListener.silent();
        DoxModel model = new DoxModel();
        File index = new File(xmlDir, "index.xml");
        if (!index.isFile()) {
            throw new IOException("doxygen index.xml not found in " + xmlDir);
        }

        DocumentBuilder builder = newSecureBuilder();
        Document indexDoc;
        try {
            indexDoc = builder.parse(index);
        } catch (Exception ex) {
            throw new IOException("failed to parse " + index.getName() + ": " + ex.getMessage(), ex);
        }

        NodeList compounds = indexDoc.getElementsByTagName("compound");
        for (int i = 0; i < compounds.getLength(); i++) {
            Element compoundEl = (Element) compounds.item(i);
            String kind = compoundEl.getAttribute("kind");
            String refid = compoundEl.getAttribute("refid");
            String indexName = childText(compoundEl, "name");
            if (TYPE_KINDS.contains(kind)) {
                DoxCompound compound = parseCompoundFile(builder, xmlDir, refid, kind, indexName, log, model);
                if (compound == null) {
                    // compound ファイルが無い/壊れている場合は index.xml の情報だけで縮退。
                    compound = new DoxCompound(refid, kind, indexName, "");
                    addIndexMembers(compoundEl, compound);
                }
                model.addCompound(compound);
            } else if ("group".equals(kind)) {
                DoxGroup group = parseGroupFile(builder, xmlDir, refid, indexName, log);
                if (group != null) {
                    model.addGroup(group);
                }
            }
        }
        return model;
    }

    /** {@code group___*.xml} を開いてグループのタイトル・所属型・下位グループを取り出す。失敗時は null。 */
    private static DoxGroup parseGroupFile(DocumentBuilder builder, File xmlDir, String refid,
                                           String fallbackId, ErrorListener log) {
        if (refid == null || refid.isEmpty()) {
            return null;
        }
        File file = new File(xmlDir, refid + ".xml");
        if (!file.isFile()) {
            return new DoxGroup(fallbackId, fallbackId);
        }
        Document doc;
        try {
            doc = builder.parse(file);
        } catch (Exception ex) {
            log.onError(juml.util.ErrorCode.PRJ_010, file.getName(), -1, "doxygen group parse failed: " + ex.getMessage());
            return new DoxGroup(fallbackId, fallbackId);
        }
        NodeList defs = doc.getElementsByTagName("compounddef");
        if (defs.getLength() == 0) {
            return new DoxGroup(fallbackId, fallbackId);
        }
        Element def = (Element) defs.item(0);
        String id = childText(def, "compoundname");
        if (id.isEmpty()) {
            id = fallbackId;
        }
        DoxGroup group = new DoxGroup(id, childText(def, "title"));
        NodeList innerClasses = def.getElementsByTagName("innerclass");
        for (int i = 0; i < innerClasses.getLength(); i++) {
            group.addInnerClass(normalize(innerClasses.item(i).getTextContent()));
        }
        NodeList innerGroups = def.getElementsByTagName("innergroup");
        for (int i = 0; i < innerGroups.getLength(); i++) {
            group.addInnerGroup(normalize(innerGroups.item(i).getTextContent()));
        }
        return group;
    }

    /** {@code <refid>.xml} を開いて compound の詳細 (brief・メンバー・xref) を取り出す。失敗時は null。 */
    private static DoxCompound parseCompoundFile(DocumentBuilder builder, File xmlDir, String refid,
                                                 String kind, String fallbackName, ErrorListener log,
                                                 DoxModel model) {
        if (refid == null || refid.isEmpty()) {
            return null;
        }
        File file = new File(xmlDir, refid + ".xml");
        if (!file.isFile()) {
            return null;
        }
        Document doc;
        try {
            doc = builder.parse(file);
        } catch (Exception ex) {
            log.onError(juml.util.ErrorCode.PRJ_010, file.getName(), -1, "doxygen compound parse failed: " + ex.getMessage());
            return null;
        }
        NodeList defs = doc.getElementsByTagName("compounddef");
        if (defs.getLength() == 0) {
            return null;
        }
        Element def = (Element) defs.item(0);
        String name = childText(def, "compoundname");
        if (name.isEmpty()) {
            name = fallbackName;
        }
        String brief = briefText(def);
        DoxCompound compound = new DoxCompound(refid, kind, name, brief);
        compound.setDetailed(parseDetailed(def).prose);
        collectXrefs(def, name, model);
        // R4: 継承メタ (基底型 / 派生型)。
        for (String base : childTexts(def, "basecompoundref")) {
            compound.addBaseType(base);
        }
        for (String derived : childTexts(def, "derivedcompoundref")) {
            compound.addDerivedType(derived);
        }

        NodeList memberDefs = def.getElementsByTagName("memberdef");
        for (int i = 0; i < memberDefs.getLength(); i++) {
            Element m = (Element) memberDefs.item(i);
            String mKind = m.getAttribute("kind");
            String mRefid = m.getAttribute("id");
            String mName = childText(m, "name");
            String args = childText(m, "argsstring");
            String mBrief = briefText(m);
            DoxMember member = new DoxMember(mRefid, mKind, mName, args, mBrief);
            DetailedDoc detail = parseDetailed(m);
            member.setDetail(childText(m, "type"), detail.prose,
                    detail.params, detail.returns, detail.throwsList);
            // R4: 参照メタ (重複名を除去して順序保持)。
            member.setReferences(uniqueChildTexts(m, "references"),
                    uniqueChildTexts(m, "referencedby"));
            compound.addMember(member);
            collectXrefs(m, name + "." + mName, model);
        }
        return compound;
    }

    /** 直下 (子孫含む) の指定タグのテキストを順に集めて返す。 */
    private static java.util.List<String> childTexts(Element parent, String tag) {
        java.util.List<String> out = new java.util.ArrayList<>();
        NodeList list = parent.getElementsByTagName(tag);
        for (int i = 0; i < list.getLength(); i++) {
            String t = normalize(list.item(i).getTextContent());
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** {@link #childTexts} の重複除去版 (順序保持)。referencedby が同名を繰り返す対策。 */
    private static java.util.List<String> uniqueChildTexts(Element parent, String tag) {
        return new java.util.ArrayList<>(new java.util.LinkedHashSet<>(childTexts(parent, tag)));
    }

    /**
     * 要素直下の {@code <detaileddescription>} 内の {@code <xrefsect>} (@todo/@bug/@deprecated) を
     * 走査し、{@code location} を付与して {@code model} に追加する。
     */
    private static void collectXrefs(Element parent, String location, DoxModel model) {
        Element detailed = detailedElement(parent);
        if (detailed == null) {
            return;
        }
        NodeList sects = detailed.getElementsByTagName("xrefsect");
        for (int i = 0; i < sects.getLength(); i++) {
            Element x = (Element) sects.item(i);
            String title = firstTagText(x, "xreftitle");
            DoxXrefItem.Kind kind = DoxXrefItem.Kind.fromTitle(title);
            if (kind == DoxXrefItem.Kind.OTHER) {
                continue;
            }
            String desc = firstTagText(x, "xrefdescription");
            model.addXrefItem(new DoxXrefItem(kind, location, desc));
        }
    }

    /** detaileddescription から散文・{@code @param}・{@code @return}・{@code @throws} を抽出した結果。 */
    private static final class DetailedDoc {
        String prose = "";
        final java.util.List<DoxParam> params = new java.util.ArrayList<>();
        String returns = "";
        final java.util.List<DoxParam> throwsList = new java.util.ArrayList<>();
    }

    /** 要素直下の {@code <detaileddescription>} 要素を返す。無ければ null。 */
    private static Element detailedElement(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "detaileddescription".equals(n.getNodeName())) {
                return (Element) n;
            }
        }
        return null;
    }

    /** 要素直下の {@code <detaileddescription>} を解析する。無ければ空の DetailedDoc。 */
    private static DetailedDoc parseDetailed(Element parent) {
        DetailedDoc doc = new DetailedDoc();
        Element detailed = null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "detaileddescription".equals(n.getNodeName())) {
                detailed = (Element) n;
                break;
            }
        }
        if (detailed == null) {
            return doc;
        }
        // 散文: parameterlist / simplesect (構造化部分) を除いたテキスト。
        doc.prose = normalize(proseText(detailed));
        // @param / @throws: parameterlist の kind で振り分け。
        NodeList plists = detailed.getElementsByTagName("parameterlist");
        for (int i = 0; i < plists.getLength(); i++) {
            Element pl = (Element) plists.item(i);
            String plKind = pl.getAttribute("kind");
            java.util.List<DoxParam> items = parseParamItems(pl);
            if ("exception".equals(plKind)) {
                doc.throwsList.addAll(items);
            } else if ("param".equals(plKind)) {
                doc.params.addAll(items);
            }
        }
        // @return: simplesect kind="return"。
        NodeList sects = detailed.getElementsByTagName("simplesect");
        for (int i = 0; i < sects.getLength(); i++) {
            Element s = (Element) sects.item(i);
            if ("return".equals(s.getAttribute("kind")) && doc.returns.isEmpty()) {
                doc.returns = normalize(s.getTextContent());
            }
        }
        return doc;
    }

    /** parameterlist 配下の各 parameteritem を (名前, 説明) に変換する。 */
    private static java.util.List<DoxParam> parseParamItems(Element parameterlist) {
        java.util.List<DoxParam> out = new java.util.ArrayList<>();
        NodeList items = parameterlist.getElementsByTagName("parameteritem");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String pName = firstTagText(item, "parametername");
            String pDesc = firstTagText(item, "parameterdescription");
            out.add(new DoxParam(pName, pDesc));
        }
        return out;
    }

    /** 構造化要素 (parameterlist / simplesect) を除いた散文テキストを再帰収集する。 */
    private static String proseText(Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.TEXT_NODE) {
                sb.append(n.getNodeValue());
            } else if (n.getNodeType() == Node.ELEMENT_NODE) {
                String name = n.getNodeName();
                if ("parameterlist".equals(name) || "simplesect".equals(name)
                        || "xrefsect".equals(name)) {
                    continue;
                }
                sb.append(proseText(n)).append(' ');
            }
        }
        return sb.toString();
    }

    /** 子孫の最初の指定タグのテキストを正規化して返す。無ければ空文字。 */
    private static String firstTagText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() > 0 ? normalize(list.item(0).getTextContent()) : "";
    }

    /** index.xml の {@code <member>} 子要素だけで縮退メンバーを足す (compound ファイル欠落時)。 */
    private static void addIndexMembers(Element compoundEl, DoxCompound compound) {
        NodeList members = compoundEl.getElementsByTagName("member");
        for (int i = 0; i < members.getLength(); i++) {
            Element m = (Element) members.item(i);
            compound.addMember(new DoxMember(
                    m.getAttribute("refid"), m.getAttribute("kind"),
                    childText(m, "name"), "", ""));
        }
    }

    /** 直下の最初の {@code briefdescription} のテキストを正規化して返す。無ければ空文字。 */
    private static String briefText(Element parent) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "briefdescription".equals(n.getNodeName())) {
                return normalize(n.getTextContent());
            }
        }
        return "";
    }

    /** 直下の最初の指定タグのテキストを返す。無ければ空文字。 */
    private static String childText(Element parent, String tag) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && tag.equals(n.getNodeName())) {
                return normalize(n.getTextContent());
            }
        }
        return "";
    }

    /** 連続空白を 1 個に畳んで trim する。 */
    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** 外部エンティティ解決を無効化したセキュアな DocumentBuilder を生成する。 */
    private static DocumentBuilder newSecureBuilder() throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (ParserConfigurationException ex) {
            throw new IOException("failed to configure XML parser: " + ex.getMessage(), ex);
        }
    }
}
