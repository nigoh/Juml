// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;

/**
 * ツールコンボ (関係 / メッセージのモード選択) 用の矢印プレビューアイコン。
 *
 * <p>PlantUML の矢印表記 ({@code <|--} / {@code o--} / {@code ->} など) を文字で読ま
 * なくても形で選べるよう、各モードの線種・矢頭を小さな Java2D グリフとして描く。
 * 既存のアイコン体系 (juml.app.uml.MaterialIcons) と同じく外部アセット非依存で、描画色は
 * {@link UIManager} の前景色から実行時に解決してテーマ切替へ追従する。</p>
 */
final class SketchToolIcon implements Icon {

    /** アイコンが表すツールの形。 */
    enum Shape {
        /** 選択/移動 (マウスポインタ)。 */
        SELECT,
        /** 継承 {@code <|--}: 実線 + 左に白三角。 */
        REL_EXTENDS,
        /** 実現 {@code <|..}: 破線 + 左に白三角。 */
        REL_IMPLEMENTS,
        /** 関連 {@code -->}: 実線 + 右に開き矢印。 */
        REL_ASSOCIATION,
        /** リンク {@code --}: 実線のみ。 */
        REL_LINK,
        /** 集約 {@code o--}: 実線 + 左に白ひし形。 */
        REL_AGGREGATION,
        /** コンポジション {@code *--}: 実線 + 左に黒ひし形。 */
        REL_COMPOSITION,
        /** 依存 {@code ..>}: 破線 + 右に開き矢印。 */
        REL_DEPENDENCY,
        /** 同期メッセージ {@code ->}: 実線 + 右に塗り三角。 */
        MSG_SYNC,
        /** 非同期メッセージ {@code ->>}: 実線 + 右に開き矢印。 */
        MSG_ASYNC,
        /** 応答メッセージ {@code -->}: 破線 + 右に開き矢印。 */
        MSG_REPLY
    }

    private static final int W = 36;
    private static final int H = 16;

    private final Shape shape;

    private SketchToolIcon(Shape shape) {
        this.shape = shape;
    }

    /** クラス図の関係モード用アイコン (null は選択/移動)。 */
    static Icon forRelation(SketchRelation.Kind kind) {
        if (kind == null) {
            return new SketchToolIcon(Shape.SELECT);
        }
        switch (kind) {
            case EXTENDS:      return new SketchToolIcon(Shape.REL_EXTENDS);
            case IMPLEMENTS:   return new SketchToolIcon(Shape.REL_IMPLEMENTS);
            case ASSOCIATION:  return new SketchToolIcon(Shape.REL_ASSOCIATION);
            case LINK:         return new SketchToolIcon(Shape.REL_LINK);
            case AGGREGATION:  return new SketchToolIcon(Shape.REL_AGGREGATION);
            case COMPOSITION:  return new SketchToolIcon(Shape.REL_COMPOSITION);
            default:           return new SketchToolIcon(Shape.REL_DEPENDENCY);
        }
    }

    /** シーケンス図のメッセージモード用アイコン (null は選択/移動)。 */
    static Icon forMessage(SeqItem.Arrow arrow) {
        if (arrow == null) {
            return new SketchToolIcon(Shape.SELECT);
        }
        switch (arrow) {
            case SYNC:  return new SketchToolIcon(Shape.MSG_SYNC);
            case ASYNC: return new SketchToolIcon(Shape.MSG_ASYNC);
            default:    return new SketchToolIcon(Shape.MSG_REPLY);
        }
    }

