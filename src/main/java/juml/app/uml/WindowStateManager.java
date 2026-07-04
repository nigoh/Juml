// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.Setting;

import javax.swing.JFrame;
import javax.swing.JSplitPane;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * ウィンドウの位置・サイズ・サイドバー分割位置の保存/復元を担うヘルパ。
 *
 * <p>{@link Setting} にスキーマ ({@code windowX/Y/Width/Height} ・
 * {@code mainSplitLocation}) は揃っているので、それを実際に読み書きする。</p>
 */
final class WindowStateManager {

    private WindowStateManager() {
    }

    /**
     * pack 済みフレームに、保存済みのウィンドウ位置とサイドバー分割位置を復元する。
     * 位置が未保存なら画面中央に配置する。
     */
    static void restoreLocationAndDivider(JFrame frame, JSplitPane split, Setting setting) {
        // 「未保存」は専用フラグで判定する。以前は座標 >= 0 で判定していたため、プライマリより
        // 左/上のモニタ (座標が負) に置いた位置が復元されず毎回中央へ戻っていた。保存済みなら
        // 利用可能スクリーンと交差するようにクランプし、切断済みモニタ座標での画面外復元も防ぐ。
        if (setting.isWindowLocationSaved()) {
            Point p = clampToScreens(setting.getWindowX(), setting.getWindowY(),
                    frame.getWidth(), frame.getHeight(), availableScreenBounds(),
                    primaryScreenBounds());
            frame.setLocation(p.x, p.y);
        } else {
            frame.setLocationRelativeTo(null);
        }
        // 0 は「サイドバーを畳んだ」正当な分割位置。未保存の sentinel は -1 なので、
        // >= 0 で判定する (> 0 だと畳んだ状態が復元されず毎回開いた状態に戻ってしまう)。
        if (split != null && setting.getMainSplitLocation() >= 0) {
            split.setDividerLocation(setting.getMainSplitLocation());
        }
        if (setting.isWindowMaximized()) {
            frame.setExtendedState(frame.getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
    }

    /** 現在のウィンドウ位置/サイズ/分割位置を {@link Setting} へ保存する (ベストエフォート)。 */
    static void save(JFrame frame, JSplitPane split, Setting setting, Runnable persist) {
        try {
            boolean maximized =
                    (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            setting.setWindowMaximized(maximized);
            // 最大化中は getWidth/Height が画面全体になるため、通常時の bounds を上書きしない。
            if (!maximized) {
                setting.setWindowX(frame.getX());
                setting.setWindowY(frame.getY());
                setting.setWindowWidth(frame.getWidth());
                setting.setWindowHeight(frame.getHeight());
                setting.setWindowLocationSaved(true);
            }
            if (split != null) {
                setting.setMainSplitLocation(split.getDividerLocation());
            }
            persist.run();
        } catch (RuntimeException ignored) {
            // 設定保存はベストエフォート (失敗してもアプリ終了を妨げない)
        }
    }

    /**
     * 復元したい {@code (x, y)} を、利用可能スクリーンのいずれかと「掴める程度」に交差する
     * 位置へクランプする (純関数, テスト可能)。どの画面とも十分交差しなければ (切断済みモニタの
     * 座標など) プライマリ内へ収める。十分交差していれば負座標でもそのまま採用する。
     *
     * @param screens 利用可能な各スクリーンの bounds (負原点もあり得る)
     * @param primary プライマリスクリーンの bounds (フォールバック先)
     */
    static Point clampToScreens(int x, int y, int w, int h,
            List<Rectangle> screens, Rectangle primary) {
        int width = Math.max(1, w);
        int height = Math.max(1, h);
        // タイトルバーを掴める最小交差量 (小さいウィンドウはその寸法まで緩める)。
        int needW = Math.min(width, 100);
        int needH = Math.min(height, 30);
        Rectangle win = new Rectangle(x, y, width, height);
        if (screens != null) {
            for (Rectangle sb : screens) {
                Rectangle inter = sb.intersection(win);
                if (inter.width >= needW && inter.height >= needH) {
                    return new Point(x, y);
                }
            }
        }
        if (primary == null) {
            return new Point(x, y);
        }
        int nx = Math.max(primary.x,
                Math.min(x, primary.x + primary.width - Math.min(width, primary.width)));
        int ny = Math.max(primary.y,
                Math.min(y, primary.y + primary.height - Math.min(height, primary.height)));
        return new Point(nx, ny);
    }

    /** 実環境の全スクリーン bounds (headless では空)。 */
    private static List<Rectangle> availableScreenBounds() {
        List<Rectangle> list = new ArrayList<>();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (ge.isHeadlessInstance()) {
            return list;
        }
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            if (gc != null) {
                list.add(gc.getBounds());
            }
        }
        return list;
    }

    /** プライマリスクリーンの bounds (headless では null)。 */
    private static Rectangle primaryScreenBounds() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        if (ge.isHeadlessInstance()) {
            return null;
        }
        GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
        return gc != null ? gc.getBounds() : null;
    }
}
