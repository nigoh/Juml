// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link LoadingGlassPane} の表示状態とステータス更新の振る舞いを検証する。
 *
 * <p>{@link javax.swing.JComponent} ベースでネイティブピアを持たず、表示状態は
 * {@code visible} フラグで管理されるためヘッドレスで完結する（{@code paintComponent}
 * は呼ばない）。</p>
 */
public class LoadingGlassPaneTest {

    @Test
    public void newGlassPane_isHiddenInitially() {
        LoadingGlassPane glass = new LoadingGlassPane();
        assertFalse("生成直後は非表示であること", glass.isVisible());
    }

    @Test
    public void showOverlay_makesVisible_hideOverlay_hides() {
        LoadingGlassPane glass = new LoadingGlassPane();
        glass.showOverlay();
        assertTrue("showOverlay で表示されること", glass.isVisible());

        glass.hideOverlay();
        assertFalse("hideOverlay で非表示に戻ること", glass.isVisible());
    }

    @Test
    public void hideOverlay_disablesFocusable() {
        LoadingGlassPane glass = new LoadingGlassPane();
        glass.showOverlay();
        assertTrue("オーバーレイ表示中はフォーカス可能であること", glass.isFocusable());
        glass.hideOverlay();
        assertFalse("非表示化でフォーカス不可に戻ること", glass.isFocusable());
    }

    @Test
    public void setStatus_doesNotThrow_includingNull() {
        LoadingGlassPane glass = new LoadingGlassPane();
        glass.setStatus("解析中…");
        glass.setStatus(null); // null でも例外を出さないこと（空文字に正規化）
    }
}
