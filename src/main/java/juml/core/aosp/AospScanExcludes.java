// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aosp;

import juml.core.formats.java.AndroidProjectScanner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * AOSP 系パーサ (Android.bp / Android.mk / VINTF) が共有するディレクトリ走査の除外規則。
 *
 * <p>{@link AndroidProjectScanner#DEFAULT_EXCLUDED_DIRS} ({@code build / out / .git} 等) と
 * {@link AndroidProjectScanner#AOSP_EXTRA_EXCLUDED_DIRS} ({@code prebuilts / out-soong /
 * .repo} 等) の和集合を使う。AOSP フルツリーを指定したときに prebuilts 配下の
 * Android.bp などを拾ってノイズ・性能劣化するのを防ぎ、Java ソース走査側の既定除外と
 * 判定を揃える。</p>
 */
final class AospScanExcludes {

    /** 走査から除外するディレクトリ名の集合 (既定 + AOSP 追加の和集合)。 */
    private static final Set<String> SKIP_DIRS;
    static {
        Set<String> s = new HashSet<>(AndroidProjectScanner.DEFAULT_EXCLUDED_DIRS);
        s.addAll(AndroidProjectScanner.AOSP_EXTRA_EXCLUDED_DIRS);
        SKIP_DIRS = Collections.unmodifiableSet(s);
    }

    private AospScanExcludes() {
    }

    /** 指定ディレクトリ名を走査から除外すべきか。 */
    static boolean shouldSkip(String dirName) {
        return SKIP_DIRS.contains(dirName);
    }
}
