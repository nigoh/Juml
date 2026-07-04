// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JWindow;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * アプリ起動直後に画面中央へ短時間だけ表示する、枠なしのブランド用スプラッシュ。
 *
 * <p>{@link JumlLoadingView} を載せ、ブランドロゴ ({@link JumlLogo}) の回転アニメーションと
 * ワードマーク "Juml" を表示する。juml の起動 UI 構築は軽いので、{@link UmlApp} 側で
 * 最低表示時間を確保してから {@link #close()} する。</p>
 */
final class SplashWindow extends JWindow {

    /** スプラッシュ背景の暗色。ロゴのブランド青が映えるよう深いネイビーにする。 */
    private static final Color BG = new Color(0x1E1E2A);

    private SplashWindow() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(BG);
        content.setBorder(BorderFactory.createEmptyBorder(28, 40, 28, 40));
        JumlLoadingView view = new JumlLoadingView(Messages.get("splash.loading"), 96, true);
        content.add(view, BorderLayout.CENTER);
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
