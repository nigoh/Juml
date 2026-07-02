// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import juml.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android Runtime Resource Overlay (RRO) を {@code AndroidManifest.xml} から検出する。
 *
 * <p>判定条件: Manifest に {@code <overlay ... />} 要素が含まれていること。
 * その要素の {@code targetPackage}, {@code targetName}, {@code isStatic}, {@code priority}
 * 属性、および {@code <manifest package="...">} の自身のパッケージ名を取得する。</p>
 *
 * <p>厳密な XML パースは行わず、属性抽出は正規表現ベース。RRO 検出に必要最小限の情報のみ。</p>
 */
public final class RroOverlayDetector {

    private static final Pattern MANIFEST_PACKAGE = Pattern.compile(
            "<manifest[^>]*\\bpackage\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern OVERLAY_ELEMENT = Pattern.compile(
            "<overlay\\b([^>]*)/?>", Pattern.DOTALL);
    private static final Pattern ATTR_PATTERN = Pattern.compile(
            "(?:android:)?(\\w+)\\s*=\\s*\"([^\"]*)\"");
    /** XML コメント {@code <!-- ... -->}。コメントアウトされた要素の誤検出を防ぐため除去する。 */
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    /** プロジェクト下を再帰走査して RRO オーバレイを抽出する。 */
    public List<RroOverlay> analyzeProject(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> manifests = new ArrayList<>();
        collectManifests(projectRoot, manifests);
        List<RroOverlay> out = new ArrayList<>();
        for (File m : manifests) {
            try {
                String src = AndroidProjectScanner.readFile(m);
                RroOverlay overlay = parseManifest(src, m.getPath());
                if (overlay != null) {
                    out.add(overlay);
                }
            } catch (IOException ex) {
                // skip
            }
        }
        return out;
    }

    /** 1 件の Manifest XML を解析し RRO であれば {@link RroOverlay} を返す。 */
    public RroOverlay parseManifest(String src, String filePath) {
        if (src == null || src.isEmpty()) return null;
        // コメントアウトされた <overlay> / <manifest> を実在の宣言と誤認しないよう、
        // 先に XML コメントを除去する (兄弟パーサの stripComments と同方針)。
        src = XML_COMMENT.matcher(src).replaceAll("");
        Matcher om = OVERLAY_ELEMENT.matcher(src);
        if (!om.find()) {
            return null;
        }
        String attrs = om.group(1);
        String targetPackage = "";
        String targetName = "";
        boolean isStatic = false;
        int priority = -1;
        Matcher am = ATTR_PATTERN.matcher(attrs);
        while (am.find()) {
            String key = am.group(1);
            String value = am.group(2);
            switch (key) {
                case "targetPackage": targetPackage = value; break;
                case "targetName": targetName = value; break;
                case "isStatic":
                    isStatic = "true".equalsIgnoreCase(value);
                    break;
                case "priority":
                    try {
                        priority = Integer.parseInt(value);
                    } catch (NumberFormatException ex) {
                        // ignore
                    }
                    break;
                default: break;
            }
        }

        // overlay 自身のパッケージ名
        String overlayPackage = "";
        Matcher pm = MANIFEST_PACKAGE.matcher(src);
        if (pm.find()) {
            overlayPackage = pm.group(1);
        }
        return new RroOverlay(overlayPackage, targetPackage, targetName,
                isStatic, priority, filePath);
    }

    /** プロジェクト下から AndroidManifest.xml を集める。 */
    private static void collectManifests(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                String name = c.getName();
                if (name.equals(".git") || name.equals(".gradle")
                        || name.equals("build") || name.equals("out")
                        || name.equals("node_modules")) {
                    continue;
                }
                collectManifests(c, out);
            } else if (c.isFile() && c.getName().equals("AndroidManifest.xml")) {
                out.add(c);
            }
        }
    }
}
