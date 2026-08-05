// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.regex.Pattern;

/**
 * {@code &#123; … &#125;} ブロックの中の 1 行をどう読むか、全 codec 共有の規則。
 *
 * <p>どの codec も外側のループでは「コメント行はモデル化できないので未対応として積み、
 * デザイナーの編集をロックする」という規則を持っている。ところが<b>ブロックの中を読む
 * 兄弟の経路</b>は codec ごとにばらばらで、同じ入力の扱いが割れていた:</p>
 *
 * <ul>
 *   <li>{@code ObjectSketchCodec.readAttributes} — 空行と {@code &#125;} 以外を<b>すべて</b>
 *       属性として取り込む。{@code toPuml} は属性を {@code Name : attr} へ正規化するので、
 *       行頭の {@code '} がコロンの後ろへ移り<b>コメントでなくなる</b> (実測:
 *       {@code ' a comment} が属性行として描画される)。しかも未対応行は空のままなので
 *       デザイナーは編集可能で開き、最初の 1 操作で書き換わる。</li>
 *   <li>{@code SketchPumlCodec.readMembers} — 同じくコメントをメンバーとして取り込む。</li>
 *   <li>{@code ErSketchCodec.readColumns} — こちらだけが未対応として積んでいた。</li>
 * </ul>
 *
 * <p>区切り線の判定も 2 通りあった。ER は {@code ^(--|==|__|\.\.)+\s*$} と<b>記号だけの行</b>
 * に限る一方、クラス図は {@code ^(--|==|__|\.\.).*$} と行頭一致だけを見ていたため、
 * {@code __id : int} のようなごく普通のメンバー名 (Python/C++/PHP 由来の private 命名) を
 * 区切り線と誤認してクラス全体を編集ロックしていた。往復では何も壊れないのに、である。</p>
 */
final class SketchBlockLine {

    /**
     * PlantUML のメンバー区切り線。<b>記号だけの行</b>に限る。
     *
     * <p>{@code .*} で行頭一致にすると {@code __id : int} や {@code ..next : Node} を
     * 巻き込む。PlantUML 自身もこれらを区切り線ではなくメンバー行として描く (実測)。</p>
     */
    private static final Pattern DIVIDER = Pattern.compile("^(--|==|__|\\.\\.)+\\s*$");

    private SketchBlockLine() {
    }

    /** ブロック内の区切り線か ({@code --} / {@code ==} / {@code __} / {@code ..})。 */
    static boolean isDivider(String trimmedLine) {
        return trimmedLine != null && DIVIDER.matcher(trimmedLine).matches();
    }

    /**
     * ブロック内のコメント行か。
     *
     * <p>PlantUML のコメントは 2 種類ある — 行コメント {@code '…} と<b>ブロックコメント
     * {@code /' … '/}</b>。{@code '} だけを見ていたため、ブロックコメントは素通りして
     * オブジェクト図の属性として取り込まれ、書き出しで {@code Foo : /' hidden '/} になった。
     * PlantUML はブロックコメントを除去してから解析するので {@code Foo :} だけが残り、
     * <b>構文エラーで図が描けなくなる</b> (同梱 1.2026.6 で実測)。同じ行をブロックの外に
     * 置けば各 codec の外側ループがロックする — 内と外で扱いが割れていた。</p>
     */
    static boolean isComment(String trimmedLine) {
        return trimmedLine != null
                && (trimmedLine.startsWith("'") || trimmedLine.startsWith("/'"));
    }
}
