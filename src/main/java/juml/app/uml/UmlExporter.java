// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.core.formats.uml.PlantUmlRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 生成済みの図 (PlantUML テキスト + プレビュー画像) を、選択された形式でファイルに保存する。
 *
 * <ul>
 *   <li>{@code .puml} / {@code .plantuml} / {@code .txt}: PlantUML テキストを書き出し</li>
 *   <li>{@code .svg}: 同梱 PlantUML で SVG を再レンダリング</li>
 *   <li>{@code .png}: プレビュー時の {@link BufferedImage} を {@link ImageIO} で書き出し</li>
 * </ul>
 *
 * <p>SVG は元の PlantUML テキストから再生成するためズーム倍率の影響を受けないが、
 * PNG は表示中のラスタ画像をそのまま使用するため解像度はレンダリング時設定に従う。</p>
 */
public final class UmlExporter {

    /** サポートする出力形式。 */
    public enum Format {
        SVG("svg"),
        PNG("png"),
        PUML("puml");

        private final String ext;

        Format(String ext) {
            this.ext = ext;
        }

        public String getExtension() {
            return ext;
        }

        /** ファイル名の拡張子から形式を判定する (大文字小文字無視)。判定不能なら null。 */
        public static Format fromFileName(String name) {
            if (name == null) {
                return null;
            }
            String n = name.toLowerCase();
            if (n.endsWith(".svg")) {
                return SVG;
            }
            if (n.endsWith(".png")) {
                return PNG;
            }
            if (n.endsWith(".puml") || n.endsWith(".plantuml") || n.endsWith(".txt")) {
                return PUML;
            }
            return null;
        }
    }

    private UmlExporter() {
    }

    /**
     * 指定形式で {@code outFile} に保存する。
     *
     * @param format  出力形式
     * @param outFile 出力先 (拡張子は呼び出し側で揃えておく)
     * @param puml    PlantUML テキスト (SVG / PUML で必要)
     * @param image   プレビュー画像 (PNG で必要)
     */
    public static void export(Format format, File outFile, String puml, BufferedImage image)
            throws IOException {
        if (format == null) {
            throw new IllegalArgumentException("format is null");
        }
        if (outFile == null) {
            throw new IllegalArgumentException("outFile is null");
        }
        switch (format) {
            case SVG:
                if (puml == null) {
                    throw new IllegalArgumentException("puml is required for SVG export");
                }
                PlantUmlRenderer.renderSvg(puml, outFile);
                break;
            case PNG:
                if (image == null) {
                    // レンダラは空の図 (0 バイト出力) で null を返す。以前は
                    // "image is required for PNG export" という開発者向けの分かりにくい
                    // 文言をそのままダイアログに出していた。空図/出力なしを明示する。
                    throw new IllegalArgumentException(juml.util.Messages.get("export.emptyDiagram"));
                }
                // ImageIO.write は書き出せるエンコーダが無いと false を返す (例外は投げない)。
                // 結果を無視すると 0 バイトのファイルを残したまま成功扱いになるため検査する。
                if (!ImageIO.write(image, "png", outFile)) {
                    java.nio.file.Files.deleteIfExists(outFile.toPath());
                    throw new IOException("no PNG encoder available for export");
                }
                break;
            case PUML:
                if (puml == null) {
                    throw new IllegalArgumentException("puml is required for PUML export");
                }
                try (Writer w = new OutputStreamWriter(
                        new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                    w.write(puml);
                }
                break;
            default:
                throw new IllegalStateException("Unsupported format: " + format);
        }
    }
}
