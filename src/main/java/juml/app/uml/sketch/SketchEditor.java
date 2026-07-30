// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import javax.swing.JComponent;

/**
 * 図種ごとの GUI デザイナー編集面 (ツールバー + キャンバス) の共通契約。
 *
 * <p>{@link SketchPane} が {@link SketchDiagramType} の判定結果に応じて
 * アクティブなエディタを切り替える。Undo/Redo は PlantUML テキストの
 * スナップショットとして {@link SketchPane} 側で一元管理するため、
 * 各エディタは「テキスト → モデル」({@link #load}) と「モデル → テキスト」
 * ({@link #currentPuml}) の往復と編集通知 ({@link #setOnEdited}) だけを担う。</p>
 */
interface SketchEditor {

    /** 図種固有のツールバー (追加ボタン・モード切替など)。 */
    JComponent toolbarComponent();

    /** キャンバス側のメインコンポーネント (スクロール込み)。 */
    JComponent canvasComponent();

    /**
     * PlantUML テキストを解析してキャンバスへ反映する。
     * 未対応構文が含まれる場合は編集不可として表示のみ行う。
     */
    void load(String pumlText);

    /** 現在のモデルから PlantUML を再生成して返す。 */
    String currentPuml();

    /** GUI 編集が可能な状態か (未対応構文があると false)。 */
    boolean isEditable();

    /** キャンバス操作でモデルが変わったときの通知先を登録する。 */
    void setOnEdited(Runnable onEdited);

    /**
     * 直近の {@link #load} で往復できず「未対応」とされた行の一覧 (空なら完全対応)。
     * {@link SketchPane} が「PlantUML コメント行だけが原因の編集ロック」を検出し、
     * ワンクリックでコメントを除いて編集を有効化するボタンを出すために使う。
     * 既定は空 (未実装のエディタではボタンを出さない)。
     */
    default java.util.List<String> unsupportedLines() {
        return java.util.List.of();
    }
}
