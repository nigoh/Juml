// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ErSketchCanvas} の描画スモークテスト。crow's-foot 端点・主キー区切り線・
 * 自己関連ループを含むモデルを {@link BufferedImage} へ描画して例外が飛ばないこと、
 * および関係追加モードで選択がクリアされることを確認する。ピクセル比較のみを行う
 * 純粋な Graphics2D 描画のため、実ディスプレイ (Robot) は不要で headless でも安全。
 */
public class ErSketchCanvasSmokeTest {

    private static ErSketchCanvas.Listener noopListener() {
        return new ErSketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editEntityRequested(ErSketchModel.Entity e) {
            }
        };
    }

    private static ErSketchCanvas newCanvas() {
        return GuiActionRunner.execute(() -> new ErSketchCanvas(noopListener()));
    }

    private static ErSketchModel sampleModel() {
        ErSketchModel model = new ErSketchModel();
        ErSketchModel.Entity user = new ErSketchModel.Entity("User", "User", 60, 60);
        user.getColumns().add(new ErSketchModel.Column(true, "id", "int"));
        user.getColumns().add(new ErSketchModel.Column(false, "name", "varchar"));
        ErSketchModel.Entity post = new ErSketchModel.Entity("Post", null, 300, 80);
        post.getColumns().add(new ErSketchModel.Column(true, "id", "int"));
        post.getColumns().add(new ErSketchModel.Column(false, "user_id", "int"));
        model.getEntities().add(user);
        model.getEntities().add(post);
        model.getRelations().add(new ErSketchModel.Relation(
                "User", ErSketchModel.Cardinality.EXACTLY_ONE,
                ErSketchModel.Cardinality.ZERO_OR_MANY, "Post", "has"));
        // 自己関連 (crow's-foot ループ) も描画経路を通す。
        model.getRelations().add(new ErSketchModel.Relation(
                "User", ErSketchModel.Cardinality.ONE_OR_MANY,
                ErSketchModel.Cardinality.ZERO_OR_ONE, "User", null));
        return model;
    }

    private static BufferedImage paint(ErSketchCanvas canvas) {
        BufferedImage img = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.setSize(600, 400);
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });
        return img;
    }

    private static boolean hasNonBackgroundPixel(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
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
    public void model_paintsEntitiesAndRelationsWithoutThrowing() {
        ErSketchCanvas canvas = newCanvas();
        ErSketchModel model = sampleModel();
        GuiActionRunner.execute(() -> canvas.setModel(model, true, Collections.emptyList()));
        BufferedImage img = paint(canvas);
        assertTrue("エンティティ/リレーションが描画され画素が出るはず",
                hasNonBackgroundPixel(img));
    }

    @Test
    public void lockedModel_paintsBannerWithoutThrowing() {
        // 未対応構文 (編集ロック) のモデルもバナー描画で例外を飛ばさないこと。
        ErSketchCanvas canvas = newCanvas();
        ErSketchModel model = sampleModel();
        GuiActionRunner.execute(() ->
                canvas.setModel(model, false, Collections.singletonList("package P {")));
        paint(canvas);
    }

    @Test
    public void enteringRelationMode_clearsSelection() {
        ErSketchCanvas canvas = newCanvas();
        ErSketchModel model = sampleModel();
        ErSketchModel.Entity user = model.getEntities().get(0);
        GuiActionRunner.execute(() -> {
            canvas.setModel(model, true, Collections.emptyList());
            canvas.setSelectedForTest(user);
        });
        assertTrue("前提: User が選択されている",
                GuiActionRunner.execute(() -> canvas.selectedForTest() == user));
        GuiActionRunner.execute(() -> canvas.setRelationMode(true));
        assertNull("関係追加モードに入ると選択はクリアされるはず",
                GuiActionRunner.execute(() -> canvas.selectedForTest()));
    }
}
