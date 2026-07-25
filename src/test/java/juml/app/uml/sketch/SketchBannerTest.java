// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.swing.JPanel;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link SketchBanner#paint} の truncation 分岐 (未対応行が {@code MAX_BANNER_LINES}
 * (現状 6) を超えたら先頭 K 行 + 「... 他 M 行」にまとめる) を検証する
 * (GUI テスト監査で残った Low: truncation 分岐が未検証だった項目の解消)。
 *
 * <p>{@code paint} は {@code Graphics2D} へ直接描画するだけで文字列を返さない設計であり、
 * 文字列を組み立てる package-private ヘルパーも存在しない。「... 他 N 行」というテキスト
 * そのものを厳密に assert するには {@code Graphics2D} の全抽象メソッド (Graphics/Graphics2D
 * 合わせて 70 個超) を委譲実装したレコーディング用サブクラスが必要になり、テストの複雑さに
 * 見合わないため採用しない。代わりに {@link BufferedImage} へ実際に描画し、
 * <ul>
 *   <li>バナー帯 (fillRect で塗る赤帯) のピクセル高さから、行数が何行分描画されたか
 *       (ヘッダ 1 行 + 表示行 + truncation 行の有無) を逆算する。</li>
 *   <li>{@code n-1} 行版と {@code n} 行版の帯高さの差分 = 「{@code n} 行目に対応する
 *       画面上の帯域」を求め、そこに白インク (テキスト) が実際に描かれているかを判定する。</li>
 * </ul>
 * という手法で、lineH/pad などの内部定数 (paint 内のローカル変数でリフレクション不可) を
 * 一切ハードコードせずに、(a) 上限以下では全行が個別に描画され truncation 行が無いこと、
 * (b) 上限を 1 行超えると truncation 用の行が 1 行だけ追加されテキストが描かれること、
 * (c) 超過分がどれだけ増えても (7 行でも 26 行でも) 帯の高さが変わらない
 * (= 常に「... 他 N 行」1 行にまとまる) ことを確認する。</p>
 */
public class SketchBannerTest {

    @Before
    public void requireDisplay() {
        // JPanel (Swing コンポーネント) の生成・setFont を伴うため、ヘッドレス環境では
        // 生成が失敗し得る他の *LockedBannerZoomTest と同様にスキップする。
        Assume.assumeFalse("ヘッドレス環境では Swing コンポーネント生成が失敗するためスキップ",
                GraphicsEnvironment.isHeadless());
    }

    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 320;

    /** {@code SketchBanner} の帯色 (RGB, アルファ抜き)。 */
    private static final int BANNER_RGB = 0xB71C1C;

    /** バナー描画対象の最小限の {@link JPanel} を EDT 上で生成する。 */
    private static JPanel newTarget() {
        return GuiActionRunner.execute(() -> {
            JPanel p = new JPanel();
            p.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            p.setSize(CANVAS_WIDTH, CANVAS_HEIGHT);
            return p;
        });
    }

