// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlantUML テキストの「図種を見分ける」純ロジック ({@link PlantUmlRenderer} から分離)。
 *
 * <p>レンダラは {@code @startuml} 直後にスタイル prelude や {@code scale max} を差し込むが、
 * その差し込み可否は図種で変わる。ここでは開始ディレクティブの走査と、向き指定
 * ({@code left to right direction}) を受け付ける図種かの判定だけを持つ。走査本体と
 * 分けているのは責務の分離に加え、{@link PlantUmlRenderer} を checkstyle の
 * FileLength 上限 (902 行) 内に保つため。</p>
 *
 * <p><b>実機検証で確定した事実 (PlantUML 1.2026.6):</b></p>
 * <ul>
 *   <li>{@code @startmindmap} / {@code @startwbs} / {@code @startsalt} / {@code @startgantt} /
 *       {@code @startjson} / {@code @startyaml} は {@code !theme} / {@code skinparam} /
 *       {@code scale max} を受け付ける (= 日本語フォント補完と PNG 縮小が効く)。</li>
 *   <li>ただし <b>向き指定は上記のうち mindmap 以外で致命的</b>: wbs / gantt は構文エラー、
 *       salt は PlantUML 自体がクラッシュ、json は指定行を「データ」と解釈して図が壊れる。
 *       よって {@code @startuml} 以外へ向き指定は出さない。</li>
 *   <li>{@code @startdot} / {@code @startditaa} / {@code @startlatex} 等は本文が別言語の
 *       生ソースなので一切注入しない。</li>
 * </ul>
 */
public final class PumlDiagramScan {

    private PumlDiagramScan() {
    }

    /** 行頭 (前後空白許容) に現れる開始ディレクティブ {@code @start<種別>}。 */
    private static final Pattern START_LINE =
            Pattern.compile("(?m)^[ \\t]*@start([A-Za-z]*)[^\\n]*");

    /** 行頭 (前後空白許容) に現れる終了ディレクティブ {@code @end<種別>}。 */
    private static final Pattern END_LINE =
            Pattern.compile("(?m)^[ \\t]*@end([A-Za-z]*)[^\\n]*");

    /**
     * スタイル prelude / {@code scale max} を注入しても安全と実機確認できた図種
     * ({@code @start} に続く語)。ここに無い図種 (dot / ditaa / latex / math / chen 等) は
     * 本文が別言語の生ソースだったり注入行を解釈できなかったりするため一切触らない。
     */
    private static final Set<String> STYLEABLE_KINDS =
            Set.of("uml", "mindmap", "wbs", "salt", "gantt", "json", "yaml");

    /** 見つかった開始ディレクティブ (種別と、その行の終端オフセット)。 */
    record Start(String kind, int lineEnd) {

        /** {@code @startuml} (向き指定・Smetana 注入が許される唯一の図種) か。 */
        boolean isUml() {
            return "uml".equals(kind);
        }

        /** スタイル行や {@code scale} を注入してよい図種か。 */
        boolean isStyleable() {
            return STYLEABLE_KINDS.contains(kind);
        }
    }

    /** 最初の開始ディレクティブを返す (無ければ null)。 */
    static Start firstStart(String puml) {
        if (puml == null || puml.isEmpty()) {
            return null;
        }
        Matcher m = START_LINE.matcher(puml);
        if (!m.find()) {
            return null;
        }
        return new Start(m.group(1).toLowerCase(java.util.Locale.ROOT), m.end());
    }

    /** 行頭の {@code @start...} ディレクティブ行の数。 */
    static int countStarts(String puml) {
        return countMatches(START_LINE, puml);
    }

    /** 行頭の {@code @end...} ディレクティブ行の数。 */
    static int countEnds(String puml) {
        return countMatches(END_LINE, puml);
    }

