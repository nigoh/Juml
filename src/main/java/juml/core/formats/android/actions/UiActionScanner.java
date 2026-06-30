// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.actions;

import juml.core.formats.java.AndroidProjectScanner;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android アプリのユーザー操作ハンドラを Java / Kotlin ソースと Layout XML から検出する。
 *
 * <p>検出パターン:</p>
 * <ul>
 *   <li>Java/Kotlin: {@code setOnClickListener} / {@code setOnLongClickListener} /
 *       {@code setOnCheckedChangeListener}</li>
 *   <li>Java/Kotlin: {@code onOptionsItemSelected} メソッド定義</li>
 *   <li>Compose: {@code Modifier.clickable} / {@code onClick = }</li>
 *   <li>Layout XML: {@code android:onClick="methodName"} 属性</li>
 * </ul>
 */
public final class UiActionScanner {

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";

    /**
     * {@code .setOnClickListener} の検出。ビュー ID を前の行から推測するために行番号を記録。
     * 末尾は {@code (} または Kotlin の SAM 変換 + trailing lambda {@code {} を許容する
     * ({@code btn.setOnClickListener { ... }} 形式を取りこぼさない)。
     */
    private static final Pattern ON_CLICK = Pattern.compile(
            "(?:(\\w+)\\s*\\.\\s*)?setOnClickListener\\s*[({]");
    private static final Pattern ON_LONG_CLICK = Pattern.compile(
            "(?:(\\w+)\\s*\\.\\s*)?setOnLongClickListener\\s*[({]");
    private static final Pattern ON_CHECKED_CHANGED = Pattern.compile(
            "(?:(\\w+)\\s*\\.\\s*)?setOnCheckedChangeListener\\s*[({]");

    /** {@code onOptionsItemSelected} メソッド定義。 */
    private static final Pattern MENU_ITEM_SELECTED = Pattern.compile(
            "\\b(?:public|protected|private)?\\s+boolean\\s+onOptionsItemSelected\\s*\\(");

    /** Compose: {@code onClick = } または {@code Modifier.clickable} の検出。 */
    private static final Pattern COMPOSE_CLICK = Pattern.compile(
            "(?:onClick\\s*=|Modifier\\.clickable)");

    /** View ID の取得: {@code R.id.xxx} または {@code binding.xxx} から。 */
    private static final Pattern VIEW_ID = Pattern.compile(
            "R\\.id\\.([A-Za-z0-9_]+)");

    /** ラムダ本体の呼び出し先メソッド: {@code v -> cameraTask(} の {@code cameraTask}。 */
    private static final Pattern LAMBDA_TARGET = Pattern.compile(
            "->\\s*([A-Za-z_]\\w*)\\s*\\(");

    /** メソッド参照: {@code this::onSave} / {@code ::onSave} の {@code onSave}。 */
    private static final Pattern METHOD_REF_TARGET = Pattern.compile(
            "::\\s*([A-Za-z_]\\w*)");

    /**
     * プロジェクト全体をスキャンして全 UI アクションエントリを返す。
     */
    public List<UiActionEntry> analyzeProject(File projectRoot) throws IOException {
        return analyzeProject(projectRoot, false);
    }

    /** {@code includeTests} でテストソースを含めるかを制御できる版。 */
    public List<UiActionEntry> analyzeProject(File projectRoot, boolean includeTests)
            throws IOException {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<UiActionEntry> all = new ArrayList<>();

        // Java / Kotlin ソースをスキャン
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeKotlin = true;
        opts.includeTests = includeTests;
        List<File> srcFiles = AndroidProjectScanner.scan(projectRoot, opts);
        for (File f : srcFiles) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
            try {
                String src = AndroidProjectScanner.readFile(f);
                all.addAll(analyzeSource(src, f.getPath()));
            } catch (IOException ignored) {
                // ファイル読み取り失敗は無視
            }
        }

