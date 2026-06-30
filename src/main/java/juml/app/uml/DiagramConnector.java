// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * 2 つの付箋 ({@link DiagramNote}) を結ぶコネクタ 1 本。
 * 端点は付箋の id で保持し、描画時にそれぞれの矩形へ解決する。
 */
final class DiagramConnector {

    private final String fromId;
    private final String toId;

    DiagramConnector(String fromId, String toId) {
        this.fromId = fromId;
        this.toId = toId;
    }

    String getFromId() {
        return fromId;
    }

    String getToId() {
        return toId;
    }

    /** いずれかの端点が {@code id} か。 */
    boolean touches(String id) {
        return fromId.equals(id) || toId.equals(id);
    }

    /** 端点が同じ (向きは無視) 2 本を同一視する。 */
    boolean sameEndpoints(DiagramConnector o) {
        return (fromId.equals(o.fromId) && toId.equals(o.toId))
                || (fromId.equals(o.toId) && toId.equals(o.fromId));
    }

    DiagramConnector copy() {
        return new DiagramConnector(fromId, toId);
    }
}
