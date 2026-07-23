// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml.sketch;

import java.util.regex.Pattern;

/**
 * GUI デザイナーが扱う図種。PlantUML テキストの内容から自動判定する。
 *
 * <p>まず {@code usecase} / {@code component} キーワード (いずれも一意) があれば
 * ユースケース図 / コンポーネント図と確定する。次に一意マーカーを先取りで判定する:
 * {@code object 名前} 宣言 → オブジェクト図、ER 図固有マーカー (crow's-foot 関係演算子
 * {@code ||--o{} 等、または {@code entity "..." {} の列ブロック + {@code hide circle}) → ER 図、
 * {@code node} / {@code artifact} / {@code cloud} 宣言 → 配置図 ({@code entity} / {@code database}
 * はシーケンス図と共有するため単独では判定材料にしない)。無ければ行単位の先勝ちで:
 * アクティビティ図の構文 ({@code start} / {@code :action;} /
 * {@code if (...) then}) → 状態遷移図の構文 ({@code state X} / {@code [*] --> X}) →
 * クラス宣言 ({@code class} / {@code interface} / {@code enum}) → シーケンス図の構文
 * ({@code participant} / {@code A -> B} / {@code activate}) の順で調べ、どれにも該当
 * しなければ既定のクラス図として扱う (未対応構文はクラス図コーデックが編集ロックで保全する)。</p>
 */
public enum SketchDiagramType {

    /** クラス図 (既定)。 */
    CLASS,
    /** シーケンス図。 */
    SEQUENCE,
    /** アクティビティ図 (新形式構文)。 */
    ACTIVITY,
    /** 状態遷移図。 */
    STATE,
    /** ユースケース図。 */
    USECASE,
    /** コンポーネント図。 */
    COMPONENT,
    /** オブジェクト図。 */
    OBJECT,
    /** ER (エンティティ関連) 図。 */
    ER,
    /** 配置図 (デプロイ図)。 */
    DEPLOYMENT;

    /**
     * ユースケース図に固有の行。{@code usecase} キーワードは他図種と衝突しないため、
     * これが 1 行でもあればユースケース図と確定できる ({@code actor} はシーケンス図と
     * 共有するため単独では判定材料にしない)。
     */
    private static final Pattern USECASE_LINE = Pattern.compile("^usecase\\b.*$");
    /**
     * コンポーネント図に固有の行。{@code component} キーワード、または単独の短縮形
     * {@code [Id]} (Id は識別子)。{@code [*]} は識別子でないので状態図と衝突しない。
     */
    private static final Pattern COMPONENT_LINE = Pattern.compile(
            "^(component\\b.*|\\[[A-Za-z_$][\\w$]*\\]\\s*)$");
    /**
     * オブジェクト図に固有の行。{@code object 名前} 宣言は他図種と衝突しないため、これが
     * 1 行でもあればオブジェクト図と確定できる。
     */
    private static final Pattern OBJECT_LINE = Pattern.compile(
            "^object\\s+[A-Za-z_$].*$");
    /**
     * 配置図に固有の宣言行。{@code node} / {@code artifact} / {@code cloud} は他図種と
     * 衝突しないため、これらが 1 行でもあれば配置図と確定できる。{@code database} は
     * シーケンス図の参加者宣言と共有するため、単独では判定材料にしない。
     */
    private static final Pattern DEPLOYMENT_LINE = Pattern.compile(
            "^(node|artifact|cloud)\\b.*$");