    /**
     * コンボの各項目 (と選択欄) にアイコンを表示するレンダラーを取り付ける。
     * {@code icons} はコンボのモデル順と同じ並びで渡す。
     */
    static void install(JComboBox<String> combo, Icon[] icons) {
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                // index < 0 はコンボの選択欄表示。モデルから項目位置を引き直す。
                int i = index >= 0 ? index : indexOf(combo, value);
                if (i >= 0 && i < icons.length) {
                    label.setIcon(icons[i]);
                    label.setIconTextGap(8);
                }
                return label;
            }
        });
    }

    private static int indexOf(JComboBox<String> combo, Object value) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equals(value)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getIconWidth() {
        return W;
    }

    @Override
    public int getIconHeight() {
        return H;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            Color color = UIManager.getColor("Label.foreground");
            g2.setColor(color != null ? color : new Color(0x37474F));
            if (shape == Shape.SELECT) {
                paintPointer(g2);
                return;
            }
            int midY = H / 2;
            // 矢頭ぶんだけ線の端を空ける。
            int x1 = hasLeftHead() ? 12 : 2;
            int x2 = hasRightHead() ? W - 12 : W - 2;
            Stroke old = g2.getStroke();
            g2.setStroke(dashed()
                    ? new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            10f, new float[]{3.5f, 3f}, 0f)
                    : new BasicStroke(1.4f));
            g2.drawLine(x1, midY, x2, midY);
            g2.setStroke(old);
            paintHead(g2, midY);
        } finally {
            g2.dispose();
        }
    }

    private boolean dashed() {
        return shape == Shape.REL_IMPLEMENTS || shape == Shape.REL_DEPENDENCY
                || shape == Shape.MSG_REPLY;
    }

    private boolean hasLeftHead() {
        return shape == Shape.REL_EXTENDS || shape == Shape.REL_IMPLEMENTS
                || shape == Shape.REL_AGGREGATION || shape == Shape.REL_COMPOSITION;
    }

    private boolean hasRightHead() {
        return shape == Shape.REL_ASSOCIATION || shape == Shape.REL_DEPENDENCY
                || shape == Shape.MSG_SYNC || shape == Shape.MSG_ASYNC
                || shape == Shape.MSG_REPLY;
    }

    private void paintHead(Graphics2D g2, int midY) {
        switch (shape) {
            case REL_EXTENDS:
            case REL_IMPLEMENTS:
                paintTriangle(g2, midY, 2, true, false);
                break;
            case REL_AGGREGATION:
                paintDiamond(g2, midY, false);
                break;
            case REL_COMPOSITION:
                paintDiamond(g2, midY, true);
                break;
            case MSG_SYNC:
                paintTriangle(g2, midY, W - 2, false, true);
                break;
            case REL_ASSOCIATION:
            case REL_DEPENDENCY:
            case MSG_ASYNC:
            case MSG_REPLY:
                paintOpenArrow(g2, midY);
                break;
            default:
                break;
        }
    }

    /** {@code tipX} を先端とする三角矢頭 (left=true なら左向き)。白抜き or 塗り。 */
    private void paintTriangle(Graphics2D g2, int midY, int tipX,
                               boolean pointsLeft, boolean filled) {
        int dir = pointsLeft ? 1 : -1;
        Path2D p = new Path2D.Double();
        p.moveTo(tipX, midY);
        p.lineTo(tipX + dir * 10, midY - 5);
        p.lineTo(tipX + dir * 10, midY + 5);
        p.closePath();
        Color line = g2.getColor();
        if (!filled) {
            Color bg = UIManager.getColor("Label.background");
            g2.setColor(bg != null ? bg : Color.WHITE);
            g2.fill(p);
            g2.setColor(line);
            g2.draw(p);
        } else {
            g2.fill(p);
        }
    }

    /** 左端のひし形 (集約=白抜き / コンポジション=塗り)。 */
    private void paintDiamond(Graphics2D g2, int midY, boolean filled) {
        Path2D p = new Path2D.Double();
        p.moveTo(2, midY);
        p.lineTo(7, midY - 4);
        p.lineTo(12, midY);
        p.lineTo(7, midY + 4);
        p.closePath();
        Color line = g2.getColor();
        if (!filled) {
            Color bg = UIManager.getColor("Label.background");
            g2.setColor(bg != null ? bg : Color.WHITE);
            g2.fill(p);
            g2.setColor(line);
        }
        if (filled) {
            g2.fill(p);
        } else {
            g2.draw(p);
        }
    }

    /** 右端の開き矢印 (V 字)。 */
    private void paintOpenArrow(Graphics2D g2, int midY) {
        int tipX = W - 2;
        g2.setStroke(new BasicStroke(1.4f));
        g2.drawLine(tipX, midY, tipX - 9, midY - 5);
        g2.drawLine(tipX, midY, tipX - 9, midY + 5);
    }

    /** 選択/移動モードのマウスポインタ。 */
    private void paintPointer(Graphics2D g2) {
        int px = W / 2 - 5;
        Path2D p = new Path2D.Double();
        p.moveTo(px, 1);
        p.lineTo(px, 12);
        p.lineTo(px + 3, 9.5);
        p.lineTo(px + 5.4, 14);
        p.lineTo(px + 7.2, 13);
        p.lineTo(px + 4.8, 8.7);
        p.lineTo(px + 8.5, 8.3);
        p.closePath();
        g2.fill(p);
    }
}
