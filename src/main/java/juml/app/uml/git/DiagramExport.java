// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;
import juml.util.Messages;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 左右比較ダイアログの図を「旧｜新」1 枚の画像に合成し、PNG 保存 / クリップボードコピー
 * するためのユーティリティ。設計書へそのまま貼れるよう白背景・ラベル付きで書き出す。
 */
final class DiagramExport {

    /** 書き出しの拡大率 (資料貼り付け向けに少し大きめ)。 */
    private static final double SCALE = 2.0;
    private static final int PAD = 16;
    private static final int HEADER_H = 26;

    private DiagramExport() {
    }

    /**
     * 「PNG 保存 / コピー」ボタンを持つツールバーを作る。押下時に {@code supplier} から
     * 現在の合成画像を取得する (まだ描画前なら null を返させて no-op)。
     */
    static javax.swing.JComponent toolbar(Component parent, String baseName,
            java.util.function.Supplier<BufferedImage> supplier) {
        javax.swing.JPanel bar = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 2));
        javax.swing.JButton save = new javax.swing.JButton(Messages.get("git.export.png"));
        save.addActionListener(e -> {
            BufferedImage img = supplier.get();
            if (img != null) {
                saveAsPng(parent, img, baseName + "-compare.png");
            }
        });
        javax.swing.JButton copy = new javax.swing.JButton(Messages.get("git.export.copy"));
        copy.addActionListener(e -> {
            BufferedImage img = supplier.get();
            if (img != null) {
                copyToClipboard(img);
            }
        });
        bar.add(save);
        bar.add(copy);
        return bar;
    }

    /** パスからファイル名の基底 (拡張子・ディレクトリを除く) を取り出す。 */
    static String baseName(String path) {
        if (path == null || path.isEmpty()) {
            return "diagram";
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 旧 (左) と新 (右) の図を白背景で横並びに合成する。片側が null (その版に無い) の
     * ときは代替テキストのプレースホルダを置く。
     */
    static BufferedImage composite(RenderedSvg oldSvg, RenderedSvg newSvg,
                                   String oldLabel, String newLabel) {
        BufferedImage left = raster(oldSvg);
        BufferedImage right = raster(newSvg);
        int colW = Math.max(left.getWidth(), right.getWidth());
        int bodyH = Math.max(left.getHeight(), right.getHeight());
        int w = colW * 2 + PAD * 3;
        int h = HEADER_H + bodyH + PAD * 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        g.setColor(new Color(0x30, 0x34, 0x3a));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 13f));
        FontMetrics fm = g.getFontMetrics();
        int textY = PAD + fm.getAscent();
        drawCentered(g, fm, oldLabel, PAD, colW, textY);
        drawCentered(g, fm, newLabel, PAD * 2 + colW, colW, textY);

        int bodyY = PAD + HEADER_H;
        g.drawImage(left, PAD, bodyY, null);
        g.drawImage(right, PAD * 2 + colW, bodyY, null);
        g.setColor(new Color(0xD0, 0xD7, 0xDE));
        g.fillRect(PAD + colW + PAD / 2 - 1, PAD, 2, h - PAD * 2);
        g.dispose();
        return img;
    }

    private static void drawCentered(Graphics2D g, FontMetrics fm, String text,
                                     int x, int width, int y) {
        String t = text != null ? text : "";
        g.drawString(t, x + (width - fm.stringWidth(t)) / 2, y);
    }

    /** RenderedSvg を白背景の画像へラスタライズする。null なら「無し」プレースホルダ。 */
    private static BufferedImage raster(RenderedSvg svg) {
        if (svg == null) {
            BufferedImage ph = new BufferedImage(240, 80, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = ph.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, ph.getWidth(), ph.getHeight());
            g.setColor(Color.GRAY);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawString(Messages.get("git.actcmp.absent"), 12, 44);
            g.dispose();
            return ph;
        }
        int w = Math.max(1, (int) Math.ceil(svg.getWidth() * SCALE));
        int h = Math.max(1, (int) Math.ceil(svg.getHeight() * SCALE));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.scale(SCALE, SCALE);
        svg.getRoot().paint(g);
        g.dispose();
        return img;
    }

    /** ファイル選択ダイアログで PNG として保存する。 */
    static void saveAsPng(Component parent, BufferedImage img, String defaultName) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle(Messages.get("git.export.png"));
        fc.setFileFilter(new FileNameExtensionFilter("PNG", "png"));
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            file = new File(file.getParentFile(), file.getName() + ".png");
        }
        try {
            ImageIO.write(img, "png", file);
        } catch (IOException ex) {
            javax.swing.JOptionPane.showMessageDialog(parent,
                    Messages.get("git.export.failed") + ex.getMessage());
        }
    }

    /** 画像をシステムクリップボードへコピーする。 */
    static void copyToClipboard(BufferedImage img) {
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new ImageTransferable(img), null);
    }

    /** 画像 1 枚だけを運ぶ Transferable。 */
    private static final class ImageTransferable implements Transferable {
        private final BufferedImage image;

        ImageTransferable(BufferedImage image) {
            this.image = image;
        }

        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
