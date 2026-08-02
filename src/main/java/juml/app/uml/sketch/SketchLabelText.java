// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

/**
 * 引用符付きラベル ({@code entity "表示名" as alias} など) の共通エスケープ規則。
 *
 * <p>ラベルを無変換で {@code "..."} に埋め込むと、ラベル自身が {@code "} を含んだ瞬間に
 * 引用符の対応が崩れる ({@code component "App "Prod"" as c1})。宣言行のパターンが
 * マッチしなくなるため、その行は「未対応」に落ちて GUI 編集がロックされ、往復すると
 * 要素ごと消える。配置図コーデックで先に対処した規則をここへ集約し、
 * 使用例図・コンポーネント図・ER 図の各コーデックが同じ規則を共有する。</p>
 *
 * <p>正規表現側は {@link #QUOTED_LABEL} を使う ({@code "([^"]*)"} だとエスケープされた
 * {@code \"} で早期終了してしまう)。</p>
 */
final class SketchLabelText {

    private SketchLabelText() {
    }

    /**
     * 引用符付きラベルの捕捉グループ付きパターン片。{@code \\.} を許すことで
     * エスケープされた {@code \"} を本体の一部として読む。
     */
    static final String QUOTED_LABEL = "\"((?:\\\\.|[^\"\\\\])*)\"";

    /** 引用符付きラベルとして書き出すため {@code \} と {@code "} をエスケープする。 */
    static String escape(String label) {
        if (label == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(label.length());
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    /** {@link #escape} の逆変換 ({@link #QUOTED_LABEL} が捕捉した本体に適用する)。 */
    static String unescape(String escaped) {
        if (escaped == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\' && i + 1 < escaped.length()) {
                i++;
                c = escaped.charAt(i);
            }
            out.append(c);
        }
        return out.toString();
    }
}
