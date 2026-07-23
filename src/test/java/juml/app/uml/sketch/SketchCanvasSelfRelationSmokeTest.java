// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code A --> A} のような自己関連を含むモデルの描画スモークテスト。
 *
 * <p>修正前の {@link SketchCanvas} は自己関連 (始点=終点) を通常の線分計算にかけて
 * しまい、退化した点として潰れていた。修正後はボックス右上へ回り込むループとして
 * 専用描画するため、ボックス上方にも線が描かれる (SketchCanvas.java の
 * {@code paintSelfRelation} 参照)。ここでは (1) 例外を投げずに描画できること、
 * (2) 通常の (自己関連でない) 描画と見た目が異なること、の 2 点を最低限のスモークで
 * 確認する。ピクセル比較のみを行う純粋な Graphics2D 描画のため、実ディスプレイ
 * (Robot) は不要で headless でも安全に実行できる。</p>
 */
public class SketchCanvasSelfRelationSmokeTest {

    private static SketchCanvas.Listener noopListener() {
        return new SketchCanvas.Listener() {
            @Override
            public void modelEdited() {
            }

            @Override
            public void editRequested(SketchClass c) {
            }

            @Override
            public void addClassRequested(Point at) {
            }
        };
    }

    private static SketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new SketchCanvas(noopListener()));
    }

    /** {@code canvas} を 500x400 の {@link BufferedImage} へ描画して返す。 */
    private static BufferedImage paint(SketchCanvas canvas) {
        BufferedImage img = new BufferedImage(500, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(500, 400);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
        return img;
    }

    /**
     * ボックス上端よりさらに上の帯 (自己関連ループが描かれる領域) に、背景 (白/透明)
     * 以外の画素があるか。
     */
    private static boolean hasNonBackgroundPixelAbove(BufferedImage img, SketchClass c) {
        int y0 = Math.max(0, c.getY() - 30);
        int y1 = Math.max(0, c.getY() - 2);
        int x0 = Math.max(0, c.getX());
        int x1 = Math.min(img.getWidth() - 1, c.getX() + 300);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int argb = img.getRGB(x, y);
                boolean transparent = (argb >>> 24) == 0;
                boolean white = (argb & 0x00FFFFFF) == 0x00FFFFFF;
                if (!transparent && !white) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void enteringRelationMode_clearsSelection() {
        // 関係追加モードに入ると旧選択がクリアされること。残すとダブルクリック編集/Delete が
        // 旧クラスへ漏れ、描画中に無関係なクラスの編集ダイアログ表示・破壊的削除が起きる。
        SketchCanvas canvas = newCanvas();
        SketchModel model = new SketchModel();
        SketchClass a = new SketchClass("A", SketchClass.Kind.CLASS, 60, 60);
        model.getClasses().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSelectedForTest(a); // press でクラス A を選択した状態を再現
        });
        assertTrue("前提: A が選択されている",
                GuiActionRunner.execute(() -> canvas.selectedForTest() == a));
        GuiActionRunner.execute(() -> canvas.setRelationMode(SketchRelation.Kind.EXTENDS));
        assertTrue("関係モードに入ると選択はクリアされるはず",
                GuiActionRunner.execute(() -> canvas.selectedForTest() == null));
    }

    @Test
    public void selfRelation_createdByClickingSameClassTwice() {
        // 同一クラスを 2 回クリックすると自己関連 (A→A) が作られること。以前は
        // handleRelationClick が relationSource != hit を要求し、自己関連を作れなかった。
        SketchCanvas canvas = newCanvas();
        SketchModel model = new SketchModel();
        SketchClass a = new SketchClass("A", SketchClass.Kind.CLASS, 60, 60);
        model.getClasses().add(a);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setRelationMode(SketchRelation.Kind.ASSOCIATION);
            canvas.relationClickForTest(a);
            canvas.relationClickForTest(a); // 同一クラス 2 回目 → 自己関連
        });
        assertEquals("自己関連が 1 本作られるはず", 1, canvas.model().getRelations().size());
        SketchRelation r = canvas.model().getRelations().get(0);
        assertEquals("A", r.getLeft());
        assertEquals("A", r.getRight());
    }

    @Test
    public void selfRelation_paintsWithoutThrowing() {
        SketchCanvas canvas = newCanvas();
        SketchModel model = new SketchModel();
        SketchClass a = new SketchClass("A", SketchClass.Kind.CLASS, 60, 60);
        model.getClasses().add(a);
        model.getRelations().add(
                new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "A", null));
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));

        // 最低限の保証: A --> A を含むモデルの描画で例外が飛ばないこと。
        paint(canvas);
    }

    @Test
    public void selfRelation_drawsLoopAboveBox_unlikeClassWithNoRelations() {
        SketchCanvas selfCanvas = newCanvas();
        SketchModel selfModel = new SketchModel();
        SketchClass selfA = new SketchClass("A", SketchClass.Kind.CLASS, 60, 60);
        selfModel.getClasses().add(selfA);
        selfModel.getRelations().add(
                new SketchRelation("A", SketchRelation.Kind.ASSOCIATION, "A", null));
        GuiActionRunner.execute(() -> selfCanvas.setModel(selfModel, true, Collections.emptyList()));
        BufferedImage selfImg = paint(selfCanvas);

        SketchCanvas plainCanvas = newCanvas();
        SketchModel plainModel = new SketchModel();
        SketchClass plainA = new SketchClass("A", SketchClass.Kind.CLASS, 60, 60);
        plainModel.getClasses().add(plainA);
        // 関係なし (通常のクラス単体描画): ボックス上方には何も描かれないはず。
        GuiActionRunner.execute(() -> plainCanvas.setModel(plainModel, true, Collections.emptyList()));
        BufferedImage plainImg = paint(plainCanvas);

        assertTrue("自己関連 (A --> A) はボックス上方にループ線を描くはず",
                hasNonBackgroundPixelAbove(selfImg, selfA));
        assertFalse("関係を持たないクラス単体の描画ではボックス上方に何も描かれないはず",
                hasNonBackgroundPixelAbove(plainImg, plainA));
    }
}
