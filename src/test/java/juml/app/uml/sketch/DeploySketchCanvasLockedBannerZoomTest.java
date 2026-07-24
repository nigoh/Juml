// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import juml.app.uml.sketch.DeploySketchModel.DeployNode;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * {@link DeploySketchCanvas} のロックバナー (未対応構文で編集を無効化したときの警告帯) が、
 * ズーム倍率に依らずキャンバス幅いっぱいに跨ることを検証する
 * (bug-hunt round 1: Component/Deploy: ロックバナーがズームに依らずキャンバス幅を跨ぐ)。
 *
 * <p>修正前はバナーを {@link SketchViewport#applyTransform} 適用後 (ズームで縮小された)
 * {@code Graphics2D} に描いていたため、{@code SketchBanner.paint} が渡された
 * {@code target.getWidth()} いっぱいに {@code fillRect} していても、実際の画面上では
 * ズーム倍率分だけ縮小されて描かれ、縮小時 (例 0.25 倍) は幅の 25% ほどしか帯が伸びない
 * バグがあった。修正後はズーム変換を適用しない別の overlay {@code Graphics2D} へ描くため、
 * 常にキャンバス幅いっぱいに跨る。</p>
 */
public class DeploySketchCanvasLockedBannerZoomTest {

    @Before
    public void requireDisplay() {
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private DeploySketchCanvas.Listener noopListener() {
        return new DeploySketchCanvas.Listener() {
            @Override public void modelEdited() {
            }

            @Override public void editNodeRequested(DeployNode n) {
            }
        };
    }

    /** {@code SketchBanner} の帯色 (RGB, アルファ抜き)。 */
    private static final int BANNER_RGB = 0xB71C1C;

    /**
     * 上端バナー行 ({@code y}) のうちバナー色の画素がキャンバス幅の何割を占めるかを返す。
     * ズーム変換の影響を受けていれば、この比率が大きく下がる (縮小分しか帯が伸びない)。
     */
    private static double bannerRowCoverage(BufferedImage img, int y, int width) {
        int count = 0;
        for (int x = 0; x < width; x++) {
            int rgb = img.getRGB(x, y) & 0x00FFFFFF;
            if (rgb == BANNER_RGB) {
                count++;
            }
        }
        return count / (double) width;
    }

    @Test
    public void lockedBanner_spansCanvasWidthRegardlessOfZoom() {
        DeploySketchCanvas canvas = GuiActionRunner.execute(() -> new DeploySketchCanvas(noopListener()));
        DeploySketchModel model = new DeploySketchModel();
        model.getNodes().add(new DeployNode(DeployNode.Kind.NODE, "N", null, 40, 40));

        int width = 800;
        int height = 600;
        GuiActionRunner.execute(() -> {
            // 未対応キーワードのブロック 1 行を与えてロック状態 (editable=false) にする。
            canvas.setModel(model, false, List.of("package \"P\" {"));
            canvas.setSize(width, height);
            canvas.setZoomForTest(0.25); // 大きく縮小してもバナーは影響を受けないはず。
        });

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                canvas.paintComponent(g2);
            } finally {
                g2.dispose();
            }
        });

        // バナー上端の余白帯 (SketchBanner の pad=5px 分。見出し文字の上昇部より上で
        // 文字の白抜きが混ざらない) をサンプリングし、帯の塗り (fillRect) だけを見る。
        double coverage = bannerRowCoverage(img, 3, width);
        assertTrue("ロックバナーはズーム 0.25 倍でもキャンバス幅の 90% 以上に跨るはず (実測 "
                + coverage + ")", coverage >= 0.9);
    }
}
