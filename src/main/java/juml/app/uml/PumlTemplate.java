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
 */
public enum PumlTemplate {

    /** クラス図: クラス 2 つ + 継承・関連のサンプル。 */
    CLASS("template.class", String.join("\n",
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

    /** シーケンス図: 参加者 2 つ + 呼び出し/応答のサンプル。 */
    SEQUENCE("template.sequence", String.join("\n",
            "@startuml",
            "actor User",
            "participant Service",
            "User -> Service : request()",
            "activate Service",
            "Service --> User : response",
            "deactivate Service",
            "@enduml",
            "")),

    /** ユースケース図: アクターとユースケースのサンプル。 */
    USECASE("template.usecase", String.join("\n",
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

    /** アクティビティ図: 開始→分岐→終了のサンプル。 */
    ACTIVITY("template.activity", String.join("\n",
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

    /** 状態遷移図: 状態 2 つ + 遷移のサンプル。 */
    STATE("template.state", String.join("\n",
            "@startuml",
            "[*] --> Idle",
            "Idle --> Running : start",
            "Running --> Idle : stop",
            "Running --> [*] : finish",
            "@enduml",
            "")),

    /** コンポーネント図: コンポーネント 2 つ + インターフェースのサンプル。 */
    COMPONENT("template.component", String.join("\n",
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

    /** 空テンプレート: @startuml/@enduml のみ。 */
    EMPTY("template.empty", String.join("\n",
            "@startuml",
            "",
            "@enduml",
            ""));

    private final String labelKey;
    private final String body;

    PumlTemplate(String labelKey, String body) {
        this.labelKey = labelKey;
        this.body = body;
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
