// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml;

import juml.core.formats.uml.DiagramStyle;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * GUI 関連の永続化設定。
 *
 * <p>ウィンドウ位置・サイズと分割ペイン位置、PlantUML 描画スタイルを保持する。
 * フォーマットは Properties (XML) で、未知のキーは無視される。
 * 2.0 で PAD 用のフィールド (フォント / 色 / ツールバー / 保存メニュー設定) は削除された。
 * 旧バージョンが書き出した XML から読み込んだ場合、それらのキーは単に無視される。</p>
 */
public class Setting {

    /** ウィンドウ位置・サイズ */
    private int windowX = -1;
    private int windowY = -1;
    private int windowWidth = 1200;
    private int windowHeight = 800;
    /**
     * ウィンドウ位置を一度でも保存したか。負座標 (プライマリより左/上のモニタ) を
     * 「未保存」の sentinel (-1) と区別するための専用フラグ。false のうちは中央寄せする。
     */
    private boolean windowLocationSaved = false;
    /** 左/右ペインの分割位置 */
    private int mainSplitLocation = -1;
    private int leftSplitLocation = -1;
    /** 終了時にウィンドウが最大化されていたか (次回起動で復元する)。 */
    private boolean windowMaximized = false;
    /** PlantUML 描画スタイル (テーマ・色・フォント等)。 */
    private String styleTheme = "";
    private String styleBackgroundColor = "";
    private String styleFontName = "";
    private int styleFontSize = 0;
    private String styleDirection = DiagramStyle.Direction.DEFAULT.name();
    private String styleLineType = DiagramStyle.LineType.DEFAULT.name();
    private String styleShadowing = DiagramStyle.Shadowing.DEFAULT.name();
    private int styleNodeSep = 0;
    private int styleRankSep = 0;
    private String styleCustomSkinparam = "";
    /** シーケンス図に JavaDoc / コメントを note として表示するか。 */
    private boolean sequenceShowComments = true;
    /** シーケンス図のコメント表示スタイル ("INLINE" | "NOTE")。 */
    private String sequenceCommentStyle = "INLINE";
    /** コメント表示位置 ("AT_CALL_SITE" | "PARTICIPANT_TOP")。 */
    private String sequenceCommentPlacement = "AT_CALL_SITE";
    /** メソッド呼び出しラベルにクラス名を付けるか (例: "Foo.bar()" vs "bar()")。 */
    private boolean sequenceQualifyMethodNames = true;

    /** クラス図に最後に適用したプリセット名 ("MINIMAL" / "BALANCED" / "DETAILED" / "CUSTOM")。 */
    private String classDiagramLastPreset = "BALANCED";
    /** クラス図でフィールドを表示するか。 */
    private boolean classDiagramShowFields = true;
    /** クラス図でメソッドを表示するか。 */
    private boolean classDiagramShowMethods = true;
    /** クラス図でアノテーションを表示するか。 */
    private boolean classDiagramShowAnnotations = true;
    /** public のみ表示する公開フィルタ。 */
    private boolean classDiagramPublicOnly = false;
    /** 外部ライブラリ (java.* / android.* / kotlin.* など) を除外するか。 */
    private boolean classDiagramExcludeExternal = false;
    /** 継承元が JDK 標準/外部ライブラリかをステレオタイプ表示するか。 */
    private boolean classDiagramMarkExternalSupertypes = false;
    /** 関係線を種別ごとに色分け (継承=緑/実装=青/利用=灰破線) して大規模図を追いやすくするか。 */
    private boolean classDiagramColorCodeRelations = false;
    /** コメントの最大文字数。INLINE では省略幅、NOTE では折り返し幅として使用 (0 で非表示)。 */
    private int classDiagramCommentMaxLength = 0;
    /** 非表示アノテーション名の CSV (例: "Override,SuppressWarnings")。 */
    private String classDiagramHiddenAnnotations = "Override,SuppressWarnings";

