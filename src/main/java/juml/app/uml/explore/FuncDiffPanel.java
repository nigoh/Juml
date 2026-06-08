// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.explore;

import juml.core.formats.java.AndroidProjectScanner;
import juml.core.formats.uml.JavaClassInfo;
import juml.core.formats.uml.JavaMethodInfo;
import juml.core.formats.uml.UmlGenerator;
import juml.core.funcdiff.MarkdownMethodDiffReport;
import juml.core.funcdiff.MethodDiffAnalyzer;
import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 2つのJavaメソッドの呼び出し列を比較し、差分レポートを表示するパネル。
 *
 * <p>LCS・Levenshtein・Jaccard の3指標と行ごとの信頼度スコアを
 * Markdown テキストとして結果エリアに表示する。</p>
 */
public class FuncDiffPanel extends JPanel {

    private final JTextField fileAField = new JTextField(40);
    private final JTextField methodAField = new JTextField(30);
    private final JTextField fileBField = new JTextField(40);
    private final JTextField methodBField = new JTextField(30);
    private final JButton compareButton = new JButton("Compare");
    private final JButton saveButton = new JButton("Save Report...");
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea resultArea = new JTextArea();

    public FuncDiffPanel() {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(buildInputForm(), BorderLayout.NORTH);
        add(buildResultArea(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        compareButton.addActionListener(this::onCompare);
        saveButton.addActionListener(this::onSave);
        saveButton.setEnabled(false);
    }

    // -------------------------------------------------------------------------
    // UI 構築
    // -------------------------------------------------------------------------

    private JPanel buildInputForm() {
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        // ── Method A ──
        addSectionLabel(form, c, "Method A", 0);

        addLabel(form, c, "File:", 1, 0);
        c.gridx = 1; c.gridy = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(fileAField, c);
        c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.gridx = 2; form.add(makeBrowseButton(fileAField), c);

        addLabel(form, c, "Method:", 2, 0);
        methodAField.setToolTipText("ClassName.methodName または methodName（クラス名省略可）");
        c.gridx = 1; c.gridy = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(methodAField, c);
        c.fill = GridBagConstraints.NONE; c.weightx = 0;

        // ── Method B ──
        addSectionLabel(form, c, "Method B", 3);

        addLabel(form, c, "File:", 4, 0);
        c.gridx = 1; c.gridy = 4; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(fileBField, c);
        c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.gridx = 2; form.add(makeBrowseButton(fileBField), c);

        addLabel(form, c, "Method:", 5, 0);
        methodBField.setToolTipText("ClassName.methodName または methodName（クラス名省略可）");
        c.gridx = 1; c.gridy = 5; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        form.add(methodBField, c);
        c.fill = GridBagConstraints.NONE; c.weightx = 0;

        // ── ボタン行 ──
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        buttons.add(compareButton);
        buttons.add(saveButton);
        c.gridx = 0; c.gridy = 6; c.gridwidth = 3;
        c.insets = new Insets(6, 0, 2, 0);
        form.add(buttons, c);
        c.gridwidth = 1;
        c.insets = new Insets(2, 4, 2, 4);

        return form;
    }

    private JScrollPane buildResultArea() {
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        resultArea.setLineWrap(false);
        resultArea.setTabSize(2);
        JScrollPane scroll = new JScrollPane(resultArea);
        scroll.setPreferredSize(new java.awt.Dimension(600, 400));
        return scroll;
    }

    private JLabel buildStatusBar() {
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        return statusLabel;
    }

    private void addSectionLabel(JPanel form, GridBagConstraints c, String text, int row) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        c.gridx = 0; c.gridy = row; c.gridwidth = 3;
        c.insets = new Insets(row == 0 ? 0 : 8, 4, 2, 4);
        form.add(label, c);
        c.gridwidth = 1;
        c.insets = new Insets(2, 4, 2, 4);
    }

    private void addLabel(JPanel form, GridBagConstraints c, String text, int row, int col) {
        c.gridx = col; c.gridy = row;
        form.add(new JLabel(text), c);
    }

    private JButton makeBrowseButton(JTextField target) {
        JButton btn = new JButton("Browse...");
        btn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle(Messages.get("dlg.selectJavaFile"));
            fc.setAcceptAllFileFilterUsed(true);
            fc.setFileFilter(new FileNameExtensionFilter("Java source (*.java)", "java"));
            String current = target.getText().trim();
            if (!current.isEmpty()) {
                File f = new File(current);
                if (f.getParentFile() != null && f.getParentFile().isDirectory()) {
                    fc.setCurrentDirectory(f.getParentFile());
                }
            }
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                target.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        return btn;
    }

    // -------------------------------------------------------------------------
    // イベントハンドラ
    // -------------------------------------------------------------------------

    private void onCompare(ActionEvent e) {
        String fileA = fileAField.getText().trim();
        String methodA = methodAField.getText().trim();
        String fileB = fileBField.getText().trim();
        String methodB = methodBField.getText().trim();

        if (fileA.isEmpty() || fileB.isEmpty()) {
            statusLabel.setText("File path is required for both methods.");
            return;
        }
        if (methodA.isEmpty() || methodB.isEmpty()) {
            statusLabel.setText("Method name is required for both methods.");
            return;
        }

        String specA = fileA + "::" + methodA;
        String specB = fileB + "::" + methodB;

        MethodDiffAnalyzer.MethodSpec parsedA;
        MethodDiffAnalyzer.MethodSpec parsedB;
        try {
            parsedA = MethodDiffAnalyzer.parseSpec(specA);
            parsedB = MethodDiffAnalyzer.parseSpec(specB);
        } catch (IllegalArgumentException ex) {
            statusLabel.setText("Invalid spec: " + ex.getMessage());
            return;
        }

        compareButton.setEnabled(false);
        saveButton.setEnabled(false);
        resultArea.setText("");
        statusLabel.setText("Analyzing...");

        final MethodDiffAnalyzer.MethodSpec finalA = parsedA;
        final MethodDiffAnalyzer.MethodSpec finalB = parsedB;

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                List<JavaClassInfo> classesA = UmlGenerator.extractFromSource(
                        AndroidProjectScanner.readFile(new File(finalA.filePath)),
                        new File(finalA.filePath).getName());
                List<JavaClassInfo> classesB = UmlGenerator.extractFromSource(
                        AndroidProjectScanner.readFile(new File(finalB.filePath)),
                        new File(finalB.filePath).getName());

                JavaMethodInfo mA = MethodDiffAnalyzer.findMethod(classesA, finalA);
                JavaMethodInfo mB = MethodDiffAnalyzer.findMethod(classesB, finalB);

                MethodDiffAnalyzer.DiffResult result =
                        MethodDiffAnalyzer.analyze(mA, finalA, mB, finalB);
                return MarkdownMethodDiffReport.render(result);
            }

            @Override
            protected void done() {
                compareButton.setEnabled(true);
                try {
                    String report = get();
                    resultArea.setText(report);
                    resultArea.setCaretPosition(0);
                    saveButton.setEnabled(true);
                    statusLabel.setText("Done. Scroll down to see the full report.");
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setText("Error: " + cause.getMessage());
                    resultArea.setText("Error: " + cause.getMessage());
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
        fc.setDialogTitle(Messages.get("dlg.saveDiffReport"));
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
