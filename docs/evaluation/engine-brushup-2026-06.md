# Java→PlantUML 中核エンジン ブラッシュアップログ (2026-06)

Juml の解析スキル（`/java-analyze` → java-analyst エージェント、`/java-lex` `/java-struct`
`/java-diagram`）を使って **Java→PlantUML 中核エンジン**（`JavaLexer` / JavaParser アダプタ
`core/formats/java/jp/` / `PlantUmlClassDiagram` / `PlantUmlSequenceDiagram` /
`PlantUmlPackageDiagram`）の解析精度・出力品質を、「探索 → 修正 → 検証 → 記録」で 10 ループ
ブラッシュアップする記録。Android/AOSP 軽量パーサ（別バッチで対応済み）とは重複しない。

各ループ = 1コミット（実コード修正 + 新規ユニットテスト + 本ドキュメント追記）。既存テストが
意図的に固定する挙動は尊重し、改善が妥当な場合のみ期待値を同時更新する。

| Loop | テーマ | 対象 | 状態 |
|---|---|---|---|
| 1 | シーケンス図ラベルの `< > &` HTML エスケープ | `PlantUmlSequenceDiagram` | 完了 |
| 2 | インラインコメントの `< >` 安全化 | `PlantUmlCommentFormatter` | 完了 |
| 3 | クラス図メンバー型の `< >` エスケープ | `PlantUmlClassDiagram` | 完了 |
| 4 | `record` コンポーネントのフィールド抽出 | `TypeDeclAdapter` | 完了 |
| 5 | `super.` 呼び出しの participant 化を防ぐ | `PlantUmlSequenceDiagram` | 完了 |
| 6 | 継承/実装エッジの重複出力防止 | `PlantUmlClassRelations` | 完了 |
| 7 | NOTE モードのオーバーロード note 重複 | `PlantUmlClassDiagram` | 完了 |
| 8 | パッケージ図の循環依存検出（双方向矢印） | `PlantUmlPackageDiagram` | 完了 |
| 9 | `sealed`/`permits` を継承矢印と区別 | `TypeDeclAdapter` (+`JavaClassInfo`) | 完了 |
| 10 | `static`/`instance` initializer の抽出 | `TypeDeclAdapter` (+`MemberAdapter`) | 完了 |

## Loop 1: HTML-escape sequence-diagram labels: シーケンス図ラベルの `< > &` を HTML エスケープ

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/PlantUmlSequenceDiagram.java` の `escapeLabel` は
  空白畳みと長さ制限のみで、`<` `>` `&` を無害化していなかった。これはメッセージラベル
  （`A -> B: ...`）と制御フロー guard（`opt`/`alt`/`loop` の条件）の両方に使われる。
- 症状: ラベルに `<`/`>`/`&` が含まれると PlantUML が creole/HTML タグとして解釈し、表示崩れや
  構文エラーになる。
- 再現入力（最小例）:
  ```java
  void run() { stream.filter(x -> x > 0); }   // ラベル: filter(x > 0)
  void m() { foo(List<String> xs); }           // ラベル: foo(List<String>)
  ```
  before: `filter(x > 0)` / after: `filter(x &gt; 0)`、`foo(List&lt;String&gt;)`。

### 修正 (Fix)

- `Add escapeHtml and apply it in escapeLabel: escapeHtml を追加し escapeLabel に適用`
  目的: ラベル中の `< > &` を HTML エンティティ化し、PlantUML のタグ誤認・構文エラーを防ぐため。
- 変更点: `escapeHtml`（`&`→`&amp;` を先頭に、`<`→`&lt;`、`>`→`&gt;`）を新設し、`escapeLabel` の
  末尾（長さ制限後）で適用。長さ評価はエスケープ前に行いエンティティ途中で切れないようにする。
- 既存テスト更新: 制御フロー guard も `escapeLabel` 経由のため、`opt x > 0` を期待していた
  `testOptForSingleIf` / `testNestedControlStructures` を `opt x &gt; 0` / `opt i &gt; 0` に更新
  （guard に `<` が出るケースの構文エラーも併せて防ぐ正当な改善のため）。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/PlantUmlSequenceSyntaxSafetyTest.java`):
  - `escapeLabelEscapesHtmlSpecials` … `x > 0` / `List<String>` / `a & b` のエンティティ化を検証。
