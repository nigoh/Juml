// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.formats.uml;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PlantUML 描画スタイル設定。
 *
 * <p>{@link PlantUmlRenderer} が {@code @startuml} 直後に挿入する
 * {@code !theme} / {@code skinparam} 行を生成する値オブジェクト。
 * 各フィールドは未指定 (null / 空文字 / 0 / {@link Direction#DEFAULT}) を取れて、
 * その場合は対応する行を出力しない。</p>
 *
 * <p>本オブジェクトは GUI / 設定永続化 (Properties XML) / レンダラの 3 者で共有される。</p>
 */
public final class DiagramStyle {

    /** PlantUML のファイル読み込み・プリプロセッサ系ディレクティブ（危険）を除去するパターン。 */
    private static final Pattern UNSAFE_DIRECTIVE =
            Pattern.compile("(?im)^[ \\t]*!(?:include|pragma|define|undef|ifdef|ifndef|else|endif|import|log|dump_memory|endprocedure|procedure|function|endfunction|return|call|startsub|endsub|stdlib)[^\\n]*", Pattern.MULTILINE);

    /** 図の描画方向。 */
    public enum Direction {
        /** PlantUML デフォルト (上から下)。指定行を出さない。 */
        DEFAULT,
        /** {@code left to right direction} を出力。 */
        LEFT_TO_RIGHT,
        /** {@code top to bottom direction} を出力 (明示)。 */
        TOP_TO_BOTTOM
    }

    /**
     * 関連線（リンク）の描画スタイル。クラス図などで線が交差・湾曲して見づらい場合に
     * 直交線（ortho）や折れ線（polyline）へ切り替えることで可読性を上げられる。
     */
    public enum LineType {
        /** PlantUML デフォルト（曲線 spline）。指定行を出さない。 */
        DEFAULT,
        /** {@code skinparam linetype polyline}（折れ線）。 */
        POLYLINE,
        /** {@code skinparam linetype ortho}（直交線）。交差が減り読みやすい。 */
        ORTHO,
        /** {@code skinparam linetype spline}（曲線・明示指定）。 */
        SPLINE
    }

    /** 影 (shadowing) の有無。DEFAULT は指定行を出さず PlantUML 既定に従う。 */
    public enum Shadowing {
        /** 指定なし（PlantUML 既定）。 */
        DEFAULT,
        /** {@code skinparam shadowing true}。 */
        ON,
        /** {@code skinparam shadowing false}（フラットで見やすい）。 */
        OFF
    }

    /**
     * モノクロ（グレースケール）描画。白黒印刷やカラーに依存しない資料向け。
     * DEFAULT は指定行を出さず PlantUML 既定（カラー）に従う。
     */
    public enum Monochrome {
        /** 指定なし（カラー）。 */
        DEFAULT,
        /** {@code skinparam monochrome true}（グレースケール）。 */
        ON,
        /** {@code skinparam monochrome reverse}（白黒反転＝暗背景向け）。 */
        REVERSE
    }

    private String theme = "";
    private String backgroundColor = "";
    private String fontName = "";
    private int fontSize = 0;
    private Direction direction = Direction.DEFAULT;
    private LineType lineType = LineType.DEFAULT;
    private Shadowing shadowing = Shadowing.DEFAULT;
    private int nodeSep = 0;
    private int rankSep = 0;
    private String customSkinparam = "";
    /** 図の下部中央に出すキャプション文字列（空で非表示）。全図種共通のブランディング/文脈付与用。 */
    private String caption = "";
    /** モノクロ（グレースケール）描画。DEFAULT は指定行を出さない。 */
    private Monochrome monochrome = Monochrome.DEFAULT;
    /** ボックスの角丸半径 (px)。0 で指定行を出さない (PlantUML 既定)。 */
    private int roundCorner = 0;

    /** 全フィールド未指定の既定スタイル。 */
    public static DiagramStyle defaults() {
        return new DiagramStyle();
    }

    /**
     * 可読性を優先した推奨スタイルを返す（フォント / 背景は未指定のまま環境依存に委ねる）。
     *
     * <p>影なし・直交線・余白広めにすることで、関連の多いクラス図などが読みやすくなる。
     * Style 設定ダイアログの「可読性優先」ボタンの基準として用いる。</p>
     */
    public static DiagramStyle readable() {
        DiagramStyle s = new DiagramStyle();
        s.theme = "plain";
        s.lineType = LineType.ORTHO;
        s.shadowing = Shadowing.OFF;
        s.nodeSep = 50;
        s.rankSep = 60;
        return s;
    }

    /** 値をすべてコピーした独立インスタンスを返す。 */
    public DiagramStyle copy() {
        DiagramStyle s = new DiagramStyle();
        s.theme = this.theme;
        s.backgroundColor = this.backgroundColor;
        s.fontName = this.fontName;
        s.fontSize = this.fontSize;
        s.direction = this.direction;
        s.lineType = this.lineType;
        s.shadowing = this.shadowing;
        s.nodeSep = this.nodeSep;
        s.rankSep = this.rankSep;
        s.customSkinparam = this.customSkinparam;
        s.caption = this.caption;
        s.monochrome = this.monochrome;
        s.roundCorner = this.roundCorner;
        return s;
    }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme == null ? "" : theme; }

    public String getBackgroundColor() { return backgroundColor; }
    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor == null ? "" : backgroundColor;
    }

    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName == null ? "" : fontName; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) { this.fontSize = Math.max(0, fontSize); }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) {
        this.direction = direction == null ? Direction.DEFAULT : direction;
    }

    public LineType getLineType() { return lineType; }
    public void setLineType(LineType lineType) {
        this.lineType = lineType == null ? LineType.DEFAULT : lineType;
    }

    public Shadowing getShadowing() { return shadowing; }
    public void setShadowing(Shadowing shadowing) {
        this.shadowing = shadowing == null ? Shadowing.DEFAULT : shadowing;
    }

    public int getNodeSep() { return nodeSep; }
    public void setNodeSep(int nodeSep) { this.nodeSep = Math.max(0, nodeSep); }

    public int getRankSep() { return rankSep; }
    public void setRankSep(int rankSep) { this.rankSep = Math.max(0, rankSep); }

    public String getCustomSkinparam() { return customSkinparam; }
    public void setCustomSkinparam(String customSkinparam) {
        this.customSkinparam = customSkinparam == null ? "" : customSkinparam;
    }

    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption == null ? "" : caption; }

    public Monochrome getMonochrome() { return monochrome; }
    public void setMonochrome(Monochrome monochrome) {
        this.monochrome = monochrome == null ? Monochrome.DEFAULT : monochrome;
    }

    public int getRoundCorner() { return roundCorner; }
    public void setRoundCorner(int roundCorner) { this.roundCorner = Math.max(0, roundCorner); }

    /**
     * PlantUML に挿入する {@code !theme} / {@code skinparam} 等のプレリュード行を返す。
     * フィールドが未指定の項目は対応する行を出力しない。
     * 戻り値は改行終端 (空でない場合)。空文字を返すこともある。
     */
    public String toPlantUmlPrelude() {
        return toPlantUmlPrelude(true);
    }

    /**
     * {@link #toPlantUmlPrelude()} の向き指定 ({@code left to right direction} /
     * {@code top to bottom direction}) を出力するか選べるオーバーロード。
     *
     * <p>シーケンス図・アクティビティ図は向き指定ディレクティブを受け付けず
     * PlantUML 構文エラーになるため、これらの図種では {@code includeDirection=false}
     * を渡して向き指定を抑制する。レンダラ ({@code PlantUmlRenderer#injectLayout})
     * が図種を判別して呼び分ける。</p>
     *
     * @param includeDirection 向き指定ディレクティブを出力するか
     */
    public String toPlantUmlPrelude(boolean includeDirection) {
        StringBuilder sb = new StringBuilder();
        if (!theme.isEmpty()) {
            sb.append("!theme ").append(theme).append('\n');
        }
        if (!backgroundColor.isEmpty()) {
            sb.append("skinparam backgroundColor ").append(backgroundColor).append('\n');
        }
        if (!fontName.isEmpty()) {
            sb.append("skinparam defaultFontName ").append(fontName).append('\n');
        }
        if (fontSize > 0) {
            sb.append("skinparam defaultFontSize ").append(fontSize).append('\n');
        }
        if (includeDirection) {
            switch (direction) {
                case LEFT_TO_RIGHT:
                    sb.append("left to right direction\n");
                    break;
                case TOP_TO_BOTTOM:
                    sb.append("top to bottom direction\n");
                    break;
                default:
                    break;
            }
        }
        switch (lineType) {
            case POLYLINE:
                sb.append("skinparam linetype polyline\n");
                break;
            case ORTHO:
                sb.append("skinparam linetype ortho\n");
                break;
            case SPLINE:
                sb.append("skinparam linetype spline\n");
                break;
            default:
                break;
        }
        switch (shadowing) {
            case ON:
                sb.append("skinparam shadowing true\n");
                break;
            case OFF:
                sb.append("skinparam shadowing false\n");
                break;
            default:
                break;
        }
        switch (monochrome) {
            case ON:
                sb.append("skinparam monochrome true\n");
                break;
            case REVERSE:
                sb.append("skinparam monochrome reverse\n");
                break;
            default:
                break;
        }
        if (roundCorner > 0) {
            sb.append("skinparam roundcorner ").append(roundCorner).append('\n');
        }
        if (nodeSep > 0) {
            sb.append("skinparam nodesep ").append(nodeSep).append('\n');
        }
        if (rankSep > 0) {
            sb.append("skinparam ranksep ").append(rankSep).append('\n');
        }
        if (!customSkinparam.isEmpty()) {
            // ユーザ入力の改行は LF に正規化し、危険なプリプロセッサ系ディレクティブを除去して埋め込む。
            String normalized = customSkinparam.replace("\r\n", "\n").replace('\r', '\n');
            String sanitized = UNSAFE_DIRECTIVE.matcher(normalized).replaceAll("");
            sb.append(sanitized);
            if (!sanitized.endsWith("\n")) {
                sb.append('\n');
            }
        }
        if (!caption.isEmpty()) {
            // 図下部中央のキャプション。改行と <> & をタグ誤認しないよう 1 行化 + エスケープする。
            String oneLine = caption.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
            sb.append("caption ")
              .append(PlantUmlCommentFormatter.escapeLabel(oneLine)).append('\n');
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiagramStyle)) return false;
        DiagramStyle that = (DiagramStyle) o;
        return fontSize == that.fontSize
                && nodeSep == that.nodeSep
                && rankSep == that.rankSep
                && roundCorner == that.roundCorner
                && Objects.equals(theme, that.theme)
                && Objects.equals(backgroundColor, that.backgroundColor)
                && Objects.equals(fontName, that.fontName)
                && direction == that.direction
                && lineType == that.lineType
                && shadowing == that.shadowing
                && monochrome == that.monochrome
                && Objects.equals(customSkinparam, that.customSkinparam)
                && Objects.equals(caption, that.caption);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme, backgroundColor, fontName, fontSize, direction,
                lineType, shadowing, nodeSep, rankSep, customSkinparam, caption,
                monochrome, roundCorner);
    }
}
