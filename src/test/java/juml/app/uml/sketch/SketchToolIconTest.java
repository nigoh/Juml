// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchToolIcon} が全モードでアイコンを生成し、実際に何かを描くことを
 * 検証するテスト (ヘッドレス環境でも BufferedImage への描画で確認できる)。
 * {@link SketchToolIcon#install} のコンボ選択欄レンダラー (index&lt;0 分岐) を検証するテストだけは
 * {@link JComboBox} / {@link JList} の生成を伴うため、そのテストに限りヘッドレス環境でスキップする
 * (他のテストはヘッドレス環境でも動くという既存の性質を保つ)。
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

    @Test
    public void install_selectionBoxRenderer_showsIconForKnownValue() {
        // ヘッドレス環境では JComboBox/JList の生成が失敗しうるためこのテストだけスキップする。
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
        String[] items = {"Select", "Extends", "Association"};
        Icon[] icons = {
                SketchToolIcon.forRelation(null),
                SketchToolIcon.forRelation(SketchRelation.Kind.EXTENDS),
                SketchToolIcon.forRelation(SketchRelation.Kind.ASSOCIATION),
        };
        JLabel rendered = GuiActionRunner.execute(() -> {
            JComboBox<String> combo = new JComboBox<>(items);
            SketchToolIcon.install(combo, icons);
            ListCellRenderer<? super String> renderer = combo.getRenderer();
            // index < 0 はコンボの選択欄 (閉じているときの表示部) 相当。値からモデル内の
            // 位置を indexOf で引き直して対応アイコンを表示する分岐を検証する。
            return (JLabel) renderer.getListCellRendererComponent(
                    new JList<>(items), "Association", -1, false, false);
        });
        assertSame("選択欄でも値から位置を引き直して対応アイコンを表示するはず",
                icons[2], rendered.getIcon());
    }

    @Test
    public void install_selectionBoxRenderer_leavesIconUnsetForUnknownValue() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
        String[] items = {"Select", "Extends"};
        Icon[] icons = {
                SketchToolIcon.forRelation(null),
                SketchToolIcon.forRelation(SketchRelation.Kind.EXTENDS),
        };
        JLabel rendered = GuiActionRunner.execute(() -> {
            JComboBox<String> combo = new JComboBox<>(items);
            SketchToolIcon.install(combo, icons);
            ListCellRenderer<? super String> renderer = combo.getRenderer();
            // コンボのモデルに無い値 (indexOf が -1) では対応アイコンが引けないため、
            // アイコンを設定しない境界を確認する。
            return (JLabel) renderer.getListCellRendererComponent(
                    new JList<>(items), "NotInModel", -1, false, false);
        });
        assertNull("モデルに無い値は indexOf が -1 を返しアイコンを設定しないはず",
                rendered.getIcon());
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
