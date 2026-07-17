// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * アクティビティ図向け Setting 永続化用 DTO (不変)。
 *
 * <p>{@link StyleSettingsDialog} の戻り値 ({@link StyleSettingsDialog.Result}) と
 * {@link juml.Setting} の間でアクティビティ図設定を受け渡す。以前は
 * {@code StyleSettingsDialog} のネストクラスだったが、ファイル長 (checkstyle FileLength)
 * を抑えるためトップレベルへ切り出した。</p>
 */
public final class ActivityDiagramPrefs {
    /** ラムダ/匿名クラスのコールバック本体を partition ブロックに展開する。 */
    public final boolean expandInlineCallbacks;
    /** ローカル変数宣言をアクションノードとして表示する。 */
    public final boolean showLocalVars;
    /** 代入・インクリメント文をアクションノードとして表示する。 */
    public final boolean showAssignments;
    /** メソッド呼び出しの引数を表示する (例: helper.done(label))。 */
    public final boolean showCallArguments;
    /** メソッド本体内のインラインコメントを note として表示する。 */
    public final boolean showInlineComments;

    public ActivityDiagramPrefs(boolean expandInlineCallbacks, boolean showLocalVars,
                                 boolean showAssignments, boolean showCallArguments,
                                 boolean showInlineComments) {
        this.expandInlineCallbacks = expandInlineCallbacks;
        this.showLocalVars = showLocalVars;
        this.showAssignments = showAssignments;
        this.showCallArguments = showCallArguments;
        this.showInlineComments = showInlineComments;
    }

    /** 既定値 (PlantUmlActivityDiagram.Options の既定 = すべて表示)。 */
    public static ActivityDiagramPrefs defaults() {
        return new ActivityDiagramPrefs(true, true, true, true, true);
    }
}
