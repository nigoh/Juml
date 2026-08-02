// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.ArrayList;
import java.util.List;

/**
 * マインドマップデザイナーのノード 1 個分 (中心トピックまたは枝)。
 *
 * <p>PlantUML マインドマップ構文に対応する。深さは記号の連続長で表し、記号の種類が
 * 左右 (もしくは自動) を決める: {@code *}=自動 (AUTO)、{@code -}=左 (LEFT)、
 * {@code +}=右 (RIGHT)。子は順序付きリストで持ち、その並び順が左右の割り当てと出力順を
 * 兼ねる。実効的な左右は祖先方向へ辿って最初の非 AUTO を継承する
 * ({@link MindmapSketchModel#effectiveSideOf(MindmapNode)} / {@link MindmapSketchCodec})。</p>
 */
public final class MindmapNode {

    /** ノードの左右 (PlantUML の記号に対応)。 */
    public enum Side {
        /** {@code *}: 未指定 (祖先から継承する)。 */
        AUTO('*'),
        /** {@code -}: 左固定。 */
        LEFT('-'),
        /** {@code +}: 右固定。 */
        RIGHT('+');

        private final char symbol;

        Side(char symbol) {
            this.symbol = symbol;
        }

        /** PlantUML の記号 ({@code *} / {@code -} / {@code +})。 */
        public char symbol() {
            return symbol;
        }

        /** 記号から左右を引く (未対応記号は null)。 */
        public static Side fromSymbol(char c) {
            for (Side s : values()) {
                if (s.symbol == c) {
                    return s;
                }
            }
            return null;
        }
    }

    private String text;
    /** このノード自身に指定された左右 (継承前の生の値)。 */
    private Side ownSide = Side.AUTO;
    /** 子ノード (可変・順序が左右振り分けと出力順を兼ねる)。 */
    private final List<MindmapNode> children = new ArrayList<>();
    /** 親ノード (ルートなら null)。 */
    private MindmapNode parent;

    public MindmapNode(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /** このノード自身に指定された左右 (継承前の生の値。既定は AUTO)。 */
    public Side getOwnSide() {
        return ownSide;
    }

    public void setOwnSide(Side ownSide) {
        this.ownSide = ownSide != null ? ownSide : Side.AUTO;
    }

    /** 子ノード一覧 (可変、追加/削除に使ってよい)。 */
    public List<MindmapNode> getChildren() {
        return children;
    }

    /** 親ノード (ルートなら null)。 */
    public MindmapNode getParent() {
        return parent;
    }

    void setParent(MindmapNode parent) {
        this.parent = parent;
    }
}