    /** {@code "unsupported line 0" .. "unsupported line (n-1)"} を持つリストを作る。 */
    private static List<String> lines(int n) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add("unsupported line " + i);
        }
        return list;
    }

    /** {@code unsupported} を {@link SketchBanner#paint} で実際に描いた結果を返す。 */
    private static BufferedImage paint(List<String> unsupported) {
        JPanel target = newTarget();
        BufferedImage img = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        GuiActionRunner.execute(() -> {
            Graphics2D g2 = img.createGraphics();
            try {
                SketchBanner.paint(g2, target, unsupported);
            } finally {
                g2.dispose();
            }
        });
        return img;
    }

    /**
     * 行 {@code y} のうちバナー色の画素が占める割合。文字のアンチエイリアスが混ざっても
     * 帯自体が塗られていれば十分高い値になり、帯の外 (未描画領域) では 0 になる。
     */
    private static double bannerRowCoverage(BufferedImage img, int y) {
        int count = 0;
        for (int x = 0; x < CANVAS_WIDTH; x++) {
            int rgb = img.getRGB(x, y) & 0x00FFFFFF;
            if (rgb == BANNER_RGB) {
                count++;
            }
        }
        return count / (double) CANVAS_WIDTH;
    }

    /** バナー帯 (fillRect) のピクセル高さ ({@code paint} 内部の {@code bannerH} 相当)。 */
    private static int bannerPixelHeight(BufferedImage img) {
        int y = 0;
        while (y < img.getHeight() && bannerRowCoverage(img, y) >= 0.3) {
            y++;
        }
        return y;
    }

    /** {@code [yFrom, yTo)} の帯域に白インク (テキストの芯) が存在するかどうか。 */
    private static boolean hasWhiteInk(BufferedImage img, int yFrom, int yTo) {
        for (int y = Math.max(0, yFrom); y < Math.min(img.getHeight(), yTo); y++) {
            for (int x = 16; x < CANVAS_WIDTH - 8; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > 200 && g > 200 && b > 200) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int maxBannerLines() throws ReflectiveOperationException {
        Field f = SketchBanner.class.getDeclaredField("MAX_BANNER_LINES");
        f.setAccessible(true);
        return f.getInt(null);
    }

    /** 境界: 未対応行 0 件でも例外を投げず、ヘッダ行 1 行分の帯は描かれる。 */
    @Test
    public void paint_zeroUnsupportedLines_doesNotThrowAndPaintsHeaderOnly() {
        BufferedImage img = paint(Collections.emptyList());
        assertTrue("未対応行 0 件でもヘッダ行 1 行分の帯は描かれるはず",
                bannerPixelHeight(img) > 0);
    }

    /** 上限未満では truncation が起きず、1 行増えるごとに帯高さが一定量ずつ伸びる。 */
    @Test
    public void paint_belowLimit_heightGrowsByOneLinePerLine() {
        int h1 = bannerPixelHeight(paint(lines(1)));
        int h2 = bannerPixelHeight(paint(lines(2)));
        int h3 = bannerPixelHeight(paint(lines(3)));
        int step1 = h2 - h1;
        int step2 = h3 - h2;
        assertTrue("1 行増えるごとに帯は一定量だけ高くなるはず (実測 step1=" + step1 + ")",
                step1 > 0);
        assertEquals("上限未満なら truncation 行が混入しないので増分は常に一定のはず",
                step1, step2);
    }

    /**
     * ケース 1: 未対応行が上限ちょうど (MAX_BANNER_LINES) のとき、全行が個別に
     * 描画対象になり truncation 行は追加されない。上限ちょうどの最後の行にも
     * 実際にテキストが描かれていること (静かに欠落していないこと) も確認する。
     */
    @Test
    public void paint_atLimit_allLinesRenderedIndividually_noTruncationRow()
            throws ReflectiveOperationException {
        int max = maxBannerLines();
        int hBelow = bannerPixelHeight(paint(lines(max - 1)));
        int hAt = bannerPixelHeight(paint(lines(max)));
        assertTrue("上限ちょうどの最後の行の分だけ帯は伸びるはず", hAt > hBelow);

        // 上限ちょうどの「最後の行」に対応する帯域 [hBelow, hAt) に実際に文字があること。
        assertTrue("上限ちょうど (" + max + " 行) の最後の行も truncation されず描画されるはず",
                hasWhiteInk(paint(lines(max)), hBelow, hAt));
    }

    /**
     * ケース 2: 未対応行が上限を 1 行超えると、先頭 K 行 (K = MAX_BANNER_LINES) + 「... 他 M 行」
     * の truncation 行がちょうど 1 行だけ追加され、そこに実際にテキストが描かれる。
     */
    @Test
    public void paint_overLimit_addsSingleTruncationRowWithText()
            throws ReflectiveOperationException {
        int max = maxBannerLines();
        int hAt = bannerPixelHeight(paint(lines(max)));
        int hOver = bannerPixelHeight(paint(lines(max + 1)));
        assertTrue("上限を 1 行超えると truncation 用の行が追加されるはず", hOver > hAt);

        int belowStep = bannerPixelHeight(paint(lines(2))) - bannerPixelHeight(paint(lines(1)));
        assertEquals("追加される truncation 行はちょうど 1 行分のはず (元の行を個別列挙しない)",
                belowStep, hOver - hAt);

        assertTrue("truncation 行の帯域に「... 他 N 行」の文字が描かれているはず",
                hasWhiteInk(paint(lines(max + 1)), hAt, hOver));
    }

    /**
     * ケース 2 の境界: 超過分がどれだけ増えても (上限+1 でも上限+20 でも) truncation は
     * 常に 1 行にまとまるため、帯の高さは変わらない。
     */
    @Test
    public void paint_farOverLimit_collapsesToSameHeightAsJustOverLimit()
            throws ReflectiveOperationException {
        int max = maxBannerLines();
        int hJustOver = bannerPixelHeight(paint(lines(max + 1)));
        int hFarOver = bannerPixelHeight(paint(lines(max + 20)));
        assertEquals("未対応行が " + (max + 1) + " 行でも " + (max + 20)
                + " 行でも、表示は先頭 " + max + " 行 + 「他 N 行」の 1 行にまとまるため"
                + "帯の高さは変わらないはず", hJustOver, hFarOver);
    }
}
