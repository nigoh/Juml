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
        return perDiagramTarget(fileOut, sanitizeDiagramName(label, defaultBase, index),
                imageExtensionOf(fileOut));
    }

    /**
     * {@code fileOut} が「1 ファイル 1 図」しか表現できない画像出力かを判定する
     * (拡張子 {@code .svg} / {@code .png}、または既存ディレクトリ)。
     *
     * <p>同梱 PlantUML の SVG/PNG 出力はどちらも先頭の {@code @startuml} ブロックしか
     * ラスタライズしない。複数図をこの形式へ書く呼び出し側は図ごとに別ファイルへ
     * 分割しなければ、2 枚目以降が<b>警告なく消える</b>。</p>
     */
    public static boolean isSingleDiagramImageTarget(File fileOut) {
        if (fileOut == null) {
            return false;
        }
        String lower = fileOut.getName().toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".svg") || lower.endsWith(".png") || fileOut.isDirectory();
    }

    /** {@code fileOut} に対応する画像拡張子 ({@code png} / それ以外は {@code svg})。 */
    public static String imageExtensionOf(File fileOut) {
        return fileOut != null
                && fileOut.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")
                ? "png" : "svg";
    }

    /** 図名をファイル名に使える形へ落とす (空なら {@code defaultBase-index})。 */
    private static String sanitizeDiagramName(String label, String defaultBase, int index) {
        String safe = label == null ? "" : label.replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.isEmpty() ? defaultBase + "-" + index : safe;
    }

    /**
     * 複数図ぶんのファイル名を、<b>サニタイズ後に</b>重複解決して確定する。
     *
     * <p>サニタイズ前の名前で重複を解決すると、{@code a/b} と {@code a:b} のように
     * 使えない文字だけが違う名前が同じ {@code a_b} へ落ちて再衝突し、後の図が前の図を
     * 黙って上書きする。比較は大文字小文字を無視する (Windows/macOS の既定ファイル
     * システムでは {@code Nav} と {@code nav} が同じファイル)。</p>
     */
    public static java.util.List<String> planDiagramNames(java.util.List<String> labels,
                                                          String defaultBase) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < labels.size(); i++) {
            String base = sanitizeDiagramName(labels.get(i), defaultBase, i);
            String candidate = base;
            int n = 2;
            while (!used.add(candidate.toLowerCase(java.util.Locale.ROOT))) {
                candidate = base + "_" + n;
                n++;
            }
            out.add(candidate);
        }
        return out;
    }

    /**
     * 複数図を書き出すときの、図 1 枚ぶんの出力先を決める。
     * ディレクトリ指定なら {@code <dir>/<name>.<ext>}、単一ファイル指定なら
     * その隣に {@code <base>-<name>.<ext>} を作る。
     */
    public static File perDiagramTarget(File fileOut, String name, String ext) {
        if (fileOut != null && fileOut.isDirectory()) {
            return new File(fileOut, name + "." + ext);
        }
        String outName = fileOut == null ? "diagram." + ext : fileOut.getName();
        int dot = outName.lastIndexOf('.');
        String base = dot >= 0 ? outName.substring(0, dot) : outName;
        File parent = fileOut == null ? null : fileOut.getParentFile();
        String childName = base + "-" + name + "." + ext;
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
        String lower = fileOut == null ? ""
                : fileOut.getName().toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".svg")) {
            try {
                ensureParentDir(fileOut);
                renderSvgAtomically(puml, fileOut);
            } catch (PlantUmlRenderFailedException ex) {
                fallbackToPuml(fileOut, puml, ex, "svg");
            }
        } else if (lower.endsWith(".png")) {
            // .png 指定は同梱 PlantUML でラスタライズして「実際の PNG」を書き出す。
            // 以前は拡張子を無視して PlantUML テキストを .png ファイルに書いていた
            // (画像を期待した利用者が中身がテキストのファイルを受け取る不具合)。
            try {
                ensureParentDir(fileOut);
                renderPng(puml, fileOut);
            } catch (PlantUmlRenderFailedException ex) {
                fallbackToPuml(fileOut, puml, ex, "png");
            }
        } else {
            writeText(fileOut, puml);
        }
    }

    /** 同梱 PlantUML で PNG にラスタライズしてファイルへ保存する。空図は失敗として扱う。 */
    private static void renderPng(String puml, File pngFile)
            throws IOException {
        java.awt.image.BufferedImage img =
                juml.app.uml.PlantUmlImageRenderer.toBufferedImage(puml);
        if (img == null) {
            // 要素が 1 つも配置されなかった (空図)。テキストへ静かにフォールスルー
            // させず、SVG と同じサイドカー .puml フォールバック経路に載せる。
            throw new PlantUmlRenderFailedException(juml.util.ErrorCode.UML_R006,
                    "empty diagram — no PNG was produced");
        }
        juml.app.uml.UmlExporter.export(
                juml.app.uml.UmlExporter.Format.PNG, pngFile, puml, img);
    }

    /**
     * SVG/PNG レンダリング失敗時の共通フォールバック: サイドカー {@code .puml} を残し、
     * 外部レンダリング手順を案内して {@code exit(2)} する。
     *
     * @param kind {@code svg} / {@code png} — 案内する {@code plantuml -t<kind>} の種別
     */
    private static void fallbackToPuml(File fileOut, String puml,
            PlantUmlRenderFailedException ex, String kind) throws IOException {
        reportRenderFallback(fileOut, puml, ex, kind);
        System.exit(2);
    }

    /**
     * レンダリング失敗時のサイドカー {@code .puml} 保存と案内だけを行う (プロセスは落とさない)。
     *
     * <p>複数の図を 1 本ずつ書き出すループから使う。{@link #fallbackToPuml} をそのまま呼ぶと
     * 途中の 1 枚が失敗しただけで残りが<b>生成すらされない</b>まま {@code exit(2)} してしまう。
     * 呼び出し側はループを最後まで回し、失敗があれば最後にまとめて終了コードを決める。</p>
     */
    public static void reportRenderFallback(File fileOut, String puml,
            PlantUmlRenderFailedException ex, String kind) throws IOException {
        File pumlFallback = siblingPumlFor(fileOut);
        writeText(pumlFallback, puml);
        System.err.println("[juml] " + fileOut.getName()
                + " FAILED: " + ex.getMessage());
        System.err.println("[juml]    Saved " + pumlFallback.getPath()
                + " -- render externally with: plantuml -t" + kind + " "
                + pumlFallback.getName());
    }

    /**
     * 画像 1 枚を書き出し、失敗しても例外にせず {@code false} を返す
     * ({@link #reportRenderFallback} でサイドカーと案内は出す)。
     */
    public static boolean writeImageOrFallback(File target, String puml, String kind)
            throws IOException {
        try {
            ensureParentDir(target);
            if ("png".equals(kind)) {
                renderPng(puml, target);
            } else {
                renderSvgAtomically(puml, target);
            }
            return true;
        } catch (PlantUmlRenderFailedException ex) {
            reportRenderFallback(target, puml, ex, kind);
            return false;
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
    /**
     * SVG を「一時ファイルへ書き切ってから原子的に置換」で書き出す。
     *
     * <p>{@code PlantUmlRenderer.renderSvg(String, File)} は対象を開いた時点で切り詰め、
     * 失敗時にはファイルごと削除する。前回の出力を壊さないため、CLI からはこちらを使う
     * (PNG は {@code UmlExporter} 経由で既に原子的)。</p>
     */
    public static void renderSvgAtomically(String puml, File svgFile) throws IOException {
        juml.util.AtomicFileWrite.write(svgFile, os -> PlantUmlRenderer.renderSvg(puml, os));
    }

    public static boolean renderSvgOrFallback(String puml, File svgFile,
                                               ProgressLogger progress,
                                               ErrorListener listener) throws IOException {
        try {
            renderSvgAtomically(puml, svgFile);
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
        } else if (name.endsWith(".svg") || name.endsWith(".png")) {
            // ヘルプは「.svg / .png は同梱 PlantUML で描画する」と明記している。
            // .png を拡張子なし扱いにしていたため、画像を期待した利用者に md+puml が返っていた。
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
