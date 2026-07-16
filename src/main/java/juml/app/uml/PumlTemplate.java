// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.app.uml;

import juml.util.Messages;

/**
 * 「新規 UML 図」で使う PlantUML スケルトンのテンプレート集。
 *
 * <p>自由編集エディタタブ ({@link DiagramTabPane#openPumlEditor}) の初期テキストとして
 * 使う。ユーザーが最初の 1 行目から書き始めなくて済むよう、図種ごとの最小構成 +
 * 書き換えやすいサンプル要素を含める。</p>
 *
 * <p>UML 系だけでなく、同梱 PlantUML (Smetana レイアウト) がそのまま描ける
 * あらゆる図種 — エンティティ関連図・マインドマップ・WBS・ガント・JSON/YAML・
 * ワイヤフレーム (salt)・ArchiMate など — を網羅する。図解の初速を上げ、
 * 「まず空白の 1 行目」で手が止まらないようにするのが狙い。各スケルトンは
 * {@code @start…/@end…} を含む完結した図で、そのまま描画できることを
 * {@code PumlTemplateTest} が保証する。</p>
 */
public enum PumlTemplate {

    // ----- 構造 (Structure) -------------------------------------------------

    /** クラス図: クラス 2 つ + 継承・関連のサンプル。 */
    CLASS(Category.STRUCTURE, "template.class", String.join("\n",
            "@startuml",
            "class Example {",
            "  - name : String",
            "  + getName() : String",
            "}",
            "class Child",
            "interface Greetable {",
            "  + greet() : String",
            "}",
            "Example <|-- Child",
            "Greetable <|.. Example",
            "@enduml",
            "")),

    /** オブジェクト図: インスタンスとリンクのサンプル。 */
    OBJECT(Category.STRUCTURE, "template.object", String.join("\n",
            "@startuml",
            "object User {",
            "  id = 42",
            "  name = \"Ada\"",
            "}",
            "object Order {",
            "  total = 1980",
            "}",
            "User --> Order : places",
            "@enduml",
            "")),

    /** コンポーネント図: コンポーネント 2 つ + インターフェースのサンプル。 */
    COMPONENT(Category.STRUCTURE, "template.component", String.join("\n",
            "@startuml",
            "package \"App\" {",
            "  [UI]",
            "  [Core]",
            "}",
            "database DB",
            "[UI] --> [Core]",
            "[Core] --> DB",
            "@enduml",
            "")),

    /** 配置図: ノード・成果物・データベースのサンプル。 */
    DEPLOYMENT(Category.STRUCTURE, "template.deployment", String.join("\n",
            "@startuml",
            "cloud Internet",
            "node \"Web Server\" {",
            "  artifact app.war",
            "}",
            "database \"DB\" as db",
            "Internet --> \"Web Server\"",
            "\"Web Server\" --> db",
            "@enduml",
            "")),

    // ----- ふるまい (Behavior) ---------------------------------------------

    /**
     * シーケンス図: 参加者 2 つ + 呼び出し/応答のサンプル。
     *
     * <p>図形デザイナー (Design タブ) が扱える基本要素だけにとどめる。alt/loop などの
     * 複合フラグメントはデザイナーが未対応でタブがロックされるため、それらは
     * スニペット/スケルトンパレット (R2) から挿入させる。</p>
     */
    SEQUENCE(Category.BEHAVIOR, "template.sequence", String.join("\n",
            "@startuml",
            "actor User",
            "participant Service",
            "User -> Service : request()",
            "activate Service",
            "Service --> User : response",
            "deactivate Service",
            "@enduml",
            "")),

    /** アクティビティ図: 開始→分岐→終了のサンプル。 */
    ACTIVITY(Category.BEHAVIOR, "template.activity", String.join("\n",
            "@startuml",
            "start",
            ":Prepare input;",
            "if (valid?) then (yes)",
            "  :Process;",
            "else (no)",
            "  :Report error;",
            "endif",
            "stop",
            "@enduml",
            "")),

    /** 状態遷移図: 状態 2 つ + 入れ子状態 + 遷移のサンプル。 */
    STATE(Category.BEHAVIOR, "template.state", String.join("\n",
            "@startuml",
            "[*] --> Idle",
            "Idle --> Running : start",
            "state Running {",
            "  [*] --> Working",
            "  Working --> Paused : pause",
            "  Paused --> Working : resume",
            "}",
            "Running --> Idle : stop",
            "Running --> [*] : finish",
            "@enduml",
            "")),

    /** ユースケース図: アクターとユースケースのサンプル。 */
    USECASE(Category.BEHAVIOR, "template.usecase", String.join("\n",
            "@startuml",
            "left to right direction",
            "actor User",
            "rectangle System {",
            "  usecase \"Do something\" as UC1",
            "  usecase \"See results\" as UC2",
            "}",
            "User --> UC1",
            "User --> UC2",
            "@enduml",
            "")),

    /** タイミング図: 状態の時間変化 (robust/concise) のサンプル。 */
    TIMING(Category.BEHAVIOR, "template.timing", String.join("\n",
            "@startuml",
            "robust \"User\" as U",
            "concise \"Server\" as S",
            "@0",
            "U is Idle",
            "S is Waiting",
            "@100",
            "U is Active",
            "S is Processing",
            "@300",
            "U is Idle",
            "@enduml",
            "")),

