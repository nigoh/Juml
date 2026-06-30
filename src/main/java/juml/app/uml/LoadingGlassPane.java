// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.net.URL;

/**
 * プロジェクト解析中に {@link UmlMainFrame} の glass pane として全面に被せる
 * ローディングオーバーレイ。
 *
 * <p>stock_controller の {@code layer: overlay} 中央配置に相当する。半透明の暗幕を
 * 全面に描き、中央に動く GIF + ステータス文言を載せる。解析中は背後の UI を誤操作
 * させないよう、マウス/キーイベントを自身で消費する。</p>
 *
 * <p>GIF は子コンポーネント (JLabel) ではなく {@code paintComponent} 内で直接描画する。
 * アニメ GIF を子ラベルで再生すると、フレーム更新時の部分再描画が非不透明 glass pane の
 * 暗幕を消してしまう Swing の挙動があるため。{@link ImageIcon#paintIcon} の
 * {@code ImageObserver} に自身を渡すことで、フレーム進行が glass pane の再描画を駆動し、
 * 暗幕とアニメを両立させる。</p>
 */
final class LoadingGlassPane extends JComponent {

    /** 背後 UI を覆う半透明の暗幕色。 */
    private static final Color SCRIM = new Color(0, 0, 0, 140);

    /** アニメ GIF。リソースが無ければ null (ステータスのみ表示)。 */
    private final ImageIcon icon;
    private final JButton cancelButton;
    private Runnable cancelAction;
    private String status = Messages.get("loading.initial");

    LoadingGlassPane() {
        setOpaque(false);
        setVisible(false);
        setLayout(null);
        URL gifUrl = LoadingGlassPane.class.getResource(LoadingGifs.pickResource());
        icon = gifUrl != null ? new ImageIcon(gifUrl) : null;
        cancelButton = new JButton(Messages.get("loading.cancel"));
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> {
            if (cancelAction != null) {
                cancelAction.run();
            }
        });
        add(cancelButton);
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });
        addKeyListener(new KeyAdapter() { });
    }

    /** ステータス文言を更新する (stock_controller の {@code update_status} 相当)。 */
    void setStatus(String message) {
        status = message != null ? message : "";
        repaint();
    }

    /** キャンセルアクションを設定する。非 null ならキャンセルボタンを表示する。 */
    void setCancelAction(Runnable action) {
        cancelAction = action;
        cancelButton.setVisible(action != null);
    }

    /** オーバーレイを表示する。フォーカスを奪い、解析中のキーボードショートカットを遮断する。 */
    void showOverlay() {
        setVisible(true);
        setFocusable(true);
        requestFocusInWindow();
    }

    /** オーバーレイを隠す。 */
    void hideOverlay() {
        cancelAction = null;
        cancelButton.setVisible(false);
        setFocusable(false);
        setVisible(false);
    }

    /**
     * GIF のフレーム進行ごとに glass pane 全体を再描画する。
     *
     * <p>既定の {@link java.awt.Component#imageUpdate} は画像の矩形だけを再描画するため、
     * 部分再描画では暗幕 (scrim) が画像領域の外で更新されず消えて見える。全体を repaint
     * することで暗幕とアニメ GIF を確実に両立させる。</p>
     */
    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int w, int h) {
        if (isShowing()) {
            repaint();
        }
        return isVisible();
    }

    @Override
    public void doLayout() {
        if (cancelButton.isVisible()) {
            Dimension pref = cancelButton.getPreferredSize();
            int bx = (getWidth() - pref.width) / 2;
            int by = getHeight() / 2 + 60;
            cancelButton.setBounds(bx, by, pref.width, pref.height);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(SCRIM);
        g.fillRect(0, 0, w, h);

        int textBaselineY = h / 2;
        if (icon != null) {
            int iw = icon.getIconWidth();
            int ih = icon.getIconHeight();
            int ix = (w - iw) / 2;
            int iy = h / 2 - ih / 2 - 16;
            // observer=this: GIF のフレーム進行が glass pane の再描画を駆動する。
            icon.paintIcon(this, g, ix, iy);
            textBaselineY = iy + ih + 24;
        }

        g.setColor(Color.WHITE);
        g.setFont(getFont().deriveFont(Font.BOLD, 14f));
        FontMetrics fm = g.getFontMetrics();
        int sw = fm.stringWidth(status);
        g.drawString(status, (w - sw) / 2, textBaselineY + fm.getAscent());
    }
}
