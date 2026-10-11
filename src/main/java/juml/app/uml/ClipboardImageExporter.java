// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * 表示中の図を「画像 (ビットマップ)」としてシステムクリップボードへコピーする補助クラス。
 *
 * <p>Juml の従来の「コピー」はすべてテキスト (SVG XML / PlantUML ソース) をクリップボードへ
 * 置いていたため、Slack・Google Docs/Slides・Confluence・GitHub Issue・メール等へ貼ると
 * 画像ではなく文字列として貼られてしまっていた。これらの宛先が受け取れる
 * {@link java.awt.datatransfer.DataFlavor#imageFlavor} の {@link BufferedImage} を渡す。</p>
 *
 * <p>まず「見たまま」(付箋込み) を EDT でラスタライズし、プレビュー未表示等で取得できない
 * ときのみ PlantUML テキストから背景レンダリングへフォールバックする。</p>
 */
final class ClipboardImageExporter {

    /** クリップボード画像の解像度倍率 (プレビューの見た目より高精細に貼れるよう 2x)。 */
    private static final double SCALE = 2.0;

    private ClipboardImageExporter() {
    }

    /**
     * 図の画像をクリップボードへコピーする。すべての呼び出しは EDT から行うこと。
     *
     * @param preview  アクティブ図のプレビュー (付箋込みの見たまま画像を得るため。null 可)
     * @param puml     フォールバック用の PlantUML テキスト (preview から取得できない場合に使用)
     * @param parent   エラーダイアログの親
     * @param onStatus ステータス表示先
     */
    static void copy(SvgPreviewPanel preview, String puml, Component parent,
                     Consumer<String> onStatus) {
        // 1) 付箋があるときだけ、表示中の図 (付箋込み) を EDT でラスタライズする (savePng と同じ
        //    経路)。付箋が無ければ背景レンダリング経路へ落とし、巨大図の 2x ラスタライズで
        //    EDT が秒単位で固まるのを避ける。OOM は捕捉して同じく背景経路へ (bug-hunt R2)。
        BufferedImage shown = null;
        if (preview != null && preview.notes().hasNotes()) {
            try {
                shown = preview.renderDiagramWithNotes(SCALE);
            } catch (OutOfMemoryError oom) {
                juml.util.AppLog.error(juml.util.ErrorCode.EXP_006, "ClipboardImageExporter",
                        "on-screen rasterize ran out of memory; falling back to background render",
                        oom);
            }
        }
        if (shown != null) {
            putOnClipboard(shown, parent, onStatus);
            return;
        }
        // 2) プレビューが未描画等で取れなければ PlantUML から背景レンダリング。
        if (puml == null || puml.isEmpty()) {
            onStatus.accept(Messages.get("export.noDiagram"));
            return;
        }
        onStatus.accept(Messages.get("status.copyingImage"));
        new SwingWorker<BufferedImage, Void>() {
            private Exception failure;

            @Override
            protected BufferedImage doInBackground() {
                try {
                    return PlantUmlImageRenderer.toBufferedImage(puml);
                } catch (Exception ex) {
                    failure = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                BufferedImage img = null;
                try {
                    img = get();
                } catch (Exception ignore) {
                    // failure に既に理由を保持。null のまま失敗として扱う。
                }
                if (failure != null || img == null) {
                    juml.util.AppLog.error(
                            juml.util.JumlException.codeOf(failure, juml.util.ErrorCode.EXP_006),
                            "ClipboardImageExporter",
                            "diagram image render for clipboard failed", failure);
                    String detail = failure != null && failure.getMessage() != null
                            ? failure.getMessage() : Messages.get("export.emptyDiagram");
                    JOptionPane.showMessageDialog(parent,
                            Messages.get("export.copyFailed") + detail,
                            Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
                    onStatus.accept(Messages.get("export.copyFailed") + detail);
                } else {
                    putOnClipboard(img, parent, onStatus);
                }
            }
        }.execute();
    }

    /** 画像をクリップボードへ置き、成功/失敗をステータスへ反映する。 */
    private static void putOnClipboard(BufferedImage img, Component parent,
                                       Consumer<String> onStatus) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new ImageTransferable(img), null);
            onStatus.accept(Messages.get("export.imageCopied"));
        } catch (RuntimeException ex) {
            juml.util.AppLog.error(juml.util.ErrorCode.EXP_002, "ClipboardImageExporter",
                    "image copy to clipboard failed", ex);
            JOptionPane.showMessageDialog(parent,
                    Messages.get("export.copyFailed") + ex.getMessage(),
                    Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
