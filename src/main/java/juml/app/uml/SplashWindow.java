// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JWindow;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * アプリ起動直後に画面中央へ短時間だけ表示する、枠なしのブランド用スプラッシュ。
 *
 * <p>{@link LoadingGifView} を載せて起動オーバーレイと同じ GIF・中央レイアウトに
 * そろえる。juml の起動 UI 構築は軽いので、{@link UmlApp} 側で最低表示時間を
 * 確保してから {@link #close()} する。</p>
 */
final class SplashWindow extends JWindow {

    private SplashWindow() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(new Color(32, 32, 44)); // stock_controller のローダ背景に合わせた暗色
        content.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        content.add(new LoadingGifView("起動中..."), BorderLayout.CENTER);
        setContentPane(content);
        pack();
        setLocationRelativeTo(null); // 画面中央
    }

    /** スプラッシュを生成して表示する。EDT から呼ぶこと。 */
    static SplashWindow display() {
        SplashWindow w = new SplashWindow();
        w.setVisible(true);
        return w;
    }

    /** スプラッシュを閉じる。 */
    void close() {
        dispose();
    }
}
