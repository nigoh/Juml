// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import juml.app.uml.ProjectAnalysisCache;
import juml.app.uml.ReferenceIndexCache;
import juml.core.insights.InsightsAnalyzer;
import juml.core.insights.InsightsModel;
import juml.core.insights.MarkdownInsightsReport;
import juml.core.refs.ReferenceIndex;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * アーキテクチャ俯瞰 (Insights) レポートを表示するパネル。
 *
 * <p>「Analyze」ボタンで {@link InsightsAnalyzer} を実行し、エントリポイント /
 * ホットスポット / パッケージ循環 / デッドコード候補 / 推定レイヤを
 * Markdown テキストとして結果エリアに表示する (CLI {@code --insights} と同内容)。</p>
 *
 * <p>{@link ReferenceIndexCache} 経由で Impact / References パネルと
 * 逆参照インデックスを共有する。初回構築は重いので {@link SwingWorker} で
 * バックグラウンド実行する。</p>
 */
public final class InsightsPanel extends JPanel {

    private final ProjectAnalysisCache projectCache;
    private final ReferenceIndexCache refCache;
    private final JButton runButton = new JButton("Analyze");
    private final JButton saveButton = new JButton("Save Report...");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea resultArea = new JTextArea();

    public InsightsPanel(ProjectAnalysisCache projectCache, ReferenceIndexCache refCache) {
        super(new BorderLayout(0, 4));
        if (projectCache == null || refCache == null) {
            throw new IllegalArgumentException("projectCache/refCache");
        }
        this.projectCache = projectCache;
        this.refCache = refCache;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        input.add(runButton);
        input.add(saveButton);
        input.add(new JLabel("Entry points / hotspots / package cycles /"
                + " dead-code candidates / estimated layers"));
        add(input, BorderLayout.NORTH);

        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultArea.setLineWrap(false);
        resultArea.setTabSize(2);
        add(new JScrollPane(resultArea), BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(statusLabel, BorderLayout.SOUTH);

        runButton.addActionListener(this::onRun);
        saveButton.addActionListener(this::onSave);
        saveButton.setEnabled(false);
    }

    private void onRun(ActionEvent e) {
        if (!projectCache.isLoaded()) {
            statusLabel.setText("No project loaded. Open a project first.");
            return;
        }
        runButton.setEnabled(false);
        saveButton.setEnabled(false);
        statusLabel.setText("Building reference index...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                ReferenceIndex idx = refCache.get();
                if (idx == null) {
                    return null;
                }
                InsightsModel model = InsightsAnalyzer.analyze(
                        projectCache.getClasses(), projectCache.getIndex(), idx);
                return MarkdownInsightsReport.render(model);
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                try {
                    String report = get();
                    if (report == null) {
                        statusLabel.setText("No project loaded. Open a project first.");
                        return;
                    }
                    resultArea.setText(report);
                    resultArea.setCaretPosition(0);
                    saveButton.setEnabled(true);
                    statusLabel.setText("Done. Scroll down to see the full report.");
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Analysis failed: " + cause.getMessage());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("Interrupted.");
                }
            }
        }.execute();
    }

    private void onSave(ActionEvent e) {
        String content = resultArea.getText();
        if (content.isEmpty()) {
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(Messages.get("dlg.saveInsightsReport"));
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new FileNameExtensionFilter("Markdown (*.md)", "md"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = fc.getSelectedFile();
        if (!chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
            chosen = new File(chosen.getAbsolutePath() + ".md");
        }
        try {
            Files.write(chosen.toPath(), content.getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("Saved to: " + chosen.getAbsolutePath());
        } catch (IOException ex) {
            statusLabel.setText("Save failed: " + ex.getMessage());
        }
    }
}