    /** コールグラフの最大追跡階層数 (1〜10)。 */
    private int callGraphMaxDepth = 4;

    /** アプリ全体の Look &amp; Feel キー ("FLATLAF_LIGHT" / "CROSS_PLATFORM" / "NIMBUS")。 */
    private String lookAndFeel = "FLATLAF_LIGHT";
    /** 起動時に前回開いたプロジェクトを自動で復元するか。 */
    private boolean restoreLastProjectOnStartup = false;
    /** UI 表示言語 ("ja" / "en")。既定は日本語。 */
    private String language = "ja";
    /** 図プレビューの描画品質 (ラスタライズ解像度)。"AUTO" / "LOW" / "HIGH" / "ULTRA"。 */
    private String diagramRenderQuality = "AUTO";
    /** 開けるタブの最大数 (LRU で自動クローズ)。0 以下で無制限。 */
    private int maxDiagramTabs = 20;
    /** 描画済み SVG を保持するタブの最大数。超過分は解放して再フォーカス時に再描画。 */
    private int renderedTabs = 4;
    /** タブ内の上下分割 (プレビュー / ソース) の既定比率 (0.0〜1.0)。 */
    private double tabSplitRatio = 0.7;

    public int getWindowX() { return windowX; }
    public void setWindowX(int windowX) { this.windowX = windowX; }
    public boolean isWindowLocationSaved() { return windowLocationSaved; }
    public void setWindowLocationSaved(boolean saved) { this.windowLocationSaved = saved; }
    public int getWindowY() { return windowY; }
    public void setWindowY(int windowY) { this.windowY = windowY; }
    public int getWindowWidth() { return windowWidth; }
    public void setWindowWidth(int windowWidth) { this.windowWidth = windowWidth; }
    public int getWindowHeight() { return windowHeight; }
    public void setWindowHeight(int windowHeight) { this.windowHeight = windowHeight; }
    public int getMainSplitLocation() { return mainSplitLocation; }
    public void setMainSplitLocation(int mainSplitLocation) { this.mainSplitLocation = mainSplitLocation; }
    public boolean isWindowMaximized() { return windowMaximized; }
    public void setWindowMaximized(boolean windowMaximized) { this.windowMaximized = windowMaximized; }
    public int getLeftSplitLocation() { return leftSplitLocation; }
    public void setLeftSplitLocation(int leftSplitLocation) { this.leftSplitLocation = leftSplitLocation; }

    public boolean isSequenceShowComments() { return sequenceShowComments; }
    public void setSequenceShowComments(boolean v) { this.sequenceShowComments = v; }
    public String getSequenceCommentStyle() { return sequenceCommentStyle; }
    public void setSequenceCommentStyle(String v) {
        this.sequenceCommentStyle = (v == null || v.isEmpty()) ? "INLINE" : v;
    }
    public String getSequenceCommentPlacement() { return sequenceCommentPlacement; }
    public void setSequenceCommentPlacement(String v) {
        this.sequenceCommentPlacement = (v == null || v.isEmpty())
                ? "AT_CALL_SITE" : v;
    }
    public boolean isSequenceQualifyMethodNames() { return sequenceQualifyMethodNames; }
    public void setSequenceQualifyMethodNames(boolean v) {
        this.sequenceQualifyMethodNames = v;
    }

