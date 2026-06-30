// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.android;

import java.util.ArrayList;
import java.util.List;

/**
 * Jetpack Navigation グラフ内の 1 つの遷移先（画面）を表すデータクラス。
 *
 * <p>{@code <fragment>} / {@code <activity>} / {@code <dialog>} /
 * ネスト {@code <navigation>} / {@code <include>} のいずれかに対応する。</p>
 *
 * <p>{@link AndroidNavigationGraphParser} が {@link AndroidNavigationGraphInfo} に格納する。</p>
 */
public class NavigationDestination {

    /** Destination の種別。PlantUML 図化時のステレオタイプ振り分けに使う。 */
    public enum Kind {
        FRAGMENT,
        ACTIVITY,
        DIALOG,
        NAVIGATION,
        INCLUDE
    }

    private Kind kind;
    private String id;
    private String idRef;
    private String name;
    private String label;
    private String toolsLayout;
    private String startDestination;
    private final List<NavigationAction> actions = new ArrayList<>();
    private final List<NavigationArgument> arguments = new ArrayList<>();
    private final List<String> deepLinks = new ArrayList<>();

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    /** {@code android:id} の生値 (例: {@code @+id/homeFragment})。 */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /** {@code android:id} を正規化した参照名 (例: {@code homeFragment})。 */
    public String getIdRef() {
        return idRef;
    }

    public void setIdRef(String idRef) {
        this.idRef = idRef;
    }

    /** {@code android:name} (クラス名、FQCN or 短縮形)。 */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** {@code android:label}。 */
    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /** {@code tools:layout} (Android Studio でのプレビュー用レイアウト参照)。 */
    public String getToolsLayout() {
        return toolsLayout;
    }

    public void setToolsLayout(String toolsLayout) {
        this.toolsLayout = toolsLayout;
    }

    /** ネスト {@code <navigation>} の {@code app:startDestination} (正規化済み)。 */
    public String getStartDestination() {
        return startDestination;
    }

    public void setStartDestination(String startDestination) {
        this.startDestination = startDestination;
    }

    public List<NavigationAction> getActions() {
        return actions;
    }

    public List<NavigationArgument> getArguments() {
        return arguments;
    }

    public List<String> getDeepLinks() {
        return deepLinks;
    }

    /**
     * 図化時の表示名。{@code label} → {@code name} の短縮形 → {@code idRef} の優先順で返す。
     */
    public String displayName() {
        if (label != null && !label.isEmpty()) {
            return label;
        }
        if (name != null && !name.isEmpty()) {
            int dot = name.lastIndexOf('.');
            return dot >= 0 ? name.substring(dot + 1) : name;
        }
        if (idRef != null && !idRef.isEmpty()) {
            return idRef;
        }
        return id != null ? id : "(unknown)";
    }
}