- 実行: `./gradlew test --tests "...PlantUmlSequence*"` → BUILD SUCCESSFUL（更新済み既存含め PASSED）。

### 残課題 (Open Issues)

- アクティビティ図 (`PlantUmlActivityDiagram`) の guard（`while (i<10)`）は別経路で未エスケープ。
  同種の安全化が将来必要（本ループはシーケンス図に限定）。

## Loop 2: Neutralize `< > &` in inline comments: インラインコメントの `< > &` を無害化

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/PlantUmlCommentFormatter.java` の
  `sanitizeInlineComment` は制御文字畳み込みと末尾 `..` 抑止のみで、`< > &` を残していた。
- 症状: JavaDoc の `{@code List<String>}` や `<br>` が class body / NOTE に出ると PlantUML が
  HTML タグとして解釈し、さらに外側の `<color:...>...</color>` ラッパとネストが壊れる。
- 再現入力（最小例）:
  ```java
  /** Returns a List<String> of names. */
  public List<String> getNames() { ... }
  ```
  before: `Returns a List<String> of names.` / after: `Returns a List&lt;String&gt; of names.`。

### 修正 (Fix)

- `Escape comment HTML specials after length cap: 長さ確定後にコメントの HTML 特殊文字をエスケープ`
  目的: コメント中の `< > &` を無害化し、タグ誤認と `<color>` ラッパ干渉を防ぐため。
- 変更点: `escapeHtml`（`&`→`&amp;` 優先、`<`/`>`→エンティティ）を追加し、`sanitizeInlineComment` の
  末尾（長さ制限・`…` 付与の後）で適用。エンティティ途中での切断を避ける。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/PlantUmlCommentFormatterTest.java`・新規):
  - `sanitizeEscapesHtmlSpecials` / `escapeHtmlConvertsAmpersandFirst` /
    `sanitizeCollapsesControlCharsAndTrailingDots`（従来挙動の回帰防止）。
- 実行: `./gradlew test --tests "...PlantUmlCommentFormatterTest" --tests "...PlantUmlClassDiagram*"`
  → BUILD SUCCESSFUL。

### 残課題 (Open Issues)

- `escapeHtml` が `PlantUmlSequenceDiagram`（Loop 1）と本クラスに重複。将来共有ユーティリティへ
  集約してもよい（現状は各図生成器に閉じた最小変更を優先）。

## Loop 3: HTML-escape class-diagram member types: クラス図メンバー型の `< >` を HTML エスケープ

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/PlantUmlClassDiagram.java` の `emitField` /
  `emitMethod` / `emitFieldInlineMethods` はフィールド型・引数型・戻り型を素のまま class body に
  書いていた。
- 症状: `Map<String, List<Integer>>` のようなジェネリクス型の `< >` を PlantUML がタグとして
  解釈し、メンバー行のレイアウトが崩れる。
- 再現入力（最小例）:
  ```java
  class Foo { Map<String, List<Integer>> data; List<String> names(Set<Long> ids); }
  ```
  before: `data: Map<String, List<Integer>>` / after: `data: Map&lt;String, List&lt;Integer&gt;&gt;`。

### 修正 (Fix)

- `Escape member type strings via PlantUmlCommentFormatter.escapeHtml: メンバー型を escapeHtml で無害化`
  目的: ジェネリクス型の `< >` を HTML エンティティ化し、PlantUML のタグ誤認を防ぐため。
- 変更点: フィールド型・インラインラベル型・メソッド引数型・戻り型の 4 箇所で Loop 2 で追加した
  `PlantUmlCommentFormatter.escapeHtml` を適用（パッケージ内共有でコード重複を増やさない）。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/PlantUmlClassDiagramTest.java`):
  - `testGenericMemberTypesHtmlEscaped` … ネストジェネリクス・引数型・戻り型のエンティティ化を検証。
