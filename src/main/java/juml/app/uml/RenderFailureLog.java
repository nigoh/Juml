// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.AppLog;
import juml.util.ErrorCode;
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
     * 描画失敗の原因をエラー ID に分類する。
     *
     * <p>PlantUML 由来の失敗 ({@link juml.core.formats.uml.PlantUmlRenderFailedException})
     * はレンダラが分類済みの ID (UML-R001 構文 / UML-R002 レイアウト等) を持つ。
     * エディタタブでは編集内容起因なので UML-E 系へ読み替える (生成図での構文エラーは
     * Juml 側の生成不具合、エディタでの構文エラーはユーザ編集起因と、対処が異なるため)。
     * メモリ不足は UML-R003、それ以外は未分類 UML-R007。</p>
     *
     * @param error  発生した例外 (null 可)
     * @param editor エディタタブでの失敗なら true
     */
    static ErrorCode classify(Throwable error, boolean editor) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof OutOfMemoryError) {
                return ErrorCode.UML_R003;
            }
            t = t.getCause();
        }
        if (error instanceof juml.core.formats.uml.PlantUmlRenderFailedException) {
            ErrorCode code = ((juml.core.formats.uml.PlantUmlRenderFailedException) error)
                    .getErrorCode();
            if (editor) {
                if (code == ErrorCode.UML_R001) {
                    return ErrorCode.UML_E001;
                }
                if (code == ErrorCode.UML_R002) {
                    return ErrorCode.UML_E002;
                }
            }
            return code;
        }
        return ErrorCode.UML_R007;
    }

    /**
     * 描画失敗を記録する。失敗した PlantUML をファイルへ保存し、例外を AppLog へ出力する。
     *
     * <p>エディタタブ ({@code editor=true}) ではファイル保存を行わない。ライブプレビューは
     * 入力ポーズ (600ms) のたびに編集途中のテキストで描画され、編集中はほぼ常に構文エラーに
     * なるため、毎回 EDT 上で logs/ の走査+書込が走るうえ、{@link #MAX_DUMPS} の保存枠が
     * 編集途中の一時テキストで埋まり、本来の目的である「生成図の失敗の報告用保存」が
     * 押し出されてしまう。エディタの失敗テキストはエディタ自身に表示されているため
     * ファイル保存は不要で、AppLog への記録だけ行う。</p>
     *
     * @param label  図のラベル (タブ名等、ログの識別用)
     * @param puml   失敗した生成 PlantUML (null 可 = 生成前に失敗)
     * @param error  発生した例外
     * @param editor エディタタブでの失敗なら true (エラー ID の分類とファイル保存の抑制に使う)
     * @return 保存した .puml ファイル。保存しなかった (puml が null / エディタ) 場合は null。
     */
    static File dump(String label, String puml, Throwable error, boolean editor) {
        File saved = null;
        if (!editor && puml != null && !puml.isEmpty()) {
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
                AppLog.warn(ErrorCode.UML_R005, "RenderFailureLog",
                        "failed to save failing PlantUML", e);
                saved = null;
            }
        }
        ErrorCode code = classify(error, editor);
        StringBuilder msg = new StringBuilder("render failed: ").append(label);
        if (saved != null) {
            msg.append(" — failing PlantUML saved to ").append(saved.getAbsolutePath());
        }
        if (error != null) {
            AppLog.error(code, "DiagramTab", msg.toString(), error);
        } else {
            AppLog.error(code, "DiagramTab", msg.toString());
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
