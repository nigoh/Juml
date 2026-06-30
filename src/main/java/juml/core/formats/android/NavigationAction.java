// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

/**
 * Jetpack Navigation の {@code <action>} 要素を表すデータクラス。
 *
 * <p>各 {@link NavigationDestination} が保持するローカルアクションか、
 * {@link AndroidNavigationGraphInfo} が保持するグローバルアクションのどちらか。</p>
 */
public class NavigationAction {

    private String id;
    private String idRef;
    private String destination;
    private String popUpTo;
    private boolean popUpToInclusive;
    private boolean global;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIdRef() {
        return idRef;
    }

    public void setIdRef(String idRef) {
        this.idRef = idRef;
    }

    /** 遷移先 Destination の idRef (正規化済み)。 */
    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    /** app:popUpTo の idRef (正規化済み)。null ならバックスタックを操作しない。 */
    public String getPopUpTo() {
        return popUpTo;
    }

    public void setPopUpTo(String popUpTo) {
        this.popUpTo = popUpTo;
    }

    public boolean isPopUpToInclusive() {
        return popUpToInclusive;
    }

    public void setPopUpToInclusive(boolean popUpToInclusive) {
        this.popUpToInclusive = popUpToInclusive;
    }

    /** グラフルート直下のグローバルアクションなら true。 */
    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }
}