    private static int countMatches(Pattern p, String puml) {
        if (puml == null || puml.isEmpty()) {
            return 0;
        }
        Matcher m = p.matcher(puml);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 開始ディレクティブ行の直後へ {@code block} を差し込む。 */
    static String insertAfter(String puml, Start start, String block) {
        int lineEnd = start.lineEnd();
        if (lineEnd >= puml.length()) {
            return puml + "\n" + block;
        }
        // lineEnd は行内容の終端 (改行の手前)。改行を挟んで次行として差し込む。
        return puml.substring(0, lineEnd) + "\n" + block.stripTrailing()
                + puml.substring(lineEnd);
    }

    // --- 向き指定 (left to right direction) の可否判定 ----------------------------








    /**
     * 渡された PlantUML が向き指定ディレクティブ ({@code left to right direction} /
     * {@code top to bottom direction}) を受け付ける図種かを判定する。
     *
     * <p>シーケンス図・アクティビティ図はこれらを受け付けず構文エラーになる (実機検証済み)。
     * 一方 ER 図・配置図は受け付ける。判定が難しいのは {@code entity} / {@code database} /
     * {@code queue} で、シーケンス図の参加者宣言でもあり ER/配置図の要素宣言でもある。
     * これらだけで「シーケンス図」と決めつけると、ER 図 ({@code entity User {...}}) や
     * 配置図 ({@code database DB}) でユーザが選んだ向き指定が黙って捨てられる。そこで
     * 構造図であることを示す宣言 ({@code hide circle} / {@code node} / ブロック付き宣言 /
     * crow's foot 記法など) が同居する場合は、これらを参加者宣言とみなさない。</p>
     *
     * <p>構造図の手掛かりが一切ない {@code database A} だけの図は依然シーケンス図として
     * 扱う (曖昧なときは「向き指定を出さない」= 構文エラーを避ける側に倒す)。</p>
     *
     * @return 向き指定を出してよければ true (= sequence/activity 以外)
     */
    /**
     * PlantUML 自身にパースさせて得た図種クラス名 (判定できなければ null)。
     *
     * <p>レイアウトまでは行わず構文解析だけなので、実測で 1〜50ms 程度と描画より一桁安い。
     * 正規表現で図種を推測すると、参加者名が {@code Node} / {@code Note} のような
     * キーワードと同綴りなだけで誤判定する類の事故が延々と出る (実際に何度も踏んだ)。
     * <b>推測ではなく PlantUML の解釈そのもの</b>を使うのが唯一収束する方法。</p>
     */
    private static String parsedDiagramKind(String puml) {
        try {
            java.util.List<net.sourceforge.plantuml.BlockUml> blocks =
                    new net.sourceforge.plantuml.SourceStringReader(puml).getBlocks();
            if (blocks.isEmpty()) {
                return null;
            }
            net.sourceforge.plantuml.core.Diagram d = blocks.get(0).getDiagram();
            return d == null ? null : d.getClass().getSimpleName();
        } catch (RuntimeException | StackOverflowError ex) {
            return null;
        }
    }

    /**
     * 向き指定 ({@code left to right direction}) を受け付ける PlantUML の図種クラス。
     *
     * <p>実機検証: クラス図・記述図 (使用例/配置/コンポーネント)・状態図は受け付け、
     * シーケンス図は<b>黙ってクラス図へ化け</b>、アクティビティ図は構文エラーになる。</p>
     */
    private static final Set<String> DIRECTION_CAPABLE_DIAGRAMS =
            Set.of("ClassDiagram", "DescriptionDiagram", "StateDiagram");


    static boolean supportsDirection(String puml) {
        if (puml == null || puml.isEmpty()) {
            return true;
        }
        // パースできない図 (構文エラー、@startuml へ埋め込んだ別 DSL など) には出さない。
        // どのみち描画は失敗するし、行を足すとエラー行番号がずれて診断を悪くするだけ。
        return DIRECTION_CAPABLE_DIAGRAMS.contains(parsedDiagramKind(puml));
    }

    /** ブロックを開く自由記述 ({@code note over X} / {@code legend} など)。本文は散文。 */
    private static final Pattern FREE_TEXT_BLOCK_START = Pattern.compile(
            "^(note|hnote|rnote|legend|caption|title|header|footer)\\b.*",
            Pattern.CASE_INSENSITIVE);

    /**
     * 自由記述ブロックの終端。<b>種別名を必ず伴う形だけ</b>を受理する
     * ({@code end note} / {@code endnote} / {@code endlegend} …)。素の {@code end} も
     * PlantUML は受けるが、それを終端とみなすと本文の散文に "end" と書いただけで
     * ブロックが閉じたことになり、以降の散文が図の構造として読まれてしまう
     * (Juml のシーケンス図は JavaDoc をそのまま note 本文へ入れる)。
     */
    private static final Pattern FREE_TEXT_BLOCK_END = Pattern.compile(
            "^end\\s*(note|hnote|rnote|legend|caption|title|header|footer)\\s*$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 1 行で完結する自由記述。{@code note over A : text} のようなコロン形式に加え、
     * {@code note "text" as N1} の<b>浮動ノート</b>も 1 行で完結する
     * (これをブロック開始と誤解すると {@code end note} が来ないままファイル末尾まで
     * 全行を捨ててしまい、図種判定の材料が消える)。
     *
     * <p>浮動ノートは色やステレオタイプを続けられる ({@code note "draft" as N1 #pink})。
     * 行末で切っていたため色付き浮動ノートがブロック開始と誤解され、以降が全部
     * 捨てられて<b>どんな図もクラス図と判定される</b>状態だった。</p>
     *
     * <p>コロン形式の判定では {@code ::} を本文の区切りと数えない。数えると
     * {@code note right of Foo::doWork} (メンバー宛ノートのブロック開始) が
     * 「1 行ノート」に見え、<b>ブロック本文の散文がマスクされずに宣言として読まれる</b>。</p>
     */
    private static final Pattern FREE_TEXT_ONE_LINE = Pattern.compile(
            "^(note|hnote|rnote)\\b(?:[^:]|::)*:(?!:).*"
            + "|^(note|hnote|rnote)\\s+\"[^\"]*\"\\s+as\\s+\\S+.*$"
            + "|^(title|caption|header|footer)\\s+\\S.*", Pattern.CASE_INSENSITIVE);

    /** 複数行コメント {@code /' ... '/} の開始・終了。 */
    private static final Pattern BLOCK_COMMENT_START = Pattern.compile("^/'.*");
    private static final Pattern BLOCK_COMMENT_END = Pattern.compile(".*'/\\s*$");

    /**
     * 図種判定に使ってよい「コード行」だけを取り出す (空行・コメント・自由記述の本文を除く)。
     *
     * <p>{@code note} / {@code legend} / {@code title} の本文は<b>利用者が書いた散文</b>であり、
     * 図の構造とは無関係。ここを素通ししていたため、note に
     * 「{@code state transitions are logged}」と書いただけで {@code state ...} 宣言と誤認され、
     * シーケンス図が構造図と判定されて向き指定が注入され、実レンダリングが構文エラーになっていた。
     * 逆に活動図の本文に「{@code start}」と書いても同じ事故が起きうる。</p>
     */

    /**
     * 各行が「図の構造を表すコード行」かを判定したマスクを返す。
     *
     * <p>行を捨てずに位置を保つので、元テキストを書き換える処理
     * ({@code stripBodyDirectionLines}) からも同じ判定を共有できる。</p>
     *
     * <p>ビジュアル設計器の図種判定 ({@code SketchDiagramType}) も同じ穴を持つため
     * この判定を共有する。散文を宣言として読むと、note に「{@code node Server}」と
     * 書いた ER 図が配置図デザイナーで開いてしまう。判定が 2 箇所に分かれると
     * 片方だけ直して再発するので、公開して 1 つに保つ。</p>
     *
     * @param rawLines 元テキストを行分割したもの
     * @return 各行が構造を表すコード行なら {@code true}
     */
    public static boolean[] codeLineMask(String[] rawLines) {
        boolean[] isCode = new boolean[rawLines.length];
        boolean inFreeText = false;
        boolean inBlockComment = false;
        for (int i = 0; i < rawLines.length; i++) {
            String t = rawLines[i].trim();
            if (inBlockComment) {
                if (BLOCK_COMMENT_END.matcher(t).matches()) {
                    inBlockComment = false;
                }
                continue;
            }
            if (t.isEmpty()) {
                continue;
            }
            if (inFreeText) {
                if (FREE_TEXT_BLOCK_END.matcher(t).matches()) {
                    inFreeText = false;
                }
                continue;
            }
            if (BLOCK_COMMENT_START.matcher(t).matches()) {
                // 1 行で閉じる /' ... '/ もある。
                inBlockComment = !BLOCK_COMMENT_END.matcher(t).matches();
                continue;
            }
            if (t.startsWith("'")) {
                continue;
            }
            if (FREE_TEXT_ONE_LINE.matcher(t).matches()) {
                continue;
            }
            if (FREE_TEXT_BLOCK_START.matcher(t).matches()) {
                inFreeText = true;
                continue;
            }
            isCode[i] = true;
        }
        return isCode;
    }

}
