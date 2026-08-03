// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * 引用符付きラベル ({@code entity "表示名" as alias} など) の共通取り扱い規則。
 *
 * <p><b>PlantUML の引用符付きラベルにエスケープは存在しない。</b>実機
 * (PlantUML 1.2026.6) で確認した挙動:</p>
 * <ul>
 *   <li>{@code component "App "Prod"" as c1} → {@code App "Prod"} と<b>正しく描画される</b>
 *       (末尾の {@code " as <id>} を手掛かりに本体を貪欲に取っている。
 *       {@code component "App as Prod" as c1} も {@code App as Prod} と描かれる)</li>
 *   <li>{@code component "App \"Prod\"" as c1} → {@code App \"Prod\"} と
 *       <b>逆スラッシュごと描画される</b> ({@code \"} は未知のエスケープ扱い)</li>
 *   <li>{@code "Alpha\nBeta"} の {@code \n} は<b>改行</b>として解釈される
 *       (ラベル中の {@code \} は 1 文字も触ってはいけない)</li>
 * </ul>
 *
 * <p>したがってラベルは<b>無変換で書き出し</b>、読み取り側が {@link #QUOTED_LABEL} の
 * 貪欲マッチで PlantUML と同じ規則で本体を取り切る。以前ここには {@code \} による
 * エスケープがあったが、それは (1) 図に逆スラッシュを描画してしまい、(2) {@code \n} の
 * 改行を潰し、(3) 末尾が {@code \} のラベルを未対応行に落として編集をロックしていた。</p>
 *
 * <p>逆に素朴な {@code "([^"]*)"} で読むと {@code "} を含むラベルで宣言行がマッチせず、
 * その行が未対応に落ちて<b>要素ごと消える</b>。貪欲マッチはこの両方を同時に避ける
 * (使う側が必ず末尾へ {@code \s+as\s+<id>\s*$} などのアンカーを置くため取り過ぎない)。</p>
 */
final class SketchLabelText {

    private SketchLabelText() {
    }

    /**
     * 引用符付きラベルの捕捉グループ付きパターン片。<b>貪欲</b>にすることで、
     * ラベル内の {@code "} や {@code as} を PlantUML と同じ規則で取り込む。
     * 使う側は必ず末尾にアンカー ({@code \s+as\s+<id>\s*$} や行末) を付けること。
     */
    static final String QUOTED_LABEL = "\"(.*)\"";

    /**
     * ラベルを引用符の中へ書き出すための変換。PlantUML にエスケープが無いため<b>無変換</b>。
     * 「ここでエスケープを検討した」という判断を残すために関数として置く。
     */
    static String forOutput(String label) {
        return label == null ? "" : label;
    }

    /** {@link #QUOTED_LABEL} が捕捉した本体をモデルのラベルへ戻す変換 (同じく無変換)。 */
    static String fromCaptured(String captured) {
        return captured == null ? "" : captured;
    }
}