    public String getClassDiagramLastPreset() { return classDiagramLastPreset; }
    public void setClassDiagramLastPreset(String v) {
        this.classDiagramLastPreset = (v == null || v.isEmpty()) ? "BALANCED" : v;
    }
    public boolean isClassDiagramShowFields() { return classDiagramShowFields; }
    public void setClassDiagramShowFields(boolean v) { this.classDiagramShowFields = v; }
    public boolean isClassDiagramShowMethods() { return classDiagramShowMethods; }
    public void setClassDiagramShowMethods(boolean v) { this.classDiagramShowMethods = v; }
    public boolean isClassDiagramShowAnnotations() { return classDiagramShowAnnotations; }
    public void setClassDiagramShowAnnotations(boolean v) {
        this.classDiagramShowAnnotations = v;
    }
    public boolean isClassDiagramPublicOnly() { return classDiagramPublicOnly; }
    public void setClassDiagramPublicOnly(boolean v) { this.classDiagramPublicOnly = v; }
    public boolean isClassDiagramExcludeExternal() { return classDiagramExcludeExternal; }
    public void setClassDiagramExcludeExternal(boolean v) {
        this.classDiagramExcludeExternal = v;
    }
    public boolean isClassDiagramMarkExternalSupertypes() {
        return classDiagramMarkExternalSupertypes;
    }
    public void setClassDiagramMarkExternalSupertypes(boolean v) {
        this.classDiagramMarkExternalSupertypes = v;
    }
    public boolean isClassDiagramColorCodeRelations() {
        return classDiagramColorCodeRelations;
    }
    public void setClassDiagramColorCodeRelations(boolean v) {
        this.classDiagramColorCodeRelations = v;
    }
    public int getClassDiagramCommentMaxLength() { return classDiagramCommentMaxLength; }
    public void setClassDiagramCommentMaxLength(int v) {
        this.classDiagramCommentMaxLength = Math.max(0, v);
    }
    public String getClassDiagramHiddenAnnotations() { return classDiagramHiddenAnnotations; }
    public void setClassDiagramHiddenAnnotations(String v) {
        this.classDiagramHiddenAnnotations = v == null ? "" : v;
    }

    public int getCallGraphMaxDepth() { return callGraphMaxDepth; }
    public void setCallGraphMaxDepth(int v) {
        this.callGraphMaxDepth = Math.max(1, Math.min(10, v));
    }

    public String getLookAndFeel() { return lookAndFeel; }
    public void setLookAndFeel(String v) {
        this.lookAndFeel = (v == null || v.isEmpty()) ? "FLATLAF_LIGHT" : v;
    }
    public boolean isRestoreLastProjectOnStartup() { return restoreLastProjectOnStartup; }
    public void setRestoreLastProjectOnStartup(boolean v) {
        this.restoreLastProjectOnStartup = v;
    }

    public String getLanguage() { return language; }
    /** UI 言語を設定する。"en" 以外 (null / 未知の値含む) は既定の "ja" に丸める。 */
    public void setLanguage(String v) {
        this.language = (v != null && "en".equalsIgnoreCase(v.trim())) ? "en" : "ja";
    }

    /** 図プレビューの描画品質キー ("AUTO" / "LOW" / "HIGH" / "ULTRA")。 */
    public String getDiagramRenderQuality() { return diagramRenderQuality; }
    /** 描画品質を設定する。null/空は既定の "AUTO" に丸める。 */
    public void setDiagramRenderQuality(String v) {
        this.diagramRenderQuality = (v == null || v.isEmpty()) ? "AUTO" : v;
    }

    public int getMaxDiagramTabs() { return maxDiagramTabs; }
    public void setMaxDiagramTabs(int v) {
        this.maxDiagramTabs = Math.max(0, Math.min(100, v));
    }
    public int getRenderedTabs() { return renderedTabs; }
    public void setRenderedTabs(int v) {
        this.renderedTabs = Math.max(1, Math.min(50, v));
    }
    public double getTabSplitRatio() { return tabSplitRatio; }
    public void setTabSplitRatio(double v) {
        this.tabSplitRatio = Math.max(0.1, Math.min(0.9, v));
    }

    /** 永続化済みの値から {@link DiagramStyle} を組み立てて返す。 */
    public DiagramStyle getStyle() {
        DiagramStyle s = new DiagramStyle();
        s.setTheme(styleTheme);
        s.setBackgroundColor(styleBackgroundColor);
        s.setFontName(styleFontName);
        s.setFontSize(styleFontSize);
        s.setDirection(parseDirection(styleDirection));
        s.setLineType(parseLineType(styleLineType));
        s.setShadowing(parseShadowing(styleShadowing));
        s.setNodeSep(styleNodeSep);
        s.setRankSep(styleRankSep);
        s.setCustomSkinparam(styleCustomSkinparam);
        return s;
    }

