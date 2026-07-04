// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.git;

import juml.app.uml.git.LineDiff.Row;
import juml.app.uml.git.LineDiff.Type;

import java.util.List;

/**
 * 2 つのアクティビティ図 PlantUML (旧 / 新) を行単位で突き合わせ、変化した<b>アクション
 * ノード</b>だけに背景色を差し込んだ左右比較用テキストを作る純ロジック。
 *
 * <p>アクティビティ図のアクションノードは {@code <字下げ>:text;} の 1 行で出力されるため
 * ({@link juml.core.formats.uml.PlantUmlActivityDiagram})、{@link LineDiff} の行整列結果を
 * そのまま利用できる。旧側は削除/変更ノードを赤/黄、新側は追加/変更ノードを緑/黄に塗る。
 * ノード以外 (if/while など制御構文行) は塗らずに原文のまま残すので、図は必ず再描画できる。</p>
 */
final class ActivityDiffHighlight {

    /** 追加ノードの前景色 (GitHub diff 風・クラス差分と統一)。 */
    static final String ADDED_FG = "#1A7F37";
    /** 削除ノードの前景色。 */
    static final String REMOVED_FG = "#CF222E";
    /** 変更ノードの前景色。 */
    static final String MODIFIED_FG = "#9A6700";

    private ActivityDiffHighlight() {
    }

    /**
     * 旧 / 新のアクティビティ PlantUML から、色付けした旧側・新側テキストを返す。
     *
     * <p>変化したアクションノードのラベルを creole の {@code <color:>} で着色する
     * (削除は打ち消し線付き赤)。背景塗り {@code #color:label;} は同梱 PlantUML で
     * 非推奨のため、バージョンに強い前景着色を使う。</p>
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
     * アクションノード行 {@code <indent>:text;} のラベルを {@code <color:>} で着色する。
     * ノードでない行 (if/while など制御構文) は原文のまま返す。
     */
    private static String paint(String line, String color, boolean struck) {
        int colon = indentEnd(line);
        int semi = lastSemicolon(line);
        if (colon < 0 || line.charAt(colon) != ':' || semi <= colon) {
            return line; // ノードでない → 触らない
        }
        String indent = line.substring(0, colon);
        String text = line.substring(colon + 1, semi);
        String tail = line.substring(semi); // ";" 以降 (末尾空白を含む)
        String open = "<color:" + color + ">" + (struck ? "<s>" : "");
        String close = (struck ? "</s>" : "") + "</color>";
        return indent + ":" + open + text + close + tail;
    }

    /** 先頭の空白を飛ばした最初の非空白位置を返す (全て空白なら -1)。 */
    private static int indentEnd(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /** 末尾側にある最後の {@code ';'} の位置 (末尾が ';' でなければ -1)。 */
    private static int lastSemicolon(String line) {
        for (int i = line.length() - 1; i >= 0; i--) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == ';' ? i : -1;
        }
        return -1;
    }
}
