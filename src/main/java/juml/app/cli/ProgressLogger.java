// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import java.io.File;

/**
 * 複合 CLI 処理 ({@code --all} / {@code --sequence-diagrams} / {@code --init-flow} 等) 用の
 * 進捗ロガー。{@code -v} の有無に関わらず常に stderr に出力する。
 */
public final class ProgressLogger {

    public void step(String msg) {
        System.err.println("[juml] " + msg);
    }

    public void wrote(File f) {
        wrote(f, null);
    }

    public void wrote(File f, String suffix) {
        long size = f.exists() ? f.length() : 0L;
        StringBuilder sb = new StringBuilder("[juml]     -> ");
        sb.append(f.getName()).append(" (").append(formatBytes(size)).append(')');
        if (suffix != null && !suffix.isEmpty()) {
            sb.append(' ').append(suffix);
        }
        System.err.println(sb.toString());
    }

    public void done(File outDir, long elapsedMs) {
        System.err.println("[juml] Done in " + elapsedMs + " ms. "
                + "Output: " + outDir.getAbsolutePath());
    }

    private static String formatBytes(long n) {
        if (n < 1024) {
            return n + "B";
        }
        if (n < 1024 * 1024) {
            return (n / 1024) + "KB";
        }
        return String.format("%.1fMB", n / 1024.0 / 1024.0);
    }
}