    // ----- データ (Data) ----------------------------------------------------

    /** エンティティ関連図 (ER): 主キー・外部キー・多重度のサンプル。 */
    ER(Category.DATA, "template.er", String.join("\n",
            "@startuml",
            "entity User {",
            "  * id : int <<PK>>",
            "  --",
            "  name : varchar",
            "  email : varchar",
            "}",
            "entity Order {",
            "  * id : int <<PK>>",
            "  --",
            "  user_id : int <<FK>>",
            "  total : int",
            "}",
            "User ||--o{ Order",
            "@enduml",
            "")),

    /** JSON データ図: 構造化データを木として描くサンプル。 */
    JSON(Category.DATA, "template.json", String.join("\n",
            "@startjson",
            "{",
            "  \"name\": \"Juml\",",
            "  \"version\": 2,",
            "  \"tags\": [\"uml\", \"java\"],",
            "  \"nested\": { \"ok\": true }",
            "}",
            "@endjson",
            "")),

    /** YAML データ図: 構造化データを木として描くサンプル。 */
    YAML(Category.DATA, "template.yaml", String.join("\n",
            "@startyaml",
            "name: Juml",
            "version: 2",
            "tags:",
            "  - uml",
            "  - java",
            "nested:",
            "  ok: true",
            "@endyaml",
            "")),

    // ----- 計画・発想 (Planning) -------------------------------------------

    /** マインドマップ: 発想の階層構造を描くサンプル。 */
    MINDMAP(Category.PLANNING, "template.mindmap", String.join("\n",
            "@startmindmap",
            "* Project",
            "** Design",
            "*** UI",
            "*** UX",
            "** Build",
            "** Ship",
            "@endmindmap",
            "")),

    /** WBS (作業分解構成図): 作業の分解ツリーを描くサンプル。 */
    WBS(Category.PLANNING, "template.wbs", String.join("\n",
            "@startwbs",
            "* Release",
            "** Plan",
            "** Develop",
            "*** Frontend",
            "*** Backend",
            "** Test",
            "** Deploy",
            "@endwbs",
            "")),

    /** ガントチャート: タスクの期間・依存・マイルストーンのサンプル。 */
    GANTT(Category.PLANNING, "template.gantt", String.join("\n",
            "@startgantt",
            "[Design] lasts 5 days",
            "[Build] lasts 10 days",
            "[Build] starts at [Design]'s end",
            "[Test] lasts 4 days",
            "[Test] starts at [Build]'s end",
            "[Ship] happens at [Test]'s end",
            "@endgantt",
            "")),

    // ----- UI・アーキテクチャ (UI / Architecture) -------------------------

    /** ワイヤフレーム (salt): 画面レイアウトのモックのサンプル。 */
    WIREFRAME(Category.UI, "template.wireframe", String.join("\n",
            "@startsalt",
            "{",
            "  Login",
            "  {",
            "    Name     | \"value\"",
            "    Password | \"****\"",
            "  }",
            "  [ Cancel ] | [   OK   ]",
            "}",
            "@endsalt",
            "")),

    /** ArchiMate 図: ビジネス/アプリケーション層の要素と関係のサンプル。 */
    ARCHIMATE(Category.UI, "template.archimate", String.join("\n",
            "@startuml",
            "archimate #Business \"Capture Information\" as capture <<business-process>>",
            "archimate #Application \"Service\" as svc <<application-service>>",
            "capture --> svc",
            "@enduml",
            "")),

    // ----- 基本 (Basic) -----------------------------------------------------

    /** 空テンプレート: @startuml/@enduml のみ。 */
    EMPTY(Category.BASIC, "template.empty", String.join("\n",
            "@startuml",
            "",
            "@enduml",
            ""));

    /**
     * テンプレートの分類。「新規」メニューをカテゴリ別サブメニューへ束ねるのに使う
     * (フラットな長い一覧を避け、図種を見つけやすくする)。
     */
    public enum Category {
        STRUCTURE("template.category.structure"),
        BEHAVIOR("template.category.behavior"),
        DATA("template.category.data"),
        PLANNING("template.category.planning"),
        UI("template.category.ui"),
        BASIC("template.category.basic");

        private final String labelKey;

        Category(String labelKey) {
            this.labelKey = labelKey;
        }

        /** メニュー表示用の i18n 済みカテゴリ名。 */
        public String displayName() {
            return Messages.get(labelKey);
        }
    }

    private final Category category;
    private final String labelKey;
    private final String body;

    PumlTemplate(Category category, String labelKey, String body) {
        this.category = category;
        this.labelKey = labelKey;
        this.body = body;
    }

    /** このテンプレートの分類。 */
    public Category category() {
        return category;
    }

    /** メニュー表示用の i18n 済みラベル。 */
    public String displayName() {
        return Messages.get(labelKey);
    }

    /** エディタの初期テキストとなる PlantUML スケルトン。 */
    public String body() {
        return body;
    }
}