    /** GUI 等から受け取った {@link DiagramStyle} の値を永続化フィールドへ反映する。 */
    public void setStyle(DiagramStyle style) {
        DiagramStyle s = style != null ? style : DiagramStyle.defaults();
        styleTheme = s.getTheme();
        styleBackgroundColor = s.getBackgroundColor();
        styleFontName = s.getFontName();
        styleFontSize = s.getFontSize();
        styleDirection = s.getDirection().name();
        styleLineType = s.getLineType().name();
        styleShadowing = s.getShadowing().name();
        styleNodeSep = s.getNodeSep();
        styleRankSep = s.getRankSep();
        styleCustomSkinparam = s.getCustomSkinparam();
    }

    /**
     * Properties形式でファイルに保存する。
     * XMLEncoder/XMLDecoderはデシリアライズ攻撃の脆弱性があるため使用しない。
     */
    public void saveToFile(File f) throws IOException {
        if (f == null) {
            throw new IOException("File is null");
        }
        Properties props = new Properties();
        props.setProperty("windowX", Integer.toString(windowX));
        props.setProperty("windowY", Integer.toString(windowY));
        props.setProperty("windowLocationSaved", Boolean.toString(windowLocationSaved));
        props.setProperty("windowWidth", Integer.toString(windowWidth));
        props.setProperty("windowHeight", Integer.toString(windowHeight));
        props.setProperty("mainSplitLocation", Integer.toString(mainSplitLocation));
        props.setProperty("leftSplitLocation", Integer.toString(leftSplitLocation));
        props.setProperty("windowMaximized", Boolean.toString(windowMaximized));
        props.setProperty("style.theme", styleTheme);
        props.setProperty("style.backgroundColor", styleBackgroundColor);
        props.setProperty("style.fontName", styleFontName);
        props.setProperty("style.fontSize", Integer.toString(styleFontSize));
        props.setProperty("style.direction", styleDirection);
        props.setProperty("style.lineType", styleLineType);
        props.setProperty("style.shadowing", styleShadowing);
        props.setProperty("style.nodeSep", Integer.toString(styleNodeSep));
        props.setProperty("style.rankSep", Integer.toString(styleRankSep));
        props.setProperty("style.customSkinparam", styleCustomSkinparam);
        props.setProperty("sequence.showComments", Boolean.toString(sequenceShowComments));
        props.setProperty("sequence.commentStyle", sequenceCommentStyle);
        props.setProperty("sequence.commentPlacement", sequenceCommentPlacement);
        props.setProperty("sequence.qualifyMethodNames",
                Boolean.toString(sequenceQualifyMethodNames));
        props.setProperty("classDiagram.lastPreset", classDiagramLastPreset);
        props.setProperty("classDiagram.showFields",
                Boolean.toString(classDiagramShowFields));
        props.setProperty("classDiagram.showMethods",
                Boolean.toString(classDiagramShowMethods));
        props.setProperty("classDiagram.showAnnotations",
                Boolean.toString(classDiagramShowAnnotations));
        props.setProperty("classDiagram.publicOnly",
                Boolean.toString(classDiagramPublicOnly));
        props.setProperty("classDiagram.excludeExternal",
                Boolean.toString(classDiagramExcludeExternal));
        props.setProperty("classDiagram.markExternalSupertypes",
                Boolean.toString(classDiagramMarkExternalSupertypes));
        props.setProperty("classDiagram.colorCodeRelations",
                Boolean.toString(classDiagramColorCodeRelations));
        props.setProperty("classDiagram.commentMaxLength",
                Integer.toString(classDiagramCommentMaxLength));
        // 旧既定値 60 → 0 の一括移行を済ませた印。これが立っていれば以後 60 は
        // ユーザ指定値として尊重する (loadFromFile 参照)。
        props.setProperty("classDiagram.commentMaxLength.migrated", "true");
        props.setProperty("classDiagram.hiddenAnnotations",
                classDiagramHiddenAnnotations);
        props.setProperty("callGraph.maxDepth", Integer.toString(callGraphMaxDepth));
        props.setProperty("app.lookAndFeel", lookAndFeel);
        props.setProperty("app.restoreLastProjectOnStartup",
                Boolean.toString(restoreLastProjectOnStartup));
        props.setProperty("app.language", language);
        props.setProperty("app.diagramRenderQuality", diagramRenderQuality);
        props.setProperty("app.maxDiagramTabs", Integer.toString(maxDiagramTabs));
        props.setProperty("app.renderedTabs", Integer.toString(renderedTabs));
        props.setProperty("app.tabSplitRatio", Double.toString(tabSplitRatio));

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(f))) {
            props.storeToXML(bos, "Juml Settings");
        }
    }

    /**
     * Properties形式でファイルから読み込む。
     * 不正な値は無視してデフォルト値を使用する。
     * 旧バージョンが書き出した PAD 関連キー (editorFont.* / viewFont.* / viewColor.rgb
     * / disableSaveMenu / disableToolbar) は読み込み時に無視される。
     * 新規追加された {@code style.*} キーが無い場合は既定スタイル ((None) テーマ) を用いる。
     */
    public static Setting loadFromFile(File f) throws IOException {
        Properties props = new Properties();
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(f))) {
            props.loadFromXML(bis);
        }

        Setting s = new Setting();
        s.windowX = parseIntSafe(props.getProperty("windowX"), -1);
        s.windowY = parseIntSafe(props.getProperty("windowY"), -1);
        s.windowLocationSaved = parseBooleanSafe(
                props.getProperty("windowLocationSaved"), false);
        s.windowWidth = parseIntSafe(props.getProperty("windowWidth"), 1200);
        s.windowHeight = parseIntSafe(props.getProperty("windowHeight"), 800);
        s.mainSplitLocation = parseIntSafe(props.getProperty("mainSplitLocation"), -1);
        s.leftSplitLocation = parseIntSafe(props.getProperty("leftSplitLocation"), -1);
        s.windowMaximized = parseBooleanSafe(props.getProperty("windowMaximized"), false);
        s.styleTheme = stringOrEmpty(props.getProperty("style.theme"));
        s.styleBackgroundColor = stringOrEmpty(props.getProperty("style.backgroundColor"));
        s.styleFontName = stringOrEmpty(props.getProperty("style.fontName"));
        s.styleFontSize = parseIntSafe(props.getProperty("style.fontSize"), 0);
        s.styleDirection = stringOrDefault(props.getProperty("style.direction"),
                DiagramStyle.Direction.DEFAULT.name());
        s.styleLineType = stringOrDefault(props.getProperty("style.lineType"),
                DiagramStyle.LineType.DEFAULT.name());
        s.styleShadowing = stringOrDefault(props.getProperty("style.shadowing"),
                DiagramStyle.Shadowing.DEFAULT.name());
        s.styleNodeSep = parseIntSafe(props.getProperty("style.nodeSep"), 0);
        s.styleRankSep = parseIntSafe(props.getProperty("style.rankSep"), 0);
        s.styleCustomSkinparam = stringOrEmpty(props.getProperty("style.customSkinparam"));
        s.sequenceShowComments = parseBooleanSafe(
                props.getProperty("sequence.showComments"), true);
        s.sequenceCommentStyle = stringOrDefault(
                props.getProperty("sequence.commentStyle"), "INLINE");
        s.sequenceCommentPlacement = stringOrDefault(
                props.getProperty("sequence.commentPlacement"), "AT_CALL_SITE");
        s.sequenceQualifyMethodNames = parseBooleanSafe(
                props.getProperty("sequence.qualifyMethodNames"), true);
        s.classDiagramLastPreset = stringOrDefault(
                props.getProperty("classDiagram.lastPreset"), "BALANCED");
        s.classDiagramShowFields = parseBooleanSafe(
                props.getProperty("classDiagram.showFields"), true);
        s.classDiagramShowMethods = parseBooleanSafe(
                props.getProperty("classDiagram.showMethods"), true);
        s.classDiagramShowAnnotations = parseBooleanSafe(
                props.getProperty("classDiagram.showAnnotations"), true);
        s.classDiagramPublicOnly = parseBooleanSafe(
                props.getProperty("classDiagram.publicOnly"), false);
        s.classDiagramExcludeExternal = parseBooleanSafe(
                props.getProperty("classDiagram.excludeExternal"), false);
        s.classDiagramMarkExternalSupertypes = parseBooleanSafe(
                props.getProperty("classDiagram.markExternalSupertypes"), false);
        s.classDiagramColorCodeRelations = parseBooleanSafe(
                props.getProperty("classDiagram.colorCodeRelations"), false);
        // コメント最大長は既定を 60 → 0 (無制限 = 全文表示) に変更した。旧バージョンが
        // 既定値のまま書き出した 60 が残っていると全文表示にならないため、未移行の
        // 設定ファイルに限り旧既定値 60 を新既定値 0 へ移行する (それ以外のユーザ指定値、
        // および移行後に明示設定された 60 はそのまま尊重する)。
        int storedCommentMax = parseIntSafe(
                props.getProperty("classDiagram.commentMaxLength"), 0);
        boolean commentMaxMigrated = parseBooleanSafe(
                props.getProperty("classDiagram.commentMaxLength.migrated"), false);
        s.classDiagramCommentMaxLength =
                (!commentMaxMigrated && storedCommentMax == 60) ? 0 : storedCommentMax;
        s.classDiagramHiddenAnnotations = stringOrDefault(
                props.getProperty("classDiagram.hiddenAnnotations"),
                "Override,SuppressWarnings");
        s.callGraphMaxDepth = parseIntSafe(props.getProperty("callGraph.maxDepth"), 4);
        s.lookAndFeel = stringOrDefault(props.getProperty("app.lookAndFeel"), "FLATLAF_LIGHT");
        s.restoreLastProjectOnStartup = parseBooleanSafe(
                props.getProperty("app.restoreLastProjectOnStartup"), false);
        s.setLanguage(stringOrDefault(props.getProperty("app.language"), "ja"));
        s.diagramRenderQuality = stringOrDefault(
                props.getProperty("app.diagramRenderQuality"), "AUTO");
        s.maxDiagramTabs = parseIntSafe(props.getProperty("app.maxDiagramTabs"), 20);
        s.renderedTabs = parseIntSafe(props.getProperty("app.renderedTabs"), 4);
        s.tabSplitRatio = parseDoubleSafe(props.getProperty("app.tabSplitRatio"), 0.7);

        return s;
    }

    private static boolean parseBooleanSafe(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = value.trim().toLowerCase();
        if ("true".equals(s)) return true;
        if ("false".equals(s)) return false;
        return defaultValue;
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseDoubleSafe(String value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String stringOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static DiagramStyle.Direction parseDirection(String name) {
        if (name == null || name.isEmpty()) {
            return DiagramStyle.Direction.DEFAULT;
        }
        try {
            return DiagramStyle.Direction.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DiagramStyle.Direction.DEFAULT;
        }
    }

    private static DiagramStyle.LineType parseLineType(String name) {
        if (name == null || name.isEmpty()) {
            return DiagramStyle.LineType.DEFAULT;
        }
        try {
            return DiagramStyle.LineType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DiagramStyle.LineType.DEFAULT;
        }
    }

    private static DiagramStyle.Shadowing parseShadowing(String name) {
        if (name == null || name.isEmpty()) {
            return DiagramStyle.Shadowing.DEFAULT;
        }
        try {
            return DiagramStyle.Shadowing.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DiagramStyle.Shadowing.DEFAULT;
        }
    }
}