- 実行: `./gradlew test --tests "...PlantUmlClassDiagram*"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- アノテーション値（`@Foo(List<X>.class)` 等）は稀なため未エスケープ。必要になれば同様に適用可能。

## Loop 4: Extract record components as fields: record コンポーネントをフィールドとして抽出

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/java/jp/TypeDeclAdapter.java` の `adapt` は
  `FieldDeclaration` のみをフィールド化していた。`record` のヘッダコンポーネントは
  `RecordDeclaration.getParameters()` に現れ `FieldDeclaration` ではないため、完全に抜け落ちていた。
- 症状: `record Point(int x, int y)` がクラス図でフィールドゼロの空ボックスになる。
- 再現入力（最小例）:
  ```java
  public record Point(int x, java.util.List<String> tags) {}
  ```
  before: フィールド 0 件 / after: `x: int`（private final）、`tags: List<String>`（private final）。

### 修正 (Fix)

- `Add MemberAdapter.addRecordComponent and call it for records: record 用の addRecordComponent を追加`
  目的: record のコンポーネントを暗黙の `private final` フィールドとして取り込み、クラス図に表示するため。
- 変更点: `MemberAdapter.addRecordComponent(owner, Parameter)` を新設（name/type/annotations を移送、
  visibility=PRIVATE・final=true）。`TypeDeclAdapter.adapt` の field 収集後に `RecordDeclaration`
  のとき `getParameters()` を走査して追加。明示 static フィールドは従来どおり別途取り込まれる。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/JavaStructureExtractorModernJavaTest.java`):
  - `testRecordComponentsExtractedAsFields` … 2 コンポーネントを private final フィールド化。
  - `testRecordWithExplicitStaticFieldAndComponents` … 明示 static フィールドとコンポーネント両方。
- 実行: `./gradlew test --tests "...JavaStructureExtractor*" --tests "...java.*"` → BUILD SUCCESSFUL。

### 残課題 (Open Issues)

- record の暗黙アクセサ（`x()` 等）は AST に現れないため未合成（明示宣言されたものは従来どおり取り込み）。
  クラス図にアクセサを出したい場合は合成ロジックの追加が必要。

## Loop 5: Resolve super.* calls to the superclass, not a "super" participant: super.* 呼び出しを親クラスに解決

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/PlantUmlSequenceDiagram.java` の `resolveTarget` は
  receiver が `"this"` のみ自クラス扱いで、`"super"` はフィールド照合に失敗して receiver 文字列を
  そのまま返していた。
- 症状: `super.onCreate()` で `participant "super"` という意味不明なノードがシーケンス図に出る。
- 再現入力（最小例）:
  ```java
  class MyActivity extends AppCompatActivity { void onCreate() { super.onCreate(); } }
  ```
  before: `participant "super"` / after: 親クラス `AppCompatActivity` に解決。

### 修正 (Fix)

- `Map super receiver to superclass simple name: super レシーバを親クラスの単純名に対応付け`
  目的: `super.*` を親クラスへの呼び出しとして正しく表現し、無意味な `super` participant を防ぐため。
- 変更点: `resolveTarget` に `"super"` 分岐を追加。`cls.getSuperClass()` をジェネリクス除去＋単純名化して
  返す。親クラス不明（暗黙 Object）なら自クラス（`this` 相当）にフォールバック。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/PlantUmlSequenceDiagramTest.java`):
  - `testSuperCallResolvesToSuperclassNotParticipantSuper` … `AppCompatActivity` に解決、`super` participant なし。
  - `testSuperCallWithoutKnownSuperclassFallsBackToSelf` … 親不明時は自クラス扱い。
- 実行: `./gradlew test --tests "...PlantUmlSequenceDiagramTest"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- 親クラスが外部型の場合、lifeline は単純名のみ（FQN 解決はソルバ依存）。多段 `super`（grandparent）の
  区別はモデル上できない。

