// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

/**
 * クラス図上で Jetpack 系ステレオタイプ・装飾を出すかどうかを束ねた設定。
 *
 * <p>{@link PlantUmlClassDiagram.Options#jetpack} に保持される。
 * デフォルトは {@code enabled=false} (後方互換)。{@code enabled=true} のとき、
 * 個別トグル ({@link #relations} / {@link #lifecycle} / {@link #observable}) で
 * サブ機能を絞れる。サブトグルの初期値は全て true。</p>
 */
public final class JetpackOptions {
    /** Jetpack ステレオタイプ全体の有効化スイッチ。 */
    public boolean enabled = false;
    /** Activity ↔ Fragment / NavHost の関連線を出す (Phase C)。 */
    public boolean relations = true;
    /** ライフサイクルコールバックメソッドに {@code {lifecycle}} 装飾を付ける (Phase B)。 */
    public boolean lifecycle = true;
    /** LiveData / StateFlow / Flow フィールドに {@code <<observable:...>>} を付ける (Phase B)。 */
    public boolean observable = true;
}
