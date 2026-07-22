// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.gui.recording;

import juml.SettingManager;
import juml.app.uml.PumlTemplate;
import juml.app.uml.UmlMainFrame;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.FrameFixture;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;

/**
 * アクセシビリティ・操作性レビュー用の PNG スクリーンショットを、実際に GUI を起動して
 * 採取する実装確認テスト（合否ではなく「実画面を目で確認する」ための記録が目的）。
 *
 * <p>Xvfb 上で実行すること:
 * <pre>
 *   xvfb-run -a -s "-screen 0 1400x950x24" \
 *     gradle test --tests 'juml.gui.recording.AccessibilityShotIT'
 * </pre>
 * 生成物は {@code build/screenshots/*.png}。
 */
public class AccessibilityShotIT {

    // 出力先はビルドディレクトリ相対で解決する (CI のチェックアウト先でも書ける)。
    private static final String OUT_DIR =
            System.getProperty("user.dir") + "/build/screenshots/";
    private FrameFixture window;
    private Robot robot;

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse("headless 環境ではスクショ採取不可（xvfb-run でラップしてください）",
                GraphicsEnvironment.isHeadless());
        try {
            SettingManager.getInstance();
        } catch (RuntimeException ex) {
            SettingManager.initialize();
        }
        robot = new Robot();
        new File(OUT_DIR).mkdirs();
    }

    @After
    public void tearDown() {
        if (window != null) {
            window.cleanUp();
        }
        SettingManager.resetForTest();
    }

    /** 一連の主要画面をライト/ダークで採取する（1 プロセスで全状態を撮る）。 */
    @Test
    public void captureKeyScreens() throws Exception {
        UmlMainFrame frame = startFrame(1400, 950);
        pause(800);

        // 1. ウェルカム画面 (プロジェクト未ロード・ライト)
        shot("01-welcome-light");

        // 2. PlantUML エディタタブ (テンプレート初期表示 + プレビュー)
        invokeOpenPumlEditorTab(frame, PumlTemplate.CLASS.body(), null);
        pause(3800); // 初回描画完了待ち
        shot("02-editor-light");

        // 3. 入力補完ポップアップ（打ちかけ語で自動表示）
        Object sourcePanel = getSourcePanelOfActiveTab(frame);
        setText(sourcePanel, "@startuml\nclass Alice\nclass Bob\ncla\n@enduml\n");
        setCaretAfterFirst(sourcePanel, "cla");
        focusEditor(sourcePanel);
        pause(300);
        showCompletion(sourcePanel);
        pause(700);
        shot("03-editor-completion-light");

        // 4. keep-last-good ライブプレビュー: 正常描画後に不正構文を入れると
        //    直前の図が残り、上端に失敗バナーが出る。
        setText(sourcePanel, PumlTemplate.CLASS.body()); // 正常テキストへ戻す
        pause(2800); // 再描画（正常）
        String broken = PumlTemplate.CLASS.body().replace("@enduml", "class @@@ broken\n@enduml");
        setText(sourcePanel, broken);
        pause(2600); // デバウンス + 失敗描画 → バナー表示
        shot("04-editor-error-banner-light");

        // 5. 図形デザイナー (Design サブタブ)
        setText(sourcePanel, PumlTemplate.CLASS.body());
        pause(2600);
        JTabbedPane bottomTabs = getBottomTabsOfActiveTab(frame);
        if (bottomTabs != null) {
            switchToDesignSubTab(bottomTabs);
            pause(1200);
            shot("05-designer-light");
        }

        // 6. ダークテーマへ切り替えて主要画面を撮り直す
        applyDark(frame);
        pause(1000);
        shot("06-designer-dark");
        if (bottomTabs != null) {
            selectBottomTab(bottomTabs, 0); // PlantUML テキストへ戻す
            pause(600);
        }
        // ダークでの補完ポップアップ
        setText(sourcePanel, "@startuml\nparticipant Server\npar\n@enduml\n");
        setCaretAfterFirst(sourcePanel, "par");
        focusEditor(sourcePanel);
        pause(300);
        showCompletion(sourcePanel);
        pause(700);
        shot("07-editor-completion-dark");
        // ダークでのエラーバナー
        hideCompletion(sourcePanel);
        setText(sourcePanel, PumlTemplate.CLASS.body());
        pause(2600);
        setText(sourcePanel, broken);
        pause(2600);
        shot("08-editor-error-banner-dark");

        System.out.println("[AccessibilityShot] screenshots written under " + OUT_DIR);
    }

    // ------------------------------------------------------------------
    // スクショ
    // ------------------------------------------------------------------

    private void shot(String name) {
        // JWindow ポップアップやダイアログも含めるため画面全体を採取する。
        // これは合否判定ではなく目視レビュー用の画像採取ハーネスなので、書き出しに
        // 失敗しても (CI で書き込み不可・エンコーダ差異など) テストは落とさない
        // (既存の録画 IT と同じ非致命方針)。
        try {
            java.awt.Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            BufferedImage img = robot.createScreenCapture(new Rectangle(0, 0, d.width, d.height));
            File out = new File(OUT_DIR + name + ".png");
            if (ImageIO.write(img, "png", out)) {
                System.out.println("[AccessibilityShot] " + out.getName() + " "
                        + out.length() + "B");
            }
        } catch (Exception ex) {
            System.err.println("[AccessibilityShot] 書き出し失敗 " + name + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // ヘルパー
    // ------------------------------------------------------------------

    private UmlMainFrame startFrame(int w, int h) {
        UmlMainFrame frame = GuiActionRunner.execute(() -> new UmlMainFrame(null));
        window = new FrameFixture(frame);
        window.show();
        GuiActionRunner.execute(() -> {
            frame.setSize(w, h);
            frame.setLocationRelativeTo(null);
        });
        return frame;
    }

    private static void invokeOpenPumlEditorTab(UmlMainFrame frame, String text, File file) {
        GuiActionRunner.execute(() -> {
            try {
                Method m = frame.getClass().getDeclaredMethod(
                        "openPumlEditorTab", String.class, File.class);
                m.setAccessible(true);
                m.invoke(frame, text, file);
            } catch (Exception e) {
                System.err.println("[Shot] openPumlEditorTab: " + e.getMessage());
            }
        });
    }

    private static void applyDark(UmlMainFrame frame) {
        GuiActionRunner.execute(() -> {
            try {
                Class<?> pref = Class.forName("juml.app.uml.PreferencesDialog");
                Method m = pref.getMethod("applyLookAndFeelLive", String.class);
                m.invoke(null, "FLATLAF_DARK");
                javax.swing.SwingUtilities.updateComponentTreeUI(frame);
            } catch (Exception e) {
                System.err.println("[Shot] applyDark: " + e.getMessage());
            }
        });
    }

    private static Object getSourcePanelOfActiveTab(UmlMainFrame frame) {
        try {
            JTabbedPane mainTabs = getField(frame, "mainTabs");
            Component active = GuiActionRunner.execute(() -> mainTabs.getSelectedComponent());
            return active == null ? null : getField(active, "sourcePanel");
        } catch (ReflectiveOperationException e) {
            System.err.println("[Shot] getSourcePanel: " + e.getMessage());
            return null;
        }
    }

    private static JTabbedPane getBottomTabsOfActiveTab(UmlMainFrame frame) {
        try {
            JTabbedPane mainTabs = getField(frame, "mainTabs");
            Component active = GuiActionRunner.execute(() -> mainTabs.getSelectedComponent());
            return active == null ? null : getField(active, "bottomTabs");
        } catch (ReflectiveOperationException e) {
            System.err.println("[Shot] getBottomTabs: " + e.getMessage());
            return null;
        }
    }

    private static void switchToDesignSubTab(JTabbedPane bottomTabs) {
        GuiActionRunner.execute(() -> {
            for (int i = 0; i < bottomTabs.getTabCount(); i++) {
                String t = bottomTabs.getTitleAt(i);
                if (t != null && (t.contains("Design") || t.contains("デザ"))) {
                    bottomTabs.setSelectedIndex(i);
                    return;
                }
            }
            if (bottomTabs.getTabCount() > 1) {
                bottomTabs.setSelectedIndex(1);
            }
        });
    }

    private static void selectBottomTab(JTabbedPane bottomTabs, int idx) {
        GuiActionRunner.execute(() -> {
            if (idx < bottomTabs.getTabCount()) {
                bottomTabs.setSelectedIndex(idx);
            }
        });
    }

    private static void setText(Object sourcePanel, String text) {
        if (sourcePanel == null) {
            return;
        }
        GuiActionRunner.execute(() -> {
            try {
                Method m = sourcePanel.getClass().getMethod("setText", String.class);
                m.invoke(sourcePanel, text);
            } catch (Exception e) {
                System.err.println("[Shot] setText: " + e.getMessage());
            }
        });
    }

    private static void setCaretAfterFirst(Object sourcePanel, String token) {
        if (sourcePanel == null) {
            return;
        }
        GuiActionRunner.execute(() -> {
            try {
                Method getText = sourcePanel.getClass().getMethod("getText");
                String text = (String) getText.invoke(sourcePanel);
                int idx = text.indexOf(token);
                if (idx >= 0) {
                    Method m = sourcePanel.getClass().getDeclaredMethod(
                            "setCaretForTest", int.class);
                    m.setAccessible(true);
                    m.invoke(sourcePanel, idx + token.length());
                }
            } catch (Exception e) {
                System.err.println("[Shot] setCaret: " + e.getMessage());
            }
        });
    }

    private static void focusEditor(Object sourcePanel) {
        if (sourcePanel == null) {
            return;
        }
        GuiActionRunner.execute(() -> {
            try {
                sourcePanel.getClass().getMethod("focusEditor").invoke(sourcePanel);
            } catch (Exception e) {
                System.err.println("[Shot] focusEditor: " + e.getMessage());
            }
        });
    }

    private static void showCompletion(Object sourcePanel) {
        popupCall(sourcePanel, "showNow");
    }

    private static void hideCompletion(Object sourcePanel) {
        popupCall(sourcePanel, "hide");
    }

    private static void popupCall(Object sourcePanel, String method) {
        if (sourcePanel == null) {
            return;
        }
        GuiActionRunner.execute(() -> {
            try {
                Method pm = sourcePanel.getClass().getDeclaredMethod("completionPopupForTest");
                pm.setAccessible(true);
                Object popup = pm.invoke(sourcePanel);
                if (popup != null) {
                    Method mm = popup.getClass().getDeclaredMethod(method);
                    mm.setAccessible(true);
                    mm.invoke(popup);
                }
            } catch (Exception e) {
                System.err.println("[Shot] popup " + method + ": " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object obj, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field f = findField(obj.getClass(), name);
        f.setAccessible(true);
        return (T) f.get(obj);
    }

    private static java.lang.reflect.Field findField(Class<?> cls, String name)
            throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void pause(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
