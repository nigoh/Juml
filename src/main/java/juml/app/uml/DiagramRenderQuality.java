// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.awt.Component;
import java.awt.GraphicsConfiguration;
import java.awt.geom.AffineTransform;

/**
 * 図プレビューのラスタライズ解像度 (描画品質) を表すアプリ全体の設定。
 *
 * <p>{@link DiagramRenderCache} は巨大図を軽く扱うため、図本体を一旦バックバッファへ
 * ラスタライズしてブリットする。この際バッファを論理ピクセル解像度で作ると、HiDPI
 * (Retina / 4K / Windows 150% など) ディスプレイでは OS がさらに拡大するため図がボケる。
 * そこでバッファをここで決めた倍率で「スーパーサンプリング」し、描画時に縮小して貼ることで
 * デバイス解像度ぴったりの鮮明さを得る。</p>
 *
 * <p>{@link #AUTO} はディスプレイのデバイス倍率に自動追従するため、ほとんどの環境で
 * これが最適 (既定)。低スペック機向けに {@link #LOW}、印刷・拡大確認向けに {@link #HIGH} /
 * {@link #ULTRA} を選べる。値は {@link juml.Setting} に永続化され、起動時に復元される。</p>
 */
public enum DiagramRenderQuality {

    /** 自動 (推奨): ディスプレイのデバイス倍率に追従し、HiDPI でもボケない。 */
    AUTO,
    /** 低 (最速): 常に等倍。HiDPI ではボケるが最も軽い。 */
    LOW,
    /** 高: 最低 2x で描画 (アンチエイリアスが効いて拡大しても鮮明)。 */
    HIGH,
    /** 最高: 最低 3x で描画 (重いが最も鮮明)。 */
    ULTRA;

    /** バッファ肥大とメモリ消費を抑えるための実効倍率の上限。 */
    private static final double MAX_SCALE = 4.0;

    /** 現在アプリ全体に適用されている描画品質 (既定は AUTO)。 */
    private static volatile DiagramRenderQuality current = AUTO;

    /** 現在の描画品質を返す (never null)。 */
    public static DiagramRenderQuality current() {
        return current;
    }

    /** アプリ全体の描画品質を差し替える。{@code null} は AUTO に丸める。 */
    public static void setCurrent(DiagramRenderQuality quality) {
        current = (quality == null) ? AUTO : quality;
    }

    /** 永続化キー (列挙子名) から復元する。未知/空/null は AUTO。 */
    public static DiagramRenderQuality fromKey(String key) {
        if (key != null) {
            for (DiagramRenderQuality q : values()) {
                if (q.name().equalsIgnoreCase(key.trim())) {
                    return q;
                }
            }
        }
        return AUTO;
    }

    /**
     * 指定コンポーネントが載っているディスプレイで、この品質が要求する実効ラスタライズ倍率。
     * 1.0〜{@value #MAX_SCALE} にクランプされる。
     */
    public double scaleFor(Component c) {
        double device = deviceScale(c);
        switch (this) {
            case LOW:
                return 1.0;
            case HIGH:
                return clamp(Math.max(device, 2.0));
            case ULTRA:
                return clamp(Math.max(device, 3.0));
            case AUTO:
            default:
                return clamp(device);
        }
    }

    private static double clamp(double s) {
        if (Double.isNaN(s) || s < 1.0) {
            return 1.0;
        }
        return Math.min(MAX_SCALE, s);
    }

    /**
     * コンポーネントが属するディスプレイのデバイス倍率 (HiDPI なら {@code > 1})。
     * グラフィック構成が取れない (ヘッドレス / 未表示) 場合は 1.0。
     */
    static double deviceScale(Component c) {
        if (c != null) {
            GraphicsConfiguration gc = c.getGraphicsConfiguration();
            if (gc != null) {
                AffineTransform tx = gc.getDefaultTransform();
                if (tx != null) {
                    double sx = tx.getScaleX();
                    if (sx > 0) {
                        return sx;
                    }
                }
            }
        }
        return 1.0;
    }
}
