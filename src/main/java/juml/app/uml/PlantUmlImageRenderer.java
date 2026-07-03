// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import juml.core.formats.uml.PlantUmlRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PlantUML テキストを画面プレビュー用の {@link BufferedImage} に変換する。
 *
 * <p>レンダリングは同梱の PlantUML jar が提供する PNG 直接出力経由で実施するため、
 * SVG → 画像化用に Apache Batik を経由する必要がない。SVG / PDF へのエクスポート時のみ
 * {@link PlantUmlRenderer#renderSvg(String, java.io.OutputStream)} 等を使用する。</p>
 */
public final class PlantUmlImageRenderer {

    private PlantUmlImageRenderer() {
    }

    /**
     * PlantUML テキストをレンダリングして {@link BufferedImage} を返す。
     *
     * <p>{@code @startuml} 直後に Smetana レイアウトを自動注入するため、Graphviz/dot の
     * インストールは不要。{@code @startuml} を含まない文字列が渡された場合は
     * 何もせずそのまま PlantUML に解釈させる。</p>
     */
    public static BufferedImage toBufferedImage(String puml) throws IOException {
        if (puml == null) {
            throw new IllegalArgumentException("puml is null");
        }
        // PlantUML 以外の図種が直接生成した SVG は Batik でラスタライズする。
        if (PlantUmlRenderer.looksLikeSvg(puml)) {
            return rasterizeSvg(puml);
        }
        // 上限を超える巨大な図は切り詰めではなく縮小して収める (PNG キャンバス上限対策)。
        String prepared = PlantUmlRenderer.injectScaleMax(puml, PlantUmlRenderer.imageLimit());
        SourceStringReader reader = new SourceStringReader(PlantUmlRenderer.injectLayout(prepared));
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Object[] descHolder = new Object[1];
            // SVG 経路と同じ stderr 捕捉で、PlantUML が握りつぶすレイアウトエンジンの
            // 致命的障害 (dot 実行失敗 / Smetana 内部クラッシュ) を PNG 経路でも検出する。
            // 障害時は要素が配置されない「ほぼ空の PNG」が正常出力として返るため、
            // マーカー画像判定だけでは壊れたエクスポートを防げない。
            String stderrTail = PlantUmlRenderer.captureStderrDuring(() ->
                    descHolder[0] = reader.outputImage(baos, new FileFormatOption(FileFormat.PNG)));
            Object desc = descHolder[0];
            // PlantUML は失敗時も例外を投げず「An error has occured」画像を返す。
            // その場合 DiagramDescription はちょうど "(Error)" になる (正常図は
            // "(N entities)" 等でユーザー内容を含まないことを実測確認済み) ため、
            // 厳密一致で判定し、壊れた PNG を正常出力として返さず例外に変換する。
            if (desc != null && "(Error)".equals(String.valueOf(desc))) {
                juml.util.AppLog.error(juml.util.ErrorCode.UML_R006, "PlantUmlImageRenderer",
                        "PNG render returned PlantUML error image: " + desc);
                throw new juml.core.formats.uml.PlantUmlRenderFailedException(
                        juml.util.ErrorCode.UML_R006,
                        "PlantUML render failed (error image returned on PNG export). "
                                + "Check logs/juml.log for details.");
            }
            juml.util.ErrorCode fatal = PlantUmlRenderer.fatalLayoutErrorCode(stderrTail);
            if (fatal != null) {
                juml.util.AppLog.error(fatal, "PlantUmlImageRenderer",
                        "PNG render hit a fatal layout engine failure; stderr tail:\n"
                                + stderrTail);
                throw new juml.core.formats.uml.PlantUmlRenderFailedException(
                        fatal,
                        "PlantUML layout engine failed during PNG render; "
                                + "the diagram would be rendered incomplete.");
            }
            byte[] bytes = baos.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                return ImageIO.read(bais);
            }
        }
    }

    /** 既製 SVG 文字列を Batik で {@link BufferedImage} へラスタライズする。 */
    private static BufferedImage rasterizeSvg(String svg) throws IOException {
        PlantUmlSvgRenderer.RenderedSvg rendered = PlantUmlSvgRenderer.render(svg);
        if (rendered == null || rendered.getRoot() == null) {
            return null;
        }
        int w = Math.max(1, (int) Math.ceil(rendered.getWidth()));
        int h = Math.max(1, (int) Math.ceil(rendered.getHeight()));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            rendered.getRoot().paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }
}
