// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import org.junit.Test;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * 追加した付箋が<b>書き出される図の範囲内</b>に置かれることの回帰テスト。
 *
 * <p>追加位置は下限 0 だけを見ていて上限が無かった。図がウィンドウより小さいとき
 * {@link javax.swing.JViewport} はビューをウィンドウ大まで引き伸ばすので、画面上は
 * どこにでも置けてしまう。しかし PNG も SVG も<b>図の内容矩形</b>で寸法が決まるため、
 * 内容矩形の外に置かれた付箋は書き出しから黙って消える — アプリでは見えているのに
 * 共有した成果物には無い、という一番たちの悪い形になっていた。</p>
 */
public class NoteStaysInsideExportBoxTest {

    /** 300x200 の図を持つプレビューパネル (画像モードで内容矩形を確定させる)。 */
    private static SvgPreviewPanel panelWithSmallDiagram() {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));
        return panel;
    }

    private static void assertInside(SvgPreviewPanel panel, String label) {
        List<DiagramNote> notes = panel.notes().getNotes();
        assertTrue(label + ": 付箋が 1 件あること", notes.size() == 1);
        DiagramNote n = notes.get(0);
        assertTrue(label + ": 原点が内容矩形の中にあること x=" + n.getX(),
                n.getX() >= 0 && n.getX() <= panel.contentWidth());
        assertTrue(label + ": 原点が内容矩形の中にあること y=" + n.getY(),
                n.getY() >= 0 && n.getY() <= panel.contentHeight());
    }

    /** ウィンドウ中央 (図の外) を指しても、内容矩形の中へ寄せること。 */
    @Test
    public void aNoteAddedFarOutsideTheDiagramIsPulledBackIn() {
        SvgPreviewPanel panel = panelWithSmallDiagram();

        // 実測された症状と同じ座標: 1197x797 のパネル中央。
        panel.notes().addNoteAt(new Point(598, 398), 1.0);

        assertInside(panel, "遠方");
    }

    /** 右クリック位置が図の少し外でも同じこと。 */
    @Test
    public void aNoteAddedJustOutsideTheDiagramIsPulledBackIn() {
        SvgPreviewPanel panel = panelWithSmallDiagram();

        panel.notes().addNoteAt(new Point(320, 210), 1.0);

        assertInside(panel, "近傍");
    }

    /** 非退行: 図の中に置いた付箋は動かさないこと。 */
    @Test
    public void aNoteAddedInsideTheDiagramKeepsItsPosition() {
        SvgPreviewPanel panel = panelWithSmallDiagram();

        panel.notes().addNoteAt(new Point(10, 20), 1.0);

        List<DiagramNote> notes = panel.notes().getNotes();
        assertTrue("x が動かないこと: " + notes.get(0).getX(), notes.get(0).getX() == 10);
        assertTrue("y が動かないこと: " + notes.get(0).getY(), notes.get(0).getY() == 20);
    }

    /**
     * 回帰: 追加以外の経路 (ドラッグ・矢印キー・複製・ペースト) も範囲内に留めること。
     *
     * <p>クランプを追加経路にだけ入れていた。付箋は追加したあと<b>動かす</b>ものなので、
     * 移動が野放しなら「書き出しから黙って消える」症状はそのまま残る。同じファイルの
     * 隣の経路に同じ規則を適用し忘れた、という一点で、追加経路の修正時に気付くべきだった。</p>
     */
    @Test
    public void everyPlacementPathKeepsTheNoteInsideTheBox() {
        SvgPreviewPanel panel = panelWithSmallDiagram();
        DiagramNotesLayer layer = panel.notes();
        layer.addNoteAt(new Point(10, 20), 1.0);

        // 矢印キーで大きく外へ動かす (Shift 付きの粗い移動を何度も)。
        for (int i = 0; i < 60; i++) {
            layer.moveSelected(20, 20);
        }
        assertInside(panel, "矢印キー");

        // 複製・ペーストのオフセットも範囲内に留まること。
        layer.duplicateSelected();
        for (DiagramNote n : layer.getNotes()) {
            assertTrue("複製後も範囲内: x=" + n.getX(),
                    n.getX() >= 0 && n.getX() <= panel.contentWidth());
            assertTrue("複製後も範囲内: y=" + n.getY(),
                    n.getY() >= 0 && n.getY() <= panel.contentHeight());
        }
    }

    /**
     * 非退行: 図の大きさが分からないとき (内容なし) は従来どおり下限だけを見ること。
     *
     * <p>{@link DiagramNotesLayer} は単体でも使われるので、所有者が図の寸法を答えられない
     * 場合に位置を 0 へ潰してはいけない。</p>
     */
    @Test
    public void withoutContentOnlyTheLowerBoundApplies() {
        DiagramNotesLayer layer = new DiagramNotesLayer(new javax.swing.JPanel());

        layer.addNoteAt(new Point(400, 300), 1.0);

        DiagramNote n = layer.getNotes().get(0);
        assertTrue("下限だけが効くこと: " + n.getX() + "," + n.getY(),
                n.getX() == 400 && n.getY() == 300);
    }

    /**
     * 大きさを変える経路も書き出し範囲に収まること。
     *
     * <p>付箋が占める矩形は原点と<b>大きさ</b>の両方で決まるのに、クランプは原点にしか
     * 入っていなかった。リサイズは図の外まで広げ放題で、プレビューでは全部見えるのに
     * ({@code JViewport} がビューを引き伸ばす) 書き出しは図の内容矩形で寸法が決まるため
     * 本文ごと切り落とされる — {@code placeFree} が潰したはずの症状が、この 1 経路だけに
     * 残っていた。</p>
     */
    @Test
    public void resizingAlsoKeepsTheNoteInsideTheBox() {
        SvgPreviewPanel panel = panelWithSmallDiagram();
        DiagramNotesLayer layer = panel.notes();
        layer.addNoteAt(new Point(10, 20), 1.0);
        DiagramNote n = layer.getNotes().get(0);

        // 右下のリサイズハンドルを掴んで、図のはるか外へドラッグする。
        int hx = (int) Math.round(n.getX() + n.getWidth()) - 2;
        int hy = (int) Math.round(n.getY() + n.getHeight()) - 2;
        assertTrue("リサイズ操作が始まること",
                layer.pressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 1L, 0,
                        hx, hy, 1, false, MouseEvent.BUTTON1), 1.0));
        layer.dragged(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 1L, 0,
                1100, 760, 0, false, MouseEvent.BUTTON1), 1.0);
        layer.released();

        DiagramNote after = layer.getNotes().get(0);
        assertTrue("右端が書き出し範囲を超えないこと: right="
                        + (after.getX() + after.getWidth()) + " limit=" + panel.contentWidth(),
                after.getX() + after.getWidth() <= panel.contentWidth() + 0.5);
        assertTrue("下端が書き出し範囲を超えないこと: bottom="
                        + (after.getY() + after.getHeight()) + " limit=" + panel.contentHeight(),
                after.getY() + after.getHeight() <= panel.contentHeight() + 0.5);
    }

    /**
     * 大きさを決める経路もすべて範囲内であること (生成・複製・貼り付け)。
     *
     * <p>ラウンド 21 は大きさのクランプをリサイズにだけ入れた。生成は既定 280x160 を
     * 無条件に設定するので、図が小さいと追加した瞬間に書き出しからはみ出す。
     * 貼り付けはクリップボードがタブ間共有なので、大きい図で作った付箋がそのまま
     * 小さい図へ入る。</p>
     */
    @Test
    public void everySizingPathKeepsTheNoteInsideTheBox() {
        SvgPreviewPanel tiny = new SvgPreviewPanel();
        tiny.setImage(new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB));
        tiny.notes().addNoteAt(new Point(10, 10), 1.0);
        assertFits(tiny, "生成");

        SvgPreviewPanel big = new SvgPreviewPanel();
        big.setImage(new BufferedImage(2000, 1500, BufferedImage.TYPE_INT_ARGB));
        big.notes().addNoteAt(new Point(100, 100), 1.0);
        DiagramNote b = big.notes().getNotes().get(0);
        b.setWidth(900);
        b.setHeight(800);
        big.notes().copySelected();

        SvgPreviewPanel small = new SvgPreviewPanel();
        small.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));
        assertTrue("貼り付けが成功すること", small.notes().pasteClipboard());
        assertFits(small, "貼り付け");

        small.notes().duplicateSelected();
        assertFits(small, "複製");
    }

    /**
     * 「本文に合わせて高さ調整」が幅を変えず、本文が収まる高さを与えること。
     *
     * <p>ラウンド 21 で大きさのクランプを素通しで使った結果、幅まで縮み、図の下端寄りでは
     * <b>本文が入らない高さ</b>を返すようになっていた。矩形が範囲に収まればよいのだから、
     * 高さを削るのではなく付箋を上へ寄せればよい。</p>
     */
    @Test
    public void fitHeightKeepsTheWidthAndActuallyFits() {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));
        DiagramNotesLayer layer = panel.notes();
        layer.addNoteAt(new Point(0, 150), 1.0);
        DiagramNote n = layer.getNotes().get(0);
        n.setText("l1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9\nl10");
        double width = n.getWidth();
        double needed = new NoteRenderer().contentHeight(n);

        layer.fitHeightSelected();

        DiagramNote after = layer.getNotes().get(0);
        assertTrue("幅は変えないこと: " + width + " -> " + after.getWidth(),
                after.getWidth() == width);
        assertTrue("本文が収まる高さになること: 必要 " + needed + " / 実際 " + after.getHeight(),
                after.getHeight() >= needed);
        assertFits(panel, "高さ合わせ");
    }

    /**
     * 大きさを変える操作は<b>原点を動かさない</b>こと。
     *
     * <p>「矩形を範囲へ寄せる」不変条件をリサイズにもそのまま使ったせいで、幅が箱幅まで
     * 育った瞬間に原点が押し出され、付箋の左辺が図の左端まで走った。マウスを戻しても
     * 復元しない。リサイズで許される上限は「箱の幅」ではなく<b>原点から端までの残り</b>
     * である。ラウンド 21 の修正が作り込んだ退行なので、ここで固定する。</p>
     */
    @Test
    public void resizingNeverMovesTheOrigin() {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB));
        DiagramNotesLayer layer = panel.notes();
        layer.addNoteAt(new Point(400, 300), 1.0);
        DiagramNote n = layer.getNotes().get(0);

        int hx = (int) Math.round(n.getX() + n.getWidth()) - 2;
        int hy = (int) Math.round(n.getY() + n.getHeight()) - 2;
        assertTrue("リサイズ操作が始まること",
                layer.pressed(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 1L, 0,
                        hx, hy, 1, false, MouseEvent.BUTTON1), 1.0));
        layer.dragged(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 1L, 0,
                1100, 900, 0, false, MouseEvent.BUTTON1), 1.0);
        layer.released();

        DiagramNote after = layer.getNotes().get(0);
        assertTrue("原点が動かないこと: x=" + after.getX() + " y=" + after.getY(),
                after.getX() == 400 && after.getY() == 300);
        assertFits(panel, "リサイズ");
    }

    /**
     * 図が描き直されて内容矩形が変わったら、付箋の不変条件を<b>張り直す</b>こと。
     *
     * <p>不変条件は付箋を動かす経路にしか適用されていなかった。箱の側が変わる経路
     * (F5・テーマ変更・深度やフィルタの変更・ソース編集) は付箋に触れないので、図が縮むと
     * 付箋は内容矩形の外に取り残される — <b>画面には見えているのに書き出しに出ない</b>。
     * しかもその状態で矢印キーを 1 回押すと、放置されたクランプが遅れて効いて突然ワープした。</p>
     */
    @Test
    public void shrinkingTheDiagramRefitsExistingNotes() {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(1200, 900, BufferedImage.TYPE_INT_ARGB));
        panel.notes().addNoteAt(new Point(900, 700), 1.0);

        // 同じタブで図を描き直す (フィルタ変更などで内容矩形が小さくなる)。
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));

        assertFits(panel, "図の縮小後");
    }

    /**
     * 要素に貼った付箋も、<b>書き出し時に</b>範囲へ収めること。
     *
     * <p>要素アンカーの既定配置は「要素の右隣 (要素幅 + 16)」である。図の内容矩形は
     * 要素に密着しているので、この既定位置は<b>ほぼ必ず</b>図の右外に出る。書き出しは
     * アンカーを解決して FREE に直すのに、そこだけ不変条件を通していなかったため、
     * 要素に貼った付箋は本文ごと切り落とされていた。</p>
     */
    @Test
    public void elementAnchoredNotesAreFittedWhenExported() {
        SvgPreviewPanel panel = new SvgPreviewPanel();
        panel.setImage(new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB));
        DiagramNotesLayer layer = panel.notes();
        layer.addNoteAt(new Point(10, 20), 1.0);
        DiagramNote n = layer.getNotes().get(0);
        n.setAnchor(DiagramNote.Anchor.ELEMENT);
        n.setX(280);
        n.setY(180);

        for (DiagramNote e : layer.notesForExportResolved()) {
            assertTrue("書き出し時は FREE へ解決すること", e.getAnchor() == DiagramNote.Anchor.FREE);
            assertTrue("右端が範囲内 right=" + (e.getX() + e.getWidth())
                            + " limit=" + panel.contentWidth(),
                    e.getX() >= 0 && e.getX() + e.getWidth() <= panel.contentWidth() + 0.5);
            assertTrue("下端が範囲内 bottom=" + (e.getY() + e.getHeight())
                            + " limit=" + panel.contentHeight(),
                    e.getY() >= 0 && e.getY() + e.getHeight() <= panel.contentHeight() + 0.5);
        }
    }

    /** 付箋の矩形全体が図の内容矩形に収まっていること。 */
    private static void assertFits(SvgPreviewPanel panel, String label) {
        for (DiagramNote n : panel.notes().getNotes()) {
            assertTrue(label + ": 右端が範囲内 right=" + (n.getX() + n.getWidth())
                            + " limit=" + panel.contentWidth(),
                    n.getX() >= 0 && n.getX() + n.getWidth() <= panel.contentWidth() + 0.5);
            assertTrue(label + ": 下端が範囲内 bottom=" + (n.getY() + n.getHeight())
                            + " limit=" + panel.contentHeight(),
                    n.getY() >= 0 && n.getY() + n.getHeight() <= panel.contentHeight() + 0.5);
        }
    }
}
