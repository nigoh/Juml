// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import juml.core.formats.java.AndroidProjectScanner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * プロジェクト下の VINTF manifest / compatibility-matrix XML を走査して解析する。
 *
 * <p>ファイル名が {@code manifest*.xml} (例: {@code manifest.xml},
 * {@code manifest_media.xml}) または {@code compatibility_matrix*.xml}
 * (例: {@code compatibility_matrix.xml}, {@code compatibility_matrix.8.xml}) の
 * ものを候補として {@link VintfManifestParser} でパースし、VINTF と判定できた
 * ({@link VintfManifest.Kind#UNKNOWN} でない) ものだけを返す。
 * {@code AndroidManifest.xml} 等の紛らわしい XML はパーサ側がルート要素で除外する。</p>
 */
public final class VintfProjectScanner {

    /** 1 ファイル分の解析結果 (ファイルパスと manifest のペア)。 */
    public static final class Entry {
        private final String file;
        private final VintfManifest manifest;

        public Entry(String file, VintfManifest manifest) {
            this.file = file == null ? "" : file;
            this.manifest = manifest;
        }

        /** 解析元ファイルのパス。 */
        public String getFile() {
            return file;
        }

        /** 解析結果の manifest モデル。 */
        public VintfManifest getManifest() {
            return manifest;
        }
    }

    /** プロジェクト全体を走査し、VINTF と判定できたファイルの解析結果を返す。 */
    public List<Entry> analyzeProject(File projectRoot) {
        if (projectRoot == null || !projectRoot.isDirectory()) {
            return Collections.emptyList();
        }
        List<File> candidates = new ArrayList<>();
        collectCandidates(projectRoot, candidates);
        List<Entry> out = new ArrayList<>();
        for (File f : candidates) {
            try {
                String xml = AndroidProjectScanner.readFile(f);
                VintfManifest m = VintfManifestParser.parse(xml);
                if (m.getKind() != VintfManifest.Kind.UNKNOWN) {
                    out.add(new Entry(f.getPath(), m));
                }
            } catch (IOException ex) {
                // 読み取り失敗はスキップ
            }
        }
        return out;
    }

    /** VINTF manifest の候補ファイル名か (小文字比較)。 */
    static boolean isVintfCandidateName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xml")) {
            return false;
        }
        return name.startsWith("manifest") || name.startsWith("compatibility_matrix");
    }

    /** プロジェクト下を再帰走査して候補 XML を集める (AOSP 級除外を適用)。 */
    private static void collectCandidates(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File c : children) {
            if (c.isDirectory()) {
                if (AospScanExcludes.shouldSkip(c.getName())) {
                    continue;
                }
                collectCandidates(c, out);
            } else if (c.isFile() && isVintfCandidateName(c.getName())) {
                out.add(c);
            }
        }
    }
}
