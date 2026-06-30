// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * UML ビューアーの可変状態を一か所にまとめた POJO。
 *
 * <p>副作用なし。{@link DiagramController} が状態を変更し、
 * {@link UmlMainFrame} の renderLoop が参照する。</p>
 */
public final class DiagramState {

    /** 現在選択されているシーケンス図起点 ({@code Class.method})。null なら未設定。 */
    String sequenceEntry;

    /** 現在選択されているアクティビティ図起点 ({@code Class.method})。null なら未設定。 */
    String activityEntry;

    /** 現在選択されているコールグラフ起点 ({@code Class.method})。null なら未設定。 */
    String callGraphEntry;

    /** 現在選択されている Layout 図のキー。null なら未設定。 */
    String currentLayoutKey;

    /** 現在選択されている Navigation 図のキー。null なら未設定。 */
    String currentNavigationKey;

    /** クラス図の現在の絞り込みスコープ。null なら全件表示。 */
    DiagramScope currentScope;

    /** 現在表示中の PlantUML ソース。 */
    String currentPuml;

    /** 現在表示中の SVG XML。 */
    String currentSvgXml;

    /**
     * シーケンス図で隠す participant 名 (空ならフィルタ無し)。
     * {@link SequenceParticipantFilterDialog} で設定する。
     */
    final Set<String> sequenceHiddenParticipants = new LinkedHashSet<>();

    /**
     * sequence / activity / callgraph の 3 エントリを同じメソッドに揃える。
     * これによりツールバーボタンでどの図種へ切り替えても再入力ダイアログが出ない。
     */
    void setAllMethodEntries(String entry) {
        sequenceEntry = entry;
        activityEntry = entry;
        callGraphEntry = entry;
    }
}
