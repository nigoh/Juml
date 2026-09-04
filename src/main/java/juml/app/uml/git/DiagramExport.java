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
        javax.swing.JLabel status = new javax.swing.JLabel(" ");
        javax.swing.JButton save = new javax.swing.JButton(Messages.get("git.export.png"));
        javax.swing.JButton copy = new javax.swing.JButton(Messages.get("git.export.copy"));
        save.addActionListener(e -> withComposite(supplier, status, save, copy,
                img -> saveAsPng(parent, img, baseName + "-compare.png", save)));
        copy.addActionListener(e -> withComposite(supplier, status, save, copy, img -> {
            copyToClipboard(img);
            status.setText(Messages.get("git.export.copied"));
        }));
        bar.add(status);
        bar.add(save);
        bar.add(copy);
        return bar;
    }

    /**
     * 合成画像を背景スレッドで作り、完成後に {@code action} を EDT で呼ぶ。
     *
     * <p>合成は両側の SVG を 2 倍でラスタライズするため大きな図では数秒かかる。EDT で走らせると
     * ダイアログの親 (メインウィンドウ) ごと無応答になるので SwingWorker へ逃がし、実行中は
     * ボタンを無効化する。描画完了前 (supplier が null) は無反応に見えないよう案内を出す。</p>
     */
    private static void withComposite(java.util.function.Supplier<BufferedImage> supplier,
            javax.swing.JLabel status, javax.swing.JButton save, javax.swing.JButton copy,
            java.util.function.Consumer<BufferedImage> action) {
        status.setText(" ");
        save.setEnabled(false);
        copy.setEnabled(false);
        new javax.swing.SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() {
                return supplier.get();
            }

            @Override protected void done() {
                save.setEnabled(true);
                copy.setEnabled(true);
                BufferedImage img;
                try {
                    img = get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText(Messages.get("git.export.failed") + cause.getMessage());
                    return;
                }
                if (img == null) {
                    status.setText(Messages.get("git.umldiff.rendering"));
                    return;
                }
                action.accept(img);
            }
        }.execute();
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
        // 画面に出ている GraphicsNode を背景スレッドから paint すると EDT の描画と競合するため、
        // SVG テキストから専用のノードを組み直して描く (組み直せないときだけ元ノードを使う)。
        RenderedSvg own = reparse(svg);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.scale(SCALE, SCALE);
        (own != null ? own : svg).getRoot().paint(g);
        g.dispose();
        return img;
    }

    /** 表示中のノードと競合しない専用の {@link RenderedSvg} を作る (作れなければ null)。 */
    private static RenderedSvg reparse(RenderedSvg svg) {
        String xml = svg.getSvgXml();
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        try {
            return juml.app.uml.PlantUmlSvgRenderer.render(xml);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * ファイル選択ダイアログで PNG として保存する。エンコード + 書き込みは SwingWorker で
     * 行い (2 倍ラスタの大きな図で EDT が数秒固まっていた)、その間 {@code trigger} を無効化する。
     */
    static void saveAsPng(Component parent, BufferedImage img, String defaultName,
                          javax.swing.AbstractButton trigger) {
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
        if (file.exists() && javax.swing.JOptionPane.showConfirmDialog(parent,
                java.text.MessageFormat.format(
                        Messages.get("git.export.overwrite"), file.getName()),
                Messages.get("git.export.png"),
                javax.swing.JOptionPane.YES_NO_OPTION) != javax.swing.JOptionPane.YES_OPTION) {
            return; // 既存ファイルを黙って上書きしない (アプリ内の他の保存経路と揃える)
        }
        final File target = file;
        if (trigger != null) {
            trigger.setEnabled(false);
        }
        new javax.swing.SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                writePng(img, target);
                return null;
            }

            @Override protected void done() {
                if (trigger != null) {
                    trigger.setEnabled(true);
                }
                try {
                    get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    javax.swing.JOptionPane.showMessageDialog(parent,
                            Messages.get("git.export.failed") + cause.getMessage());
                }
            }
        }.execute();
    }

    /**
     * 一時ファイルへ書き切ってから置換する。対象へ直接書くと、エンコード失敗や
     * ディスク満杯で前回保存した PNG が壊れた状態で失われる。ImageIO.write は
     * エンコーダが無いと例外ではなく false を返すので、それも失敗として扱う。
     */
    static void writePng(BufferedImage img, File target) throws IOException {
        juml.util.AtomicFileWrite.writeFile(target, tmp -> {
            if (!ImageIO.write(img, "png", tmp)) {
                throw new IOException("no PNG encoder available for export");
            }
        });
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
