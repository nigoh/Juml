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
        return analyzeProject(projectRoot, false);
    }

    /**
     * {@code includeTests} を指定できる版。指定しないとテストソースの
     * {@code res/xml} が常に混ざり、{@code --include-tests} を付けていないのに
     * テスト用の設定定義がレポートへ入ってしまう (Java 側の走査とも食い違う)。
     */
    public List<PreferenceXmlEntry> analyzeProject(File projectRoot, boolean includeTests)
            throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> xmlFiles = new ArrayList<>();
        // ルートがシンボリックリンクだと、リンクを辿らない走査は「ルートをファイルとして
        // 1 件訪問して終わり」になり、結果が黙って空になる。~/work -> /mnt/src/work の
        // ような貼り方は普通なので、ルートだけ実体へ解決してから走査する。
        Path rootPath = juml.core.formats.java.AndroidProjectScanner.realRoot(projectRoot);
        Files.walkFileTree(rootPath, EnumSet.noneOf(FileVisitOption.class),
                Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        // ルート自身は除外判定にかけない (AndroidProjectScanner と同じ規律)。
                        // 利用者が指定したパスそのものを名前で弾くと、"MyAppTests/" や
                        // "carservice_unit_test/" を解析対象にしただけで結果が黙って空になる。
                        if (dir.equals(rootPath)) {
                            return FileVisitResult.CONTINUE;
                        }
                        // ビルド出力・隠しディレクトリをスキップ
                        if (!includeTests
                                && juml.core.formats.java.AndroidProjectScanner.isTestDir(
                                        dir.toFile())) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        // 除外名は Java 側の走査と同じ集合を使う。ここだけ 4 つしか
                        // 見ていなかったため、out/ や bin/ にある生成物のコピーが
                        // settings.md に二重計上され、クラス図とも食い違っていた。
                        if (juml.core.formats.java.AndroidProjectScanner
                                .DEFAULT_EXCLUDED_DIRS.contains(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        // 権限拒否などで 1 つ読めないだけで --settings 全体を落とさない
                        // (AndroidProjectScanner も同じく無視して継続する)。
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
        // タグ名が "Preference" / "PreferenceCompat" で終わる要素を対象とする。
        // AndroidX の SwitchPreferenceCompat (Android Studio の Settings Activity
        // テンプレートが生成する要素) は "Compat" で終わるため、"Preference" だけを見ると
        // その android:key が黙って落ち、設定項目数も表も 1 件少なく出てしまう。
        // 容器要素 (PreferenceScreen / PreferenceCategory) はどちらの接尾辞にも当たらない。
        if (tag != null && (tag.endsWith("Preference") || tag.endsWith("PreferenceCompat"))) {
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