        // Layout XML をスキャン
        all.addAll(analyzeLayoutFiles(projectRoot));
        return all;
    }

    /**
     * 単一ソースファイルをスキャンして UI アクションエントリを返す。
     */
    public List<UiActionEntry> analyzeSource(String src, String filePath) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        String[] lines = src.split("\n", -1);
        List<UiActionEntry> entries = new ArrayList<>();

        // クラス名を簡易抽出
        String className = extractClassName(src);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;

            // 行コメント (// ...) / ブロックコメント継続 (* ...) はハンドラ検出の対象外。
            // ({} を許容したことでコメント内の擬似コードを誤検出しないため)
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }

            // setOnClickListener
            Matcher m = ON_CLICK.matcher(line);
            if (m.find()) {
                String viewId = m.group(1) != null ? m.group(1) : extractViewIdFromContext(lines, i);
                entries.add(new UiActionEntry(viewId, UiActionEntry.ActionType.ON_CLICK,
                        resolveHandler(className, "setOnClickListener", line, m.end()),
                        filePath, lineNum));
            }

            // setOnLongClickListener
            m = ON_LONG_CLICK.matcher(line);
            if (m.find()) {
                String viewId = m.group(1) != null ? m.group(1) : "";
                entries.add(new UiActionEntry(viewId, UiActionEntry.ActionType.ON_LONG_CLICK,
                        resolveHandler(className, "setOnLongClickListener", line, m.end()),
                        filePath, lineNum));
            }

            // setOnCheckedChangeListener
            m = ON_CHECKED_CHANGED.matcher(line);
            if (m.find()) {
                String viewId = m.group(1) != null ? m.group(1) : "";
                entries.add(new UiActionEntry(viewId, UiActionEntry.ActionType.ON_CHECKED_CHANGED,
                        resolveHandler(className, "setOnCheckedChangeListener", line, m.end()),
                        filePath, lineNum));
            }

            // onOptionsItemSelected
            if (MENU_ITEM_SELECTED.matcher(line).find()) {
                entries.add(new UiActionEntry("(menu)", UiActionEntry.ActionType.MENU_ITEM,
                        className + "#onOptionsItemSelected", filePath, lineNum));
            }

            // Compose onClick
            if (COMPOSE_CLICK.matcher(line).find()) {
                entries.add(new UiActionEntry("(composable)",
                        UiActionEntry.ActionType.COMPOSE_CLICK,
                        className + "#composableClick", filePath, lineNum));
            }
        }
        return entries;
    }

    private List<UiActionEntry> analyzeLayoutFiles(File projectRoot) throws IOException {
        List<UiActionEntry> all = new ArrayList<>();
        Files.walkFileTree(projectRoot.toPath(), EnumSet.noneOf(FileVisitOption.class),
                Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                        if ("build".equals(name) || ".gradle".equals(name)
                                || ".git".equals(name) || "node_modules".equals(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName() == null ? "" : file.getFileName().toString();
                        if (!name.endsWith(".xml")) return FileVisitResult.CONTINUE;
                        // layout ディレクトリ配下のみ
                        Path parent = file.getParent();
                        if (parent == null) return FileVisitResult.CONTINUE;
                        String parentName = parent.getFileName() == null
                                ? "" : parent.getFileName().toString();
                        if (!parentName.startsWith("layout")) return FileVisitResult.CONTINUE;
                        try {
                            String content = new String(Files.readAllBytes(file),
                                    StandardCharsets.UTF_8);
                            all.addAll(analyzeLayoutXml(content, file.toString()));
                        } catch (IOException ignored) {
                            // 読み取り失敗は無視
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
        return all;
    }

    /**
     * Layout XML から {@code android:onClick} 属性を抽出する。
     */
    public List<UiActionEntry> analyzeLayoutXml(String xml, String filePath) {
        if (xml == null || xml.isEmpty()) {
            return Collections.emptyList();
        }
        List<UiActionEntry> entries = new ArrayList<>();
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
            extractOnClickFromElement(doc.getDocumentElement(), filePath, entries);
        } catch (Exception ignored) {
            // XML パース失敗は無視
        }
        return entries;
    }

    private void extractOnClickFromElement(Element el, String filePath,
                                            List<UiActionEntry> entries) {
        if (el == null) return;
        String viewId = attrAndroid(el, "id");
        if (viewId.startsWith("@+id/")) viewId = viewId.substring(5);
        else if (viewId.startsWith("@id/")) viewId = viewId.substring(4);

        String onClick = attrAndroid(el, "onClick");
        if (!onClick.isEmpty()) {
            entries.add(new UiActionEntry(
                    viewId.isEmpty() ? el.getTagName() : viewId,
                    UiActionEntry.ActionType.XML_ON_CLICK,
                    normalizeOnClickHandler(onClick), filePath, -1));
        }

        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element) {
                extractOnClickFromElement((Element) children.item(i), filePath, entries);
            }
        }
    }

    /** データバインディング呼び出し {@code receiver.method(...)} の呼び出し名抽出用。 */
    private static final Pattern DATA_BINDING_CALL = Pattern.compile(
            "([A-Za-z_][\\w.]*)\\s*\\(");

    /**
     * {@code android:onClick} の値を正規化する。データバインディング式
     * ({@code @{() -> vm.onSave()}} / {@code @{vm::onSave}}) なら呼び出し先 (vm.onSave) を、
     * 通常のメソッド名ならそのまま返す。
     */
    private static String normalizeOnClickHandler(String raw) {
        String v = raw.trim();
        if (!v.startsWith("@{") || !v.endsWith("}")) {
            return v;
        }
        String expr = v.substring(2, v.length() - 1).trim();
        // ラムダ () -> body はボディ側を採用
        int arrow = expr.indexOf("->");
        if (arrow >= 0) {
            expr = expr.substring(arrow + 2).trim();
        }
        // メソッド参照 vm::onSave → vm.onSave
        expr = expr.replace("::", ".");
        // 呼び出し receiver.method(...) なら呼び出し名のみを取り出す
        Matcher cm = DATA_BINDING_CALL.matcher(expr);
        if (cm.find()) {
            return cm.group(1);
        }
        return expr;
    }

    private String attrAndroid(Element el, String localName) {
        String v = el.getAttributeNS(NS_ANDROID, localName);
        if (v == null || v.isEmpty()) {
            v = el.getAttribute("android:" + localName);
        }
        return v != null ? v : "";
    }

    /** ソース全体から最初のクラス名を簡易抽出する。 */
    private static String extractClassName(String src) {
        java.util.regex.Matcher m = Pattern.compile(
                "(?:public|private|protected)?\\s+(?:abstract\\s+)?class\\s+([A-Za-z0-9_$]+)")
                .matcher(src);
        return m.find() ? m.group(1) : "";
    }

    /**
     * リスナ登録呼び出しのハンドラ名を解決する。ラムダ ({@code v -> cameraTask()}) や
     * メソッド参照 ({@code this::onSave}) なら呼び出し先メソッド名を、解決できなければ
     * リスナ登録メソッド名そのものをフォールバックとして {@code Class#name} 形式で返す。
     */
    private static String resolveHandler(String className, String fallbackMethod,
                                         String line, int afterOpenParen) {
        String rest = afterOpenParen < line.length() ? line.substring(afterOpenParen) : "";
        Matcher lm = LAMBDA_TARGET.matcher(rest);
        if (lm.find()) {
            return className + "#" + lm.group(1);
        }
        Matcher rm = METHOD_REF_TARGET.matcher(rest);
        if (rm.find()) {
            return className + "#" + rm.group(1);
        }
        return className + "#" + fallbackMethod;
    }

    /** 同じ行前後の `R.id.xxx` からビュー ID を推測する。 */
    private static String extractViewIdFromContext(String[] lines, int lineIndex) {
        // 同じ行と前後 3 行を検索
        int start = Math.max(0, lineIndex - 3);
        int end = Math.min(lines.length - 1, lineIndex + 1);
        for (int i = end; i >= start; i--) {
            Matcher m = VIEW_ID.matcher(lines[i]);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }
}
