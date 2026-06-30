// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.app.uml.PlantUmlSvgRenderer.LinkArea;
import juml.app.uml.PlantUmlSvgRenderer.RenderedSvg;

import javax.swing.JMenuItem;
import java.util.List;

/**
 * {@link DiagramTabPane} 内部でのみ使う小さなヘルパ群。
 *
 * <p>本体ファイルの肥大化 (行数上限) を避けるため、状態を持たない結果ホルダと
 * href 解析ユーティリティをここへ切り出している。</p>
 */
final class DiagramTabInternals {

    private DiagramTabInternals() {
    }

    /** ラベルとアクションから {@link JMenuItem} を 1 つ作る (ポップアップ構築の短縮用)。 */
    static JMenuItem menuItem(String label, Runnable action) {
        JMenuItem mi = new JMenuItem(label);
        mi.addActionListener(e -> action.run());
        return mi;
    }

    /**
     * クラス FQN に対応するリンク領域 (クラスボックス) の SVG 座標矩形 {@code [x,y,w,h]} を返す。
     * 見つからなければ null。ELEMENT アンカー付箋の追従位置計算に使う。
     */
    static double[] elementRect(List<LinkArea> areas, String fqn) {
        if (fqn == null || areas == null) {
            return null;
        }
        for (LinkArea a : areas) {
            if (fqn.equals(parseClassFqnFromHref(a.getHref()))) {
                return new double[] {a.getX(), a.getY(), a.getWidth(), a.getHeight()};
            }
        }
        return null;
    }

    /** 1 タブ分の描画結果 (PlantUML テキスト + レンダリング済み SVG)。 */
    static final class RenderResult {
        final String puml;
        final RenderedSvg svg;

        RenderResult(String puml, RenderedSvg svg) {
            this.puml = puml;
            this.svg = svg;
        }
    }

    /** {@code juml://class/<FQN>} 形式の href からクラス FQN を取り出す (無効なら null)。 */
    static String parseClassFqnFromHref(String href) {
        if (href == null) {
            return null;
        }
        final String prefix = "juml://class/";
        if (!href.startsWith(prefix)) {
            return null;
        }
        String s = href.substring(prefix.length()).trim();
        return s.isEmpty() ? null : s;
    }

    /** シーケンス/アクティビティ図のエントリポイント {@code Class.method} を組み立てる (未指定なら null)。 */
    static String entryOf(DiagramRequest r) {
        String cls = r.getSequenceEntryClass();
        String method = r.getSequenceEntryMethod();
        if (cls == null || method == null) {
            return null;
        }
        return cls + "." + method;
    }

    /** 完全修飾名から単純クラス名を取り出す (null/空は空文字)。 */
    static String extractSimpleClass(String qn) {
        if (qn == null || qn.isEmpty()) {
            return "";
        }
        int dot = qn.lastIndexOf('.');
        return dot < 0 ? qn : qn.substring(dot + 1);
    }
}
