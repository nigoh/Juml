// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

/**
 * 補完候補 1 件。ポップアップの 1 行に対応する。
 *
 * <p>「表示する見出し」({@link #label()}) と「実際に挿入するテンプレート」
 * ({@link #insert()}) を分けているのが要点で、これによりスニペット候補
 * (見出しは {@code alt}、挿入は {@code alt …/else/end} のブロック) を
 * キーワード候補と同じ一覧に混ぜられる。</p>
 */
final class PumlCompletionItem {

    /** 候補の種別 (表示上のタグ付けと並べ替えの重み付けに使う)。 */
    enum Kind {
        /** 複数行の雛形を展開する。最も入力を減らせるので上位に出す。 */
        SNIPPET,
        /** PlantUML の予約語・ディレクティブ。 */
        KEYWORD,
        /** 関係を表す矢印記法 ({@code -->} / {@code <|--} など)。 */
        ARROW,
        /** 本文中に既に現れている識別子 (クラス名・参加者名など)。 */
        IDENTIFIER,
        /** 特定キーワードの引数値 ({@code !theme} のテーマ名・skinparam の属性など)。 */
        VALUE
    }

    private final Kind kind;
    private final String label;
    private final String insert;
    private final String detail;
    private final int score;

    private PumlCompletionItem(Kind kind, String label, String insert, String detail,
                               int score) {
        this.kind = kind;
        this.label = label;
        this.insert = insert;
        this.detail = detail;
        this.score = score;
    }

    /** 挿入テキストが見出しと同じ単純な候補 (キーワード・識別子・矢印・値)。 */
    static PumlCompletionItem word(Kind kind, String word, String detail) {
        return new PumlCompletionItem(kind, word, word, detail, 0);
    }

    /** 見出しと挿入テンプレートが異なる候補 (スニペット)。 */
    static PumlCompletionItem snippet(String label, String template, String detail) {
        return new PumlCompletionItem(Kind.SNIPPET, label, template, detail, 0);
    }

    /** 並べ替え得点だけ差し替えた複製を返す (辞書側の定義は不変に保つ)。 */
    PumlCompletionItem withScore(int newScore) {
        return new PumlCompletionItem(kind, label, insert, detail, newScore);
    }

    Kind kind() {
        return kind;
    }

    /** ポップアップに出す見出し (= 打鍵で絞り込む対象の語)。 */
    String label() {
        return label;
    }

    /**
     * 確定時に挿入するテンプレート。{@link PumlSnippetTemplate} の
     * プレースホルダ記法を含みうる。
     */
    String insert() {
        return insert;
    }

    /** 見出しの右に薄く出す補足 (図種名や展開後の形)。無ければ空文字。 */
    String detail() {
        return detail == null ? "" : detail;
    }

    /** 並べ替え得点 (大きいほど上位)。 */
    int score() {
        return score;
    }

    /** 挿入テンプレートがタブストップ/複数行を含むか (= 単純な語ではない)。 */
    boolean isTemplate() {
        return !insert.equals(label);
    }

    @Override
    public String toString() {
        return label;
    }
}
