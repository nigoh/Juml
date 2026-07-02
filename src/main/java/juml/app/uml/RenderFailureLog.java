// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.AppLog;
import juml.util.PathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * GUI での図描画失敗を報告可能な形で残すユーティリティ。
 *
 * <p>失敗した生成 PlantUML を {@code logs/render-failed-*.puml} として保存し、
 * 例外の詳細 (スタックトレース含む) を {@link AppLog} に記録する。
 * これによりユーザは「どの図が・どの PlantUML で・なぜ」失敗したかを
 * ログビューア / logs ディレクトリからそのまま報告できる。</p>
 */
final class RenderFailureLog {

    /** 保存する失敗 PlantUML の最大件数。超過した古いものから削除する。 */
    private static final int MAX_DUMPS = 20;

    private RenderFailureLog() {
    }

    /**
     * 描画失敗を記録する。失敗した PlantUML をファイルへ保存し、例外を AppLog へ出力する。
     *
     * @param label 図のラベル (タブ名等、ログの識別用)
     * @param puml  失敗した生成 PlantUML (null 可 = 生成前に失敗)
     * @param error 発生した例外
     * @return 保存した .puml ファイル。保存できなかった (puml が null 等) 場合は null。
     */
    static File dump(String label, String puml, Throwable error) {
        File saved = null;
        if (puml != null && !puml.isEmpty()) {
            try {
                File dir = new File(PathUtil.getBasePath(), "logs");
                if (dir.isDirectory() || dir.mkdirs()) {
                    pruneOldDumps(dir);
                    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS")
                            .format(new Date());
                    saved = new File(dir, "render-failed-" + stamp + ".puml");
                    Files.write(saved.toPath(), puml.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException | RuntimeException e) {
                AppLog.warn("RenderFailureLog", "failed to save failing PlantUML", e);
                saved = null;
            }
        }
        StringBuilder msg = new StringBuilder("render failed: ").append(label);
        if (saved != null) {
            msg.append(" — failing PlantUML saved to ").append(saved.getAbsolutePath());
        }
        if (error != null) {
            AppLog.error("DiagramTab", msg.toString(), error);
        } else {
            AppLog.error("DiagramTab", msg.toString());
        }
        return saved;
    }

    /** {@code render-failed-*.puml} が {@link #MAX_DUMPS} 件を超えないよう古い順に削除する。 */
    private static void pruneOldDumps(File dir) {
        File[] dumps = dir.listFiles((d, name) ->
                name.startsWith("render-failed-") && name.endsWith(".puml"));
        if (dumps == null || dumps.length < MAX_DUMPS) {
            return;
        }
        Arrays.sort(dumps, Comparator.comparing(File::getName));
        for (int i = 0; i <= dumps.length - MAX_DUMPS; i++) {
            if (!dumps[i].delete()) {
                dumps[i].deleteOnExit();
            }
        }
    }
}
