// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

/**
 * ツリーセル横 / タブヘッダに表示する小さなアイコン。
 *
 * <p><b>マテリアルデザイン (outlined) のグリフ + 文字バッジ</b>で種別を識別する:</p>
 * <ul>
 *   <li>Material グリフ ({@link MaterialIcons}) — 構造 (Module/Package)、図種
 *       (Sequence/Activity/Method)、Android (Manifest/コンポーネント/Permission/Feature)。
 *       カテゴリ色で着色し、ひと目で意味が伝わるようにする。</li>
 *   <li>文字バッジ (BADGE) — Java 型: C=Class / I=Interface / E=Enum /
 *       {@code @}=Annotation / A=AIDL。IDE 慣習に倣った角丸の色バッジ。</li>
 * </ul>
 *
 * <p>すべてベクター描画なので HiDPI でも輪郭が破綻しない。</p>
 */
public final class TreeNodeIcon implements Icon {

    private enum Shape { BADGE, GLYPH }

    // ── 構造ノード (Material グリフ) ─────────────────────────────
    /** モジュールノード: スチールブルーのモジュールグリフ */
    public static final TreeNodeIcon MODULE =
            glyph(MaterialIcons.Glyph.MODULE, new Color(0x546E7A), 15);
    /** パッケージノード: 茶色のパッケージ (箱) グリフ */
    public static final TreeNodeIcon PACKAGE =
            glyph(MaterialIcons.Glyph.PACKAGE, new Color(0x795548), 15);

    // ── Java 型ノード (BADGE: 色背景 + 白い 1 文字) ─────────────
    /** クラス: 青バッジ "C" */
    public static final TreeNodeIcon CLASS =
            badge(new Color(0x1565C0), "C", 14);
    /** インターフェース: 緑バッジ "I" */
    public static final TreeNodeIcon INTERFACE =
            badge(new Color(0x2E7D32), "I", 14);
    /** 列挙型: 紫バッジ "E" */
    public static final TreeNodeIcon ENUM =
            badge(new Color(0x6A1B9A), "E", 14);
    /** アノテーション: オレンジバッジ "@" */
    public static final TreeNodeIcon ANNOTATION =
            badge(new Color(0xE65100), "@", 14);
    /** AIDL インターフェース: ティールバッジ "A" */
    public static final TreeNodeIcon AIDL =
            badge(new Color(0x00695C), "A", 14);

    // ── メソッド / 図種ノード (Material グリフ) ─────────────────
    /** メソッド: 青灰色の関数グリフ */
    public static final TreeNodeIcon METHOD =
            glyph(MaterialIcons.Glyph.FUNCTION, new Color(0x607D8B), 14);
    /** シーケンス図: 赤いタイムライングリフ */
    public static final TreeNodeIcon SEQUENCE =
            glyph(MaterialIcons.Glyph.TIMELINE, new Color(0xE53935), 15);
    /** アクティビティ図: 青いフローチャートグリフ */
    public static final TreeNodeIcon ACTIVITY =
            glyph(MaterialIcons.Glyph.FLOWCHART, new Color(0x1E88E5), 15);

    // ── Android / Manifest (Material グリフ) ────────────────────
    /** AndroidManifest.xml ノード: 緑のドキュメントグリフ */
    public static final TreeNodeIcon MANIFEST =
            glyph(MaterialIcons.Glyph.MANIFEST, new Color(0x2E7D32), 15);
    /** コンポーネントグループ: 青灰色の widgets グリフ */
    public static final TreeNodeIcon COMPONENT_GROUP =
            glyph(MaterialIcons.Glyph.WIDGETS, new Color(0x546E7A), 14);
    /** Android Activity コンポーネント: オレンジの画面グリフ */
    public static final TreeNodeIcon COMPONENT_ACTIVITY =
            glyph(MaterialIcons.Glyph.CENTER_FOCUS, new Color(0xF57C00), 14);
    /** Android Service コンポーネント: インディゴの稲妻グリフ (バックグラウンド処理) */
    public static final TreeNodeIcon COMPONENT_SERVICE =
            glyph(MaterialIcons.Glyph.BOLT, new Color(0x3949AB), 14);
    /** Android BroadcastReceiver: 紫の hub グリフ (ブロードキャスト) */
    public static final TreeNodeIcon COMPONENT_RECEIVER =
            glyph(MaterialIcons.Glyph.HUB, new Color(0x7B1FA2), 14);
    /** Android ContentProvider: 緑の layers グリフ (データ層) */
    public static final TreeNodeIcon COMPONENT_PROVIDER =
            glyph(MaterialIcons.Glyph.LAYERS, new Color(0x388E3C), 14);
    /** uses-permission: 赤の盾グリフ */
    public static final TreeNodeIcon PERMISSION =
            glyph(MaterialIcons.Glyph.SHIELD, new Color(0xC62828), 14);
    /** uses-feature: 黄色の星グリフ */
    public static final TreeNodeIcon FEATURE =
            glyph(MaterialIcons.Glyph.STAR, new Color(0xF9A825), 14);

    // ─────────────────────────────────────────────────────────────

    private static final Color BADGE_TEXT = Color.WHITE;

    private final Shape shape;
    private final Color color;
    private final String letter;             // BADGE 専用
    private final MaterialIcons.Glyph glyph;  // GLYPH 専用
    private final int size;

    private TreeNodeIcon(Shape shape, Color color, String letter,
                         MaterialIcons.Glyph glyph, int size) {
        this.shape  = shape;
        this.color  = color;
        this.letter = letter;
        this.glyph  = glyph;
        this.size   = size;
    }

    private static TreeNodeIcon badge(Color color, String letter, int size) {
        return new TreeNodeIcon(Shape.BADGE, color, letter, null, size);
    }

    private static TreeNodeIcon glyph(MaterialIcons.Glyph glyph, Color color, int size) {
        return new TreeNodeIcon(Shape.GLYPH, color, null, glyph, size);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (shape == Shape.GLYPH) {
                MaterialIcons.of(glyph, size, color).paintIcon(c, g2, x, y);
            } else {
                paintBadge(g2, x, y);
            }
        } finally {
            g2.dispose();
        }
    }

    /** 角丸の色バッジ + 中央の白い 1 文字 (Java 型用)。 */
    private void paintBadge(Graphics2D g2, int x, int y) {
        g2.setColor(color);
        g2.fill(new RoundRectangle2D.Float(x, y, size, size, 5f, 5f));
        if (letter != null) {
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, size - 4);
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int tx = x + (size - fm.stringWidth(letter)) / 2;
            int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.setColor(BADGE_TEXT);
            g2.drawString(letter, tx, ty);
        }
    }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }
}
