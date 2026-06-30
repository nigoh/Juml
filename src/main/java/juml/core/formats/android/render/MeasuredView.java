// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android.render;

import java.util.ArrayList;
import java.util.List;

import juml.core.formats.android.LayoutViewNode;

/**
 * {@link AndroidLayoutEngine} が計測・配置した結果の 1 ノード。
 *
 * <p>{@link #getX()}/{@link #getY()}/{@link #getWidth()}/{@link #getHeight()} は
 * 画面左上を原点とした <b>絶対座標</b> (dp)。{@link #getNode()} は元の XML ノード、
 * {@link #getType()} は描画用の大分類。子は {@link #getChildren()} に絶対座標で並ぶ。</p>
 */
public final class MeasuredView {

    private final LayoutViewNode node;
    private final WidgetType type;
    private double x;
    private double y;
    private double width;
    private double height;
    private final List<MeasuredView> children = new ArrayList<>();

    MeasuredView(LayoutViewNode node, WidgetType type, double width, double height) {
        this.node = node;
        this.type = type;
        this.width = width;
        this.height = height;
    }

    public LayoutViewNode getNode() {
        return node;
    }

    public WidgetType getType() {
        return type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public List<MeasuredView> getChildren() {
        return children;
    }

    void setSize(double width, double height) {
        this.width = width;
        this.height = height;
    }

    void setHeight(double height) {
        this.height = height;
    }

    void setWidth(double width) {
        this.width = width;
    }

    void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
