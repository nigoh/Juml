// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link ImageTransferable} が画像用の {@link DataFlavor#imageFlavor} を正しく公開する契約テスト
 * (クリップボードへの実書き込みを伴わないため headless でも安全)。
 */
public class ImageTransferableTest {

    private static BufferedImage img() {
        return new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    public void exposesOnlyImageFlavor() {
        ImageTransferable t = new ImageTransferable(img());
        assertArrayEquals(new DataFlavor[] { DataFlavor.imageFlavor },
                t.getTransferDataFlavors());
        assertTrue(t.isDataFlavorSupported(DataFlavor.imageFlavor));
        assertFalse(t.isDataFlavorSupported(DataFlavor.stringFlavor));
    }

    @Test
    public void returnsTheImageForImageFlavor() throws Exception {
        BufferedImage src = img();
        ImageTransferable t = new ImageTransferable(src);
        assertSame(src, t.getTransferData(DataFlavor.imageFlavor));
    }

    @Test
    public void rejectsUnsupportedFlavor() {
        ImageTransferable t = new ImageTransferable(img());
        try {
            t.getTransferData(DataFlavor.stringFlavor);
            fail("expected UnsupportedFlavorException for a non-image flavor");
        } catch (UnsupportedFlavorException expected) {
            // 期待どおり: 画像以外のフレーバは拒否する
        }
    }
}