## Loop 6: Deduplicate implements edges in class diagrams: クラス図の実装エッジを重複排除

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/PlantUmlClassRelations.java` の `emitInheritance` は
  `c.getInterfaces()` を重複チェックなしで出力していた（`emitUsage` は `emitted` セットで排除済み）。
- 症状: `interfaces` リストに同一型が重複（パース復旧や `permits` 併合で起こり得る）すると、
  同じ実装エッジ `Parent <|.. Child` が複数行出て PlantUML のレイアウトが乱れる。
- 再現入力: 解析後の `JavaClassInfo.getInterfaces()` に同名 interface が 2 回入った状態。

### 修正 (Fix)

- `Guard implements edges with a LinkedHashSet: 実装エッジを LinkedHashSet で重複排除`
  目的: 同一実装エッジを 1 本だけ出力し、図のノイズ・崩れを防ぐため（`emitUsage` と同方針）。
- 変更点: `emitInheritance` の implements ループに `Set<String> emittedIface` を追加し、
  解決後の parent id を `add` できたときのみ出力。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/PlantUmlClassDiagramTest.java`):
  - `testDuplicateInterfaceEdgeEmittedOnce` … 重複注入後もエイリアス間エッジ行は 1 本。
- 実行: `./gradlew test --tests "...PlantUmlClassDiagram*"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- superClass と interfaces に同一型が現れるケース（不正だが）は別矢印種別のため統合しない。
  Loop 9（sealed/permits の区別）で permits 由来の混入を根本的に解消する。

## Loop 7: Merge overloaded method notes in NOTE mode: NOTE モードのオーバーロード note を統合

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/PlantUmlClassDiagram.java` の `emitNoteBlocks` は
  メソッドごとに `note right of Class::method` を出力していた。PlantUML はメソッド名のみで note
  ターゲットを参照するため、同名オーバーロードがあると同じターゲットに複数 note が出る。
- 症状: オーバーロードの 2 件目以降の note が無効化される／構文エラーになり、JavaDoc が失われる。
- 再現入力（最小例）:
  ```java
  class Foo { /** doc1 */ void process(String s){} /** doc2 */ void process(int n){} }
  ```
  before: `note right of C0::process` が 2 回（最後だけ有効）/ after: 1 note に doc1・doc2 を連結。

### 修正 (Fix)

- `Group method comments by name and emit one note each: メソッドコメントを名前でまとめ 1 note にする`
  目的: 同名 note の衝突を避けつつ、全オーバーロードの JavaDoc を保持するため。
- 変更点: `emitNoteBlocks` のメソッド処理を `Map<String, List<String>>`（名前→コメント群）でまとめ、
  名前ごとに 1 つの note を出力し各オーバーロードの本文を連結。フィールド名は一意なので従来どおり。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/PlantUmlClassDiagramTest.java`):
  - `testNoteModeMergesOverloadedMethodNotes` … `::process` note は 1 件、doc1・doc2 両方を含む。
- 実行: `./gradlew test --tests "...PlantUmlClassDiagram*"` → BUILD SUCCESSFUL（既存含め PASSED）。

### 残課題 (Open Issues)

- 同名オーバーロードの note 本文はシグネチャ無しで連結されるため、どの doc がどの引数版かは
  本文表現に依存する。引数シグネチャ付きの note ターゲットは PlantUML が対応しないため現状の連結が最善。

## Loop 8: Render package-diagram cycles as bidirectional arrows: パッケージ図の循環依存を双方向矢印で表現

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/uml/PlantUmlPackageDiagram.java` の `addRef` は
  `src --> dst` をその場で出力し、`src->dst` と `dst->src` を別キーで重複排除していた。
- 症状: 相互参照（循環依存）`A --> B` と `B --> A` が両方描かれ、PlantUML が矢印を重ねて
  循環の視認性が下がる。
- 再現入力（最小例）:
  ```java
  package com.a; class A { com.b.B b; }
  package com.b; class B { com.a.A a; }
  ```
  before: `--> ` 2 本 / after: `<--> ` 1 本。

### 修正 (Fix)

- `Collect directed edges then emit, merging reverse pairs: 有向エッジを収集後に出力し逆向きペアを統合`
  目的: 循環依存を双方向矢印 1 本にまとめ、重複描画を無くして循環を視認しやすくするため。
- 変更点: `addRef` を「`out` へ直書き」から「`Set<String> edges` への収集」に変更し、新設の
  `emitDependencyEdges` で出力。逆向きエッジが存在するペアは `a <--> b` 1 本に統合（自己ループ
  `a==b` は除外）。順序は first-seen を保持。

### 検証 (Verification)

