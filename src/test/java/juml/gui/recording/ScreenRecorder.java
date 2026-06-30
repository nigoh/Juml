// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 対象矩形を一定間隔でキャプチャし、停止時にアニメ GIF として書き出す簡易レコーダ。
 * 外部エンコーダ不要。Xvfb 上で動かすこと（headless では Robot が使えない）。
 */
public final class ScreenRecorder {

    private final Robot robot;
    private final Rectangle area;
    private final int delayMs;
    private final List<BufferedImage> frames = new ArrayList<>();
    private volatile boolean running;
    private Thread thread;

    public ScreenRecorder(Rectangle area, int fps) throws AWTException {
        this.robot = new Robot();
        this.area = area;
        this.delayMs = Math.max(1, 1000 / Math.max(1, fps));
    }

    /** 録画を開始する。すでに開始済みの場合は何もしない。 */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(() -> {
            while (running) {
                frames.add(robot.createScreenCapture(area));
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "screen-recorder");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 録画を止めて GIF を書き出す。書き出したファイルを返す。
     *
     * @param out 出力先ファイル
     * @return 書き出したファイル（out と同一）
     * @throws Exception 書き出し失敗時
     */
    public synchronized File stopAndSave(File out) throws Exception {
        running = false;
        if (thread != null) {
            thread.join(2000);
        }
        out.getParentFile().mkdirs();
        writeGif(frames, delayMs, out);
        return out;
    }

    private static void writeGif(List<BufferedImage> imgs, int delayMs, File out) throws Exception {
        if (imgs.isEmpty()) {
            throw new IllegalStateException("フレームが 0 枚（Xvfb 上で動かしたか確認）");
        }
        ImageWriter writer = ImageIO.getImageWritersBySuffix("gif").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(Files.newOutputStream(out.toPath()))) {
            writer.setOutput(ios);
            ImageWriteParam params = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            boolean first = true;
            for (BufferedImage img : imgs) {
                BufferedImage rgb = toIndexedFriendly(img);
                IIOMetadata meta = writer.getDefaultImageMetadata(
                        javax.imageio.ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB),
                        params);
                configureFrame(meta, delayMs, first);
                writer.writeToSequence(new IIOImage(rgb, null, meta), params);
                first = false;
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
    }

    private static BufferedImage toIndexedFriendly(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(src, 0, 0, null);
        return rgb;
    }

    private static void configureFrame(IIOMetadata meta, int delayMs, boolean first) throws Exception {
        String fmt = meta.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(fmt);

        IIOMetadataNode gce = child(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", Integer.toString(Math.max(1, delayMs / 10))); // 1/100s 単位
        gce.setAttribute("transparentColorIndex", "0");

        if (first) {
            IIOMetadataNode appExts = child(root, "ApplicationExtensions");
            IIOMetadataNode appExt = new IIOMetadataNode("ApplicationExtension");
            appExt.setAttribute("applicationID", "NETSCAPE");
            appExt.setAttribute("authenticationCode", "2.0");
            appExt.setUserObject(new byte[] {0x1, 0x0, 0x0}); // ループ無限
            appExts.appendChild(appExt);
        }
        meta.setFromTree(fmt, root);
    }

    private static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }
}
