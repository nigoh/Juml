// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.awt.geom.Rectangle2D;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * {@link DiagramSearchLayer#paint} が描画後に Graphics2D の状態 (色・ストローク) を
 * 呼び出し元へ戻すことを検証する。{@link SvgPreviewPanel} は同一フレームで scratch g2 を
 * 付箋・検索・ミニマップの各レイヤに使い回すため、色を戻し忘れるとヒットの塗り色が
 * 後続レイヤに漏れる。BufferedImage 上で動くため headless でも実行できる。
 */
public class DiagramSearchLayerPaintTest {

    @Test
    public void paint_restoresColorAndStroke() {
        DiagramSearchLayer layer = new DiagramSearchLayer(new JPanel());
        layer.set(List.of(new Rectangle2D.Double(5, 5, 20, 10),
                new Rectangle2D.Double(40, 40, 15, 8)), 0);

        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            Color sentinelColor = Color.MAGENTA;
            Stroke sentinelStroke = new BasicStroke(3.5f);
            g2.setColor(sentinelColor);
            g2.setStroke(sentinelStroke);

            layer.paint(g2, 1.0);

            assertEquals("paint 後は色が呼び出し元の値へ戻るはず",
                    sentinelColor, g2.getColor());
            assertEquals("paint 後はストロークが呼び出し元の値へ戻るはず",
                    sentinelStroke, g2.getStroke());
        } finally {
            g2.dispose();
        }
    }

    @Test
    public void paint_emptyHits_leavesGraphicsUntouched() {
        DiagramSearchLayer layer = new DiagramSearchLayer(new JPanel());
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setColor(Color.GREEN);
            layer.paint(g2, 1.0);
            assertEquals(Color.GREEN, g2.getColor());
        } finally {
            g2.dispose();
        }
    }
}
