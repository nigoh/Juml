// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * アプリ全体で使うエラー ID のカタログ。
 *
 * <p>クローズド環境 (外部アクセス不可) での運用を想定し、画面に表示された ID を
 * 目視・口頭で伝達するだけで「どのエラーか」「どう対処するか」が一意に決まる
 * ことを目的とする。ID は {@code 領域プレフィックス + 3 桁連番}
 * (例: {@code UML-R001}, {@code PRJ-005})。ERROR / WARN の別は ID に含めず、
 * ログのレベル欄で表現する。</p>
 *
 * <p>各コードの要約と対処法は {@link Messages} の
 * {@code errcode.<ID>.summary} / {@code errcode.<ID>.remedy} キーで日英管理する。
 * カタログの詳細版 {@code docs/errors.md} は本 enum から自動生成される
 * (乖離はテストで検出)。採番・追加のルールは
 * {@code .claude/rules/error-logging.md} を参照。</p>
 */
public enum ErrorCode {

    /**
     * 「ID なし」を表すセンチネル。進捗・情報通知など、エラーではない
     * {@link ErrorListener} 経由のメッセージに使う。カタログには載らない。
     */
    NONE(Area.SYS, ""),

    // ── UML_RENDER ──
    /** 生成された PlantUML の構文エラーで図を描画できませんでした。 */
    UML_R001(Area.UML_RENDER, "UML-R001"),
    /** レイアウトエンジンが図の描画に失敗しました。 */
    UML_R002(Area.UML_RENDER, "UML-R002"),
    /** メモリ不足により図の描画に失敗しました。 */
    UML_R003(Area.UML_RENDER, "UML-R003"),
    /** 描画結果の処理中に予期しないエラーが発生しました。 */
    UML_R004(Area.UML_RENDER, "UML-R004"),
    /** 失敗した PlantUML の保存に失敗しました。 */
    UML_R005(Area.UML_RENDER, "UML-R005"),
    /** PNG 描画が PlantUML のエラー画像を返しました。 */
    UML_R006(Area.UML_RENDER, "UML-R006"),
    /** 図の描画に失敗しました（原因未分類）。 */
    UML_R007(Area.UML_RENDER, "UML-R007"),
    /** 設定された Graphviz (dot) が実行できません。 */
    UML_R008(Area.UML_RENDER, "UML-R008"),

    // ── UML_EDITOR ──
    /** 編集中の PlantUML に構文エラーがあります。 */
    UML_E001(Area.UML_EDITOR, "UML-E001"),
    /** 編集中の PlantUML のレイアウトに失敗しました。 */
    UML_E002(Area.UML_EDITOR, "UML-E002"),
    /** PlantUML ファイルの保存に失敗しました。 */
    UML_E003(Area.UML_EDITOR, "UML-E003"),
    /** PlantUML ファイルを開けませんでした。 */
    UML_E004(Area.UML_EDITOR, "UML-E004"),

    // ── DIAG ──
    /** 図設定の読込に失敗したため既定値で継続します。 */
    DIAG_001(Area.DIAG, "DIAG-001"),
    /** スコープフィルタのクラス名正規表現が不正です。 */
    DIAG_002(Area.DIAG, "DIAG-002"),
    /** Doxygen の実行または解析に失敗しました。 */
    DIAG_003(Area.DIAG, "DIAG-003"),

    // ── PRJ ──
    /** プロジェクトの解析に失敗しました。 */
    PRJ_001(Area.PRJ, "PRJ-001"),
    /** アーカイブ (jar/zip 等) の読込に失敗しました。 */
    PRJ_002(Area.PRJ, "PRJ-002"),
    /** Android.bp の走査に失敗したため Soong モジュールなしで継続します。 */
    PRJ_003(Area.PRJ, "PRJ-003"),
    /** ソースファイルの読込に失敗しました。 */
    PRJ_004(Area.PRJ, "PRJ-004"),
    /** Java ソースの解析でエラーが発生しました。 */
    PRJ_005(Area.PRJ, "PRJ-005"),
    /** AIDL ソースが文法から逸脱しています。 */
    PRJ_006(Area.PRJ, "PRJ-006"),
    /** Android リソース XML の解析で警告が発生しました。 */
    PRJ_007(Area.PRJ, "PRJ-007"),
    /** Android リソース XML の解析に失敗しました。 */
    PRJ_008(Area.PRJ, "PRJ-008"),
    /** Gradle / Version Catalog の解析に失敗しました。 */
    PRJ_009(Area.PRJ, "PRJ-009"),
    /** Doxygen XML の解析に失敗しました。 */
    PRJ_010(Area.PRJ, "PRJ-010"),
    /** smali ファイルの解析に失敗しました。 */
    PRJ_011(Area.PRJ, "PRJ-011"),
    /** 解析インデックスの更新に失敗しました。 */
    PRJ_012(Area.PRJ, "PRJ-012"),

    // ── ANA ──
    /** 参照検索に失敗しました。 */
    ANA_001(Area.ANA, "ANA-001"),
    /** メソッド差分の解析に失敗しました。 */
    ANA_002(Area.ANA, "ANA-002"),
    /** 差分レポートの保存に失敗しました。 */
    ANA_003(Area.ANA, "ANA-003"),
    /** 影響範囲の解析に失敗しました。 */
    ANA_004(Area.ANA, "ANA-004"),
    /** インサイトの解析に失敗しました。 */
    ANA_005(Area.ANA, "ANA-005"),
    /** インサイトレポートの保存に失敗しました。 */
    ANA_006(Area.ANA, "ANA-006"),

