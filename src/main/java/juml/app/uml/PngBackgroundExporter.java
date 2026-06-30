// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.function.Consumer;

/**
 * PlantUML の PNG ラスタライズ (SourceStringReader#outputImage) は数百ms〜数秒かかる重い処理のため、
 * EDT 上で同期実行するとウィンドウ全体がフリーズする。このクラスはレンダリングと保存を
 * {@link SwingWorker} で背景実行し、結果のステータス更新・エラー通知だけを EDT へ戻す。
 *
 * <p>ファイル選択ダイアログ自体は呼び出し側が EDT で開き、確定したパスを渡すこと。</p>
 */
final class PngBackgroundExporter {

    private PngBackgroundExporter() {
    }

    /**
     * {@code puml} を PNG にレンダリングして {@code target} へ保存する。保存中は
     * {@code onStatus} へ「生成中」メッセージを、完了時は保存先パスを通知する。失敗時は
     * {@code dialogParent} を親にエラーダイアログを表示する。すべて呼び出しは EDT から行うこと。
     */
    static void save(String puml, File target, Component dialogParent, Consumer<String> onStatus) {
        onStatus.accept(Messages.get("status.exportingPng"));
        new SwingWorker<Void, Void>() {
            private Exception failure;

            @Override
            protected Void doInBackground() {
                try {
                    BufferedImage img = PlantUmlImageRenderer.toBufferedImage(puml);
                    UmlExporter.export(UmlExporter.Format.PNG, target, puml, img);
                } catch (Exception ex) {
                    failure = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (failure != null) {
                    juml.util.AppLog.error("PngBackgroundExporter",
                            "PNG render/export failed: " + target.getAbsolutePath(), failure);
                    String detail = failure.getMessage() != null
                            ? failure.getMessage() : failure.getClass().getSimpleName();
                    JOptionPane.showMessageDialog(dialogParent,
                            Messages.get("export.failed") + detail,
                            Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
                } else {
                    onStatus.accept(Messages.get("status.saved") + target.getAbsolutePath());
                }
            }
        }.execute();
    }
}
