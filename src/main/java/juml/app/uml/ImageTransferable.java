// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

/**
 * {@link BufferedImage} を {@link DataFlavor#imageFlavor} でシステムクリップボードへ渡す
 * {@link Transferable}。JDK は文字列用の {@code StringSelection} は用意するが画像用は
 * 用意しないため、図のラスタをそのまま Slack / Docs / Confluence 等へ貼り付けられるよう
 * 最小限の実装を持つ。
 */
final class ImageTransferable implements Transferable {

    private final BufferedImage image;

    ImageTransferable(BufferedImage image) {
        this.image = image;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { DataFlavor.imageFlavor };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.imageFlavor.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!DataFlavor.imageFlavor.equals(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return image;
    }
}
