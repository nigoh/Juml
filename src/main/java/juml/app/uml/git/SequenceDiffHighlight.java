// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.LineDiff.Row;
import juml.app.uml.git.LineDiff.Type;

import java.util.List;

/**
 * 2 つのシーケンス図 PlantUML (旧 / 新) を行単位で突き合わせ、変化した<b>メッセージ</b>の
 * ラベルだけに色を付けた左右比較用テキストを作る純ロジック。
 *
 * <p>シーケンス図のメッセージ行は {@code caller -> target : label} 形式なので
 * ({@link juml.core.formats.uml.PlantUmlSequenceDiagram})、矢印 ({@code ->} / {@code <-}) を
 * 含む行の {@code " : "} 以降のラベルを creole {@code <color:>} で着色する。participant 宣言・
 * activate・alt/loop などラベルの無い制御行や矢印の無い行は原文のまま残す。</p>
 */
final class SequenceDiffHighlight {

    /** 追加メッセージの前景色 (クラス/アクティビティ差分と統一)。 */
    static final String ADDED_FG = "#1A7F37";
    /** 削除メッセージの前景色。 */
    static final String REMOVED_FG = "#CF222E";
    /** 変更メッセージの前景色。 */
    static final String MODIFIED_FG = "#9A6700";

    private SequenceDiffHighlight() {
    }

    /**
     * 旧 / 新のシーケンス PlantUML から、色付けした旧側・新側テキストを返す。
     *
     * @return {@code [0]} = 旧側 (削除赤・変更黄)、{@code [1]} = 新側 (追加緑・変更黄)
     */
    static String[] colorize(String oldPuml, String newPuml) {
        List<Row> rows = LineDiff.compute(oldPuml, newPuml);
        StringBuilder oldOut = new StringBuilder();
        StringBuilder newOut = new StringBuilder();
        for (Row r : rows) {
            if (r.oldText != null) {
                oldOut.append(r.type == Type.REMOVED ? paint(r.oldText, REMOVED_FG, true)
                        : r.type == Type.MODIFIED ? paint(r.oldText, MODIFIED_FG, false)
                        : r.oldText).append('\n');
            }
            if (r.newText != null) {
                newOut.append(r.type == Type.ADDED ? paint(r.newText, ADDED_FG, false)
                        : r.type == Type.MODIFIED ? paint(r.newText, MODIFIED_FG, false)
                        : r.newText).append('\n');
            }
        }
        return new String[]{oldOut.toString(), newOut.toString()};
    }

    /**
     * メッセージ行 {@code caller -> target: label} / {@code ... : label} のラベル部を
     * {@code <color:>} で着色する。矢印が無い行やラベルの無い行 (participant/activate/alt
     * など) は原文のまま。同梱 PlantUML は矢印直後の {@code target:} 形式で出力するため、
     * 矢印以降の最初の {@code :} を区切りとして扱う。
     */
    private static String paint(String line, String color, boolean struck) {
        int arrow = arrowIndex(line);
        if (arrow < 0) {
            return line;
        }
        int colon = line.indexOf(':', arrow);
        if (colon < 0) {
            return line;
        }
        String head = line.substring(0, colon + 1); // ':' まで含む
        String rest = line.substring(colon + 1);     // 先頭空白 + ラベル
        int labelStart = 0;
        while (labelStart < rest.length() && rest.charAt(labelStart) == ' ') {
            labelStart++;
        }
        String spaces = rest.substring(0, labelStart);
        String label = rest.substring(labelStart);
        if (label.isEmpty()) {
            return line;
        }
        String open = "<color:" + color + ">" + (struck ? "<s>" : "");
        String close = (struck ? "</s>" : "") + "</color>";
        return head + spaces + open + label + close;
    }

    /** メッセージ矢印 ({@code ->} または {@code <-}) の位置 (無ければ -1)。 */
    private static int arrowIndex(String line) {
        int i = line.indexOf("->");
        return i >= 0 ? i : line.indexOf("<-");
    }
}
