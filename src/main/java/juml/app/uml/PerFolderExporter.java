// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.ClassIndex;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.PerFolderClassDiagrams;
import juml.util.ErrorListener;
import juml.util.Messages;
import juml.util.ProgressListener;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.io.File;
import java.util.List;

/**
 * 「Export Class Diagrams Per Folder…」の GUI ハンドラを {@link UmlMainFrame} から切り出した
 * ヘルパ。出力先の選択、{@link SwingWorker} 上での生成、進捗バーと完了ダイアログの表示までを
 * 行う。
 */
final class PerFolderExporter {

    private PerFolderExporter() {
    }

    /**
     * 出力ディレクトリを選ばせて、ロード済みプロジェクトのクラス図を
     * フォルダごとに書き出す。
     *
     * @param parent       親ウィンドウ (ダイアログ表示先)
     * @param projectRoot  プロジェクトルート
     * @param classes      対象クラス集合
     * @param index        ソースファイル解決用インデックス
     * @param loadProgress 進捗バー (非表示状態から見せて使う)
     * @param status       ステータス行 ({@link JLabel})
     */
    static void choose(final JFrame parent,
                       final File projectRoot,
                       final List<JavaClassInfo> classes,
                       final ClassIndex index,
                       final JProgressBar loadProgress,
                       final JLabel status) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Messages.get("dlg.chooseOutputDir"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File outDir = chooser.getSelectedFile();
        if (outDir == null) {
            return;
        }
        runAsync(parent, projectRoot, outDir, classes, index, loadProgress, status);
    }

    private static void runAsync(final JFrame parent,
                                  final File projectRoot,
                                  final File outDir,
                                  final List<JavaClassInfo> classes,
                                  final ClassIndex index,
                                  final JProgressBar loadProgress,
                                  final JLabel status) {
        loadProgress.setVisible(true);
        loadProgress.setIndeterminate(true);
        loadProgress.setString(Messages.get("export.perFolder.generating"));
        status.setText(Messages.get("export.perFolder.exporting"));

        final ProgressListener progress = ProgressListener.throttled((done, total, message) ->
                SwingUtilities.invokeLater(() ->
                        updateBar(loadProgress, status, done, total, message)),
                150L);

        new SwingWorker<PerFolderClassDiagrams.Result, Void>() {
            private Throwable error;

            @Override
            protected PerFolderClassDiagrams.Result doInBackground() {
                try {
                    return PerFolderClassDiagrams.generate(
                            projectRoot, outDir, classes, index, null,
                            progress, ErrorListener.silent());
                } catch (Throwable t) {
                    error = t;
                    return null;
                }
            }

            @Override
            protected void done() {
                resetBar(loadProgress);
                if (error != null) {
                    juml.util.AppLog.error(juml.util.ErrorCode.EXP_005, "PerFolderExporter",
                            "Per-folder class diagram export failed: "
                                    + outDir.getAbsolutePath(), error);
                    status.setText(" ");
                    JOptionPane.showMessageDialog(parent,
                            java.text.MessageFormat.format(
                                    Messages.get("export.perFolder.failed"), error.getMessage()),
                            Messages.get("dlg.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                PerFolderClassDiagrams.Result result;
                try {
                    result = get();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                            java.text.MessageFormat.format(
                                    Messages.get("export.perFolder.failed"), ex.getMessage()),
                            Messages.get("dlg.error"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (result == null) {
                    return;
                }
                status.setText(java.text.MessageFormat.format(
                        Messages.get("export.perFolder.statusDone"),
                        result.getFolderCount(), outDir.getAbsolutePath()));
                JOptionPane.showMessageDialog(parent,
                        java.text.MessageFormat.format(
                                Messages.get("export.perFolder.done"),
                                result.getFolderCount(), result.getClassCount(),
                                outDir.getAbsolutePath()),
                        Messages.get("export.perFolder.doneTitle"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }.execute();
    }

    private static void updateBar(JProgressBar bar, JLabel status,
                                    int done, int total, String message) {
        if (total > 0) {
            if (bar.isIndeterminate()) {
                bar.setIndeterminate(false);
            }
            bar.setMaximum(total);
            bar.setValue(Math.min(done, total));
            bar.setString(done + "/" + total);
            String progressMsg = done + "/" + total;
            if (message != null && !message.isEmpty()) {
                progressMsg += " — " + message;
            }
            status.setText(java.text.MessageFormat.format(
                    Messages.get("export.perFolder.progress"), progressMsg));
        } else {
            bar.setIndeterminate(true);
            bar.setString(message != null ? message : Messages.get("export.perFolder.working"));
        }
    }

    private static void resetBar(JProgressBar bar) {
        bar.setIndeterminate(false);
        bar.setValue(0);
        bar.setString(null);
        bar.setVisible(false);
    }
}