- 追加テスト (`src/test/java/juml/core/formats/uml/PlantUmlPackageDiagramTest.java`):
  - `testCyclicDependencyRenderedAsBidirectionalArrow` … 循環は `<-->` 1 本、一方向 `-->` を出さない。
- 実行: `./gradlew test --tests "...PlantUmlPackageDiagramTest"` → BUILD SUCCESSFUL（既存 11 件含め PASSED）。

### 残課題 (Open Issues)

- 3 パッケージ以上の循環（A→B→C→A）は各辺が一方向のままで、視覚的な循環検出注記は付かない。
  SCC 検出による循環ハイライトは将来課題。`resolvePackage` の単純名照合（同名クラス）も別途課題。

## Loop 9: Separate sealed permits from implemented interfaces: sealed の permits を実装インタフェースと分離

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/java/jp/TypeDeclAdapter.java` は `sealed` 型の
  `permits` 列挙を `interfaces` に併合していた（`getPermittedTypes()` → `getInterfaces().add`）。
- 症状: `permits` は「継承を許可された子型」なのに、`emitInheritance` が
  `Child <|.. Shape`（Shape が Child を実装）という **逆向きで誤った実装矢印** を出していた。
  正しい矢印は子クラスの `extends` から出る `Shape <|-- Child`。
- 再現入力（最小例）:
  ```java
  sealed class Shape permits Circle {}
  final class Circle extends Shape {}
  ```
  before: `Circle <|.. Shape`（誤）＋`Shape <|-- Circle` / after: `Shape <|-- Circle` のみ。

### 修正 (Fix)

- `Add JavaClassInfo.permittedTypes and route permits there: permittedTypes フィールドを追加し permits を振り分け`
  目的: permits を継承許可先として別管理し、クラス図の逆向き矢印を防ぐため。
- 変更点: `JavaClassInfo` に `permittedTypes`（`getPermittedTypes()`）を追加。`TypeDeclAdapter` の
  permits を `interfaces` ではなく `permittedTypes` に格納。クラス図は permittedTypes からエッジを
  出さない（子の `extends` で正しい矢印が出るため）。
- 既存テスト更新: `permits` が interfaces に入ることを固定していた 2 テスト
  （`JavaParserFrontendStructureTest.sealedPermitsGoToInterfaces` → `...GoToPermittedTypes`、
  `JavaStructureExtractorTest.testSealedClassModifier`）を新挙動に更新（誤挙動の固定は正当な改善で置換）。
  sealed の `<<sealed>>` ステレオタイプ・凡例は modifier 由来のため影響なし。

### 検証 (Verification)

- 追加/更新テスト:
  - `JavaParserFrontendStructureTest.sealedPermitsGoToPermittedTypes` … permits は permittedTypes、interfaces は空。
  - `PlantUmlClassDiagramTest.testSealedPermitsDoNotEmitInvertedArrow` … `<|..` を出さず `<|--` のみ。
- 実行: `./gradlew test --tests "...formats.java.*" --tests "...formats.uml.*"` → BUILD SUCCESSFUL。

### 残課題 (Open Issues)

- permits 先が図に含まれない場合、その継承許可関係は可視化されない（子の extends が無いと矢印が出ない）。
  permits を破線注記等で明示するのは将来の表現拡張候補。

## Loop 10: Capture static/instance initializer blocks: static/instance イニシャライザブロックを取り込む

### 所見 (Finding)

- 弱点: `src/main/java/juml/core/formats/java/jp/TypeDeclAdapter.java` は `MethodDeclaration` /
  `FieldDeclaration` / `ConstructorDeclaration` のみを処理し、`InitializerDeclaration`
  （`static { ... }` / `{ ... }`）をどの分岐にも当てずスキップしていた。
- 症状: イニシャライザブロック内の呼び出し（`static { connect(); }`）がシーケンス図に現れない。
- 再現入力（最小例）:
  ```java
  class Dao { static { connect(); } static void connect() {} }
  ```
  before: `connect()` 呼び出しが消える / after: 擬似メソッド `<clinit>` の本体に `connect()` を保持。

### 修正 (Fix)

- `Add MemberAdapter.addInitializer and wire it in TypeDeclAdapter: addInitializer を追加し配線`
  目的: イニシャライザブロックを `<clinit>`（static）/ `<init>`（instance）擬似メソッドとして取り込み、
  内部呼び出しをシーケンス図の候補に含めるため。
- 変更点: `addInitializer`（`addConstructor` を踏襲し本体を `StatementAdapter.emitBody` で展開）を新設。
  `TypeDeclAdapter` に `InitializerDeclaration` 分岐を追加。クラス図の `emitMethod` はメソッド名を
  `escapeHtml` で出力するよう変更（`<clinit>` の `< >` が PlantUML タグに誤認されないため）。
- 付随リファクタ: Loop 1/5 の追記で `PlantUmlSequenceDiagram.java` が checkstyle の FileLength 上限
  (802) を超えたため、汎用ラベル整形 `escapeLabel` / `escapeHtml` を `PlantUmlCommentFormatter` へ集約し、
  型→単純名変換を `reduceTypeToSimpleName` に共通化して 801 行に収めた（重複も解消）。

### 検証 (Verification)

- 追加テスト:
  - `JavaStructureExtractorModernJavaTest.testStaticInitializerCapturedAsClinit` /
    `testInstanceInitializerCapturedAsInit` … `<clinit>`/`<init>` の本体呼び出しを検証。
  - `PlantUmlClassDiagramTest.testInitializerPseudoMethodNameHtmlEscaped` … 名前のエンティティ化。
- 実行: `./gradlew test checkstyleMain checkstyleTest` → **BUILD SUCCESSFUL**（全テスト + Checkstyle 緑）。

### 残課題 (Open Issues)

- 複数の `static {}` ブロックはそれぞれ別 `<clinit>` メソッドになる（JVM 的には連結だが本実装では分割）。
  必要なら 1 つに統合する処理を追加できる。

---

## 第2バッチ 全体検証（Loop 1〜10・中核エンジン）

- `./gradlew test checkstyleMain checkstyleTest` → **BUILD SUCCESSFUL**（全テスト + Checkstyle 緑）。
- 各ループは1コミット（実コード + 新規テスト + 本ドキュメント）。既存テストが意図的に固定する挙動は
  尊重し、誤挙動を固定していたテスト（`sealedPermitsGoToInterfaces` / `testSealedClassModifier` /
  guard ラベルの生 `>`）のみ、改善が妥当と判断して期待値を同時更新した。

## 変更サマリー（Loop 1〜10・CLAUDE.md テンプレート準拠）

- `Escape labels/comments/member types: ラベル・コメント・メンバー型を HTML エスケープ`（Loop 1-3）
  目的: `< > &` を含む生成物で PlantUML がタグ誤認・構文エラーを起こすのを防ぐため。
- `Extract record components as fields: record コンポーネントをフィールド化`（Loop 4）
  目的: record が空のクラス図になる問題を解消するため。
- `Resolve super.* to superclass: super.* を親クラスに解決`（Loop 5）
  目的: 無意味な `super` participant を防ぎ親クラスを正しく示すため。
- `Dedup edges / merge overload notes / cycle arrows: エッジ重複排除・note 統合・循環矢印`（Loop 6-8）
  目的: クラス図/パッケージ図の重複描画・note 衝突・循環の視認性を改善するため。
- `Separate sealed permits; capture initializers: permits 分離・イニシャライザ取り込み`（Loop 9-10）
  目的: permits の逆向き矢印を無くし、イニシャライザ内の呼び出しを可視化するため。

## 次にやること（残課題・フォローアップ候補）

- `Escape activity-diagram guards: アクティビティ図 guard のエスケープ`
  目的: `while (i<10)` 等の生 `<` を無害化するため（本バッチはシーケンス/クラス図に限定）。
- `Capture enum constant bodies / try-with-resources lambdas / LocalVar inline callbacks`
  目的: enum 定数本体・try リソース内ラムダ・ローカル変数代入コールバックの展開漏れを解消するため
  （java-analyst 調査の弱点 #5/#7/#8 に対応）。
- `Hex float literals in JavaLexer: JavaLexer の 16 進浮動小数点リテラル`
  目的: `0x1.8p+10f` のトークン化ずれを解消するため（弱点 #13・低優先）。
