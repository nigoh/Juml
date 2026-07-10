// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import javax.swing.Icon;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchToolIcon} が全モードでアイコンを生成し、実際に何かを描くことを
 * 検証するテスト (ヘッドレス環境でも BufferedImage への描画で確認できる)。
 */
public class SketchToolIconTest {

    @Test
    public void forRelation_allKindsAndSelect_paintSomething() {
        assertPaints(SketchToolIcon.forRelation(null));
        for (SketchRelation.Kind kind : SketchRelation.Kind.values()) {
            assertPaints(SketchToolIcon.forRelation(kind));
        }
    }

    @Test
    public void forMessage_allArrowsAndSelect_paintSomething() {
        assertPaints(SketchToolIcon.forMessage(null));
        for (SeqItem.Arrow arrow : SeqItem.Arrow.values()) {
            assertPaints(SketchToolIcon.forMessage(arrow));
        }
    }

    /** アイコンが非 null で、透明キャンバスに 1 ピクセル以上描くこと。 */
    private static void assertPaints(Icon icon) {
        assertNotNull(icon);
        assertTrue(icon.getIconWidth() > 0);
        assertTrue(icon.getIconHeight() > 0);
        BufferedImage img = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        icon.paintIcon(null, g2, 0, 0);
        g2.dispose();
        boolean any = false;
        for (int y = 0; y < img.getHeight() && !any; y++) {
            for (int x = 0; x < img.getWidth() && !any; x++) {
                any = (img.getRGB(x, y) >>> 24) != 0;
            }
        }
        assertTrue("何も描かれないアイコンは不可", any);
    }
}
