// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Test;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import java.awt.GraphicsEnvironment;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * これまでテストが 1 件も無かった package-private コンポーネント群のスモークテスト。
 *
 * <p>目的は「構築して例外が出ないこと」と「主要プロパティの往復」の保証まで。
 * 描画 (paint) の見た目は検証しない。ヘッドレスでも動くもの
 * (DiagramSearchLayer / DiagramMinimap / FontPickerField) はガード無しで実行し、
 * 表示ウィンドウが必要なもの (SplashWindow / ToastNotification) だけ
 * ヘッドレスガードを掛ける。</p>
 */
public class ComponentSmokeTest {

    /** repaint 回数を数えるためのホスト。 */
    private static final class CountingPanel extends JPanel {
        final AtomicInteger repaints = new AtomicInteger();

        @Override
        public void repaint() {
            // JPanel のコンストラクタからも repaint() が呼ばれる
            // (フィールド初期化前) ため null ガードが必要。
            if (repaints != null) {
                repaints.incrementAndGet();
            }
            super.repaint();
        }
    }

    // ------------------------------------------------------------------
    // DiagramSearchLayer
    // ------------------------------------------------------------------

    @Test
    public void searchLayer_setHits_requestsRepaint() {
        GuiActionRunner.execute(() -> {
            CountingPanel host = new CountingPanel();
            DiagramSearchLayer layer = new DiagramSearchLayer(host);
            int before = host.repaints.get();
            List<Rectangle2D> hits = Arrays.asList(
                    new Rectangle2D.Double(0, 0, 10, 10),
                    new Rectangle2D.Double(20, 20, 10, 10));
            layer.set(hits, 0);
            assertTrue("set() はホストの再描画を要求するはず",
                    host.repaints.get() > before);
        });
    }

    @Test
    public void searchLayer_clearWithoutHits_isNoOp() {
        GuiActionRunner.execute(() -> {
            CountingPanel host = new CountingPanel();
            DiagramSearchLayer layer = new DiagramSearchLayer(host);
            int before = host.repaints.get();
            layer.clear();
            assertEquals("ヒットが無い状態の clear() は再描画しないはず",
                    before, host.repaints.get());
        });
    }

    @Test
    public void searchLayer_clearAfterSet_requestsRepaint() {
        GuiActionRunner.execute(() -> {
            CountingPanel host = new CountingPanel();
            DiagramSearchLayer layer = new DiagramSearchLayer(host);
            layer.set(Arrays.asList(new Rectangle2D.Double(0, 0, 5, 5)), 0);
            int before = host.repaints.get();
            layer.clear();
            assertTrue("ヒットがある状態の clear() は再描画を要求するはず",
                    host.repaints.get() > before);
        });
    }

    @Test
    public void searchLayer_nullOrEmptyHits_treatedAsClear() {
        GuiActionRunner.execute(() -> {
            CountingPanel host = new CountingPanel();
            DiagramSearchLayer layer = new DiagramSearchLayer(host);
            // null / 空リストで例外にならないこと (クリア相当として扱われる)
            layer.set(null, -1);
            layer.set(java.util.Collections.emptyList(), 3);
            layer.reset();
        });
    }

    // ------------------------------------------------------------------
    // DiagramMinimap
    // ------------------------------------------------------------------

    @Test
    public void minimap_invalidate_doesNotThrow() {
        GuiActionRunner.execute(() -> {
            DiagramMinimap minimap = new DiagramMinimap();
            // サムネイルキャッシュが空でも invalidate は安全であること
            minimap.invalidate();
            minimap.invalidate();
        });
    }

    // ------------------------------------------------------------------
    // FontPickerField
    // ------------------------------------------------------------------

    @Test
    public void fontPicker_nullInitialFont_isAutoDetect() {
        GuiActionRunner.execute(() -> {
            FontPickerField picker = new FontPickerField(null);
            assertNotNull("コンボボックスが構築されるはず", picker.getComboBox());
            assertNotNull("プレビューラベルが構築されるはず", picker.getPreview());
            assertEquals("初期フォント未指定は自動検出 (空文字) のはず",
                    "", picker.fontName());
        });
    }

    @Test
    public void fontPicker_namedFont_roundTrips() {
        GuiActionRunner.execute(() -> {
            FontPickerField picker = new FontPickerField("MyCustomFont");
            assertEquals("一覧に無い任意フォント名も fontName() で往復するはず",
                    "MyCustomFont", picker.fontName());
        });
    }

    @Test
    public void fontPicker_reset_returnsToAutoDetect() {
        GuiActionRunner.execute(() -> {
            FontPickerField picker = new FontPickerField("MyCustomFont");
            picker.reset();
            assertEquals("reset() 後は自動検出 (空文字) に戻るはず",
                    "", picker.fontName());
        });
    }

    // ------------------------------------------------------------------
    // SplashWindow (要ディスプレイ)
    // ------------------------------------------------------------------

    @Test
    public void splashWindow_displayAndClose_disposesWindow() {
        Assume.assumeFalse("ヘッドレス環境では JWindow を生成できないためスキップ",
                GraphicsEnvironment.isHeadless());
        SplashWindow splash = GuiActionRunner.execute(SplashWindow::display);
        try {
            assertTrue("display() 直後は表示されているはず",
                    GuiActionRunner.execute(splash::isVisible));
        } finally {
            GuiActionRunner.execute(() -> splash.close());
        }
        assertFalse("close() 後はウィンドウが破棄されているはず",
                GuiActionRunner.execute(splash::isDisplayable));
    }

    // ------------------------------------------------------------------
    // ToastNotification (要ディスプレイ)
    // ------------------------------------------------------------------

    @Test
    public void toast_withoutRootPane_isSilentNoOp() {
        // ルートペインに載っていないコンポーネントを anchor にしても例外にならないこと
        GuiActionRunner.execute(() -> ToastNotification.show(new JPanel(), "orphan"));
    }

    @Test
    public void toast_show_addsToastToPopupLayer() {
        Assume.assumeFalse("ヘッドレス環境では JFrame を生成できないためスキップ",
                GraphicsEnvironment.isHeadless());
        JFrame frame = GuiActionRunner.execute(() -> {
            JFrame f = new JFrame("toast-smoke");
            f.setSize(400, 300);
            f.setVisible(true);
            return f;
        });
        try {
            int added = GuiActionRunner.execute(() -> {
                JLayeredPane layered = frame.getRootPane().getLayeredPane();
                int before = layered.getComponentCountInLayer(
                        JLayeredPane.POPUP_LAYER.intValue());
                ToastNotification.show(frame.getRootPane(), "hello toast");
                return layered.getComponentCountInLayer(
                        JLayeredPane.POPUP_LAYER.intValue()) - before;
            });
            assertEquals("show() で POPUP_LAYER にトーストが 1 件追加されるはず", 1, added);
        } finally {
            GuiActionRunner.execute(frame::dispose);
        }
    }
}
