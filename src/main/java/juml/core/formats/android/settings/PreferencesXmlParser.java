// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.settings;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Android プロジェクトの res/xml/ 配下にある Preference XML ファイルを解析する。
 *
 * <p>対象要素: タグ名が "Preference" で終わる要素 (SwitchPreference, EditTextPreference 等)
 * の {@code android:key} および {@code android:defaultValue} 属性を抽出する。</p>
 */
public final class PreferencesXmlParser {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";

    /**
     * プロジェクトルート配下の res/xml/ を再帰的に走査して Preference キー定義を収集する。
     */
    public List<PreferenceXmlEntry> analyzeProject(File projectRoot) throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> xmlFiles = new ArrayList<>();
        Files.walkFileTree(projectRoot.toPath(), EnumSet.noneOf(FileVisitOption.class),
                Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        // ビルド出力・隠しディレクトリをスキップ
                        if ("build".equals(name) || ".gradle".equals(name)
                                || ".git".equals(name) || "node_modules".equals(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName() == null ? "" : file.getFileName().toString();
                        if (!name.endsWith(".xml")) {
                            return FileVisitResult.CONTINUE;
                        }
                        // res/xml/ または修飾子付き res/xml-xxx/ (xml-v21, xml-night 等)
                        // 直下のファイルのみ対象
                        Path parent = file.getParent();
                        if (parent != null) {
                            String parentName = parent.getFileName() == null
                                    ? "" : parent.getFileName().toString();
                            if ("xml".equals(parentName) || parentName.startsWith("xml-")) {
                                Path grandParent = parent.getParent();
                                if (grandParent != null
                                        && "res".equals(grandParent.getFileName() == null
                                        ? "" : grandParent.getFileName().toString())) {
                                    xmlFiles.add(file.toFile());
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

        List<PreferenceXmlEntry> all = new ArrayList<>();
        for (File f : xmlFiles) {
            try {
                String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                all.addAll(parse(content, f.getPath()));
            } catch (IOException ignored) {
                // 読み取り失敗は無視して続行
            }
        }
        return all;
    }

    /**
     * XML 文字列をパースして Preference エントリのリストを返す。
     */
    public List<PreferenceXmlEntry> parse(String xml, String filePath) {
        if (xml == null || xml.isEmpty()) {
            return Collections.emptyList();
        }
        List<PreferenceXmlEntry> entries = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE 対策
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                factory.setXIncludeAware(false);
                factory.setExpandEntityReferences(false);
            } catch (Exception ignore) {
                // 一部の features は古い XML パーサで未対応。可能なものだけ設定
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            Document doc = builder.parse(new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8)));
            extractFromElement(doc.getDocumentElement(), filePath, entries);
        } catch (Exception ignored) {
            // XML パース失敗は無視して空リストを返す
        }
        return entries;
    }

    private void extractFromElement(Element el, String filePath,
                                     List<PreferenceXmlEntry> entries) {
        if (el == null) {
            return;
        }
        String tag = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
        // タグ名が "Preference" で終わる要素を対象とする
        if (tag != null && tag.endsWith("Preference")) {
            String key = attrAndroid(el, "key");
            if (!key.isEmpty()) {
                String defVal = attrAndroid(el, "defaultValue");
                String title = attrAndroid(el, "title");
                entries.add(new PreferenceXmlEntry(key, tag, defVal, title, filePath));
            }
        }
        // 子要素を再帰的に処理
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                extractFromElement((Element) children.item(i), filePath, entries);
            }
        }
    }

    private String attrAndroid(Element el, String localName) {
        String v = el.getAttributeNS(NS_ANDROID, localName);
        if (v == null || v.isEmpty()) {
            // 名前空間なしフォールバック (テスト等で android: プレフィクスなしの場合)
            v = el.getAttribute("android:" + localName);
        }
        return v != null ? v : "";
    }
}
