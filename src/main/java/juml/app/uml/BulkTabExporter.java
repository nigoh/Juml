// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.AppLog;
import juml.util.ErrorCode;
import juml.util.ErrorListener;
import juml.util.Messages;
import juml.util.ProgressListener;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 「Export All Open Tabs…」の実装。現在開いている全ダイアグラムタブを、選んだ 1 形式
 * (SVG / PNG / PlantUML) で 1 つの出力ディレクトリへ一括保存する。
 *
 * <p>VS Code 風のタブ中心モデル ({@code .claude/rules/gui-tab-architecture.md}) に沿った
 * 「今開いている図をまとめて出す」操作で、図種を問わず ({@link SketchDiagramType} の 10 種や
 * クラス図・パッケージ図など) 動作する。既存の {@link PerFolderExporter} (クラス図をフォルダ別に
 * 再生成) や CLI {@code --all} (固定成果物) とは役割が重複しない純粋な追加機能。</p>
 *
 * <p><b>スコープ (v1):</b> 各タブの<b>素の図</b>のみを出力する。プレビュー上の付箋メモ
 * オーバーレイは 1 タブ単位のエクスポート ({@link ExportController}) の機能であり、一括では
 * 含めない (全形式で挙動を揃え、半端な形式差を作らないため)。未描画のタブ (プレビュー未生成・
 * メモリ退避済み) は明示的にスキップし、完了ダイアログにスキップ件数を示す。</p>
 *
 * <p>ファイル書き出しの中核 ({@link #exportAll}) は Swing に触れないため headless で単体
 * テストでき、Swing 配線 ({@link #choose} / {@code SwingWorker}) と分離してある
 * ({@link juml.core.formats.uml.PerFolderClassDiagrams} と同じ「コア厚・配線薄」パターン)。</p>
 */
final class BulkTabExporter {

    private BulkTabExporter() {
    }

    /**
     * 一括エクスポート 1 タブ分の不変スナップショット (Swing に触れない)。
     *
     * <p>形式は<b>スナップショットを採った後に</b>選ばれる ({@link #choose}) ため、両方の
     * テキストを持つ。{@code .puml} はソースの書き出しなのでエディタタブでは編集中の
     * バッファ、SVG/PNG は「いま見えている図」なので最後に描けたテキストを使う。1 つに
     * まとめると、どちらかの形式が必ず間違ったテキストを受け取る。</p>
     */
    static final class Snapshot {
        /** タブヘッダのラベル (ファイル名の元)。 */
        final String label;
        /** タブ識別キー (ラベル衝突時のフォールバックに使う)。 */
        final String key;
        /** ソースとしての PlantUML (エディタタブは編集中のバッファ)。 */
        final String sourcePuml;
        /** 最後に描画できた PlantUML (画像出力用。null/空 = 未描画)。 */
        final String renderedPuml;

        Snapshot(String label, String key, String sourcePuml, String renderedPuml) {
            this.label = label;
            this.key = key;
            this.sourcePuml = sourcePuml;
            this.renderedPuml = renderedPuml;
        }

        /** その形式で書き出すべきテキスト。 */
        String forFormat(UmlExporter.Format fmt) {
            return fmt == UmlExporter.Format.PUML ? sourcePuml : renderedPuml;
        }
    }

    /** 一括エクスポートの集計結果。 */
    static final class Result {
        final int exported;
        final int skipped;
        /** 出力に失敗したタブの「ラベル: 理由」一覧 (空なら全成功)。 */
        final List<String> failures;

        Result(int exported, int skipped, List<String> failures) {
            this.exported = exported;
            this.skipped = skipped;
            this.failures = failures;
        }
    }

    // -------------------------------------------------------------------------
    // GUI 配線 (ディレクトリ + 形式を選ばせて SwingWorker で実行)
    // -------------------------------------------------------------------------

    /**
     * 出力ディレクトリと形式を選ばせ、{@code tabs} を一括エクスポートする。EDT で呼ぶこと。
     *
     * @param parent 親ウィンドウ (ダイアログ表示先)
     * @param tabs   開いているタブのスナップショット (描画順)
     * @param bar    進捗バー (非表示状態から見せて使う)
     * @param status ステータス行
     */
    static void choose(JFrame parent, List<Snapshot> tabs, JProgressBar bar, JLabel status) {
        if (tabs == null || tabs.isEmpty()) {
            JOptionPane.showMessageDialog(parent, Messages.get("export.allTabs.none"),
                    Messages.get("export.allTabs.title"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<String> fmtCombo = new JComboBox<>(new String[] {"SVG", "PNG", "PlantUML"});
        JPanel accessory = new JPanel(new BorderLayout(4, 4));
        accessory.add(new JLabel(Messages.get("export.allTabs.format")), BorderLayout.NORTH);
        accessory.add(fmtCombo, BorderLayout.CENTER);

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Messages.get("export.allTabs.chooseDir"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAccessory(accessory);
        applyLastDirectory(chooser);
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File outDir = chooser.getSelectedFile();
        if (outDir == null) {
            return;
        }
        // DIRECTORIES_ONLY のチューザは「まだ無いフォルダ名を打ち込んで保存」を
        // そのまま承認する (新しい出力先を作る普通の手順)。作らずに進むと全タブが
        // 「保存先が無い」で失敗し、完了ダイアログにタブ数ぶんの失敗行が並ぶだけで、
        // 本当の原因 (フォルダが無い) はどこにも出ない。ここで作る
        // (利用者がチューザで明示的に名前を決めているので、打ち間違いの黙殺ではない)。
        if (!outDir.isDirectory() && !outDir.mkdirs()) {
            JOptionPane.showMessageDialog(parent,
                    Messages.get("export.allTabs.cannotCreateDir") + outDir.getPath(),
                    Messages.get("export.allTabs.chooseDir"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        UmlExporter.Format fmt;
        switch (fmtCombo.getSelectedIndex()) {
            case 0:  fmt = UmlExporter.Format.SVG; break;
            case 1:  fmt = UmlExporter.Format.PNG; break;
            default: fmt = UmlExporter.Format.PUML; break;
        }
        rememberDirectory(outDir);
        runAsync(parent, new ArrayList<>(tabs), outDir, fmt, bar, status);
    }

    private static void runAsync(JFrame parent, List<Snapshot> tabs, File outDir,
                                 UmlExporter.Format fmt, JProgressBar bar, JLabel status) {
        bar.setVisible(true);
        bar.setIndeterminate(false);
        bar.setMaximum(tabs.size());
        bar.setValue(0);
        bar.setString("0/" + tabs.size());
        status.setText(Messages.get("export.allTabs.exporting"));

        final ProgressListener progress = ProgressListener.throttled((done, total, message) ->
                SwingUtilities.invokeLater(() -> updateBar(bar, done, total)), 120L);

        new SwingWorker<Result, Void>() {
            @Override
            protected Result doInBackground() {
                return exportAll(tabs, outDir, fmt, progress, ErrorListener.silent());
            }

            @Override
            protected void done() {
                resetBar(bar);
                Result result;
                try {
                    result = get();
                } catch (Exception ex) {
                    status.setText(" ");
                    JOptionPane.showMessageDialog(parent,
                            Messages.get("export.failed") + ex.getMessage(),
                            Messages.get("dlg.error.title"), JOptionPane.ERROR_MESSAGE);
                    return;
                }
                showCompletion(parent, status, result, outDir);
            }
        }.execute();
    }

    private static void showCompletion(JFrame parent, JLabel status, Result r, File outDir) {
        status.setText(MessageFormat.format(Messages.get("export.allTabs.status"),
                r.exported, outDir.getAbsolutePath()));
        if (!r.failures.isEmpty()) {
            String detail = String.join("\n", r.failures);
            JOptionPane.showMessageDialog(parent,
                    MessageFormat.format(Messages.get("export.allTabs.someFailed"),
                            r.exported, r.skipped, r.failures.size(), detail),
                    Messages.get("export.allTabs.doneTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String msg = r.skipped > 0
                ? MessageFormat.format(Messages.get("export.allTabs.doneSkipped"),
                        r.exported, r.skipped, outDir.getAbsolutePath())
                : MessageFormat.format(Messages.get("export.allTabs.done"),
                        r.exported, outDir.getAbsolutePath());
        JOptionPane.showMessageDialog(parent, msg,
                Messages.get("export.allTabs.doneTitle"), JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------------------------------------------------------------------
    // コア (Swing 非依存・headless テスト可能)
    // -------------------------------------------------------------------------

    /**
     * {@code tabs} を {@code outDir} へ {@code fmt} で書き出す。未描画タブ (puml が null/空) は
     * スキップし、1 タブの失敗は他タブを止めずに集計だけする (部分成功を許す)。
     *
     * @return 出力/スキップ/失敗の集計
     */
    static Result exportAll(List<Snapshot> tabs, File outDir, UmlExporter.Format fmt,
                            ProgressListener progress, ErrorListener errors) {
        ProgressListener prog = progress != null ? progress : ProgressListener.silent();
        List<String> labels = new ArrayList<>();
        for (Snapshot t : tabs) {
            labels.add(t.label);
        }
        List<String> names = planFileNames(labels, fmt.getExtension());
        int exported = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();
        int total = tabs.size();
        for (int i = 0; i < total; i++) {
            Snapshot t = tabs.get(i);
            prog.onProgress(i, total, t.label);
            String puml = t.forFormat(fmt);
            if (puml == null || puml.isBlank()) {
                skipped++;
                continue;
            }
            File out = new File(outDir, names.get(i));
            try {
                writeOne(fmt, out, puml);
                exported++;
            } catch (Exception ex) {
                failures.add(t.label + ": " + ex.getMessage());
                AppLog.error(ErrorCode.EXP_008, "BulkTabExporter",
                        "Bulk tab export failed for '" + t.label + "' -> "
                                + out.getAbsolutePath(), ex);
                if (errors != null) {
                    errors.onError(ErrorCode.EXP_008, "BulkTabExporter", -1,
                            t.label + ": " + ex.getMessage());
                }
            }
        }
        prog.onProgress(total, total, null);
        return new Result(exported, skipped, failures);
    }

    /** 1 タブを指定形式で書き出す (SVG/PNG/PlantUML)。 */
    private static void writeOne(UmlExporter.Format fmt, File out, String puml) throws Exception {
        switch (fmt) {
            case SVG:
                // UmlExporter を通す (PNG/PUML と同じ原子的置換にする)。以前はここだけ
                // renderSvg(File) を直接呼んでおり、対象を切り詰めてから描画し、失敗時は
                // ファイルごと削除していた = 前回の正しい SVG が消える。同じ操作なのに
                // 形式によって上書きの安全性が変わっていた。
                UmlExporter.export(UmlExporter.Format.SVG, out, puml, null);
                break;
            case PNG:
                BufferedImage img = PlantUmlImageRenderer.toBufferedImage(puml);
                UmlExporter.export(UmlExporter.Format.PNG, out, puml, img);
                break;
            default:
                UmlExporter.export(UmlExporter.Format.PUML, out, puml, null);
                break;
        }
    }

    /**
     * タブラベル群を、拡張子付きで<b>衝突しない</b>ファイル名一覧へ変換する (入力と同じ順序・
     * 同じ件数)。ラベルは {@link ExportController#suggestBaseName} でサニタイズし、空になれば
     * {@code "diagram"} を用いる。既出の名前とぶつかる場合は {@code _2}, {@code _3}… を付す。
     */
    static List<String> planFileNames(List<String> labels, String ext) {
        List<String> out = new ArrayList<>(labels.size());
        Set<String> used = new LinkedHashSet<>();
        for (String label : labels) {
            String base = ExportController.suggestBaseName(label);
            if (base == null) {
                base = "diagram";
            }
            String candidate = base;
            int n = 2;
            while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
                candidate = base + "_" + n++;
            }
            out.add(candidate + "." + ext);
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // ディレクトリ記憶 / 進捗バー (PerFolderExporter と同じ流儀)
    // -------------------------------------------------------------------------

    private static void applyLastDirectory(JFileChooser fc) {
        try {
            String saved = juml.SettingManager.getInstance().getSetting().getLastExportDirectory();
            if (saved != null && !saved.isEmpty()) {
                File d = new File(saved);
                if (d.isDirectory()) {
                    fc.setCurrentDirectory(d);
                }
            }
        } catch (RuntimeException ignored) {
            // SettingManager 未初期化 (テスト) や破損時は既定位置のまま。
        }
    }

    private static void rememberDirectory(File dir) {
        if (dir == null) {
            return;
        }
        try {
            juml.SettingManager sm = juml.SettingManager.getInstance();
            sm.getSetting().setLastExportDirectory(dir.getAbsolutePath());
            sm.save();
        } catch (RuntimeException ignored) {
            // SettingManager 未初期化 (テスト) では記憶しない。
        }
    }

    private static void updateBar(JProgressBar bar, int done, int total) {
        if (total > 0) {
            // 進捗バーはプロジェクト読込と共有のため、相手の done() で隠されていても
            // 自分の進捗更新時に可視状態を取り戻す (bug-hunt R2)。
            bar.setVisible(true);
            bar.setIndeterminate(false);
            bar.setMaximum(total);
            bar.setValue(Math.min(done, total));
            bar.setString(done + "/" + total);
        }
    }

    private static void resetBar(JProgressBar bar) {
        bar.setIndeterminate(false);
        bar.setValue(0);
        bar.setString(null);
        bar.setVisible(false);
    }
}
