// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * {@link SeqSketchCanvas} の端点ハンドル色 (bug-hunt round2 issue G) の回帰テスト。
 *
 * <p>Seq の端点ハンドルは元々ライフライン色 (0x90A4AE) に近い灰色の円で描かれており、
 * 他 6 キャンバス (青 0x1565C0 の正方形ハンドル) と比べて発見しづらかった。ここでは
 * 描画そのものではなく色定数を検証する: 他キャンバスと同じ青であり、かつライフライン色
 * とは明確に異なること (純関数寄りの検証のため headless でもスキップしない)。</p>
 */
public class SeqSketchCanvasEndpointHandleColorTest {

    @Test
    public void handleColor_isSameBlueAsOtherSixCanvases() {
        assertEquals("他 6 キャンバスと同じ青 (0x1565C0) のはず",
                new Color(0x1565C0), SeqSketchCanvas.HANDLE_COLOR);
    }

    @Test
    public void handleColor_isNotTheLifelineGray() {
        Color lifelineColor = new Color(0x90A4AE);
        assertNotEquals("ライフライン色と紛れてはいけないはず",
                lifelineColor, SeqSketchCanvas.HANDLE_COLOR);
    }
}
