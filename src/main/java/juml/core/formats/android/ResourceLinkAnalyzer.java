// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import juml.core.formats.java.AndroidProjectScanner;
import juml.util.ErrorListener;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 実コード (Java/Kotlin) と Android リソースの紐づけを抽出するアナライザ。
 *
 * <p>{@code SCREEN_FLOW} / {@code SOONG} 図と同じく、プロジェクトルートを再走査する
 * 軽量正規表現ベースの解析。重い {@code JavaClassInfo} パイプラインには依存しない。</p>
 *
 * <p>抽出するもの:</p>
 * <ol>
 *   <li>{@code R.layout.* / R.string.* / R.id.*} 参照 (参照元はファイル先頭の型宣言)</li>
 *   <li>{@code setContentView(R.layout.x)} / {@code inflate(R.layout.x, ...)} /
 *       ViewBinding ({@code XxxBinding.inflate}) — 「画面を束ねる」強い結びつき</li>
 *   <li>レイアウト XML 内の {@code @string/foo} 参照 (レイアウト→文字列のエッジ用)</li>
 * </ol>
 *
 * <p>文言解決のため、内部で {@link AndroidProjectAnalyzer} を 1 度走らせて
 * {@link AndroidProjectAnalysis} (layout / strings) を取得し結果へ同梱する。</p>
 */
public final class ResourceLinkAnalyzer {

    /** {@code R.layout.x / R.string.x / R.id.x} を捕捉。g1=種別, g2=名前。 */
    private static final Pattern R_REF = Pattern.compile(
            "\\bR\\.(layout|string|id)\\.([A-Za-z_][A-Za-z0-9_]*)");

    /** {@code setContentView(R.layout.x)}。g1=layout 名。 */
    private static final Pattern SET_CONTENT_VIEW = Pattern.compile(
            "setContentView\\s*\\(\\s*R\\.layout\\.([A-Za-z0-9_]+)");

    /** {@code inflate(R.layout.x, ...)}。g1=layout 名。 */
    private static final Pattern INFLATE_LAYOUT = Pattern.compile(
            "inflate\\s*\\(\\s*R\\.layout\\.([A-Za-z0-9_]+)");

    /** ViewBinding/DataBinding の {@code XxxBinding.inflate(} / {@code XxxBinding.bind(}。g1=Binding クラス。 */
    private static final Pattern BINDING_INFLATE = Pattern.compile(
            "\\b([A-Z][A-Za-z0-9_]*)Binding\\s*\\.\\s*(?:inflate|bind)\\s*\\(");

    /** ファイル先頭付近の型宣言。g1=型名。 */
    private static final Pattern TYPE_DECL = Pattern.compile(
            "(?m)^[ \\t]*(?:@\\w+[^\\n]*\\n[ \\t]*)*"
                    + "(?:public\\s+|final\\s+|abstract\\s+|open\\s+|internal\\s+|sealed\\s+|"
                    + "data\\s+|private\\s+|protected\\s+)*"
                    + "(?:class|interface|object|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");

    /**
     * プロジェクトルートを走査してコード↔リソースの紐づけを解析する。
     *
     * @param projectRoot プロジェクトルート (Gradle/Android プロジェクト)
     * @return 解析結果。{@code projectRoot} が無効なら空の結果。
     */
    public ResourceLinkAnalysis analyzeProject(File projectRoot) throws IOException {
        ResourceLinkAnalysis result = new ResourceLinkAnalysis();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return result;
        }
        // 1. layout / strings をまとめて取得 (文言解決・レイアウト→文字列エッジに使う)
        AndroidProjectAnalysis analysis =
                AndroidProjectAnalyzer.analyze(projectRoot, ErrorListener.silent());
        result.setAnalysis(analysis);
        collectLayoutStringRefs(analysis, result);