    // ── CACHE ──
    /** 解析キャッシュの読込に失敗しました。 */
    CACHE_001(Area.CACHE, "CACHE-001"),
    /** 解析キャッシュの保存に失敗しました。 */
    CACHE_002(Area.CACHE, "CACHE-002"),
    /** 旧形式キャッシュの退避に失敗しました。 */
    CACHE_003(Area.CACHE, "CACHE-003"),

    // ── EXP ──
    /** 図のエクスポートに失敗しました。 */
    EXP_001(Area.EXP, "EXP-001"),
    /** クリップボードへのコピーに失敗しました。 */
    EXP_002(Area.EXP, "EXP-002"),
    /** 一覧のエクスポートに失敗しました。 */
    EXP_003(Area.EXP, "EXP-003"),
    /** メンバー一覧ワークブックの出力に失敗しました。 */
    EXP_004(Area.EXP, "EXP-004"),
    /** フォルダ別クラス図の一括出力に失敗しました。 */
    EXP_005(Area.EXP, "EXP-005"),
    /** PNG のバックグラウンド出力に失敗しました。 */
    EXP_006(Area.EXP, "EXP-006"),

    // ── NOTE ──
    /** ノート付き PNG の出力に失敗しました。 */
    NOTE_001(Area.NOTE, "NOTE-001"),

    // ── CFG ──
    /** 設定の保存に失敗しました。 */
    CFG_001(Area.CFG, "CFG-001"),
    /** プロジェクト履歴データベースの初期化に失敗しました。 */
    CFG_002(Area.CFG, "CFG-002"),
    /** プロジェクト履歴データベースの操作に失敗しました。 */
    CFG_003(Area.CFG, "CFG-003"),

    // ── SYS ──
    /** 標準エラー出力を検出しました（未分類）。 */
    SYS_001(Area.SYS, "SYS-001"),
    /** 未捕捉の例外が発生しました。 */
    SYS_002(Area.SYS, "SYS-002"),
    /** 外部アプリケーションの起動に失敗しました。 */
    SYS_003(Area.SYS, "SYS-003"),
    /** 不明なコマンドラインオプションです。 */
    SYS_004(Area.SYS, "SYS-004"),
    /** コマンドラインオプションに必要な値が指定されていません。 */
    SYS_005(Area.SYS, "SYS-005");

    /** エラーの発生領域。ID のプレフィックスに対応する。 */
    public enum Area {
        /** PlantUML 描画 ({@code UML-R})。 */
        UML_RENDER("UML-R"),
        /** UML エディタ ({@code UML-E})。 */
        UML_EDITOR("UML-E"),
        /** 図生成サービス・タブ管理 ({@code DIAG})。 */
        DIAG("DIAG"),
        /** プロジェクト読込・解析 ({@code PRJ})。 */
        PRJ("PRJ"),
        /** 解析パネル ({@code ANA})。 */
        ANA("ANA"),
        /** 解析キャッシュ ({@code CACHE})。 */
        CACHE("CACHE"),
        /** エクスポート・コピー ({@code EXP})。 */
        EXP("EXP"),
        /** ノート機能 ({@code NOTE})。 */
        NOTE("NOTE"),
        /** 設定・永続化 ({@code CFG})。 */
        CFG("CFG"),
        /** 基盤・未分類 ({@code SYS})。 */
        SYS("SYS");

        private final String prefix;

        Area(String prefix) {
            this.prefix = prefix;
        }

        /** ID のプレフィックス (例: {@code UML-R})。 */
        public String getPrefix() {
            return prefix;
        }

        /** 領域の表示名 (言語設定に追従)。 */
        public String displayName() {
            return Messages.get("errcode.area." + prefix);
        }
    }

    /** ID 文字列 → コードの索引 ({@link #fromId(String)} 用)。 */
    private static final Map<String, ErrorCode> BY_ID;

    static {
        Map<String, ErrorCode> m = new LinkedHashMap<>();
        for (ErrorCode c : values()) {
            if (c.hasId()) {
                m.put(c.id, c);
            }
        }
        BY_ID = Collections.unmodifiableMap(m);
    }

    private final Area area;
    private final String id;

    ErrorCode(Area area, String id) {
        this.area = area;
        this.id = id;
    }

    /** 表示・転記用の ID (例: {@code UML-R001})。{@link #NONE} は空文字。 */
    public String getId() {
        return id;
    }

    /** 発生領域。 */
    public Area getArea() {
        return area;
    }

    /** {@link #NONE} 以外 (= カタログに載る実 ID) なら true。 */
    public boolean hasId() {
        return !id.isEmpty();
    }

    /** ログ行に埋め込む形式 (例: {@code [UML-R001]})。ID なしは {@code [-]}。 */
    public String tag() {
        return hasId() ? "[" + id + "]" : "[-]";
    }

    /** 要約 (言語設定に追従)。{@link #NONE} は空文字。 */
    public String summary() {
        return hasId() ? Messages.get("errcode." + id + ".summary") : "";
    }

    /** 対処法 (言語設定に追従)。{@link #NONE} は空文字。 */
    public String remedy() {
        return hasId() ? Messages.get("errcode." + id + ".remedy") : "";
    }

    /**
     * ID 文字列からコードを引く (大文字小文字は無視)。
     *
     * @return 対応するコード。未知の ID や空文字は null。
     */
    public static ErrorCode fromId(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        return BY_ID.get(id.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
