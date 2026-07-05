// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.cli;

import juml.core.formats.uml.PlantUmlRenderFailedException;
import juml.core.formats.uml.PlantUmlRenderer;
import juml.util.ErrorListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * CLI 各モード共通の成果物書き出しヘルパ。テキスト/PlantUML/SVG の出力先判定と、
 * SVG レンダリング失敗時のサイドカー {@code .puml} フォールバックを集約する。
 */
public final class CliOutput {

    private CliOutput() {
    }

    /** テキストをファイルへ UTF-8 で書き出す。{@code f} が null なら標準出力。
     * {@code f} が既存ディレクトリの場合は (生の {@code FileNotFoundException} ではなく)
     * 出力先の指定方法を案内する {@link IOException} を投げる。 */
    public static void writeText(File f, String content) throws IOException {
        if (f == null) {
            // System.out の既定エンコーディングに依存しないよう UTF-8 で明示出力
            System.out.write(content.getBytes(StandardCharsets.UTF_8));
            System.out.flush();
            return;
        }
        if (f.isDirectory()) {
            throw new IOException("-o points to an existing directory: " + f.getPath()
                    + " (specify a file path, e.g. -o " + f.getPath()
                    + File.separator + "report.md)");
        }
        ensureParentDir(f);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }

    /**
     * 出力先の親ディレクトリが存在しなければ作成する。これにより
     * {@code -o out/sub/report.md} のように未作成のディレクトリを指定しても、
     * 生の {@code FileNotFoundException} (スタックトレース) ではなく素直に書き出せる。
     * 作成できない場合は案内付きの {@link IOException} を投げる。
     */
    static void ensureParentDir(File f) throws IOException {
        if (f == null) {
            return;
        }
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("出力先ディレクトリを作成できません: " + parent.getPath());
        }
    }

    /**
     * テキスト書き出し ({@code -o} がディレクトリでも動く版)。{@code f} が既存
     * ディレクトリなら {@code defaultFileName} をその中に書く。CLI ハンドラは
     * 原則こちらを使い、{@code -o} の解釈を全コマンドで統一する。
     */
    public static void writeText(File f, String content, String defaultFileName)
            throws IOException {
        writeText(resolveInDir(f, defaultFileName), content);
    }

    /** {@code f} が既存ディレクトリなら {@code defaultFileName} を補完した File を返す。 */
    private static File resolveInDir(File f, String defaultFileName) {
        if (f != null && f.isDirectory() && defaultFileName != null) {
            return new File(f, defaultFileName);
        }
        return f;
    }

    /**
     * {@code fileOut} が SVG レンダリング対象か (拡張子 {@code .svg} または既存
     * ディレクトリ) を判定する。同梱 PlantUML の SVG 出力は先頭の {@code @startuml}
     * ブロックしかレンダリングしないため、複数図を SVG 化する呼び出し側はこれを見て
     * 図ごとに別ファイルへ分割する必要がある。
     */
    public static boolean isSvgTarget(File fileOut) {
        return fileOut != null
                && (fileOut.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".svg")
                    || fileOut.isDirectory());
    }

    /**
     * 複数図を SVG へ書き出すときの、図 1 枚ぶんの出力先を決める。
     * ディレクトリ指定なら {@code <dir>/<label>.svg}、単一 {@code .svg} ファイル指定なら
     * その隣に {@code <base>-<label>.svg} を作る。{@code label} は使えない文字を
     * {@code _} に落とし、空なら {@code defaultBase + index} を使う。
     */
    public static File perDiagramSvgTarget(File fileOut, String label, int index,
                                           String defaultBase) {
        String safe = label == null ? "" : label.replaceAll("[^A-Za-z0-9_.-]", "_");
        if (safe.isEmpty()) {
            safe = defaultBase + "-" + index;
        }
        if (fileOut != null && fileOut.isDirectory()) {
            return new File(fileOut, safe + ".svg");
        }
        String name = fileOut == null ? defaultBase + ".svg" : fileOut.getName();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        File parent = fileOut == null ? null : fileOut.getParentFile();
        String childName = base + "-" + safe + ".svg";
        return parent == null ? new File(childName) : new File(parent, childName);
    }

    /**
     * PlantUML 系出力の書き出し。{@code fileOut} の拡張子が {@code .svg} なら
     * 同梱 PlantUML で SVG にレンダリングし、それ以外 (null や .puml/.txt) は
     * PlantUML テキストをそのまま書き出す (標準出力可)。
     */
    public static void writeUmlOutput(File fileOut, String puml) throws IOException {
        writeUmlOutput(fileOut, puml, null);
    }

    /**
     * PlantUML 系出力 ({@code -o} がディレクトリでも動く版)。{@code fileOut} が既存
     * ディレクトリなら {@code defaultBaseName + ".svg"} をその中に書く。
     */
    public static void writeUmlOutput(File fileOut, String puml, String defaultBaseName)
            throws IOException {
        fileOut = resolveInDir(fileOut,
                defaultBaseName == null ? null : defaultBaseName + ".svg");
        if (fileOut != null && fileOut.getName().toLowerCase().endsWith(".svg")) {
            try {
                ensureParentDir(fileOut);
                PlantUmlRenderer.renderSvg(puml, fileOut);
            } catch (PlantUmlRenderFailedException ex) {
                File pumlFallback = siblingPumlFor(fileOut);
                writeText(pumlFallback, puml);
                System.err.println("[juml] " + fileOut.getName()
                        + " FAILED: " + ex.getMessage());
                System.err.println("[juml]    Saved " + pumlFallback.getPath()
                        + " -- render externally with: plantuml -tsvg "
                        + pumlFallback.getName());
                System.exit(2);
            }
        } else {
            writeText(fileOut, puml);
        }
    }

    /** 与えられた SVG ファイルと同じ親ディレクトリ・同じベース名で {@code .puml} を指す
     * ファイル オブジェクトを返す。フォールバック保存先として使う。 */
    public static File siblingPumlFor(File svgFile) {
        String name = svgFile.getName();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        File parent = svgFile.getParentFile();
        if (parent == null) {
            return new File(base + ".puml");
        }
        return new File(parent, base + ".puml");
    }

    /** {@code --all} 内で 1 つの SVG をレンダリングする。失敗時はサイドカー puml に
     * フォールバックして「FAILED」ログを出し、次の図に進む。
     * @return レンダリングが成功したかどうか
     */
    public static boolean renderSvgOrFallback(String puml, File svgFile,
                                               ProgressLogger progress,
                                               ErrorListener listener) throws IOException {
        try {
            PlantUmlRenderer.renderSvg(puml, svgFile);
            progress.wrote(svgFile);
            listener.onError(null, -1, "wrote " + svgFile.getPath());
            return true;
        } catch (PlantUmlRenderFailedException ex) {
            File pumlFallback = siblingPumlFor(svgFile);
            writeText(pumlFallback, puml);
            System.err.println("[juml]     -> " + svgFile.getName()
                    + " FAILED: " + ex.getMessage());
            System.err.println("[juml]        Saved " + pumlFallback.getName()
                    + " -- render externally with: plantuml -tsvg "
                    + pumlFallback.getName());
            return false;
        }
    }

    /**
     * Impact レポートの書き出し。出力先の拡張子を見て .md / .puml / 両方を切り替える。
     */
    public static void writeImpactOutput(File fileOut, String markdown, String puml)
            throws IOException {
        writeImpactOutput(fileOut, markdown, puml, null);
    }

    /**
     * Markdown + PlantUML 2 成果物の書き出し ({@code -o} がディレクトリでも動く版)。
     * {@code fileOut} が既存ディレクトリなら {@code defaultBaseName + ".md"} と
     * {@code defaultBaseName + ".puml"} をその中に書く。
     */
    public static void writeImpactOutput(File fileOut, String markdown, String puml,
                                          String defaultBaseName)
            throws IOException {
        if (fileOut == null) {
            writeText(null, markdown);
            return;
        }
        if (fileOut.isDirectory() && defaultBaseName != null) {
            writeText(new File(fileOut, defaultBaseName + ".md"), markdown);
            writeText(new File(fileOut, defaultBaseName + ".puml"), puml);
            return;
        }
        String name = fileOut.getName().toLowerCase();
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            writeText(fileOut, markdown);
        } else if (name.endsWith(".puml") || name.endsWith(".plantuml")) {
            writeText(fileOut, puml);
        } else if (name.endsWith(".svg")) {
            writeUmlOutput(fileOut, puml);
        } else {
            // 拡張子なし: 同じディレクトリ・同じベース名で .md と .puml を両方書く
            File parent = fileOut.getParentFile();
            String base = fileOut.getName();
            int dot = base.lastIndexOf('.');
            if (dot >= 0) {
                base = base.substring(0, dot);
            }
            File mdFile = parent == null ? new File(base + ".md")
                    : new File(parent, base + ".md");
            File pumlFile = parent == null ? new File(base + ".puml")
                    : new File(parent, base + ".puml");
            writeText(mdFile, markdown);
            writeText(pumlFile, puml);
        }
    }
}
