// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Soong の {@code out/soong/.intermediates/} ツリーを走査し、モジュール別の中間生成物在庫を集計する。
 *
 * <p>典型的なパス構造:</p>
 * <pre>
 *   .intermediates/&lt;module-path...&gt;/&lt;module-name&gt;/&lt;variant&gt;/&lt;artifacts...&gt;
 *   例: .intermediates/frameworks/base/framework/android_common/combined/framework.jar
 * </pre>
 *
 * <p>variant セグメント (例 {@code android_common} / {@code android_arm64_armv8-a_shared})
 * を {@link #isVariantSegment(String)} のヒューリスティックで検出し、その直前を module-name、
 * さらに手前を module-path とみなす (ベストエフォート)。成果物は拡張子で種別分類する。</p>
 */
public final class IntermediatesAnalyzer {

    /** 走査ファイル数の上限 (これを超えると打ち切り)。 */
    private long maxFiles = 2_000_000L;

    public IntermediatesAnalyzer() {
    }

    public IntermediatesAnalyzer maxFiles(long maxFiles) {
        this.maxFiles = maxFiles;
        return this;
    }

    /** プロジェクト下の {@code .intermediates} ディレクトリを探して在庫を集計する。 */
    public IntermediatesInventory analyzeProject(File projectRoot) throws IOException {
        IntermediatesInventory inv = new IntermediatesInventory();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return inv;
        }
        List<File> roots = new ArrayList<>();
        if (projectRoot.getName().equals(".intermediates")) {
            roots.add(projectRoot);
        } else {
            collectIntermediatesDirs(projectRoot, roots, 0);
        }
        for (File root : roots) {
            scanIntermediates(root, inv);
            if (inv.isTruncated()) {
                break;
            }
        }
        return inv;
    }

    /** 1 つの {@code .intermediates} ディレクトリ配下を走査する。 */
    private void scanIntermediates(File intermediatesDir, IntermediatesInventory inv) {
        java.util.ArrayDeque<File> stack = new java.util.ArrayDeque<>();
        stack.push(intermediatesDir);
        int base = intermediatesDir.getPath().length();
        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] children = dir.listFiles();
            if (children == null) {
                continue;
            }
            for (File c : children) {
                if (c.isDirectory()) {
                    // symlink の循環で無限走査にならないよう、ディレクトリの
                    // symlink は辿らない (兄弟走査は深さ制限で守られているが、
                    // この walker はスタック式で深さ上限が無い)。
                    if (java.nio.file.Files.isSymbolicLink(c.toPath())) {
                        continue;
                    }
                    stack.push(c);
                } else if (c.isFile()) {
                    if (inv.getTotalFiles() >= maxFiles) {
                        inv.markTruncated();
                        return;
                    }
                    recordFile(intermediatesDir, c, inv, base);
                }
            }
        }
    }

    /** 1 ファイルを在庫に記録する。 */
    private static void recordFile(File intermediatesDir, File file,
                                   IntermediatesInventory inv, int basePathLen) {
        inv.addFile(file.length());
        // .intermediates からの相対セグメント列
        String rel = file.getPath().substring(Math.min(basePathLen, file.getPath().length()));
        String[] seg = rel.replace('\\', '/').split("/");
        // 先頭の空要素 (区切り由来) を除去
        List<String> segs = new ArrayList<>();
        for (String s : seg) {
            if (!s.isEmpty()) {
                segs.add(s);
            }
        }
        if (segs.isEmpty()) {
            return;
        }
        int variantIdx = -1;
        for (int i = 0; i < segs.size(); i++) {
            if (isVariantSegment(segs.get(i))) {
                variantIdx = i;
                break;
            }
        }
        String moduleName;
        String modulePath;
        String variant;
        if (variantIdx >= 1) {
            moduleName = segs.get(variantIdx - 1);
            variant = segs.get(variantIdx);
            StringBuilder mp = new StringBuilder();
            for (int i = 0; i < variantIdx - 1; i++) {
                if (mp.length() > 0) mp.append('/');
                mp.append(segs.get(i));
            }
            modulePath = mp.toString();
        } else {
            // variant が見つからない: 最初のセグメントをモジュール名とみなす
            moduleName = segs.get(0);
            modulePath = "";
            variant = "(unknown)";
        }
        IntermediateModule mod = inv.moduleFor(modulePath, moduleName);
        mod.addVariant(variant);
        mod.addArtifact(kindOf(file.getName()), file.length());
    }

    /**
     * variant ディレクトリらしいセグメントか判定するヒューリスティック。
     * Soong の variant ディレクトリは OS トークン ({@code android_} /
     * {@code linux_*} / {@code host_} など) で始まる。以前は
     * {@code _static} / {@code _common} などの接尾辞や {@code _arm64} の部分一致
     * でも variant 扱いしていたが、{@code libcrypto_static} や {@code foo_common}
     * のような実在のモジュール名まで variant と誤認し、同ディレクトリの別モジュール
     * が 1 つに合成されてしまうため、OS プレフィックスのみで判定する。
     */
    static boolean isVariantSegment(String seg) {
        if (seg == null || seg.isEmpty()) {
            return false;
        }
        return seg.startsWith("android_") || seg.startsWith("linux_")
                || seg.startsWith("host_") || seg.startsWith("windows_")
                || seg.startsWith("darwin_");
    }

    /** ファイル名から成果物種別 (拡張子) を返す。拡張子なしは {@code (none)}。 */
    static String kindOf(String fileName) {
        if (fileName == null) {
            return "(none)";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "(none)";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /** プロジェクト下を再帰走査して {@code .intermediates} ディレクトリを集める。 */
    private static void collectIntermediatesDirs(File dir, List<File> out, int depth) {
        if (depth > 12) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (!c.isDirectory()) {
                continue;
            }
            String name = c.getName();
            if (name.equals(".git") || name.equals(".repo")) {
                continue;
            }
            if (name.equals(".intermediates")) {
                out.add(c);
                // .intermediates 配下はモジュールツリーなので、それ以上ネストした
                // .intermediates は探さない (走査コスト削減)。
                continue;
            }
            collectIntermediatesDirs(c, out, depth + 1);
        }
    }
}