    /** アクティビティ図に固有の行 ({@code start} / {@code :action;} / {@code if} など)。 */
    private static final Pattern ACTIVITY_LINE = Pattern.compile(
            "^(start|stop|end|:.*;|if\\s*\\(.*|repeat\\b.*|while\\s*\\(.*|fork\\b.*)$");
    /**
     * 状態遷移図に固有の行。{@code state} 宣言、または初期/終了の擬似状態 {@code [*]} を
     * 端点に含む遷移。素の {@code A --> B} はクラス図の関連と曖昧なため判定材料にしない。
     */
    private static final Pattern STATE_LINE = Pattern.compile(
            "^(state\\s+[A-Za-z_$].*|\\[\\*\\]\\s*-->.*|.*-->\\s*\\[\\*\\].*)$");
    /**
     * ER 図に固有の crow's-foot (IE) リレーション演算子 ({@code ||--o{} 等)。左右の
     * カーディナリティトークン ({@code |} / {@code o} / {@code {} / {@code }} の組) は
     * クラス図の関係表記 ({@code <|--} / {@code o--} / {@code *--} / {@code -->}) と
     * 一致しないため、これが 1 つでもあれば ER 図と確定できる。
     */
    private static final Pattern ER_RELATION = Pattern.compile(
            "(\\|\\||\\|o|\\}o|\\}\\|)--(\\|\\||o\\||o\\{|\\|\\{)");
    /** ER 図の列ブロックを開くエンティティ宣言 ({@code entity ... {})。 */
    private static final Pattern ER_ENTITY_BLOCK = Pattern.compile("^entity\\b.*\\{\\s*$");
    /** ER 図でエンティティを表として描かせる {@code hide circle} 指令。 */
    private static final Pattern ER_HIDE_CIRCLE = Pattern.compile("^hide\\s+circle\\b.*$");
    /** クラス図に固有の宣言行。 */
    private static final Pattern CLASS_LINE = Pattern.compile(
            "^(abstract\\s+)?(class|interface|enum)\\b.*$");
    /**
     * シーケンス図に固有の行。矢印は {@code ->} 系のみ ({@code -->} はクラス図の関連と
     * 曖昧なため判定材料にしない。PlantUML 自身も同様の曖昧さを他の行で解決している)。
     */
    private static final Pattern SEQUENCE_LINE = Pattern.compile(
            "^(participant\\b.*|actor\\b.*|boundary\\b.*|control\\b.*|entity\\b.*"
                    + "|database\\b.*|queue\\b.*|collections\\b.*"
                    + "|activate\\b.*|deactivate\\b.*|autonumber\\b.*"
                    + "|alt\\b.*|opt\\b.*|loop\\b.*|par\\b.*|group\\b.*"
                    + "|[A-Za-z_$][\\w$.]*\\s*(->>?|-->>)\\s*[A-Za-z_$].*)$");

    /** PlantUML テキストから図種を判定する。 */
    public static SketchDiagramType detect(String text) {
        String[] lines = (text == null ? "" : text).split("\n", -1);
        // usecase キーワードは他図種と衝突しないため、1 行でもあればユースケース図と確定する
        // (actor はシーケンス図と共有するため、行順に依らずここで先取りして判定する)。
        for (String raw : lines) {
            if (USECASE_LINE.matcher(raw.trim()).matches()) {
                return USECASE;
            }
        }
        // component キーワード / [Id] も他図種と衝突しないため先取りで判定する。
        for (String raw : lines) {
            if (COMPONENT_LINE.matcher(raw.trim()).matches()) {
                return COMPONENT;
            }
        }
        // object キーワードも他図種と衝突しないため先取りで判定する。
        for (String raw : lines) {
            if (OBJECT_LINE.matcher(raw.trim()).matches()) {
                return OBJECT;
            }
        }
        // ER 図固有マーカー: crow's-foot 演算子 (単独で確定)、または entity 列ブロック +
        // hide circle の同時出現。entity 単独はシーケンス図と共有するため判定材料にしない。
        boolean entityBlock = false;
        boolean hideCircle = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (ER_RELATION.matcher(line).find()) {
                return ER;
            }
            entityBlock = entityBlock || ER_ENTITY_BLOCK.matcher(line).matches();
            hideCircle = hideCircle || ER_HIDE_CIRCLE.matcher(line).matches();
        }
        if (entityBlock && hideCircle) {
            return ER;
        }
        // node / artifact / cloud 宣言も配置図に固有なため先取りで判定する
        // (database はシーケンス図の参加者と衝突するため主判定材料にしない)。
        for (String raw : lines) {
            if (DEPLOYMENT_LINE.matcher(raw.trim()).matches()) {
                return DEPLOYMENT;
            }
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("'")) {
                continue;
            }
            if (ACTIVITY_LINE.matcher(line).matches()) {
                return ACTIVITY;
            }
            if (STATE_LINE.matcher(line).matches()) {
                return STATE;
            }
            if (CLASS_LINE.matcher(line).matches()) {
                return CLASS;
            }
            if (SEQUENCE_LINE.matcher(line).matches()) {
                return SEQUENCE;
            }
        }
        return CLASS;
    }
}
