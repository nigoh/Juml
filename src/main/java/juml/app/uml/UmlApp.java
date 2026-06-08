// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.io.File;

/**
 * UML 専用 GUI のエントリポイント。
 *
 * <p>{@link juml.Main} の既定 (引数なし) 起動経路、もしくは
 * {@code --ui=uml} 隠しフラグ経由から呼び出される。EDT で
 * {@link UmlMainFrame} を生成して表示する。</p>
 */
public final class UmlApp {

    private UmlApp() {
    }

    /** UML GUI を起動する。{@code initialProject} は null 可。 */
    public static void launch(File initialProject) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        // メニュー等を構築する前に、永続化された言語をメッセージリソースへ反映する。
        juml.util.Messages.setLanguage(resolveLanguageKey());
        PreferencesDialog.applyLookAndFeel(resolveLookAndFeelKey());
        launchWithSplash(initialProject);
    }

    /** 永続化された Look &amp; Feel キーを取得する。未初期化等では "SYSTEM"。 */
    private static String resolveLookAndFeelKey() {
        try {
            return juml.SettingManager.getInstance().getSetting().getLookAndFeel();
        } catch (RuntimeException ex) {
            // SettingManager 未初期化 (テスト等) では既定 (System) を使う
            return "SYSTEM";
        }
    }

    /** 永続化された UI 言語キーを取得する。未初期化等では既定 (日本語) を使う。 */
    private static String resolveLanguageKey() {
        try {
            return juml.SettingManager.getInstance().getSetting().getLanguage();
        } catch (RuntimeException ex) {
            return "ja";
        }
    }

    /** 起動スプラッシュ (GIF) を中央表示しつつメインウィンドウを起動する。 */
    private static void launchWithSplash(File initialProject) {
        // 起動直後に一瞬で消えないよう、スプラッシュの最低表示時間を確保する。
        final int minSplashMillis = 900;
        SwingUtilities.invokeLater(() -> {
            SplashWindow splash = SplashWindow.display();
            UmlMainFrame frame = new UmlMainFrame(initialProject);
            frame.setVisible(true);
            Timer timer = new Timer(minSplashMillis, e -> splash.close());
            timer.setRepeats(false);
            timer.start();
        });
    }
}