        // 2. Java/Kotlin を走査してコード参照を抽出
        AndroidProjectScanner.Options opts = new AndroidProjectScanner.Options();
        opts.includeKotlin = true;
        List<File> files = AndroidProjectScanner.scan(projectRoot, opts);
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (!name.endsWith(".java") && !name.endsWith(".kt")) {
                continue;
            }
            try {
                String src = AndroidProjectScanner.readFile(f);
                analyzeSource(src, f.getPath(), result);
            } catch (IOException ex) {
                // 読めないファイルはスキップ
            }
        }
        return result;
    }

    /** 1 ファイル分のソースを解析して result へ参照を追加する (テスト用に公開)。 */
    public void analyzeSource(String src, String filePath, ResourceLinkAnalysis result) {
        if (src == null || src.isEmpty()) {
            return;
        }
        String owner = detectOwner(src);

        // content binding となる layout 名を先に集める
        Set<String> contentLayouts = new LinkedHashSet<>();
        addMatches(SET_CONTENT_VIEW, src, contentLayouts);
        addMatches(INFLATE_LAYOUT, src, contentLayouts);
        Matcher bm = BINDING_INFLATE.matcher(src);
        while (bm.find()) {
            contentLayouts.add(bindingClassToLayout(bm.group(1)));
        }

        // (owner, kind, name) で重複排除しつつ contentBinding は OR で統合
        Map<String, ResourceReference> dedup = new LinkedHashMap<>();
        Matcher rm = R_REF.matcher(src);
        while (rm.find()) {
            ResourceReference.Kind kind = kindOf(rm.group(1));
            String resName = rm.group(2);
            boolean content = kind == ResourceReference.Kind.LAYOUT
                    && contentLayouts.contains(resName);
            putRef(dedup, owner, kind, resName, content, filePath);
        }
        // ViewBinding 由来の content layout は R.layout.* が無くても紐づけたい
        for (String layoutName : contentLayouts) {
            putRef(dedup, owner, ResourceReference.Kind.LAYOUT, layoutName, true, filePath);
        }
        result.getReferences().addAll(dedup.values());
    }

    private static void putRef(Map<String, ResourceReference> dedup, String owner,
                               ResourceReference.Kind kind, String resName,
                               boolean content, String filePath) {
        String key = kind.name() + ":" + resName;
        ResourceReference existing = dedup.get(key);
        boolean merged = content || (existing != null && existing.isContentBinding());
        dedup.put(key, new ResourceReference(owner, kind, resName, merged, filePath));
    }

    private static void addMatches(Pattern p, String src, Set<String> out) {
        Matcher m = p.matcher(src);
        while (m.find()) {
            out.add(m.group(1));
        }
    }

    /** layout XML を走査し、各レイアウトが参照する {@code @string/foo} を集める。 */
    private static void collectLayoutStringRefs(AndroidProjectAnalysis analysis,
                                                ResourceLinkAnalysis result) {
        if (analysis == null) {
            return;
        }
        for (AndroidLayoutInfo layout : analysis.allLayouts()) {
            String layoutName = stripXml(layout.getFileName());
            if (layout.getRoot() != null) {
                collectStringRefsInNode(layout.getRoot(), layoutName, result);
            }
        }
    }

    private static void collectStringRefsInNode(LayoutViewNode node, String layoutName,
                                                ResourceLinkAnalysis result) {
        addStringRef(node.getText(), layoutName, result);
        addStringRef(node.getContentDescription(), layoutName, result);
        for (String v : node.getExtraAttributes().values()) {
            addStringRef(v, layoutName, result);
        }
        for (LayoutViewNode child : node.getChildren()) {
            collectStringRefsInNode(child, layoutName, result);
        }
    }

    private static void addStringRef(String value, String layoutName,
                                     ResourceLinkAnalysis result) {
        if (value != null && value.startsWith("@string/")) {
            result.addLayoutStringRef(layoutName, value.substring("@string/".length()));
        }
    }

    /** ファイル先頭の型宣言名を返す。見つからなければ null。 */
    static String detectOwner(String src) {
        Matcher m = TYPE_DECL.matcher(src);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /** {@code ActivityMainBinding} → {@code activity_main} (Binding 接尾辞除去 + snake_case 化)。 */
    static String bindingClassToLayout(String bindingClass) {
        if (bindingClass == null || bindingClass.isEmpty()) {
            return "";
        }
        String base = bindingClass.endsWith("Binding")
                ? bindingClass.substring(0, bindingClass.length() - "Binding".length())
                : bindingClass;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static ResourceReference.Kind kindOf(String token) {
        switch (token) {
            case "layout": return ResourceReference.Kind.LAYOUT;
            case "string": return ResourceReference.Kind.STRING;
            case "id":     return ResourceReference.Kind.ID;
            default:       return ResourceReference.Kind.ID;
        }
    }

    private static String stripXml(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.endsWith(".xml")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
    }
}
